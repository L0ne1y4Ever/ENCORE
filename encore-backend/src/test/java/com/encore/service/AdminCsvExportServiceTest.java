package com.encore.service;

import com.encore.dto.AdminDashboardResponse;
import com.encore.dto.AdminBoxOfficeResponse;
import com.encore.dto.AdminOrderResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminCsvExportServiceTest {

    @Test
    void exportsOrderCsvWithBusinessColumns() {
        AdminService adminService = mock(AdminService.class);
        when(adminService.listOrders()).thenReturn(List.of(new AdminOrderResponse(
                "ord-1",
                "u-1",
                "user",
                "sch-1",
                "茶馆",
                "Main Hall",
                LocalDateTime.of(2026, 6, 21, 19, 30),
                BigDecimal.valueOf(180),
                "PAID",
                "OFFLINE",
                "COUNTER",
                "u-admin",
                "admin",
                2,
                0,
                LocalDateTime.of(2026, 6, 9, 12, 0),
                LocalDateTime.of(2026, 6, 9, 12, 3),
                null
        )));

        String csv = new AdminCsvExportService(adminService).exportOrdersCsv();

        assertThat(csv).contains("订单号,用户,演出,剧场,金额,状态,来源,收款方式,收银员,票数,已核销,创建时间,支付时间");
        assertThat(csv).contains("ord-1,user,茶馆,Main Hall,180,PAID,OFFLINE,COUNTER,admin,2,0,2026-06-09 12:00:00,2026-06-09 12:03:00");
    }

    @Test
    void exportsDashboardCsvWithTrendAndSummary() {
        AdminService adminService = mock(AdminService.class);
        when(adminService.dashboard()).thenReturn(new AdminDashboardResponse(
                BigDecimal.valueOf(360),
                4,
                2,
                BigDecimal.valueOf(50),
                List.of(new AdminDashboardResponse.SalesTrendItem(
                        LocalDate.of(2026, 6, 9),
                        BigDecimal.valueOf(360),
                        4
                )),
                List.of(new AdminDashboardResponse.TopShowItem(
                        "s-1",
                        "THE PHANTOM OF THE OPERA",
                        4,
                        BigDecimal.valueOf(360)
                )),
                new AdminDashboardResponse.CheckInSummary(1, 3, 0),
                new AdminBoxOfficeResponse.Summary(
                        BigDecimal.valueOf(420),
                        BigDecimal.valueOf(60),
                        BigDecimal.valueOf(360),
                        BigDecimal.valueOf(100),
                        5,
                        1,
                        4,
                        1,
                        BigDecimal.valueOf(25)
                )
        ));

        String csv = new AdminCsvExportService(adminService).exportDashboardCsv();

        assertThat(csv).contains("分组,指标,数值");
        assertThat(csv).contains("数据看板,总营收,360");
        assertThat(csv).contains("财务摘要,净票房,360");
        assertThat(csv).contains("7日销量趋势,2026-06-09,票数 4 / 营收 360");
        assertThat(csv).contains("热门演出,THE PHANTOM OF THE OPERA,票数 4 / 营收 360");
        assertThat(csv).contains("核销概览,已核销,1");
    }

    @Test
    void exportsBoxOfficeCsvWithSummaryTrendShowsAndSchedules() {
        AdminService adminService = mock(AdminService.class);
        AdminBoxOfficeResponse boxOffice = new AdminBoxOfficeResponse(
                new AdminBoxOfficeResponse.Summary(
                        BigDecimal.valueOf(500),
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(400),
                        BigDecimal.valueOf(60),
                        5,
                        1,
                        4,
                        1,
                        BigDecimal.valueOf(25)
                ),
                new AdminBoxOfficeResponse.Summary(
                        BigDecimal.valueOf(300),
                        BigDecimal.valueOf(80),
                        BigDecimal.valueOf(220),
                        BigDecimal.valueOf(50),
                        3,
                        1,
                        2,
                        1,
                        BigDecimal.valueOf(50)
                ),
                List.of(new AdminBoxOfficeResponse.TrendItem(
                        LocalDate.of(2026, 6, 9),
                        BigDecimal.valueOf(300),
                        BigDecimal.valueOf(80),
                        BigDecimal.valueOf(220),
                        BigDecimal.valueOf(50),
                        3,
                        1,
                        2
                )),
                List.of(new AdminBoxOfficeResponse.CategoryRow(
                        "Drama",
                        BigDecimal.valueOf(300),
                        BigDecimal.valueOf(80),
                        BigDecimal.valueOf(220),
                        BigDecimal.valueOf(50),
                        3,
                        1,
                        2,
                        1,
                        BigDecimal.valueOf(50),
                        1,
                        1
                )),
                List.of(new AdminBoxOfficeResponse.ShowRow(
                        "s-1",
                        "茶馆",
                        "Drama",
                        BigDecimal.valueOf(300),
                        BigDecimal.valueOf(80),
                        BigDecimal.valueOf(220),
                        BigDecimal.valueOf(50),
                        3,
                        1,
                        2,
                        1,
                        BigDecimal.valueOf(50),
                        1
                )),
                List.of(new AdminBoxOfficeResponse.ScheduleRow(
                        "sch-1",
                        "s-1",
                        "茶馆",
                        "Main Hall",
                        LocalDateTime.of(2026, 6, 21, 19, 30),
                        BigDecimal.valueOf(300),
                        BigDecimal.valueOf(80),
                        BigDecimal.valueOf(220),
                        BigDecimal.valueOf(50),
                        3,
                        1,
                        2,
                        1,
                        BigDecimal.valueOf(50)
                ))
        );
        when(adminService.boxOffice("LAST_30_DAYS", null, null, null, null)).thenReturn(boxOffice);

        String csv = new AdminCsvExportService(adminService).exportBoxOfficeCsv("LAST_30_DAYS", null, null, null, null);

        assertThat(csv).contains("分组,层级,名称,类型,日期/场次,剧场,销售票房,退款金额,净票房,待支付金额,支付票数,退款票数,有效票数,已核销,核销率,票房演出数,票房场次数");
        assertThat(csv).contains("摘要,全局总票房,全平台累计,,,,500,100,400,60,5,1,4,1,25%,,");
        assertThat(csv).contains("摘要,当前筛选,当前筛选,,LAST_30_DAYS,,300,80,220,50,3,1,2,1,50%,,");
        assertThat(csv).contains("每日趋势,日期,2026-06-09,,2026-06-09,,300,80,220,50,3,1,2,,,,");
        assertThat(csv).contains("类型票房,类型,Drama,Drama,,,300,80,220,50,3,1,2,1,50%,1,1");
        assertThat(csv).contains("演出票房,演出,茶馆,Drama,,,300,80,220,50,3,1,2,1,50%,,1");
        assertThat(csv).contains("场次票房,场次,茶馆,,2026-06-21 19:30:00,Main Hall,300,80,220,50,3,1,2,1,50%,,");
        String[] lines = csv.split("\\r?\\n");
        int expectedColumns = lines[0].split(",", -1).length;
        assertThat(expectedColumns).isEqualTo(17);
        assertThat(lines).allSatisfy(line -> assertThat(line.split(",", -1)).hasSize(expectedColumns));
    }
}
