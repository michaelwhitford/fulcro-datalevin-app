(ns app.ui.root
  "Root UI component for the test application."
  (:require
   [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
   #?(:clj [com.fulcrologic.fulcro.dom-server :as dom]
      :cljs [com.fulcrologic.fulcro.dom :as dom])
   #?@(:cljs [[com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown :refer [ui-dropdown]]
              [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown-menu :refer [ui-dropdown-menu]]
              [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown-item :refer [ui-dropdown-item]]
              [com.fulcrologic.semantic-ui.modules.modal.ui-modal :refer [ui-modal]]
              [com.fulcrologic.semantic-ui.modules.modal.ui-modal-content :refer [ui-modal-content]]
              [com.fulcrologic.semantic-ui.modules.modal.ui-modal-actions :refer [ui-modal-actions]]])
   [com.fulcrologic.fulcro.application :as app]
   [com.fulcrologic.fulcro.mutations :as m]
   [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
   [com.fulcrologic.fulcro.data-fetch :as df]
   [com.fulcrologic.fulcro.algorithms.merge :as merge]
   [com.fulcrologic.rad.form :as form]
   [com.fulcrologic.rad.form-options :as fo]
   [com.fulcrologic.rad.report :as report]
   [com.fulcrologic.rad.report-options :as ro]
   [com.fulcrologic.fulcro.routing.dynamic-routing :refer [defrouter]]
   [com.fulcrologic.rad.control :as control]
   [com.fulcrologic.statecharts.chart :refer [statechart]]
   [com.fulcrologic.statecharts.integration.fulcro.rad-integration :as ri]
   [com.fulcrologic.statecharts.integration.fulcro :as scf]
   [com.fulcrologic.statecharts.integration.fulcro.ui-routes :as uir]
   [app.model.account :as account]
   [app.model.category :as category]
   [app.model.item :as item]
   #?(:cljs [taoensso.timbre :as log])))

;; Account Form and Report

(form/defsc-form AccountForm [this props]
  {fo/id account/id
   fo/attributes [account/name account/email account/active?]
   fo/default-values {:account/active? true}
   fo/route-prefix "account"
   fo/title "Edit Account"
   fo/debug? true
   fo/cancel-route ::AccountList})

(defsc AccountListItem [this {:account/keys [id name email active?] :as props}
                        {:keys [report-instance row-class ::report/idx]}]
  {:query [:account/id :account/name :account/email :account/active?]
   :ident :account/id}
  (let [{:keys [edit-form entity-id]} (report/form-link report-instance props :account/id)]
    (dom/div :.item
             (dom/div :.content
                      (if edit-form
                        (dom/a :.header {:onClick (fn [] (form/edit! this edit-form entity-id))} name)
                        (dom/strong "Name: " name))
                      (dom/div (dom/strong "Email: ") email)
                      (dom/div (dom/strong "Active: ") (if active? "Yes" "No"))))))

(def ui-account-item (comp/factory AccountListItem {:keyfn :account/id}))

;; Account Report

(report/defsc-report AccountList [this props]
  {ro/title                     "All Accounts"
   ro/source-attribute          :account/all
   ro/row-pk                    account/id
   ro/columns                   [account/name account/email account/active?]
   ro/route                     "accounts"
   ro/column-formatters {:account/name (fn [this v {:account/keys [id name]}]
                                         (dom/a {:onClick (fn [] (ri/edit! this AccountForm id))}
                                                (str name)))}
   ro/controls  {::new-account {:type :button
                                :local? true
                                :label "New Account"
                                :action (fn [this _] (ri/create! this AccountForm))}}

   ro/row-actions [{:label "Delete"
                    :action (fn [this {:account/keys [id] :as row}]
                              (form/delete! this :account/id id))}]

   ro/control-layout {:action-buttons [::new-account]}}
  #_(report/render-layout this))

(def ui-account-list (comp/factory AccountList {:keyfn :account/id}))

;; Category Form and Report

(form/defsc-form CategoryForm [this props]
  {fo/id category/id
   fo/attributes [category/label]
   fo/route-prefix "category"
   fo/title "Edit Category"
   fo/debug? true
   fo/cancel-route ::CategoryList})

(report/defsc-report CategoryList [this props]
  {ro/title                     "All Categories"
   ro/source-attribute          :category/all
   ro/row-pk                    category/id
   ro/columns                   [category/label]
   ro/route                     "categories"
   ro/column-formatters {:category/label (fn [this v {:category/keys [id label]}]
                                           (dom/a {:onClick (fn [] (ri/edit! this CategoryForm id))}
                                                  (str label)))}
   ro/controls  {::new-category {:type :button
                                 :local? true
                                 :label "New Category"
                                 :action (fn [this _] (ri/create! this CategoryForm))}}

   ro/row-actions [{:label "Delete"
                    :action (fn [this {:category/keys [id] :as row}]
                              (form/delete! this :category/id id))}]

   ro/control-layout {:action-buttons [::new-category]}})

(def ui-category-list (comp/factory CategoryList {:keyfn :category/id}))

;; Item Form and Report

(defsc CategoryQuery [_ _]
  {:query [:category/id :category/label]
   :ident :category/id})

(form/defsc-form ItemForm [this props]
  {fo/id item/id
   fo/attributes [item/name item/description item/price item/in-stock item/category]
   fo/route-prefix "item"
   fo/title "Edit Item"
   fo/debug? true
   fo/cancel-route ::ItemList
   ;; Optional: Allow creating new categories directly from the item form picker
   ;; Uncomment the line below to enable this feature:
   #_#_fo/field-options {:item/category {com.fulcrologic.rad.picker-options/form CategoryForm}}})

(report/defsc-report ItemList [this props]
  {ro/title                     "All Items"
   ro/source-attribute          :item/all
   ro/columns-EQL               {:item/category [:category/id :category/label]}
   ro/row-pk                    item/id
   ro/columns                   [item/name item/description item/price item/in-stock item/category]
   ro/route                     "items"
   ro/column-formatters {:item/name (fn [this v {:item/keys [id name]}]
                                      (dom/a {:onClick (fn [] (ri/edit! this ItemForm id))}
                                             (str name)))
                         :item/category (fn [this v {:item/keys [category] :as row}]
                                          ;; v is the category value; should be {:category/id uuid :category/label "..."}
                                          ;; if the backend query worked properly
                                          (let [label (or (:category/label v) (:category/label category) "None")]
                                            (dom/span (str label))))}
   ro/controls  {::new-item {:type :button
                             :local? true
                             :label "New Item"
                             :action (fn [this _] (ri/create! this ItemForm))}}

   ro/row-actions [{:label "Delete"
                    :action (fn [this {:item/keys [id] :as row}]
                              (form/delete! this :item/id id))}]

   ro/control-layout {:action-buttons [::new-item]}})

(def ui-item-list (comp/factory ItemList {:keyfn :item/id}))

(defsc LandingPage [this props]
  {:query [:ui/ready?]
   :ident (fn [] [:component/id ::LandingPage])
   :initial-state {}}
  #?(:cljs
     (comp/fragment)))

;; Main Router
#_(defrouter MainRouter [this props]
    {:router-targets [LandingPage AccountForm]})

#_(def ui-main-router (comp/factory MainRouter))


;; Root Component

(defsc Root [this {:ui/keys [ready?] ::app/keys [active-remotes] :as props}]
  {:query         [::app/active-remotes (scf/statechart-session-ident uir/session-id)
                   :ui/ready?]
   :initial-state (fn [_]
                    {:ui/ready? false})}

  #?(:cljs
     (let [config (scf/current-configuration this uir/session-id)
           routing-blocked? (uir/route-denied? this)
           busy? (seq active-remotes)]
       (dom/div (ui-modal {:open routing-blocked?}
                          (ui-modal-content {}
                                            "Routing away from this form will lose unsaved changes.  Are you sure?")
                          (ui-modal-actions {}
                                            (dom/button :.ui.negative.button {:onClick (fn [] (uir/abandon-route-change! this))} "No")
                                            (dom/button :.ui.positive.button {:onClick (fn [] (uir/force-continue-routing! this))} "Yes")))

                (if ready?
                  (comp/fragment
                   (dom/div :.ui.top.menu
                            (ui-dropdown {:className " item " :text " Account "}
                                         (ui-dropdown-menu {}
                                                           (ui-dropdown-item {:onClick (fn [] (uir/route-to! this `AccountList))} " View All ")
                                                           (ui-dropdown-item {:onClick (fn [] (ri/create! this `AccountForm))} " New ")))
                            (ui-dropdown {:className " item " :text " Category "}
                                         (ui-dropdown-menu {}
                                                           (ui-dropdown-item {:onClick (fn [] (uir/route-to! this `CategoryList))} " View All ")
                                                           (ui-dropdown-item {:onClick (fn [] (ri/create! this `CategoryForm))} " New ")))
                            (ui-dropdown {:className " item " :text " Item "}
                                         (ui-dropdown-menu {}
                                                           (ui-dropdown-item {:onClick (fn [] (uir/route-to! this `ItemList))} " View All ")
                                                           (ui-dropdown-item {:onClick (fn [] (ri/create! this `ItemForm))} " New ")))
                            (dom/div :.right.menu
                                     (dom/div :.ui.item
                                              (dom/i :.ui.icon.user)))))
                  (dom/div :.ui.active.dimmer
                           (dom/div :.ui.large.text.loader "Loading... ")))
                (comp/fragment
                 (dom/div :.ui.container.segment
                          (uir/ui-current-subroute this comp/factory)))))
     :clj
     (dom/div "Server-side rendering not implemented")))

(def ui-root (comp/factory Root))

(def application-chart
  (statechart {:name "fulcro-rad-datalevin-test"}
              (uir/routing-regions
               (uir/routes {:id :region/routes
                            :routing/root Root}
                           (uir/rstate {:route/target `LandingPage
                                        :route/path ["landing-page"]})
                           (ri/report-state {:route/target `AccountList
                                             :route/path ["accounts"]})
                           (ri/form-state {:route/target `AccountForm
                                           :route/path ["account"]})
                           (ri/report-state {:route/target `CategoryList
                                             :route/path ["categories"]})
                           (ri/form-state {:route/target `CategoryForm
                                           :route/path ["category"]})
                           (ri/report-state {:route/target `ItemList
                                             :route/path ["items"]})
                           (ri/form-state {:route/target `ItemForm
                                           :route/path ["item"]})))))

(comment
  (comp/get-query Root)
  (comp/get-query AccountList)
  (comp/get-initial-state AccountList))
