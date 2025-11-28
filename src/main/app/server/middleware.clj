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
     (try
       (let [handler-result (handler pathom-env)]
            handler-result)
          (catch Throwable t
            {::form/errors [{:message (str "Unexpected error saving form: " (ex-message t))}]})))))

(defn validate-delta
  "Validates all attributes in a delta map according to their attribute definitions.
   Delta format is: {[id-attr id] {attr {:before val :after val} ...} ...}
   Returns a vector of error maps if validation fails, or nil if all valid."
  [delta all-attributes]
  (let [errors (reduce-kv
                (fn [errors ident entity-delta]
                  ;; For each entity in the delta
                  (reduce-kv
                   (fn [errors attr-key {:keys [after]}]
                     ;; Validate the :after value for each attribute
                     (if-let [attribute (some #(when (= attr-key (::attr/qualified-key %)) %) all-attributes)]
                       (if (attr/valid-value? attribute after {} attr-key)
                         errors
                         (conj errors {:message (str "Invalid value for " attr-key ": "
                                                    (cond
                                                      (and (string? after) (str/blank? after))
                                                      "cannot be blank"

                                                      (nil? after)
                                                      "is required"

                                                      :else
                                                      "value is invalid"))}))
                       errors))
                   errors
                   entity-delta))
                []
                delta)]
    (when (seq errors)
      errors)))

(defn wrap-attribute-validation
  "Middleware wrapper that validates all attributes in the delta before saving.
   If validation fails, returns errors without calling the handler.
   Always includes ::form/errors in the response (empty vector if no errors) for Fulcro RAD."
  [handler]
  (fn [{::attr/keys [key->attribute]
        ::form/keys [params] :as env}]
    (let [delta (:delta params)
          all-attributes (when key->attribute (vals key->attribute))]
      (if-let [errors (and all-attributes delta (validate-delta delta all-attributes))]
        {::form/errors errors}
        (let [result (handler env)]
          ;; Always include ::form/errors (empty vector if no errors) for Fulcro RAD
          (assoc result ::form/errors []))))))

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
