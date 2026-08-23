(ns app.fulltext-search-integration-test
  "Phase 3 integration: the adapter-generated :<entity>/search resolvers driven
   through the app's REAL RAD Pathom 3 parser, using the app's own model —
   exactly the EQL a RAD report with ro/source-attribute :account/search and a
   :query control produces. Also sanity-checks the AccountSearchList report
   component's wiring (cljc, so it loads on the JVM)."
  {:clj-kondo/config '{:linters {:unresolved-symbol {:exclude [(app.test-utils/with-test-db [conn])]}}}}
  (:require
   [app.model :as model]
   [app.model.account]
   [app.server.middleware :as middleware]
   [app.test-utils :refer [with-test-db]]
   [app.ui.root :as root]
   [clojure.test :refer [deftest testing is]]
   [com.fulcrologic.fulcro.components :as comp]
   [com.fulcrologic.rad.attributes :as attr]
   [com.fulcrologic.rad.form :as form]
   [com.fulcrologic.rad.pathom3 :as pathom3]
   [com.fulcrologic.rad.report-options :as ro]
   [datalevin.core :as d]
   [us.whitford.fulcro.rad.database-adapters.datalevin :as dl]
   [us.whitford.fulcro.rad.database-adapters.datalevin-options :as dlo]))

(def test-schema
  (dl/automatic-schema :main model/all-attributes))

(defn- make-test-parser
  "The app's real parser wiring (mirrors app.server.parser) bound to `conn`."
  [conn]
  (let [automatic-resolvers (dl/generate-resolvers model/all-attributes :main)
        env-middleware      (-> (attr/wrap-env model/all-attributes)
                                (form/wrap-env middleware/save-middleware
                                               middleware/delete-middleware)
                                (dl/wrap-env (fn [_env] {:main conn})))]
    (pathom3/new-processor {} env-middleware [] [automatic-resolvers])))

(deftest account-search-through-real-parser
  (testing "report-shaped EQL with a :query param returns relevance-ordered rows"
    (with-test-db [conn test-schema]
      (let [id1 (random-uuid) id2 (random-uuid) id3 (random-uuid)]
        (d/transact! conn
                     [{:account/id id1 :account/name "Fox Consulting"
                       :account/email "info@fox.example"}
                      {:account/id id2 :account/name "Fox Fox and Fox LLP"
                       :account/email "legal@foxes.example"}
                      {:account/id id3 :account/name "Badger Industries"
                       :account/email "hi@badger.example"}])
        (let [process (make-test-parser conn)
              result  (process {}
                               [(list {:account/search [:account/id :account/name]}
                                      {:query "fox"})])]
          (is (= [{:account/id id2 :account/name "Fox Fox and Fox LLP"}
                  {:account/id id1 :account/name "Fox Consulting"}]
                 (:account/search result))
              "relevance order, columns auto-filled, badger excluded"))))))

(deftest account-search-pagination-params
  (testing ":top caps results through the real parser"
    (with-test-db [conn test-schema]
      (d/transact! conn
                   [{:account/id (random-uuid) :account/name "Fox One"
                     :account/email "one@fox.example"}
                    {:account/id (random-uuid) :account/name "Fox Two"
                     :account/email "two@fox.example"}])
      (let [process (make-test-parser conn)
            result  (process {}
                             [(list {:account/search [:account/id]}
                                    {:query "fox" :top 1})])]
        (is (= 1 (count (:account/search result))))))))

(deftest account-search-empty-query
  (testing "a report with an empty search control renders an empty list"
    (with-test-db [conn test-schema]
      (let [process (make-test-parser conn)
            result  (process {} [(list {:account/search [:account/id]} {})])]
        (is (= [] (:account/search result)))))))

(deftest person-search-native-id-through-real-parser
  (testing "search on a native-id entity returns eid-idents; fields fill via id-resolver"
    (with-test-db [conn test-schema]
      (d/transact! conn [{:person/name "Ada" :person/email "ada@x.example"
                          :person/bio "pioneering zanzibar analyst"}
                         {:person/name "Bob" :person/email "bob@x.example"
                          :person/bio "ordinary paperwork"}])
      (let [process (make-test-parser conn)
            result  (process {}
                             [(list {:person/search [:person/id :person/name]}
                                    {:query "zanzibar"})])
            rows    (:person/search result)]
        (is (= 1 (count rows)) "only the matching person")
        (is (= "Ada" (:person/name (first rows)))
            "fields auto-filled through the native-id id-resolver")
        (is (pos-int? (:person/id (first rows)))
            "native-id ident carries the raw eid")))))

;; ================================================================================
;; RC2: ::dlo/score — the full-text relevance metric surfaced through the real
;; parser. Opt-in at query time: requesting ::dlo/score in the join makes the
;; generated search resolver attach it (descending). Omitting it returns the
;; byte-identical pre-RC2 shape (regression-guarded below).
;; ================================================================================

(deftest account-search-score-metric-through-real-parser
  (testing ":account/search attaches ::dlo/score (desc) when the join requests it"
    (with-test-db [conn test-schema]
      (let [id1 (random-uuid) id2 (random-uuid) id3 (random-uuid)]
        (d/transact! conn
                     [{:account/id id1 :account/name "Fox Consulting"
                       :account/email "info@fox.example"}
                      {:account/id id2 :account/name "Fox Fox and Fox LLP"
                       :account/email "legal@foxes.example"}
                      {:account/id id3 :account/name "Badger Industries"
                       :account/email "hi@badger.example"}])
        (let [process (make-test-parser conn)
              result  (process {}
                               [(list {:account/search [:account/id :account/name
                                                        ::dlo/score]}
                                      {:query "fox"})])
              rows    (:account/search result)]
          (is (= [id2 id1] (mapv :account/id rows))
              "relevance order preserved; badger excluded")
          (is (= ["Fox Fox and Fox LLP" "Fox Consulting"] (mapv :account/name rows))
              "fields still auto-fill through the id-resolver")
          (is (every? #(number? (::dlo/score %)) rows)
              "each ident carries its ::dlo/score metric")
          (is (apply >= (map ::dlo/score rows))
              "scores are descending (most relevant first)"))))))

(deftest account-search-score-is-opt-in-regression
  (testing "omitting ::dlo/score yields the byte-identical pre-RC2 shape"
    (with-test-db [conn test-schema]
      ;; Same 3-record seed as account-search-through-real-parser so the
      ;; relevance order is deterministic ([id2 id1], badger excluded).
      (let [id1 (random-uuid) id2 (random-uuid) id3 (random-uuid)]
        (d/transact! conn
                     [{:account/id id1 :account/name "Fox Consulting"
                       :account/email "info@fox.example"}
                      {:account/id id2 :account/name "Fox Fox and Fox LLP"
                       :account/email "legal@foxes.example"}
                      {:account/id id3 :account/name "Badger Industries"
                       :account/email "hi@badger.example"}])
        (let [process (make-test-parser conn)
              without (process {}
                               [(list {:account/search [:account/id :account/name]}
                                      {:query "fox"})])]
          (is (= [{:account/id id2 :account/name "Fox Fox and Fox LLP"}
                  {:account/id id1 :account/name "Fox Consulting"}]
                 (:account/search without))
              "byte-identical to the pre-RC2 shape (no ::dlo/score key)")
          (is (not-any? #(contains? % ::dlo/score) (:account/search without))
              "the metric is pruned by the planner when unrequested"))))))

(deftest person-search-score-metric-native-id-through-real-parser
  (testing "native-id :person/search also carries ::dlo/score (desc)"
    (with-test-db [conn test-schema]
      (d/transact! conn [{:person/name "Ada" :person/email "ada@x.example"
                          :person/bio "zanzibar zanzibar analyst zanzibar"}
                         {:person/name "Cy" :person/email "cy@x.example"
                          :person/bio "one zanzibar mention"}
                         {:person/name "Bob" :person/email "bob@x.example"
                          :person/bio "ordinary paperwork"}])
      (let [process (make-test-parser conn)
            result  (process {}
                             [(list {:person/search [:person/id :person/name
                                                     ::dlo/score]}
                                    {:query "zanzibar"})])
            rows    (:person/search result)]
        (is (= 2 (count rows)) "both zanzibar persons match; Bob excluded")
        (is (= ["Ada" "Cy"] (mapv :person/name rows))
            "the zanzibar-heavy bio ranks first")
        (is (every? #(pos-int? (:person/id %)) rows)
            "native-id idents carry raw entity ids")
        (is (every? #(number? (::dlo/score %)) rows)
            "the metric rides the native-id ident maps too")
        (is (apply >= (map ::dlo/score rows))
            "descending score on the native-id leg")))))

(deftest account-search-report-wiring
  (testing "AccountSearchList report targets the generated resolver with a :query control"
    (let [opts (comp/component-options root/AccountSearchList)]
      (is (= :account/search (ro/source-attribute opts))
          "report source-attribute is the generated search key")
      (is (contains? (ro/controls opts) :query)
          "the search box control exists; its value is sent as the :query param"))))
