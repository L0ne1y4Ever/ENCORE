package com.encore.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("venue")
public class Venue {
    @TableId
    private String id;
    private String name;
    private String city;
    private String address;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
