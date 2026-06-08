package com.encore.service;

import com.encore.dto.ShowRecommendationResponse;
import com.encore.entity.ScheduleSeat;
import com.encore.entity.ShowEntity;
import com.encore.entity.ShowSchedule;
import com.encore.entity.TicketItem;
import com.encore.entity.TicketOrder;
import com.encore.mapper.ShowMapper;
import com.encore.mapper.ShowScheduleMapper;
import com.encore.mapper.ScheduleAreaInventoryMapper;
import com.encore.mapper.ScheduleSeatMapper;
import com.encore.mapper.TicketItemMapper;
import com.encore.mapper.TicketOrderMapper;
import com.encore.mapper.VenueAreaMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShowServiceTest {
    @Mock
    private ShowMapper showMapper;
    @Mock
    private ShowScheduleMapper showScheduleMapper;
    @Mock
    private TicketOrderMapper ticketOrderMapper;
    @Mock
    private TicketItemMapper ticketItemMapper;
    @Mock
    private ScheduleSeatMapper scheduleSeatMapper;
    @Mock
    private ScheduleAreaInventoryMapper scheduleAreaInventoryMapper;
    @Mock
    private VenueAreaMapper venueAreaMapper;
    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    void recommendationsPrioritizeOnSaleBeforeHigherSellingUnavailableShow() {
        ShowService service = createService();
        ShowEntity unavailablePopular = show("s-popular", "PUBLISHED", 10, 1);
        ShowEntity onSale = show("s-on-sale", "PUBLISHED", 20, 2);

        when(showMapper.selectList(any())).thenReturn(List.of(unavailablePopular, onSale));
        when(showScheduleMapper.selectList(any())).thenReturn(List.of(
                schedule("sch-popular", "s-popular", "PREPARING"),
                schedule("sch-on-sale", "s-on-sale", "ON_SALE")
        ));
        when(ticketOrderMapper.selectList(any())).thenReturn(List.of(
                order("ord-popular", "sch-popular", "PAID", 500),
                order("ord-on-sale", "sch-on-sale", "PAID", 100)
        ));
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(
                ticket("tk-pop-1", "ord-popular", "sch-popular", "UNUSED"),
                ticket("tk-pop-2", "ord-popular", "sch-popular", "CHECKED_IN"),
                ticket("tk-pop-3", "ord-popular", "sch-popular", "UNUSED"),
                ticket("tk-sale-1", "ord-on-sale", "sch-on-sale", "UNUSED")
        ));

        List<ShowRecommendationResponse> recommendations = service.listTopRecommendations();

        assertThat(recommendations).extracting(ShowRecommendationResponse::id)
                .containsExactly("s-on-sale", "s-popular");
        assertThat(recommendations.get(0).availableScheduleCount()).isEqualTo(1L);
        assertThat(recommendations.get(0).rank()).isEqualTo(1);
    }

    @Test
    void recommendationsSortOnSaleShowsByTicketCountThenRevenue() {
        ShowService service = createService();

        when(showMapper.selectList(any())).thenReturn(List.of(
                show("s-low", "PUBLISHED", 10, 1),
                show("s-high-revenue", "PUBLISHED", 20, 2),
                show("s-low-revenue", "PUBLISHED", 30, 3)
        ));
        when(showScheduleMapper.selectList(any())).thenReturn(List.of(
                schedule("sch-low", "s-low", "ON_SALE"),
                schedule("sch-high-revenue", "s-high-revenue", "ON_SALE"),
                schedule("sch-low-revenue", "s-low-revenue", "ON_SALE")
        ));
        when(ticketOrderMapper.selectList(any())).thenReturn(List.of(
                order("ord-low", "sch-low", "PAID", 100),
                order("ord-high-revenue", "sch-high-revenue", "PAID", 200),
                order("ord-low-revenue", "sch-low-revenue", "PAID", 120)
        ));
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(
                ticket("tk-low-1", "ord-low", "sch-low", "UNUSED"),
                ticket("tk-high-1", "ord-high-revenue", "sch-high-revenue", "UNUSED"),
                ticket("tk-high-2", "ord-high-revenue", "sch-high-revenue", "CHECKED_IN"),
                ticket("tk-lowrev-1", "ord-low-revenue", "sch-low-revenue", "UNUSED"),
                ticket("tk-lowrev-2", "ord-low-revenue", "sch-low-revenue", "UNUSED")
        ));

        List<ShowRecommendationResponse> recommendations = service.listTopRecommendations();

        assertThat(recommendations).extracting(ShowRecommendationResponse::id)
                .containsExactly("s-high-revenue", "s-low-revenue", "s-low");
        assertThat(recommendations.get(0).ticketsSold()).isEqualTo(2L);
        assertThat(recommendations.get(0).hotScore()).isGreaterThan(recommendations.get(1).hotScore());
    }

    @Test
    void recommendationsExcludeUnpublishedShowsAndIgnoreInvalidTicketSources() {
        ShowService service = createService();

        when(showMapper.selectList(any())).thenReturn(List.of(
                show("s-published", "PUBLISHED", 10, 1),
                show("s-draft", "DRAFT", 20, 2),
                show("s-archived", "ARCHIVED", 30, 3)
        ));
        when(showScheduleMapper.selectList(any())).thenReturn(List.of(
                schedule("sch-published", "s-published", "ON_SALE"),
                schedule("sch-draft", "s-draft", "ON_SALE")
        ));
        when(ticketOrderMapper.selectList(any())).thenReturn(List.of(
                order("ord-paid", "sch-published", "PAID", 100),
                order("ord-pending", "sch-published", "PENDING_PAYMENT", 300),
                order("ord-refunded", "sch-published", "REFUNDED", 300)
        ));
        when(ticketItemMapper.selectList(any())).thenReturn(List.of(
                ticket("tk-valid", "ord-paid", "sch-published", "UNUSED"),
                ticket("tk-void", "ord-paid", "sch-published", "VOID"),
                ticket("tk-pending", "ord-pending", "sch-published", "UNUSED"),
                ticket("tk-refunded", "ord-refunded", "sch-published", "UNUSED")
        ));

        List<ShowRecommendationResponse> recommendations = service.listTopRecommendations();

        assertThat(recommendations).hasSize(1);
        assertThat(recommendations.get(0).id()).isEqualTo("s-published");
        assertThat(recommendations.get(0).ticketsSold()).isEqualTo(1L);
    }

    @Test
    void recommendationsIgnoreUnpublishedSchedules() {
        ShowService service = createService();

        when(showMapper.selectList(any())).thenReturn(List.of(show("s-published", "PUBLISHED", 10, 1)));
        ShowSchedule hidden = schedule("sch-hidden", "s-published", "ON_SALE");
        hidden.setPublishStatus("DRAFT");
        when(showScheduleMapper.selectList(any())).thenReturn(List.of(hidden));

        List<ShowRecommendationResponse> recommendations = service.listTopRecommendations();

        assertThat(recommendations).hasSize(1);
        assertThat(recommendations.get(0).availableScheduleCount()).isZero();
        assertThat(recommendations.get(0).availableTicketCount()).isZero();
    }

    @Test
    void recommendationsFallbackToSortOrderWhenNoSalesDataExists() {
        ShowService service = createService();

        when(showMapper.selectList(any())).thenReturn(List.of(
                show("s-second", "PUBLISHED", 20, 1),
                show("s-first", "PUBLISHED", 10, 2)
        ));
        when(showScheduleMapper.selectList(any())).thenReturn(List.of(
                schedule("sch-second", "s-second", "ON_SALE"),
                schedule("sch-first", "s-first", "ON_SALE")
        ));
        when(ticketOrderMapper.selectList(any())).thenReturn(List.of());

        List<ShowRecommendationResponse> recommendations = service.listTopRecommendations();

        assertThat(recommendations).extracting(ShowRecommendationResponse::id)
                .containsExactly("s-first", "s-second");
        assertThat(recommendations).allSatisfy(item -> assertThat(item.ticketsSold()).isZero());
        verify(ticketItemMapper, never()).selectList(any());
    }

    @Test
    void recommendationsExposeRemainingAvailableTickets() {
        ShowService service = createService();

        when(showMapper.selectList(any())).thenReturn(List.of(show("s-available", "PUBLISHED", 10, 1)));
        when(showScheduleMapper.selectList(any())).thenReturn(List.of(schedule("sch-available", "s-available", "ON_SALE")));
        when(scheduleSeatMapper.selectList(any())).thenReturn(List.of(availableSeat("seat-1-1"), availableSeat("seat-1-2")));
        when(redisTemplate.hasKey("encore:seat-lock:sch-available:seat-1-1")).thenReturn(false);
        when(redisTemplate.hasKey("encore:seat-lock:sch-available:seat-1-2")).thenReturn(true);
        when(ticketOrderMapper.selectList(any())).thenReturn(List.of());

        List<ShowRecommendationResponse> recommendations = service.listTopRecommendations();

        assertThat(recommendations).hasSize(1);
        assertThat(recommendations.get(0).availableTicketCount()).isEqualTo(1L);
    }

    @Test
    void recommendationsLimitToEightShowsAndAssignRanks() {
        ShowService service = createService();
        List<ShowEntity> shows = java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(index -> show("s-%02d".formatted(index), "PUBLISHED", index * 10, index))
                .toList();
        List<ShowSchedule> schedules = java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(index -> schedule("sch-%02d".formatted(index), "s-%02d".formatted(index), "ON_SALE"))
                .toList();

        when(showMapper.selectList(any())).thenReturn(shows);
        when(showScheduleMapper.selectList(any())).thenReturn(schedules);
        when(ticketOrderMapper.selectList(any())).thenReturn(List.of());

        List<ShowRecommendationResponse> recommendations = service.listTopRecommendations();

        assertThat(recommendations).hasSize(8);
        assertThat(recommendations).extracting(ShowRecommendationResponse::rank)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(recommendations).extracting(ShowRecommendationResponse::id)
                .containsExactly("s-01", "s-02", "s-03", "s-04", "s-05", "s-06", "s-07", "s-08");
    }

    private ShowService createService() {
        return new ShowService(
                showMapper,
                showScheduleMapper,
                ticketOrderMapper,
                ticketItemMapper,
                scheduleSeatMapper,
                scheduleAreaInventoryMapper,
                venueAreaMapper,
                redisTemplate
        );
    }

    private ShowEntity show(String id, String status, int sortOrder, int createdOffset) {
        ShowEntity show = new ShowEntity();
        show.setId(id);
        show.setTitle("Show " + id);
        show.setSubtitle("Subtitle " + id);
        show.setCoverUrl("https://example.com/" + id + ".jpg");
        show.setDescription("Description " + id);
        show.setDuration(120);
        show.setCategory("Musical");
        show.setTags(List.of("Tag"));
        show.setStatus(status);
        show.setSortOrder(sortOrder);
        show.setCreatedAt(LocalDateTime.of(2026, 5, 1, 10, 0).plusDays(createdOffset));
        return show;
    }

    private ShowSchedule schedule(String id, String showId, String status) {
        ShowSchedule schedule = new ShowSchedule();
        schedule.setId(id);
        schedule.setShowId(showId);
        schedule.setTheaterName("Main Hall");
        schedule.setStatus(status);
        return schedule;
    }

    private TicketOrder order(String id, String scheduleId, String status, int amount) {
        TicketOrder order = new TicketOrder();
        order.setId(id);
        order.setScheduleId(scheduleId);
        order.setStatus(status);
        order.setTotalAmount(BigDecimal.valueOf(amount));
        return order;
    }

    private TicketItem ticket(String id, String orderId, String scheduleId, String status) {
        TicketItem ticket = new TicketItem();
        ticket.setId(id);
        ticket.setOrderId(orderId);
        ticket.setScheduleId(scheduleId);
        ticket.setStatus(status);
        return ticket;
    }

    private ScheduleSeat availableSeat(String seatCode) {
        ScheduleSeat seat = new ScheduleSeat();
        seat.setScheduleId("sch-available");
        seat.setSeatCode(seatCode);
        seat.setStatus("AVAILABLE");
        return seat;
    }
}
