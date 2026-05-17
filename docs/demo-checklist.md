# ENCORE Demo Checklist

Use this checklist to prepare the final course defense.

## Environment

- GitHub repository can be cloned.
- Dependencies can be installed.
- Frontend can build with `npm run build`.
- Backend can start with `mvn spring-boot:run`.
- MySQL and Redis can start through Docker Compose.
- `GET /api/health` returns `code:0`.
- Backend login and show APIs can read initialized MySQL data.
- Frontend login page authenticates through backend auth API.
- Frontend home and show detail pages read backend show APIs.
- Chinese/English switch is available on login, user, admin, and check-in screens.
- Language selection persists after navigation and refresh.
- Demo accounts are available.

## User Flow

- User logs in with `user / 123`.
- User browses show list.
- Home page shows the backend-ranked Top 8 recommendation rail above category tabs.
- Recommendation cards show rank, valid ticket count, and on-sale schedule count.
- User can click a recommendation card and open the existing show detail page.
- User opens show detail.
- User selects an ON_SALE schedule.
- User sees the Three.js stage-seat preview above the 2D seat map.
- User can select an available seat from the 3D preview and see it reflected in the 2D map and order summary.
- User selects seats from the visual seat map.
- Seat states show AVAILABLE / LOCKED / SOLD / DISABLED.
- User can start a group-seat invitation from selected seats.
- Friend logs in with `friend / 123`, opens the invite link, and joins with another seat.
- Host sees the friend in the group member list without leaving the seat page.
- Host checks out the group seats into one pending order and completes mock payment.
- User creates an order and sees countdown.
- User completes mock payment.
- User opens electronic ticket and sees ticket code.
- Seat selection, order creation, payment, and ticket generation use backend APIs.
- Same-schedule seat state changes stream over WebSocket without manual refresh.
- The generated ticket code can be submitted to the backend check-in endpoint.

## Check-In Flow

- Checker logs in with `checker / 123`.
- Checker selects the current check-in schedule before scanning.
- The selected check-in schedule persists after page refresh.
- Checker enters or scans ticket code.
- Valid ticket is checked in successfully.
- Repeated check-in is rejected.
- Invalid ticket is rejected.
- Check-in page uses backend `POST /api/checkin/verify`.
- Check-in page loads available current schedules through `GET /api/checkin/schedules`.
- Tickets from another schedule are rejected with `票据不属于当前检票场次`.
- Tickets before the 2-hour pre-show check-in window are rejected.
- Tickets after schedule end or for cancelled/missing schedules are rejected.
- Admin force-check-in remains available for operational correction outside the ordinary scanner window.

## Admin Flow

- Admin logs in with `admin / 123`.
- Admin views dashboard metrics.
- Dashboard shows real paid revenue, sold tickets, active shows, attendance, 7-day sales trend, top shows, and check-in summary.
- Dashboard auto-refreshes when paid/refunded/check-in/show-change events arrive over WebSocket.
- Admin manages shows through backend CRUD APIs.
- Admin creates a draft show.
- Admin edits show title/category/duration.
- Admin publishes and unpublishes a show.
- Admin archives a show through soft delete.
- Public show list only displays `PUBLISHED` shows.
- Admin manages schedules through backend data.
- Admin creates a schedule for an existing show.
- Admin-generated schedules create a seat pool.
- Admin edits schedule theater, time, status, and price range.
- Admin changes schedule status through backend API.
- Admin cancels a schedule through soft delete.
- Admin views backend orders.
- Admin can refund eligible paid orders through backend API.
- Admin can force-check-in eligible paid orders through backend API.
- Admin can publish an ON_SALE schedule.

## Differentiating Features

- Real-time seat update across two browser sessions.
- WebSocket seat events cover lock, sold, refund, expiry, and cancelled-schedule refresh paths.
- Dashboard WebSocket refresh event updates admin metrics without manual refresh.
- Three.js seat-stage preview is visible on the user seat-selection page and stays synced with realtime seat events.
- Black-gold command-center dashboard.
- Dashboard charts read backend aggregate data instead of static demo data.
- Top 8 recommendation block reads real backend ranking data and degrades to the public show list on API failure.
- Basic group-seat invitation flow uses Redis temporary sessions and host checkout.

## Evidence To Capture

- Screenshots of each major page.
- User purchase-flow screenshots are stored in `docs/demo-evidence/2026-05-17-purchase-flow.md`.
- Screenshots of successful and failed check-in.
- Screenshot or recording of real-time seat update.
- Screenshot of dashboard charts.
- Screenshot of homepage Top 8 recommendations.
- Screenshot or recording of group-seat invitation with `user/123` and `friend/123`.
- Terminal output for frontend build and backend tests.
- GitHub commit history showing staged progress.
