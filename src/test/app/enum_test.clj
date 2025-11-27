(ns app.enum-test
  "Tests for enum attribute support in Datalevin adapter."
  {:clj-kondo/config '{:linters {:unresolved-symbol {:exclude [(app.test-utils/with-test-db [conn])]}}}}
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.fulcrologic.rad.attributes :as attr]
   [datalevin.core :as d]
   [us.whitford.fulcro.rad.database-adapters.datalevin :as dl]
   [us.whitford.fulcro.rad.database-adapters.datalevin.start-databases :as start-db]
   [app.test-utils :refer [with-test-db]]
   [app.model :as model]
   [app.model.account :as account]))

(def test-schema
  "Schema for enum tests including enum ident entities"
  (dl/automatic-schema :main model/all-attributes))

(defn- transact-enum-idents!
  "Transact enum ident entities for the test schema."
  [conn]
  (let [enum-txn (#'start-db/enumerated-values
                  (filter #(= :main (::attr/schema %))
                          model/all-attributes))]
    (when (seq enum-txn)
      (try
        (d/transact! conn enum-txn)
        (catch Exception _e
          ;; Ignore if enums already exist
          nil)))))

;; ================================================================================
;; Schema Generation Tests
;; ================================================================================

(deftest enum-schema-generation
  (testing "generates correct schema for enum attributes"
    (let [schema test-schema]
      ;; Enum attributes should map to :db.type/ref
      (is (= :db.type/ref (get-in schema [:account/role :db/valueType]))
          "Single-value enum should be :db.type/ref")
      (is (= :db.type/ref (get-in schema [:account/status :db/valueType]))
          "Qualified enum should be :db.type/ref")
      (is (= :db.type/ref (get-in schema [:account/permissions :db/valueType]))
          "Many-cardinality enum should be :db.type/ref")
      
      ;; Many cardinality should be preserved
      (is (= :db.cardinality/many (get-in schema [:account/permissions :db/cardinality]))
          "Many-cardinality enum should preserve cardinality")))

  (testing "generates enum ident entities with unqualified keywords"
    (let [enum-txn (#'start-db/enumerated-values [account/role])]
      (is (= 3 (count enum-txn)) "Should generate 3 enum idents for role")
      (is (contains? (set (map :db/ident enum-txn)) :account.role/admin)
          "Should generate :account.role/admin")
      (is (contains? (set (map :db/ident enum-txn)) :account.role/user)
          "Should generate :account.role/user")
      (is (contains? (set (map :db/ident enum-txn)) :account.role/guest)
          "Should generate :account.role/guest")))

  (testing "generates enum ident entities with qualified keywords"
    (let [enum-txn (#'start-db/enumerated-values [account/status])]
      (is (= 3 (count enum-txn)) "Should generate 3 enum idents for status")
      (is (contains? (set (map :db/ident enum-txn)) :status/active)
          "Should preserve qualified keyword :status/active")
      (is (contains? (set (map :db/ident enum-txn)) :status/inactive)
          "Should preserve qualified keyword :status/inactive")
      (is (contains? (set (map :db/ident enum-txn)) :status/pending)
          "Should preserve qualified keyword :status/pending"))))

;; ================================================================================
;; CRUD Tests with Enums
;; ================================================================================

(deftest create-with-single-enum
  (testing "creating an entity with single-value enum (unqualified keywords)"
    (with-test-db [conn test-schema]
      (transact-enum-idents! conn)
      (let [account-id (random-uuid)
            account-data [{:account/id   account-id
                           :account/name "Alice Admin"
                           :account/email "alice@example.com"
                           :account/role :account.role/admin}]]
        (d/transact! conn account-data)
        
        (let [result (d/pull (d/db conn)
                             [:account/id :account/name {:account/role [:db/ident]}]
                             [:account/id account-id])
              role   (get-in result [:account/role :db/ident])]
          (is (= account-id (:account/id result)))
          (is (= "Alice Admin" (:account/name result)))
          (is (= :account.role/admin role))))))

  (testing "creating an entity with single-value enum (qualified keywords)"
    (with-test-db [conn test-schema]
      (transact-enum-idents! conn)
      (let [account-id (random-uuid)
            account-data [{:account/id     account-id
                           :account/name   "Bob Active"
                           :account/email  "bob@example.com"
                           :account/status :status/active}]]
        (d/transact! conn account-data)
        
        (let [result (d/pull (d/db conn)
                             [:account/id :account/name {:account/status [:db/ident]}]
                             [:account/id account-id])
              status (get-in result [:account/status :db/ident])]
          (is (= account-id (:account/id result)))
          (is (= "Bob Active" (:account/name result)))
          (is (= :status/active status)))))))

(deftest create-with-many-enum
  (testing "creating an entity with many-cardinality enum"
    (with-test-db [conn test-schema]
      (transact-enum-idents! conn)
      (let [account-id (random-uuid)
            account-data [{:account/id          account-id
                           :account/name        "Charlie Perms"
                           :account/email       "charlie@example.com"
                           :account/permissions [:account.permissions/read
                                                 :account.permissions/write]}]]
        (d/transact! conn account-data)
        
        (let [result (d/pull (d/db conn)
                             [:account/id :account/name {:account/permissions [:db/ident]}]
                             [:account/id account-id])
              perms  (set (map :db/ident (:account/permissions result)))]
          (is (= account-id (:account/id result)))
          (is (= "Charlie Perms" (:account/name result)))
          (is (= 2 (count perms)))
          (is (contains? perms :account.permissions/read))
          (is (contains? perms :account.permissions/write)))))))

(deftest read-enum-values
  (testing "reading enum values from database"
    (with-test-db [conn test-schema]
      (transact-enum-idents! conn)
      (let [admin-id (random-uuid)
            user-id  (random-uuid)]
        ;; Create accounts with different roles
        (d/transact! conn [{:account/id admin-id
                            :account/name "Admin User"
                            :account/email "admin@test.com"
                            :account/role :account.role/admin}
                           {:account/id user-id
                            :account/name "Regular User"
                            :account/email "user@test.com"
                            :account/role :account.role/user}])
        
        ;; Query for admin role
        (let [admin-result (d/q '[:find ?name
                                  :where
                                  [?e :account/name ?name]
                                  [?e :account/role ?role]
                                  [?role :db/ident :account.role/admin]]
                                (d/db conn))]
          (is (= [["Admin User"]] (vec admin-result))))
        
        ;; Query for user role
        (let [user-result (d/q '[:find ?name
                                 :where
                                 [?e :account/name ?name]
                                 [?e :account/role ?role]
                                 [?role :db/ident :account.role/user]]
                               (d/db conn))]
          (is (= [["Regular User"]] (vec user-result))))))))

(deftest update-enum-value
  (testing "updating a single-value enum attribute"
    (with-test-db [conn test-schema]
      (transact-enum-idents! conn)
      (let [account-id (random-uuid)]
        ;; Create with admin role
        (d/transact! conn [{:account/id   account-id
                            :account/name "Promoted User"
                            :account/email "promoted@test.com"
                            :account/role :account.role/user}])
        
        ;; Verify initial role
        (let [initial (d/pull (d/db conn)
                              [{:account/role [:db/ident]}]
                              [:account/id account-id])
              initial-role (get-in initial [:account/role :db/ident])]
          (is (= :account.role/user initial-role)))
        
        ;; Get entity ID and update to admin
        (let [eid (d/q '[:find ?e . :in $ ?id :where [?e :account/id ?id]]
                       (d/db conn)
                       account-id)]
          (d/transact! conn [[:db/add eid :account/role :account.role/admin]]))
        
        ;; Verify updated role
        (let [updated (d/pull (d/db conn)
                              [{:account/role [:db/ident]}]
                              [:account/id account-id])
              updated-role (get-in updated [:account/role :db/ident])]
          (is (= :account.role/admin updated-role))))))

  (testing "updating status enum with qualified keywords"
    (with-test-db [conn test-schema]
      (transact-enum-idents! conn)
      (let [account-id (random-uuid)]
        ;; Create with pending status
        (d/transact! conn [{:account/id     account-id
                            :account/name   "Status User"
                            :account/email  "status@test.com"
                            :account/status :status/pending}])
        
        ;; Update to active
        (let [eid (d/q '[:find ?e . :in $ ?id :where [?e :account/id ?id]]
                       (d/db conn)
                       account-id)]
          (d/transact! conn [[:db/add eid :account/status :status/active]]))
        
        ;; Verify
        (let [result (d/pull (d/db conn)
                             [{:account/status [:db/ident]}]
                             [:account/id account-id])
              status (get-in result [:account/status :db/ident])]
          (is (= :status/active status)))))))

(deftest update-many-enum-values
  (testing "adding and removing values from many-cardinality enum"
    (with-test-db [conn test-schema]
      (transact-enum-idents! conn)
      (let [account-id (random-uuid)]
        ;; Create with read permission only
        (d/transact! conn [{:account/id          account-id
                            :account/name        "Permissions User"
                            :account/email       "perms@test.com"
                            :account/permissions [:account.permissions/read]}])
        
        ;; Verify initial permissions
        (let [initial (d/pull (d/db conn)
                              [{:account/permissions [:db/ident]}]
                              [:account/id account-id])
              initial-perms (set (map :db/ident (:account/permissions initial)))]
          (is (= 1 (count initial-perms)))
          (is (contains? initial-perms :account.permissions/read)))
        
        ;; Add write permission
        (let [eid (d/q '[:find ?e . :in $ ?id :where [?e :account/id ?id]]
                       (d/db conn)
                       account-id)]
          (d/transact! conn [[:db/add eid :account/permissions :account.permissions/write]]))
        
        ;; Verify both permissions
        (let [with-write (d/pull (d/db conn)
                                 [{:account/permissions [:db/ident]}]
                                 [:account/id account-id])
              perms (set (map :db/ident (:account/permissions with-write)))]
          (is (= 2 (count perms)))
          (is (contains? perms :account.permissions/read))
          (is (contains? perms :account.permissions/write)))
        
        ;; Remove read permission
        (let [eid (d/q '[:find ?e . :in $ ?id :where [?e :account/id ?id]]
                       (d/db conn)
                       account-id)]
          (d/transact! conn [[:db/retract eid :account/permissions :account.permissions/read]]))
        
        ;; Verify only write remains
        (let [final (d/pull (d/db conn)
                            [{:account/permissions [:db/ident]}]
                            [:account/id account-id])
              final-perms (set (map :db/ident (:account/permissions final)))]
          (is (= 1 (count final-perms)))
          (is (contains? final-perms :account.permissions/write)))))))

(deftest delete-with-enum
  (testing "deleting an entity with enum values"
    (with-test-db [conn test-schema]
      (transact-enum-idents! conn)
      (let [account-id (random-uuid)]
        ;; Create account with enums
        (d/transact! conn [{:account/id          account-id
                            :account/name        "To Delete"
                            :account/email       "delete@test.com"
                            :account/role        :account.role/guest
                            :account/status      :status/inactive
                            :account/permissions [:account.permissions/read]}])
        
        ;; Verify it exists
        (is (= 1 (d/q '[:find (count ?e) . :where [?e :account/name "To Delete"]]
                      (d/db conn))))
        
        ;; Delete
        (let [eid (d/q '[:find ?e . :in $ ?id :where [?e :account/id ?id]]
                       (d/db conn)
                       account-id)]
          (d/transact! conn [[:db/retractEntity eid]]))
        
        ;; Verify deletion
        (is (zero? (or (d/q '[:find (count ?e) . :where [?e :account/name "To Delete"]]
                            (d/db conn))
                       0)))))))

;; ================================================================================
;; Query Pattern Tests
;; ================================================================================

(deftest query-by-enum-value
  (testing "querying entities by enum value"
    (with-test-db [conn test-schema]
      (transact-enum-idents! conn)
      ;; Create multiple accounts with different roles
      (d/transact! conn [{:account/id (random-uuid)
                          :account/name "Admin 1"
                          :account/email "admin1@test.com"
                          :account/role :account.role/admin}
                         {:account/id (random-uuid)
                          :account/name "Admin 2"
                          :account/email "admin2@test.com"
                          :account/role :account.role/admin}
                         {:account/id (random-uuid)
                          :account/name "User 1"
                          :account/email "user1@test.com"
                          :account/role :account.role/user}
                         {:account/id (random-uuid)
                          :account/name "Guest 1"
                          :account/email "guest1@test.com"
                          :account/role :account.role/guest}])
      
      ;; Find all admins
      (let [admins (d/q '[:find [?name ...]
                          :where
                          [?e :account/name ?name]
                          [?e :account/role ?role]
                          [?role :db/ident :account.role/admin]]
                        (d/db conn))]
        (is (= 2 (count admins)))
        (is (contains? (set admins) "Admin 1"))
        (is (contains? (set admins) "Admin 2")))
      
      ;; Find all users (non-admin, non-guest)
      (let [users (d/q '[:find [?name ...]
                         :where
                         [?e :account/name ?name]
                         [?e :account/role ?role]
                         [?role :db/ident :account.role/user]]
                       (d/db conn))]
        (is (= 1 (count users)))
        (is (contains? (set users) "User 1"))))))

(deftest query-by-many-enum-values
  (testing "querying entities by many-cardinality enum values"
    (with-test-db [conn test-schema]
      (transact-enum-idents! conn)
      ;; Create accounts with different permission sets
      (d/transact! conn [{:account/id (random-uuid)
                          :account/name "Read Only"
                          :account/email "readonly@test.com"
                          :account/permissions [:account.permissions/read]}
                         {:account/id (random-uuid)
                          :account/name "Read Write"
                          :account/email "readwrite@test.com"
                          :account/permissions [:account.permissions/read
                                                :account.permissions/write]}
                         {:account/id (random-uuid)
                          :account/name "All Perms"
                          :account/email "allperms@test.com"
                          :account/permissions [:account.permissions/read
                                                :account.permissions/write
                                                :account.permissions/execute]}])
      
      ;; Find all accounts with write permission
      (let [writers (d/q '[:find [?name ...]
                           :where
                           [?e :account/name ?name]
                           [?e :account/permissions ?perm]
                           [?perm :db/ident :account.permissions/write]]
                         (d/db conn))]
        (is (= 2 (count writers)))
        (is (contains? (set writers) "Read Write"))
        (is (contains? (set writers) "All Perms")))
      
      ;; Find accounts with execute permission
      (let [executors (d/q '[:find [?name ...]
                             :where
                             [?e :account/name ?name]
                             [?e :account/permissions ?perm]
                             [?perm :db/ident :account.permissions/execute]]
                           (d/db conn))]
        (is (= 1 (count executors)))
        (is (contains? (set executors) "All Perms"))))))

(deftest complex-enum-queries
  (testing "complex queries with multiple enum conditions"
    (with-test-db [conn test-schema]
      (transact-enum-idents! conn)
      ;; Create accounts with various combinations
      (d/transact! conn [{:account/id (random-uuid)
                          :account/name "Active Admin"
                          :account/email "activeadmin@test.com"
                          :account/role :account.role/admin
                          :account/status :status/active}
                         {:account/id (random-uuid)
                          :account/name "Pending Admin"
                          :account/email "pendingadmin@test.com"
                          :account/role :account.role/admin
                          :account/status :status/pending}
                         {:account/id (random-uuid)
                          :account/name "Active User"
                          :account/email "activeuser@test.com"
                          :account/role :account.role/user
                          :account/status :status/active}])
      
      ;; Find active admins only
      (let [active-admins (d/q '[:find [?name ...]
                                 :where
                                 [?e :account/name ?name]
                                 [?e :account/role ?role]
                                 [?role :db/ident :account.role/admin]
                                 [?e :account/status ?status]
                                 [?status :db/ident :status/active]]
                               (d/db conn))]
        (is (= 1 (count active-admins)))
        (is (contains? (set active-admins) "Active Admin"))))))

(comment
  ;; Run all enum tests
  (clojure.test/run-tests 'app.enum-test)
  
  ;; Run specific tests
  (clojure.test/test-var #'enum-schema-generation)
  (clojure.test/test-var #'create-with-single-enum)
  (clojure.test/test-var #'create-with-many-enum)
  (clojure.test/test-var #'update-enum-value)
  (clojure.test/test-var #'query-by-enum-value))
