(ns app.model.account
  "Account entity model with RAD attributes for Datalevin testing."
  (:require
    [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    #?(:clj [us.whitford.fulcro.rad.database-adapters.datalevin-options :as dlo])))

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

(defattr all-accounts :account/all-accounts :ref
  {ao/target      :account/id
   ao/pc-output   [{:account/all-accounts [:account/id]}]})

(def attributes [id name email active? created-at all-accounts])
