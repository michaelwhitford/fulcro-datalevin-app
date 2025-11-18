(ns app.ui.root
  "Root UI component for the test application."
  (:require
   [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
   #?(:clj [com.fulcrologic.fulcro.dom-server :as dom]
      :cljs [com.fulcrologic.fulcro.dom :as dom])
   [com.fulcrologic.fulcro.mutations :as m]
   [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
   [com.fulcrologic.fulcro.data-fetch :as df]
   [com.fulcrologic.fulcro.algorithms.merge :as merge]
   [com.fulcrologic.rad.form :as form]
   [com.fulcrologic.rad.form-options :as fo]
   [com.fulcrologic.rad.report :as report]
   [com.fulcrologic.rad.report-options :as ro]
   [com.fulcrologic.rad.control :as control]
   [app.model.account :as account]
   [app.model.category :as category]
   [app.model.item :as item]
   #?(:cljs [taoensso.timbre :as log])))

;; Simple Account Item Component

(form/defsc-form AccountForm [this props]
  {fo/id account/id
   fo/attributes [account/name account/email account/active?]
   fo/default-values {:account/active? true}
   fo/route-prefix "account"
   fo/title "Edit Account"})

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
   ro/source-attribute          :account/id
   ro/columns                   [account/id account/name account/email account/active?]
   ro/row-pk                    account/id
   ro/route                     "accounts"}
  (report/render-layout this))

(def ui-account-list (comp/factory AccountList))

;; Root Component

(defsc Root [this {:ui/keys [ready?] :keys [all-accounts] :as props}]
  {:query         [:ui/ready?
                   {:all-accounts (comp/get-query AccountList)}]
   :initial-state (fn [_]
                    {:ui/ready?     false
                     :all-accounts (comp/get-initial-state AccountList)})}
  #?(:cljs
     (dom/div {:style {:padding "20px" :fontFamily "system-ui, sans-serif"}}
              (dom/h1 "Datalevin RAD Test Application")
              (dom/p "Testing fulcro-rad-datalevin plugin integration")
              (dom/div {:style {:marginTop "20px"}}
                       (when (seq all-accounts)
                         (ui-account-list all-accounts))))
     :clj
     (dom/div "Server-side rendering not implemented")))

(def ui-root (comp/factory Root))

(comment
  (comp/get-initial-state AccountList))
