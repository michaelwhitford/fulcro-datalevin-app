(ns app.server.parser
  "Pathom3 parser configuration with Datalevin resolvers."
  (:require
   [mount.core :refer [defstate]]
   [com.fulcrologic.rad.attributes :as attr]
   [com.fulcrologic.rad.form :as form]
   [com.fulcrologic.rad.pathom3 :as pathom3]
   [us.whitford.fulcro.rad.database-adapters.datalevin :as dl]
   [us.whitford.fulcro.rad.database-adapters.datalevin-options :as dlo]
   [us.whitford.fulcro.rad.database-adapters.datalevin-common :as common]
   [app.model :as model]
   [app.server.config :refer [config]]
   [app.server.database :refer [connections]]
   [app.server.middleware :as middleware]
   [com.wsscode.pathom3.connect.operation :as pco]
   [datalevin.core :as d]
   [taoensso.timbre :as log]))

;; Custom resolvers for testing

(pco/defresolver all-accounts-resolver
  "Returns all account IDs"
  [{::dlo/keys [databases]} _]
  {::all-accounts
   (let [db (get databases :main)]
     (mapv first
           (d/q '[:find ?id
                  :where [?e :account/id ?id]]
                db)))})

(pco/defresolver all-categories-resolver
  "Returns all category IDs"
  [{::dlo/keys [databases]} _]
  {::all-categories
   (let [db (get databases :main)]
     (mapv first
           (d/q '[:find ?id
                  :where [?e :category/id ?id]]
                db)))})

(pco/defresolver all-items-resolver
  "Returns all item IDs"
  [{::dlo/keys [databases]} _]
  {::all-items
   (let [db (get databases :main)]
     (mapv first
           (d/q '[:find ?id
                  :where [?e :item/id ?id]]
                db)))})

(pco/defresolver accounts-list-resolver
  "Resolver for accounts list at the root"
  [{::dlo/keys [databases]} _]
  {::pco/output [{:all-accounts [:account/id :account/name :account/email :account/active?]}]}
  (let [db (get databases :main)
        accounts (mapv (fn [[id name email active?]]
                         {:account/id id
                          :account/name name
                          :account/email email
                          :account/active? active?})
                       (d/q '[:find ?id ?name ?email ?active?
                              :where
                              [?e :account/id ?id]
                              [?e :account/name ?name]
                              [?e :account/email ?email]
                              [?e :account/active? ?active?]]
                            db))]
    (log/info "All accounts resolver returning:" (count accounts) "accounts -" accounts)
    {:all-accounts accounts}))

(pco/defresolver categories-list-resolver
  "Resolver for categories list at the root"
  [{::dlo/keys [databases]} _]
  {::pco/output [:categories]}
  (let [db (get databases :main)
        categories (mapv (fn [id] {:category/id id})
                         (mapv first
                               (d/q '[:find ?id
                                      :where [?e :category/id ?id]]
                                    db)))]
    (log/info "Categories list resolver returning:" (count categories) "categories")
    {:categories categories}))

(pco/defresolver items-list-resolver
  "Resolver for items list at the root"
  [{::dlo/keys [databases]} _]
  {::pco/output [:items]}
  (let [db (get databases :main)
        items (mapv (fn [id] {:item/id id})
                    (mapv first
                          (d/q '[:find ?id
                                 :where [?e :item/id ?id]]
                               db)))]
    (log/info "Items list resolver returning:" (count items) "items")
    {:items items}))

(pco/defresolver ui-ready-resolver
  "Resolver for ui/ready? flag"
  [_ _]
  {::pco/output [:ui/ready?]}
  {:ui/ready? true})

(pco/defresolver db-metrics-resolver
  "Returns current database metrics"
  [_ _]
  {::db-metrics (dl/get-metrics)})

;; Mutations

(pco/defmutation add-account-mutation
  [{::dlo/keys [connections]} {:keys [name email tempid]}]
  {::pco/op-name 'app.ui.root/add-account}
  (let [conn (get connections :main)
        id   (d/squuid)
        tx-data [{:account/id id
                  :account/name name
                  :account/email email
                  :account/active? true
                  :account/created-at (java.util.Date.)}]]
    (log/info "Adding account:" tx-data)
    (d/transact! conn tx-data)
    (cond-> {:account/id id}
      tempid (assoc :tempids {tempid id}))))

(pco/defmutation add-category-mutation
  [{::dlo/keys [connections]} {:keys [label tempid]}]
  {::pco/op-name 'app.ui.root/add-category}
  (let [conn (get connections :main)
        id   (d/squuid)
        tx-data [{:category/id id
                  :category/label label}]]
    (log/info "Adding category:" tx-data)
    (d/transact! conn tx-data)
    (cond-> {:category/id id}
      tempid (assoc :tempids {tempid id}))))

(pco/defmutation add-item-mutation
  [{::dlo/keys [connections]} {:keys [name description price in-stock tempid]}]
  {::pco/op-name 'app.ui.root/add-item}
  (let [conn (get connections :main)
        id   (d/squuid)
        tx-data [(cond-> {:item/id id
                          :item/name name}
                   description (assoc :item/description description)
                   price (assoc :item/price price)
                   in-stock (assoc :item/in-stock in-stock))]]
    (log/info "Adding item:" tx-data)
    (d/transact! conn tx-data)
    (cond-> {:item/id id}
      tempid (assoc :tempids {tempid id}))))

(pco/defmutation delete-account-mutation
  [{::dlo/keys [connections]} {:keys [id]}]
  {::pco/op-name 'app.ui.root/delete-account}
  (let [conn (get connections :main)
        db   (d/db conn)
        eid  (d/q '[:find ?e .
                    :in $ ?id
                    :where [?e :account/id ?id]]
                  db id)]
    (when eid
      (log/info "Deleting account:" id)
      (d/transact! conn [[:db/retractEntity eid]]))
    {}))

(pco/defmutation delete-category-mutation
  [{::dlo/keys [connections]} {:keys [id]}]
  {::pco/op-name 'app.ui.root/delete-category}
  (let [conn (get connections :main)
        db   (d/db conn)
        eid  (d/q '[:find ?e .
                    :in $ ?id
                    :where [?e :category/id ?id]]
                  db id)]
    (when eid
      (log/info "Deleting category:" id)
      (d/transact! conn [[:db/retractEntity eid]]))
    {}))

(pco/defmutation delete-item-mutation
  [{::dlo/keys [connections]} {:keys [id]}]
  {::pco/op-name 'app.ui.root/delete-item}
  (let [conn (get connections :main)
        db   (d/db conn)
        eid  (d/q '[:find ?e .
                    :in $ ?id
                    :where [?e :item/id ?id]]
                  db id)]
    (when eid
      (log/info "Deleting item:" id)
      (d/transact! conn [[:db/retractEntity eid]]))
    {}))

(def custom-resolvers
  [all-accounts-resolver
   all-categories-resolver
   all-items-resolver
   db-metrics-resolver
   accounts-list-resolver
   categories-list-resolver
   items-list-resolver
   ui-ready-resolver])

(def custom-mutations
  [add-account-mutation
   add-category-mutation
   add-item-mutation
   delete-account-mutation
   delete-category-mutation
   delete-item-mutation])

(def automatic-resolvers (dl/generate-resolvers model/all-attributes))

(def all-resolvers
  "All hand-written resolvers and mutations."
  [custom-resolvers
   custom-mutations])

(defstate parser
  :start
  (let [env-middleware (-> (attr/wrap-env model/all-attributes)
                           (form/wrap-env middleware/save-middleware middleware/delete-middleware)
                           (common/wrap-env (fn [env] {:main (:main connections)}) d/db))]
    (pathom3/new-processor config env-middleware []
                           [automatic-resolvers
                            form/resolvers
                            all-resolvers])))

(comment
  (tap> {:from :repl :automatic-resolvers automatic-resolvers}))
