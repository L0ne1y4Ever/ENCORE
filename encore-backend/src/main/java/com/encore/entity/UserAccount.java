package com.encore.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_account")
public class UserAccount {
    @TableId
    private String id;
    private String username;
    private String password;
    private String role;
    private String displayName;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
