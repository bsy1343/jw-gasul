// WorkerService.java — 근로자 CRUD(유형별 규칙) + 서류 사진 업로드/교체/삭제(F-02, 3.1·3.2).
// 외부 시스템이 없어 구현이 하나뿐이므로 인터페이스 없이 단일 클래스로 둔다(embedded PG가 실 백엔드).
// 서버는 jpg/png만 허용(HEIC 등 거부) — 리사이즈/변환은 클라이언트가 수행한다는 전제.
package com.jwgasul.service;

import com.jwgasul.common.exception.DuplicateWorkerException;
import com.jwgasul.domain.DocType;
import com.jwgasul.domain.Worker;
import com.jwgasul.domain.WorkerDocument;
import com.jwgasul.domain.WorkerType;
import com.jwgasul.dto.WorkerForm;
import com.jwgasul.repository.WorkerDocumentRepository;
import com.jwgasul.repository.WorkerRepository;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkerService {

    // 서버 수용 형식(클라이언트에서 JPEG로 변환 후 업로드하는 것을 전제)
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    private final WorkerRepository workerRepository;
    private final WorkerDocumentRepository documentRepository;
    private final StorageService storageService;
    private final int eduValidYearsForeign;
    private final int eduValidYearsKorean;

    public WorkerService(
            WorkerRepository workerRepository,
            WorkerDocumentRepository documentRepository,
            StorageService storageService,
            @Value("${app.edu.valid-years.foreign:2}") int eduValidYearsForeign,
            @Value("${app.edu.valid-years.korean:2}") int eduValidYearsKorean) {
        this.workerRepository = workerRepository;
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.eduValidYearsForeign = eduValidYearsForeign;
        this.eduValidYearsKorean = eduValidYearsKorean;
    }

    // ============================================================
    // 근로자 CRUD
    // ============================================================

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
        Worker worker = new Worker();
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

    // ============================================================
    // 서류 사진 (같은 근로자 메뉴의 하위 기능)
    // ============================================================

    // 근로자의 서류를 슬롯(DocType)별 맵으로 반환(상세 화면용)
    @Transactional(readOnly = true)
    public Map<DocType, WorkerDocument> documentsByType(Long workerId) {
        Map<DocType, WorkerDocument> map = new EnumMap<>(DocType.class);
        for (WorkerDocument doc : documentRepository.findByWorkerId(workerId)) {
            map.put(doc.getDocType(), doc);
        }
        return map;
    }

    // 슬롯에 파일을 업로드/교체한다. 기존 파일이 있으면 스토리지에서 지우고 메타데이터를 갱신한다.
    @Transactional
    public WorkerDocument uploadDocument(Long workerId, DocType docType, MultipartFile file) {
        validateImage(file);
        StorageService.StoredFile stored = storageService.store(workerId, docType.name(), file);

        WorkerDocument doc = documentRepository.findByWorkerIdAndDocType(workerId, docType).orElse(null);
        if (doc == null) {
            doc = new WorkerDocument(workerId, docType, stored.relativePath(), stored.originalName(), stored.size());
        } else {
            storageService.delete(doc.getFilePath()); // 이전 파일 제거
            doc.replace(stored.relativePath(), stored.originalName(), stored.size());
        }
        return documentRepository.save(doc);
    }

    // 슬롯의 서류를 삭제한다(파일 + 레코드)
    @Transactional
    public void deleteDocument(Long workerId, DocType docType) {
        documentRepository.findByWorkerIdAndDocType(workerId, docType).ifPresent(doc -> {
            storageService.delete(doc.getFilePath());
            documentRepository.delete(doc);
        });
    }

    // 업로드 파일이 허용 이미지(jpg/png)인지 검증. 아니면 400.
    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일이 비어 있습니다");
        }
        String contentType = file.getContentType();
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        boolean okType = contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase());
        boolean okExt = ext != null && ALLOWED_EXTENSIONS.contains(ext.toLowerCase());
        if (!okType || !okExt) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "지원하지 않는 형식입니다. jpg 또는 png로 변환 후 업로드하세요");
        }
    }
}
