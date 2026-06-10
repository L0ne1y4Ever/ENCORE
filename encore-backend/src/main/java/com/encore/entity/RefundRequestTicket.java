package com.encore.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("refund_request_ticket")
public class RefundRequestTicket {
    @TableId
    private String id;
    private String refundRequestId;
    private String orderId;
    private String ticketId;
    private String holderUserId;
    private BigDecimal amount;
    private LocalDateTime createdAt;
}
