# Agent Guide for Datalevin RAD Test Application

Use a single PLAN.md for planning. Use a single CHANGELOG.md for changes. Do not create intermediary summary documents.
This is a test application to verify the functionality of a database adapter named fulcro-rad-datalevin
This project is checked out at /Users/mwhitford/src/datalevin-test-app
The database adapter is checked out at /Users/mwhitford/src/datalevin-test-app
The clj-nrepl-eval tool should have access to both a clj and cljs nrepl for this project, use them as needed to fulfill user requests.
The app should have a clj and cljs repl started from the editor.

App restarts can be accomplished from the repl:

```clojure
(require 'development)
(development/restart)
```

## Diagnostic Tools & Documentation

**ALWAYS start troubleshooting with RADAR diagnostics:**

This query returns useful fulcro-rad diagnostic data

```clojure
(app.server.parser/parser {} [:radar/overview])
```

This query returns the pathom-env available to resolvers

```clojure
(app.server.parser/parser {} [:radar/pathom-env])
```

## Build & Test Commands

- **Run all tests**: `clojure -M:run-tests`
- **Run single test**: `clojure -M:run-tests --focus app.middleware-test/save-middleware-is-a-function`
- **Lint command**: `clj-kondo --lint .`
- **Clean build artifacts**: `bb clean`
- **Check outdated deps**: `clojure -M:outdated`

## Code Style Guidelines

### Imports

- Group requires by purpose: mount/state, fulcro/rad libs, local namespaces, external libs
- Use `:refer` sparingly (defattr, defstate, mount helpers)
- Use reader conditionals `#?(:clj ...)` for platform-specific code in `.cljc` files

### Formatting & Conventions

- 2-space indentation, align map keys/values vertically
- Docstrings on all public vars and namespaces
- Use kebab-case for vars, namespaces; avoid underscores except platform interop
- Prefer destructuring in let/fn args: `{:keys [parser config]}` or `[{:keys [env]}]`

### RAD Attributes

- Define with `defattr` macro, one per entity field
- Use qualified keywords (`:account/id`, `:account/name`)
- Always specify `ao/schema`, `ao/identities`, and `ao/identity?` (for ID fields)
- Platform-specific options use reader conditionals: `#?@(:clj [dlo/attribute-schema ...])`

### Error Handling

- Wrap middleware with try/catch returning `{::form/errors [{:message "..."}]}`
- Use Timbre for logging: `(log/info ...)`, `(log/error ...)`
- Include context in error messages for debugging

### Testing

- Use `clojure.test` with `deftest`, `testing`, `is`
- Test middleware returns proper types (maps, not functions)
- Include REPL comment blocks for manual verification

## Key Patterns

- Mount for stateful components (`defstate parser`, `defstate connections`)
- Pathom3 for backend query processing with auto-generated resolvers
- Datalevin requires JVM flag: `--enable-native-access=ALL-UNNAMED`

```

```
