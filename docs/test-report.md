# ENCORE Test Report

Last updated: 2026-05-17

## Environment

- OS: Windows development machine
- Backend: Spring Boot 3, Java 17 target, Maven
- Frontend: Vue 3 + Vite + TypeScript
- Database: MySQL 8.4 through Docker Compose, host port `3307`
- Cache/realtime support: Redis 7.4 through Docker Compose, host port `6379`
- Backend URL: `http://localhost:8080`
- Frontend URL: `http://localhost:5173`

## Automated Checks

| Check | Command | Result |
| --- | --- | --- |
| Backend focused group-order tests | `mvn -Dtest=GroupOrderServiceTest test` | Passed, 6 tests |
| Backend full test suite | `mvn test` in `encore-backend` | Passed, 34 tests |
| Frontend production build | `npm run build` in `encore-frontend` | Passed |
| Docker Compose config validation | `docker compose -f docker-compose.full.yml config` | Passed |

Known non-blocking warnings:

- Frontend build reports the existing Sass `@import` deprecation warning.
- Vite reports large chunks for Three.js/ECharts routes; current acceptance build still succeeds.

## Manual / API Verification

| Scenario | Evidence |
| --- | --- |
| Health endpoint | `GET /api/health` returned `code:0` and `status:UP`. |
| User purchase flow | Browser screenshot evidence is stored in `docs/demo-evidence/2026-05-17-purchase-flow.md`. |
| Check-in time window | Future schedule ticket rejected by scanner; admin force check-in remained available. |
| Check-in current schedule binding | Wrong-schedule ticket rejected with `票据不属于当前检票场次`; correct schedule check-in succeeded. |
| Admin show CRUD | Create, edit, publish, unpublish, archive, public-list filtering, and ordinary-user rejection verified through real APIs. |
| Admin schedule CRUD | Create/edit/cancel schedule, generated seat pool, lock allowed while `ON_SALE`, and lock rejected after cancel verified. |
| Admin dashboard metrics | `admin/123` dashboard loaded real metrics; `user/123` access was rejected. |
| WebSocket seat sync | Two browser sessions on the same schedule saw lock and sold counts update without refresh. |
| Dashboard WebSocket refresh | Dashboard API calls increased after paid/refunded events and live status was visible. |
| Three.js seat preview | Desktop/mobile canvas nonblank checks, drag movement, 3D click selection, and realtime redraw verified. |
| Top 8 recommendations | Home page called `/api/shows/recommendations/top8`, rendered ranked cards, and opened detail from a recommendation card. |
| Group-seat invitation | `user/123` created invite `g-5b7f36d8e9`, `friend/123` joined, host checkout created `ord-8724105db9d64186`, mock payment succeeded, and the e-ticket page opened. |

## Acceptance Coverage

- Auth and role routing: covered by login and role-specific browser/API checks.
- Show browsing/detail/schedules: covered by purchase-flow evidence and Top 8 verification.
- Seat map, lock, payment, and tickets: covered by user purchase and group-seat verification.
- Check-in: covered by valid, duplicate, wrong-time, and wrong-schedule scenarios.
- Admin management: covered by show, schedule, order, refund, force check-in, and dashboard checks.
- Differentiators: WebSocket seat sync, dashboard refresh, Three.js preview, Top 8 recommendations, and group-seat invitations are all runnable.

## Recommended Final Defense Evidence

- Reuse `docs/demo-evidence/2026-05-17-purchase-flow.md` for the core purchase screenshots.
- Capture one short recording of two-browser realtime seat sync.
- Capture one short recording of group-seat invitation with `user/123` and `friend/123`.
- Capture one admin dashboard screenshot after a paid order and refund event.
