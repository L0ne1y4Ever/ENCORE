package com.encore.service;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class GroupOrderSession {
    private String inviteCode;
    private String scheduleId;
    private String hostUserId;
    private String hostDisplayName;
    private String status;
    private String orderId;
    private LocalDateTime expiresAt;
    private Integer maxSeats;
    private List<Member> members = new ArrayList<>();

    @Data
    public static class Member {
        private String userId;
        private String displayName;
        private List<String> seatIds = new ArrayList<>();
        private LocalDateTime joinedAt;
    }
}
