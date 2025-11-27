(ns app.model.person
  "Person entity model with native-id support for Datalevin testing.
   
   This model demonstrates the use of Datalevin's built-in :db/id as the
   identity attribute instead of a domain-specific UUID. Native IDs provide
   better performance and compatibility with existing Datalevin databases."
  (:require
   [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
   [com.fulcrologic.rad.attributes-options :as ao]
   #?(:clj [us.whitford.fulcro.rad.database-adapters.datalevin-options :as dlo]))
  (:refer-clojure :exclude [name]))

(defattr id :person/id :long
  {ao/identity? true
   ao/schema    :main
   ;; Use Datalevin's built-in :db/id instead of a domain-specific ID
   #?@(:clj [dlo/native-id? true])})

(defattr name :person/name :string
  {ao/schema     :main
   ao/identities #{:person/id}
   ao/required?  true})

(defattr email :person/email :string
  {ao/schema     :main
   ao/identities #{:person/id}
   ao/required?  true
   #?@(:clj [:us.whitford.fulcro.rad.database-adapters.datalevin-options/attribute-schema 
             {:db/unique :db.unique/value}])})

(defattr age :person/age :long
  {ao/schema     :main
   ao/identities #{:person/id}})

(defattr bio :person/bio :string
  {ao/schema     :main
   ao/identities #{:person/id}})

(def attributes [id name email age bio])
