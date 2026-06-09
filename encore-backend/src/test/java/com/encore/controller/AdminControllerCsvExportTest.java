package com.encore.controller;

import com.encore.service.AdminCsvExportService;
import com.encore.service.AdminService;
import com.encore.service.AuditLogService;
import com.encore.service.StaffAccountService;
import com.encore.service.VenueManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AdminControllerCsvExportTest {

    private final AdminCsvExportService csvExportService = mock(AdminCsvExportService.class);
    private final MockMvc mockMvc = standaloneSetup(new AdminController(
            mock(AdminService.class),
            csvExportService,
            mock(VenueManagementService.class),
            mock(StaffAccountService.class),
            mock(AuditLogService.class)
    )).build();

    @Test
    void exportsOrdersAsCsvAttachment() throws Exception {
        when(csvExportService.exportOrdersCsv()).thenReturn("订单号,用户\r\nord-1,user");

        byte[] body = mockMvc.perform(get("/api/admin/orders/export"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("encore-orders")))
                .andExpect(content().contentTypeCompatibleWith(new MediaType("text", "csv", StandardCharsets.UTF_8)))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(new String(body, StandardCharsets.UTF_8)).startsWith("\uFEFF订单号,用户");
    }

    @Test
    void exportsDashboardAsCsvAttachment() throws Exception {
        when(csvExportService.exportDashboardCsv()).thenReturn("分组,指标,数值\r\n数据看板,售出票数,10");

        byte[] body = mockMvc.perform(get("/api/admin/dashboard/export"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("encore-dashboard")))
                .andExpect(content().contentTypeCompatibleWith(new MediaType("text", "csv", StandardCharsets.UTF_8)))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(new String(body, StandardCharsets.UTF_8)).startsWith("\uFEFF分组,指标,数值");
    }

    @Test
    void exportsBoxOfficeAsCsvAttachment() throws Exception {
        when(csvExportService.exportBoxOfficeCsv("LAST_30_DAYS", null, null, null, null))
                .thenReturn("分组,层级,名称,类型,日期/场次,剧场,销售票房,退款金额,净票房,待支付金额,支付票数,退款票数,有效票数,已核销,核销率,票房演出数,票房场次数\r\n摘要,当前筛选,当前筛选,,,,300,80,220,50,3,1,2,1,50%,,");

        byte[] body = mockMvc.perform(get("/api/admin/box-office/export")
                        .param("range", "LAST_30_DAYS"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("encore-box-office")))
                .andExpect(content().contentTypeCompatibleWith(new MediaType("text", "csv", StandardCharsets.UTF_8)))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(new String(body, StandardCharsets.UTF_8)).startsWith("\uFEFF分组,层级,名称,类型,日期/场次");
    }
}
