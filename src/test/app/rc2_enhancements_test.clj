(ns app.rc2-enhancements-test
  "Integration coverage for the fulcro-rad-datalevin 1.0.0-RC2 additive surface,
   proven end-to-end in this app (the adapter's proving ground):

   1. `::dlo/all-ids-key` — renaming the generated enumeration resolver so a
      consumer-authored `:<ns>/all` (git, fs, external service) can coexist with
      the index enumeration. Proven through a REAL RAD Pathom 3 processor.
   2. `::dlo/all-resolver?` gating — the flag alone gates; the key alone names.
   3. The raw ranked-search primitives `dl/fulltext-search` (and, where a `:vec`
      attribute exists, `dl/vec-search`) — the non-Pathom layer the generated
      resolvers ride, returning `[[eid metric] ...]` in metric order.

   These use a small self-contained `:page` model so the rename does NOT disturb
   the app's real model (whose `:<ns>/all` the rest of the suite depends on)."
  {:clj-kondo/config '{:linters {:unresolved-symbol {:exclude [(app.test-utils/with-test-db [conn])]}}}}
  (:require
   [app.model :as model]
   [app.test-utils :refer [with-test-db]]
   [clojure.test :refer [deftest testing is]]
   [com.fulcrologic.rad.attributes :as attr]
   [com.fulcrologic.rad.pathom3 :as pathom3]
   [datalevin.core :as d]
   [us.whitford.fulcro.rad.database-adapters.datalevin :as dl]
   [us.whitford.fulcro.rad.database-adapters.datalevin-options :as dlo]))

;; ================================================================================
;; Small self-contained :page model (schema :main so it can share a test db)
;; ================================================================================

(def page-id
  {::attr/qualified-key :page/id
   ::attr/type          :uuid
   ::attr/schema        :main
   ::attr/identity?     true
   ;; Opt in to enumeration AND rename the output key. A consumer could then
   ;; author their own :page/all with a different truth-source.
   ::dlo/all-resolver?  true
   ::dlo/all-ids-key    :page/index-all})

(def page-title
  {::attr/qualified-key :page/title
   ::attr/type          :string
   ::attr/schema        :main
   ::attr/identities    #{:page/id}})

(def page-attributes [page-id page-title])

(defn- make-page-parser
  "A real RAD Pathom 3 processor bound to `conn`, wired exactly like the app's
   parser but over the small :page model."
  [conn]
  (let [resolvers      (dl/generate-resolvers page-attributes :main)
        env-middleware (-> (attr/wrap-env page-attributes)
                           (dl/wrap-env (fn [_env] {:main conn})))]
    (pathom3/new-processor {} env-middleware [] [resolvers])))

(def page-schema
  (dl/automatic-schema :main page-attributes))

;; ================================================================================
;; ::dlo/all-ids-key rename — end-to-end through a real parser
;; ================================================================================

(deftest all-ids-key-renames-enumeration-resolver-end-to-end
  (testing "the renamed :page/index-all resolves through the parser; :page/all absent"
    (with-test-db [conn page-schema]
      (let [id1 (random-uuid) id2 (random-uuid)]
        (d/transact! conn [{:page/id id1 :page/title "Alpha"}
                           {:page/id id2 :page/title "Beta"}])
        (let [process (make-page-parser conn)
              renamed (process {} [{:page/index-all [:page/id :page/title]}])
              rows    (:page/index-all renamed)]
          (is (contains? renamed :page/index-all)
              "the renamed enumeration key resolves")
          (is (= #{"Alpha" "Beta"} (set (map :page/title rows)))
              "it enumerates every page through the id-resolver")
          (is (= #{id1 id2} (set (map :page/id rows)))
              "each row carries its identity"))
        (let [process (make-page-parser conn)
              default (process {} [{:page/all [:page/id :page/title]}])]
          (is (not (contains? default :page/all))
              ":page/all is left to the consumer — the adapter emits only the renamed key"))))))

(deftest all-ids-key-rename-topology
  (testing "generated resolvers expose the renamed key, never :page/all"
    (let [resolvers (dl/generate-resolvers page-attributes :main)
          syms      (set (map :com.wsscode.pathom.connect/sym resolvers))]
      (is (contains? syms 'page-index-all-resolver)
          "resolver sym derives from the renamed key")
      (is (not (contains? syms 'page-all-resolver))
          "no resolver under the default :page/all name"))))

(deftest all-resolver-flag-gates-generation
  (testing "the flag gates: ::dlo/all-ids-key without ::dlo/all-resolver? emits nothing"
    (let [attrs     [(dissoc page-id ::dlo/all-resolver?) page-title]
          resolvers (dl/generate-resolvers attrs :main)
          syms      (set (map :com.wsscode.pathom.connect/sym resolvers))]
      (is (not (contains? syms 'page-index-all-resolver))
          "no enumeration resolver when the flag is absent")
      (is (not (contains? syms 'page-all-resolver))
          "and none under the default name either")
      (is (= 1 (count resolvers))
          "only the collision-safe id-resolver remains"))))

;; ================================================================================
;; Raw ranked-search primitives — the non-Pathom layer, against the app's db
;; ================================================================================

(def app-schema
  (dl/automatic-schema :main model/all-attributes))

(deftest fulltext-search-primitive-descending
  (testing "dl/fulltext-search returns [[eid score] ...] descending against the app db"
    (with-test-db [conn app-schema]
      ;; :account/name is ::dlo/fulltext? true → domain \"account\"
      (d/transact! conn
                   [{:account/id (random-uuid) :account/name "Fox Consulting"
                     :account/email "info@fox.example"}
                    {:account/id (random-uuid) :account/name "Fox Fox and Fox LLP"
                     :account/email "legal@foxes.example"}
                    {:account/id (random-uuid) :account/name "Badger Industries"
                     :account/email "hi@badger.example"}])
      (let [db     (d/db conn)
            scored (dl/fulltext-search db "fox" {:domains ["account"]})]
        (is (= 2 (count scored)) "both fox accounts match; badger excluded")
        (is (every? (fn [[eid score]] (and (pos-int? eid) (number? score))) scored)
            "each result is an [eid score] pair")
        (is (apply >= (map second scored))
            "scores are descending (most relevant first)")
        (let [names (mapv #(:account/name (d/pull db '[:account/name] (first %))) scored)]
          (is (= #{"Fox Consulting" "Fox Fox and Fox LLP"} (set names))
              "eids pull back the matched accounts"))))))

(deftest fulltext-search-primitive-top-cap
  (testing ":top caps the raw primitive's result count"
    (with-test-db [conn app-schema]
      (d/transact! conn
                   [{:account/id (random-uuid) :account/name "Fox One"
                     :account/email "one@fox.example"}
                    {:account/id (random-uuid) :account/name "Fox Two"
                     :account/email "two@fox.example"}])
      (let [db     (d/db conn)
            scored (dl/fulltext-search db "fox" {:domains ["account"] :top 1})]
        (is (= 1 (count scored)) ":top 1 yields a single [eid score] pair")))))

(deftest fulltext-search-primitive-native-id-domain
  (testing "dl/fulltext-search works over a native-id entity's domain (person)"
    (with-test-db [conn app-schema]
      ;; :person/bio is ::dlo/fulltext? true → domain \"person\"
      (d/transact! conn [{:person/name "Ada" :person/email "ada@x.example"
                          :person/bio "pioneering zanzibar analyst"}
                         {:person/name "Bob" :person/email "bob@x.example"
                          :person/bio "ordinary paperwork"}])
      (let [db     (d/db conn)
            scored (dl/fulltext-search db "zanzibar" {:domains ["person"]})]
        (is (= 1 (count scored)) "only Ada's bio mentions zanzibar")
        (is (= "Ada" (:person/name (d/pull db '[:person/name] (ffirst scored))))
            "the raw eid pulls back the matching person")))))

;; dl/vec-search: the app's demo model declares no :vec attribute, so there is no
;; seeded vector data to search here. The raw ascending-distance primitive is
;; covered upstream (vector_search_test.clj); adding a :vec entity to the demo
;; model is tracked separately. The metric-carrying :<ns>/similar resolver path
;; would surface via ::dlo/distance once a :vec attribute is introduced.

(comment
  ;; Sanity: confirm the primitive is on the facade
  (require '[app.rc2-enhancements-test])
  (deref #'dl/fulltext-search)
  (deref #'dl/vec-search))
