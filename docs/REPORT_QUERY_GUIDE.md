# RAD Report Query Inclusions & Column Formatters Guide

## Summary of Issues Fixed

### Issue 1: `ro/query-inclusions` was commented out
**Problem**: The line was prefixed with `#_#_` which comments out the next two forms.
**Solution**: Removed the comment markers.

### Issue 2: Incorrect query-inclusions syntax
**Problem**: You had `{:category/all [:category/id :category/label]}`
**Solution**: Changed to `{:item/category [:category/id :category/label]}`

**Explanation**:
- `:category/all` is a ROOT-LEVEL query that would fetch all categories independently
- `:item/category` is a JOIN that fetches category data for each item's category reference
- We want the latter - to include category details within each item row

### Issue 3: Column formatter destructuring was incorrect
**Problem**: The formatter tried to destructure category keys directly from the row:
```clojure
(fn [this v {:category/keys [id label] :as opts}]
  (str (or label "None")))
```

**Solution**: Properly destructure the item row to access the category join:
```clojure
(fn [this v {:item/keys [category] :as row}]
  (str (or (:category/label category) "None")))
```

**Explanation**:
- The third argument to a column formatter is the ENTIRE ROW (the item, in this case)
- The row has keys like `:item/id`, `:item/name`, `:item/category`, etc.
- The `:item/category` value is the JOINED data: `{:category/id uuid, :category/label "Electronics"}`
- You must destructure `:item/keys [category]` first, then access `:category/label` from that

## Understanding RAD Report Queries

### The Three Arguments to Column Formatters

```clojure
(fn [this v row]
  ...)
```

1. **`this`**: The report component instance
2. **`v`**: The RAW value of the attribute for this row
   - For `:item/name`, this is the string name
   - For `:item/category`, this is the ref: `[:category/id #uuid "..."]`
3. **`row`**: The complete row data (all columns included in the query)

### Query Structure for References

When you have a reference attribute like `:item/category` (which points to `:category/id`),
you need to specify what fields to fetch from the referenced entity.

**Without `ro/query-inclusions`:**
```clojure
{ro/columns [item/name item/category]}
```
Generates query:
```clojure
[:item/id :item/name :item/category]
```
Result:
```clojure
{:item/id #uuid "..."
 :item/name "Laptop"
 :item/category [:category/id #uuid "..."]}  ; Just the ref, no label!
```

**With `ro/query-inclusions`:**
```clojure
{ro/columns [item/name item/category]
 ro/query-inclusions [{:item/category [:category/id :category/label]}]}
```
Generates query:
```clojure
[:item/id :item/name {:item/category [:category/id :category/label]}]
```
Result:
```clojure
{:item/id #uuid "..."
 :item/name "Laptop"
 :item/category {:category/id #uuid "..."
                 :category/label "Electronics"}}  ; Full category data!
```

## Complete Working Example

```clojure
(report/defsc-report ItemList [this props]
  {ro/title            "All Items"
   ro/source-attribute :item/all
   ro/row-pk           item/id
   ro/columns          [item/name item/description item/price item/in-stock item/category]
   ro/route            "items"
   
   ;; Specify what to fetch from the :item/category reference
   ro/query-inclusions [{:item/category [:category/id :category/label]}]
   
   ro/column-formatters 
   {:item/name     (fn [this v {:item/keys [id name]}]
                     (dom/a {:onClick (fn [] (ri/edit! this ItemForm id))}
                            (str name)))
    
    ;; Access the category data correctly
    :item/category (fn [this v {:item/keys [category] :as row}]
                     ;; category is now {:category/id ... :category/label "..."}
                     (str (or (:category/label category) "None")))}
   
   ro/controls {::new-item {:type :button
                            :local? true
                            :label "New Item"
                            :action (fn [this _] (ri/create! this ItemForm))}}
   
   ro/row-actions [{:label "Delete"
                    :action (fn [this {:item/keys [id] :as row}]
                              (form/delete! this :item/id id))}]
   
   ro/control-layout {:action-buttons [::new-item]}})
```

## Debugging Tips

### 1. Check the Generated Query
```clojure
(comp/get-query ItemList)
;; Should show: [:item/id :item/name ... {:item/category [:category/id :category/label]}]
```

### 2. Inspect Data in Column Formatter
```clojure
:item/category (fn [this v {:item/keys [category] :as row}]
                 (tap> {:v v :category category :row row})
                 ;; Now check your tap listener to see what data is available
                 (str (or (:category/label category) "None")))
```

### 3. Use Fulcro Inspect
- Open browser dev tools
- Check Fulcro Inspect -> Network tab
- Look at the query and response for the `:item/all` load
- Verify that category data is being fetched

### 4. Check Client DB
In CLJS REPL:
```clojure
@(-> app :com.fulcrologic.fulcro.application/state-atom)
;; Look for [:item/id <uuid>] entries and check if :item/category has joined data
```

## Common Patterns

### Multiple Reference Levels
If you have nested references (e.g., item -> category -> parent-category):
```clojure
ro/query-inclusions [{:item/category [:category/id 
                                      :category/label
                                      {:category/parent [:category/id :category/label]}]}]
```

### Optional vs Required References
If a reference might be nil, always use `(or ...)` in the formatter:
```clojure
(str (or (:category/label category) "None"))
```

### Multiple Query Inclusions
You can include multiple joins:
```clojure
ro/query-inclusions [{:item/category [:category/id :category/label]}
                     {:item/vendor [:vendor/id :vendor/name]}
                     :item/extra-field]
```

## Related Files
- Attribute definition: `src/main/app/model/item.cljc`
- Report definition: `src/main/app/ui/root.cljc`
- Integration tests showing queries: `src/test/app/pathom_integration_test.clj`
