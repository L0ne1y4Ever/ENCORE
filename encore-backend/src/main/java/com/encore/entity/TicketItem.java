package com.encore.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ticket_item")
public class TicketItem {
    @TableId
    private String id;
    private String orderId;
    private String scheduleId;
    private String seatId;
    private String ticketCode;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
