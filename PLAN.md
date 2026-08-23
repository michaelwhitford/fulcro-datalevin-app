# Plan: Migrate to fulcro-rad-datalevin 1.0.0-RC2

## Status: ✅ COMPLETED

## Objective
Absorb the one upstream breaking change (opt-in `:<ns>/all`) and exercise the
three additive RC2 capabilities end-to-end, keeping the suite green. This app is
the adapter's proving ground — integration reality is proven here.

## Upstream changes (fulcro-rad-datalevin 1.0.0-RC1 → 1.0.0-RC2)
- **BREAKING:** `generate-resolvers` no longer emits `:<ns>/all` by default.
  Enumeration is opt-in via `::dlo/all-resolver? true` on the identity attribute
  (canonical Datomic/XTDB idiom: autogen only collision-safe id-resolvers).
- **Additive:** `::dlo/all-ids-key` renames the generated enumeration key (flag
  gates, key names) so a consumer-authored `:<ns>/all` can coexist.
- **Additive:** `:<ns>/search` idents carry `::dlo/score` (desc); `:<ns>/similar`
  idents carry `::dlo/distance` (asc). Opt-in at query time — omitting the metric
  key leaves old queries byte-identical.
- **Additive:** raw primitives `dl/vec-search` (`[[eid dist]…]` asc) and
  `dl/fulltext-search` (`[[eid score]…]` desc) exported on the facade.
- **Additive:** `::dlo/generate-resolvers? false` wired (opt-out).

## Work performed
1. ✅ **Baseline** — ran suite on RC2 classpath; confirmed the 32 failures were
   ALL `:<ns>/all` consumers (account/category/item/person), no other kinds.
2. ✅ **Migrate model** — added `::dlo/all-resolver? true` (under `:clj`) to
   `:account/id`, `:category/id`, `:item/id`, `:person/id`. Restored 91/356/0.
3. ✅ **`::dlo/score`** — added tests through the real parser for the account
   (UUID) and person (native-id) legs; asserted present + descending; added an
   opt-in regression proving byte-identical pre-RC2 shape when unrequested.
4. ✅ **`::dlo/all-ids-key` rename** — new `app.rc2-enhancements-test` proves the
   renamed `:page/index-all` resolves end-to-end while `:page/all` is absent;
   also verifies the flag-gates/key-names rule. Uses a self-contained `:page`
   model to avoid disturbing the real model.
5. ✅ **Raw primitives** — `dl/fulltext-search` exercised over `account` and
   native-id `person` domains (`[[eid score]…]` desc + `:top` cap).
   `dl/vec-search` documented as skipped (no `:vec` attribute in the demo model;
   covered upstream).
6. ✅ **Docs + lint** — CHANGELOG/PLAN updated; `clj-kondo` clean.

## Result
- **Suite: 100 tests / 383 assertions / 0 failures** (was 91/356/0 pre-RC2).
- New: 9 tests / 27 assertions covering `::dlo/score`, `::dlo/all-ids-key`
  rename, gating, and the `dl/fulltext-search` primitive.

## Files changed
- `src/main/app/model/{account,category,item,person}.cljc` — `::dlo/all-resolver? true`
- `src/test/app/fulltext_search_integration_test.clj` — `::dlo/score` tests + regression
- `src/test/app/rc2_enhancements_test.clj` — NEW: rename + gating + primitives
- `CHANGELOG.md`, `PLAN.md`

---

# Plan: Add Native ID Support Testing

## Objective
Add native-id functionality testing to the datalevin-test-app following the implementation in fulcro-rad-datalevin.

## Status: ✅ COMPLETED

## Background
The upstream database adapter (fulcro-rad-datalevin) added native-id support (commit b95656c). This feature allows identity attributes to use Datalevin's built-in `:db/id` directly instead of domain-specific UUID or other identity keys. We need to add comprehensive tests to verify this integration works correctly.

## Native ID Implementation in fulcro-rad-datalevin

Based on the upstream commit b95656c:

### What is Native ID?
- **Purpose**: Use Datalevin's internal entity ID (`:db/id`) as the identity attribute
- **Type Requirement**: Must be of type `:long`
- **Option**: `::dlo/native-id? true` on identity attributes
- **Benefits**: Better performance, compatibility with existing databases, no need for separate lookup refs

### How It Works:

1. **Schema Generation**: Native-id attributes are skipped (uses built-in `:db/id`)
2. **Query Conversion**: Pathom queries replace native-id keys with `:db/id` in pull patterns
3. **Result Mapping**: Resolvers convert `:db/id` back to the identity key in results
4. **Save Middleware**: Handles native IDs using raw entity IDs instead of lookup refs
5. **Delta Processing**: Uses raw entity ID (e.g., `42`) instead of lookup ref (e.g., `[:person/id 42]`)

### Key Functions:
- `native-id?`: Helper to identify native-id attributes
- `pathom-query->datalevin-query`: Converts query patterns
- `datalevin-result->pathom-result`: Maps `:db/id` back to identity key
- `fix-id-keys`: Ensures proper ID mapping in resolver results

## Implementation Plan

### 1. Create New Entity Model with Native ID ✅
Create `src/main/app/model/person.cljc` with:
- `:person/id` - `:long` type with `::dlo/native-id? true`
- `:person/name` - `:string`
- `:person/email` - `:string` with unique constraint
- `:person/age` - `:long`

### 2. Update Global Model Registry ✅
Update `src/main/app/model.cljc` to include person attributes

### 3. Create Comprehensive Native ID Test Suite ✅
Create `src/test/app/native_id_test.clj` covering:
- ✅ Schema generation (verify native-id excluded)
- ✅ Helper function `native-id?` identification
- ✅ Entity creation (auto-assigned `:db/id`)
- ✅ Query by native ID (using raw entity ID)
- ✅ Update operations (using raw entity ID)
- ✅ Delete operations
- ✅ Resolver generation for native-id entities
- ✅ Resolver result mapping (`:db/id` → `:person/id`)
- ✅ Delta conversion (raw ID vs lookup ref)
- ✅ Save middleware integration

### 4. Integration Tests ✅
Add tests for:
- ✅ Pathom query processing with native IDs
- ✅ Save middleware with native ID entities
- ✅ Mixed queries (native-id + UUID entities)

### 5. UI Integration ✅
- ✅ Create `PersonForm` for creating/editing persons
- ✅ Create `PersonList` report for viewing all persons
- ✅ Add "Person" menu item to navigation
- ✅ Add person routes to statechart
- ✅ Update seed data to include sample persons

### 6. Documentation Updates ✅
- ✅ Update `CHANGELOG.md`
- ✅ Update `README.md` with native-id examples
- ✅ Update `PLAN.md` with completion status

## Files to Create/Modify

1. ✅ `src/main/app/model/person.cljc` - New entity with native-id
2. ✅ `src/main/app/model.cljc` - Add person attributes to registry
3. ✅ `src/test/app/native_id_test.clj` - Comprehensive test suite
4. ✅ `src/main/app/ui/root.cljc` - Add PersonForm, PersonList, and navigation
5. ✅ `src/dev/development.clj` - Add person seed data
6. ✅ `src/test/app/resolver_test.clj` - Update for 4 entity types
7. ✅ `CHANGELOG.md` - Document changes
8. ✅ `README.md` - Add native-id examples
9. ✅ `PLAN.md` - Update with status

## Test Coverage Goals

Aim for at least 10 tests covering:
- ✅ Schema generation excludes native-id attributes
- ✅ Helper functions correctly identify native-id attributes
- ✅ CRUD operations with auto-assigned entity IDs
- ✅ Resolver generation and execution
- ✅ Result mapping between `:db/id` and identity key
- ✅ Delta processing with raw entity IDs
- ✅ Save middleware integration
- ✅ Pathom query integration
- ✅ Mixed entity types (native + UUID)

## Breaking Changes

The upstream adapter has a breaking change:
- `delta->txn` now requires `env` as first parameter: `(delta->txn env delta)`
- Need to verify our middleware uses the correct signature

## Success Criteria

- ✅ All new tests pass
- ✅ All existing tests continue to pass
- ✅ Native-id entities can be created without explicit ID
- ✅ Queries work with raw entity IDs
- ✅ Resolvers properly map `:db/id` to identity key
- ✅ Save middleware handles native-id deltas correctly
- ✅ Documentation is comprehensive and accurate

## Verification Results

### Test Results ✅
- **Total tests**: 71 (up from 58)
- **Total assertions**: 266 (up from 212)
- **New native-id tests**: 13 tests with 48 assertions
- **All tests passing**: ✅ 0 failures

### Native-ID Tests Coverage ✅
1. ✅ `native-id-schema-generation` - Verifies native-id excluded from schema
2. ✅ `native-id-helper-function` - Tests `native-id?` helper
3. ✅ `create-person-with-auto-assigned-id` - Auto-assignment of `:db/id`
4. ✅ `create-multiple-persons` - Batch creation
5. ✅ `read-person-by-native-id` - Query by entity ID
6. ✅ `update-person-by-native-id` - Update using entity ID
7. ✅ `delete-person-by-native-id` - Delete using entity ID
8. ✅ `pathom-query-conversion` - Query pattern conversion
9. ✅ `native-id-resolver-generation` - Resolver generation
10. ✅ `native-id-resolver-result-mapping` - `:db/id` → `:person/id` mapping
11. ✅ `native-id-delta-conversion` - Delta with raw entity ID
12. ✅ `native-id-delta-new-entity` - Delta with tempid
13. ✅ `mixed-native-and-uuid-entities` - Mixed entity types

### Updated Tests ✅
- ✅ `resolver-test/resolver-generation` - Updated for 4 identity types (was 3)
- ✅ `resolver-test/id-resolver-configuration` - Updated for 4 ID resolvers
- ✅ `resolver-test/all-ids-resolver-configuration` - Updated for 4 all-IDs resolvers

### Key Functionality Verified ✅
- ✅ Schema generation correctly excludes `:person/id` (uses built-in `:db/id`)
- ✅ Helper function `native-id?` correctly identifies native-id attributes
- ✅ Entities created without explicit ID get auto-assigned `:db/id`
- ✅ Queries use raw entity IDs (no lookup refs needed)
- ✅ Pathom queries convert `:person/id` to `:db/id` in pull patterns
- ✅ Resolvers map `:db/id` back to `:person/id` in results
- ✅ Delta processing uses raw entity ID (e.g., `42`) not lookup ref
- ✅ Mixed queries work with both native-id and UUID entities
- ✅ All CRUD operations work correctly with native IDs

### UI Integration ✅
- ✅ `PersonForm` created with all person attributes
- ✅ `PersonList` report created with native-id column display
- ✅ Navigation menu includes "Person" dropdown
- ✅ Statechart routes configured for person list and form
- ✅ Person seed data added to development utilities
- ✅ UI compiles without errors (clj-kondo clean)

### Documentation ✅
- ✅ `CHANGELOG.md` updated with native-id feature description and UI integration
- ✅ `README.md` updated with native-id usage examples
- ✅ `README.md` entity models section includes Person
- ✅ `README.md` test coverage updated to 71 tests
- ✅ `PLAN.md` marked as completed with verification results

---

# Previous: Add Enum Support to Test Application

## Status: ✅ COMPLETED

## Summary
Successfully added enum support testing with:
- 3 enum attributes in Account model (role, status, permissions)
- 10 comprehensive tests (49 assertions)
- UI integration with pick-one and pick-many field styles
- Full CRUD operations and complex query testing

For detailed enum implementation, see git history.
