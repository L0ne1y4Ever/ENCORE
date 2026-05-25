package com.encore.controller;

import com.encore.common.ApiResponse;
import com.encore.dto.ScheduleResponse;
import com.encore.dto.ShowRecommendationResponse;
import com.encore.dto.ShowResponse;
import com.encore.service.ShowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shows")
public class ShowController {
    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @GetMapping
    public ApiResponse<List<ShowResponse>> listShows(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category
    ) {
        return ApiResponse.ok(showService.listShows(keyword, category));
    }

    @GetMapping("/recommendations/top8")
    public ApiResponse<List<ShowRecommendationResponse>> listTopRecommendations() {
        return ApiResponse.ok(showService.listTopRecommendations());
    }

    @GetMapping("/{id}")
    public ApiResponse<ShowResponse> getShowDetail(@PathVariable String id) {
        return ApiResponse.ok(showService.getShowDetail(id));
    }

    @GetMapping("/{id}/schedules")
    public ApiResponse<List<ScheduleResponse>> listSchedules(@PathVariable String id) {
        return ApiResponse.ok(showService.listSchedules(id));
    }

    @GetMapping("/schedules/{scheduleId}")
    public ApiResponse<ScheduleResponse> getScheduleDetail(@PathVariable String scheduleId) {
        return ApiResponse.ok(showService.getScheduleDetail(scheduleId));
    }
}
