package com.encore.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("venue_area")
public class VenueArea {
    @TableId
    private String id;
    private String hallId;
    private String name;
    private String code;
    private String areaType;
    private Boolean isSeated;
    private Integer capacity;
    private BigDecimal basePrice;
    private Integer availableCount;
    private Integer lockedCount;
    private Integer soldCount;
    private String positionData;
    private String color;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
