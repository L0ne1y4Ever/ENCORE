package com.encore.service;

import com.encore.dto.DashboardRefreshEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DashboardRefreshPublisherTest {
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void publishSendsRefreshEventToAdminDashboardTopic() {
        DashboardRefreshPublisher publisher = new DashboardRefreshPublisher(messagingTemplate);
        ArgumentCaptor<DashboardRefreshEvent> eventCaptor = ArgumentCaptor.forClass(DashboardRefreshEvent.class);

        publisher.publish("ORDER_PAID", "ord-1");

        verify(messagingTemplate).convertAndSend(
                eq("/topic/admin/dashboard"),
                eventCaptor.capture()
        );
        DashboardRefreshEvent event = eventCaptor.getValue();
        assertThat(event.reason()).isEqualTo("ORDER_PAID");
        assertThat(event.referenceId()).isEqualTo("ord-1");
        assertThat(event.timestamp()).isNotNull();
    }
}
