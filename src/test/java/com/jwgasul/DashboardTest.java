// DashboardTest.java — 대시보드 렌더링/집계 및 교육 링크 노출 검증(F-11·F-10)
package com.jwgasul;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class DashboardTest {

    @Autowired
    private MockMvc mockMvc;

    // 대시보드가 집계 데이터와 함께 렌더링된다
    @Test
    void dashboardRenders() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("dashboard"));
    }

    // 근로자 목록에 교육 듣기 링크(eduEntCode)가 노출된다(GlobalModelAdvice)
    @Test
    void workerListHasEduEntCode() throws Exception {
        mockMvc.perform(get("/workers"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("eduEntCode"));
    }
}
