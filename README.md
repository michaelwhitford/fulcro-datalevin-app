# Datalevin RAD Test Application

A test application for the `fulcro-rad-datalevin` plugin, demonstrating integration of [Fulcro RAD](https://github.com/fulcrologic/fulcro-rad) with [Datalevin](https://github.com/juji-io/datalevin).

## Features

- **Automatic Schema Generation**: RAD attributes automatically generate Datalevin schemas
- **Pathom3 Resolvers**: Auto-generated resolvers for entity queries
- **Save/Delete Middleware**: RAD form operations persist to Datalevin
- **Multiple Entity Types**: Accounts, Categories, Items, and Persons
- **Reference Support**: Item to Category relationships
- **Native ID Support**: Person entities use Datalevin's built-in `:db/id` for better performance
- **Enum Support**: Account entities support role, status, and permissions enums

## Prerequisites

- Clojure CLI tools
- Node.js (for shadow-cljs)
- Java 11+ with native access (for Datalevin)

## Testing

This project includes a comprehensive test suite with **71 tests** covering all aspects of the Datalevin adapter:

```bash
# Run all tests
clojure -M:run-tests

# Run specific test namespace
clojure -M:run-tests --focus app.crud-test

# Run single test
clojure -M:run-tests --focus app.crud-test/create-single-account
```

Test coverage includes:

- **CRUD Operations** - Create, Read, Update, Delete for all entity types
- **Schema Generation** - Automatic Datalevin schema from RAD attributes
- **Resolver Generation** - Pathom3 resolver creation and configuration
- **Constraints** - Unique values, identity attributes, references
- **Pathom Integration** - Query processing and navigation
- **Middleware** - Save and delete middleware configuration
- **Native ID Support** - Datalevin's built-in `:db/id` as identity attribute
- **Enum Support** - Single and many-cardinality enum attributes

See [TEST_SUMMARY.md](TEST_SUMMARY.md) for detailed test documentation.

## Project Structure

```
datalevin-test-app/
├── deps.edn                 # Clojure dependencies
├── shadow-cljs.edn          # ClojureScript build config
├── src/
│   ├── main/
│   │   └── app/
│   │       ├── model/       # RAD entity definitions
│   │       │   ├── account.cljc
│   │       │   ├── category.cljc
│   │       │   └── item.cljc
│   │       ├── server/      # Backend components
│   │       │   ├── core.clj      # HTTP server
│   │       │   ├── database.clj  # Datalevin connections
│   │       │   ├── middleware.clj # Save/delete handlers
│   │       │   └── parser.clj    # Pathom3 configuration
│   │       ├── ui/          # Frontend components
│   │       │   └── root.cljs
│   │       └── client.cljs  # Fulcro app setup
│   └── dev/
│       └── user.clj         # REPL development utilities
├── resources/
│   └── public/
│       └── index.html       # Application entry point
└── data/                    # Database storage (created at runtime)
```

## Troubleshooting & Diagnostics

This project includes **fulcro-radar** for AI-assisted troubleshooting:

- 🎯 **Fulcro-RADAR Integration** - Single-query diagnostics via `(parser {} [:radar/overview])`

### Quick Health Check

```clojure
;; Single command shows: mount states, entities, forms, reports,
;; enums, constraints, relationships, and entity counts
(require '[app.server.parser :refer [parser]])
(parser {} [:radar/overview])
```

## Quick Start

### 1. Install Dependencies

```bash
npm install
```

### 2. Start the Backend Server

```bash
clojure -A:dev
```

In the REPL:

```clojure
(start)  ; Starts database and HTTP server on port 3000
```

### 3. Compile Frontend (in another terminal)

```bash
npx shadow-cljs watch main
```

### 4. Access the Application

Open http://localhost:3000 in your browser.

## REPL Development

The `user` namespace provides convenient utilities:

```clojure
;; Start/stop server
(start)
(stop)
(restart)

;; Reset database (deletes all data)
(reset-db)

;; Add sample data
(seed-sample-data)

;; Query the database
(show-all-accounts)
(show-all-categories)
(show-all-items)

;; Check metrics
(show-metrics)

;; Test Pathom queries
(test-pathom-query [{:app.server.parser/all-accounts [:account/id :account/name]}])
```

## Testing the Plugin

### 1. Schema Generation

The application automatically generates Datalevin schema from RAD attributes:

```clojure
(require '[us.whitford.fulcro.rad.database-adapters.datalevin :as dl])
(require '[app.model :as model])

(dl/automatic-schema :main model/all-attributes)
;; => {:account/id {:db/valueType :db.type/uuid :db/unique :db.unique/identity}
;;     :account/name {:db/valueType :db.type/string}
;;     ...}
```

### 2. Resolver Generation

Resolvers are automatically created for identity attributes:

```clojure
(def resolvers (dl/generate-resolvers model/all-attributes))
```

### 3. Save Operations

Create entities via the UI or directly:

```clojure
(require '[app.server.middleware :as middleware])
(require '[us.whitford.fulcro.rad.database-adapters.datalevin-options :as dlo])
(require '[com.fulcrologic.rad.form :as form])

(let [delta {[:account/id temp-id]
             {:account/name {:before nil :after "Test User"}
              :account/email {:before nil :after "test@example.com"}}}
      env {::dlo/connections database/connections
           ::form/params {::form/delta delta}}]
  ((middleware/save-middleware identity) env))
```

### 4. Native ID Support

Native IDs allow entities to use Datalevin's built-in `:db/id` directly:

```clojure
;; Define a native-id attribute
(defattr id :person/id :long
  {ao/identity? true
   ao/schema :main
   ::dlo/native-id? true})  ;; Uses :db/id instead of separate UUID

;; Create entity - ID is auto-assigned by Datalevin
(d/transact! conn [{:person/name "Alice"
                    :person/email "alice@test.com"}])

;; Query using entity ID
(let [eid (ffirst (d/q '[:find ?e :where [?e :person/name "Alice"]] db))]
  (d/pull db [:db/id :person/name :person/email] eid))
;; => {:db/id 1, :person/name "Alice", :person/email "alice@test.com"}

;; Resolvers automatically map :db/id to :person/id in Pathom results
```

**Benefits of Native IDs:**

- Better performance (no lookup ref translation needed)
- Compatibility with existing Datalevin databases
- Simplified entity creation (auto-assigned IDs)
- Smaller transaction payloads

**Requirements:**

- Must be `:long` type
- Set `::dlo/native-id? true` on identity attribute
- Schema generation automatically excludes native-id attributes

## API Endpoints

- `POST /api` - Execute EQL queries via Pathom3

## Configuration

### Database Location

Modify `app.server.database/db-path` to change the database storage location (default: `data/main-db`).

### JVM Options

Datalevin requires native access. Run with:

```bash
clojure -J--enable-native-access=ALL-UNNAMED -A:dev
```

Or add to your aliases in `deps.edn`:

```clojure
:jvm-opts ["--enable-native-access=ALL-UNNAMED"]
```

## Entity Models

### Account

- `id` (uuid, identity)
- `name` (string, required)
- `email` (string, required, unique)
- `active?` (boolean)
- `created-at` (instant)
- `role` (enum: `:admin`, `:user`, `:guest`)
- `status` (enum: `:status/active`, `:status/inactive`, `:status/pending`)
- `permissions` (enum, many: `:read`, `:write`, `:execute`)

### Category

- `id` (uuid, identity)
- `label` (string, required, unique)

### Item

- `id` (uuid, identity)
- `name` (string, required)
- `description` (string)
- `price` (double)
- `in-stock` (int)
- `category` (ref to category/id)

### Person (Native ID Example)

- `id` (long, identity, uses Datalevin's `:db/id`)
- `name` (string, required)
- `email` (string, required, unique)
- `age` (long)
- `bio` (string)

## Notes

- Database files are stored in the `data/` directory
- The database is automatically created on first run
- Schema updates are applied via `ensure-schema!`
- Temporary databases for testing use `with-temp-database` macro

## License

MIT Copyright (c) 2025 Michael Whitford
