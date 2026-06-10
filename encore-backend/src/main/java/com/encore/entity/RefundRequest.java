package com.encore.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("refund_request")
public class RefundRequest {
    @TableId
    private String id;
    private String orderId;
    private String userId;
    private String requesterId;
    private String status;
    private String source;
    private String scope;
    private String reason;
    private String reviewNote;
    private String reviewerId;
    private String reviewerUsername;
    private BigDecimal refundAmount;
    private Integer ticketCount;
    private LocalDateTime requestedAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime updatedAt;
}
