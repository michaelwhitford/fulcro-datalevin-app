(ns development
  (:require
   [clojure.tools.namespace.repl :as tools-ns :refer [set-refresh-dirs]]
   [mount.core :as mount]
   [taoensso.timbre :as log]
   [us.whitford.fulcro.rad.database-adapters.datalevin :as dl]
   [datalevin.core :as d]
   [app.server.database :refer [connections]]
   [app.server.ring-middleware]
   [app.server.http-server]
   [app.model :as model]))

;; Prevent tools-ns from finding source in other places, such as resources
(set-refresh-dirs "src/main" "src/dev")

(defn clear-and-seed!
  "Clear all data and reseed the database. Useful for development."
  []
  (let [conn (:main connections)]
    (when conn
      (log/info "Clearing existing database data...")
      (d/clear conn)
      (log/info "SEEDING sample data.")
      (dl/seed-database! conn
                         [{:account/id       (random-uuid)
                           :account/name     "Alice Johnson"
                           :account/email    "alice@example.com"
                           :account/active?  true
                           :account/created-at (java.util.Date.)}
                          {:account/id       (random-uuid)
                           :account/name     "Bob Smith"
                           :account/email    "bob@example.com"
                           :account/active?  true
                           :account/created-at (java.util.Date.)}
                          {:category/id    (random-uuid)
                           :category/label "Electronics"}
                          {:category/id    (random-uuid)
                           :category/label "Books"}
                          {:item/id          (random-uuid)
                           :item/name        "Wireless Mouse"
                           :item/description "Ergonomic wireless mouse"
                           :item/price       29.99
                           :item/in-stock    50}
                          {:item/id          (random-uuid)
                           :item/name        "Programming Clojure"
                           :item/description "Learn Clojure programming"
                           :item/price       49.95
                           :item/in-stock    25}])
      (log/info "Seeding complete."))))

(defn seed! []
  (let [conn (:main connections)]
    (when conn
      ;; Only seed if the database is empty
      ;; Check by querying for any entity
      (let [has-data? (seq (d/q '[:find ?e :where [?e _ _]] (d/db conn)))]
        (if has-data?
          (log/info "Database already contains data. Skipping seed.")
          (do
            (log/info "SEEDING sample data.")
            (dl/seed-database! conn
                               [{:account/id       (random-uuid)
                                 :account/name     "Alice Johnson"
                                 :account/email    "alice@example.com"
                                 :account/active?  true
                                 :account/created-at (java.util.Date.)}
                                {:account/id       (random-uuid)
                                 :account/name     "Bob Smith"
                                 :account/email    "bob@example.com"
                                 :account/active?  true
                                 :account/created-at (java.util.Date.)}
                                {:category/id    (random-uuid)
                                 :category/label "Electronics"}
                                {:category/id    (random-uuid)
                                 :category/label "Books"}
                                {:item/id          (random-uuid)
                                 :item/name        "Wireless Mouse"
                                 :item/description "Ergonomic wireless mouse"
                                 :item/price       29.99
                                 :item/in-stock    50}
                                {:item/id          (random-uuid)
                                 :item/name        "Programming Clojure"
                                 :item/description "Learn Clojure programming"
                                 :item/price       49.95
                                 :item/in-stock    25}])
            (log/info "Seeding complete.")))))))


(defn start []
  (mount/start-with-args {:config "config/dev.edn"})
  (seed!)
  :ok)

(defn stop
  "Stop the server."
  []
  (mount/stop))

(defn fast-restart
  "Stop and restart the server without code reload."
  []
  (stop)
  (start))

(defn restart
  "Stop, refresh code, and restart the server."
  []
  (stop)
  (tools-ns/refresh :after 'development/start))

(comment
  (start)
  (stop)
  (restart)

  ;; Clear and reseed the database
  (clear-and-seed!)

  ;; Query database directly
  (d/q '[:find ?name ?email ?e
         :where
         [?e :account/name ?name]
         [?e :account/email ?email]]
       (d/db (:main connections)))

  ;; Get database metrics
  (dl/get-metrics))
