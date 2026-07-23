// WorkerFilterTest.java — 목록 세부 필터(F-03) 실행/정확성 검증. 특히 서브쿼리(서류미비·계좌미등록)가 PG에서 정상 동작하는지.
package com.jwgasul;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jwgasul.dto.AccountForm;
import com.jwgasul.dto.WorkerFilter;
import com.jwgasul.dto.WorkerForm;
import com.jwgasul.domain.WorkerType;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import com.jwgasul.service.WorkerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class WorkerFilterTest {

    private static final AtomicInteger SEQ = new AtomicInteger(5000);

    @Autowired
    private WorkerService workerService;

    private Long createWorker() {
        int n = SEQ.incrementAndGet();
        WorkerForm f = new WorkerForm();
        f.setWorkerType(WorkerType.KOREAN);
        f.setNameKo("필터테스트" + n);
        f.setBirthDate(LocalDate.of(1990, 1, 1));
        f.setPhone("010" + String.format("%08d", n));
        return workerService.create(f).getId();
    }

    // 모든 필터 조합이 SQL 오류 없이 실행된다(서브쿼리 포함)
    @Test
    void allFiltersExecuteWithoutError() {
        var pageable = PageRequest.of(0, 50);
        String[] visas = {null, "EXPIRED", "IMMINENT"};
        String[] edus = {null, "EXPIRED", "IMMINENT", "UNREGISTERED"};
        for (String v : visas) {
            for (String e : edus) {
                WorkerFilter f = new WorkerFilter();
                f.setVisa(v);
                f.setEdu(e);
                f.setFixed(true);
                f.setMissingDoc(true);
                f.setNoAccount(true);
                workerService.list(f, pageable); // 예외 없이 완료되면 통과
            }
        }
    }

    // 계좌 미등록 필터: 계좌 없으면 포함, 추가하면 제외
    @Test
    void noAccountFilterReflectsAccounts() {
        Long id = createWorker();
        var pageable = PageRequest.of(0, 500);
        WorkerFilter f = new WorkerFilter();
        f.setNoAccount(true);

        assertTrue(contains(workerService.list(f, pageable).getContent(), id),
                "계좌 없는 근로자는 '계좌 미등록' 필터에 포함되어야 한다");

        AccountForm acc = new AccountForm();
        acc.setBankName("국민");
        acc.setAccountNumber("123456");
        acc.setAccountHolder("예금주");
        workerService.addAccount(id, acc);

        assertFalse(contains(workerService.list(f, pageable).getContent(), id),
                "계좌 추가 후에는 '계좌 미등록' 필터에서 빠져야 한다");
    }

    private boolean contains(java.util.List<com.jwgasul.domain.Worker> workers, Long id) {
        return workers.stream().anyMatch(w -> w.getId().equals(id));
    }
}
