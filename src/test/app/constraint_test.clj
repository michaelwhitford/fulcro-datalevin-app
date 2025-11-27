(ns app.constraint-test
  "Tests for database constraints (unique values, identity, etc.)."
  {:clj-kondo/config '{:linters {:unresolved-symbol {:exclude [(app.test-utils/with-test-db [conn])]}}}}
  (:require
   [clojure.test :refer [deftest testing is]]
   [datalevin.core :as d]
   [us.whitford.fulcro.rad.database-adapters.datalevin :as dl]
   [app.test-utils :refer [with-test-db]]
   [app.model :as model]))

(def test-schema
  "Schema for constraint tests"
  (dl/automatic-schema :main model/all-attributes))

(deftest unique-email-constraint
  (testing "Account email must be unique - duplicate should fail"
    (with-test-db [conn test-schema]
      ;; Create first account
      (dl/seed-database! conn
                         [{:account/id (random-uuid)
                           :account/name "First User"
                           :account/email "duplicate@test.com"}])
      
      ;; Attempt to create second account with same email should fail
      (is (thrown? Exception
                   (dl/seed-database! conn
                                      [{:account/id (random-uuid)
                                        :account/name "Second User"
                                        :account/email "duplicate@test.com"}]))
          "Should throw exception for duplicate email"))))

(deftest unique-category-label-constraint
  (testing "Category label must be unique - duplicate should fail"
    (with-test-db [conn test-schema]
      ;; Create first category
      (dl/seed-database! conn
                         [{:category/id (random-uuid)
                           :category/label "Electronics"}])
      
      ;; Attempt to create second category with same label should fail
      (is (thrown? Exception
                   (dl/seed-database! conn
                                      [{:category/id (random-uuid)
                                        :category/label "Electronics"}]))
          "Should throw exception for duplicate category label"))))

(deftest identity-attribute-lookup
  (testing "Identity attributes can be used for entity lookup"
    (with-test-db [conn test-schema]
      (let [account-id (random-uuid)]
        ;; Create account
        (dl/seed-database! conn
                           [{:account/id account-id
                             :account/name "Lookup Test"
                             :account/email "lookup@test.com"}])
        
        ;; Query by identity should work
        (let [result (d/q '[:find ?name .
                            :in $ ?id
                            :where
                            [?e :account/id ?id]
                            [?e :account/name ?name]]
                          (d/db conn)
                          account-id)]
          (is (= "Lookup Test" result)))))))

(deftest unique-identity-acts-as-upsert
  (testing "Identity attributes act as upserts when duplicated"
    (with-test-db [conn test-schema]
      (let [same-id (random-uuid)]
        ;; Create first account
        (dl/seed-database! conn
                           [{:account/id same-id
                             :account/name "First"
                             :account/email "first@test.com"}])
        
        (is (= 1 (or (d/q '[:find (count ?e) . :where [?e :account/id _]]
                          (d/db conn))
                     0)))
        
        ;; Insert with same ID should update/upsert, not create duplicate
        (dl/seed-database! conn
                           [{:account/id same-id
                             :account/name "Second"
                             :account/email "second@test.com"}])
        
        ;; Should still only have one entity
        (is (= 1 (or (d/q '[:find (count ?e) . :where [?e :account/id _]]
                          (d/db conn))
                     0)))
        
        ;; Verify it was updated
        (let [name (d/q '[:find ?name . :in $ ?id :where [?e :account/id ?id] [?e :account/name ?name]]
                        (d/db conn)
                        same-id)]
          (is (= "Second" name)))))))

(deftest different-emails-allowed
  (testing "Different emails for different accounts should work"
    (with-test-db [conn test-schema]
      ;; Create multiple accounts with different emails
      (dl/seed-database! conn
                         [{:account/id (random-uuid)
                           :account/name "User 1"
                           :account/email "user1@test.com"}
                          {:account/id (random-uuid)
                           :account/name "User 2"
                           :account/email "user2@test.com"}
                          {:account/id (random-uuid)
                           :account/name "User 3"
                           :account/email "user3@test.com"}])
      
      ;; Verify all three were created
      (let [count (d/q '[:find (count ?e) .
                         :where [?e :account/email _]]
                       (d/db conn))]
        (is (= 3 count))))))

(deftest update-to-duplicate-email-fails
  (testing "Updating an email to a duplicate value should fail"
    (with-test-db [conn test-schema]
      ;; Create two accounts
      (dl/seed-database! conn
                         [{:account/id (random-uuid)
                           :account/name "User 1"
                           :account/email "user1@test.com"}
                          {:account/id (random-uuid)
                           :account/name "User 2"
                           :account/email "user2@test.com"}])
      
      ;; Get entity ID of second user
      (let [user2-eid (d/q '[:find ?e .
                             :where [?e :account/email "user2@test.com"]]
                           (d/db conn))]
        
        ;; Attempt to update user2's email to user1's email should fail
        (is (thrown? Exception
                     (d/transact! conn [[:db/add user2-eid :account/email "user1@test.com"]]))
            "Should throw exception when updating to duplicate email")))))

(deftest reference-to-nonexistent-entity-allowed
  (testing "Creating a reference to non-existent entity is allowed (dangling ref)"
    (with-test-db [conn test-schema]
      ;; Datalevin allows dangling references - doesn't validate on insert
      (dl/seed-database! conn
                         [{:item/id (random-uuid)
                           :item/name "Dangling Item"
                           :item/price 10.0
                           :item/category 999}])
      
      (is (= 1 (or (d/q '[:find (count ?e) . :where [?e :item/name "Dangling Item"]]
                        (d/db conn))
                   0)))
      
      ;; Reference exists but points to non-existent entity
      (is (= 1 (or (d/q '[:find (count ?e) . :where [?e :item/category 999]]
                        (d/db conn))
                   0))))))

(deftest valid-reference-succeeds
  (testing "Creating a valid reference should succeed"
    (with-test-db [conn test-schema]
      ;; Create category first
      (dl/seed-database! conn
                         [{:category/id (random-uuid)
                           :category/label "Valid Category"}])
      
      ;; Get category entity ID
      (let [cat-eid (d/q '[:find ?e . :where [?e :category/label "Valid Category"]]
                         (d/db conn))]
        
        ;; Create item with valid reference
        (dl/seed-database! conn
                           [{:item/id (random-uuid)
                             :item/name "Valid Item"
                             :item/price 10.0
                             :item/category cat-eid}])
        
        ;; Verify item was created with reference
        (let [result (d/q '[:find ?item-name ?cat-label
                            :where
                            [?item :item/name ?item-name]
                            [?item :item/category ?cat]
                            [?cat :category/label ?cat-label]]
                          (d/db conn))]
          (is (= [["Valid Item" "Valid Category"]] (vec result))))))))

(deftest required-attributes-validation
  (testing "Required attributes are enforced at application level"
    ;; Note: Datalevin doesn't enforce required attributes at DB level
    ;; This would be enforced by RAD form validation
    ;; Here we just verify we can create entities with all required fields
    (with-test-db [conn test-schema]
      ;; Account with all required fields
      (dl/seed-database! conn
                         [{:account/id (random-uuid)
                           :account/name "Complete User"  ; required
                           :account/email "complete@test.com"}])  ; required
      
      (is (= 1 (d/q '[:find (count ?e) . :where [?e :account/name _]]
                    (d/db conn)))))))

(comment
  ;; Run tests in REPL
  (clojure.test/run-tests 'app.constraint-test)
  
  ;; Run specific tests
  (clojure.test/test-var #'unique-email-constraint)
  (clojure.test/test-var #'update-to-duplicate-email-fails))
