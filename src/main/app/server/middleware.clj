(ns app.server.middleware
  "RAD form middleware for save and delete operations."
  (:require
   [us.whitford.fulcro.rad.database-adapters.datalevin :as dl]
   [com.fulcrologic.rad.middleware.save-middleware :as save-mw]
   [com.fulcrologic.rad.form :as form]
   [com.fulcrologic.rad.attributes :as attr]
   [com.fulcrologic.rad.attributes-options :as ao]
   [clojure.string :as str]
   [taoensso.timbre :as log]
   [clojure.test :as t]))

(defn wrap-exceptions-as-form-errors
  ([handler]
   (fn [pathom-env]
     (try (let [handler-result (handler pathom-env)]
            handler-result)
          (catch Throwable t
            {::form/errors [{:message (str "Unexpected error saving form: " (ex-message t))}]})))))

(defn validate-delta
  "Validates all attributes in a delta map according to their attribute definitions.
   Returns a vector of error maps if validation fails, or nil if all valid."
  [delta all-attributes]
  (let [errors (reduce
                (fn [errors [k v]]
                  (if-let [attribute (some #(when (= k (::attr/qualified-key %)) %) all-attributes)]
                    (if (attr/valid-value? attribute v {} k)
                      errors
                      (conj errors {:message (str "Invalid value for " k ": "
                                                 (cond
                                                   (and (string? v) (str/blank? v))
                                                   "cannot be blank"
                                                   
                                                   (nil? v)
                                                   "is required"
                                                   
                                                   :else
                                                   "value is invalid"))}))
                    errors))
                []
                delta)]
    (when (seq errors)
      errors)))

(defn wrap-attribute-validation
  "Middleware wrapper that validates all attributes in the delta before saving.
   If validation fails, returns errors without calling the handler."
  [handler]
  (fn [pathom-env]
    (let [form-params (::form/params pathom-env)
          delta (:delta form-params)
          attr-map (::attr/key->attribute pathom-env)
          all-attributes (when attr-map (vals attr-map))]
      (if-let [errors (and all-attributes delta (validate-delta delta all-attributes))]
        {::form/errors errors}
        (handler pathom-env)))))

(def save-middleware
  "RAD save middleware for Datalevin.
   This is a HANDLER FUNCTION that processes save operations.
   
   Middleware execution order (outermost to innermost):
   1. wrap-attribute-validation - validates required fields before processing
   2. wrap-rewrite-values - applies value transformations
   3. wrap-exceptions-as-form-errors - catches exceptions
   4. wrap-datalevin-save - performs the actual database save"
  (-> (dl/wrap-datalevin-save)
      (wrap-exceptions-as-form-errors)
      (save-mw/wrap-rewrite-values)
      (wrap-attribute-validation)))

(def delete-middleware
  "RAD delete middleware for Datalevin.
   This is a HANDLER FUNCTION that processes delete operations."
  (dl/wrap-datalevin-delete))
