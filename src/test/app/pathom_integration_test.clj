(ns app.pathom-integration-test
  "Integration tests for Pathom3 queries with Datalevin."
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [datalevin.core :as d]
   [mount.core :as mount]
   [us.whitford.fulcro.rad.database-adapters.datalevin :as dl]
   [us.whitford.fulcro.rad.database-adapters.datalevin-options :as dlo]
   [us.whitford.fulcro.rad.database-adapters.datalevin-common :as common]
   [com.fulcrologic.rad.attributes :as attr]
   [com.fulcrologic.rad.pathom3 :as pathom3]
   [com.fulcrologic.rad.resolvers :as res]
   [app.test-utils :refer [with-test-db]]
   [app.model :as model]
   [app.server.config :as config]))

(def test-schema
  "Schema for Pathom integration tests"
  (dl/automatic-schema :main model/all-attributes))

(defn make-test-parser
  "Create a Pathom parser for testing with the given connection."
  [conn]
  (let [automatic-resolvers (vec (concat (res/generate-resolvers model/all-attributes)
                                         (dl/generate-resolvers model/all-attributes :main)))
        env-middleware (-> (attr/wrap-env model/all-attributes)
                           (common/wrap-env (fn [env] {:main conn}) d/db))]
    (pathom3/new-processor config/config env-middleware [] [automatic-resolvers])))

(deftest query-all-accounts
  (testing "Query all accounts via Pathom"
    (with-test-db [conn test-schema]
      ;; Seed test data
      (dl/seed-database! conn
                         [{:account/id (random-uuid)
                           :account/name "Alice"
                           :account/email "alice@test.com"
                           :account/active? true}
                          {:account/id (random-uuid)
                           :account/name "Bob"
                           :account/email "bob@test.com"
                           :account/active? true}])
      
      (let [parser (make-test-parser conn)
            result (parser {} [{:account/all-accounts 
                                [:account/id :account/name :account/email]}])]
        
        (is (contains? result :account/all-accounts))
        (is (= 2 (count (:account/all-accounts result))))
        
        (let [accounts (:account/all-accounts result)
              names (set (map :account/name accounts))]
          (is (contains? names "Alice"))
          (is (contains? names "Bob")))))))

(deftest query-single-account-by-id
  (testing "Query a specific account by its ID"
    (with-test-db [conn test-schema]
      (let [account-id (random-uuid)]
        ;; Seed test data
        (dl/seed-database! conn
                           [{:account/id account-id
                             :account/name "Specific User"
                             :account/email "specific@test.com"
                             :account/active? true
                             :account/created-at (java.util.Date.)}])
        
        (let [parser (make-test-parser conn)
              result (parser {}
                             [{[:account/id account-id]
                               [:account/name :account/email :account/active?]}])]
          
          (is (contains? result [:account/id account-id]))
          (let [account (get result [:account/id account-id])]
            (is (= "Specific User" (:account/name account)))
            (is (= "specific@test.com" (:account/email account)))
            (is (true? (:account/active? account)))))))))

(deftest query-all-categories
  (testing "Query all categories via Pathom"
    (with-test-db [conn test-schema]
      ;; Seed test data
      (dl/seed-database! conn
                         [{:category/id (random-uuid)
                           :category/label "Electronics"}
                          {:category/id (random-uuid)
                           :category/label "Books"}
                          {:category/id (random-uuid)
                           :category/label "Clothing"}])
      
      (let [parser (make-test-parser conn)
            result (parser {} [{:category/all-categorys 
                                [:category/id :category/label]}])]
        
        (is (contains? result :category/all-categorys))
        (is (= 3 (count (:category/all-categorys result))))
        
        (let [categories (:category/all-categorys result)
              labels (set (map :category/label categories))]
          (is (contains? labels "Electronics"))
          (is (contains? labels "Books"))
          (is (contains? labels "Clothing")))))))

(deftest query-items-with-category-reference
  (testing "Query items and navigate to their categories"
    (with-test-db [conn test-schema]
      ;; Create categories first
      (dl/seed-database! conn
                         [{:category/id (random-uuid)
                           :category/label "Electronics"}
                          {:category/id (random-uuid)
                           :category/label "Books"}])
      
      ;; Get category entity IDs
      (let [electronics-eid (d/q '[:find ?e . :where [?e :category/label "Electronics"]]
                                 (d/db conn))
            books-eid (d/q '[:find ?e . :where [?e :category/label "Books"]]
                           (d/db conn))]
        
        ;; Create items with category references
        (dl/seed-database! conn
                           [{:item/id (random-uuid)
                             :item/name "Laptop"
                             :item/description "High-performance laptop"
                             :item/price 999.99
                             :item/in-stock 5
                             :item/category electronics-eid}
                            {:item/id (random-uuid)
                             :item/name "Clojure Book"
                             :item/description "Learn Clojure"
                             :item/price 49.99
                             :item/in-stock 20
                             :item/category books-eid}])
        
        (let [parser (make-test-parser conn)
              result (parser {}
                             [{:item/all-items
                               [:item/id
                                :item/name
                                :item/price
                                {:item/category [:category/id :category/label]}]}])]
          
          (is (contains? result :item/all-items))
          (is (= 2 (count (:item/all-items result))))
          
          (let [items (:item/all-items result)
                laptop (first (filter #(= "Laptop" (:item/name %)) items))
                book (first (filter #(= "Clojure Book" (:item/name %)) items))]
            
            ;; Verify laptop has Electronics category
            (is (some? laptop))
            (when laptop
              (is (= "Electronics" (get-in laptop [:item/category :category/label]))))
            
            ;; Verify book has Books category
            (is (some? book))
            (when book
              (is (= "Books" (get-in book [:item/category :category/label]))))))))))

(deftest query-with-multiple-levels
  (testing "Query with nested data navigation"
    (with-test-db [conn test-schema]
      ;; Setup test data
      (dl/seed-database! conn
                         [{:category/id (random-uuid)
                           :category/label "Test Category"}])
      
      (let [cat-eid (d/q '[:find ?e . :where [?e :category/label "Test Category"]]
                         (d/db conn))
            item-id (random-uuid)]
        
        (dl/seed-database! conn
                           [{:item/id item-id
                             :item/name "Test Item"
                             :item/price 100.0
                             :item/category cat-eid}])
        
        (let [parser (make-test-parser conn)
              result (parser {}
                             [{[:item/id item-id]
                               [:item/name
                                :item/price
                                {:item/category [:category/label]}]}])]
          
          (is (contains? result [:item/id item-id]))
          (let [item (get result [:item/id item-id])]
            (is (= "Test Item" (:item/name item)))
            (is (= 100.0 (:item/price item)))
            (is (= "Test Category" (get-in item [:item/category :category/label])))))))))

(deftest query-empty-collection
  (testing "Query returns empty collection when no data exists"
    (with-test-db [conn test-schema]
      (let [parser (make-test-parser conn)
            result (parser {} [{:account/all-accounts [:account/id :account/name]}])]
        
        (is (contains? result :account/all-accounts))
        (is (empty? (:account/all-accounts result)))))))

(deftest query-nonexistent-entity
  (testing "Query for non-existent entity ID"
    (with-test-db [conn test-schema]
      (let [fake-id (random-uuid)
            parser (make-test-parser conn)
            result (parser {}
                           [{[:account/id fake-id]
                             [:account/name :account/email]}])]
        
        ;; Result should contain the key but with nil or empty data
        (is (contains? result [:account/id fake-id]))))))

(comment
  ;; Run tests in REPL
  (clojure.test/run-tests 'app.pathom-integration-test)
  
  ;; Run specific test
  (clojure.test/test-var #'query-all-accounts)
  (clojure.test/test-var #'query-items-with-category-reference))
