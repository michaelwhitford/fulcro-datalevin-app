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
   [com.fulcrologic.statecharts.integration.fulcro.ui-routes :as uir]
   [fulcro.inspect.tool :as it]
   [taoensso.timbre :as log]
   [app.application :refer [SPA]]
   [app.ui.root :as root]
   [com.fulcrologic.statecharts.integration.fulcro :as scf]))

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
  (uir/update-chart! app root/application-chart)
  (app/force-root-render! app))

(m/defmutation application-ready [_]
  (action [{:keys [state]}]
          (swap! state assoc :ui/ready? true)))

(defn ^:export init []
  ;; makes js console logging a bit nicer
  (log/merge-config! {:output-fn prefix-output-fn
                      :appenders {:console (console-appender)}})
  (log/info "Starting App")
  (reset! SPA app)
  ;; default time zone
  (datetime/set-timezone! "America/Phoenix")
  (app/set-root! app root/Root {:initialize-state? true})
  (setup-RAD app)
  (scf/install-fulcro-statecharts! app)
  (uir/start-routing! app root/application-chart)
  (uir/route-to! app root/LandingPage)
  #_(df/load! app :account-list root/AccountList)
  #_(df/load! app :all-accounts root/AccountList)
  (app/mount! app root/Root "app" {:initalize-state? false})
  ; enable :ui/ready?
  (comp/transact! app [(application-ready {})])
  (log/info "App mounted and initialized"))
