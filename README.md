# Military Asset Management System (MAMS)

A complete, database-backed asset management system for military units: a
**Spring Boot 3 / MySQL 8** REST API and a separate **React 18 + Vite + Bootstrap 5**
single page application. No mock data, no client-side database — every figure on
screen comes from the MySQL schema in `database/military_asset_management.sql`.

```
MMS/
├── backend/          Spring Boot 3.5 · Java 17 · Spring Security (JWT) · JPA/Hibernate · MySQL
├── frontend/         React 18 · Vite 5 · React Router 6 · Bootstrap 5
├── database/         military_asset_management.sql (schema, seed data, reporting view)
├── docs/             API.md - full REST reference
├── scripts/          setup-database.ps1, rbac-test.ps1, smoke-test.ps1, integrity-test.ps1
├── render.yaml       Render blueprint for the backend container
├── frontend/vercel.json  Vercel config for the SPA (rewrites, cache headers)
└── .env.example      every environment variable used by both applications
└── .env.example      every environment variable used by both applications
```

---

## 1. Prerequisites

| Tool | Version used | Notes |
| --- | --- | --- |
| JDK | 17 or newer | Built and tested on JDK 25 with `maven.compiler.release=17` |
| Maven | 3.9+ | |
| Node.js | 18+ | Built and tested on Node 22 |
| MySQL | 8.0+ | Schema uses check constraints, generated columns and `utf8mb4` |

The MySQL client must be reachable. On Windows the `mysql` binary is usually at
`C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe`; the setup script finds it
automatically.

---

## 2. Quick start

```powershell
# 1. Create and seed the schema (destructive: drops and recreates the database)
powershell -ExecutionPolicy Bypass -File .\scripts\setup-database.ps1 -Password <your-local-mysql-password>

# 2. Provide the secrets the API requires. There is no committed default for
#    any of these, by design. Set them once per shell.
$env:DB_USERNAME = "<your-local-mysql-user>"
$env:DB_PASSWORD = "<your-local-mysql-password>"
$env:JWT_SECRET  = (openssl rand -base64 48)
$env:DEFAULT_ADMIN_PASSWORD = "<a-local-admin-password>"

# 3. Start the API on http://localhost:8080
cd backend
mvn spring-boot:run

# 4. In a second terminal, start the UI on http://localhost:5173
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173> and sign in as `admin` with the
`DEFAULT_ADMIN_PASSWORD` you set above. Copying `.env.example` to `.env` and
filling it in is a convenient alternative; `.env` is git-ignored.

The Vite dev server proxies `/api` to `http://localhost:8080`, so no CORS
configuration is needed in development.

### Verify the installation

Three suites exercise the running stack through real HTTP calls and the real
MySQL schema. Start the backend and UI first, then run them in order.

```powershell
# The suites sign in as the seeded accounts, whose passwords are not committed.
# Export them once per shell, or pass each script's matching -...Password switch.
$env:MAMS_ADMIN_PASSWORD      = "<admin-password>"
$env:MAMS_COMMANDER_PASSWORD  = "<commander-password>"
$env:MAMS_LOGISTICS_PASSWORD  = "<logistics-password>"
$env:MYSQL_PASSWORD           = "<mysql-password>"

# 97 checks - every role, every base scope, and token forgery/tampering
powershell -ExecutionPolicy Bypass -File .\scripts\rbac-test.ps1

# 76 checks - health, dashboard formula, the four transaction flows including
# rollback cases, administration, and the append-only audit trail
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1

# 29 checks - API/MySQL reconciliation, absence of mock data, the
# no-negative-stock guarantees, transaction rollback, and audit coverage
powershell -ExecutionPolicy Bypass -File .\scripts\integrity-test.ps1
```

Each script also accepts the values as parameters (`-AdminPassword`,
`-DbPassword`, `-BaseUrl`, and so on); see the header of each file. All three
exit early with an actionable message if a required password is missing, rather
than falling back to a value from the repository.

The RBAC and smoke suites write real transactions to the database. Re-run
`setup-database.ps1 -DropFirst` afterwards to return to the seeded state.

---

## 3. Configuration

Both applications read their configuration from environment variables.
`.env.example` at the repository root documents every one. The most important:

| Variable | Used by | Default | Purpose |
| --- | --- | --- | --- |
| `DB_URL` | backend | `jdbc:mysql://localhost:3306/military_asset_management?...` | JDBC URL, schema name must stay `military_asset_management` |
| `DB_USERNAME` | backend | **none — required** | Database user |
| `DB_PASSWORD` | backend | **none — required** | Database password |
| `JWT_SECRET` | backend | **none — required** | HS256 signing key. The app will not start without it and no fallback is committed. Generate with `openssl rand -base64 48`; keep it only in the platform secret store |
| `JWT_EXPIRATION_MS` | backend | `43200000` | Token lifetime (12 h) |
| `CORS_ALLOWED_ORIGINS` | backend | `http://localhost:5173,...` | Comma separated browser origins |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | backend | empty | Optional wildcard origins, e.g. `https://*.vercel.app`, so preview deployments are accepted |
| `SERVER_PORT` | backend | `8080` | API port (Render injects `PORT`, which the app honours) |
| `VITE_API_BASE_URL` | frontend | empty | API **origin** for a split deployment, with no trailing `/api` (the SPA adds `/api/...` itself). Leave empty in dev, where Vite proxies `/api` |
| `VITE_PROXY_TARGET` | frontend | `http://localhost:8080` | Dev-server proxy target |

**Production checklist**

1. Set a unique `JWT_SECRET` (`openssl rand -base64 48`). It is mandatory: the API
   refuses to start without it, so a missing or placeholder value is a startup
   failure rather than a silent downgrade to a published key.
2. Point `DB_USERNAME` / `DB_PASSWORD` at a least-privilege account (the app needs
   DML, not DDL — `spring.jpa.hibernate.ddl-auto=validate`).
3. Set `CORS_ALLOWED_ORIGINS` to the exact frontend origin, never `*`.
4. Change the seeded passwords, or set `BOOTSTRAP_ENABLED=false` and create the first
   administrator through a controlled process.
5. Serve `frontend/dist` from a static host or CDN and set `VITE_API_BASE_URL` at
   build time (`VITE_API_BASE_URL=https://api.mams.example npm run build`).
6. Terminate TLS in front of both applications; JWTs are bearer tokens.
7. Set `LOG_FILE` to a path the service account can write, and forward it to your
   log collector.

---

## 4. Roles and access control

| Capability | ADMIN | BASE_COMMANDER | LOGISTICS_OFFICER |
| --- | :---: | :---: | :---: |
| Dashboard, inventory, purchases, transfers, audit logs | ✔ | own base | ✔ |
| Assignments, expenditures | ✔ | own base | — |
| Personnel administration | ✔ | — | — |
| User / base / equipment type administration | ✔ | — | — |

* Enforcement lives in Spring Security (`@PreAuthorize`) plus a base-scope service;
  hiding a menu item in the UI is only a convenience.
* A `BASE_COMMANDER` is bound to one base. Every request is rewritten to that base
  and a request for another base returns `403`.
* Passwords are stored as BCrypt hashes. Passwords are never returned by the API.

### Seeded accounts

| Username | Password | Role | Base |
| --- | --- | --- | --- |
| Username | Role | Base |
| --- | --- | --- |
| `admin` | ADMIN | — (all bases) |
| `gen.alpha` | BASE_COMMANDER | Alpha Army Base |
| `gen.bravo` | BASE_COMMANDER | Bravo Army Base |
| `gen.delta` | BASE_COMMANDER | Delta Forward Base |
| `logistics` | LOGISTICS_OFFICER | — |
| `logistics2` | LOGISTICS_OFFICER | — |

Passwords are deliberately not written down here or in `docs/API.md`. Set
`DEFAULT_ADMIN_PASSWORD` before the first run, and reset every other seeded
account's password before any real deployment.

---

## 5. Inventory accounting

The dashboard is computed from the transaction ledger, never from cached counters:

```
Net Movement    = Purchases + Transfer In - Transfer Out
Closing Balance = Opening Balance + Purchases + Transfer In
                  - Transfer Out - Assigned - Expended
```

`Opening Balance` is the stock on hand before the period start, so the closing
figure always reconciles with the live `stock_balances` table.

### Consistency rules

* Every write that touches stock runs in a single `@Transactional` method.
* Stock rows are locked with `SELECT ... FOR UPDATE` (pessimistic write lock) in a
  deterministic base order, so concurrent transfers cannot deadlock or oversell.
* A transfer that would take a base below zero is rejected with `409` and rolls back
  completely — the destination is not credited either.
* Personnel can only receive assets at the base they are posted to.
* Duplicate codes, referenced records and cross-base references return `409` rather
  than producing a partially written row.

---

## 6. Audit trail

* Every create, update, delete, login, logout, failed login, status change and
  password reset is written to `audit_logs`.
* The trail is **append-only**: `AuditLogController` exposes `GET` endpoints only.
  Any write verb against `/api/audit-logs` returns `405`.
* Audit rows are written in a separate transaction (`REQUIRES_NEW`), so a failed
  business operation still leaves evidence of the attempt while the business
  rollback stands.
* Base commanders see only entries for their own base.

---

## 7. Database

`database/military_asset_management.sql` is a single re-runnable script that drops
and recreates the schema, creates every constraint and index, inserts the demo data
and creates the `v_stock_summary` reporting view.

| Table | Purpose |
| --- | --- |
| `users`, `roles` | Accounts and role assignments (BCrypt password hashes) |
| `bases` | Military bases |
| `equipment_types` | Equipment catalogue with unit of measure |
| `personnel` | Personnel roster, each posted to one base |
| `stock_balances` | Current on-hand quantity per base and equipment type |
| `purchases`, `purchase_items` | Inbound stock and cost |
| `transfers`, `transfer_items` | Base-to-base movements |
| `assignments` | Who is using which asset |
| `expenditures` | Consumption, loss and damage |
| `audit_logs` | Append-only activity trail |

`spring.jpa.hibernate.ddl-auto=validate` means the schema in the script is the
source of truth: the application never creates or alters tables at startup and will
refuse to start if the database and the entities disagree.

---

## 8. Application screens

| Screen | Route | What it does |
| --- | --- | --- |
| Login | `/login` | JWT sign-in |
| Dashboard | `/` | Period filters, opening/purchases/transfers/assigned/expended/closing tiles, per-base rows, formula breakdown, Net Movement drill-down |
| Inventory | `/inventory` | Live on-hand balances with base and equipment filters |
| Purchases | `/purchases` | Searchable history with expandable line items; records new purchases |
| Transfers | `/transfers` | History with line items; records base-to-base transfers |
| Assignments | `/assignments` | Who holds which asset; issues new assets |
| Expenditures | `/expenditures` | Consumption and loss records |
| Audit logs | `/audit-logs` | Read-only activity trail with entity, action, base and time filters |
| Personnel | `/personnel` | Roster administration (ADMIN) |
| Users | `/users` | Accounts, roles, enable/disable, password reset (ADMIN) |
| Bases | `/bases` | Base administration (ADMIN) |
| Equipment types | `/equipment-types` | Catalogue administration (ADMIN) |

The JWT is kept in `localStorage` for session continuity only; no application data
is cached there, and every page reads live from the API.

---

## 9. Production build

```powershell
# Backend
cd backend
mvn clean package
java -jar target/military-asset-management-backend-1.0.0.jar

# Frontend
cd ..\frontend
# API origin only - no trailing /api, the SPA appends /api/... itself
$env:VITE_API_BASE_URL = "https://api.mams.example.com"
npm run build          # emits frontend/dist
```

Serve `frontend/dist` with any static file server, configuring it to fall back to
`index.html` for client-side routes.

---

## 10. Deployment

### 10.1 Database

Create the schema on the production MySQL 8 host before the first deploy. Aiven's
default database is usually named `defaultdb`, so either create a database called
`military_asset_management` on it or point `DB_URL` at the one you did create.

```powershell
$env:MYSQL_PWD = "<db password>"
mysql -h <host> -P <port> -u <user> --default-character-set=utf8mb4 `
      -e "source database/military_asset_management.sql"
Remove-Item Env:\MYSQL_PWD
```

Aiven requires TLS. Keep `ssl-mode=REQUIRED` in `DB_URL`.

### 10.2 Split deployment: backend on Render, frontend on Vercel

This is the configuration in use. The Spring Boot API runs as a long-lived Docker
container on Render, and the static SPA runs on Vercel.

| Piece | Where | How it runs |
| --- | --- | --- |
| `backend` | Render | `backend/Dockerfile` with Docker context `backend/`, health-checked at `/api/health` |
| `frontend` | Vercel | Root Directory `frontend/`, native Vite build, output `dist` |

`render.yaml` is a Render blueprint that builds the same backend. Set `DB_*` and
`DEFAULT_ADMIN_PASSWORD` in **Render → the service → Environment**. The blueprint
uses the **free** plan, which sleeps after 15 minutes; change `plan` to `starter`
when the service must stay reachable.

Point the Vercel project at the repository with **Root Directory = `frontend`**,
then set `VITE_API_BASE_URL` to the **API origin**, without a trailing `/api`,
because the SPA appends its own `/api/...` paths:

```
VITE_API_BASE_URL=https://mams-backend-snii.onrender.com
```

`VITE_*` values are inlined at build time, so changing it requires a redeploy.
Because the two are on different origins, the Render service must allow the Vercel
origin: set `CORS_ALLOWED_ORIGINS` to the Vercel URL and
`CORS_ALLOWED_ORIGIN_PATTERNS` to `https://*.vercel.app` so preview deployments
are accepted.

`render.yaml` remains in the repository so the service can be recreated from
scratch or moved to a paid plan without rework.

### 10.4 Order of operations

1. Provision MySQL and apply the schema (§10.1). The backend validates the schema
   on startup and will not start without it.
2. Deploy, then wait for `https://<your-domain>/api/health` to return `200`.
3. Sign in and walk one transaction of each type per role; confirm the audit log
   records them and that the figures survive a hard refresh.

---

## 11. Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| Backend exits with `Schema-validation: missing table` | The database was not created from the SQL script. Run `scripts\setup-database.ps1`. |
| `Access denied for user` | `DB_USERNAME` / `DB_PASSWORD` do not match your MySQL account. |
| `JWT signature does not validate` | `JWT_SECRET` differs between the processes that issued and use the token; clear the browser session. |
| `Could not resolve placeholder 'JWT_SECRET'` | The signing key is not set. Add it to the environment and restart; the API will not start without it by design. |
| UI shows 403 on every page | The signed-in account is a base commander using a token from a previous base assignment; sign out and back in. |
| CORS error in the browser | Add the browser origin to `CORS_ALLOWED_ORIGINS`. The Vite dev server on port 5173 is allowed by default. |
| Empty dashboard | The date range excludes all transactions; clear the From/To filters. |
| `409` on a transfer | The source base does not hold the requested quantity, or source and destination are the same. |
| Render deploy fails with `Schema-validation` | The database exists but the schema script was never applied. See §10.1. |
| UI loads but every request fails on a deployed build | `VITE_API_BASE_URL` is empty or wrong. It is inlined at build time, so rebuild after changing it. |
| Sign-in fails with 404 or 401 and the network tab shows `/api/api/...` | `VITE_API_BASE_URL` ends in `/api`. Set it to the origin only; the SPA appends `/api/...` itself. |
| Vercel 404 on a deep link such as `/personnel` | The SPA rewrite is missing. `frontend/vercel.json` supplies it; do not delete it. |
| `Public Key Retrieval is not allowed` | Aiven requires `allowPublicKeyRetrieval=true` in `DB_URL`. |
