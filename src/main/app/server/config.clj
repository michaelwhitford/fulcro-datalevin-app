(ns app.server.config
  "Configuration management with mount."
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [mount.core :refer [args defstate]]
    [taoensso.timbre :as log]))

(defstate config
  :start
  (let [{:keys [config] :or {config "config/dev.edn"}} (args)
        path (or config "config/dev.edn")]
    (log/info "Loading config from" path)
    (if-let [resource (io/resource path)]
      (edn/read-string (slurp resource))
      (do
        (log/error "Config file not found:" path)
        {}))))
