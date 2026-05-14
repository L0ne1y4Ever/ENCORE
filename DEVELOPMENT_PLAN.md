# ENCORE Development Plan

Updated: 2026-05-14

## Goal

Build the course acceptance version of ENCORE from the current Vue frontend prototype into a runnable full-stack ticketing system. The project should support defense-friendly demonstrations, daily logs, and GitHub history that clearly show realistic development progress.

## Phase 0 - Repository Memory And Workflow

Status: in progress on 2026-05-14

- Add repository-level `.gitignore`.
- Add `PROJECT_MEMORY.md`.
- Add `DEVELOPMENT_PLAN.md`.
- Add daily log directory and first log.
- Add commit history index.
- Add demo checklist.
- Verify frontend build.
- Commit and push workflow foundation.

## Phase 1 - Backend Foundation

Target outcome: backend can start and expose health/auth/show APIs.

- Create `encore-backend` Spring Boot 3 project.
- Configure Java 17, Maven, MyBatis-Plus, Sa-Token, Redis, MySQL, Knife4j.
- Add unified response object `{ code, msg, data }`.
- Add global exception handling.
- Add development profile and environment examples.
- Add database schema and seed data matching current frontend mock data.
- Add Docker Compose services for MySQL and Redis.

Commit trigger:

- Backend starts successfully.
- Database initialization succeeds.
- Health/auth/show list APIs are callable.

## Phase 2 - Auth And Catalog Integration

Target outcome: frontend login and show browsing use real backend APIs.

- Implement register/login/logout/current-user APIs.
- Keep demo accounts: `user`, `admin`, `checker`, `sysadmin`, all password `123`.
- Implement show list, search, detail, and schedule list APIs.
- Add Axios client in frontend.
- Replace mock auth and show APIs with backend calls.
- Preserve role-based route guards.

Commit trigger:

- User can log in through backend and browse shows from database.

## Phase 3 - Seat Locking, Orders, And Payment

Target outcome: full user purchase flow works against backend state.

- Implement schedule seat query.
- Implement Redis seat lock with 15-minute TTL.
- Implement order creation and idempotency guard.
- Implement order countdown and timeout release.
- Implement mock payment.
- Generate ticket items after successful payment.
- Update frontend seat selection, confirmation, payment, and ticket pages.

Commit trigger:

- User can buy seats end to end and receive electronic tickets.

## Phase 4 - Check-In And Admin Operations

Target outcome: checker and admin workflows are demonstrable.

- Implement check-in verification by ticket code.
- Reject duplicate, invalid, refunded, or wrong-time tickets with clear messages.
- Implement minimum admin CRUD for shows, categories, halls, seats, schedules, orders, and announcements.
- Implement schedule publishing and ticket-pool generation.

Commit trigger:

- A paid ticket can be checked in once, and admin can prepare an ON_SALE schedule.

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
