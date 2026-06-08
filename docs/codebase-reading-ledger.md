# ENCORE Codebase Reading Ledger

Last updated: 2026-06-05

Purpose: track progress toward full codebase familiarity. This is a working ledger, not a completion claim.

## Coverage Legend

- `mapped`: role and boundaries are understood from current files.
- `first-pass`: key methods, data flow, and invariants have been read.
- `deep-pass`: field-by-field or branch-by-branch review has been done.
- `pending`: not yet read deeply enough for reliable recall.

## Backend Source Coverage

Current backend source count: 116 Java files under `encore-backend/src/main/java/com/encore`.

| Package | Files | Status | Notes |
| --- | ---: | --- | --- |
| root app | 1 | first-pass | `EncoreBackendApplication`: scheduling, mapper scan, Spring Boot entry. |
| common | 2 | deep-pass | `ApiResponse`, `ErrorCode`: response envelope and semantic HTTP codes. |
| exception | 2 | deep-pass | `BusinessException`, `GlobalExceptionHandler`: business status mapping, Sa-Token 401/403 handling. |
| config | 8 | first-pass | CORS, WebSocket/STOMP, Knife4j, clock, Sa-Token route gate, BCrypt bean, password migration. |
| satoken | 1 | first-pass | `StpInterfaceImpl`: role source from `user_account.role`. |
| controller | 8 | first-pass | REST surface mapped for auth, public shows, seats, orders, group orders, check-in, admin, health. |
| entity | 13 | first-pass | Table mapping and primary fields identified; needs entity-by-entity invariant pass. |
| mapper | 13 | first-pass | Mostly MyBatis-Plus `BaseMapper`; `ScheduleAreaInventoryMapper` deep-read for atomic inventory SQL. |
| dto | 56 | pending | Record list identified; next target is field contract mapping by workflow. |
| service | 12 | first-pass | Core flows read for auth, shows, seats, orders, groups, check-in, admin, venue, staff, publishers. Needs branch-level pass for largest services. |

## Backend Test Coverage

Current backend test count: 13 test files under `encore-backend/src/test/java/com/encore`.

| Package | Files | Status | Notes |
| --- | ---: | --- | --- |
| config | 1 | pending | `PasswordHashMigrationRunnerTest` pending detailed assertion map. |
| satoken | 1 | pending | `StpInterfaceImplTest` pending detailed assertion map. |
| service | 10 | pending | Service tests identified; next target is test-intent map by business invariant. |
| web | 1 | pending | `GlobalExceptionHandlerTest` pending detailed assertion map. |

Verification already performed against current worktree:

- Backend: `mvn test` passed, 74 tests.
- Frontend: `npm run build` passed.

## Frontend Source Coverage

Current frontend source count: 51 TS/Vue/SCSS/CSS files under `encore-frontend/src`.

| Area | Files | Status | Notes |
| --- | ---: | --- | --- |
| src root | 4 | first-pass | `main.ts`, `App.vue`, `i18n.ts`, legacy/global `style.css`; `style.css` still pending detailed pass. |
| api | 11 | first-pass | Axios wrapper, auth/show/seat/order/group/checkin/admin APIs, realtime STOMP clients mapped. |
| router | 1 | first-pass | Route tree and client-side role guard mapped. |
| stores | 1 | first-pass | Auth store session persistence and nickname update mapped. |
| locales | 2 | mapped | i18n files searched for key coverage; full key-by-key diff pending. |
| styles | 2 | first-pass | Theme tokens and global SCSS mapped; Sass `@import` deprecation noted. |
| layouts | 3 | first-pass | User/admin/check-in shells mapped. |
| auth view | 1 | first-pass | Login/register role redirect flow mapped. |
| user views | 7 | first-pass | Home, detail, seat selection, confirm, payment, ticket, profile flow mapped. `SeatSelection.vue` needs deep branch pass. |
| check-in view | 1 | first-pass | Scanner schedule binding and verify flow mapped. |
| admin views | 8 | first-pass | Dashboard, shows, venues, layouts, schedules, inventory, orders, staff users mapped. `Schedules.vue` and `Layouts.vue` need deep branch pass. |
| components | 6 | mapped | Area panel and 3D preview partially read; all components need template/style deep pass. |
| mock | 4 | mapped | Type contracts and fallback data identified; full fixture pass pending. |

## Resource and Deployment Coverage

| Area | Status | Notes |
| --- | --- | --- |
| Maven `pom.xml` | first-pass | Spring Boot 3.3.5, Java 17, MyBatis-Plus, Sa-Token, Redis, WebSocket, Flyway, Knife4j, BCrypt crypto. |
| Frontend `package.json` | first-pass | Vue 3/Vite/TypeScript/Pinia/Router/I18n/Element Plus/ECharts/Three/STOMP. |
| Spring config YAML | first-pass | Dev profile, datasource, Redis, Flyway, Sa-Token, CORS. |
| Flyway migrations | first-pass | V1 baseline and V2 compatibility/default schedule upgrade mapped. |
| Docker Compose | first-pass | Local MySQL/Redis and full backend/frontend stack mapped. |
| nginx config | first-pass | SPA fallback plus `/api` and `/ws` proxy to backend. |
| docs/dev logs | mapped | Main implementation history read through 2026-06-02; older logs pending detailed pass. |
| UML sources/images | pending | File presence known; diagram semantics not yet reviewed. |
| SRS/report scripts | pending | Presence known; script logic and generated artifacts not yet reviewed. |

## Next Reading Queue

1. Deep-read all backend DTOs by workflow: auth, public show/schedule, seat/area realtime, order/ticket, group order, check-in, admin show/schedule/order/dashboard, venue/layout/staff.
2. Deep-read backend tests and map each test to the invariant it protects.
3. Deep-read frontend complex components: `SeatSelection.vue`, `SeatStagePreview.vue`, `ConcertVenuePreview.vue`, `AdminSeatMapEditor.vue`.
4. Deep-read admin form pages: `Schedules.vue`, `Layouts.vue`, `Shows.vue`, `Venues.vue`, `ScheduleInventory.vue`.
5. Review scripts, docs generation, UML files, and SRS assets.
