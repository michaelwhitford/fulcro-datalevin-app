(ns app.schema-test
  "Tests for automatic schema generation from RAD attributes."
  (:require
   [clojure.test :refer [deftest testing is]]
   [us.whitford.fulcro.rad.database-adapters.datalevin :as dl]
   [app.model :as model]))

(deftest automatic-schema-generation
  (testing "Schema is correctly generated from RAD attributes"
    (let [schema (dl/automatic-schema :main model/all-attributes)]
      
      (testing "Account attributes"
        (is (contains? schema :account/id))
        (is (= :db.type/uuid (get-in schema [:account/id :db/valueType])))
        (is (= :db.unique/identity (get-in schema [:account/id :db/unique])))
        
        (is (contains? schema :account/name))
        (is (= :db.type/string (get-in schema [:account/name :db/valueType])))
        
        (is (contains? schema :account/email))
        (is (= :db.type/string (get-in schema [:account/email :db/valueType])))
        (is (= :db.unique/value (get-in schema [:account/email :db/unique])))
        
        (is (contains? schema :account/active?))
        (is (= :db.type/boolean (get-in schema [:account/active? :db/valueType])))
        
        (is (contains? schema :account/created-at))
        (is (= :db.type/instant (get-in schema [:account/created-at :db/valueType]))))
      
      (testing "Category attributes"
        (is (contains? schema :category/id))
        (is (= :db.type/uuid (get-in schema [:category/id :db/valueType])))
        (is (= :db.unique/identity (get-in schema [:category/id :db/unique])))
        
        (is (contains? schema :category/label))
        (is (= :db.type/string (get-in schema [:category/label :db/valueType])))
        (is (= :db.unique/value (get-in schema [:category/label :db/unique]))))
      
      (testing "Item attributes"
        (is (contains? schema :item/id))
        (is (= :db.type/uuid (get-in schema [:item/id :db/valueType])))
        (is (= :db.unique/identity (get-in schema [:item/id :db/unique])))
        
        (is (contains? schema :item/name))
        (is (= :db.type/string (get-in schema [:item/name :db/valueType])))
        
        (is (contains? schema :item/description))
        (is (= :db.type/string (get-in schema [:item/description :db/valueType])))
        
        (is (contains? schema :item/price))
        (is (= :db.type/double (get-in schema [:item/price :db/valueType])))
        
        (is (contains? schema :item/in-stock))
        (is (= :db.type/long (get-in schema [:item/in-stock :db/valueType])))
        
        (is (contains? schema :item/category))
        (is (= :db.type/ref (get-in schema [:item/category :db/valueType])))))))

(deftest schema-unique-constraints
  (testing "Unique constraints are properly configured"
    (let [schema (dl/automatic-schema :main model/all-attributes)
          identity-attrs [:account/id :category/id :item/id]
          unique-value-attrs [:account/email :category/label]]
      
      (testing "Identity attributes have :db.unique/identity"
        (doseq [attr identity-attrs]
          (is (= :db.unique/identity (get-in schema [attr :db/unique]))
              (str attr " should have :db.unique/identity"))))
      
      (testing "Unique value attributes have :db.unique/value"
        (doseq [attr unique-value-attrs]
          (is (= :db.unique/value (get-in schema [attr :db/unique]))
              (str attr " should have :db.unique/value")))))))

(deftest schema-reference-types
  (testing "Reference attributes are correctly typed"
    (let [schema (dl/automatic-schema :main model/all-attributes)]
      (is (= :db.type/ref (get-in schema [:item/category :db/valueType]))
          ":item/category should be a reference type"))))

(deftest schema-value-types
  (testing "All value types are correctly mapped"
    (let [schema (dl/automatic-schema :main model/all-attributes)
          type-mappings {:account/name :db.type/string
                         :account/email :db.type/string
                         :account/active? :db.type/boolean
                         :account/created-at :db.type/instant
                         :account/id :db.type/uuid
                         :category/label :db.type/string
                         :category/id :db.type/uuid
                         :item/name :db.type/string
                         :item/description :db.type/string
                         :item/price :db.type/double
                         :item/in-stock :db.type/long
                         :item/id :db.type/uuid
                         :item/category :db.type/ref}]
      
      (doseq [[attr expected-type] type-mappings]
        (is (= expected-type (get-in schema [attr :db/valueType]))
            (str attr " should have type " expected-type))))))

(comment
  ;; Run tests in REPL
  (clojure.test/run-tests 'app.schema-test)
  
  ;; Inspect generated schema
  (require '[us.whitford.fulcro.rad.database-adapters.datalevin :as dl])
  (require '[app.model :as model])
  (clojure.pprint/pprint (dl/automatic-schema :main model/all-attributes)))
