(ns app.crud-test
  "Tests for basic CRUD operations using Datalevin."
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [datalevin.core :as d]
   [us.whitford.fulcro.rad.database-adapters.datalevin :as dl]
   [app.test-utils :refer [with-test-db seed-test-data!]]
   [app.model :as model]))

(def test-schema
  "Schema for CRUD tests"
  (dl/automatic-schema :main model/all-attributes))

;; CREATE Tests

(deftest create-single-account
  (testing "Creating a single account entity"
    (with-test-db [conn test-schema]
      (let [account-id (random-uuid)
            account-data [{:account/id account-id
                           :account/name "Alice Johnson"
                           :account/email "alice@example.com"
                           :account/active? true
                           :account/created-at (java.util.Date.)}]]
        (dl/seed-database! conn account-data)
        
        (let [result (d/q '[:find ?name ?email ?active
                            :in $ ?id
                            :where
                            [?e :account/id ?id]
                            [?e :account/name ?name]
                            [?e :account/email ?email]
                            [?e :account/active? ?active]]
                          (d/db conn)
                          account-id)]
          (is (= 1 (count result)))
          (is (= [["Alice Johnson" "alice@example.com" true]] (vec result))))))))

(deftest create-multiple-accounts-batch
  (testing "Creating multiple accounts in a single batch"
    (with-test-db [conn test-schema]
      (let [accounts [{:account/id (random-uuid)
                       :account/name "Alice"
                       :account/email "alice@test.com"
                       :account/active? true}
                      {:account/id (random-uuid)
                       :account/name "Bob"
                       :account/email "bob@test.com"
                       :account/active? true}
                      {:account/id (random-uuid)
                       :account/name "Charlie"
                       :account/email "charlie@test.com"
                       :account/active? false}]]
        (dl/seed-database! conn accounts)
        
        (let [count-result (d/q '[:find (count ?e) .
                                  :where [?e :account/name _]]
                                (d/db conn))]
          (is (= 3 count-result)))))))

(deftest create-item-with-category-reference
  (testing "Creating an item with a reference to a category"
    (with-test-db [conn test-schema]
      (let [category-id (random-uuid)
            item-id (random-uuid)]
        ;; First create the category
        (dl/seed-database! conn [{:category/id category-id
                                  :category/label "Electronics"}])
        
        ;; Get the entity id for the category
        (let [cat-eid (d/q '[:find ?e .
                             :where [?e :category/label "Electronics"]]
                           (d/db conn))]
          ;; Create item with reference to category entity id
          (dl/seed-database! conn [{:item/id item-id
                                    :item/name "Laptop"
                                    :item/price 999.99
                                    :item/in-stock 5
                                    :item/category cat-eid}])
          
          ;; Verify the reference
          (let [result (d/q '[:find ?item-name ?cat-label
                              :where
                              [?item :item/name ?item-name]
                              [?item :item/category ?cat]
                              [?cat :category/label ?cat-label]]
                            (d/db conn))]
            (is (= [["Laptop" "Electronics"]] (vec result)))))))))

;; READ Tests

(deftest read-all-accounts
  (testing "Reading all account entities"
    (with-test-db [conn test-schema]
      (let [accounts [{:account/id (random-uuid)
                       :account/name "User 1"
                       :account/email "user1@test.com"}
                      {:account/id (random-uuid)
                       :account/name "User 2"
                       :account/email "user2@test.com"}]]
        (dl/seed-database! conn accounts)
        
        (let [result (d/q '[:find ?name ?email
                            :where
                            [?e :account/name ?name]
                            [?e :account/email ?email]]
                          (d/db conn))]
          (is (= 2 (count result)))
          (is (some #(= "User 1" (first %)) result))
          (is (some #(= "User 2" (first %)) result)))))))

(deftest read-account-by-id
  (testing "Reading a specific account by UUID"
    (with-test-db [conn test-schema]
      (let [account-id (random-uuid)]
        (dl/seed-database! conn [{:account/id account-id
                                  :account/name "Specific User"
                                  :account/email "specific@test.com"}])
        
        (let [result (d/q '[:find ?name ?email
                            :in $ ?id
                            :where
                            [?e :account/id ?id]
                            [?e :account/name ?name]
                            [?e :account/email ?email]]
                          (d/db conn)
                          account-id)]
          (is (= [["Specific User" "specific@test.com"]] (vec result))))))))

(deftest read-items-with-category-navigation
  (testing "Reading items and navigating to their categories"
    (with-test-db [conn test-schema]
      ;; Create categories
      (dl/seed-database! conn [{:category/id (random-uuid)
                                :category/label "Electronics"}
                               {:category/id (random-uuid)
                                :category/label "Books"}])
      
      ;; Get entity IDs
      (let [electronics-eid (d/q '[:find ?e . :where [?e :category/label "Electronics"]]
                                 (d/db conn))
            books-eid (d/q '[:find ?e . :where [?e :category/label "Books"]]
                           (d/db conn))]
        
        ;; Create items with category references
        (dl/seed-database! conn [{:item/id (random-uuid)
                                  :item/name "Laptop"
                                  :item/price 999.99
                                  :item/category electronics-eid}
                                 {:item/id (random-uuid)
                                  :item/name "Programming Book"
                                  :item/price 49.99
                                  :item/category books-eid}])
        
        ;; Query items with their categories
        (let [result (d/q '[:find ?item-name ?cat-label
                            :where
                            [?item :item/name ?item-name]
                            [?item :item/category ?cat]
                            [?cat :category/label ?cat-label]]
                          (d/db conn))]
          (is (= 2 (count result)))
          (is (some #(= ["Laptop" "Electronics"] %) result))
          (is (some #(= ["Programming Book" "Books"] %) result)))))))

;; UPDATE Tests

(deftest update-account-email
  (testing "Updating an account's email address"
    (with-test-db [conn test-schema]
      (let [account-id (random-uuid)]
        ;; Create account
        (dl/seed-database! conn [{:account/id account-id
                                  :account/name "Update Test"
                                  :account/email "old@test.com"}])
        
        ;; Get entity ID
        (let [eid (d/q '[:find ?e . :in $ ?id :where [?e :account/id ?id]]
                       (d/db conn)
                       account-id)]
          
          ;; Update email
          (d/transact! conn [[:db/add eid :account/email "new@test.com"]])
          
          ;; Verify update
          (let [result (d/q '[:find ?email .
                              :in $ ?id
                              :where
                              [?e :account/id ?id]
                              [?e :account/email ?email]]
                            (d/db conn)
                            account-id)]
            (is (= "new@test.com" result))))))))

(deftest update-item-price-and-stock
  (testing "Updating multiple attributes of an item"
    (with-test-db [conn test-schema]
      (let [item-id (random-uuid)]
        ;; Create item
        (dl/seed-database! conn [{:item/id item-id
                                  :item/name "Test Product"
                                  :item/price 99.99
                                  :item/in-stock 10}])
        
        ;; Get entity ID
        (let [eid (d/q '[:find ?e . :in $ ?id :where [?e :item/id ?id]]
                       (d/db conn)
                       item-id)]
          
          ;; Update price and stock
          (d/transact! conn [[:db/add eid :item/price 79.99]
                             [:db/add eid :item/in-stock 25]])
          
          ;; Verify updates
          (let [result (d/q '[:find ?price ?stock
                              :in $ ?id
                              :where
                              [?e :item/id ?id]
                              [?e :item/price ?price]
                              [?e :item/in-stock ?stock]]
                            (d/db conn)
                            item-id)]
            (is (= [[79.99 25]] (vec result)))))))))

(deftest update-account-active-status
  (testing "Toggling account active status"
    (with-test-db [conn test-schema]
      (let [account-id (random-uuid)]
        ;; Create inactive account
        (dl/seed-database! conn [{:account/id account-id
                                  :account/name "Status Test"
                                  :account/email "status@test.com"
                                  :account/active? false}])
        
        (let [eid (d/q '[:find ?e . :in $ ?id :where [?e :account/id ?id]]
                       (d/db conn)
                       account-id)]
          
          ;; Activate account
          (d/transact! conn [[:db/add eid :account/active? true]])
          
          ;; Verify
          (let [is-active (d/q '[:find ?active .
                                 :in $ ?id
                                 :where
                                 [?e :account/id ?id]
                                 [?e :account/active? ?active]]
                               (d/db conn)
                               account-id)]
            (is (true? is-active))))))))

;; DELETE Tests

(deftest delete-account
  (testing "Deleting an account entity"
    (with-test-db [conn test-schema]
      (let [account-id (random-uuid)]
        ;; Create account
        (dl/seed-database! conn [{:account/id account-id
                                  :account/name "Delete Me"
                                  :account/email "delete@test.com"}])
        
        ;; Verify it exists
        (is (= 1 (or (d/q '[:find (count ?e) . :where [?e :account/name "Delete Me"]]
                          (d/db conn))
                     0)))
        
        ;; Get entity ID and delete
        (let [eid (d/q '[:find ?e . :in $ ?id :where [?e :account/id ?id]]
                       (d/db conn)
                       account-id)]
          (d/transact! conn [[:db/retractEntity eid]])
          
          ;; Verify deletion
          (is (= 0 (or (d/q '[:find (count ?e) . :where [?e :account/name "Delete Me"]]
                            (d/db conn))
                       0))))))))

(deftest delete-category-and-verify-items
  (testing "Deleting a category - items should lose reference"
    (with-test-db [conn test-schema]
      (let [category-id (random-uuid)
            item-id (random-uuid)]
        ;; Create category and item with reference
        (dl/seed-database! conn [{:category/id category-id
                                  :category/label "ToDelete"}])
        
        (let [cat-eid (d/q '[:find ?e . :where [?e :category/label "ToDelete"]]
                           (d/db conn))]
          (dl/seed-database! conn [{:item/id item-id
                                    :item/name "Orphaned Item"
                                    :item/price 10.0
                                    :item/category cat-eid}])
          
          ;; Verify reference exists
          (is (= 1 (or (d/q '[:find (count ?item) .
                              :where
                              [?item :item/name "Orphaned Item"]
                              [?item :item/category ?cat]]
                            (d/db conn))
                       0)))
          
          ;; Delete category
          (d/transact! conn [[:db/retractEntity cat-eid]])
          
          ;; Verify category is gone
          (is (zero? (or (d/q '[:find (count ?e) . :where [?e :category/label "ToDelete"]]
                              (d/db conn))
                         0)))
          
          ;; Item should still exist but without category reference
          (is (= 1 (or (d/q '[:find (count ?e) . :where [?e :item/name "Orphaned Item"]]
                            (d/db conn))
                       0)))
          (is (zero? (or (d/q '[:find (count ?item) .
                                :where
                                [?item :item/name "Orphaned Item"]
                                [?item :item/category ?cat]]
                              (d/db conn))
                         0))))))))

(deftest delete-multiple-entities-batch
  (testing "Deleting multiple entities in a single transaction"
    (with-test-db [conn test-schema]
      ;; Create multiple accounts
      (let [ids [(random-uuid) (random-uuid) (random-uuid)]]
        (dl/seed-database! conn (mapv (fn [id i]
                                        {:account/id id
                                         :account/name (str "User " i)
                                         :account/email (str "user" i "@test.com")})
                                      ids
                                      (range (count ids))))
        
        ;; Verify all exist
        (is (= 3 (d/q '[:find (count ?e) . :where [?e :account/name _]]
                      (d/db conn))))
        
        ;; Get all entity IDs
        (let [eids (d/q '[:find [?e ...]
                          :where [?e :account/name _]]
                        (d/db conn))
              retract-txs (mapv (fn [eid] [:db/retractEntity eid]) eids)]
          
          ;; Delete all in one transaction
          (d/transact! conn retract-txs)
          
          ;; Verify all deleted
          (is (zero? (or (d/q '[:find (count ?e) . :where [?e :account/name _]]
                              (d/db conn))
                         0))))))))

(comment
  ;; Run tests in REPL
  (clojure.test/run-tests 'app.crud-test)
  
  ;; Run specific test
  (clojure.test/test-var #'create-single-account)
  (clojure.test/test-var #'create-item-with-category-reference)
  (clojure.test/test-var #'update-account-email)
  (clojure.test/test-var #'delete-account))
