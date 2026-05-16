# ENCORE Commit History Index

This file is a defense-friendly index of meaningful commits. It complements `git log` with plain-language summaries.

## 2026-05-14

| Commit | Type | Summary | Verification | Push |
| --- | --- | --- | --- | --- |
| `90aed83` | feat | Initialized frontend project with layouts, mock data, API structure, and documentation support. | Existing frontend build was verified before this planning pass. | Pushed to `origin/main` |
| `abf2489` | docs | Added repository memory, development plan, daily log system, commit index, demo checklist, and `.gitignore`. | `npm run build` in `encore-frontend` passed. | Pushed to `origin/main` |
| `230942e` | docs | Recorded the workflow foundation commit hash in the daily log and commit index. | Git metadata only. | Pushed to `origin/main` |
| `58ce69a` | feat | Scaffolded Spring Boot backend foundation, MySQL/Redis Compose services, seed SQL, and cloud-friendly env defaults. | `mvn test` passed; Docker MySQL/Redis healthy; seed counts verified; health/auth/show APIs passed. | Pushed to `origin/main` |
| `70cd190` | feat | Integrated frontend auth and show browsing with backend APIs using Axios and `VITE_API_BASE_URL`. | `npm run build` passed; CORS preflight for Vite origin passed; backend login from Vite origin passed. | Pushed to `origin/main` |
| `dce8087` | feat | Improved Chinese/English switching with persisted locale, reusable segmented switcher, and localized demo-flow pages. | `npm run build` passed; Chrome headless verified login/home language switching and persisted locale. | Pushed to `origin/main` |
| `e3800c3` | feat | Added backend seat locking, order creation, mock payment, ticket generation, and frontend seat/order API integration. | `mvn test` passed; `npm run build` passed; backend lock/order/pay API flow passed; repeated order creation reused the pending order. | Pushed to `origin/main` |
| `87e7fd0` | feat | Added backend ticket check-in verification, connected scanner UI to real API, and stabilized the latest frontend UI/i18n changes. | `mvn test` passed; `npm run build` passed; real API purchase/pay/check-in flow passed; browser scanner success and duplicate rejection passed. | Pushed to `origin/main` |
| `f2d4a9d` | feat | Wired admin schedule/order pages to backend APIs and added admin schedule status, refund, and force-check-in operations. | `mvn test` passed; `npm run build` passed; real admin API schedule update/restore, force check-in, and refund flow passed. | Pushed to `origin/main` |
| `267f6cc` | feat | Added backend admin show CRUD and connected the admin show page to real create/edit/publish/archive operations. | `mvn test` passed; `npm run build` passed; real admin show CRUD API flow passed; browser admin show page and create dialog loaded. | Pushed to `origin/main` |
| `90415e0` | feat | Added admin schedule create/edit/cancel APIs, generated seat pools for new schedules, and connected the admin schedule page to real CRUD operations. | `mvn test` passed; `npm run build` passed; real schedule CRUD and generated-seat lock API flow passed; browser admin schedule page and create dialog loaded. | Pushed to `origin/main` |
| `aa52c14` | feat | Added backend admin dashboard metrics and connected the admin dashboard page to real revenue, ticket, trend, top-show, and check-in data. | `mvn test` passed; `npm run build` passed; real admin dashboard API and ordinary-user rejection passed; browser admin dashboard rendered live metrics. | Pushed to `origin/main` |

## 2026-05-17

| Commit | Type | Summary | Verification | Push |
| --- | --- | --- | --- | --- |
| `6b0802d` | feat | Added ordinary check-in time-window and schedule-validity rules while keeping admin force-check-in available for correction. | `mvn test` passed; `npm run build` passed; real API rejected future-schedule scanner check-in and admin force-check-in still succeeded. | Pushed to `origin/main` |
| `b5e5a27` | docs | Added browser screenshot evidence for the full user purchase flow from login through electronic ticket. | `mvn test` passed; `npm run build` passed; browser flow created paid order `ord-1e911c2c5bba466d` and ticket `TMP8LC0P51-9XYZ`. | Pushed to `origin/main` |
| `6b5c929` | feat | Added WebSocket/STOMP live-seat updates across lock, sale, refund, expiry, and cancelled-schedule transitions. | `mvn test` passed; `npm run build` passed; real STOMP/API flow emitted `LOCKED`, `SOLD`, and `REFUNDED -> AVAILABLE` events on `sch-101`; two browser sessions also synced lock/sold counts without refresh. | Pushed to `origin/main` |
| `13f73f4` | feat | Added Dashboard WebSocket refresh events and connected the admin dashboard to automatic metric reloads. | `mvn test` passed; `npm run build` passed; real STOMP/API emitted `ORDER_PAID` and `ORDER_REFUNDED`; browser dashboard API calls increased from 1 to 3 after live events. | Pushed to `origin/main` |
