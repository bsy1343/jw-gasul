// WorkerFlowTest.java — 근로자 목록/등록/상세 화면 렌더링 및 CRUD 통합 검증(F-02, F-03)
package com.jwgasul;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser // 인증된 사용자로 접근
class WorkerFlowTest {

    @Autowired
    private MockMvc mockMvc;

    // 목록 화면이 렌더링되고 샘플 데이터가 실린다
    @Test
    void listRenders() throws Exception {
        mockMvc.perform(get("/workers"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("workers", "page"));
    }

    // 등록 폼이 렌더링된다
    @Test
    void newFormRenders() throws Exception {
        mockMvc.perform(get("/workers/new"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("workerForm"));
    }

    // 외국인 근로자를 등록하면 상세로 리다이렉트된다
    @Test
    void createForeignWorkerRedirectsToDetail() throws Exception {
        mockMvc.perform(post("/workers").with(csrf())
                        .param("workerType", "FOREIGN")
                        .param("nameKo", "홍길동테스트")
                        .param("nameForeign", "Hong")
                        .param("birthDate", "1991-04-04")
                        .param("phone", "010-9999-8888")
                        .param("nationality", "베트남")
                        .param("visaGrade", "E-9")
                        .param("eduCompleteDate", "2026-01-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/workers/*"));
    }

    // 검증 실패(이름 누락) 시 폼으로 되돌아온다
    @Test
    void createWithMissingNameReturnsForm() throws Exception {
        mockMvc.perform(post("/workers").with(csrf())
                        .param("workerType", "KOREAN")
                        .param("birthDate", "1991-04-04")
                        .param("phone", "010-1234-5678"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("workerForm", "nameKo"));
    }
}
