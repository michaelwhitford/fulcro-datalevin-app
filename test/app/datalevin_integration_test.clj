(ns app.datalevin-integration-test
  "Integration tests for fulcro-rad-datalevin plugin."
  (:require
    [clojure.test :refer [deftest testing is use-fixtures]]
    [us.whitford.fulcro.rad.database-adapters.datalevin :as dl]
    [us.whitford.fulcro.rad.database-adapters.datalevin-options :as dlo]
    [com.fulcrologic.rad.attributes :as attr]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [app.model :as model]
    [app.model.account :as account]
    [app.model.category :as category]
    [app.model.item :as item]
    [datalevin.core :as d]))

;; Reset metrics before each test
(use-fixtures :each
  (fn [test-fn]
    (dl/reset-metrics!)
    (test-fn)))

(deftest schema-generation-test
  (testing "Schema generation from RAD attributes"
    (let [schema (dl/automatic-schema :main model/all-attributes)]
      (is (map? schema))
      (is (contains? schema :account/id))
      (is (contains? schema :account/name))
      (is (contains? schema :category/id))
      (is (contains? schema :item/id))
      
      ;; Check identity attributes have unique constraint
      (is (= :db.unique/identity (get-in schema [:account/id :db/unique])))
      (is (= :db.type/uuid (get-in schema [:account/id :db/valueType])))
      
      ;; Check value types
      (is (= :db.type/string (get-in schema [:account/name :db/valueType])))
      (is (= :db.type/boolean (get-in schema [:account/active? :db/valueType])))
      (is (= :db.type/instant (get-in schema [:account/created-at :db/valueType])))
      (is (= :db.type/double (get-in schema [:item/price :db/valueType])))
      (is (= :db.type/long (get-in schema [:item/in-stock :db/valueType])))
      
      ;; Check custom schema overrides
      (is (= :db.unique/value (get-in schema [:account/email :db/unique]))))))

(deftest temp-database-test
  (testing "Temporary database creation and cleanup"
    (dl/with-temp-database [conn :main model/all-attributes]
      (is (some? conn))
      (let [db (d/db conn)]
        (is (some? db))
        ;; Database should be empty
        (is (empty? (d/q '[:find ?e :where [?e :account/id _]] db)))))))

(deftest seed-database-test
  (testing "Seeding database with initial data"
    (dl/with-temp-database [conn :main model/all-attributes]
      (let [account-id (random-uuid)
            category-id (random-uuid)]
        (dl/seed-database! conn
          [{:account/id    account-id
            :account/name  "Test User"
            :account/email "test@example.com"}
           {:category/id    category-id
            :category/label "Test Category"}])
        
        (let [db (d/db conn)]
          ;; Verify data was inserted
          (is (= #{[account-id]}
                 (d/q '[:find ?id :where [_ :account/id ?id]] db)))
          (is (= #{[category-id]}
                 (d/q '[:find ?id :where [_ :category/id ?id]] db))))))))

(deftest get-by-ids-test
  (testing "Fetching multiple entities by ID"
    (dl/with-temp-database [conn :main model/all-attributes]
      (let [id1 (random-uuid)
            id2 (random-uuid)
            id3 (random-uuid)]
        (dl/seed-database! conn
          [{:account/id    id1
            :account/name  "Alice"
            :account/email "alice@example.com"}
           {:account/id    id2
            :account/name  "Bob"
            :account/email "bob@example.com"}
           {:account/id    id3
            :account/name  "Charlie"
            :account/email "charlie@example.com"}])
        
        (let [db     (d/db conn)
              result (dl/get-by-ids db :account/id [id1 id3] [:account/name :account/email])]
          (is (map? result))
          (is (= 2 (count result)))
          (is (= "Alice" (get-in result [id1 :account/name])))
          (is (= "Charlie" (get-in result [id3 :account/name])))
          (is (nil? (get result id2))))))))

(deftest delta-to-transaction-test
  (testing "Converting RAD deltas to Datalevin transactions"
    (let [account-id (random-uuid)
          delta {[:account/id account-id]
                 {:account/name  {:before "Old Name" :after "New Name"}
                  :account/email {:before nil :after "new@example.com"}}}
          txn   (dl/delta->txn delta)]
      (is (vector? txn))
      (is (= 1 (count txn)))
      (let [entry (first txn)]
        (is (= [:account/id account-id] (:db/id entry)))
        (is (= "New Name" (:account/name entry)))
        (is (= "new@example.com" (:account/email entry)))))))

(deftest save-middleware-test
  (testing "Save middleware transacts deltas"
    (dl/with-temp-database [conn :main model/all-attributes]
      (let [account-id     (random-uuid)
            key->attribute (attr/attribute-map model/all-attributes)
            delta          {[:account/id account-id]
                            {:account/name    {:before nil :after "New Account"}
                             :account/email   {:before nil :after "new@example.com"}
                             :account/active? {:before nil :after true}}}
            env            {::attr/key->attribute key->attribute
                            ::dlo/connections     {:main conn}
                            ::form/delta          delta}
            save-fn        (dl/wrap-datalevin-save {:default-schema :main})
            result         ((save-fn identity) env)]
        
        ;; Check result
        (is (map? result))
        
        ;; Verify data was saved
        (let [db       (d/db conn)
              entities (d/q '[:find ?e ?name ?email
                              :where
                              [?e :account/id ?id]
                              [?e :account/name ?name]
                              [?e :account/email ?email]]
                            db)]
          (is (= 1 (count entities)))
          (is (= "New Account" (second (first entities))))
          (is (= "new@example.com" (nth (first entities) 2))))))))

(deftest delete-middleware-test
  (testing "Delete middleware removes entities"
    (dl/with-temp-database [conn :main model/all-attributes]
      (let [account-id (random-uuid)]
        ;; Create an account first
        (dl/seed-database! conn
          [{:account/id    account-id
            :account/name  "To Delete"
            :account/email "delete@example.com"}])
        
        ;; Verify it exists
        (let [db (d/db conn)]
          (is (= 1 (count (d/q '[:find ?e :where [?e :account/id _]] db)))))
        
        ;; Delete it
        (let [key->attribute (attr/attribute-map model/all-attributes)
              env            {::attr/key->attribute key->attribute
                              ::dlo/connections     {:main conn}
                              ::form/delete-params  [[:account/id account-id]]}
              delete-fn      (dl/wrap-datalevin-delete {:default-schema :main})]
          ((delete-fn identity) env))
        
        ;; Verify it's gone
        (let [db (d/db conn)]
          (is (empty? (d/q '[:find ?e :where [?e :account/id _]] db))))))))

(deftest resolver-generation-test
  (testing "Auto-generated resolvers"
    (let [resolvers (dl/generate-resolvers model/all-attributes)]
      (is (seq resolvers))
      ;; Should have resolvers for identity attributes
      (let [resolver-names (set (map #(-> % meta :com.wsscode.pathom3.connect.operation/op-name str) 
                                     resolvers))]
        (is (some #(re-find #"account" %) resolver-names))
        (is (some #(re-find #"category" %) resolver-names))
        (is (some #(re-find #"item" %) resolver-names))))))

(deftest metrics-test
  (testing "Database metrics tracking"
    (dl/reset-metrics!)
    (dl/with-temp-database [conn :main model/all-attributes]
      (let [initial-metrics (dl/get-metrics)]
        (is (= 0 (:transaction-count initial-metrics)))
        
        ;; Perform a transaction
        (dl/seed-database! conn
          [{:account/id    (random-uuid)
            :account/name  "Test"
            :account/email "test@example.com"}])
        
        (let [after-metrics (dl/get-metrics)]
          (is (= 1 (:transaction-count after-metrics)))
          (is (> (:total-transaction-time-ms after-metrics) 0)))))))

(deftest mock-resolver-env-test
  (testing "Mock environment for resolver testing"
    (dl/with-temp-database [conn :main model/all-attributes]
      (let [env (dl/mock-resolver-env {:main conn})]
        (is (contains? env ::dlo/connections))
        (is (contains? env ::dlo/databases))
        (is (= conn (get-in env [::dlo/connections :main])))
        (is (some? (get-in env [::dlo/databases :main])))))))

(deftest tempid-handling-test
  (testing "Handling of Fulcro tempids in save middleware"
    (dl/with-temp-database [conn :main model/all-attributes]
      (let [temp-id        (tempid/tempid)
            key->attribute (attr/attribute-map model/all-attributes)
            delta          {[:account/id temp-id]
                            {:account/name    {:before nil :after "New Temp Account"}
                             :account/email   {:before nil :after "temp@example.com"}
                             :account/active? {:before nil :after true}}}
            env            {::attr/key->attribute key->attribute
                            ::dlo/connections     {:main conn}
                            ::form/delta          delta}
            save-fn        (dl/wrap-datalevin-save {:default-schema :main})
            result         ((save-fn identity) env)]
        
        ;; Should have tempid remapping in result
        (is (contains? result :tempids))
        (is (contains? (:tempids result) temp-id))
        (is (uuid? (get-in result [:tempids temp-id])))
        
        ;; Verify data was saved with the real UUID
        (let [db (d/db conn)]
          (is (= 1 (count (d/q '[:find ?e :where [?e :account/id _]] db)))))))))

(deftest error-handling-test
  (testing "Error handling for missing connections"
    (let [key->attribute (attr/attribute-map model/all-attributes)
          delta          {[:account/id (random-uuid)]
                          {:account/name {:before nil :after "Test"}}}
          env            {::attr/key->attribute key->attribute
                          ::dlo/connections     {} ; No connections!
                          ::form/delta          delta}
          save-fn        (dl/wrap-datalevin-save {:default-schema :main})]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"No database connection"
                            ((save-fn identity) env))))))
