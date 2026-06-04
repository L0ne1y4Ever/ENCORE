package com.encore.controller;

import com.encore.common.ApiResponse;
import com.encore.dto.CreateOrderRequest;
import com.encore.dto.OrderResponse;
import com.encore.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<String> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.ok(orderService.createOrder(request));
    }

    @GetMapping("/my")
    public ApiResponse<List<OrderResponse>> listMyOrders() {
        return ApiResponse.ok(orderService.listMyOrders());
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOrderDetail(@PathVariable String id) {
        return ApiResponse.ok(orderService.getOrderDetail(id));
    }

    @PostMapping("/{id}/pay")
    public ApiResponse<Boolean> simulatePayment(@PathVariable String id) {
        return ApiResponse.ok(orderService.simulatePayment(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancelOrder(@PathVariable String id) {
        return ApiResponse.ok(orderService.cancelOrder(id));
    }

    @PostMapping("/{id}/refund")
    public ApiResponse<OrderResponse> refundOrder(@PathVariable String id) {
        return ApiResponse.ok(orderService.refundOrder(id));
    }
}
