# ENCORE User Manual

Last updated: 2026-05-17

This manual describes the runnable course-defense flows for ENCORE.

## Accounts

| Account | Password | Role | Entry |
| --- | --- | --- | --- |
| `user` | `123` | User | `http://localhost:5173` |
| `friend` | `123` | User | Group-seat invitation participant |
| `checker` | `123` | Checker | `/checkin` |
| `admin` | `123` | Admin | `/admin` |
| `sysadmin` | `123` | System admin | `/admin` |

## User Purchase Flow

1. Log in as `user / 123`.
2. Browse the home page and Top 8 recommendations.
3. Open an `ON_SALE` show such as `THE PHANTOM OF THE OPERA`.
4. Choose an available schedule and enter the seat page.
5. Select seats from the Three.js preview or the 2D seat map.
6. Click checkout, confirm the order, and complete mock payment.
7. Open the electronic ticket page and record the ticket code.

Expected result: the order becomes `PAID`, selected seats become `SOLD`, and electronic tickets show real ticket codes.

## Group-Seat Invitation Flow

1. Browser A logs in as `user / 123`.
2. Browser A opens `/seat/sch-101`, selects one available seat, and clicks `发起拼座`.
3. Copy the generated invitation link.
4. Browser B logs in as `friend / 123` and opens the invitation link.
5. Browser B selects another available seat and clicks `加入/更新座位`.
6. Browser A waits for the polling refresh and confirms the member list shows two users.
7. Browser A clicks `发起人结算`, completes mock payment, and opens the e-ticket page.

Expected result: the group session stays temporary in Redis, but the checked-out order and tickets persist in MySQL.

## Check-In Flow

1. Log in as `checker / 123`.
2. Open `/checkin`.
3. Select the current check-in schedule.
4. Enter a ticket code from a paid order.
5. Submit verification.

Expected result: a valid paid unused ticket becomes `CHECKED_IN`. Duplicate, wrong-schedule, cancelled, too-early, and ended-schedule tickets are rejected with backend business messages.

## Admin Flow

1. Log in as `admin / 123`.
2. Open `/admin` to view dashboard metrics and live refresh state.
3. Use `演出管理` to create, edit, publish, unpublish, or archive shows.
4. Use `场次管理` to create schedules, generate seat pools, update status, or cancel schedules.
5. Use `订单管理` to inspect paid/pending/refunded orders, refund eligible paid orders, or force check in eligible tickets.

Expected result: public user pages only show `PUBLISHED` shows, cancelled schedules reject locking, and dashboard metrics reflect paid/refunded/check-in activity.

## Useful Verification URLs

- Health: `http://localhost:8080/api/health`
- API docs: `http://localhost:8080/doc.html`
- Frontend: `http://localhost:5173`
- Admin: `http://localhost:5173/admin`
- Check-in: `http://localhost:5173/checkin`
