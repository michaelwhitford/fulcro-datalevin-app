(ns app.server.middleware
  "RAD form middleware for save and delete operations."
  (:require
   [us.whitford.fulcro.rad.database-adapters.datalevin :as dl]
   [com.fulcrologic.rad.middleware.save-middleware :as save-mw]))

(def save-middleware
  "RAD save middleware for Datalevin.
   This is a HANDLER FUNCTION that processes save operations."
  (-> (fn [env] {})  ; Base handler - returns empty result map
      ((dl/wrap-datalevin-save {:default-schema :main}))
      (save-mw/wrap-rewrite-values)))

(def delete-middleware
  "RAD delete middleware for Datalevin.
   This is a HANDLER FUNCTION that processes delete operations."
  ((dl/wrap-datalevin-delete {:default-schema :main})
   (fn [env] {})))  ; Base handler - returns empty result map
