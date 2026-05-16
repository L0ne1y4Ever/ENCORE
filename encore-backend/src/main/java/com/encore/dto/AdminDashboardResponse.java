package com.encore.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AdminDashboardResponse(
        BigDecimal totalRevenue,
        long ticketsSold,
        long activeShows,
        BigDecimal avgAttendance,
        List<SalesTrendItem> salesTrend,
        List<TopShowItem> topShows,
        CheckInSummary checkInSummary
) {
    public record SalesTrendItem(
            LocalDate date,
            BigDecimal revenue,
            long ticketCount
    ) {
    }

    public record TopShowItem(
            String showId,
            String showTitle,
            long ticketCount,
            BigDecimal revenue
    ) {
    }

    public record CheckInSummary(
            long checkedIn,
            long unused,
            long voided
    ) {
    }
}
