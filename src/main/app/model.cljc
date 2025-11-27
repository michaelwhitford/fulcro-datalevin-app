(ns app.model
  "Central model namespace that aggregates all attributes."
  (:require
    [app.model.account :as account]
    [app.model.category :as category]
    [app.model.item :as item]
    [app.model.person :as person]))

(def all-attributes
  "Vector of all RAD attributes in the application."
  (vec (concat
         account/attributes
         category/attributes
         item/attributes
         person/attributes)))
