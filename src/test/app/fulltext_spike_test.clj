(ns app.fulltext-spike-test
  "Phase 0 spike for fulcro-rad-datalevin full-text search (design doc:
   fulcro-rad-datalevin mementum/knowledge/design/full-text-search.md).

   Proves the two Phase 0 risks BEFORE the generator is built:

   RISK #1 — Pathom param plumbing: a `{:params {:query ...}}` EQL join reaches
   a resolver as `(:query-params env)` through RAD's REAL Pathom 3 processor.
   The echo resolver is written as a Pathom-2-shape map — the exact shape the
   adapter's generate-resolvers emits — so this also proves the converted-
   resolver path sees the params.

   Low-level d/fulltext — `:db/fulltext true` schema + transact indexes
   synchronously; the `(fulltext $ ?q opts)` Datalog predicate returns matches,
   `:top` limits, and relevance order is observable via :refs+scores."
  (:require
   [app.model :as model]
   [app.test-utils :refer [with-test-db]]
   [clojure.test :refer [deftest testing is]]
   [com.fulcrologic.rad.attributes :as attr]
   [com.fulcrologic.rad.pathom3 :as pathom3]
   [datalevin.core :as d]))

;; ================================================================================
;; RISK #1 — params reach the resolver as (:query-params env)
;; ================================================================================

(def search-echo-resolver
  "Hand-written stand-in for the future :account/search resolver, in the
   Pathom-2 map shape the adapter's generator emits. Echoes what it sees in
   env so the test can assert exactly what arrived."
  {:com.wsscode.pathom.connect/sym 'app.fulltext-spike-test/account-search-echo
   :com.wsscode.pathom.connect/output [{:account/search [:spike/query :spike/limit :spike/offset]}]
   :com.wsscode.pathom.connect/resolve
   (fn [env _input]
     (let [{:keys [query limit offset]} (:query-params env)]
       {:account/search [{:spike/query  query
                          :spike/limit  limit
                          :spike/offset offset}]}))})

(defn- make-echo-processor []
  (pathom3/new-processor {}
                         (attr/wrap-env model/all-attributes)
                         []
                         [[search-echo-resolver]]))

(deftest params-round-trip-to-resolver
  (testing "EQL join params arrive in the resolver via (:query-params env) [RISK #1]"
    (let [process (make-echo-processor)
          result  (process {}
                           [(list {:account/search [:spike/query :spike/limit :spike/offset]}
                                  {:query "red fox" :limit 20 :offset 5})])
          echoed  (first (:account/search result))]
      (is (= "red fox" (:spike/query echoed))
          "the :query param reaches the resolver via (:query-params env)")
      (is (= 20 (:spike/limit echoed))
          "the :limit param reaches the resolver")
      (is (= 5 (:spike/offset echoed))
          "the :offset param reaches the resolver"))))

;; ================================================================================
;; Low-level d/fulltext round-trip (default "datalevin" domain, sync indexing)
;; ================================================================================

(def fulltext-schema
  {:doc/title {:db/valueType :db.type/string}
   :doc/text  {:db/valueType :db.type/string
               :db/fulltext  true}})

(defn- seed-docs! [conn]
  (d/transact! conn
               [{:doc/title "one"   :doc/text "the quick red fox jumps"}
                {:doc/title "two"   :doc/text "fox fox fox everywhere a fox"}
                {:doc/title "three" :doc/text "lazy brown dog sleeps"}]))

(deftest fulltext-round-trip
  (testing "transacted :db/fulltext values are searchable via the fulltext predicate"
    (with-test-db [conn fulltext-schema]
      (seed-docs! conn)
      (let [db     (d/db conn)
            eids   (d/q '[:find [?e ...]
                          :in $ ?q
                          :where [(fulltext $ ?q) [[?e _ _]]]]
                        db "fox")
            titles (set (map #(:doc/title (d/pull db '[:doc/title] %)) eids))]
        (is (= #{"one" "two"} titles)
            "both fox docs match; the dog doc does not")))))

(deftest fulltext-top-option
  (testing ":top limits the number of fulltext results"
    (with-test-db [conn fulltext-schema]
      (seed-docs! conn)
      (let [eids (d/q '[:find [?e ...]
                        :in $ ?q ?opts
                        :where [(fulltext $ ?q ?opts) [[?e _ _]]]]
                      (d/db conn) "fox" {:top 1})]
        (is (= 1 (count eids))
            ":top 1 returns exactly one result")))))

(deftest fulltext-relevance-order
  (testing "relevance order requires :refs+scores + explicit sort"
    ;; SPIKE FINDING (invalidates the original design sketch): Datalog's set
    ;; semantics do NOT preserve the search engine's rank order — a plain
    ;; `:find [?e ...]` returned ["one" "two"] while the score order was
    ;; ["two" "one"]. The generated search resolver MUST query with
    ;; {:display :refs+scores} and sort by score descending itself.
    (with-test-db [conn fulltext-schema]
      (seed-docs! conn)
      (let [db      (d/db conn)
            scored  (d/q '[:find ?e ?s
                           :in $ ?q ?opts
                           :where [(fulltext $ ?q ?opts) [[?e _ _ ?s]]]]
                         db "fox" {:display :refs+scores})
            by-score (mapv first (sort-by second > scored))
            title    #(:doc/title (d/pull db '[:doc/title] %))
            titles-by-score (mapv title by-score)]
        (is (= ["two" "one"] titles-by-score)
            "sorting by :refs+scores score yields relevance order (fox-heavy doc first)")
        (is (every? number? (map second scored))
            "scores are numeric and usable for explicit sorting")))))
