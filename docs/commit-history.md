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
| `f3a427d` | feat | Improved Chinese/English switching with persisted locale, reusable segmented switcher, and localized demo-flow pages. | `npm run build` passed; Chrome headless verified login/home language switching and persisted locale. | Pending push |
