package com.encore.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("seat_layout")
public class SeatLayout {
    @TableId
    private String id;
    private String hallId;
    private String name;
    private String ticketMode;
    private Integer version;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
