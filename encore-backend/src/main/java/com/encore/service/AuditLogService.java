package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.encore.common.ErrorCode;
import com.encore.dto.AdminOperationLogResponse;
import com.encore.entity.AdminOperationLog;
import com.encore.entity.UserAccount;
import com.encore.exception.BusinessException;
import com.encore.mapper.AdminOperationLogMapper;
import com.encore.mapper.UserAccountMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AuditLogService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final AdminOperationLogMapper adminOperationLogMapper;
    private final UserAccountMapper userAccountMapper;

    public AuditLogService(AdminOperationLogMapper adminOperationLogMapper, UserAccountMapper userAccountMapper) {
        this.adminOperationLogMapper = adminOperationLogMapper;
        this.userAccountMapper = userAccountMapper;
    }

    public List<AdminOperationLogResponse> listLogs(String module, String result, String keyword, Integer limit) {
        ensureSysadminRole();
        int resolvedLimit = resolveLimit(limit);
        LambdaQueryWrapper<AdminOperationLog> query = new LambdaQueryWrapper<AdminOperationLog>()
                .orderByDesc(AdminOperationLog::getCreatedAt)
                .last("limit " + resolvedLimit);
        if (StringUtils.hasText(module)) {
            query.eq(AdminOperationLog::getModule, module.trim().toUpperCase());
        }
        if (StringUtils.hasText(result)) {
            query.eq(AdminOperationLog::getResult, result.trim().toUpperCase());
        }
        if (StringUtils.hasText(keyword)) {
            String key = keyword.trim();
            query.and(wrapper -> wrapper
                    .like(AdminOperationLog::getActorUsername, key)
                    .or()
                    .like(AdminOperationLog::getTargetId, key)
                    .or()
                    .like(AdminOperationLog::getTargetLabel, key)
                    .or()
                    .like(AdminOperationLog::getDetail, key));
        }
        return adminOperationLogMapper.selectList(query).stream()
                .map(this::toResponse)
                .toList();
    }

    public void record(AdminOperationLog log) {
        adminOperationLogMapper.insert(log);
    }

    public AdminOperationLog newLog() {
        AdminOperationLog log = new AdminOperationLog();
        log.setId(generateLogId());
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }

    public UserAccount findCurrentActor() {
        try {
            if (!StpUtil.isLogin()) {
                return null;
            }
            return userAccountMapper.selectById(StpUtil.getLoginIdAsString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private void ensureSysadminRole() {
        String userId = StpUtil.getLoginIdAsString();
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus()) || !"sysadmin".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅系统管理员可查看操作日志");
        }
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(MAX_LIMIT, limit));
    }

    private AdminOperationLogResponse toResponse(AdminOperationLog log) {
        return new AdminOperationLogResponse(
                log.getId(),
                log.getActorId(),
                log.getActorUsername(),
                log.getActorRole(),
                log.getModule(),
                log.getAction(),
                log.getTargetId(),
                log.getTargetLabel(),
                log.getResult(),
                log.getDetail(),
                log.getCreatedAt()
        );
    }

    private String generateLogId() {
        return "log-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
