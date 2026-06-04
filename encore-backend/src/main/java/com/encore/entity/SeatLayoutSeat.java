package com.encore.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("seat_layout_seat")
public class SeatLayoutSeat {
    @TableId
    private String id;
    private String layoutId;
    private String areaId;
    private String seatCode;
    private Integer rowNo;
    private Integer colNo;
    private String section;
    private String status;
    private BigDecimal price;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
