# Changelog

All notable changes to this project will be documented in this file.

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
