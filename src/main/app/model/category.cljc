(ns app.model.category
  "Category entity model for organizing items."
  (:require
    [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    #?(:clj [us.whitford.fulcro.rad.database-adapters.datalevin-options :as dlo])))

(defattr id :category/id :uuid
  {ao/identity? true
   ao/schema    :main
   ;; Specify which attribute to use as the label when this entity is referenced
   ao/label     :category/label})

(defattr label :category/label :string
  {ao/schema     :main
   ao/identities #{:category/id}
   ao/required?  true
   #?@(:clj [:us.whitford.fulcro.rad.database-adapters.datalevin-options/attribute-schema {:db/unique :db.unique/value}])})

(def attributes [id label])
