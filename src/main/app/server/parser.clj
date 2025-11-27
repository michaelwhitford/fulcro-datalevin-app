(ns app.server.parser
  "Pathom3 parser configuration with Datalevin resolvers."
  (:require
   [mount.core :refer [defstate]]
   [com.fulcrologic.rad.attributes :as attr]
   [com.fulcrologic.rad.form :as form]
   [com.fulcrologic.rad.resolvers :as res]
   [com.fulcrologic.rad.report :as report]
   [com.fulcrologic.rad.pathom3 :as pathom3]
   [us.whitford.fulcro.rad.database-adapters.datalevin :as dl]
   [us.whitford.fulcro.rad.database-adapters.datalevin-options :as dlo]
   [us.whitford.fulcro.rad.database-adapters.datalevin-common :as common]
   [app.model :as model]
   [app.server.config :refer [config]]
   [app.server.database :refer [connections]]
   [app.server.middleware :as middleware]
   [datalevin.core :as d]))

;; Generate automatic resolvers from the database adapter we are testing with this app
(def automatic-resolvers (vec (concat (res/generate-resolvers model/all-attributes)
                                      (dl/generate-resolvers model/all-attributes :main))))

(defstate parser
  :start
  (let [env-middleware (-> (attr/wrap-env model/all-attributes)
                           (form/wrap-env middleware/save-middleware middleware/delete-middleware)
                           (common/wrap-env (fn [env] {:main (:main connections)}) d/db))]
    (pathom3/new-processor config env-middleware []
                           [automatic-resolvers
                            form/resolvers])))

(comment
  (tap> {:from :repl :automatic-resolvers automatic-resolvers}))
