// RosterExcelTest.java — 명부 엑셀 3종(A/B/C) 생성·다운로드·계좌반출 감사 검증(F-08)
package com.jwgasul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jwgasul.domain.RosterType;
import com.jwgasul.dto.RosterCriteria;
import com.jwgasul.dto.WorkerForm;
import com.jwgasul.domain.WorkerType;
import com.jwgasul.service.AuditService;
import com.jwgasul.service.RosterService;
import com.jwgasul.service.WorkerService;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
class RosterExcelTest {

    private static final AtomicInteger SEQ = new AtomicInteger(7000);

    @Autowired private MockMvc mockMvc;
    @Autowired private WorkerService workerService;
    @Autowired private RosterService rosterService;
    @Autowired private AuditService auditService;

    private Long createRoster() {
        int n = SEQ.incrementAndGet();
        WorkerForm wf = new WorkerForm();
        wf.setWorkerType(WorkerType.KOREAN);
        wf.setNameKo("엑셀테스트" + n);
        wf.setBirthDate(LocalDate.of(1990, 1, 1));
        wf.setPhone("010" + String.format("%08d", n));
        Long wid = workerService.create(wf).getId();

        RosterCriteria c = new RosterCriteria();
        c.setTitle("엑셀현장" + n);   // 같은 제목·날짜 중복 저장은 차단되므로 테스트마다 다른 제목을 쓴다
        c.setTargetDate(LocalDate.now());
        return rosterService.save(RosterType.MANUAL, c, List.of(wid), "tester").getId();
    }

    // A/B/C 모두 유효한 xlsx(zip, PK 시그니처) 바이트를 생성한다
    @Test
    void allModesProduceXlsx() {
        Long rosterId = createRoster();
        for (String mode : new String[]{"A", "B", "C"}) {
            byte[] b = rosterService.exportExcel(rosterId, mode);
            assertTrue(b.length > 0, "엑셀 바이트가 비어있으면 안 됨: " + mode);
            assertEquals('P', b[0]);
            assertEquals('K', b[1]); // xlsx = zip
        }
    }

    // 계좌 포함(C) 다운로드는 감사 로그(반출)를 남긴다
    @Test
    void modeCWritesExportAudit() {
        Long rosterId = createRoster();
        int before = auditService.history("ROSTER", rosterId).size();
        rosterService.exportExcel(rosterId, "C");
        var after = auditService.history("ROSTER", rosterId);
        assertEquals(before + 1, after.size());
        assertTrue(after.stream().anyMatch(a -> "VIEW".equals(a.getAction())));
    }

    // 엔드포인트: 첨부(Content-Disposition) + 스프레드시트 content-type
    @Test
    void excelEndpointDownloads() throws Exception {
        Long rosterId = createRoster();
        mockMvc.perform(get("/roster/" + rosterId + "/excel").param("mode", "A"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
    }
}
