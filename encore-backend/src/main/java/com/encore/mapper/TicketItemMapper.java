package com.encore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.encore.entity.TicketItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface TicketItemMapper extends BaseMapper<TicketItem> {
    @Update("UPDATE ticket_item SET status = 'CHECKED_IN', updated_at = #{checkedInAt} " +
            "WHERE id = #{id} AND status = 'UNUSED'")
    int markCheckedInIfUnused(@Param("id") String id, @Param("checkedInAt") LocalDateTime checkedInAt);
}
