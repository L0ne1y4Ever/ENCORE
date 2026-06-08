# ENCORE Codebase Understanding

Last reviewed: 2026-06-05

This note records the current evidence-based project map for continued codebase familiarization. It reflects the current worktree, including uncommitted files and modifications.

## Repository Shape

- Root: full-stack course-design ticketing system.
- Backend: `encore-backend`, Spring Boot 3.3.5, Java 17, Maven, MyBatis-Plus, Sa-Token, Redis, MySQL, Flyway, STOMP WebSocket, Knife4j.
- Frontend: `encore-frontend`, Vue 3, Vite, TypeScript, Pinia, Vue Router, Vue I18n, Element Plus, ECharts, Three.js.
- Infrastructure: `docker-compose.yml` runs MySQL and Redis for local dev; `docker-compose.full.yml` adds backend and nginx-served frontend.
- Documentation: root `README.md`, `PROJECT_MEMORY.md`, `DEVELOPMENT_PLAN.md`, `docs/dev-logs/*`, demo and test reports.

## Backend Map

Entry and config:

- `EncoreBackendApplication` enables scheduling and scans MyBatis mappers.
- `application.yml` selects `dev`; `application-dev.yml` configures MySQL, Redis, Flyway, MyBatis-Plus, Sa-Token, and CORS origin patterns.
- `SecurityConfig` only provides `BCryptPasswordEncoder`; Sa-Token owns authentication.
- `PasswordHashMigrationRunner` migrates historical plaintext demo passwords to BCrypt at startup.
- `SaTokenConfigure` protects `/api/orders/**`, `/api/group-orders/**`, `/api/auth/me`, POST seat lock, `/api/admin/**`, and `/api/checkin/**`.
- `StpInterfaceImpl` loads a user's single role from `user_account.role`.
- `GlobalExceptionHandler` maps `BusinessException` code to real HTTP status and handles Sa-Token 401/403 cases.

Controller surface:

- Public/user: `AuthController`, `ShowController`, `SeatController`, `OrderController`, `GroupOrderController`.
- Staff/admin: `CheckInController`, `AdminController`.
- Utility: `HealthController`.

Core services:

- `AuthService`: login/register/logout/current user; BCrypt password matching; new users default to `user`.
- `ShowService`: public show search/detail/schedules; Top 8 recommendations ranked by sale availability, sold tickets, revenue, sort order, creation time; available-ticket calculation considers Redis seat locks.
- `SeatService`: reads seat maps and area inventory; enforces sale windows; manages Redis seat locks under `encore:seat-lock:{scheduleId}:{seatCode}` plus an index set; publishes seat/area events.
- `OrderService`: creates fixed-seat and zoned-area orders, simulates payment, cancels, refunds, expires pending orders every 5 seconds; handles Redis lock cleanup on transaction rollback.
- `GroupOrderService`: Redis-backed 15-minute group sessions under `encore:group-order:{inviteCode}`; max 6 seats; locks are owned by `group:{inviteCode}` until checkout transfers them to an order.
- `CheckInService`: verifies ticket codes; requires checker/admin/sysadmin; check-in opens 2 hours before start and closes after schedule end; optional schedule binding prevents wrong-session scans.
- `AdminService`: dashboard metrics, show CRUD/status/archive, schedule CRUD/status/cancel, order refund/force check-in.
- `VenueManagementService`: venue/hall/layout CRUD, layout seat status, layout-to-schedule snapshotting, schedule inventory seat/area adjustment, layout sync to future schedules.
- `StaffAccountService`: sysadmin-only staff account list/create/update/reset; only `admin` and `checker` are editable through this service.

Data model:

- Main tables: `user_account`, `encore_show`, `show_schedule`, `schedule_seat`, `ticket_order`, `ticket_item`.
- Venue/layout tables: `venue`, `venue_hall`, `seat_layout`, `seat_layout_area`, `seat_layout_seat`, `venue_area`, `schedule_area_inventory`.
- `schedule_seat` is the truth source for fixed seats and seated areas in MIXED mode.
- `schedule_area_inventory` is the truth source for non-seated zoned inventory; seated inventory rows remain as area registry/price/map tiles.
- Flyway V1 is the complete baseline; V2 upgrades older schemas and inserts default future showtimes plus venue/layout data.

Realtime:

- Seat and area events share `/topic/schedules/{scheduleId}/seats`.
- `SeatStatusEvent` contains `seats` and `areas`; reasons include `LOCKED`, `SOLD`, `EXPIRED`, `REFUNDED`, `CANCELLED`, `AREA_LOCKED`, `AREA_SOLD`, `AREA_RELEASED`, `AREA_REFUNDED`, `AREA_ADJUSTED`, `LAYOUT_SYNC`, `INVENTORY_ADJUSTED`.
- Dashboard refresh events use `/topic/admin/dashboard` and trigger client-side reloads, not pushed aggregate payloads.

## Frontend Map

Entry and shell:

- `main.ts` mounts Vue, Pinia, Router, I18n, Element Plus, and global SCSS.
- `App.vue` injects fonts and forces Element Plus dark mode.
- `router/index.ts` defines `/login`, user routes, `/admin`, and `/checkin`; route guards use the Pinia auth store, but backend authorization remains authoritative.
- `stores/auth.ts` stores current user in `sessionStorage`; API token name/value are stored separately by `api/index.ts`.

API layer:

- `api/index.ts` configures Axios with `VITE_API_BASE_URL || http://localhost:8080`, attaches the Sa-Token header, unwraps `ApiResponse<T>`, and surfaces backend `msg`.
- Public/user modules: `auth.ts`, `show.ts`, `seat.ts`, `order.ts`, `groupOrder.ts`, `checkin.ts`.
- Admin modules: `admin.ts`, `adminRealtime.ts`.
- WebSocket URL resolution derives `/ws` from Axios baseURL.

User flow:

- `Login.vue`: login/register form; redirects by role.
- `Home.vue`: loads public shows, falls back to mock shows, loads Top Picks, category filters.
- `ShowDetail.vue`: loads show detail and schedules; ON_SALE schedules enter seat selection; PREPARING/COMING_SOON show reservation modal only.
- `SeatSelection.vue`: central booking page. Supports SEATED, ZONED, MIXED, fixed-seat selection, area ticket quantity, realtime seat/area updates, 3D preview, group-seat invitation polling, and checkout.
- `OrderConfirm.vue`: confirms temporary fixed-seat order data from `sessionStorage` and creates the real backend order.
- `Payment.vue`: loads order and calls simulated payment.
- `ETicket.vue`: renders backend ticket data and uses structured row/col when present for localized seat labels.
- `Profile.vue`: loads my orders, derives tickets/reservations, supports cancel pending orders and refund eligible paid orders.

Admin/check-in flow:

- `AdminLayout.vue`: role-aware side navigation; sysadmin sees staff-user page.
- `Dashboard.vue`: loads metrics and subscribes to dashboard refresh events.
- `Shows.vue`: admin show CRUD/status/archive.
- `Venues.vue`: venue and hall CRUD.
- `Layouts.vue`: layout CRUD/status, seat enable/disable, sync layout seat status to future schedules using the same layout.
- `Schedules.vue`: list/calendar schedule management; loads shows/venues/halls/layouts; creates schedules from selected layout.
- `ScheduleInventory.vue`: seat/area inventory management; subscribes to schedule realtime events and reloads.
- `Orders.vue`: admin order list, refund, force check-in.
- `StaffUsers.vue`: sysadmin-only staff account operations.
- `Scanner.vue`: check-in schedule selection stored in `localStorage`, ticket-code verify call, success/error scan state.

UI foundation:

- Global theme tokens live in `styles/variables.scss`.
- `styles/main.scss` sets dark theme, font selection by locale, Element Plus variable overrides, route transitions, and shared animation helpers.
- I18n setup in `i18n.ts` persists locale and writes `html lang` / `data-locale`.

## Key Invariants

- Protected backend endpoints must pass Sa-Token HTTP-layer checks; service-layer role guards remain defense-in-depth.
- Order totals and buyer identity are computed on the backend, not trusted from the client.
- Fixed-seat availability is `schedule_seat.status == AVAILABLE` plus no active Redis lock.
- Area inventory updates for non-seated areas use atomic SQL counters; admin adjustment must not overwrite locked/sold counters.
- Seated-area counts in MIXED mode derive from `schedule_seat`, not `schedule_area_inventory`.
- Redis locks are non-transactional; order creation registers rollback callbacks to release or revert locks.
- Check-in validates order status, ticket status, optional current schedule, schedule status, and time window.
- Layout status sync only applies to future non-cancelled/non-ended schedules using that layout and refuses sold/locked/reserved seats.

## Current Worktree Notes

- The worktree contains existing uncommitted backend, frontend, UML, and SRS-related changes.
- Notable current untracked backend files include `SecurityConfig`, `SaTokenConfigure`, `PasswordHashMigrationRunner`, and related tests.
- Treat the current dirty worktree as authoritative unless the user explicitly asks for rollback.

## Verification Snapshot

- `cd encore-backend && mvn test`: passed on 2026-06-05; 74 tests, 0 failures, 0 errors, 0 skipped.
- `cd encore-frontend && npm run build`: passed on 2026-06-05.
- Frontend build warnings observed: Sass `@import` deprecation in `src/styles/main.scss`; Rollup removed two non-interpretable `/* #__PURE__ */` comments from `@vueuse/core`.

## Remaining Reading Targets

- Full line-by-line pass on all frontend templates/styles, especially `SeatSelection.vue`, `SeatStagePreview.vue`, `ConcertVenuePreview.vue`, and admin pages.
- Full DTO field-by-field pass across `encore-backend/src/main/java/com/encore/dto`.
- Full test intent review across all backend test classes.
- Optional runtime smoke test with MySQL/Redis if services are available.
