(ns app.model.item
  "Item entity model with references to category."
  (:require
   [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
   [com.fulcrologic.rad.attributes-options :as ao]
   [com.fulcrologic.rad.form-options :as fo]
   [com.fulcrologic.rad.picker-options :as po]
   #?(:clj [us.whitford.fulcro.rad.database-adapters.datalevin-options :as dlo]))
  (:refer-clojure :exclude [name]))

(defattr id :item/id :uuid
  {ao/identity? true
   ao/schema    :main})

(defattr name :item/name :string
  {ao/schema     :main
   ao/identities #{:item/id}
   ao/required?  true})

(defattr description :item/description :string
  {ao/schema     :main
   ao/identities #{:item/id}})

(defattr price :item/price :double
  {ao/schema     :main
   ao/identities #{:item/id}})

(defattr in-stock :item/in-stock :int
  {ao/schema     :main
   ao/identities #{:item/id}})

(defattr category :item/category :ref
  {ao/schema       :main
   ao/identities   #{:item/id}
   ao/target       :category/id
   ao/cardinality  :one
   ao/pc-output    [:category/id :category/label]
   fo/field-style  :pick-one
   fo/field-options {po/query-key      :category/all
                     po/query          [:category/id :category/label]
                     po/cache-time-ms  30000
                     po/cache-key :all-category-options
                     po/options-xform #?(:cljs (fn [_ options]
                                                 (mapv (fn [{:category/keys [id label]}]
                                                         {:text (str label) :value [:category/id id]})
                                                       options))
                                         :clj nil)}})

(def attributes [id name description price in-stock category])
