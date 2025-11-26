(ns app.server.middleware
  "RAD form middleware for save and delete operations."
  (:require
   [us.whitford.fulcro.rad.database-adapters.datalevin :as dl]
   [com.fulcrologic.rad.middleware.save-middleware :as save-mw]
   [com.fulcrologic.rad.form :as form]
   [clojure.test :as t]))

(defn wrap-exceptions-as-form-errors
  ([handler]
   (fn [pathom-env]
     (try (let [handler-result (handler pathom-env)]
            handler-result)
          (catch Throwable t
            {::form/errors [{:message (str "Unexpected error saving form: " (ex-message t))}]})))))

(def save-middleware
  "RAD save middleware for Datalevin.
   This is a HANDLER FUNCTION that processes save operations."
  (-> (dl/wrap-datalevin-save)
      (wrap-exceptions-as-form-errors)
      (save-mw/wrap-rewrite-values)))

(def delete-middleware
  "RAD delete middleware for Datalevin.
   This is a HANDLER FUNCTION that processes delete operations."
  (dl/wrap-datalevin-delete))
