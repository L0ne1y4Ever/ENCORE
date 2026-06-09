package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.encore.entity.AdminOperationLog;
import com.encore.entity.UserAccount;
import com.encore.exception.BusinessException;
import com.encore.mapper.AdminOperationLogMapper;
import com.encore.mapper.UserAccountMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {
    @Mock
    private AdminOperationLogMapper adminOperationLogMapper;
    @Mock
    private UserAccountMapper userAccountMapper;

    @Test
    void sysadminCanListAuditLogs() {
        AuditLogService service = new AuditLogService(adminOperationLogMapper, userAccountMapper);
        when(userAccountMapper.selectById("u-sys")).thenReturn(user("u-sys", "sysadmin"));
        when(adminOperationLogMapper.selectList(any())).thenReturn(List.of(log()));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-sys");
            var logs = service.listLogs("show", "success", "show-1", 20);

            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).module()).isEqualTo("SHOW");
            assertThat(logs.get(0).result()).isEqualTo("SUCCESS");
        }
    }

    @Test
    void adminCannotListAuditLogs() {
        AuditLogService service = new AuditLogService(adminOperationLogMapper, userAccountMapper);
        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");
            assertThrows(BusinessException.class, () -> service.listLogs(null, null, null, null));
        }
    }

    private AdminOperationLog log() {
        AdminOperationLog log = new AdminOperationLog();
        log.setId("log-1");
        log.setActorId("u-sys");
        log.setActorUsername("sysadmin");
        log.setActorRole("sysadmin");
        log.setModule("SHOW");
        log.setAction("UPDATE_STATUS");
        log.setTargetId("show-1");
        log.setResult("SUCCESS");
        log.setDetail("PATCH /api/admin/shows/show-1/status -> HTTP 200");
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }

    private UserAccount user(String id, String role) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setUsername(id);
        user.setRole(role);
        user.setDisplayName(id);
        user.setStatus("ACTIVE");
        return user;
    }
}
