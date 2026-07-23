// AuditFlowTest.java — 감사 로그(F-12): 근로자 생성/수정 시 감사 기록 · /audit 화면 렌더링 검증
package com.jwgasul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jwgasul.domain.AuditLog;
import com.jwgasul.domain.Worker;
import com.jwgasul.domain.WorkerType;
import com.jwgasul.dto.AuditFilter;
import com.jwgasul.dto.WorkerForm;
import com.jwgasul.service.AuditService;
import com.jwgasul.service.WorkerService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "tester")
class AuditFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkerService workerService;

    @Autowired
    private AuditService auditService;

    // 근로자 생성 시 WORKER CREATE 로그가 남는다
    @Test
    void createLogsAudit() {
        Worker w = workerService.create(sampleForm("감사생성테스트", "010-1000-0001"));

        List<AuditLog> history = auditService.workerHistory(w.getId());
        assertThat(history).isNotEmpty();
        assertThat(history).anyMatch(l -> "WORKER".equals(l.getEntityType()) && "CREATE".equals(l.getAction()));
    }

    // 근로자 수정 시 변경된 필드별로 UPDATE diff 로그가 남는다
    @Test
    void updateLogsFieldDiff() {
        Worker w = workerService.create(sampleForm("감사수정테스트", "010-1000-0002"));

        WorkerForm form = sampleForm("감사수정테스트", "010-1000-0002");
        form.setMemo("비고 추가됨");
        workerService.update(w.getId(), form);

        List<AuditLog> history = auditService.workerHistory(w.getId());
        assertThat(history).anyMatch(l ->
                "UPDATE".equals(l.getAction()) && "비고".equals(l.getChangedField())
                        && "비고 추가됨".equals(l.getNewValue()));
    }

    // 필터 검색(대상=WORKER)이 동작한다
    @Test
    void searchFiltersByEntityType() {
        workerService.create(sampleForm("감사검색테스트", "010-1000-0003"));

        AuditFilter filter = new AuditFilter();
        filter.setEntityType("WORKER");
        Page<AuditLog> page = auditService.search(filter, PageRequest.of(0, 10));

        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent()).allMatch(l -> "WORKER".equals(l.getEntityType()));
    }

    // /audit 화면이 렌더링되고 페이지 모델이 실린다
    @Test
    void auditScreenRenders() throws Exception {
        mockMvc.perform(get("/audit"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("logs", "entityTypes"));
    }

    // 외국인 근로자 등록 폼 DTO
    private WorkerForm sampleForm(String name, String phone) {
        WorkerForm f = new WorkerForm();
        f.setWorkerType(WorkerType.FOREIGN);
        f.setNameKo(name);
        f.setNameForeign("Test");
        f.setBirthDate(LocalDate.of(1990, 1, 1));
        f.setPhone(phone);
        f.setNationality("베트남");
        f.setVisaGrade("E-9");
        f.setEduCompleteDate(LocalDate.of(2026, 1, 1));
        return f;
    }
}
