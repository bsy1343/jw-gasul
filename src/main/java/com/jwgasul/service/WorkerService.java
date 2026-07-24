// WorkerService.java — 근로자 CRUD(유형별 규칙) + 서류 사진 업로드/교체/삭제(F-02, 3.1·3.2).
// 외부 시스템이 없어 구현이 하나뿐이므로 인터페이스 없이 단일 클래스로 둔다(embedded PG가 실 백엔드).
// 서버는 jpg/png만 허용(HEIC 등 거부) — 리사이즈/변환은 클라이언트가 수행한다는 전제.
package com.jwgasul.service;

import com.jwgasul.common.exception.DuplicateWorkerException;
import com.jwgasul.domain.DocType;
import com.jwgasul.domain.ExpiryStatus;
import com.jwgasul.domain.Worker;
import com.jwgasul.domain.WorkerAccount;
import com.jwgasul.domain.WorkerDocument;
import com.jwgasul.domain.WorkerType;
import com.jwgasul.dto.AccountForm;
import com.jwgasul.dto.AccountView;
import com.jwgasul.dto.ExpirySummary;
import com.jwgasul.dto.ImportResult;
import com.jwgasul.dto.WorkerFilter;
import com.jwgasul.dto.WorkerForm;
import java.time.LocalDate;
import com.jwgasul.repository.WorkerAccountRepository;
import jakarta.persistence.criteria.Predicate;
import com.jwgasul.repository.WorkerDocumentRepository;
import com.jwgasul.repository.WorkerRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    // 인원당 계좌 최대 개수(3.3)
    private static final int MAX_ACCOUNTS = 3;

    private final WorkerRepository workerRepository;
    private final WorkerDocumentRepository documentRepository;
    private final WorkerAccountRepository accountRepository;
    private final StorageService storageService;
    private final AuditService auditService;
    private final int eduValidYearsForeign;
    private final int eduValidYearsKorean;

    public WorkerService(
            WorkerRepository workerRepository,
            WorkerDocumentRepository documentRepository,
            WorkerAccountRepository accountRepository,
            StorageService storageService,
            AuditService auditService,
            @Value("${app.edu.valid-years.foreign:2}") int eduValidYearsForeign,
            @Value("${app.edu.valid-years.korean:2}") int eduValidYearsKorean) {
        this.workerRepository = workerRepository;
        this.documentRepository = documentRepository;
        this.accountRepository = accountRepository;
        this.storageService = storageService;
        this.auditService = auditService;
        this.eduValidYearsForeign = eduValidYearsForeign;
        this.eduValidYearsKorean = eduValidYearsKorean;
    }

    // ============================================================
    // 근로자 CRUD
    // ============================================================

    // 목록 조회(유형 탭 + 검색 + 세부 필터). 동적 조건은 Specification으로 조합한다(F-03).
    @Transactional(readOnly = true)
    public Page<Worker> list(WorkerFilter f, Pageable pageable) {
        LocalDate today = LocalDate.now();
        LocalDate limit = today.plusDays(ExpiryStatus.IMMINENT_DAYS);

        Specification<Worker> spec = notDeleted();
        if (f.getType() != null) {
            spec = spec.and(hasType(f.getType()));
        }
        if (StringUtils.hasText(f.getQ())) {
            spec = spec.and(matchesKeyword(f.getQ().trim()));
        }
        if ("EXPIRED".equals(f.getVisa())) {
            spec = spec.and((r, q, cb) -> cb.lessThan(r.get("visaExpireDate"), today));
        } else if ("IMMINENT".equals(f.getVisa())) {
            spec = spec.and((r, q, cb) -> cb.between(r.get("visaExpireDate"), today, limit));
        }
        if ("EXPIRED".equals(f.getEdu())) {
            spec = spec.and((r, q, cb) -> cb.lessThan(r.get("eduExpireDate"), today));
        } else if ("IMMINENT".equals(f.getEdu())) {
            spec = spec.and((r, q, cb) -> cb.between(r.get("eduExpireDate"), today, limit));
        } else if ("UNREGISTERED".equals(f.getEdu())) {
            spec = spec.and((r, q, cb) -> cb.isNull(r.get("eduExpireDate")));
        }
        if (Boolean.TRUE.equals(f.getFixed())) {
            spec = spec.and((r, q, cb) -> cb.isTrue(r.get("fixed")));
        }
        if (Boolean.TRUE.equals(f.getMissingDoc())) {
            spec = spec.and(docCountLessThan(3));
        }
        if (Boolean.TRUE.equals(f.getNoAccount())) {
            spec = spec.and(accountCountIsZero());
        }

        // 정렬 고정: 고정 인원 우선(내림차순) → 비자만료일 오름차순(9999-12-31은 후순위로 밀림)
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("fixed"), Sort.Order.asc("visaExpireDate")));
        return workerRepository.findAll(spec, sorted);
    }

    // 서류가 n개 미만(서류 미비) — 상관 서브쿼리
    private Specification<Worker> docCountLessThan(long n) {
        return (root, query, cb) -> {
            var sub = query.subquery(Long.class);
            var d = sub.from(WorkerDocument.class);
            sub.select(cb.count(d)).where(cb.equal(d.get("workerId"), root.get("id")));
            return cb.lessThan(sub, n);
        };
    }

    // 계좌가 0개(계좌 미등록) — 상관 서브쿼리
    private Specification<Worker> accountCountIsZero() {
        return (root, query, cb) -> {
            var sub = query.subquery(Long.class);
            var a = sub.from(WorkerAccount.class);
            sub.select(cb.count(a)).where(cb.equal(a.get("workerId"), root.get("id")));
            return cb.equal(sub, 0L);
        };
    }

    // 목록 상단 요약 배지용 만료/임박 집계(F-04)
    @Transactional(readOnly = true)
    public ExpirySummary expirySummary() {
        LocalDate today = LocalDate.now();
        LocalDate limit = today.plusDays(ExpiryStatus.IMMINENT_DAYS);
        return new ExpirySummary(
                workerRepository.countVisaExpired(today),
                workerRepository.countVisaImminent(today, limit),
                workerRepository.countEduExpired(today),
                workerRepository.countEduImminent(today, limit));
    }

    // 삭제되지 않은 근로자
    private Specification<Worker> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    // 유형 일치
    private Specification<Worker> hasType(WorkerType type) {
        return (root, query, cb) -> cb.equal(root.get("workerType"), type);
    }

    // 이름(한국/외국) 또는 연락처 부분 일치(대소문자 무시).
    // 연락처는 숫자만 저장하지만 화면에는 010-1234-5678로 보이므로, 검색어의 하이픈을 제거해 비교한다.
    private Specification<Worker> matchesKeyword(String kw) {
        String like = "%" + kw.toLowerCase() + "%";
        String digits = kw.replaceAll("\\D", "");
        return (root, query, cb) -> {
            Predicate byName = cb.or(
                    cb.like(cb.lower(root.get("nameKo")), like),
                    cb.like(cb.lower(root.get("nameForeign")), like));
            return digits.isEmpty()
                    ? byName
                    : cb.or(byName, cb.like(root.get("phone"), "%" + digits + "%"));
        };
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
        Worker saved = workerRepository.save(worker);
        auditService.log("WORKER", saved.getId(), "CREATE");
        return saved;
    }

    // 기존 근로자 수정. 변경 필드별 감사 diff 기록(F-12).
    @Transactional
    public Worker update(Long id, WorkerForm form) {
        Worker worker = getActive(id);
        Map<String, String> before = snapshot(worker);
        applyForm(worker, form, normalizePhone(form.getPhone()));
        Worker saved = workerRepository.save(worker);
        logWorkerDiff(id, before, snapshot(saved));
        return saved;
    }

    // soft delete(명부 이력 보존)
    @Transactional
    public void softDelete(Long id) {
        Worker worker = getActive(id);
        worker.markDeleted(Instant.now());
        workerRepository.save(worker);
        auditService.log("WORKER", id, "DELETE");
    }

    // 감사 diff용 필드 스냅샷(한글 라벨)
    private Map<String, String> snapshot(Worker w) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("유형", String.valueOf(w.getWorkerType()));
        m.put("한국이름", nz(w.getNameKo()));
        m.put("외국이름", nz(w.getNameForeign()));
        m.put("생년월일", String.valueOf(w.getBirthDate()));
        m.put("연락처", nz(w.getPhone()));
        m.put("국적", nz(w.getNationality()));
        m.put("비자등급", nz(w.getVisaGrade()));
        m.put("비자만료", String.valueOf(w.getVisaExpireDate()));
        m.put("교육이수", String.valueOf(w.getEduCompleteDate()));
        m.put("고정", String.valueOf(w.isFixed()));
        m.put("비고", nz(w.getMemo()));
        return m;
    }

    // 변경된 필드마다 감사 로그 1건
    private void logWorkerDiff(Long id, Map<String, String> before, Map<String, String> after) {
        for (String k : before.keySet()) {
            String o = before.get(k);
            String n = after.get(k);
            if (!Objects.equals(o, n)) {
                auditService.log("WORKER", id, "UPDATE", k, o, n);
            }
        }
    }

    private String nz(String s) {
        return s == null ? "" : s;
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
        boolean isNew = (doc == null);
        if (isNew) {
            doc = new WorkerDocument(workerId, docType, stored.relativePath(), stored.originalName(), stored.size());
        } else {
            storageService.delete(doc.getFilePath()); // 이전 파일 제거
            doc.replace(stored.relativePath(), stored.originalName(), stored.size());
        }
        WorkerDocument saved = documentRepository.save(doc);
        // 감사: 서류는 근로자 단위 이력에 함께 보이도록 entityId=workerId, 슬롯명을 변경필드로
        auditService.log("DOCUMENT", workerId, isNew ? "CREATE" : "UPDATE", docType.getLabel(), null, null);
        return saved;
    }

    // 슬롯의 서류를 삭제한다(파일 + 레코드)
    @Transactional
    public void deleteDocument(Long workerId, DocType docType) {
        documentRepository.findByWorkerIdAndDocType(workerId, docType).ifPresent(doc -> {
            storageService.delete(doc.getFilePath());
            documentRepository.delete(doc);
            auditService.log("DOCUMENT", workerId, "DELETE", docType.getLabel(), null, null);
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

    // ============================================================
    // 계좌 (F-09, 3.3) — 표시는 마스킹, 전체 노출/변경은 감사 기록
    // ============================================================

    // 근로자의 계좌 목록(마스킹). 표시 순서대로.
    @Transactional(readOnly = true)
    public List<AccountView> accounts(Long workerId) {
        List<AccountView> views = new ArrayList<>();
        for (WorkerAccount a : accountRepository.findByWorkerIdOrderByPrimaryDescSortOrderAsc(workerId)) {
            views.add(toView(a));
        }
        return views;
    }

    // 계좌 추가(최대 3개). 첫 계좌는 자동 주계좌.
    @Transactional
    public WorkerAccount addAccount(Long workerId, AccountForm form) {
        getActive(workerId); // 존재 검증
        long count = accountRepository.countByWorkerId(workerId);
        if (count >= MAX_ACCOUNTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "계좌는 최대 " + MAX_ACCOUNTS + "개까지 등록할 수 있습니다");
        }
        boolean primary = count == 0; // 첫 계좌 = 주계좌
        WorkerAccount account = new WorkerAccount(
                workerId, form.getBankName().trim(), digitsOnly(form.getAccountNumber()),
                form.getAccountHolder().trim(), primary, (short) (count + 1), emptyToNull(form.getMemo()));
        WorkerAccount saved = accountRepository.save(account);
        auditService.log("ACCOUNT", saved.getId(), "CREATE");
        return saved;
    }

    // 계좌 수정
    @Transactional
    public WorkerAccount updateAccount(Long workerId, Long accountId, AccountForm form) {
        WorkerAccount account = getAccount(workerId, accountId);
        account.update(form.getBankName().trim(), digitsOnly(form.getAccountNumber()),
                form.getAccountHolder().trim(), emptyToNull(form.getMemo()));
        WorkerAccount saved = accountRepository.save(account);
        auditService.log("ACCOUNT", saved.getId(), "UPDATE");
        return saved;
    }

    // 계좌 삭제
    @Transactional
    public void deleteAccount(Long workerId, Long accountId) {
        WorkerAccount account = getAccount(workerId, accountId);
        accountRepository.delete(account);
        auditService.log("ACCOUNT", accountId, "DELETE");
    }

    // 주계좌 지정(인원당 1개). 부분 유니크(WHERE is_primary=TRUE) 위반을 막기 위해
    // 기존 주계좌 해제를 먼저 DB에 flush한 뒤 새 주계좌를 true로 만든다(동시에 두 개 true 금지).
    @Transactional
    public void setPrimaryAccount(Long workerId, Long accountId) {
        WorkerAccount target = getAccount(workerId, accountId);
        accountRepository.findByWorkerIdAndPrimaryTrue(workerId).ifPresent(prev -> {
            if (!prev.getId().equals(accountId)) {
                prev.setPrimary(false);
                accountRepository.saveAndFlush(prev); // 먼저 false 반영
            }
        });
        target.setPrimary(true);
        accountRepository.save(target);
        auditService.log("ACCOUNT", accountId, "UPDATE", "is_primary", null, "true");
    }

    // 계좌번호 전체 노출(하이픈 제외 숫자). 감사 로그(VIEW) 기록(F-09).
    @Transactional
    public String revealAccount(Long workerId, Long accountId) {
        WorkerAccount account = getAccount(workerId, accountId);
        auditService.log("ACCOUNT", accountId, "VIEW");
        return account.getAccountNumber();
    }

    // 근로자-계좌 소유 검증 후 계좌 반환
    private WorkerAccount getAccount(Long workerId, Long accountId) {
        WorkerAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("계좌를 찾을 수 없습니다: " + accountId));
        if (!account.getWorkerId().equals(workerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "근로자와 계좌가 일치하지 않습니다");
        }
        return account;
    }

    // 엔티티 → 마스킹 표시 DTO
    private AccountView toView(WorkerAccount a) {
        return new AccountView(a.getId(), a.getBankName(), AccountView.mask(a.getAccountNumber()),
                a.getAccountHolder(), a.isPrimary(), a.getMemo());
    }

    // 하이픈·공백 제거 숫자만
    private String digitsOnly(String s) {
        return s == null ? null : s.replaceAll("\\D", "");
    }

    // ============================================================
    // 엑셀 일괄등록 (명단 + 계좌). 사진은 미포함(스프레드시트 행 매핑이 불안정 → 별도 방식 예정).
    //   행 단위 사전검증으로 유효행만 DB에 기록 → 잘못된 행은 DB를 건드리지 않아 PG 트랜잭션이 오염되지 않는다.
    // ============================================================

    private static final String[] IMPORT_HEADERS = {
            "유형(외국인/한국인)", "한국이름*", "외국이름", "생년월일*(YYYY-MM-DD)", "연락처*",
            "국적", "비자등급", "비자만료일(YYYY-MM-DD)", "교육이수일(YYYY-MM-DD)", "고정(Y/N)", "비고",
            "은행1", "예금주1", "계좌번호1", "은행2", "예금주2", "계좌번호2", "은행3", "예금주3", "계좌번호3"
    };

    // 빈 템플릿(헤더 + 예시 1행) 생성 — 다운로드 제공
    public byte[] buildImportTemplate() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("근로자");
            Row header = sheet.createRow(0);
            for (int i = 0; i < IMPORT_HEADERS.length; i++) {
                header.createCell(i).setCellValue(IMPORT_HEADERS[i]);
                sheet.setColumnWidth(i, 18 * 256);
            }
            String[] sample = {
                    "외국인", "홍길동", "Hong Gil Dong", "1990-01-15", "010-1234-5678",
                    "베트남", "E-9", "2027-05-01", "2026-01-10", "N", "(예시행 — 삭제 후 입력하세요)",
                    "국민", "홍길동", "123-45-678901", "", "", "", "", "", ""
            };
            Row ex = sheet.createRow(1);
            for (int i = 0; i < sample.length; i++) {
                ex.createCell(i).setCellValue(sample[i]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "템플릿 생성에 실패했습니다");
        }
    }

    // 엑셀 파싱 → 근로자/계좌 일괄 등록. 필수값·유형·중복은 사전검증하여 유효행만 저장한다.
    @Transactional
    public ImportResult importExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일이 비어 있습니다");
        }
        int total = 0;
        int created = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            int last = sheet.getLastRowNum();
            for (int r = 1; r <= last; r++) { // 0행 = 헤더
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                int rowNo = r + 1; // 사람이 보는 엑셀 행 번호(1-based)
                total++;

                // 1) 셀 파싱(DB 접근 전) — 형식 오류는 이 단계에서 걸러 트랜잭션을 건드리지 않는다
                String typeStr, nameKo, phone, nameForeign, nationality, visaGrade, memo, fixedStr;
                LocalDate birth, visaExp, eduDate;
                try {
                    typeStr = cellStr(row, 0);
                    nameKo = cellStr(row, 1);
                    nameForeign = cellStr(row, 2);
                    birth = cellDate(row, 3);
                    phone = cellStr(row, 4);
                    nationality = cellStr(row, 5);
                    visaGrade = cellStr(row, 6);
                    visaExp = cellDate(row, 7);
                    eduDate = cellDate(row, 8);
                    fixedStr = cellStr(row, 9);
                    memo = cellStr(row, 10);
                } catch (RuntimeException e) {
                    errors.add(rowNo + "행: 셀 형식 오류 — " + msg(e) + " (건너뜀)");
                    continue;
                }

                // 2) 필수값/유형/중복 사전검증 — 모두 DB 미변경 continue
                if (!StringUtils.hasText(nameKo) || birth == null || !StringUtils.hasText(phone)) {
                    errors.add(rowNo + "행: 한국이름·생년월일·연락처는 필수입니다 (건너뜀)");
                    continue;
                }
                WorkerType type = parseType(typeStr);
                if (type == null) {
                    errors.add(rowNo + "행: 유형은 '외국인' 또는 '한국인'만 가능합니다 (건너뜀)");
                    continue;
                }
                String normPhone = digitsOnly(phone);
                if (workerRepository.existsByNameKoAndBirthDateAndPhoneAndDeletedAtIsNull(nameKo, birth, normPhone)) {
                    skipped++;
                    errors.add(rowNo + "행: 이미 등록된 근로자(" + nameKo + ") (건너뜀)");
                    continue;
                }

                // 3) 저장(유효행만) — 여기부터는 예상 예외 없음. 이례적 오류는 배치 전체 롤백.
                WorkerForm form = new WorkerForm();
                form.setWorkerType(type);
                form.setNameKo(nameKo);
                form.setNameForeign(nameForeign);
                form.setBirthDate(birth);
                form.setPhone(phone);
                form.setNationality(nationality);
                form.setVisaGrade(visaGrade);
                form.setVisaExpireDate(visaExp);
                form.setEduCompleteDate(eduDate);
                form.setFixed(parseYn(fixedStr));
                form.setMemo(memo);
                Worker saved = create(form);
                created++;

                // 4) 계좌 최대 3세트(은행+계좌번호가 모두 있는 것만)
                for (int g = 0; g < 3; g++) {
                    int base = 11 + g * 3;
                    String bank = cellStr(row, base);
                    String holder = cellStr(row, base + 1);
                    String number = cellStr(row, base + 2);
                    if (!StringUtils.hasText(bank) && !StringUtils.hasText(number)) {
                        continue;
                    }
                    if (!StringUtils.hasText(bank) || !StringUtils.hasText(number)) {
                        errors.add(rowNo + "행: 계좌" + (g + 1) + " 은행·계좌번호가 불완전 (해당 계좌 생략)");
                        continue;
                    }
                    AccountForm af = new AccountForm();
                    af.setBankName(bank);
                    af.setAccountNumber(number);
                    af.setAccountHolder(StringUtils.hasText(holder) ? holder : nameKo);
                    addAccount(saved.getId(), af);
                }
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "엑셀을 읽을 수 없습니다(.xlsx 형식인지 확인하세요)");
        }
        return new ImportResult(total, created, skipped, errors);
    }

    // "외국인"/"FOREIGN" → FOREIGN, "한국인"/"KOREAN" → KOREAN, 그 외 null
    private WorkerType parseType(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String v = s.trim().toUpperCase();
        if (v.contains("외국") || v.startsWith("F")) {
            return WorkerType.FOREIGN;
        }
        if (v.contains("한국") || v.startsWith("K")) {
            return WorkerType.KOREAN;
        }
        return null;
    }

    // Y/예/1/true → true
    private boolean parseYn(String s) {
        if (!StringUtils.hasText(s)) {
            return false;
        }
        String v = s.trim().toLowerCase();
        return v.equals("y") || v.equals("yes") || v.equals("예") || v.equals("1") || v.equals("true") || v.equals("o");
    }

    // 행 전체가 빈 셀인지
    private boolean isBlankRow(Row row) {
        for (int c = 0; c < IMPORT_HEADERS.length; c++) {
            if (StringUtils.hasText(cellStr(row, c))) {
                return false;
            }
        }
        return true;
    }

    // 셀을 문자열로(숫자는 정수면 소수점 없이, 날짜서식은 yyyy-MM-dd). 빈값이면 null.
    private String cellStr(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        String out;
        switch (cell.getCellType()) {
            case STRING -> out = cell.getStringCellValue();
            case BOOLEAN -> out = String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    out = cell.getLocalDateTimeCellValue().toLocalDate().toString();
                } else {
                    double d = cell.getNumericCellValue();
                    out = (d == Math.rint(d)) ? String.valueOf((long) d) : String.valueOf(d);
                }
            }
            case FORMULA -> {
                try {
                    out = cell.getStringCellValue();
                } catch (IllegalStateException e) {
                    out = String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> out = null;
        }
        if (out == null) {
            return null;
        }
        out = out.trim();
        return out.isEmpty() ? null : out;
    }

    // 셀을 LocalDate로. 날짜서식 셀 또는 YYYY-MM-DD/./ 구분 텍스트 허용. 빈값 null. 형식오류 시 예외.
    private LocalDate cellDate(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String s = cellStr(row, col);
        if (s == null) {
            return null;
        }
        String norm = s.replace('.', '-').replace('/', '-').replaceAll("-+$", "").trim();
        try {
            return LocalDate.parse(norm);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("날짜는 YYYY-MM-DD 형식으로 입력하세요: '" + s + "'");
        }
    }

    // 예외 메시지 축약
    private String msg(Throwable e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
