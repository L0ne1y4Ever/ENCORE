# ENCORE Development Plan

Updated: 2026-05-16

## Goal

Build the course acceptance version of ENCORE from the current Vue frontend prototype into a runnable full-stack ticketing system. The project should support defense-friendly demonstrations, daily logs, and GitHub history that clearly show realistic development progress.

## Phase 0 - Repository Memory And Workflow

Status: completed on 2026-05-14

- Add repository-level `.gitignore`.
- Add `PROJECT_MEMORY.md`.
- Add `DEVELOPMENT_PLAN.md`.
- Add daily log directory and first log.
- Add commit history index.
- Add demo checklist.
- Verify frontend build.
- Commit and push workflow foundation.

## Phase 1 - Backend Foundation

Status: completed on 2026-05-14

Target outcome: backend can start and expose health/auth/show APIs.

- Create `encore-backend` Spring Boot 3 project.
- Configure Java 17, Maven, MyBatis-Plus, Sa-Token, Redis, MySQL, Knife4j.
- Add unified response object `{ code, msg, data }`.
- Add global exception handling.
- Add development profile and environment examples.
- Add database schema and seed data matching current frontend mock data.
- Add Docker Compose services for MySQL and Redis.

Progress:

- Created `encore-backend` Spring Boot 3 Maven project.
- Added unified response, global exception handling, CORS, Knife4j config, and dev profile.
- Added health, auth, show, show detail, and schedule APIs.
- Added MyBatis-Plus entities/mappers/services for users, shows, and schedules.
- Added MySQL schema/seed scripts and Docker Compose services for MySQL and Redis.
- Verified Docker Compose MySQL/Redis startup, database seed data, `mvn test`, `GET /api/health`, login, current user, show list, show detail, and schedule list APIs.

Commit trigger:

- Backend starts successfully.
- Database initialization succeeds.
- Health/auth/show list APIs are callable.

## Phase 2 - Auth And Catalog Integration

Status: in progress on 2026-05-14

Target outcome: frontend login and show browsing use real backend APIs.

- Implement register/login/logout/current-user APIs.
- Keep demo accounts: `user`, `admin`, `checker`, `sysadmin`, all password `123`.
- Implement show list, search, detail, and schedule list APIs.
- Add Axios client in frontend.
- Replace mock auth and show APIs with backend calls.
- Preserve role-based route guards.

Progress:

- Added Axios frontend client with `VITE_API_BASE_URL`.
- Replaced frontend login/logout/current-user flow with backend auth APIs.
- Persisted Sa-Token header and current user in `sessionStorage` for route guards.
- Replaced show list, detail, and schedule APIs with backend calls.
- Verified frontend production build and backend CORS/login response for Vite origin.
- Added persistent Chinese/English switching with a reusable segmented language control.
- Localized the main user, admin, check-in, order, payment, and ticket visible text used in the demo flow.

Commit trigger:

- User can log in through backend and browse shows from database.

## Phase 3 - Seat Locking, Orders, And Payment

Status: in progress on 2026-05-14

Target outcome: full user purchase flow works against backend state.

- Implement schedule seat query.
- Implement Redis seat lock with 15-minute TTL.
- Implement order creation and idempotency guard.
- Implement order countdown and timeout release.
- Implement mock payment.
- Generate ticket items after successful payment.
- Update frontend seat selection, confirmation, payment, and ticket pages.

Progress:

- Added `schedule_seat`, `ticket_order`, and `ticket_item` schema.
- Seeded deterministic 10x15 seat pools for each schedule.
- Implemented schedule seat query API with Redis lock overlay.
- Implemented Redis seat lock API with 15-minute TTL and conflict checks.
- Implemented order creation with pending-order idempotency for repeated submits.
- Implemented order detail, timeout expiration, mock payment, seat sale finalization, and ticket status transition.
- Switched frontend seat/order API layer from mock data to backend APIs without changing the optimized page layouts.
- Verified lock -> order -> payment -> ticket flow against Docker MySQL and Redis.

Commit trigger:

- User can buy seats end to end and receive electronic tickets.

## Phase 4 - Check-In And Admin Operations

Status: in progress on 2026-05-16

Target outcome: checker and admin workflows are demonstrable.

- Implement check-in verification by ticket code.
- Reject duplicate, invalid, refunded, or wrong-time tickets with clear messages.
- Implement minimum admin CRUD for shows, categories, halls, seats, schedules, orders, and announcements.
- Implement schedule publishing and ticket-pool generation.

Progress:

- Added backend `POST /api/checkin/verify` endpoint.
- Added role guard for checker/admin/sysadmin check-in authority.
- Added ticket-code lookup and validation against paid order state.
- Added one-time transition from `UNUSED` ticket to `CHECKED_IN`.
- Added frontend `api/checkin.ts` and connected the scanner page to the backend endpoint.
- Added `CheckInServiceTest` for successful check-in, duplicate rejection, and unauthorized role rejection.
- Restored i18n keys removed by the latest UI edits so order/payment/ticket/admin pages keep rendering translated text.
- Verified real API flow: login, seat selection, lock, order creation, mock payment, check-in, and duplicate check-in rejection.
- Verified browser check-in UI with a paid ticket code and duplicate rejection message.
- Added backend admin schedule list and schedule status update APIs.
- Added backend admin order list, refund, and force-check-in APIs.
- Connected frontend admin schedule/order pages to backend APIs with loading, refresh, success, and error feedback.
- Verified real admin API flow: schedule status update/restore, paid order force check-in, and paid order refund.
- Added backend admin show CRUD APIs for all-status listing, draft creation, editing, status changes, and soft delete to `ARCHIVED`.
- Connected frontend admin show management to `/api/admin/shows` with a dialog form, status buttons, delete confirmation, loading/disabled states, and localized backend error display.
- Verified public `/api/shows` still only returns `PUBLISHED` shows after draft/archive transitions.
- Verified ordinary `user/123` cannot access `/api/admin/shows`.

Commit trigger:

- A paid ticket can be checked in once, admin can prepare an ON_SALE schedule, and admin can publish/unpublish shows from backend state.

## Phase 5 - Dashboard And Differentiators

Target outcome: defense demo has visible highlights beyond CRUD.

- Implement dashboard metrics API.
- Add black-gold command-center dashboard visuals.
- Add WebSocket seat updates.
- Add WebSocket dashboard refresh event.
- Add three.js seat-stage preview with fallback.
- Add Top 8 recommendation API and homepage section.
- Add basic group-seat invitation flow.

Commit trigger:

- At least one differentiating feature is runnable and documented per commit.

## Phase 6 - Delivery Package

Target outcome: final course submission can be started, tested, and explained.

- Add Docker Compose for frontend, backend, MySQL, Redis, and Nginx if needed.
- Add README startup guide.
- Add user manual.
- Add test cases and test report.
- Add JMeter baseline scripts or documented manual performance evidence.
- Export or reference Knife4j API docs.
- Update final demo checklist and defense summary.

Commit trigger:

- Project can be started from a clean checkout following documentation.

## Daily Development Rule

Every day with development work must update `docs/dev-logs/YYYY-MM-DD.md`. Each log should include:

- Today's goal.
- Completed work.
- Key implementation notes.
- Problems and solutions.
- Verification results.
- GitHub commit/push record.
- Tomorrow's plan.
