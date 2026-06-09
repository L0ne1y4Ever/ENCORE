package com.encore.config;

import com.encore.entity.AdminOperationLog;
import com.encore.entity.UserAccount;
import com.encore.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

@Component
public class AdminAuditLogInterceptor implements HandlerInterceptor {
    private static final List<String> MUTATING_METHODS = List.of("POST", "PUT", "PATCH", "DELETE");

    private final AuditLogService auditLogService;

    public AdminAuditLogInterceptor(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        if (!shouldRecord(request)) {
            return;
        }

        try {
            AdminOperationLog log = auditLogService.newLog();
            UserAccount actor = auditLogService.findCurrentActor();
            if (actor != null) {
                log.setActorId(actor.getId());
                log.setActorUsername(actor.getUsername());
                log.setActorRole(actor.getRole());
            }

            String path = request.getRequestURI();
            log.setModule(resolveModule(path));
            log.setAction(resolveAction(request.getMethod(), path));
            log.setTargetId(resolveTargetId(path));
            log.setTargetLabel(resolveTargetLabel(path));
            log.setResult((ex == null && response.getStatus() < 400) ? "SUCCESS" : "FAILED");
            log.setDetail(buildDetail(request, response, ex));
            auditLogService.record(log);
        } catch (Exception ignored) {
            // Audit logging must never break the original admin operation.
        }
    }

    private boolean shouldRecord(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return MUTATING_METHODS.contains(method)
                && path.startsWith("/api/admin/")
                && !path.startsWith("/api/admin/audit-logs");
    }

    private String resolveModule(String path) {
        if (path.contains("/users/staff")) return "STAFF";
        if (path.contains("/shows")) return "SHOW";
        if (path.contains("/venues")) return "VENUE";
        if (path.contains("/halls")) return "HALL";
        if (path.contains("/layouts") && path.contains("/sync-seat-status")) return "LAYOUT_SYNC";
        if (path.contains("/layouts") && path.contains("/seats/")) return "LAYOUT_SEAT";
        if (path.contains("/layouts")) return "LAYOUT";
        if (path.contains("/schedules") && path.contains("/inventory")) return "INVENTORY";
        if (path.contains("/schedules")) return "SCHEDULE";
        if (path.contains("/orders")) return "ORDER";
        return "ADMIN";
    }

    private String resolveAction(String method, String path) {
        if (path.endsWith("/refund/approve")) return "APPROVE_REFUND";
        if (path.endsWith("/refund/reject")) return "REJECT_REFUND";
        if (path.endsWith("/refund")) return "REFUND";
        if (path.endsWith("/force-checkin")) return "FORCE_CHECKIN";
        if (path.endsWith("/reset-password")) return "RESET_PASSWORD";
        if (path.endsWith("/sync-seat-status")) return "SYNC_SEAT_STATUS";
        if (path.endsWith("/status")) return "UPDATE_STATUS";
        if (path.contains("/inventory/seats/")) return "UPDATE_SEAT_STATUS";
        if (path.contains("/inventory/areas/")) return "UPDATE_AREA_INVENTORY";
        if ("POST".equals(method)) return "CREATE";
        if ("PUT".equals(method) || "PATCH".equals(method)) return "UPDATE";
        if ("DELETE".equals(method)) return "ARCHIVE_OR_CANCEL";
        return method;
    }

    private String resolveTargetId(String path) {
        String[] segments = Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .toArray(String[]::new);
        for (int index = 0; index < segments.length; index++) {
            String segment = segments[index];
            if (List.of("venues", "halls", "layouts", "shows", "schedules", "orders").contains(segment)
                    && index + 1 < segments.length) {
                return segments[index + 1];
            }
            if ("staff".equals(segment) && index + 1 < segments.length) {
                return segments[index + 1];
            }
        }
        return null;
    }

    private String resolveTargetLabel(String path) {
        String[] segments = Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .toArray(String[]::new);
        if (path.contains("/seats/")) {
            return lastSegmentAfter(segments, "seats");
        }
        if (path.contains("/areas/")) {
            return lastSegmentAfter(segments, "areas");
        }
        return null;
    }

    private String lastSegmentAfter(String[] segments, String marker) {
        for (int index = 0; index < segments.length; index++) {
            if (marker.equals(segments[index]) && index + 1 < segments.length) {
                return segments[index + 1];
            }
        }
        return null;
    }

    private String buildDetail(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        String detail = "%s %s -> HTTP %d".formatted(request.getMethod(), request.getRequestURI(), response.getStatus());
        if (ex != null) {
            detail += " · " + ex.getClass().getSimpleName();
        }
        return detail.length() > 512 ? detail.substring(0, 512) : detail;
    }
}
