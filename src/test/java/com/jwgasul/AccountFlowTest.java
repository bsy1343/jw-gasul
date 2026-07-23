// AccountFlowTest.java — 계좌 등록·최대 3개·전체노출(마스킹 해제) 통합 검증(F-09)
package com.jwgasul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jwgasul.domain.WorkerType;
import com.jwgasul.dto.WorkerForm;
import com.jwgasul.service.WorkerService;
import java.time.LocalDate;
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
class AccountFlowTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkerService workerService;

    // 테스트용 근로자 1명 생성(이름·연락처를 유니크하게)
    private Long createWorker() {
        int n = SEQ.incrementAndGet();
        WorkerForm f = new WorkerForm();
        f.setWorkerType(WorkerType.KOREAN);
        f.setNameKo("계좌테스트" + n);
        f.setBirthDate(LocalDate.of(1990, 1, 1));
        f.setPhone("010" + String.format("%08d", n));
        return workerService.create(f).getId();
    }

    // 계좌 추가 후 전체노출 시 하이픈 제외 숫자를 반환한다
    @Test
    void addAccountThenRevealReturnsDigits() throws Exception {
        Long id = createWorker();
        mockMvc.perform(post("/workers/" + id + "/accounts").with(csrf())
                        .param("bankName", "국민")
                        .param("accountNumber", "123-456-7890")
                        .param("accountHolder", "홍길동"))
                .andExpect(status().is3xxRedirection());

        assertEquals(1, workerService.accounts(id).size());
        Long accountId = workerService.accounts(id).get(0).id();

        mockMvc.perform(get("/workers/" + id + "/accounts/" + accountId + "/reveal"))
                .andExpect(status().isOk())
                .andExpect(content().string("1234567890"));
    }

    // 계좌는 최대 3개까지만 등록된다
    @Test
    void maxThreeAccounts() throws Exception {
        Long id = createWorker();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/workers/" + id + "/accounts").with(csrf())
                    .param("bankName", "은행")
                    .param("accountNumber", "1000" + i + "000000")
                    .param("accountHolder", "예금주"));
        }
        assertEquals(3, workerService.accounts(id).size());

        // 4번째는 차단(서비스 400 → 컨트롤러가 메시지 flash 후 redirect). 여전히 3개.
        mockMvc.perform(post("/workers/" + id + "/accounts").with(csrf())
                        .param("bankName", "은행")
                        .param("accountNumber", "9999999999")
                        .param("accountHolder", "예금주"))
                .andExpect(status().is3xxRedirection());
        assertEquals(3, workerService.accounts(id).size());
    }

    // 첫 계좌는 자동 주계좌, 두 번째를 주계좌로 지정하면 이전 주계좌는 해제된다
    @Test
    void primaryAccountIsUnique() throws Exception {
        Long id = createWorker();
        mockMvc.perform(post("/workers/" + id + "/accounts").with(csrf())
                .param("bankName", "국민").param("accountNumber", "111111").param("accountHolder", "예금주"));
        mockMvc.perform(post("/workers/" + id + "/accounts").with(csrf())
                .param("bankName", "신한").param("accountNumber", "222222").param("accountHolder", "예금주"));

        var accts = workerService.accounts(id);
        assertEquals(true, accts.get(0).primary());   // 첫 계좌 = 주계좌
        assertEquals(false, accts.get(1).primary());

        Long secondId = accts.get(1).id();
        mockMvc.perform(post("/workers/" + id + "/accounts/" + secondId + "/primary").with(csrf()))
                .andExpect(status().is3xxRedirection());

        // 주계좌는 정확히 1개이고, 방금 지정한 계좌이며, 목록 최상단에 온다
        var after = workerService.accounts(id);
        assertEquals(1, after.stream().filter(a -> a.primary()).count());
        assertEquals(secondId, after.get(0).id());
        assertEquals(true, after.get(0).primary());
    }
}
