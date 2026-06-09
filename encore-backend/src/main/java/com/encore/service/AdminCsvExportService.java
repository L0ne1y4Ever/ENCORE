package com.encore.service;

import com.encore.dto.AdminBoxOfficeResponse;
import com.encore.dto.AdminDashboardResponse;
import com.encore.dto.AdminOrderResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AdminCsvExportService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> BOX_OFFICE_HEADERS = List.of(
            "分组",
            "层级",
            "名称",
            "类型",
            "日期/场次",
            "剧场",
            "销售票房",
            "退款金额",
            "净票房",
            "待支付金额",
            "支付票数",
            "退款票数",
            "有效票数",
            "已核销",
            "核销率",
            "票房演出数",
            "票房场次数"
    );

    private final AdminService adminService;

    public AdminCsvExportService(AdminService adminService) {
        this.adminService = adminService;
    }

    public String exportOrdersCsv() {
        List<AdminOrderResponse> orders = adminService.listOrders();
        return csv(
                List.of("订单号", "用户", "演出", "剧场", "金额", "状态", "票数", "已核销", "创建时间", "支付时间"),
                orders.stream()
                        .map(order -> List.of(
                                value(order.id()),
                                value(order.username()),
                                value(order.showName()),
                                value(order.theaterName()),
                                money(order.totalAmount()),
                                value(displayStatus(order)),
                                value(order.ticketCount()),
                                value(order.checkedInCount()),
                                dateTime(order.createdAt()),
                                dateTime(order.paidAt())
                        ))
                        .toList()
        );
    }

    public String exportDashboardCsv() {
        AdminDashboardResponse dashboard = adminService.dashboard();
        List<List<String>> rows = new java.util.ArrayList<>();
        rows.add(List.of("数据看板", "总营收", money(dashboard.totalRevenue())));
        rows.add(List.of("数据看板", "售出票数", value(dashboard.ticketsSold())));
        rows.add(List.of("数据看板", "活跃演出", value(dashboard.activeShows())));
        rows.add(List.of("数据看板", "平均上座率", percent(dashboard.avgAttendance())));
        AdminBoxOfficeResponse.Summary financeSummary = dashboard.financeSummary();
        if (financeSummary != null) {
            rows.add(List.of("财务摘要", "销售票房", money(financeSummary.salesRevenue())));
            rows.add(List.of("财务摘要", "退款金额", money(financeSummary.refundAmount())));
            rows.add(List.of("财务摘要", "净票房", money(financeSummary.netRevenue())));
            rows.add(List.of("财务摘要", "待支付金额", money(financeSummary.pendingAmount())));
        }
        for (AdminDashboardResponse.SalesTrendItem item : safeList(dashboard.salesTrend())) {
            rows.add(List.of(
                    "7日销量趋势",
                    date(item.date()),
                    "票数 " + item.ticketCount() + " / 营收 " + money(item.revenue())
            ));
        }
        for (AdminDashboardResponse.TopShowItem item : safeList(dashboard.topShows())) {
            rows.add(List.of(
                    "热门演出",
                    value(item.showTitle()),
                    "票数 " + item.ticketCount() + " / 营收 " + money(item.revenue())
            ));
        }
        AdminDashboardResponse.CheckInSummary checkInSummary = dashboard.checkInSummary() == null
                ? new AdminDashboardResponse.CheckInSummary(0, 0, 0)
                : dashboard.checkInSummary();
        rows.add(List.of("核销概览", "已核销", value(checkInSummary.checkedIn())));
        rows.add(List.of("核销概览", "未核销票", value(checkInSummary.unused())));
        rows.add(List.of("核销概览", "作废票", value(checkInSummary.voided())));
        return csv(List.of("分组", "指标", "数值"), rows);
    }

    public String exportBoxOfficeCsv(String range, LocalDate startDate, LocalDate endDate, String showId, String category) {
        AdminBoxOfficeResponse boxOffice = adminService.boxOffice(range, startDate, endDate, showId, category);
        List<List<String>> rows = new java.util.ArrayList<>();
        AdminBoxOfficeResponse.Summary globalSummary = boxOffice.globalSummary();
        rows.add(boxOfficeRow(
                "摘要",
                "全局总票房",
                "全平台累计",
                "",
                "",
                "",
                globalSummary.salesRevenue(),
                globalSummary.refundAmount(),
                globalSummary.netRevenue(),
                globalSummary.pendingAmount(),
                globalSummary.paidTickets(),
                globalSummary.refundedTickets(),
                globalSummary.validTickets(),
                globalSummary.checkedInTickets(),
                globalSummary.attendanceRate(),
                "",
                ""
        ));
        AdminBoxOfficeResponse.Summary summary = boxOffice.summary();
        rows.add(boxOfficeRow(
                "摘要",
                "当前筛选",
                "当前筛选",
                value(category),
                rangeLabel(range, startDate, endDate),
                "",
                summary.salesRevenue(),
                summary.refundAmount(),
                summary.netRevenue(),
                summary.pendingAmount(),
                summary.paidTickets(),
                summary.refundedTickets(),
                summary.validTickets(),
                summary.checkedInTickets(),
                summary.attendanceRate(),
                "",
                ""
        ));
        for (AdminBoxOfficeResponse.TrendItem item : safeList(boxOffice.trends())) {
            rows.add(boxOfficeRow(
                    "每日趋势",
                    "日期",
                    date(item.date()),
                    "",
                    date(item.date()),
                    "",
                    item.salesRevenue(),
                    item.refundAmount(),
                    item.netRevenue(),
                    item.pendingAmount(),
                    item.paidTickets(),
                    item.refundedTickets(),
                    item.validTickets(),
                    "",
                    null,
                    "",
                    ""
            ));
        }
        for (AdminBoxOfficeResponse.CategoryRow row : safeList(boxOffice.categories())) {
            rows.add(boxOfficeRow(
                    "类型票房",
                    "类型",
                    value(row.category()),
                    value(row.category()),
                    "",
                    "",
                    row.salesRevenue(),
                    row.refundAmount(),
                    row.netRevenue(),
                    row.pendingAmount(),
                    row.paidTickets(),
                    row.refundedTickets(),
                    row.validTickets(),
                    row.checkedInTickets(),
                    row.attendanceRate(),
                    row.showCount(),
                    row.scheduleCount()
            ));
        }
        for (AdminBoxOfficeResponse.ShowRow row : safeList(boxOffice.shows())) {
            rows.add(boxOfficeRow(
                    "演出票房",
                    "演出",
                    value(row.showTitle()),
                    value(row.category()),
                    "",
                    "",
                    row.salesRevenue(),
                    row.refundAmount(),
                    row.netRevenue(),
                    row.pendingAmount(),
                    row.paidTickets(),
                    row.refundedTickets(),
                    row.validTickets(),
                    row.checkedInTickets(),
                    row.attendanceRate(),
                    "",
                    row.scheduleCount()
            ));
        }
        for (AdminBoxOfficeResponse.ScheduleRow row : safeList(boxOffice.schedules())) {
            rows.add(boxOfficeRow(
                    "场次票房",
                    "场次",
                    value(row.showTitle()),
                    "",
                    dateTime(row.startTime()),
                    value(row.theaterName()),
                    row.salesRevenue(),
                    row.refundAmount(),
                    row.netRevenue(),
                    row.pendingAmount(),
                    row.paidTickets(),
                    row.refundedTickets(),
                    row.validTickets(),
                    row.checkedInTickets(),
                    row.attendanceRate(),
                    "",
                    ""
            ));
        }
        return csv(BOX_OFFICE_HEADERS, rows);
    }

    private List<String> boxOfficeRow(
            String section,
            String level,
            String name,
            String category,
            String period,
            String theater,
            BigDecimal salesRevenue,
            BigDecimal refundAmount,
            BigDecimal netRevenue,
            BigDecimal pendingAmount,
            Object paidTickets,
            Object refundedTickets,
            Object validTickets,
            Object checkedInTickets,
            BigDecimal attendanceRate,
            Object showCount,
            Object scheduleCount
    ) {
        return List.of(
                value(section),
                value(level),
                value(name),
                value(category),
                value(period),
                value(theater),
                money(salesRevenue),
                money(refundAmount),
                money(netRevenue),
                money(pendingAmount),
                value(paidTickets),
                value(refundedTickets),
                value(validTickets),
                value(checkedInTickets),
                percent(attendanceRate),
                value(showCount),
                value(scheduleCount)
        );
    }

    private String rangeLabel(String range, LocalDate startDate, LocalDate endDate) {
        if ("CUSTOM".equals(range)) {
            return date(startDate) + " 至 " + date(endDate);
        }
        if (range == null || range.isBlank()) {
            return "LAST_30_DAYS";
        }
        return range;
    }

    private String csv(List<String> headers, List<List<String>> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append(toLine(headers));
        for (List<String> row : rows) {
            builder.append("\r\n").append(toLine(row));
        }
        return builder.toString();
    }

    private String toLine(List<String> cells) {
        return cells.stream().map(this::escape).collect(java.util.stream.Collectors.joining(","));
    }

    private String escape(String value) {
        String text = value == null ? "" : value;
        if (text.contains("\"") || text.contains(",") || text.contains("\r") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private String displayStatus(AdminOrderResponse order) {
        if ("PAID".equals(order.status()) && order.ticketCount() > 0 && order.checkedInCount() >= order.ticketCount()) {
            return "CHECKED_IN";
        }
        return order.status();
    }

    private String money(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private String percent(BigDecimal value) {
        return value == null ? "" : value.toPlainString() + "%";
    }

    private String date(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    private String dateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME_FORMATTER);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
