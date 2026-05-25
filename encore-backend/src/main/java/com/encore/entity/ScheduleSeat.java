package com.encore.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("schedule_seat")
public class ScheduleSeat {
    @TableId
    private String id;
    private String scheduleId;
    private String seatCode;
    private Integer rowNo;
    private Integer colNo;
    private String section;
    private String status;
    private BigDecimal price;
    private String areaId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
