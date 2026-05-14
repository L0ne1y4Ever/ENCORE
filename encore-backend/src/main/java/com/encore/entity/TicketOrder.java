package com.encore.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ticket_order")
public class TicketOrder {
    @TableId
    private String id;
    private String userId;
    private String scheduleId;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime paidAt;
    private LocalDateTime updatedAt;
}
