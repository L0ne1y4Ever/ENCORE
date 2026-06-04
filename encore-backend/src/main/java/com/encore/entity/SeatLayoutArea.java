package com.encore.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("seat_layout_area")
public class SeatLayoutArea {
    @TableId
    private String id;
    private String layoutId;
    private String name;
    private String code;
    private String areaType;
    private Boolean isSeated;
    private Integer capacity;
    private BigDecimal basePrice;
    private String color;
    private String description;
    private String positionData;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
