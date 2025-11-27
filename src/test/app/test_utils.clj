(ns app.test-utils
  "Utilities for testing Datalevin operations."
  (:require
   [clojure.java.io :as io]
   [datalevin.core :as d]))

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

(defn with-test-db*
  "Execute function f with a temporary test database connection.
   Schema should be a map of Datalevin schema definitions.
   Automatically cleans up the database after execution."
  [schema f]
  (let [db-dir (create-temp-db-dir)
        conn (d/get-conn db-dir schema)]
    (try
      (f conn)
      (finally
        (d/close conn)
        (delete-db-dir db-dir)))))

(defmacro with-test-db
  "Execute body with a temporary test database connection bound to conn-sym.
   
   Example:
   (with-test-db [conn {:test/id {:db/valueType :db.type/uuid}}]
     (d/transact! conn [{:test/id (random-uuid)}])
     (d/q '[:find ?e :where [?e :test/id _]] (d/db conn)))"
  [[conn-sym schema] & body]
  `(with-test-db* ~schema
     (fn [~conn-sym]
       ~@body)))

(defn seed-test-data!
  "Seed test database with sample entities."
  [conn entities]
  (doseq [batch (partition-all 100 entities)]
    (d/transact! conn batch)))
