// WorkerService.java — 근로자 CRUD 및 유형별 규칙(비자 기본값·교육 만료 자동계산·한국인 필드 정리)(F-02, 3.1)
package com.jwgasul.worker;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WorkerService {

    private final WorkerRepository workerRepository;
    private final int eduValidYearsForeign;
    private final int eduValidYearsKorean;

    public WorkerService(
            WorkerRepository workerRepository,
            @Value("${app.edu.valid-years.foreign:2}") int eduValidYearsForeign,
            @Value("${app.edu.valid-years.korean:2}") int eduValidYearsKorean) {
        this.workerRepository = workerRepository;
        this.eduValidYearsForeign = eduValidYearsForeign;
        this.eduValidYearsKorean = eduValidYearsKorean;
    }

    // 목록 조회(유형 탭 + 이름/연락처 검색). 동적 조건은 Specification으로 조합한다.
    @Transactional(readOnly = true)
    public Page<Worker> list(WorkerType type, String keyword, Pageable pageable) {
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Specification<Worker> spec = notDeleted();
        if (type != null) {
            spec = spec.and(hasType(type));
        }
        if (kw != null) {
            spec = spec.and(matchesKeyword(kw));
        }
        return workerRepository.findAll(spec, pageable);
    }

    // 삭제되지 않은 근로자
    private Specification<Worker> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    // 유형 일치
    private Specification<Worker> hasType(WorkerType type) {
        return (root, query, cb) -> cb.equal(root.get("workerType"), type);
    }

    // 이름(한국/외국) 또는 연락처 부분 일치(대소문자 무시)
    private Specification<Worker> matchesKeyword(String kw) {
        String like = "%" + kw.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("nameKo")), like),
                cb.like(cb.lower(root.get("nameForeign")), like),
                cb.like(root.get("phone"), "%" + kw + "%"));
    }

    // 삭제되지 않은 단건 조회
    @Transactional(readOnly = true)
    public Worker getActive(Long id) {
        return workerRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("근로자를 찾을 수 없습니다: " + id));
    }

    // 신규 등록. 동일인 중복(삭제 안 된 행) 검사 후 저장.
    @Transactional
    public Worker create(WorkerForm form) {
        String phone = normalizePhone(form.getPhone());
        if (workerRepository.existsByNameKoAndBirthDateAndPhoneAndDeletedAtIsNull(
                form.getNameKo(), form.getBirthDate(), phone)) {
            throw new DuplicateWorkerException("이미 등록된 근로자입니다(이름·생년월일·연락처 동일)");
        }
        Worker worker = new Worker(); // 같은 패키지 — protected 기본 생성자 접근 가능
        applyForm(worker, form, phone);
        return workerRepository.save(worker);
    }

    // 기존 근로자 수정.
    @Transactional
    public Worker update(Long id, WorkerForm form) {
        Worker worker = getActive(id);
        applyForm(worker, form, normalizePhone(form.getPhone()));
        return workerRepository.save(worker);
    }

    // soft delete(명부 이력 보존)
    @Transactional
    public void softDelete(Long id) {
        Worker worker = getActive(id);
        worker.markDeleted(Instant.now());
        workerRepository.save(worker);
    }

    // 수정 폼 채우기용 — 엔티티를 폼 DTO로 변환
    public WorkerForm toForm(Worker w) {
        WorkerForm f = new WorkerForm();
        f.setWorkerType(w.getWorkerType());
        f.setNameKo(w.getNameKo());
        f.setNameForeign(w.getNameForeign());
        f.setBirthDate(w.getBirthDate());
        f.setPhone(w.getPhone());
        f.setNationality(w.getNationality());
        f.setVisaGrade(w.getVisaGrade());
        // 화면에는 관례값(9999-12-31)을 비워서 보여준다
        f.setVisaExpireDate(w.isVisaTracked() ? w.getVisaExpireDate() : null);
        f.setEduCompleteDate(w.getEduCompleteDate());
        f.setFixed(w.isFixed());
        f.setMemo(w.getMemo());
        return f;
    }

    // 폼 값을 엔티티에 반영하며 유형별 규칙을 적용한다
    private void applyForm(Worker worker, WorkerForm form, String normalizedPhone) {
        WorkerType type = form.getWorkerType();
        worker.setWorkerType(type);
        worker.setNameKo(form.getNameKo());
        worker.setBirthDate(form.getBirthDate());
        worker.setPhone(normalizedPhone);
        worker.setFixed(form.isFixed());
        worker.setMemo(emptyToNull(form.getMemo()));

        if (type == WorkerType.KOREAN) {
            // 한국인: 외국이름·국적·비자 관련 필드 숨김 → 저장하지 않는다
            worker.setNameForeign(null);
            worker.setNationality(null);
            worker.setVisaGrade(null);
            worker.setVisaExpireDate(Worker.VISA_NO_EXPIRE);
        } else {
            worker.setNameForeign(emptyToNull(form.getNameForeign()));
            worker.setNationality(emptyToNull(form.getNationality()));
            worker.setVisaGrade(emptyToNull(form.getVisaGrade()));
            // 미입력 시 관례값(9999-12-31)
            worker.setVisaExpireDate(
                    form.getVisaExpireDate() != null ? form.getVisaExpireDate() : Worker.VISA_NO_EXPIRE);
        }

        // 교육 만료일 자동 계산: 이수일 + 유형별 유효기간(년). 이수일 없으면 만료일도 없음.
        worker.setEduCompleteDate(form.getEduCompleteDate());
        worker.setEduExpireDate(
                form.getEduCompleteDate() != null
                        ? form.getEduCompleteDate().plusYears(eduValidYears(type))
                        : null);
    }

    // 유형별 교육 유효기간(년)
    private int eduValidYears(WorkerType type) {
        return type == WorkerType.KOREAN ? eduValidYearsKorean : eduValidYearsForeign;
    }

    // 휴대폰 번호를 숫자만 남겨 정규화(3.1)
    private String normalizePhone(String phone) {
        return phone == null ? null : phone.replaceAll("\\D", "");
    }

    private String emptyToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
