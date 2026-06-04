package com.encore.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("venue_hall")
public class VenueHall {
    @TableId
    private String id;
    private String venueId;
    private String name;
    private String hallType;
    private Integer capacity;
    private String defaultLayoutId;
    private Integer clearanceMinutes;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
