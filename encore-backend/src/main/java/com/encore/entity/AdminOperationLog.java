package com.encore.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("admin_operation_log")
public class AdminOperationLog {
    @TableId
    private String id;
    private String actorId;
    private String actorUsername;
    private String actorRole;
    private String module;
    private String action;
    private String targetId;
    private String targetLabel;
    private String result;
    private String detail;
    private LocalDateTime createdAt;
}
