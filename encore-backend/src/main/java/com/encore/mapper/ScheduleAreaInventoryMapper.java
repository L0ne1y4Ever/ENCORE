package com.encore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.encore.entity.ScheduleAreaInventory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ScheduleAreaInventoryMapper extends BaseMapper<ScheduleAreaInventory> {

    @Update("UPDATE schedule_area_inventory SET " +
            "available_count = available_count - #{quantity}, " +
            "locked_count = locked_count + #{quantity} " +
            "WHERE id = #{id} AND status = 'AVAILABLE' AND available_count >= #{quantity}")
    int lockInventory(@Param("id") String id, @Param("quantity") int quantity);

    @Update("UPDATE schedule_area_inventory SET " +
            "locked_count = locked_count - #{quantity}, " +
            "sold_count = sold_count + #{quantity} " +
            "WHERE id = #{id} AND locked_count >= #{quantity}")
    int sellInventory(@Param("id") String id, @Param("quantity") int quantity);

    @Update("UPDATE schedule_area_inventory SET " +
            "locked_count = locked_count - #{quantity}, " +
            "available_count = available_count + #{quantity} " +
            "WHERE id = #{id} AND locked_count >= #{quantity}")
    int unlockInventory(@Param("id") String id, @Param("quantity") int quantity);

    @Update("UPDATE schedule_area_inventory SET " +
            "sold_count = sold_count - #{quantity}, " +
            "available_count = available_count + #{quantity} " +
            "WHERE id = #{id} AND sold_count >= #{quantity}")
    int refundInventory(@Param("id") String id, @Param("quantity") int quantity);
}
