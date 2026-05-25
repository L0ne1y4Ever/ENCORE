package com.encore.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("schedule_area_inventory")
public class ScheduleAreaInventory {
    @TableId
    private String id;
    private String scheduleId;
    private String areaId;
    private BigDecimal price;
    private Integer totalCount;
    private Integer availableCount;
    private Integer lockedCount;
    private Integer soldCount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
