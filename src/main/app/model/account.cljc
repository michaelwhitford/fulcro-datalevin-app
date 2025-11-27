(ns app.model.account
  "Account entity model with RAD attributes for Datalevin testing."
  (:require
   [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
   [com.fulcrologic.rad.attributes-options :as ao]
   [com.fulcrologic.rad.form-options :as fo]
   #?(:clj [us.whitford.fulcro.rad.database-adapters.datalevin-options :as dlo]))
  (:refer-clojure :exclude [name]))

(defattr id :account/id :uuid
  {ao/identity? true
   ao/schema    :main})

(defattr name :account/name :string
  {ao/schema     :main
   ao/identities #{:account/id}
   ao/required?  true})

(defattr email :account/email :string
  {ao/schema     :main
   ao/identities #{:account/id}
   ao/required?  true
   #?@(:clj [:us.whitford.fulcro.rad.database-adapters.datalevin-options/attribute-schema {:db/unique :db.unique/value}])})

(defattr active? :account/active? :boolean
  {ao/schema     :main
   ao/identities #{:account/id}})

(defattr created-at :account/created-at :instant
  {ao/schema     :main
   ao/identities #{:account/id}})

;; Enum Attributes

(defattr role :account/role :enum
  {ao/schema             :main
   ao/identities         #{:account/id}
   ao/enumerated-values  #{:admin :user :guest}
   ao/enumerated-labels  {:admin "Administrator"
                          :user  "Regular User"
                          :guest "Guest User"}
   fo/field-style        :pick-one})

(defattr status :account/status :enum
  {ao/schema             :main
   ao/identities         #{:account/id}
   ao/enumerated-values  #{:status/active :status/inactive :status/pending}
   ao/enumerated-labels  {:status/active   "Active"
                          :status/inactive "Inactive"
                          :status/pending  "Pending Approval"}
   fo/field-style        :pick-one})

(defattr permissions :account/permissions :enum
  {ao/schema             :main
   ao/identities         #{:account/id}
   ao/cardinality        :many
   ao/enumerated-values  #{:read :write :execute}
   ao/enumerated-labels  {:read    "Read Access"
                          :write   "Write Access"
                          :execute "Execute Access"}
   fo/field-style        :pick-many})

(def attributes [id name email active? created-at role status permissions])
