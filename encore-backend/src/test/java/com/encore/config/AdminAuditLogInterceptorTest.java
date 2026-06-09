package com.encore.config;

import com.encore.entity.AdminOperationLog;
import com.encore.entity.UserAccount;
import com.encore.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAuditLogInterceptorTest {

    @Test
    void resetPasswordAuditLogDoesNotStoreRequestBody() {
        AuditLogService auditLogService = mock(AuditLogService.class);
        AdminOperationLog log = new AdminOperationLog();
        UserAccount actor = new UserAccount();
        actor.setId("u-sys");
        actor.setUsername("sysadmin");
        actor.setRole("sysadmin");

        when(auditLogService.newLog()).thenReturn(log);
        when(auditLogService.findCurrentActor()).thenReturn(actor);

        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/admin/users/staff/u-checker/reset-password"
        );
        request.setContent("{\"password\":\"plain-secret\"}".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        new AdminAuditLogInterceptor(auditLogService)
                .afterCompletion(request, response, new Object(), null);

        assertThat(log.getModule()).isEqualTo("STAFF");
        assertThat(log.getAction()).isEqualTo("RESET_PASSWORD");
        assertThat(log.getResult()).isEqualTo("SUCCESS");
        assertThat(log.getDetail()).contains("POST /api/admin/users/staff/u-checker/reset-password -> HTTP 200");
        assertThat(log.getDetail()).doesNotContain("plain-secret");
        assertThat(log.getDetail()).doesNotContain("{\"password\"");
        verify(auditLogService).record(log);
    }
}
