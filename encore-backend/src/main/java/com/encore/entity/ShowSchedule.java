package com.encore.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Data
@TableName("show_schedule")
public class ShowSchedule {
    @TableId
    private String id;
    private String showId;
    private String hallId;
    private String layoutId;
    private String theaterName;
    private LocalDate businessDate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime saleStartTime;
    private LocalDateTime saleEndTime;
    private String status;
    private String publishStatus;
    private String priceRange;
    private String ticketMode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
