(ns us.whitford.fulcro.rad.database-adapters.datalevin-common
  "Common utilities for Datalevin database adapter, following Datomic adapter pattern."
  (:require
    [us.whitford.fulcro.rad.database-adapters.datalevin-options :as dlo]))

(defn wrap-env
  "Middleware to add database connections to the pathom environment.
   
   `connection-map-fn` - A function that takes env and returns a map of {:db-key connection}
   `db-fn` - A function that takes a connection and returns a database value (e.g. datalevin.core/db)"
  [handler connection-map-fn db-fn]
  (fn [env]
    (let [connections (connection-map-fn env)
          databases   (reduce-kv
                        (fn [m k conn]
                          (assoc m k (db-fn conn)))
                        {}
                        connections)]
      (handler (assoc env
                 ::dlo/connections connections
                 ::dlo/databases databases)))))
