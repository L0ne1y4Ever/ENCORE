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
- User opens show detail.
- User selects an ON_SALE schedule.
- User selects seats from the visual seat map.
- Seat states show AVAILABLE / LOCKED / SOLD / DISABLED.
- User creates an order and sees countdown.
- User completes mock payment.
- User opens electronic ticket and sees ticket code.
- Seat selection, order creation, payment, and ticket generation use backend APIs.
- The generated ticket code can be submitted to the backend check-in endpoint.

## Check-In Flow

- Checker logs in with `checker / 123`.
- Checker enters or scans ticket code.
- Valid ticket is checked in successfully.
- Repeated check-in is rejected.
- Invalid ticket is rejected.
- Check-in page uses backend `POST /api/checkin/verify`.
- Wrong schedule/time ticket is rejected when backend time validation is implemented.

## Admin Flow

- Admin logs in with `admin / 123`.
- Admin views dashboard metrics.
- Admin manages shows.
- Admin manages schedules through backend data.
- Admin changes schedule status through backend API.
- Admin views backend orders.
- Admin can refund eligible paid orders through backend API.
- Admin can force-check-in eligible paid orders through backend API.
- Admin can publish an ON_SALE schedule after full show/schedule CRUD is added.

## Differentiating Features

- Real-time seat update across two browser sessions.
- three.js seat-stage preview on seat click.
- Black-gold command-center dashboard.
- Top 8 recommendation block.
- Basic group-seat invitation flow.

## Evidence To Capture

- Screenshots of each major page.
- Screenshots of successful and failed check-in.
- Screenshot or recording of real-time seat update.
- Screenshot of dashboard charts.
- Terminal output for frontend build and backend tests.
- GitHub commit history showing staged progress.
