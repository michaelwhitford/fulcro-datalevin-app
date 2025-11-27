# Changelog

## [Unreleased]

### Fixed
- **Client-side form validation now properly enforces required fields**
  - Added `fo/validator` option to `AccountForm`, `CategoryForm`, and `ItemForm` using `attr/make-attribute-validator`
  - Empty strings and nil values in required fields are now properly rejected before save
  - Validation errors will be displayed in the UI when fields are marked complete (on blur)
  - The save button will remain disabled when form has invalid fields
  
  **Root Cause:** Forms were missing the `fo/validator` configuration which is required for client-side validation in fulcro-rad. While the `attr/valid-value?` function worked correctly, the form validator was returning `:unchecked` instead of actually checking field values.
  
  **Investigation:** Used CLJS REPL to surface the issue:
  - Confirmed account/name was set to empty string in app state
  - Verified `attr/valid-value?` correctly returned `false` for empty strings
  - Discovered form validator was not configured to use attribute validation
  
  **Solution:** Added `fo/validator (attr/make-attribute-validator [attributes...])` to all forms, which creates a proper Fulcro form-state validator that uses the RAD attribute validation system.

- **Fixed Pathom3 error when querying for form errors**
  - Eliminated "EQL query for :com.fulcrologic.rad.form/errors cannot be resolved" error in server logs
  - Modified save middleware to always include `::form/errors` in responses
  
  **Root Cause:** The form's query includes `::form/errors`, but when a save succeeds, the middleware only returns `{:tempids {...}}` without `::form/errors`. Pathom3 then tries to resolve `::form/errors` from the query but can't find it in the mutation response or via a resolver, causing an "attribute-unreachable" error.
  
  **Solution:** Modified `wrap-attribute-validation` in `app.server.middleware` to always include `::form/errors` in the response. When validation passes, it now returns `(assoc result ::form/errors nil)` instead of just returning the handler result. This ensures Pathom3 can always resolve `::form/errors` from the mutation response.
