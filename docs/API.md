# REST API Reference

Base URL: `http://localhost:8080`
All endpoints are prefixed with `/api`.
All endpoints except `POST /api/auth/login` and `GET /api/health` require a bearer token.

---

## Conventions

### Authentication

```
POST /api/auth/login
Content-Type: application/json

{ "username": "admin", "password": "admin123" }
```

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInMs": 86400000,
  "user": {
    "id": 1,
    "username": "admin",
    "fullName": "System Administrator",
    "email": "admin@mams.local",
    "role": "ADMIN",
    "baseId": null,
    "baseCode": null,
    "baseName": null
  }
}
```

Send the token on every subsequent call:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

`GET /api/auth/me` returns the same `user` object for the current token.

### Roles

| Role | Capabilities |
| --- | --- |
| `ADMIN` | Full access to every endpoint, including user, base, equipment type and personnel administration. |
| `BASE_COMMANDER` | Everything except user/base/equipment administration, and always limited to the base bound to the account. Cross-base requests return `403`. |
| `LOGISTICS_OFFICER` | Dashboard, inventory, purchases, transfers and audit logs only. Assignments, expenditures, personnel and administration return `403`. |

Role checks are enforced on the backend. The frontend only hides links it knows the
current user cannot use.

### Pagination

List endpoints accept `page` (0-based, default `0`) and `size` (default `20`):

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

### Errors

```json
{
  "timestamp": "2026-01-15T10:22:31.123",
  "status": 409,
  "error": "Conflict",
  "message": "Insufficient stock at Bravo Army Base for 24H Ration Pack: requested 40, available 25. Stock cannot go negative.",
  "path": "/api/transfers",
  "fieldErrors": { "quantity": "quantity must be greater than zero" }
}
```

| Status | Meaning |
| --- | --- |
| `400` | Validation or malformed parameter (`fieldErrors` lists the offending fields). |
| `401` | Missing, expired or invalid token, or bad credentials / disabled account. |
| `403` | Authenticated but not permitted (role or base scope). |
| `404` | Unknown endpoint or resource. |
| `405` | Unsupported verb, e.g. any write attempt against the audit trail. |
| `409` | Business rule violation: duplicate code, referenced record, insufficient stock, same-base transfer, cross-base personnel. |

---

## Health

### `GET /api/health`

Public liveness probe.

```json
{ "status": "UP", "service": "military-asset-management", "time": "2026-01-15T10:00:00" }
```

---

## Dashboard

### `GET /api/dashboard`

Query: `baseId`, `equipmentTypeId`, `from` (`yyyy-MM-dd`), `to` (`yyyy-MM-dd`).

All three roles may call it. `BASE_COMMANDER` requests are forced to their own base;
asking for another base returns `403`.

```json
{
  "from": "2026-01-01",
  "to": "2026-12-31",
  "baseId": null,
  "equipmentTypeId": null,
  "totals": {
    "openingBalance": 770, "purchases": 140, "transferIn": 30, "transferOut": 30,
    "netMovement": 140, "assigned": 65, "expended": 45, "closingBalance": 800
  },
  "rows": [
    {
      "baseId": 1, "baseCode": "ALPHA", "baseName": "Alpha Army Base",
      "equipmentTypeId": 1, "equipmentCode": "RAT-24H", "equipmentName": "24H Ration Pack",
      "unit": "pcs",
      "openingBalance": 200, "purchases": 100, "transferIn": 0, "transferOut": 0,
      "netMovement": 100, "assigned": 40, "expended": 25, "closingBalance": 235
    }
  ]
}
```

Formulas (enforced by the service and verified by the smoke test):

```
Net Movement    = Purchases + Transfer In - Transfer Out
Closing Balance = Opening Balance + Purchases + Transfer In
                  - Transfer Out - Assigned - Expended
```

`Opening Balance` is the stock carried in before `from`; `Assigned` and `Expended`
are cumulative up to `to`.

### `GET /api/dashboard/movements`

Drill-down behind the Net Movement figure.

| Parameter | Required | Notes |
| --- | --- | --- |
| `type` | yes | `PURCHASE`, `TRANSFER_IN` or `TRANSFER_OUT` |
| `baseId` | yes for all three types | 409 if missing for `PURCHASE` / `TRANSFER_IN` / `TRANSFER_OUT` |
| `equipmentTypeId` | no | |
| `from`, `to` | no | `yyyy-MM-dd` |

```json
{
  "type": "PURCHASE",
  "count": 2,
  "lines": [
    {
      "id": 8,
      "movementType": "PURCHASE",
      "referenceNo": "PUR-2026-0004",
      "movementDate": "2026-01-15",
      "fromBase": null,
      "toBase": "BRAVO",
      "equipmentCode": "RAT-24H",
      "equipmentName": "24H Ration Pack",
      "unit": "pcs",
      "quantity": 25
    }
  ]
}
```

For `PURCHASE` the `fromBase` is null and `toBase` is the receiving base; for
`TRANSFER_IN` only `toBase` is set; for `TRANSFER_OUT` only `fromBase` is set.

---

## Inventory

### `GET /api/inventory`

Paginated stock balances. Query: `baseId`, `equipmentTypeId`, `page`, `size`.

```json
{
  "content": [
    {
      "id": 1, "baseId": 1, "baseCode": "ALPHA", "baseName": "Alpha Army Base",
      "equipmentTypeId": 1, "equipmentCode": "RAT-24H", "equipmentName": "24H Ration Pack",
      "category": "Rations", "unit": "pcs",
      "openingBalance": 200, "onHandQuantity": 235,
      "updatedAt": "2026-01-15T10:22:31"
    }
  ],
  "page": 0, "size": 20, "totalElements": 11, "totalPages": 1
}
```

### `GET /api/inventory/all`

Same filters, unpaged array. Useful for exports and charts.

---

## Purchases

Purchases increase stock. Roles: `ADMIN`, `BASE_COMMANDER` (own base), `LOGISTICS_OFFICER`.

### `GET /api/purchases`

Query: `baseId`, `equipmentTypeId`, `from`, `to`, `page`, `size`.
Returns `referenceNo`, `supplier`, `invoiceNo`, `purchaseDate`, `totalCost`,
`createdBy` and the nested `items` array.

### `GET /api/purchases/{id}`

### `POST /api/purchases` → `201`

```json
{
  "baseId": 2,
  "supplier": "Highland Supply Co",
  "invoiceNo": "INV-99120",
  "purchaseDate": "2026-01-15",
  "remarks": "Q1 replenishment",
  "items": [
    { "equipmentTypeId": 1, "quantity": 25, "unitCost": 12.50 }
  ]
}
```

* `referenceNo` is optional; the server generates `PUR-<year>-<sequence>` when omitted.
* `totalCost` and every `lineTotal` are computed by the server.
* The whole request is one transaction: all lines succeed or none do.
* Stock rows are locked pessimistically, so concurrent purchases cannot oversell.

---

## Transfers

A transfer debits the source base and credits the destination base inside a single
transaction.

### `GET /api/transfers`

Query: `baseId` (matches either side), `equipmentTypeId`, `from`, `to`, `page`, `size`.

### `GET /api/transfers/{id}`

### `POST /api/transfers` → `201`

```json
{
  "sourceBaseId": 2,
  "destinationBaseId": 3,
  "transferDate": "2026-01-16",
  "remarks": "Rebalancing winter stock",
  "items": [
    { "equipmentTypeId": 1, "quantity": 20 }
  ]
}
```

Business rules (all return `409`):

* `sourceBaseId` and `destinationBaseId` must differ.
* The source base must hold the requested quantity; stock can never go negative.
* Both stock rows are locked in a deterministic order to avoid deadlocks.
* On any failure nothing is written: source stock, destination stock and the
  transfer header all roll back.

---

## Assignments

Assignments record who is using which asset and reduce stock.
Roles: `ADMIN`, `BASE_COMMANDER` (own base only).

### `GET /api/assignments`

Query: `baseId`, `equipmentTypeId`, `personnelId`, `from`, `to`, `page`, `size`.

```json
{
  "content": [
    {
      "id": 5, "baseId": 2, "baseCode": "BRAVO", "baseName": "Bravo Army Base",
      "equipmentTypeId": 1, "equipmentCode": "RAT-24H", "equipmentName": "24H Ration Pack",
      "unit": "pcs",
      "personnelId": 4, "personnelServiceNumber": "MIL-1004", "personnelName": "Capt. Dana Reyes",
      "quantity": 5, "assignedDate": "2026-01-19", "remarks": "Field exercise",
      "createdBy": "admin", "createdAt": "2026-01-19T08:00:00"
    }
  ]
}
```

### `GET /api/assignments/{id}`

### `POST /api/assignments` → `201`

```json
{
  "baseId": 2,
  "personnelId": 4,
  "equipmentTypeId": 1,
  "quantity": 5,
  "assignedDate": "2026-01-19",
  "remarks": "Field exercise"
}
```

Business rules: the personnel member must be posted to the selected base (`409`
otherwise) and the base must hold enough stock (`409` otherwise). Assignments are
immutable; there is no update or delete endpoint by design.

---

## Expenditures

Expenditures consume stock (training, damage, consumption).
Roles: `ADMIN`, `BASE_COMMANDER` (own base only).

### `GET /api/expenditures`

Query: `baseId`, `equipmentTypeId`, `from`, `to`, `page`, `size`.

### `GET /api/expenditures/{id}`

### `POST /api/expenditures` → `201`

```json
{
  "baseId": 2,
  "equipmentTypeId": 1,
  "quantity": 3,
  "expendedDate": "2026-01-20",
  "reason": "Training",
  "remarks": "Winter exercise rations"
}
```

`reason` is required and capped at 150 characters. Insufficient stock returns `409`
and changes nothing.

---

## Reference data

### `GET /api/bases` · `GET /api/bases/{id}`

Readable by every authenticated role.

### `POST /api/bases` · `PUT /api/bases/{id}` · `DELETE /api/bases/{id}`

`ADMIN` only.

```json
{ "code": "DELTA", "name": "Delta Forward Base", "location": "North ridge", "commander": "Col. A. Rao" }
```

`DELETE` returns `204` for an unused base and `409` if any stock, personnel or
transaction history references it.

### `GET /api/equipment-types` · `GET /api/equipment-types/{id}`

Readable by every authenticated role.

### `GET /api/equipment-types/search?q=ration&page=0&size=20`

Paginated search over code, name, category and description.

### `POST /api/equipment-types` · `PUT /api/equipment-types/{id}` · `DELETE /api/equipment-types/{id}`

`ADMIN` only.

```json
{ "code": "RAT-24H", "name": "24H Ration Pack", "category": "Rations", "unit": "pcs", "description": "Combat ration" }
```

`DELETE` returns `204` for an unused type and `409` if it is referenced.

---

## Personnel

Roles: `ADMIN` and `BASE_COMMANDER` (own base). `LOGISTICS_OFFICER` receives `403`.

### `GET /api/personnel?baseId=2`

Flat list, already scoped for base commanders.

### `GET /api/personnel/search?baseId=2&q=reyes&page=0&size=20`

Paginated search over service number, name and rank.

### `GET /api/personnel/{id}`

### `POST /api/personnel` · `PUT /api/personnel/{id}` · `DELETE /api/personnel/{id}`

`ADMIN` only.

```json
{ "serviceNumber": "MIL-1004", "fullName": "Capt. Dana Reyes", "rankTitle": "Captain", "baseId": 2, "contact": "+1-555-0104" }
```

`DELETE` returns `204` for personnel without assignment history and `409` otherwise.

---

## Users

`ADMIN` only. Every call returns `403` for other roles.

### `GET /api/users?role=BASE_COMMANDER&baseId=2&page=0&size=20`

```json
{
  "content": [
    {
      "id": 2, "username": "gen.alpha", "fullName": "Col. Amelia Hart",
      "email": "gen.alpha@mams.local", "role": "BASE_COMMANDER",
      "baseId": 1, "baseCode": "ALPHA", "baseName": "Alpha Army Base",
      "enabled": true, "createdAt": "2025-11-02T09:15:00"
    }
  ]
}
```

### `GET /api/users/roles`

Returns `["ADMIN", "BASE_COMMANDER", "LOGISTICS_OFFICER"]`.

### `GET /api/users/{id}`

### `POST /api/users` → `201`

```json
{
  "username": "logistics3",
  "password": "logistics123",
  "fullName": "Sgt. Victor Cole",
  "email": "logistics3@mams.local",
  "role": "LOGISTICS_OFFICER",
  "baseId": null,
  "enabled": true
}
```

Duplicate username → `409`.

### `PUT /api/users/{id}`

```json
{ "fullName": "Sgt. Victor Cole", "email": "v.cole@mams.local", "role": "BASE_COMMANDER", "baseId": 1, "enabled": true }
```

The username is immutable. You may not disable or delete your own account.

### `PATCH /api/users/{id}/enabled?enabled=false`

Enables or disables an account. A disabled account cannot sign in even with the
correct password.

### `PATCH /api/users/{id}/password` → `204`

```json
{ "newPassword": "changed123" }
```

### `DELETE /api/users/{id}` → `204`

Removes the account. Historical records keep the username snapshot.

---

## Audit logs

Append-only. There is intentionally no `POST`, `PUT`, `PATCH` or `DELETE` endpoint;
write verbs on `/api/audit-logs` return `405`.

Logged actions: `LOGIN`, `LOGIN_FAILED`, `LOGOUT`, `CREATE`, `UPDATE`, `DELETE`,
`STATUS_CHANGE`, `PASSWORD_RESET`, `SEED`.

### `GET /api/audit-logs`

Query: `entity`, `action`, `userId`, `baseId`, `from`, `to` (ISO date-time,
e.g. `2026-01-01T00:00:00`), `page`, `size`.

```json
{
  "content": [
    {
      "id": 41, "userId": 1, "username": "admin",
      "action": "CREATE", "entity": "PURCHASE", "entityId": 4,
      "baseId": 2, "baseCode": "BRAVO",
      "description": "Recorded purchase PUR-2026-0004 at Bravo Army Base (1 line, 25 units)",
      "createdAt": "2026-01-15T10:22:31"
    }
  ]
}
```

`BASE_COMMANDER` requests are always restricted to their own base.

### `GET /api/audit-logs/entities`

Returns the entity names that can be filtered on, e.g.
`["ASSIGNMENT", "BASE", "EQUIPMENT_TYPE", ...]`.

Audit writes use a separate transaction (`REQUIRES_NEW`), so a failed business
operation still leaves a record of the attempt while the business rollback stands.
