(ns app.server.middleware
  "RAD form middleware for save and delete operations."
  (:require
   [us.whitford.fulcro.rad.database-adapters.datalevin :as dl]
   [com.fulcrologic.rad.middleware.save-middleware :as save-mw]))

(def save-middleware
  "RAD save middleware for Datalevin"
  (-> (dl/wrap-datalevin-save {:default-schema :main})
      (save-mw/wrap-rewrite-values)))

(def delete-middleware
  "RAD delete middleware for Datalevin"
  (dl/wrap-datalevin-delete {:default-schema :main}))
