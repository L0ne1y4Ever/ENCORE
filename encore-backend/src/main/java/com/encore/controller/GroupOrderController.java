package com.encore.controller;

import com.encore.common.ApiResponse;
import com.encore.dto.CreateGroupOrderRequest;
import com.encore.dto.GroupOrderResponse;
import com.encore.dto.JoinGroupOrderRequest;
import com.encore.service.GroupOrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/group-orders")
public class GroupOrderController {
    private final GroupOrderService groupOrderService;

    public GroupOrderController(GroupOrderService groupOrderService) {
        this.groupOrderService = groupOrderService;
    }

    @PostMapping
    public ApiResponse<GroupOrderResponse> create(@Valid @RequestBody CreateGroupOrderRequest request) {
        return ApiResponse.ok(groupOrderService.create(request));
    }

    @GetMapping("/{inviteCode}")
    public ApiResponse<GroupOrderResponse> get(@PathVariable String inviteCode) {
        return ApiResponse.ok(groupOrderService.get(inviteCode));
    }

    @PostMapping("/{inviteCode}/join")
    public ApiResponse<GroupOrderResponse> join(
            @PathVariable String inviteCode,
            @Valid @RequestBody JoinGroupOrderRequest request
    ) {
        return ApiResponse.ok(groupOrderService.join(inviteCode, request));
    }

    @DeleteMapping("/{inviteCode}/members/me")
    public ApiResponse<GroupOrderResponse> leave(@PathVariable String inviteCode) {
        return ApiResponse.ok(groupOrderService.leave(inviteCode));
    }

    @DeleteMapping("/{inviteCode}")
    public ApiResponse<GroupOrderResponse> cancel(@PathVariable String inviteCode) {
        return ApiResponse.ok(groupOrderService.cancel(inviteCode));
    }

    @PostMapping("/{inviteCode}/checkout")
    public ApiResponse<String> checkout(@PathVariable String inviteCode) {
        return ApiResponse.ok(groupOrderService.checkout(inviteCode));
    }
}
