# Datalevin RAD Test Application

A test application for the `fulcro-rad-datalevin` plugin, demonstrating integration of [Fulcro RAD](https://github.com/fulcrologic/fulcro-rad) with [Datalevin](https://github.com/juji-io/datalevin).

## Features

- **Automatic Schema Generation**: RAD attributes automatically generate Datalevin schemas
- **Pathom3 Resolvers**: Auto-generated resolvers for entity queries
- **Save/Delete Middleware**: RAD form operations persist to Datalevin
- **Metrics & Observability**: Track database operations
- **Multiple Entity Types**: Accounts, Categories, and Items
- **Reference Support**: Item to Category relationships

## Prerequisites

- Clojure CLI tools
- Node.js (for shadow-cljs)
- Java 11+ with native access (for Datalevin)

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

## Quick Start

### 1. Install Dependencies

```bash
npm install
```

### 2. Start the Backend Server

```bash
clj -A:dev
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
           ::form/delta delta}]
  ((middleware/save-middleware identity) env))
```

### 4. Metrics

Monitor database operations:

```clojure
(dl/get-metrics)
;; => {:transaction-count 5
;;     :transaction-errors 0
;;     :query-count 12
;;     :total-transaction-time-ms 150}
```

## API Endpoints

- `POST /api` - Execute EQL queries via Pathom3
- `POST /save` - Handle RAD form saves
- `POST /delete` - Handle RAD form deletes
- `GET /health` - Health check endpoint

## Configuration

### Database Location

Modify `app.server.database/db-path` to change the database storage location (default: `data/main-db`).

### JVM Options

Datalevin requires native access. Run with:

```bash
clj -J--enable-native-access=ALL-UNNAMED -A:dev
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

## Notes

- Database files are stored in the `data/` directory
- The database is automatically created on first run
- Schema updates are applied via `ensure-schema!`
- Temporary databases for testing use `with-temp-database` macro

## License

MIT Copyright (c) 2025 Michael Whitford
