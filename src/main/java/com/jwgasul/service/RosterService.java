// RosterService.java — 명부 생성(랜덤/수동)·저장(인적사항 스냅샷)·이력 조회(F-06·F-07, 3.6).
// 후보 풀 필터/제외는 소규모 인원 전제로 인메모리 처리한다.
package com.jwgasul.service;

import com.jwgasul.domain.DocType;
import com.jwgasul.domain.ExpiryStatus;
import com.jwgasul.domain.Roster;
import com.jwgasul.domain.RosterMember;
import com.jwgasul.domain.RosterType;
import com.jwgasul.domain.Site;
import com.jwgasul.domain.Worker;
import com.jwgasul.domain.WorkerAccount;
import com.jwgasul.dto.RandomResult;
import com.jwgasul.dto.RosterCriteria;
import com.jwgasul.repository.RosterMemberRepository;
import com.jwgasul.repository.RosterRepository;
import com.jwgasul.repository.SiteRepository;
import com.jwgasul.repository.WorkerAccountRepository;
import com.jwgasul.repository.WorkerDocumentRepository;
import com.jwgasul.repository.WorkerRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RosterService {

    private final WorkerRepository workerRepository;
    private final WorkerDocumentRepository documentRepository;
    private final WorkerAccountRepository accountRepository;
    private final RosterRepository rosterRepository;
    private final RosterMemberRepository rosterMemberRepository;
    private final SiteRepository siteRepository;
    private final StorageService storageService;
    private final AuditService auditService;
    private final int maxMembers;

    public RosterService(
            WorkerRepository workerRepository,
            WorkerDocumentRepository documentRepository,
            WorkerAccountRepository accountRepository,
            RosterRepository rosterRepository,
            RosterMemberRepository rosterMemberRepository,
            SiteRepository siteRepository,
            StorageService storageService,
            AuditService auditService,
            @Value("${app.roster.max-members:100}") int maxMembers) {
        this.workerRepository = workerRepository;
        this.documentRepository = documentRepository;
        this.accountRepository = accountRepository;
        this.rosterRepository = rosterRepository;
        this.rosterMemberRepository = rosterMemberRepository;
        this.siteRepository = siteRepository;
        this.storageService = storageService;
        this.auditService = auditService;
        this.maxMembers = maxMembers;
    }

    // 조건에 맞는 후보 풀(삭제 안 됨 + 유형 + 제외 옵션 통과)
    @Transactional(readOnly = true)
    public List<Worker> candidatePool(RosterCriteria c) {
        Specification<Worker> spec = (r, q, cb) -> cb.isNull(r.get("deletedAt"));
        if (c.getType() != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("workerType"), c.getType()));
        }
        List<Worker> all = workerRepository.findAll(spec);
        List<Worker> pool = new ArrayList<>();
        for (Worker w : all) {
            if (c.isExcludeVisaExpired() && w.getVisaStatus() == ExpiryStatus.EXPIRED) {
                continue;
            }
            if (c.isExcludeVisaImminent() && w.getVisaStatus() == ExpiryStatus.IMMINENT) {
                continue;
            }
            if (c.isExcludeEduExpired() && w.getEduStatus() == ExpiryStatus.EXPIRED) {
                continue;
            }
            if (c.isExcludeMissingDoc() && documentRepository.findByWorkerId(w.getId()).size() < 3) {
                continue;
            }
            pool.add(w);
        }
        return pool;
    }

    // 랜덤 명부 생성. 고정 인원 항상 포함(N에 포함), 부족하면 가능한 만큼 + 경고(F-06).
    @Transactional(readOnly = true)
    public RandomResult generateRandom(RosterCriteria c) {
        List<Worker> pool = candidatePool(c);
        List<Worker> fixed = new ArrayList<>();
        List<Worker> nonFixed = new ArrayList<>();
        for (Worker w : pool) {
            (w.isFixed() ? fixed : nonFixed).add(w);
        }

        List<Worker> selected = new ArrayList<>(fixed);
        String warning = null;

        if (fixed.size() >= c.getCount()) {
            if (fixed.size() > c.getCount()) {
                warning = "고정 인원(" + fixed.size() + "명)이 요청 인원(" + c.getCount() + "명)보다 많아 고정 인원 전원을 포함했습니다.";
            }
        } else {
            int need = c.getCount() - fixed.size();
            Collections.shuffle(nonFixed);
            List<Worker> picked = nonFixed.subList(0, Math.min(need, nonFixed.size()));
            selected.addAll(picked);
            if (picked.size() < need) {
                warning = "요청 " + c.getCount() + "명 중 " + selected.size() + "명만 가능합니다(필터·제외로 대상이 부족).";
            }
        }
        return new RandomResult(selected, warning);
    }

    // 교체(다시 뽑기): 현재 선택에 없는 비고정 후보 1명(잔여 풀에서). 없으면 empty.
    @Transactional(readOnly = true)
    public Optional<Worker> replaceCandidate(RosterCriteria c, Set<Long> currentIds) {
        List<Worker> remain = new ArrayList<>();
        for (Worker w : candidatePool(c)) {
            if (!w.isFixed() && !currentIds.contains(w.getId())) {
                remain.add(w);
            }
        }
        if (remain.isEmpty()) {
            return Optional.empty();
        }
        Collections.shuffle(remain);
        return Optional.of(remain.get(0));
    }

    // 명부 저장(인적사항 스냅샷). 현장 선택 시 title은 현장명으로, 아니면 입력 제목.
    @Transactional
    public Roster save(RosterType type, RosterCriteria c, List<Long> workerIds, String createdBy) {
        if (workerIds == null || workerIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "명부에 포함할 인원이 없습니다");
        }
        if (workerIds.size() > maxMembers) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "한 명부 최대 " + maxMembers + "명까지입니다. 나눠서 생성하세요.");
        }
        String title = resolveTitle(c);
        // 같은 현장·같은 날짜 중복 저장 차단(같은 날 같은 현장에 명부가 두 벌 생기는 것을 막는다)
        findDuplicate(c).ifPresent(dup -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "같은 현장·같은 날짜의 명부가 이미 있습니다 (" + dup.getTitle() + " · " + dup.getTargetDate() + ")");
        });
        Roster roster = rosterRepository.save(new Roster(c.getSiteId(), title, c.getTargetDate(), type, createdBy));
        for (Long wid : workerIds) {
            Worker w = workerRepository.findById(wid)
                    .orElseThrow(() -> new IllegalArgumentException("근로자를 찾을 수 없습니다: " + wid));
            rosterMemberRepository.save(new RosterMember(
                    roster.getId(), w.getId(), w.getNameKo(), w.getNameForeign(), w.getPhone(), w.getBirthDate()));
        }
        return roster;
    }

    // 현장 선택이면 현장명, 아니면 입력 제목. 둘 다 없으면 예외.
    private String resolveTitle(RosterCriteria c) {
        if (c.getSiteId() != null) {
            Site site = siteRepository.findById(c.getSiteId())
                    .orElseThrow(() -> new IllegalArgumentException("현장을 찾을 수 없습니다: " + c.getSiteId()));
            return site.getName();
        }
        if (StringUtils.hasText(c.getTitle())) {
            return c.getTitle().trim();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현장을 선택하거나 제목을 입력하세요");
    }

    @Transactional(readOnly = true)
    public List<Roster> history() {
        return rosterRepository.findAllByOrderByCreatedAtDesc();
    }

    // 현장 상세의 명부 이력 — 투입 날짜 오름차순(일정 순서대로 보이게)
    @Transactional(readOnly = true)
    public List<Roster> historyForSite(Long siteId) {
        return rosterRepository.findBySiteIdOrderByTargetDateAscCreatedAtAsc(siteId);
    }

    // 같은 현장(현장 미선택이면 같은 임시 제목)·같은 날짜의 기존 명부. 없으면 empty.
    @Transactional(readOnly = true)
    public Optional<Roster> findDuplicate(RosterCriteria c) {
        if (c.getTargetDate() == null) {
            return Optional.empty();
        }
        return c.getSiteId() != null
                ? rosterRepository.findFirstBySiteIdAndTargetDate(c.getSiteId(), c.getTargetDate())
                : rosterRepository.findFirstBySiteIdIsNullAndTitleAndTargetDate(resolveTitle(c), c.getTargetDate());
    }

    @Transactional(readOnly = true)
    public Roster get(Long id) {
        return rosterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("명부를 찾을 수 없습니다: " + id));
    }

    @Transactional(readOnly = true)
    public List<RosterMember> members(Long rosterId) {
        return rosterMemberRepository.findByRosterId(rosterId);
    }

    // ============================================================
    // 엑셀 다운로드 (F-08) — A 기본 / B 사진 포함 / C 계좌 포함(송금용)
    // 인적사항은 스냅샷, 사진·계좌는 조회 시점 현재 worker_id 기준(3.6).
    // ============================================================

    @Transactional
    public byte[] exportExcel(Long rosterId, String mode) {
        Roster roster = get(rosterId);
        List<RosterMember> members = members(rosterId);
        boolean photo = "B".equalsIgnoreCase(mode);
        boolean account = "C".equalsIgnoreCase(mode);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("명부");
            CreationHelper helper = wb.getCreationHelper();
            Drawing<?> drawing = sheet.createDrawingPatriarch();

            CellStyle headStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headStyle.setFont(bold);

            // 헤더
            List<String> headers = new ArrayList<>(List.of("이름", "연락처", "생년월일"));
            if (photo) {
                headers.addAll(List.of("신분증(앞)", "신분증(뒤)", "이수증"));
            }
            if (account) {
                headers.addAll(List.of("은행", "계좌번호", "예금주"));
            }
            Row head = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell c = head.createCell(i);
                c.setCellValue(headers.get(i));
                c.setCellStyle(headStyle);
            }
            sheet.setColumnWidth(0, 12 * 256);
            sheet.setColumnWidth(1, 16 * 256);
            sheet.setColumnWidth(2, 14 * 256);
            int photoBase = 3;
            if (photo) {
                for (int c = 0; c < 3; c++) {
                    sheet.setColumnWidth(photoBase + c, 18 * 256);
                }
            }
            int accountBase = photo ? photoBase + 3 : 3;

            int r = 1;
            for (RosterMember m : members) {
                Row row = sheet.createRow(r);
                row.createCell(0).setCellValue(m.getSnapNameKo());
                row.createCell(1).setCellValue(m.getSnapPhone());
                row.createCell(2).setCellValue(m.getSnapBirthDate().toString());

                if (photo) {
                    row.setHeightInPoints(90);
                    DocType[] slots = {DocType.ID_FRONT, DocType.ID_BACK, DocType.EDU_CERT};
                    for (int s = 0; s < slots.length; s++) {
                        insertPhoto(wb, drawing, helper, m.getWorkerId(), slots[s], r, photoBase + s);
                    }
                }
                if (account) {
                    fillAccount(row, m.getWorkerId(), accountBase);
                }
                r++;
            }

            // 계좌 포함 반출은 감사 로그(F-12 필수)
            if (account) {
                auditService.log("ROSTER", rosterId, "VIEW", "excel_export_account", null, roster.getTitle());
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("엑셀 생성 실패", e);
        }
    }

    // 셀에 서류 썸네일 삽입. worker_id 없음(삭제/재입사) 또는 서류 없음 → 공란.
    private void insertPhoto(XSSFWorkbook wb, Drawing<?> drawing, CreationHelper helper,
                             Long workerId, DocType docType, int row, int col) {
        if (workerId == null) {
            return;
        }
        var doc = documentRepository.findByWorkerIdAndDocType(workerId, docType);
        if (doc.isEmpty()) {
            return;
        }
        byte[] thumb = storageService.thumbnailJpeg(doc.get().getFilePath(), 300);
        if (thumb == null) {
            return;
        }
        int picIdx = wb.addPicture(thumb, Workbook.PICTURE_TYPE_JPEG);
        ClientAnchor anchor = helper.createClientAnchor();
        anchor.setCol1(col);
        anchor.setRow1(row);
        anchor.setCol2(col + 1);
        anchor.setRow2(row + 1);
        anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
        drawing.createPicture(anchor, picIdx);
    }

    // 주계좌(없으면 표시순서 1번) 전체 계좌번호를 채운다. 특정 불가 시 공란.
    private void fillAccount(Row row, Long workerId, int base) {
        if (workerId == null) {
            return;
        }
        WorkerAccount acc = accountRepository.findByWorkerIdAndPrimaryTrue(workerId).orElseGet(() ->
                accountRepository.findByWorkerIdOrderByPrimaryDescSortOrderAsc(workerId).stream().findFirst().orElse(null));
        if (acc == null) {
            return;
        }
        row.createCell(base).setCellValue(acc.getBankName());
        row.createCell(base + 1).setCellValue(acc.getAccountNumber()); // 송금용 전체 번호
        row.createCell(base + 2).setCellValue(acc.getAccountHolder());
    }
}
