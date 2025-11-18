(ns app.client
  "Frontend Fulcro application for testing RAD datalevin plugin."
  (:require
   [com.fulcrologic.fulcro.algorithms.timbre-support :refer [console-appender prefix-output-fn]]
   [com.fulcrologic.fulcro.algorithms.tx-processing.batched-processing :as btxn]
   [com.fulcrologic.fulcro.application :as app]
   [com.fulcrologic.fulcro.components :as comp]
   [com.fulcrologic.fulcro.mutations :as m]
   [com.fulcrologic.fulcro.networking.http-remote :as net]
   [com.fulcrologic.fulcro.data-fetch :as df]
   [com.fulcrologic.rad.application :as rad-app]
   [com.fulcrologic.rad.rendering.semantic-ui.semantic-ui-controls :as sui]
   [com.fulcrologic.rad.report :as report]
   [com.fulcrologic.rad.type-support.date-time :as datetime]
   [com.fulcrologic.devtools.common.target :refer [ido]]
   [fulcro.inspect.tool :as it]
   [taoensso.timbre :as log]
   [app.application :refer [SPA]]
   [app.ui.root :as root]))

(defn setup-RAD [app]
  (rad-app/install-ui-controls! app sui/all-controls)
  (ido (it/add-fulcro-inspect! app))
  (report/install-formatter! app :boolean :affirmation (fn [_ value] (if value "yes" "no"))))

(defonce app (-> (rad-app/fulcro-rad-app
                  (let [token (when-not (undefined? js/fulcro_network_csrf_token)
                                js/fulcro_network_csrf_token)]
                    (-> (rad-app/fulcro-rad-app
                         {:remotes {:remote (net/fulcro-http-remote {:url                "/api"
                                                                     :request-middleware (rad-app/secured-request-middleware {:csrf-token token})})}})
                        (btxn/with-batched-reads))))))

(defn refresh []
  (log/info "Reinstalling controls")
  (setup-RAD app)
  (app/force-root-render! app))

(defn ^:export init []
  ;; makes js console logging a bit nicer
  (log/merge-config! {:output-fn prefix-output-fn
                      :appenders {:console (console-appender)}})
  (log/info "Starting App")
  #_(reset! SPA app)
  ;; default time zone
  (datetime/set-timezone! "America/Phoenix")
  (app/set-root! app root/Root {:initialize-state? true})
  (setup-RAD app)
  #_(df/load! app :root root/Root)
  (app/mount! app root/Root "app" {:initalize-state? false})
  (log/info "App mounted and initialized"))
