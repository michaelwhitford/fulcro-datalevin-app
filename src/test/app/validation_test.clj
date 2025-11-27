(ns app.validation-test
  "Tests to verify server-side validation is enforced during save operations."
  {:clj-kondo/config '{:linters {:unresolved-symbol {:exclude [(app.test-utils/with-test-db [conn])]}}}}
  (:require
   [clojure.test :refer [deftest testing is]]
   [datalevin.core :as d]
   [us.whitford.fulcro.rad.database-adapters.datalevin :as dl]
   [us.whitford.fulcro.rad.database-adapters.datalevin-common :as datalevin-common]
   [app.test-utils :refer [with-test-db]]
   [app.model :as model]
   [app.model.account :as account]
   [com.fulcrologic.rad.attributes :as attr]
   [com.fulcrologic.rad.form :as form]
   [com.fulcrologic.rad.pathom3 :as pathom3]
   [com.fulcrologic.rad.resolvers :as resolvers]
   [com.fulcrologic.rad.attributes-options :as ao]
   [app.server.middleware :as middleware]))

(def test-schema
  "Schema for validation tests"
  (dl/automatic-schema :main model/all-attributes))

(defn make-test-parser
  "Create a parser configured for testing"
  [conn]
  (let [env-middleware (-> (attr/wrap-env model/all-attributes)
                           (form/wrap-env middleware/save-middleware middleware/delete-middleware)
                           (datalevin-common/wrap-env
                            (fn [env] {:main conn})
                            d/db))]
    (pathom3/new-processor {} env-middleware []
                           [(vec (concat (resolvers/generate-resolvers model/all-attributes)
                                         (dl/generate-resolvers model/all-attributes :main)))
                            form/resolvers])))

(deftest attribute-validation-detects-invalid-values
  (testing "attr/valid-value? correctly identifies invalid values for required fields"
    (is (= true (attr/valid-value? account/name "Valid Name" {} :account/name))
        "Valid name should pass validation")
    
    (is (= false (attr/valid-value? account/name "" {} :account/name))
        "Empty string should fail validation for required field")
    
    (is (= false (attr/valid-value? account/name nil {} :account/name))
        "Nil should fail validation for required field")
    
    (is (= false (attr/valid-value? account/name "   " {} :account/name))
        "Whitespace-only string should fail validation for required field")))

(deftest save-middleware-should-reject-blank-required-fields
  (testing "Save middleware should prevent saving entities with blank required fields"
    (with-test-db [conn test-schema]
      (let [parser (make-test-parser conn)
            account-id (random-uuid)]
        
        ;; First, create a valid account
        (dl/seed-database! conn
                           [{:account/id account-id
                             :account/name "Original Name"
                             :account/email "test@example.com"}])
        
        ;; Verify it was created
        (is (= "Original Name"
               (d/q '[:find ?name .
                      :in $ ?id
                      :where
                      [?e :account/id ?id]
                      [?e :account/name ?name]]
                    (d/db conn)
                    account-id)))
        
        ;; Now try to update with a blank name - THIS SHOULD FAIL
        (let [result (parser {}
                             [(list 'com.fulcrologic.rad.form/save-form
                                    {:delta {[:account/id account-id] 
                                             {:account/name {:before "Original Name" :after ""}
                                              :account/email {:before "test@example.com" :after "test@example.com"}}}})])
              errors (get-in result ['com.fulcrologic.rad.form/save-form ::form/errors])]
          
          ;; The save should return errors
          (is (seq errors)
              "Save should return validation errors for blank required field")
          
          ;; The database should NOT be updated
          (is (= "Original Name"
                 (d/q '[:find ?name .
                        :in $ ?id
                        :where
                        [?e :account/id ?id]
                        [?e :account/name ?name]]
                      (d/db conn)
                      account-id))
              "Database should not be updated when validation fails"))))))

(deftest save-middleware-should-reject-blank-name-on-create
  (testing "Save middleware should prevent creating new entities with blank required fields"
    (with-test-db [conn test-schema]
      (let [parser (make-test-parser conn)
            account-id (random-uuid)
            ;; Try to create an account with blank name - THIS SHOULD FAIL
            result (parser {}
                           [(list 'com.fulcrologic.rad.form/save-form
                                  {:delta {[:account/id account-id]
                                           {:account/id {:before nil :after account-id}
                                            :account/name {:before nil :after ""}
                                            :account/email {:before nil :after "create@example.com"}}}})])]
        ;; The save should return errors
        (is (seq (get-in result ['com.fulcrologic.rad.form/save-form ::form/errors]))
            "Save should return validation errors for blank required field on create")
        ;; The entity should NOT be created
        (is (nil? (d/q '[:find ?name .
                         :in $ ?id
                         :where
                         [?e :account/id ?id]
                         [?e :account/name ?name]]
                       (d/db conn)
                       account-id))
            "Entity should not be created when validation fails")))))

(deftest save-middleware-should-allow-valid-values
  (testing "Save middleware should allow saving entities with valid required field values"
    (with-test-db [conn test-schema]
      (let [parser (make-test-parser conn)
            account-id (random-uuid)
            ;; Create an account with valid data - THIS SHOULD SUCCEED
            result (parser {}
                           [(list 'com.fulcrologic.rad.form/save-form
                                  {:delta {[:account/id account-id]
                                           {:account/id {:before nil :after account-id}
                                            :account/name {:before nil :after "Valid Name"}
                                            :account/email {:before nil :after "valid@example.com"}}}})])]
        ;; The save should NOT return errors
        (is (nil? (get-in result ['com.fulcrologic.rad.form/save-form ::form/errors]))
            "Save should not return errors for valid data")))))

(deftest save-middleware-should-reject-whitespace-only-name
  (testing "Save middleware should treat whitespace-only strings as invalid for required fields"
    (with-test-db [conn test-schema]
      (let [parser (make-test-parser conn)
            account-id (random-uuid)
            ;; Try to create with whitespace-only name - THIS SHOULD FAIL
            result (parser {}
                           [(list 'com.fulcrologic.rad.form/save-form
                                  {:delta {[:account/id account-id]
                                           {:account/id {:before nil :after account-id}
                                            :account/name {:before nil :after "   "}
                                            :account/email {:before nil :after "whitespace@example.com"}}}})])]
        ;; The save should return errors
        (is (seq (get-in result ['com.fulcrologic.rad.form/save-form ::form/errors]))
            "Save should return validation errors for whitespace-only required field")
        ;; The entity should NOT be created
        (is (nil? (d/q '[:find ?name .
                         :in $ ?id
                         :where
                         [?e :account/id ?id]
                         [?e :account/name ?name]]
                       (d/db conn)
                       account-id))
            "Entity should not be created when validation fails")))))

(comment
  ;; Run tests in REPL
  (clojure.test/run-tests 'app.validation-test)
  
  ;; Run specific tests
  (clojure.test/test-var #'attribute-validation-detects-invalid-values)
  (clojure.test/test-var #'save-middleware-should-reject-blank-required-fields)
  (clojure.test/test-var #'save-middleware-should-reject-blank-name-on-create))
