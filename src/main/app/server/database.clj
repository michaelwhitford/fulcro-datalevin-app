(ns app.server.database
  "Database component for managing Datalevin connections."
  (:require
    [mount.core :refer [defstate]]
    [us.whitford.fulcro.rad.database-adapters.datalevin :as dl]
    [app.model :as model]
    [taoensso.timbre :as log]))

(def db-path "data/main-db")

(defn start-connections []
  (log/info "Starting Datalevin database at" db-path)
  (let [conn (dl/start-database!
               {:path       db-path
                :schema     :main
                :attributes model/all-attributes
                :auto-schema? true})]
    {:main conn}))

(defn stop-connections [connections]
  (log/info "Stopping Datalevin database connections")
  (doseq [[schema conn] connections]
    (log/info "Closing connection for schema" schema)
    (dl/stop-database! conn)))

(defstate connections
  :start (start-connections)
  :stop (stop-connections connections))
