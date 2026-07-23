// SampleDataInitializer.java — test 프로필 전용 stub(샘플) 근로자 데이터 생성.
// 화면을 비어있지 않게 하여 실 데이터 연동 전 UI/기능을 완성하기 위함. prod에서는 실행되지 않는다.
package com.jwgasul.config;

import com.jwgasul.worker.WorkerForm;
import com.jwgasul.worker.WorkerRepository;
import com.jwgasul.worker.WorkerService;
import com.jwgasul.worker.WorkerType;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
@Order(100) // 계정 seed(DataInitializer) 이후 실행
public class SampleDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataInitializer.class);

    private final WorkerRepository workerRepository;
    private final WorkerService workerService;

    public SampleDataInitializer(WorkerRepository workerRepository, WorkerService workerService) {
        this.workerRepository = workerRepository;
        this.workerService = workerService;
    }

    // 근로자가 하나도 없을 때만 샘플을 생성한다
    @Override
    public void run(String... args) {
        if (workerRepository.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now();

        // 외국인: 비자 만료 임박/정상/만료 등 다양하게
        workerService.create(foreign("응우옌반남", "Nguyen Van Nam", "1990-03-15", "010-1111-2222",
                "베트남", "E-9", today.plusDays(5), today.minusMonths(6), true));   // 비자 임박 + 고정
        workerService.create(foreign("쁠라완릿", "Prawanrit", "1988-07-01", "010-2222-3333",
                "태국", "E-9", today.plusMonths(8), today.minusYears(1).minusMonths(1), false)); // 교육 만료 임박
        workerService.create(foreign("첸웨이", "Chen Wei", "1995-11-20", "010-3333-4444",
                "중국", "H-2", today.minusDays(3), null, false));                  // 비자 만료
        workerService.create(foreign("바하두르", "Bahadur", "1992-02-10", "010-4444-5555",
                "네팔", "E-9", null, today.minusDays(10), false));                 // 비자 만료일 미상(-)

        // 한국인: 비자 없음(-), 일부 고정/교육
        workerService.create(korean("김철수", "1980-05-05", "010-5555-6666", today.minusMonths(2), true));
        workerService.create(korean("박영희", "1975-09-12", "010-6666-7777", null, false));

        log.info("샘플 근로자 {}명 생성", workerRepository.count());
    }

    // 외국인 샘플 폼 구성
    private WorkerForm foreign(String nameKo, String nameForeign, String birth, String phone,
                               String nationality, String visaGrade, LocalDate visaExpire, LocalDate eduComplete,
                               boolean fixed) {
        WorkerForm f = new WorkerForm();
        f.setWorkerType(WorkerType.FOREIGN);
        f.setNameKo(nameKo);
        f.setNameForeign(nameForeign);
        f.setBirthDate(LocalDate.parse(birth));
        f.setPhone(phone);
        f.setNationality(nationality);
        f.setVisaGrade(visaGrade);
        f.setVisaExpireDate(visaExpire);
        f.setEduCompleteDate(eduComplete);
        f.setFixed(fixed);
        return f;
    }

    // 한국인 샘플 폼 구성
    private WorkerForm korean(String nameKo, String birth, String phone, LocalDate eduComplete, boolean fixed) {
        WorkerForm f = new WorkerForm();
        f.setWorkerType(WorkerType.KOREAN);
        f.setNameKo(nameKo);
        f.setBirthDate(LocalDate.parse(birth));
        f.setPhone(phone);
        f.setEduCompleteDate(eduComplete);
        f.setFixed(fixed);
        return f;
    }
}
