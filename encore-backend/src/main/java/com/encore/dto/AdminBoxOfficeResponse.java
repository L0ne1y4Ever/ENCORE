package com.encore.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AdminBoxOfficeResponse(
        Summary globalSummary,
        Summary summary,
        List<TrendItem> trends,
        List<CategoryRow> categories,
        List<ShowRow> shows,
        List<ScheduleRow> schedules
) {
    public record Summary(
            BigDecimal salesRevenue,
            BigDecimal refundAmount,
            BigDecimal netRevenue,
            BigDecimal pendingAmount,
            long paidTickets,
            long refundedTickets,
            long validTickets,
            long checkedInTickets,
            BigDecimal attendanceRate
    ) {
    }

    public record TrendItem(
            LocalDate date,
            BigDecimal salesRevenue,
            BigDecimal refundAmount,
            BigDecimal netRevenue,
            BigDecimal pendingAmount,
            long paidTickets,
            long refundedTickets,
            long validTickets
    ) {
    }

    public record ShowRow(
            String showId,
            String showTitle,
            String category,
            BigDecimal salesRevenue,
            BigDecimal refundAmount,
            BigDecimal netRevenue,
            BigDecimal pendingAmount,
            long paidTickets,
            long refundedTickets,
            long validTickets,
            long checkedInTickets,
            BigDecimal attendanceRate,
            long scheduleCount
    ) {
    }

    public record CategoryRow(
            String category,
            BigDecimal salesRevenue,
            BigDecimal refundAmount,
            BigDecimal netRevenue,
            BigDecimal pendingAmount,
            long paidTickets,
            long refundedTickets,
            long validTickets,
            long checkedInTickets,
            BigDecimal attendanceRate,
            long showCount,
            long scheduleCount
    ) {
    }

    public record ScheduleRow(
            String scheduleId,
            String showId,
            String showTitle,
            String theaterName,
            LocalDateTime startTime,
            BigDecimal salesRevenue,
            BigDecimal refundAmount,
            BigDecimal netRevenue,
            BigDecimal pendingAmount,
            long paidTickets,
            long refundedTickets,
            long validTickets,
            long checkedInTickets,
            BigDecimal attendanceRate
    ) {
    }
}
