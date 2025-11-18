(ns app.server.http-server
  "HTTP server component using mount."
  (:require
    [mount.core :refer [defstate]]
    [org.httpkit.server :refer [run-server]]
    [taoensso.timbre :as log]
    [app.server.config :refer [config]]
    [app.server.ring-middleware :refer [middleware]]))

(defstate http-server
  :start
  (let [cfg     (get config :org.httpkit.server/config)
        stop-fn (run-server middleware cfg)]
    (log/info "Starting webserver with config" cfg)
    {:stop stop-fn})
  :stop
  (let [{:keys [stop]} http-server]
    (when stop
      (stop))))

(defn -main [& args]
  (mount.core/start-with-args {:config "config/prod.edn"}))
