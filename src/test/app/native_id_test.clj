(ns app.native-id-test
  "Tests for native-id functionality using Datalevin's built-in :db/id.
   
   Native IDs allow identity attributes to use Datalevin's internal entity ID
   directly, rather than requiring a separate UUID or other identifier. This
   provides better performance and compatibility with existing databases."
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
  "Schema for native-id tests - should NOT include :person/id"
  (dl/automatic-schema :main model/all-attributes))

;; =============================================================================
;; Schema Generation Tests
;; =============================================================================

(deftest native-id-schema-generation
  (testing "native-id attributes are excluded from schema generation"
    (is (not (contains? test-schema :person/id))
        ":person/id should not be in schema because it uses native :db/id")
    (is (contains? test-schema :person/name)
        "Regular attributes should be in schema")
    (is (contains? test-schema :person/email)
        "Regular attributes should be in schema")
    (is (contains? test-schema :person/age)
        "Regular attributes should be in schema")))

(deftest native-id-helper-function
  (testing "native-id? helper correctly identifies native-id attributes"
    (is (true? (dl/native-id? person/id))
        "person/id should be identified as native-id")
    (is (false? (dl/native-id? person/name))
        "person/name should not be native-id")
    (is (false? (dl/native-id? person/email))
        "person/email should not be native-id")))

;; =============================================================================
;; CRUD Operations with Native IDs
;; =============================================================================

(deftest create-person-with-auto-assigned-id
  (testing "Creating a person entity with auto-assigned :db/id"
    (with-test-db [conn test-schema]
      ;; Insert without explicit ID - Datalevin assigns :db/id
      (let [tx-result (d/transact! conn [{:person/name "Alice Smith"
                                          :person/email "alice@test.com"
                                          :person/age 30}])
            db (:db-after tx-result)
            ;; Find the assigned entity ID
            result (first (d/q '[:find ?e ?name ?email ?age
                                 :where 
                                 [?e :person/name ?name]
                                 [?e :person/email ?email]
                                 [?e :person/age ?age]]
                               db))
            eid (first result)]
        (is (some? eid) "Entity ID should be assigned")
        (is (pos-int? eid) "Entity ID should be a positive integer")
        (is (= "Alice Smith" (second result)))
        (is (= "alice@test.com" (nth result 2)))
        (is (= 30 (nth result 3)))))))

(deftest create-multiple-persons
  (testing "Creating multiple person entities in batch"
    (with-test-db [conn test-schema]
      (let [tx-result (d/transact! conn [{:person/name "Bob"
                                          :person/email "bob@test.com"
                                          :person/age 25}
                                         {:person/name "Charlie"
                                          :person/email "charlie@test.com"
                                          :person/age 35}])
            db (:db-after tx-result)
            count-result (d/q '[:find (count ?e) .
                                :where [?e :person/name _]]
                              db)]
        (is (= 2 count-result) "Should have created 2 entities")))))

(deftest read-person-by-native-id
  (testing "Reading a person by native entity ID"
    (with-test-db [conn test-schema]
      ;; Create person
      (let [tx-result (d/transact! conn [{:person/name "Dave"
                                          :person/email "dave@test.com"
                                          :person/age 40}])
            db (:db-after tx-result)
            ;; Get the entity ID
            eid (ffirst (d/q '[:find ?e :where [?e :person/name "Dave"]] db))
            ;; Pull using entity ID
            pulled (d/pull db [:db/id :person/name :person/email :person/age] eid)]
        (is (= eid (:db/id pulled)))
        (is (= "Dave" (:person/name pulled)))
        (is (= "dave@test.com" (:person/email pulled)))
        (is (= 40 (:person/age pulled)))))))

(deftest update-person-by-native-id
  (testing "Updating a person using native entity ID"
    (with-test-db [conn test-schema]
      ;; Create person
      (let [tx-result (d/transact! conn [{:person/name "Eve"
                                          :person/email "eve@test.com"
                                          :person/age 28}])
            db1 (:db-after tx-result)
            eid (ffirst (d/q '[:find ?e :where [?e :person/name "Eve"]] db1))]
        
        ;; Update using entity ID
        (d/transact! conn [[:db/add eid :person/age 29]
                           [:db/add eid :person/email "eve.updated@test.com"]])
        
        ;; Verify updates
        (let [db2 (d/db conn)
              pulled (d/pull db2 [:person/name :person/email :person/age] eid)]
          (is (= "Eve" (:person/name pulled)))
          (is (= "eve.updated@test.com" (:person/email pulled)))
          (is (= 29 (:person/age pulled))))))))

(deftest delete-person-by-native-id
  (testing "Deleting a person using native entity ID"
    (with-test-db [conn test-schema]
      ;; Create person
      (let [tx-result (d/transact! conn [{:person/name "Frank"
                                          :person/email "frank@test.com"
                                          :person/age 45}])
            db1 (:db-after tx-result)
            eid (ffirst (d/q '[:find ?e :where [?e :person/name "Frank"]] db1))]
        
        ;; Verify exists
        (is (some? eid))
        
        ;; Delete
        (d/transact! conn [[:db/retractEntity eid]])
        
        ;; Verify deleted
        (let [db2 (d/db conn)
              result (d/q '[:find ?e :where [?e :person/name "Frank"]] db2)]
          (is (empty? result) "Person should be deleted"))))))

;; =============================================================================
;; Query Pattern Conversion
;; =============================================================================

(deftest pathom-query-conversion
  (testing "pathom-query->datalevin-query replaces :person/id with :db/id"
    (let [query [:person/id :person/name :person/email]
          converted (dl/pathom-query->datalevin-query model/all-attributes query)]
      (is (= [:db/id :person/name :person/email] converted)
          "Native-id key should be replaced with :db/id")))
  
  (testing "pathom-query->datalevin-query leaves non-native keys unchanged"
    (let [query [:account/id :account/name :account/email]
          converted (dl/pathom-query->datalevin-query model/all-attributes query)]
      (is (= [:account/id :account/name :account/email] converted)
          "Non-native UUID IDs should remain unchanged"))))

;; =============================================================================
;; Resolver Generation and Execution
;; =============================================================================

(deftest native-id-resolver-generation
  (testing "Resolvers are generated for native-id attributes"
    (let [resolvers (dl/generate-resolvers model/all-attributes :main)
          person-resolvers (filter #(= :person/id (first (::pco/input (:config %)))) 
                                   resolvers)]
      (is (>= (count person-resolvers) 1)
          "Should generate at least one resolver for :person/id"))))

(deftest native-id-resolver-result-mapping
  (testing "Resolver correctly maps :db/id back to :person/id"
    (with-test-db [conn test-schema]
      ;; Create person
      (let [tx-result (d/transact! conn [{:person/name "Grace"
                                          :person/email "grace@test.com"
                                          :person/age 32}])
            db (:db-after tx-result)
            eid (ffirst (d/q '[:find ?e :where [?e :person/name "Grace"]] db))
            
            ;; Generate resolvers and find person resolver
            resolvers (dl/generate-resolvers model/all-attributes :main)
            person-resolver (first (filter #(= :person/id (first (::pco/input (:config %))))
                                           resolvers))
            
            ;; Create resolver environment
            env {::dlo/connections {:main conn}
                 ::dlo/databases {:main db}
                 ::attr/key->attribute (into {} (map (juxt ::attr/qualified-key identity))
                                             model/all-attributes)}
            
            ;; Execute resolver with native entity ID
            result (first ((:resolve person-resolver) env [{:person/id eid}]))]
        
        (is (some? result) "Resolver should return a result")
        (is (= eid (:person/id result))
            "Result should have :person/id mapped from :db/id")
        (is (= "Grace" (:person/name result)))
        (is (= "grace@test.com" (:person/email result)))
        (is (= 32 (:person/age result)))))))

;; =============================================================================
;; Delta Conversion and Save Middleware
;; =============================================================================

(deftest native-id-delta-conversion
  (testing "delta->txn uses raw entity ID for native-id attributes"
    (let [env {::attr/key->attribute (into {} (map (juxt ::attr/qualified-key identity))
                                           model/all-attributes)}
          ;; Simulate an update to entity with native ID (eid 42)
          delta {[:person/id 42] {:person/name {:before "Old Name" :after "New Name"}
                                  :person/age {:before 30 :after 31}}}
          txn (dl/delta->txn env delta)]
      
      (is (some? txn) "Transaction should be generated")
      (is (= 1 (count txn)) "Should have one transaction entry")
      
      (let [entry (first txn)]
        (is (map? entry))
        ;; Critical: native IDs use raw entity ID, not lookup ref
        (is (= 42 (:db/id entry))
            "Native ID should use raw entity ID (42), not [:person/id 42]")
        (is (= "New Name" (:person/name entry)))
        (is (= 31 (:person/age entry)))
        ;; Identity attribute should NOT be in the map for native IDs
        (is (not (contains? entry :person/id))
            "Native ID entities should not have identity attribute in txn map")))))

(deftest native-id-delta-new-entity
  (testing "delta->txn for new native-id entity (tempid)"
    (let [env {::attr/key->attribute (into {} (map (juxt ::attr/qualified-key identity))
                                           model/all-attributes)}
          tempid "person-temp-id"
          delta {[:person/id tempid] {:person/name {:before nil :after "New Person"}
                                      :person/email {:before nil :after "new@test.com"}
                                      :person/age {:before nil :after 25}}}
          txn (dl/delta->txn env delta)]
      
      (is (some? txn))
      (is (= 1 (count txn)))
      
      (let [entry (first txn)]
        ;; For new entities, :db/id should be tempid
        (is (= tempid (:db/id entry)))
        (is (= "New Person" (:person/name entry)))
        (is (= "new@test.com" (:person/email entry)))
        (is (= 25 (:person/age entry)))
        ;; Should not have :person/id attribute
        (is (not (contains? entry :person/id)))))))

;; =============================================================================
;; Mixed Entity Types (Native ID + UUID)
;; =============================================================================

(deftest mixed-native-and-uuid-entities
  (testing "Working with both native-id and UUID entities in same database"
    (with-test-db [conn test-schema]
      ;; Create a person (native ID)
      (let [person-tx (d/transact! conn [{:person/name "Henry"
                                          :person/email "henry@test.com"
                                          :person/age 50}])
            person-db (:db-after person-tx)
            person-eid (ffirst (d/q '[:find ?e :where [?e :person/name "Henry"]] person-db))]
        
        ;; Create an account (UUID ID)
        (let [account-id (random-uuid)]
          (dl/seed-database! conn [{:account/id account-id
                                    :account/name "Henry's Account"
                                    :account/email "henry-account@test.com"}])
          
          (let [db (d/db conn)
                ;; Query both types
                person (d/pull db [:db/id :person/name :person/email] person-eid)
                account (d/pull db [:account/id :account/name :account/email]
                               [:account/id account-id])]
            
            ;; Verify person (native ID)
            (is (= person-eid (:db/id person)))
            (is (= "Henry" (:person/name person)))
            
            ;; Verify account (UUID ID)
            (is (= account-id (:account/id account)))
            (is (= "Henry's Account" (:account/name account)))))))))

(comment
  ;; Run all native-id tests
  (clojure.test/run-tests 'app.native-id-test)
  
  ;; Run specific tests
  (clojure.test/test-var #'native-id-schema-generation)
  (clojure.test/test-var #'create-person-with-auto-assigned-id)
  (clojure.test/test-var #'native-id-resolver-result-mapping)
  (clojure.test/test-var #'native-id-delta-conversion))
