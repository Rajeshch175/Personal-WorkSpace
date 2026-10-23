# Personal Workspace

React/Vite frontend with a Spring Boot 3 / Java 21 API and a persistent H2 database. No sample records are seeded. Create your own records through the UI; empty dashboards show zero totals.

## Run locally

Requires Java 21, Maven, and a Node version supported by Vite (Node 22.12+ recommended).

In one terminal:

```sh
cd backend
mvn spring-boot:run
```

In another:

```sh
cd frontend
npm ci
npm run dev -- --host 127.0.0.1
```

Open http://localhost:5173. Set your name and intention in Settings. The API listens on localhost:8080. This is a single-user local application without authentication.

Records survive API restarts in `backend/data/workspace.mv.db` when started from `backend`. Back up that directory while the API is stopped. Tests use an isolated in-memory database.

## Configuration

Backend environment variables:

| Variable | Default |
| --- | --- |
| `PORT` | `8080` |
| `BIND_ADDRESS` | `127.0.0.1` |
| `DATABASE_URL` | `jdbc:h2:file:./data/workspace` |
| `DATABASE_USER` | `sa` |
| `DATABASE_PASSWORD` | empty |
| `ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:4173` |

Frontend: set `VITE_API_URL` in `frontend/.env` if the API base URL differs from `http://localhost:8080/api`. Restart Vite after changing it.

## API

All routes are prefixed with `/api`. JSON amounts are decimal INR values, dates are `YYYY-MM-DD`, times are `HH:mm`, and record identifiers are UUIDs. Validation errors return HTTP 400 with a `message`; missing records return 404. Creates return 201; deletes return 204.

| Route | Methods | Purpose |
| --- | --- | --- |
| `/dashboard?date=YYYY-MM-DD` | GET | Aggregated dashboard for the selected date (server date if omitted) |
| `/time-blocks` | GET, POST | List/create blocks |
| `/time-blocks/{id}` | PUT, DELETE | Replace/delete a block |
| `/courses` | GET, POST | List/create courses |
| `/courses/{id}` | PUT, DELETE | Replace/delete a course and its progress |
| `/finances` | GET, POST | List/create dated commitments |
| `/finances/{id}` | PUT, DELETE | Replace/delete commitments, including paid status |
| `/loans` | GET, POST | List/create loans |
| `/loans/{id}` | PUT, DELETE | Edit/delete a loan; edits preserve repayment history |
| `/loans/{id}/repayments` | POST | Record a positive repayment; rejects overpayment |
| `/habits` | GET, POST | List/create daily habits |
| `/habits/{id}` | PUT, DELETE | Rename/delete a habit; edits preserve completions |
| `/habits/{id}/completions/{date}` | PUT, DELETE | Set/clear completion, idempotently |
| `/settings` | GET, PUT | Read/update name and intention |
| `/finances/{id}/payments` | POST | Record payment for `dueDate`; advance recurring due date |
| `/backup` | GET | Export all records and settings as versioned JSON |
| `/backup/restore` | POST | Validate and atomically replace the workspace with an exported backup |

Create/replace bodies:

```json
{"title":"Reading","date":"2026-10-23","time":"19:00","minutes":30,"type":"learning","status":"planned"}
```

```json
{"name":"Course title","subtitle":"Description","total":20,"completed":0,"url":"https://example.com/course"}
```

```json
{"name":"Subscription","date":"2026-10-25","amount":100.50,"category":"subscription","status":"scheduled","recurrence":"monthly"}
```

```json
{"name":"Person","note":"Reason","amount":500,"dueDate":"2026-11-01"}
```

Repayment: `{"amount":100}` with a required UUID `Idempotency-Key` header. Reuse the same key when retrying the same repayment; a different amount with the same key returns 409. The UI persists pending keys across reloads until a successful response. Keys are scoped to each loan and retained with its history. Habit: `{"name":"Walk"}`. Settings: `{"name":"Your name","intention":"Your intention"}`.

Time-block types: `work`, `learning`, `course`, `wellbeing`; statuses: `planned`, `active`, `done`. Finance categories: `bill`, `investment`, `subscription`; statuses: `scheduled`, `paid`. Course URLs are optional HTTP/HTTPS links. Completed units cannot exceed total units. Monetary values must be positive with at most two decimal places.

## Calculation rules

- Focus time counts completed non-wellbeing blocks on the selected date.
- Day score is completed blocks plus completed habits, divided by total blocks plus habits; zero when empty.
- Learning streak counts consecutive dates containing completed learning/course blocks, ending on the selected date or its previous day.
- The weekly chart uses completed work/study minutes for Monday–Sunday of the selected week.
- Upcoming money includes unpaid commitments on or after the selected date and before that date plus 30 days. Categories and totals use those same records.
- Loan outstanding amounts subtract all recorded repayments. Loans with outstanding balances become overdue after their due date.
- The date picker filters time blocks and habit completions and anchors dashboard calculations. Courses, finances, and loans remain current records; it is not a historical balance snapshot.

Commitment recurrence supports `none`, `monthly`, and `yearly` (omitted values default to `none` for older clients/records). Mark paid sends `{"dueDate":"YYYY-MM-DD"}` to the payment endpoint and saves the amount and timestamp. Recurring commitments advance one period, preserving the original day of month where possible (January 31 → February 28 → March 31). Repeating the same due-date payment is a no-op. Overdue commitments stay due until paid; the app does not silently skip missed payments. The dashboard includes the next unpaid occurrence of each commitment, not projections of every future occurrence. Use the payment endpoint rather than changing `status` with PUT. Payment history is preserved when editing a commitment.

Bank payments and outbound reminders are not implemented; marking paid records an existing payment and never transfers money.

## Backup and restore

Use **Backup → Download backup** to save version 1 JSON, including settings, all records, habit completions, repayment keys/history, and commitment payment history. To restore, select the JSON file, review record counts, and check the replacement confirmation. Restore replaces all existing data, so export your current workspace first. The server validates the entire backup before deleting anything, and replacement is transactional. Invalid versions, identifiers, balances, histories, or records leave the current workspace untouched. The UI accepts backup files up to 10 MB. Backup files contain your personal records; store them privately.

Editing a loan cannot reduce its amount below repayments already recorded. Habit renaming retains its completion dates.

## Verification

```sh
cd backend
mvn test
```

```sh
cd frontend
npm test
npm run build
```
