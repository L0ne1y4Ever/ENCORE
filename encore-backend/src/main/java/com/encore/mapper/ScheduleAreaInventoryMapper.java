package com.encore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.encore.entity.ScheduleAreaInventory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ScheduleAreaInventoryMapper extends BaseMapper<ScheduleAreaInventory> {

    // 管理端调整库存时按主键加行锁重读，避免与下单/支付/退票的原子计数更新发生丢失更新。
    @Select("SELECT * FROM schedule_area_inventory WHERE id = #{id} FOR UPDATE")
    ScheduleAreaInventory selectByIdForUpdate(@Param("id") String id);

    // 管理端整片调整：仅写 total/available/status，绝不回写 locked_count/sold_count(由下单链路原子维护)。
    @Update("UPDATE schedule_area_inventory SET " +
            "total_count = #{totalCount}, " +
            "available_count = #{availableCount}, " +
            "status = #{status} " +
            "WHERE id = #{id}")
    int adjustInventory(@Param("id") String id,
                        @Param("totalCount") int totalCount,
                        @Param("availableCount") int availableCount,
                        @Param("status") String status);

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
