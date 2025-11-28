(ns app.test-utils
  "Utilities for testing Datalevin operations."
  {:clj-kondo/config '{:lint-as {app.test-utils/with-test-db clojure.core/let}}}
  (:require
   [clojure.java.io :as io]
   [datalevin.core :as d]
   [com.fulcrologic.rad.attributes :as attr]
   [us.whitford.fulcro.rad.database-adapters.datalevin-options :as dlo]))

(defn create-temp-db-dir
  "Create a temporary directory for test database."
  []
  (let [temp-dir (io/file (System/getProperty "java.io.tmpdir")
                           (str "datalevin-test-" (System/currentTimeMillis)))]
    (.mkdirs temp-dir)
    (.getPath temp-dir)))

(defn delete-db-dir
  "Recursively delete database directory."
  [db-dir]
  (let [dir (io/file db-dir)]
    (when (.exists dir)
      (doseq [file (reverse (file-seq dir))]
        (io/delete-file file true)))))

(defn- enumerated-values
  "Generate schema entries for enumerated values.
   Based on the implementation in start-databases.clj"
  [attributes]
  (mapcat
   (fn [{::attr/keys [qualified-key type enumerated-values] :as a}]
     (when (= :enum type)
       (let [enum-nspc (str (namespace qualified-key) "." (name qualified-key))]
         (keep (fn [v]
                 (cond
                   (map? v) v
                   (qualified-keyword? v) {:db/ident v}
                   :else (let [enum-ident (keyword enum-nspc (name v))]
                           {:db/ident enum-ident})))
               enumerated-values))))
   attributes))

(defn with-test-db*
  "Execute function f with a temporary test database connection.
   Schema should be a map of Datalevin schema definitions.
   Optionally transacts enum values if attributes are provided.
   Automatically cleans up the database after execution."
  ([schema f]
   (with-test-db* schema nil f))
  ([schema attributes f]
   (let [db-dir (create-temp-db-dir)
         conn (d/get-conn db-dir schema)]
     (try
       ;; Transact enum values if attributes are provided
       (when attributes
         (let [enum-txn (enumerated-values attributes)]
           (when (seq enum-txn)
             (try
               (d/transact! conn enum-txn)
               (catch Exception _e
                 ;; Enum values may already exist, ignore
                 nil)))))
       (f conn)
       (finally
         (d/close conn)
         (delete-db-dir db-dir))))))

(defmacro with-test-db
  "Execute body with a temporary test database connection bound to conn-sym.
   Optionally accepts :attributes option to transact enum values.
   
   Example:
   (with-test-db [conn {:test/id {:db/valueType :db.type/uuid}}]
     (d/transact! conn [{:test/id (random-uuid)}])
     (d/q '[:find ?e :where [?e :test/id _]] (d/db conn)))
   
   With attributes:
   (with-test-db [conn schema :attributes model/all-attributes]
     ...)"
  [[conn-sym schema & {:keys [attributes]}] & body]
  `(with-test-db* ~schema ~attributes
     (fn [~conn-sym]
       ~@body)))

(defn seed-test-data!
  "Seed test database with sample entities."
  [conn entities]
  (doseq [batch (partition-all 100 entities)]
    (d/transact! conn batch)))
