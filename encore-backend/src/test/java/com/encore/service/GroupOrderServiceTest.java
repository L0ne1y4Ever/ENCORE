package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.encore.dto.CreateGroupOrderRequest;
import com.encore.dto.GroupOrderResponse;
import com.encore.dto.JoinGroupOrderRequest;
import com.encore.entity.ScheduleSeat;
import com.encore.entity.UserAccount;
import com.encore.exception.BusinessException;
import com.encore.mapper.UserAccountMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class GroupOrderServiceTest {
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private SeatService seatService;
    @Mock
    private OrderService orderService;
    @Mock
    private UserAccountMapper userAccountMapper;

    private GroupOrderService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new GroupOrderService(
                redisTemplate,
                objectMapper,
                seatService,
                orderService,
                userAccountMapper
        );
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void createGroupOrderLocksSeatsAndStoresSession() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(userAccountMapper.selectById("u-101")).thenReturn(user("u-101", "普通用户"));
        when(seatService.findSeats("sch-1", List.of("seat-1-1", "seat-1-2")))
                .thenReturn(List.of(seat("seat-1-1", 150), seat("seat-1-2", 100)));

        GroupOrderResponse response;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-101");
            response = service.create(new CreateGroupOrderRequest("sch-1", List.of("seat-1-1", "seat-1-2")));
        }

        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.hostUserId()).isEqualTo("u-101");
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(250));
        verify(seatService).lockSeatsForOwner(
                eq("sch-1"),
                eq(List.of("seat-1-1", "seat-1-2")),
                startsWith("group:g-"),
                eq(Duration.ofMinutes(15)),
                eq(true)
        );
        verify(valueOperations).set(startsWith("encore:group-order:g-"), anyString(), eq(Duration.ofMinutes(15)));
    }

    @Test
    void joinReplacesCurrentMemberSeatsAndReleasesOldSeat() throws Exception {
        GroupOrderSession session = session("OPEN");
        session.getMembers().add(member("u-101", "普通用户", List.of("seat-1-1")));
        session.getMembers().add(member("u-102", "拼座好友", List.of("seat-1-2")));
        when(valueOperations.get("encore:group-order:g-test")).thenReturn(objectMapper.writeValueAsString(session));
        when(redisTemplate.getExpire("encore:group-order:g-test", TimeUnit.SECONDS)).thenReturn(600L);
        when(userAccountMapper.selectById("u-102")).thenReturn(user("u-102", "拼座好友"));
        when(seatService.findSeats("sch-1", List.of("seat-1-1", "seat-1-3")))
                .thenReturn(List.of(seat("seat-1-1", 150), seat("seat-1-3", 100)));

        GroupOrderResponse response;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-102");
            response = service.join("g-test", new JoinGroupOrderRequest(List.of("seat-1-3")));
        }

        assertThat(response.members())
                .filteredOn(member -> "u-102".equals(member.userId()))
                .singleElement()
                .extracting(member -> member.seatIds())
                .isEqualTo(List.of("seat-1-3"));
        verify(seatService).lockSeatsForOwner("sch-1", List.of("seat-1-3"), "group:g-test", Duration.ofSeconds(600), true);
        verify(seatService).releaseLocksOwnedBy("sch-1", List.of("seat-1-2"), "group:g-test", true);
    }

    @Test
    void joinRejectsWhenTotalSeatsExceedLimit() throws Exception {
        GroupOrderSession session = session("OPEN");
        session.getMembers().add(member("u-101", "普通用户", List.of("seat-1-1", "seat-1-2", "seat-1-3", "seat-1-4", "seat-1-5")));
        when(valueOperations.get("encore:group-order:g-test")).thenReturn(objectMapper.writeValueAsString(session));
        when(redisTemplate.getExpire("encore:group-order:g-test", TimeUnit.SECONDS)).thenReturn(600L);
        when(userAccountMapper.selectById("u-102")).thenReturn(user("u-102", "拼座好友"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-102");
            assertThatThrownBy(() -> service.join("g-test", new JoinGroupOrderRequest(List.of("seat-1-6", "seat-1-7"))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("拼座最多支持 6 个座位");
        }
    }

    @Test
    void nonHostCannotCheckout() throws Exception {
        GroupOrderSession session = session("OPEN");
        session.getMembers().add(member("u-101", "普通用户", List.of("seat-1-1")));
        when(valueOperations.get("encore:group-order:g-test")).thenReturn(objectMapper.writeValueAsString(session));
        when(userAccountMapper.selectById("u-102")).thenReturn(user("u-102", "拼座好友"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-102");
            assertThatThrownBy(() -> service.checkout("g-test"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("仅发起人可结算拼座");
        }
    }

    @Test
    void missingInviteCodeIsRejected() {
        when(valueOperations.get("encore:group-order:g-missing")).thenReturn(null);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-101");
            assertThatThrownBy(() -> service.get("g-missing"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("拼座会话不存在或已过期");
        }
    }

    @Test
    void hostCheckoutCreatesOrderFromGroupLocks() throws Exception {
        GroupOrderSession session = session("OPEN");
        session.getMembers().add(member("u-101", "普通用户", List.of("seat-1-1")));
        session.getMembers().add(member("u-102", "拼座好友", List.of("seat-1-2")));
        when(valueOperations.get("encore:group-order:g-test")).thenReturn(objectMapper.writeValueAsString(session));
        when(userAccountMapper.selectById("u-101")).thenReturn(user("u-101", "普通用户"));
        when(orderService.createGroupOrder(
                eq("sch-1"),
                argThat(holders -> holders != null
                        && holders.size() == 2
                        && holders.stream().anyMatch(holder ->
                        "seat-1-1".equals(holder.seatId())
                                && "u-101".equals(holder.userId())
                                && "普通用户".equals(holder.displayName()))
                        && holders.stream().anyMatch(holder ->
                        "seat-1-2".equals(holder.seatId())
                                && "u-102".equals(holder.userId())
                                && "拼座好友".equals(holder.displayName()))),
                eq("group:g-test")
        )).thenReturn("ord-1");

        String orderId;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-101");
            orderId = service.checkout("g-test");
        }

        assertThat(orderId).isEqualTo("ord-1");
        verify(seatService).ensureLocksOwnedBy("sch-1", List.of("seat-1-1", "seat-1-2"), "group:g-test");
        verify(orderService).repairGroupTicketHolders(
                eq("ord-1"),
                argThat(holders -> holders != null
                        && holders.size() == 2
                        && holders.stream().anyMatch(holder ->
                        "seat-1-1".equals(holder.seatId())
                                && "u-101".equals(holder.userId())
                                && "普通用户".equals(holder.displayName()))
                        && holders.stream().anyMatch(holder ->
                        "seat-1-2".equals(holder.seatId())
                                && "u-102".equals(holder.userId())
                                && "拼座好友".equals(holder.displayName())))
        );
        ArgumentCaptor<String> savedSession = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("encore:group-order:g-test"), savedSession.capture(), eq(Duration.ofMinutes(15)));
        assertThat(objectMapper.readValue(savedSession.getValue(), GroupOrderSession.class).getStatus()).isEqualTo("CHECKED_OUT");
    }

    @Test
    void paidGroupResponseRepairsHoldersAndPersistsPaidStatus() throws Exception {
        GroupOrderSession session = session("CHECKED_OUT");
        session.setOrderId("ord-1");
        session.getMembers().add(member("u-101", "普通用户", List.of("seat-1-1")));
        session.getMembers().add(member("u-102", "拼座好友", List.of("seat-1-2")));
        when(valueOperations.get("encore:group-order:g-test")).thenReturn(objectMapper.writeValueAsString(session));
        when(seatService.findSeats("sch-1", List.of("seat-1-1", "seat-1-2")))
                .thenReturn(List.of(seat("seat-1-1", 150), seat("seat-1-2", 100)));
        when(orderService.getOrderStatus("ord-1")).thenReturn("PAID");

        GroupOrderResponse response;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-102");
            response = service.get("g-test");
        }

        assertThat(response.status()).isEqualTo("PAID");
        assertThat(response.orderStatus()).isEqualTo("PAID");
        verify(orderService).repairGroupTicketHolders(
                eq("ord-1"),
                argThat(holders -> holders != null
                        && holders.size() == 2
                        && holders.stream().anyMatch(holder ->
                        "seat-1-2".equals(holder.seatId())
                                && "u-102".equals(holder.userId())
                                && "拼座好友".equals(holder.displayName())))
        );
        ArgumentCaptor<String> savedSession = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("encore:group-order:g-test"), savedSession.capture(), eq(Duration.ofMinutes(15)));
        GroupOrderSession saved = objectMapper.readValue(savedSession.getValue(), GroupOrderSession.class);
        assertThat(saved.getStatus()).isEqualTo("PAID");
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    private GroupOrderSession session(String status) {
        GroupOrderSession session = new GroupOrderSession();
        session.setInviteCode("g-test");
        session.setScheduleId("sch-1");
        session.setHostUserId("u-101");
        session.setHostDisplayName("普通用户");
        session.setStatus(status);
        session.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        session.setMaxSeats(6);
        return session;
    }

    private GroupOrderSession.Member member(String userId, String displayName, List<String> seatIds) {
        GroupOrderSession.Member member = new GroupOrderSession.Member();
        member.setUserId(userId);
        member.setDisplayName(displayName);
        member.setSeatIds(seatIds);
        member.setJoinedAt(LocalDateTime.now());
        return member;
    }

    private UserAccount user(String id, String displayName) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setUsername(id);
        user.setDisplayName(displayName);
        user.setRole("user");
        user.setStatus("ACTIVE");
        return user;
    }

    private ScheduleSeat seat(String seatId, int price) {
        ScheduleSeat seat = new ScheduleSeat();
        seat.setScheduleId("sch-1");
        seat.setSeatCode(seatId);
        seat.setStatus("AVAILABLE");
        seat.setPrice(BigDecimal.valueOf(price));
        return seat;
    }
}
