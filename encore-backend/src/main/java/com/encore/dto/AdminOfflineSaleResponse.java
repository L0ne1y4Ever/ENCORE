package com.encore.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdminOfflineSaleResponse(
        AdminOrderResponse order,
        BigDecimal totalAmount,
        List<AdminOfflineSaleTicketResponse> tickets
) {
}
