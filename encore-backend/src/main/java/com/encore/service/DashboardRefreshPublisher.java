package com.encore.service;

import com.encore.dto.DashboardRefreshEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DashboardRefreshPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public DashboardRefreshPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publish(String reason, String referenceId) {
        DashboardRefreshEvent event = new DashboardRefreshEvent(
                reason,
                referenceId,
                LocalDateTime.now()
        );
        messagingTemplate.convertAndSend("/topic/admin/dashboard", event);
    }
}
