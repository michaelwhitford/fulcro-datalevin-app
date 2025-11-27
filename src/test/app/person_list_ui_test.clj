(ns app.person-list-ui-test
  "Tests for PersonList UI issue where only IDs are displayed.
   
   The PersonList report shows person IDs but other columns (name, email, age)
   are blank. This test file verifies the data flow from database through
   resolvers to ensure all person attributes are properly returned."
  {:clj-kondo/config '{:linters {:unresolved-symbol {:exclude [(app.test-utils/with-test-db [conn])]}}}}
  (:require
   [clojure.test :refer [deftest testing is]]
   [datalevin.core :as d]
   [us.whitford.fulcro.rad.database-adapters.datalevin :as dl]
   [us.whitford.fulcro.rad.database-adapters.datalevin-options :as dlo]
   [com.fulcrologic.rad.attributes :as attr]
   [com.wsscode.pathom3.connect.operation :as pco]
   [app.test-utils :refer [with-test-db]]
   [app.model :as model]
   [app.model.person :as person]))

(def test-schema
  "Schema for person tests - should NOT include :person/id"
  (dl/automatic-schema :main model/all-attributes))

;; =============================================================================
;; Data Seeding Tests
;; =============================================================================

(deftest person-data-in-database
  (testing "Person data can be stored and retrieved from Datalevin"
    (with-test-db [conn test-schema]
      ;; Create test person
      (let [tx-result (d/transact! conn [{:person/name "Test Person"
                                          :person/email "test@example.com"
                                          :person/age 30}])
            db (:db-after tx-result)
            eid (ffirst (d/q '[:find ?e :where [?e :person/name "Test Person"]] db))]
        
        (testing "Person data exists in database"
          (is (some? eid) "Entity ID should be assigned")
          (let [pulled (d/pull db [:db/id :person/name :person/email :person/age] eid)]
            (is (= eid (:db/id pulled)))
            (is (= "Test Person" (:person/name pulled)))
            (is (= "test@example.com" (:person/email pulled)))
            (is (= 30 (:person/age pulled)))))))))

;; =============================================================================
;; Person All Resolver Tests (FAILING)
;; =============================================================================

(deftest person-all-resolver-returns-full-data
  (testing "person-all-resolver returns all person attributes, not just IDs"
    (with-test-db [conn test-schema]
      ;; Create test persons
      (d/transact! conn [{:person/name "Alice"
                          :person/email "alice@example.com"
                          :person/age 28}
                         {:person/name "Bob"
                          :person/email "bob@example.com"
                          :person/age 35}])
      
      (let [db (d/db conn)
            resolvers (dl/generate-resolvers model/all-attributes :main)
            person-all-resolver (first (filter #(= 'person-all-resolver 
                                                    (::pco/op-name (pco/operation-config %)))
                                               resolvers))
            
            ;; Create resolver environment
            env {::dlo/connections {:main conn}
                 ::dlo/databases {:main db}
                 ::attr/key->attribute (into {} (map (juxt ::attr/qualified-key identity))
                                             model/all-attributes)}
            
            ;; Execute resolver with query specifying which fields to return
            ;; This simulates what the PersonList report does
            result ((:resolve person-all-resolver) 
                    env 
                    {:person/all {:person/id [:person/id :person/name :person/email :person/age]}})]
        
        (testing "Resolver returns person data"
          (is (some? result) "Resolver should return a result")
          (is (contains? result :person/all) "Result should have :person/all key")
          (is (seq (:person/all result)) "Should have person records"))
        
        (testing "Each person has all requested attributes"
          (let [persons (:person/all result)]
            (is (= 2 (count persons)) "Should have 2 persons")
            
            (doseq [person-data persons]
              (testing (str "Person " (:person/id person-data))
                (is (some? (:person/id person-data)) 
                    "Should have :person/id")
                (is (some? (:person/name person-data)) 
                    "FAILING: Should have :person/name but it's missing!")
                (is (some? (:person/email person-data)) 
                    "FAILING: Should have :person/email but it's missing!")
                (is (number? (:person/age person-data)) 
                    "FAILING: Should have :person/age but it's missing!")))))
        
        (testing "Person names are correct"
          (let [persons (:person/all result)
                names (set (map :person/name persons))]
            (is (contains? names "Alice") 
                "FAILING: Should contain Alice's name")
            (is (contains? names "Bob") 
                "FAILING: Should contain Bob's name")))))))

(deftest person-all-resolver-output-configuration
  (testing "person-all-resolver is configured to output all person attributes"
    (let [resolvers (dl/generate-resolvers model/all-attributes :main)
          person-all-resolver (first (filter #(= 'person-all-resolver 
                                                  (::pco/op-name (pco/operation-config %)))
                                             resolvers))]
      
      (is (some? person-all-resolver) "person-all-resolver should exist")
      
      (when person-all-resolver
        (let [config (pco/operation-config person-all-resolver)
              output (::pco/output config)]
          
          (testing "Resolver output configuration"
            (is (some? output) "Should have output configuration")
            
            ;; The output should include a map structure for :person/all
            ;; that specifies all the person attributes
            (testing "Output includes person attributes"
              (is (some #(and (map? %) (contains? % :person/all)) output)
                  "Output should include :person/all map")
              
              (let [person-all-spec (first (filter map? output))
                    person-attrs (get person-all-spec :person/all)]
                (when person-attrs
                  (is (some #{:person/id} person-attrs) 
                      "Should output :person/id")
                  (is (some #{:person/name} person-attrs) 
                      "Should output :person/name")
                  (is (some #{:person/email} person-attrs) 
                      "Should output :person/email")
                  (is (some #{:person/age} person-attrs) 
                      "Should output :person/age"))))))))))

(deftest person-id-resolver-returns-full-data
  (testing "person.id-resolver returns all person attributes for a given ID"
    (with-test-db [conn test-schema]
      ;; Create test person
      (let [tx-result (d/transact! conn [{:person/name "Charlie"
                                          :person/email "charlie@example.com"
                                          :person/age 42}])
            db (:db-after tx-result)
            eid (ffirst (d/q '[:find ?e :where [?e :person/name "Charlie"]] db))
            
            resolvers (dl/generate-resolvers model/all-attributes :main)
            person-id-resolver (first (filter #(= 'person.id-resolver 
                                                   (::pco/op-name (pco/operation-config %)))
                                              resolvers))
            
            ;; Create resolver environment
            env {::dlo/connections {:main conn}
                 ::dlo/databases {:main db}
                 ::attr/key->attribute (into {} (map (juxt ::attr/qualified-key identity))
                                             model/all-attributes)}
            
            ;; Execute resolver with the person ID
            result (first ((:resolve person-id-resolver) env [{:person/id eid}]))]
        
        (testing "Resolver returns person data"
          (is (some? result) "Resolver should return a result")
          (is (= eid (:person/id result)) "Should have correct :person/id"))
        
        (testing "Person has all attributes"
          (is (= "Charlie" (:person/name result)) 
              "Should have :person/name")
          (is (= "charlie@example.com" (:person/email result)) 
              "Should have :person/email")
          (is (= 42 (:person/age result)) 
              "Should have :person/age"))))))

(deftest compare-account-and-person-resolvers
  (testing "Account resolver (UUID ID) vs Person resolver (native ID) behavior"
    (with-test-db [conn test-schema]
      ;; Create test data
      (let [account-id (random-uuid)]
        (dl/seed-database! conn [{:account/id account-id
                                  :account/name "Test Account"
                                  :account/email "account@test.com"}])
        (d/transact! conn [{:person/name "Test Person"
                            :person/email "person@test.com"
                            :person/age 25}])
        
        (let [db (d/db conn)
              person-eid (ffirst (d/q '[:find ?e :where [?e :person/name "Test Person"]] db))
              resolvers (dl/generate-resolvers model/all-attributes :main)
              
              account-all-resolver (first (filter #(= 'account-all-resolver 
                                                       (::pco/op-name (pco/operation-config %)))
                                                  resolvers))
              person-all-resolver (first (filter #(= 'person-all-resolver 
                                                      (::pco/op-name (pco/operation-config %)))
                                                 resolvers))
              
              env {::dlo/connections {:main conn}
                   ::dlo/databases {:main db}
                   ::attr/key->attribute (into {} (map (juxt ::attr/qualified-key identity))
                                               model/all-attributes)}
              
              account-result ((:resolve account-all-resolver) 
                              env 
                              {:account/all {:account/id [:account/id :account/name :account/email]}})
              person-result ((:resolve person-all-resolver) 
                             env 
                             {:person/all {:person/id [:person/id :person/name :person/email :person/age]}})]
          
          (testing "Account resolver returns full data (baseline)"
            (let [accounts (:account/all account-result)
                  account (first accounts)]
              (is (= account-id (:account/id account)))
              (is (= "Test Account" (:account/name account)))
              (is (= "account@test.com" (:account/email account)))))
          
          (testing "Person resolver should return full data like Account resolver"
            (let [persons (:person/all person-result)
                  person (first persons)]
              (is (= person-eid (:person/id person)))
              (is (= "Test Person" (:person/name person)) 
                  "FAILING: Person resolver should return name like Account resolver does")
              (is (= "person@test.com" (:person/email person)) 
                  "FAILING: Person resolver should return email like Account resolver does")
              (is (= 25 (:person/age person)) 
                  "FAILING: Person resolver should return age"))))))))

(comment
  ;; Run all person UI tests
  (clojure.test/run-tests 'app.person-list-ui-test)
  
  ;; Run specific failing test
  (clojure.test/test-var #'person-all-resolver-returns-full-data)
  (clojure.test/test-var #'compare-account-and-person-resolvers)
  
  ;; Debug resolver output
  (require '[us.whitford.fulcro.rad.database-adapters.datalevin :as dl])
  (require '[app.model :as model])
  (require '[com.wsscode.pathom3.connect.operation :as pco])
  
  (def resolvers (dl/generate-resolvers model/all-attributes :main))
  (def person-all (first (filter #(= 'person-all-resolver 
                                      (::pco/op-name (pco/operation-config %)))
                                 resolvers)))
  (pco/operation-config person-all))
