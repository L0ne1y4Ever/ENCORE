package com.encore.service;

import com.encore.dto.SeatStatusChange;
import com.encore.dto.SeatStatusEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SeatStatusPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public SeatStatusPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishSeatStatus(String scheduleId, String reason, String status, List<String> seatIds) {
        publish(scheduleId, reason, seatIds.stream()
                .map(seatId -> new SeatStatusChange(seatId, status))
                .toList());
    }

    public void publishScheduleCancelled(String scheduleId) {
        publish(scheduleId, "CANCELLED", List.of());
    }

    public void publish(String scheduleId, String reason, List<SeatStatusChange> seats) {
        SeatStatusEvent event = new SeatStatusEvent(
                scheduleId,
                reason,
                LocalDateTime.now(),
                seats
        );
        messagingTemplate.convertAndSend("/topic/schedules/%s/seats".formatted(scheduleId), event);
    }
}
