export default {
  common: {
    login: 'LOGIN',
    register: 'REGISTER',
    logout: 'EXIT',
    username: 'USERNAME',
    password: 'PASSWORD',
    submit: 'SUBMIT',
    back: 'BACK',
    cancel: 'CANCEL',
    confirm: 'CONFIRM',
    status: 'Status',
    language: 'Language',
    languageSwitch: 'Switch language',
    loading: 'Loading...',
    processing: 'Processing...'
  },
  home: {
    nowShowing: 'Now Showing',
    brand: 'ENCORE',
    shows: 'Shows',
    profile: 'Profile'
  },
  auth: {
    registerSuccess: 'Registration placeholder completed. Please log in with a demo account.',
    invalidCredentials: 'Invalid credentials. Try user/123, admin/123, checker/123, or sysadmin/123.'
  },
  detail: {
    category: 'Category',
    duration: 'Duration',
    minutes: 'Mins',
    tags: 'Tags',
    synopsis: 'Synopsis',
    selectSchedule: 'Select Schedule',
    book: 'Book Tickets',
    reserve: 'Reserve / Notify Me',
    unavailable: 'Unavailable',
    emptySchedules: 'No schedules available for this show.'
  },
  reservation: {
    title: 'Reserve Show',
    subtitle: 'Get notified as soon as tickets go on sale.',
    email: 'Email Address',
    phone: 'Phone Number',
    successMsg: 'You are number {count} in line. We will notify you.',
    myReservations: 'My Reservations',
    noReservations: 'No reservations yet.'
  },
  seat: {
    stage: 'STAGE',
    selection: 'Selection',
    available: 'Available',
    locked: 'Locked',
    sold: 'Sold',
    yourSelection: 'Your Selection',
    total: 'Total',
    checkout: 'Checkout',
    noSeats: 'No seats selected',
    row: 'Row',
    col: 'Col',
    locking: 'Locking...',
    conflict: 'Seats are no longer available, please reselect.'
  },
  order: {
    confirmation: 'Order Confirmation',
    paymentDeadline: 'Please complete your payment within',
    seats: 'Seats',
    tickets: 'Tickets',
    totalAmount: 'Total Amount',
    proceedToPayment: 'Proceed to Payment',
    expired: 'Time expired. Order cancelled.',
    createFailed: 'Failed to create order'
  },
  payment: {
    title: 'Payment',
    orderId: 'Order ID',
    gateway: 'Mock Gateway',
    description: 'This is a simulated payment environment. Click below to complete the transaction.',
    pay: 'Pay ${amount}',
    failed: 'Payment failed or order expired.'
  },
  ticket: {
    seat: 'SEAT',
    section: 'SEC',
    row: 'ROW',
    number: 'NO.',
    statusValid: 'Status: VALID',
    unused: 'Unused'
  },
  admin: {
    dashboard: 'Dashboard',
    shows: 'Shows',
    schedules: 'Schedules',
    orders: 'Orders',
    ai: 'AI Assistant',
    totalRevenue: 'Total Revenue',
    ticketsSold: 'Tickets Sold',
    activeShows: 'Active Shows',
    avgAttendance: 'Avg Attendance',
    salesTrend: '7-Day Sales Trend',
    reservations: 'Reservations',
    showsManagement: 'Shows Management',
    addNewShow: 'Add New Show',
    title: 'Title',
    category: 'Category',
    durationMinutes: 'Duration (m)',
    actions: 'Actions',
    edit: 'Edit',
    delete: 'Delete',
    weekdays: {
      mon: 'Mon',
      tue: 'Tue',
      wed: 'Wed',
      thu: 'Thu',
      fri: 'Fri',
      sat: 'Sat',
      sun: 'Sun'
    }
  },
  checkin: {
    scanLabel: 'SCAN OR ENTER TICKET',
    placeholder: '...',
    online: 'ONLINE',
    offline: 'OFFLINE MODE (SYNC LATER)',
    invalid: 'INVALID TICKET OR ALREADY USED'
  }
}
