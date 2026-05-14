package com.encore.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.encore.common.ErrorCode;
import com.encore.dto.ScheduleResponse;
import com.encore.dto.ShowResponse;
import com.encore.entity.ShowEntity;
import com.encore.entity.ShowSchedule;
import com.encore.exception.BusinessException;
import com.encore.mapper.ShowMapper;
import com.encore.mapper.ShowScheduleMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ShowService {
    private final ShowMapper showMapper;
    private final ShowScheduleMapper showScheduleMapper;

    public ShowService(ShowMapper showMapper, ShowScheduleMapper showScheduleMapper) {
        this.showMapper = showMapper;
        this.showScheduleMapper = showScheduleMapper;
    }

    public List<ShowResponse> listShows(String keyword, String category) {
        LambdaQueryWrapper<ShowEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query
                    .like(ShowEntity::getTitle, keyword)
                    .or()
                    .like(ShowEntity::getSubtitle, keyword)
                    .or()
                    .like(ShowEntity::getDescription, keyword));
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(ShowEntity::getCategory, category);
        }
        wrapper.eq(ShowEntity::getStatus, "PUBLISHED")
                .orderByAsc(ShowEntity::getSortOrder)
                .orderByDesc(ShowEntity::getCreatedAt);

        return showMapper.selectList(wrapper).stream()
                .map(this::toShowResponse)
                .toList();
    }

    public ShowResponse getShowDetail(String id) {
        ShowEntity show = showMapper.selectById(id);
        if (show == null || !"PUBLISHED".equals(show.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "演出不存在或未发布");
        }
        return toShowResponse(show);
    }

    public List<ScheduleResponse> listSchedules(String showId) {
        getShowDetail(showId);
        return showScheduleMapper.selectList(new LambdaQueryWrapper<ShowSchedule>()
                        .eq(ShowSchedule::getShowId, showId)
                        .orderByAsc(ShowSchedule::getStartTime))
                .stream()
                .map(this::toScheduleResponse)
                .toList();
    }

    private ShowResponse toShowResponse(ShowEntity show) {
        return new ShowResponse(
                show.getId(),
                show.getTitle(),
                show.getSubtitle(),
                show.getCoverUrl(),
                show.getDescription(),
                show.getDuration(),
                show.getCategory(),
                show.getTags()
        );
    }

    private ScheduleResponse toScheduleResponse(ShowSchedule schedule) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getShowId(),
                schedule.getTheaterName(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getStatus(),
                schedule.getPriceRange()
        );
    }
}
