package com.encore.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "encore_show", autoResultMap = true)
public class ShowEntity {
    @TableId
    private String id;
    private String title;
    private String subtitle;
    private String coverUrl;
    private String description;
    private Integer duration;
    private String category;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
    private String status;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
