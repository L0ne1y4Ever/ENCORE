export default {
  common: {
    login: '登录',
    register: '注册',
    logout: '退出',
    username: '账号',
    password: '密码',
    submit: '提交',
    back: '返回',
    cancel: '取消',
    confirm: '确认',
    status: '状态',
    language: '语言',
    languageSwitch: '切换语言',
    loading: '加载中...',
    processing: '处理中...'
  },
  home: {
    nowShowing: '正在热映',
    brand: 'ENCORE',
    shows: '演出',
    profile: '我的'
  },
  auth: {
    registerSuccess: '注册占位流程已完成，请使用演示账号登录。',
    invalidCredentials: '账号或密码错误。可尝试 user/123、admin/123、checker/123 或 sysadmin/123。'
  },
  detail: {
    category: '类型',
    duration: '时长',
    minutes: '分钟',
    tags: '标签',
    synopsis: '剧情简介',
    selectSchedule: '选择场次',
    book: '立即购票',
    reserve: '立即预约',
    unavailable: '暂不可售',
    emptySchedules: '该演出暂无可选场次。'
  },
  reservation: {
    title: '演出预约',
    subtitle: '开售时我们将第一时间通知您。',
    email: '邮箱地址',
    phone: '手机号码',
    successMsg: '预约成功！您是第 {count} 位期待者。',
    myReservations: '我的预约',
    noReservations: '暂无预约记录。'
  },
  seat: {
    stage: '舞台',
    selection: '已选座位',
    available: '可选',
    locked: '锁定',
    sold: '已售',
    yourSelection: '您的选择',
    total: '合计',
    checkout: '去结算',
    noSeats: '暂未选择座位',
    row: '排',
    col: '座',
    locking: '锁座中...',
    conflict: '座位状态已变化，请重新选择。'
  },
  order: {
    confirmation: '确认订单',
    paymentDeadline: '请在以下时间内完成支付',
    seats: '座位',
    tickets: '张票',
    totalAmount: '应付金额',
    proceedToPayment: '去支付',
    expired: '支付超时，订单已取消。',
    createFailed: '创建订单失败'
  },
  payment: {
    title: '支付',
    orderId: '订单号',
    gateway: '模拟支付网关',
    description: '这是演示用支付环境，点击下方按钮即可完成模拟交易。',
    pay: '支付 ${amount}',
    failed: '支付失败或订单已过期。'
  },
  ticket: {
    seat: '座位',
    section: '区域',
    row: '排',
    number: '号',
    statusValid: '状态：有效',
    unused: '未使用'
  },
  admin: {
    dashboard: '数据看板',
    shows: '演出管理',
    schedules: '场次管理',
    orders: '订单管理',
    ai: 'AI 助手',
    totalRevenue: '总营收',
    ticketsSold: '售出票数',
    activeShows: '活跃演出',
    avgAttendance: '平均上座率',
    salesTrend: '7日销量趋势',
    reservations: '预约转化',
    showsManagement: '演出管理',
    addNewShow: '新增演出',
    title: '名称',
    category: '类型',
    durationMinutes: '时长(分钟)',
    actions: '操作',
    edit: '编辑',
    delete: '删除',
    weekdays: {
      mon: '周一',
      tue: '周二',
      wed: '周三',
      thu: '周四',
      fri: '周五',
      sat: '周六',
      sun: '周日'
    }
  },
  checkin: {
    scanLabel: '扫描或输入票码',
    placeholder: '...',
    online: '在线模式',
    offline: '离线模式 (稍后同步)',
    invalid: '无效票据或已核销'
  }
}
