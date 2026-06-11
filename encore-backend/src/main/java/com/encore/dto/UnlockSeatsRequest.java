package com.encore.dto;

import java.util.List;

public record UnlockSeatsRequest(
        List<String> seatIds
) {
}
