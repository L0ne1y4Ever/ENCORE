package com.encore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.encore.entity.ScheduleSeat;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ScheduleSeatMapper extends BaseMapper<ScheduleSeat> {
    @Update("""
            <script>
            UPDATE schedule_seat
            SET status = 'SOLD'
            WHERE schedule_id = #{scheduleId}
              AND status = 'AVAILABLE'
              AND seat_code IN
              <foreach collection="seatCodes" item="seatCode" open="(" separator="," close=")">
                #{seatCode}
              </foreach>
            </script>
            """)
    int sellSeats(@Param("scheduleId") String scheduleId, @Param("seatCodes") List<String> seatCodes);
}
