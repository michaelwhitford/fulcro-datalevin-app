# Plan: Fix Form Validation

## Issue
Form validation is not working correctly on the client side. The `account/name` field was successfully set to an empty string even though it's marked as `ao/required? true`.

## Root Cause Analysis (via CLJS REPL)

### Evidence Found:
1. **Current app state shows invalid data:**
   - Account name is set to `""` (empty string) which should be invalid
   
2. **The validation function itself works correctly:**
   - `(attr/valid-value? account/name "" {} :account/name)` => `false` ✓
   - `(attr/valid-value? account/name "Test" {} :account/name)` => `true` ✓

3. **The form validator returns `:unchecked` instead of enforcing validation:**
   - The validator exists but is not properly configured to check field values

4. **Manual validation check confirms the field is invalid:**
   - `:account/name ""` => `Valid? false`
   - But the form allows the save to proceed

### Root Cause:
The forms (`AccountForm`, `CategoryForm`, `ItemForm`) are missing the `fo/validator` option which should be set to use `attr/make-attribute-validator` from the RAD attributes namespace.

According to the fulcro-rad source code:
- Forms need an explicit `fo/validator` option to enforce client-side validation
- The validator should be created using `attr/make-attribute-validator` which creates a Fulcro form-state validator
- This validator uses `attr/valid-value?` to check each field

## Solution

Add `fo/validator` to all forms using `attr/make-attribute-validator`:

```clojure
(form/defsc-form AccountForm [this props]
  {fo/id account/id
   fo/attributes [account/name account/email account/active?]
   fo/validator (attr/make-attribute-validator [account/name account/email account/active?])
   ;; ... rest of options
   })
```

This will:
1. Prevent invalid fields from being marked as valid
2. Show validation errors in the UI when fields are marked complete (on blur)
3. Prevent form saves when there are invalid fields

## Files to Modify

1. `src/main/app/ui/root.cljc` - Add validator to `AccountForm`, `CategoryForm`, and `ItemForm` ✅
2. `src/main/app/server/parser.clj` - Add output declaration to form mutations for Pathom3 ✅

## Additional Issue Found

After fixing client-side validation, discovered a Pathom3 compatibility issue:
- Error: "EQL query for :com.fulcrologic.rad.form/errors cannot be resolved"
- **Root Cause:** The form query includes `::form/errors`, but when a save succeeds, the middleware doesn't return this key. Pathom3 tries to resolve it and fails because there's no resolver for it and it's not in the mutation response.
- **Solution:** Modified `wrap-attribute-validation` middleware to always include `::form/errors` in the response (set to `nil` when there are no errors). This ensures Pathom3 can resolve the key from the mutation response.

## Testing Plan

1. Run CLJS REPL tests to verify validator is working ✅
2. Try to save empty account/name and verify it's blocked ✅
3. Run existing validation tests to ensure they pass
4. Verify no Pathom3 errors appear in logs when saving ✅
