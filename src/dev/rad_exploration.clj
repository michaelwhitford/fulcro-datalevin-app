(ns rad-exploration
  "REPL exploration of RAD report options and query structures"
  (:require
   [com.fulcrologic.rad.report :as report]
   [com.fulcrologic.rad.report-options :as ro]
   [com.fulcrologic.rad.attributes :as attr]
   [com.fulcrologic.fulcro.components :as comp]
   [clojure.pprint :refer [pprint]]
   [app.ui.root :as root]
   [app.model.item :as item]
   [app.model.category :as category]))

(comment
  ;; ==============================================
  ;; EXPLORATION: Understanding RAD Report Queries
  ;; ==============================================
  
  ;; 1. First, let's examine what query the ItemList report generates
  (comp/get-query root/ItemList)
  ;; This should show us the current query structure
  
  ;; 2. Let's look at the item attribute definition
  item/category
  ;; This shows us that :item/category is a :ref with :ao/target :category/id
  
  ;; 3. Check the report options for ItemList
  (comp/component-options root/ItemList)
  
  ;; 4. Understanding ro/query-inclusions
  ;; According to RAD docs, ro/query-inclusions allows you to specify
  ;; additional joins in the report query. The format should be:
  ;; ro/query-inclusions [:additional-prop {:join-prop [:sub/key1 :sub/key2]}]
  ;;
  ;; For our case with :item/category being a ref, we need to specify
  ;; what fields to fetch from the category when loading items.
  ;;
  ;; The CORRECT format is to add it to the columns or as a query inclusion:
  ;; ro/query-inclusions [{:item/category [:category/id :category/label]}]
  
  ;; 5. Understanding column formatters
  ;; Column formatters receive THREE arguments:
  ;; - this: the report component instance
  ;; - v: the raw value of the column attribute for this row
  ;; - row: the ENTIRE row data (the item in our case)
  ;;
  ;; So for :item/category formatter, the 'row' contains the item data,
  ;; and row's :item/category will contain {:category/id ... :category/label ...}
  ;; NOT the category directly as keys.
  
  ;; ==============================================
  ;; SOLUTION ANALYSIS
  ;; ==============================================
  
  ;; PROBLEM 1: ro/query-inclusions is commented out with #_#_
  ;; This means it's not being used at all.
  
  ;; PROBLEM 2: The query-inclusions syntax you had was incorrect.
  ;; You had: ro/query-inclusions [{:category/all [:category/id :category/label]}]
  ;; Should be: ro/query-inclusions [{:item/category [:category/id :category/label]}]
  ;; 
  ;; The difference: :category/all is a root-level query for ALL categories
  ;; But we want to include data for the :item/category join on each item row.
  
  ;; PROBLEM 3: Column formatter destructuring is wrong
  ;; You had: (fn [this v {:category/keys [id label] :as opts}]
  ;; But 'opts' (the third arg) is the ITEM row, not the category!
  ;; Should be: (fn [this v {:item/keys [category] :as row}]
  ;;            and then access category data as (:category/label category)
  
  ;; ==============================================
  ;; TESTING QUERIES
  ;; ==============================================
  
  ;; Let's see what a proper query for items with category would look like:
  (def sample-item-query
    [:item/id
     :item/name
     :item/description
     :item/price
     :item/in-stock
     {:item/category [:category/id :category/label]}])
  
  (pprint sample-item-query)
  
  ;; If you run this query against the parser, you should get items with
  ;; their category data like:
  ;; {:item/id #uuid "..."
  ;;  :item/name "Laptop"
  ;;  :item/category {:category/id #uuid "..."
  ;;                  :category/label "Electronics"}}
  
  ;; ==============================================
  ;; CORRECT REPORT CONFIGURATION
  ;; ==============================================
  
  ;; The ItemList report should be configured as:
  (comment
    (report/defsc-report ItemList [this props]
      {ro/title "All Items"
       ro/source-attribute :item/all
       ro/row-pk item/id
       ro/columns [item/name item/description item/price item/in-stock item/category]
       ro/route "items"
       
       ;; METHOD 1: Use query-inclusions to specify the join
       ro/query-inclusions [{:item/category [:category/id :category/label]}]
       
       ;; Column formatter - note the correct destructuring
       ro/column-formatters {:item/name (fn [this v {:item/keys [id name]}]
                                          (dom/a {:onClick (fn [] (ri/edit! this ItemForm id))}
                                                 (str name)))
                             
                             ;; CORRECT: destructure the item row to get category
                             :item/category (fn [this v {:item/keys [category] :as row}]
                                              ;; v is the raw ref value (e.g., [:category/id uuid])
                                              ;; category is the joined data {:category/id ... :category/label ...}
                                              (str (or (:category/label category) "None")))}
       
       ro/controls {::new-item {:type :button
                                :local? true
                                :label "New Item"
                                :action (fn [this _] (ri/create! this ItemForm))}}
       
       ro/row-actions [{:label "Delete"
                        :action (fn [this {:item/keys [id] :as row}]
                                  (form/delete! this :item/id id))}]
       
       ro/control-layout {:action-buttons [::new-item]}}))
  
  ;; ==============================================
  ;; ALTERNATIVE: Without query-inclusions
  ;; ==============================================
  
  ;; You can also modify the column definition itself by adding a
  ;; computed-query to the attribute, but query-inclusions is cleaner
  ;; for this use case.
  
  ;; ==============================================
  ;; DEBUGGING TIPS
  ;; ==============================================
  
  ;; 1. Check what query is actually being generated:
  (comp/get-query root/ItemList)
  
  ;; 2. Look at the actual data in the client DB:
  ;; In CLJS REPL:
  ;; @(-> app :com.fulcrologic.fulcro.application/state-atom)
  
  ;; 3. Use tap> to inspect the formatter arguments:
  ;; (tap> {:v v :row row})
  
  ;; 4. Check Fulcro Inspect in the browser to see the network tab
  ;;    and what data is actually being loaded
  
  )
