package com.encore.dto;

import java.util.List;

public record RefundOrderRequest(String reason, List<String> ticketIds) {
}
