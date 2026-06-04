package com.encore.web;

import com.encore.exception.BusinessException;
import com.encore.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * 切片测试：standalone MockMvc + 探针控制器，验证 BusinessException 的业务码被映射为
 * 对应的 HTTP 状态(而非固定 400)，无需 DB/Redis/Sa-Token。
 */
class GlobalExceptionHandlerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void businessExceptionMapsBadRequest() throws Exception {
        mockMvc.perform(get("/probe/400"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void businessExceptionMapsUnauthorized() throws Exception {
        mockMvc.perform(get("/probe/401"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void businessExceptionMapsNotFound() throws Exception {
        mockMvc.perform(get("/probe/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void businessExceptionMapsConflict() throws Exception {
        mockMvc.perform(get("/probe/409"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @RestController
    static class ProbeController {
        @GetMapping("/probe/{code}")
        String raise(@PathVariable int code) {
            throw new BusinessException(code, "probe-" + code);
        }
    }
}
