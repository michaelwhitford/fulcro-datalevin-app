(ns app.resolver-test
  "Tests for Pathom3 resolver generation from RAD attributes."
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.wsscode.pathom3.connect.operation :as pco]
   [us.whitford.fulcro.rad.database-adapters.datalevin :as dl]
   [app.model :as model]))

(deftest resolver-generation
  (testing "Resolvers are generated for all identity attributes"
    (let [resolvers (dl/generate-resolvers model/all-attributes :main)]
      
      (testing "Correct number of resolvers"
        ;; 3 ID resolvers + 3 all-ids resolvers = 6 total
        (is (= 6 (count resolvers))))
      
      (testing "All resolvers are valid Pathom3 resolvers"
        (doseq [resolver resolvers]
          (is (record? resolver)
              "Each resolver should be a Pathom3 Resolver record")
          (is (some? (pco/operation-config resolver))
              "Each resolver should have operation config"))))))

(deftest id-resolver-configuration
  (testing "ID resolvers are properly configured"
    (let [resolvers (dl/generate-resolvers model/all-attributes :main)
          id-resolvers (filter #(re-find #"\.id-resolver$" 
                                          (str (::pco/op-name (pco/operation-config %))))
                               resolvers)]
      
      (is (= 3 (count id-resolvers)) "Should have 3 ID resolvers")
      
      (testing "ID resolvers support batch operations"
        (doseq [resolver id-resolvers]
          (let [config (pco/operation-config resolver)]
            (is (true? (::pco/batch? config))
                (str "Resolver " (::pco/op-name config) " should support batching"))))))))

(deftest all-ids-resolver-configuration
  (testing "All-IDs resolvers are properly configured"
    (let [resolvers (dl/generate-resolvers model/all-attributes :main)
          all-ids-resolvers (filter #(re-find #"\.all-.*-resolver$" 
                                               (str (::pco/op-name (pco/operation-config %))))
                                    resolvers)]
      
      (is (= 3 (count all-ids-resolvers)) "Should have 3 all-IDs resolvers")
      
      (testing "All-IDs resolvers have no required input"
        (doseq [resolver all-ids-resolvers]
          (let [config (pco/operation-config resolver)
                input (::pco/input config)]
            (is (or (nil? input) (empty? input))
                (str "Resolver " (::pco/op-name config) " should have no required input"))))))))

(deftest account-resolver-outputs
  (testing "Account ID resolver provides correct outputs"
    (let [resolvers (dl/generate-resolvers model/all-attributes :main)
          account-resolver (first (filter #(= 'account.id-resolver 
                                               (::pco/op-name (pco/operation-config %)))
                                          resolvers))]
      
      (is (some? account-resolver) "Account ID resolver should exist")
      
      (when account-resolver
        (let [config (pco/operation-config account-resolver)
              output (::pco/output config)]
          (is (some #{:account/name} output))
          (is (some #{:account/email} output))
          (is (some #{:account/active?} output))
          (is (some #{:account/created-at} output)))))))

(deftest category-resolver-outputs
  (testing "Category ID resolver provides correct outputs"
    (let [resolvers (dl/generate-resolvers model/all-attributes :main)
          category-resolver (first (filter #(= 'category.id-resolver 
                                                (::pco/op-name (pco/operation-config %)))
                                           resolvers))]
      
      (is (some? category-resolver) "Category ID resolver should exist")
      
      (when category-resolver
        (let [config (pco/operation-config category-resolver)
              output (::pco/output config)]
          (is (some #{:category/label} output)))))))

(deftest item-resolver-outputs
  (testing "Item ID resolver provides correct outputs including references"
    (let [resolvers (dl/generate-resolvers model/all-attributes :main)
          item-resolver (first (filter #(= 'item.id-resolver 
                                           (::pco/op-name (pco/operation-config %)))
                                       resolvers))]
      
      (is (some? item-resolver) "Item ID resolver should exist")
      
      (when item-resolver
        (let [config (pco/operation-config item-resolver)
              output (::pco/output config)]
          (is (some #{:item/name} output))
          (is (some #{:item/description} output))
          (is (some #{:item/price} output))
          (is (some #{:item/in-stock} output))
          
          ;; Check for category reference
          (is (some #(and (map? %) 
                          (contains? % :item/category)
                          (= [:category/id] (:item/category %)))
                    output)
              "Should include :item/category reference with :category/id"))))))

(deftest resolver-inputs
  (testing "ID resolvers require correct identity inputs"
    (let [resolvers (dl/generate-resolvers model/all-attributes :main)
          account-resolver (first (filter #(= 'account.id-resolver 
                                               (::pco/op-name (pco/operation-config %)))
                                          resolvers))
          category-resolver (first (filter #(= 'category.id-resolver 
                                                (::pco/op-name (pco/operation-config %)))
                                           resolvers))
          item-resolver (first (filter #(= 'item.id-resolver 
                                           (::pco/op-name (pco/operation-config %)))
                                       resolvers))]
      
      (when account-resolver
        (let [input (::pco/input (pco/operation-config account-resolver))]
          (is (some #{:account/id} input))))
      
      (when category-resolver
        (let [input (::pco/input (pco/operation-config category-resolver))]
          (is (some #{:category/id} input))))
      
      (when item-resolver
        (let [input (::pco/input (pco/operation-config item-resolver))]
          (is (some #{:item/id} input)))))))

(comment
  ;; Run tests in REPL
  (clojure.test/run-tests 'app.resolver-test)
  
  ;; Inspect resolvers
  (require '[us.whitford.fulcro.rad.database-adapters.datalevin :as dl])
  (require '[app.model :as model])
  (require '[com.wsscode.pathom3.connect.operation :as pco])
  
  (def resolvers (dl/generate-resolvers model/all-attributes :main))
  (count resolvers)
  
  (doseq [r resolvers]
    (println (::pco/op-name (pco/operation-config r)))
    (clojure.pprint/pprint (pco/operation-config r)))
  )
