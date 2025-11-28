(ns app.seed-issue-test
  "Tests to surface the seed! function issue.
   
   The seed! function in development.clj checks if the database has any data
   using the query [:find ?e :where [?e _ _]]. However, this query matches
   enum value entities that are automatically transacted during database startup.
   This causes seed! to skip seeding even on a fresh database."
  {:clj-kondo/config '{:linters {:unresolved-symbol {:exclude [(app.test-utils/with-test-db [conn])]}}}}
  (:require
   [clojure.test :refer [deftest testing is]]
   [datalevin.core :as d]
   [us.whitford.fulcro.rad.database-adapters.datalevin :as dl]
   [app.test-utils :refer [with-test-db]]
   [app.model :as model]))

(def test-schema
  "Schema for seed tests"
  (dl/automatic-schema :main model/all-attributes))

(deftest enum-entities-exist-after-schema-creation
  (testing "Enum values are automatically transacted when database starts"
    (with-test-db [conn test-schema :attributes model/all-attributes]
      (let [db (d/db conn)
            ;; This is the query used by seed! to check if database has data
            entities (d/q '[:find ?e :where [?e _ _]] db)]
        
        (testing "Database is not empty even before seeding"
          (is (seq entities) 
              "Database should contain enum entities"))
        
        (testing "Enum entities have :db/ident attributes"
          (let [idents (set (map #(d/pull db [:db/ident] (first %)) entities))
                has-idents (filter :db/ident idents)]
            (is (seq has-idents)
                "Should have entities with :db/ident (enum values)")))
        
        (testing "Enum entities include expected values"
          (let [all-idents (set (map #(get (d/pull db [:db/ident] (first %)) :db/ident) entities))]
            (is (contains? all-idents :account.role/admin)
                "Should contain :account.role/admin enum")
            (is (contains? all-idents :account.role/user)
                "Should contain :account.role/user enum")
            (is (contains? all-idents :status/active)
                "Should contain :status/active enum")
            (is (contains? all-idents :account.permissions/read)
                "Should contain :account.permissions/read enum")))))))

(deftest seed-check-incorrectly-detects-data
  (testing "seed! incorrectly detects enum entities as user data"
    (with-test-db [conn test-schema :attributes model/all-attributes]
      (let [db (d/db conn)
            ;; This is the exact check used by seed! function
            has-data? (seq (d/q '[:find ?e :where [?e _ _]] db))]
        
        (testing "Check returns true even though no user data exists"
          (is has-data?
              "ISSUE: seed! thinks database has data because of enum entities"))
        
        (testing "But there are no actual user entities"
          ;; Check for accounts
          (let [accounts (d/q '[:find ?e :where [?e :account/name _]] db)]
            (is (empty? accounts)
                "No accounts should exist"))
          
          ;; Check for persons
          (let [persons (d/q '[:find ?e :where [?e :person/name _]] db)]
            (is (empty? persons)
                "No persons should exist"))
          
          ;; Check for categories
          (let [categories (d/q '[:find ?e :where [?e :category/label _]] db)]
            (is (empty? categories)
                "No categories should exist"))
          
          ;; Check for items
          (let [items (d/q '[:find ?e :where [?e :item/name _]] db)]
            (is (empty? items)
                "No items should exist")))))))

(deftest better-seed-check
  (testing "A better way to check if database needs seeding"
    (with-test-db [conn test-schema :attributes model/all-attributes]
      (let [db (d/db conn)
            ;; Better check: look for actual user data, not just any entity
            has-user-data? (or (seq (d/q '[:find ?e :where [?e :account/name _]] db))
                               (seq (d/q '[:find ?e :where [?e :person/name _]] db))
                               (seq (d/q '[:find ?e :where [?e :category/label _]] db))
                               (seq (d/q '[:find ?e :where [?e :item/name _]] db)))]
        
        (testing "Better check correctly returns false on empty database"
          (is (not has-user-data?)
              "Database should be considered empty when only enums exist"))
        
        ;; Now add some data
        (d/transact! conn [{:account/id (random-uuid)
                            :account/name "Test Account"
                            :account/email "test@example.com"}])
        
        (let [db-after (d/db conn)
              has-user-data-after? (or (seq (d/q '[:find ?e :where [?e :account/name _]] db-after))
                                       (seq (d/q '[:find ?e :where [?e :person/name _]] db-after))
                                       (seq (d/q '[:find ?e :where [?e :category/label _]] db-after))
                                       (seq (d/q '[:find ?e :where [?e :item/name _]] db-after)))]
          
          (testing "Better check correctly detects data after seeding"
            (is has-user-data-after?
                "Database should be considered non-empty after adding data")))))))

(comment
  ;; Run tests
  (clojure.test/run-tests 'app.seed-issue-test)
  
  ;; Run specific test
  (clojure.test/test-var #'enum-entities-exist-after-schema-creation)
  (clojure.test/test-var #'seed-check-incorrectly-detects-data)
  (clojure.test/test-var #'better-seed-check))
