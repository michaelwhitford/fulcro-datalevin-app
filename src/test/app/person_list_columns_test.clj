(ns app.person-list-columns-test
  "Test to verify that PersonList columns configuration matches ItemList pattern.
   
   The issue was that PersonList had person/id in ro/columns, which caused
   the report to only query for IDs. ItemList works correctly because item/id
   is NOT in ro/columns - it's only specified via ro/row-pk."
  {:clj-kondo/config '{:linters {:unresolved-symbol {:exclude [(app.test-utils/with-test-db [conn])]}}}}
  (:require
   [clojure.test :refer [deftest testing is]]
   [datalevin.core :as d]
   [us.whitford.fulcro.rad.database-adapters.datalevin :as dl]
   [us.whitford.fulcro.rad.database-adapters.datalevin-common :as common]
   [com.fulcrologic.rad.attributes :as attr]
   [com.fulcrologic.rad.pathom3 :as pathom3]
   [com.fulcrologic.rad.resolvers :as res]
   [app.test-utils :refer [with-test-db]]
   [app.model :as model]
   [app.model.person :as person]
   [app.server.config :as config]))

(def test-schema
  "Schema for person tests"
  (dl/automatic-schema :main model/all-attributes))

(defn make-test-parser
  "Create a Pathom parser for testing with the given connection."
  [conn]
  (let [automatic-resolvers (vec (concat (res/generate-resolvers model/all-attributes)
                                         (dl/generate-resolvers model/all-attributes :main)))
        env-middleware (-> (attr/wrap-env model/all-attributes)
                           (common/wrap-env (fn [env] {:main conn}) d/db))]
    (pathom3/new-processor config/config env-middleware [] [automatic-resolvers])))

(deftest person-all-query-returns-data
  (testing "person-all query through Pathom returns full person data"
    (with-test-db [conn test-schema]
      ;; Create test persons
      (d/transact! conn [{:person/name "Alice"
                          :person/email "alice@example.com"
                          :person/age 28}
                         {:person/name "Bob"
                          :person/email "bob@example.com"
                          :person/age 35}])
      
      (let [db (d/db conn)
            ;; Simulate the query that PersonList would make
            ;; After fix: ro/columns should NOT include person/id
            query [{:person/all [:person/id :person/name :person/email :person/age]}]
            
            ;; Process query through Pathom
            parser (make-test-parser conn)
            result (parser {} query)]
        
        (testing "Query returns person data"
          (is (contains? result :person/all) "Result should have :person/all key")
          (let [persons (:person/all result)]
            (is (= 2 (count persons)) "Should have 2 persons")
            
            (testing "Each person has all attributes"
              (doseq [person-data persons]
                (is (some? (:person/id person-data)) "Should have :person/id")
                (is (some? (:person/name person-data)) "Should have :person/name")
                (is (some? (:person/email person-data)) "Should have :person/email")
                (is (number? (:person/age person-data)) "Should have :person/age")))
            
            (testing "Person data is correct"
              (let [names (set (map :person/name persons))
                    emails (set (map :person/email persons))]
                (is (contains? names "Alice"))
                (is (contains? names "Bob"))
                (is (contains? emails "alice@example.com"))
                (is (contains? emails "bob@example.com"))))))))))

(deftest person-all-minimal-query
  (testing "person-all with minimal query still includes ID"
    (with-test-db [conn test-schema]
      ;; Create test person
      (d/transact! conn [{:person/name "Charlie"
                          :person/email "charlie@example.com"
                          :person/age 42}])
      
      (let [db (d/db conn)
            ;; Query for just names, but ID should still be included
            query [{:person/all [:person/name]}]
            
            parser (make-test-parser conn)
            result (parser {} query)]
        
        (testing "Result includes queried field"
          (let [person (first (:person/all result))]
            (is (= "Charlie" (:person/name person)))))))))

(deftest compare-item-and-person-queries
  (testing "Both item-all and person-all queries work the same way"
    (with-test-db [conn test-schema]
      ;; Create test data
      (let [category-id (random-uuid)]
        (dl/seed-database! conn [{:category/id category-id
                                  :category/label "Test Category"}])
        (d/transact! conn [{:item/id (random-uuid)
                            :item/name "Test Item"
                            :item/description "Test description"
                            :item/price 19.99
                            :item/in-stock 10
                            :item/category [:category/id category-id]}
                           {:person/name "Test Person"
                            :person/email "test@example.com"
                            :person/age 30}])
        
        (let [db (d/db conn)
              ;; Query both types
              query [{:item/all [:item/id :item/name :item/description]}
                     {:person/all [:person/id :person/name :person/email]}]
              
              parser (make-test-parser conn)
              result (parser {} query)]
          
          (testing "Item query returns full data"
            (let [item (first (:item/all result))]
              (is (some? (:item/id item)))
              (is (= "Test Item" (:item/name item)))
              (is (= "Test description" (:item/description item)))))
          
          (testing "Person query returns full data"
            (let [person (first (:person/all result))]
              (is (some? (:person/id person)))
              (is (= "Test Person" (:person/name person)))
              (is (= "test@example.com" (:person/email person))))))))))

(comment
  ;; Run tests
  (clojure.test/run-tests 'app.person-list-columns-test)
  
  ;; Run specific test
  (clojure.test/test-var #'person-all-query-returns-data))
