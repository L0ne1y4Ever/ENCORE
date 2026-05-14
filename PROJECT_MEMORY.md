# ENCORE Project Memory

Last updated: 2026-05-14

## Project Identity

ENCORE is a course-design ticketing management system for small and medium theaters, campus venues, and performance spaces. The SRS defines a full business loop:

`show publishing -> user ticket purchase -> on-site check-in -> operation analytics`

The implementation target is the course acceptance version. Priority is to make required acceptance items and differentiating demo features runnable and easy to explain during the final defense.

Final deployment target: a cloud server. Prefer Docker Compose and environment-variable driven configuration so the same repository can run locally and on the server without committing secrets. Local development may expose MySQL on `3307` to avoid conflicts with a host MySQL installation; container-to-container or cloud deployment should use `mysql:3306`.

## Repository Rules

- Repository root: `D:\ENCORE`
- Git branch: `main`
- GitHub remote: `origin` -> `https://github.com/L0ne1y4Ever/ENCORE.git`
- Commit and push after each runnable feature loop, not after every tiny edit.
- Before each new development session, read:
  - `PROJECT_MEMORY.md`
  - `DEVELOPMENT_PLAN.md`
  - latest file under `docs/dev-logs/`
  - `docs/demo-checklist.md`
- Never overwrite unrelated local changes. Always inspect `git status --short --branch` first.

## Current Implementation State

The current implemented application is `encore-frontend`, a Vue 3 + Vite + TypeScript frontend prototype.

Implemented frontend areas:

- Auth page with backend login/logout integration and mock-only register placeholder.
- User layout with show browsing, show detail, reservation, seat selection, order confirmation, mock payment, electronic ticket, profile.
- Admin layout with dashboard and show management table.
- Check-in layout with single-screen scanner simulation.
- Mock data and mock API modules for shows, seats, orders, and users.
- Vue I18n is partially wired for Chinese/English.
- Axios client reads `VITE_API_BASE_URL`, defaults to `http://localhost:8080`, and stores the backend `encore-token` in `sessionStorage`.
- I18n now initializes from `localStorage` or browser language, writes `html lang` / `data-locale`, and uses a reusable `LanguageSwitch` component across login, user, admin, and check-in layouts.

Backend foundation added on 2026-05-14:

- `encore-backend` is a Spring Boot 3 + Maven project.
- Implemented unified API response, global exception handling, CORS, Knife4j, health endpoint, auth endpoints, show list/detail, and schedule list.
- Added MyBatis-Plus entities/mappers/services for `user_account`, `encore_show`, and `show_schedule`.
- Added MySQL initialization SQL under `encore-backend/src/main/resources/db/init`.
- Added root `docker-compose.yml` for MySQL 8.4 and Redis 7.4.
- `mvn test` passes, and `GET /api/health` returns `code:0` when the backend starts.
- Docker Compose MySQL/Redis startup was verified after Docker Desktop became available.
- Seed data was verified from MySQL: 4 demo accounts, 4 shows, and 5 schedules.
- Auth/show APIs were verified against the initialized database and Redis login state.
- Frontend auth and show browsing were switched from mock data to backend APIs on 2026-05-14.
- Frontend build passed after adding Axios integration.
- Chinese/English switching was expanded across the core demo pages and verified with browser automation on 2026-05-14.

Important current limitation:

- Seat locking, order creation, payment, ticket generation, and check-in are currently simulated in frontend memory/sessionStorage.

## Target Technical Stack

Frontend:

- Vue 3
- TypeScript
- Vite
- Vue Router
- Pinia
- Vue I18n
- Element Plus
- ECharts / vue-echarts
- three.js
- SCSS

Backend target:

- Spring Boot 3
- Java 17
- Maven
- MyBatis-Plus
- Sa-Token
- Spring WebSocket + STOMP
- Knife4j
- Redis 7
- MySQL 8
- Docker Compose

## Demo Accounts

Current frontend mock accounts:

- `user / 123` -> registered user
- `admin / 123` -> ticket administrator
- `checker / 123` -> check-in staff
- `sysadmin / 123` -> system administrator

Keep these accounts in seed data when the backend is added.

## Core Business Flow

The main acceptance flow must remain stable:

1. User logs in.
2. User browses shows and opens show detail.
3. User selects an ON_SALE schedule.
4. User selects 1-6 available seats.
5. System locks seats for 15 minutes.
6. User creates an order.
7. User completes mock payment.
8. System marks order as PAID, seats as SOLD, and creates electronic tickets.
9. Checker verifies ticket code and marks ticket as CHECKED_IN.
10. Admin dashboard reflects sales and check-in data.

## Required Acceptance Priorities

Must-have:

- Auth and role routing.
- Show browsing/search/detail/schedules.
- Visual seat map with AVAILABLE / LOCKED / SOLD / DISABLED states.
- Seat locking with concurrency conflict handling.
- Order countdown and automatic release after timeout.
- Mock payment and electronic ticket generation.
- Check-in verification with duplicate/invalid/wrong-time rejection.
- Admin show/category/hall/seat/schedule/order/announcement minimum management.
- Dashboard metrics: orders, revenue, 7-day trend, popular shows, attendance.
- Docker Compose runnable project.
- Daily development logs and test evidence for defense.

Differentiating demo features:

- Real-time seat updates through WebSocket.
- three.js seat-stage preview.
- Black-gold command-center dashboard.
- Top 8 recommendation block.
- Basic group-seat invitation demo.

Out of scope for course acceptance:

- Real payment provider.
- Real SMS provider.
- Real scanner hardware integration.
- External paid LLM dependency as a required path.
- Large-scale ticket-rush architecture.

## Development Cadence

Definition of enough progress for commit + push:

- A runnable user flow is completed.
- A backend module with API and database integration is completed.
- A frontend/backend integration loop is completed.
- A demo differentiator is completed.
- A documentation or deployment milestone is completed.

Before each push:

1. Run the relevant check.
2. Update `PROJECT_MEMORY.md` if project state changed.
3. Update today's log in `docs/dev-logs/`.
4. Update `docs/commit-history.md`.
5. Commit with a clear conventional message.
6. Push to `origin/main`.

## Useful Commands

Frontend:

```powershell
cd D:\ENCORE\encore-frontend
npm run build
npm run dev
```

Git:

```powershell
cd D:\ENCORE
git status --short --branch
git log --oneline --decorate --max-count=5
git push origin main
```

## Next Recommended Work

1. Commit the Phase 2 frontend auth/catalog integration.
2. Resolve GitHub HTTPS credentials, then push local commits to `origin/main`.
3. Start Phase 3 seat/order/payment backend after frontend auth/catalog integration is stable.
