# Changelog

All notable changes to this project will be documented in this file.

## [2025-11-27] - Report Join Query Bug Fix

### Fixed
- **Critical report query bug**: Category labels not displaying in ItemList report
  - Root cause: Duplicate `:item/category` in row query (both as join and plain keyword)
  - Using `ro/row-query-inclusion` added join but column was also added as plain keyword
  - Pathom received query with both `{:item/category [:category/id :category/label]}` AND `:item/category`
  - When same key appears as both join and keyword, Pathom only returns reference ID
  - Backend was working correctly, but duplicate query keys caused data loss

### Changed
- **Replaced `ro/row-query-inclusion` with `ro/columns-EQL`** in `ItemList` report
  - `ro/columns-EQL {:item/category [:category/id :category/label]}` 
  - Overrides the default EQL for the `:item/category` column
  - Prevents duplicate query keys by replacing plain keyword with join
  - Row query now correctly: `[:item/name ... {:item/category [:category/id :category/label]} :item/id]`
- Column formatter now receives complete category data with label
- Data normalizes correctly with category labels preserved in the `:item/id` table

### Technical Details
- **RAD report query options**:
  - `ro/query-inclusions`: Adds to top-level report query only
  - `ro/row-query-inclusion`: Adds additional fields to row query (can cause duplicates if field is also in columns)
  - `ro/columns-EQL`: Overrides EQL for specific columns (preferred for joins) - prevents duplicates
- **Key lesson**: For reference columns needing joins, use `ro/columns-EQL`, not `ro/row-query-inclusion`
- **Normalization works correctly**: Category data `{:category/id uuid :category/label "..."}` stored inline in item entities
- `ro/denormalize? true` is NOT required - standard normalization preserves nested maps without idents
- Verified via backend Pathom query and CLJS REPL state inspection

## [2025-11-27] - UI Components for Items and Categories

### Added
- **Category UI components**:
  - `CategoryForm` - Form component for creating/editing categories
  - `CategoryList` - Report component displaying all categories
  - Category dropdown menu in navigation
  - Routes for `/categories` and `/category/:id`
- **Item UI components**:
  - `ItemForm` - Form component for creating/editing items (with category reference)
  - `ItemList` - Report component displaying all items
  - Item dropdown menu in navigation
  - Routes for `/items` and `/item/:id`
- **Model enhancements**:
  - Added `:category/all-categories` resolver attribute
  - Added `:item/all-items` resolver attribute

### Changed
- Updated `app.ui.root` with Category and Item forms/reports following Account pattern
- Enhanced navigation menu with Category and Item dropdowns
- Updated application statechart with new routes

### Features
- Full CRUD operations for Categories
- Full CRUD operations for Items
- Category reference picker in Item form
- Consistent UI patterns across all entities

## [2025-11-27] - Server-Side Validation

### Added
- **Server-side attribute validation middleware** (`wrap-attribute-validation`)
  - Validates all attributes in save deltas using RAD's `attr/valid-value?`
  - Enforces `ao/required?` constraints at the server level
  - Returns user-friendly error messages for validation failures
  - Prevents invalid data from being persisted to the database
- **Validation test suite** (`app.validation-test`) - 5 tests covering:
  - Attribute validation logic verification
  - Required field enforcement on updates
  - Required field enforcement on creates
  - Valid data acceptance
  - Whitespace-only string rejection
- **Documentation** (`VALIDATION_FIX_SUMMARY.md`) - detailed validation implementation guide

### Fixed
- **Critical bug**: Blank required fields could be saved to database
  - Previously, editing an existing record allowed saving with blank names
  - Client-side validation showed errors but save operation succeeded
  - Server-side validation now blocks invalid saves before database operations

### Changed
- **Middleware chain order** in `app.server.middleware`:
  - Added `wrap-attribute-validation` as outermost middleware
  - Validation now occurs before value transformations
  - Proper error propagation to client

### Technical Details
- Validation errors returned in standard RAD format
- Uses existing attribute definitions (`ao/required?`, `ao/valid?`)
- Validates during both create and update operations
- Handles nil values, empty strings, and whitespace-only strings
- All 48 tests pass with 163 assertions

## [2025-11-26] - Comprehensive Test Suite

### Added
- **Complete test suite** with 43 tests and 151 assertions
- **Test utilities** (`app.test-utils`) for temporary database management
- **CRUD operation tests** (`app.crud-test`) - 12 tests covering:
  - Single and batch CREATE operations
  - Entity READ operations with and without references
  - UPDATE operations for single and multiple attributes
  - DELETE operations including batch deletions
- **Schema generation tests** (`app.schema-test`) - 4 tests validating:
  - Automatic schema generation from RAD attributes
  - Unique constraint configuration
  - Reference type mapping
  - All value type conversions
- **Resolver tests** (`app.resolver-test`) - 7 tests verifying:
  - Pathom3 resolver generation
  - Batch operation support
  - Input/output specifications
  - Reference navigation in resolvers
- **Constraint tests** (`app.constraint-test`) - 9 tests documenting:
  - Unique value constraint enforcement
  - Identity attribute upsert behavior
  - Reference handling (including dangling refs)
  - Data integrity rules
- **Pathom integration tests** (`app.pathom-integration-test`) - 7 tests ensuring:
  - Collection queries
  - Single entity queries by ident
  - Reference navigation
  - Empty and non-existent entity handling
- **Middleware tests** (`app.middleware-test`) - 4 tests confirming:
  - Save middleware configuration
  - Delete middleware configuration
  - Proper return value types

### Documentation
- `TEST_SUMMARY.md` - Comprehensive test documentation
- `EVALUATION_REPORT.md` - Detailed evaluation of all features
- Updated `README.md` with testing information
- Added `CHANGELOG.md` for project history

### Testing Improvements
- Temporary database per test with automatic cleanup
- Isolated test environments
- Clear test patterns and examples
- REPL-friendly test development

### Key Findings Documented
- Identity attributes perform upserts (not errors) on duplicates
- Unique value constraints properly throw exceptions
- Datalevin allows dangling references
- Empty aggregation queries return `nil`
- References require entity IDs, not UUIDs directly

## [Initial] - Project Setup

### Added
- Initial project structure
- Model definitions for Account, Category, Item
- Database adapter integration
- Pathom3 parser configuration
- RAD middleware setup
- Development REPL utilities
- Sample data seeding
- HTTP server with Ring

### Features
- Automatic schema generation
- Pathom3 resolver generation
- Save/delete middleware
- Reference support
- Multiple entity types
