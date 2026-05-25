package com.encore.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("show_schedule")
public class ShowSchedule {
    @TableId
    private String id;
    private String showId;
    private String theaterName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private String priceRange;
    private String ticketMode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
