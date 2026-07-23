// SiteService.java — 현장 CRUD 및 종료/재개 처리(F-05, 3.5). 현장명은 유니크.
// 삭제 시 명부 이력 참조 차단은 Stage 6(명부)에서 추가한다.
package com.jwgasul.service;

import com.jwgasul.common.exception.DuplicateSiteException;
import com.jwgasul.domain.Site;
import com.jwgasul.dto.SiteForm;
import com.jwgasul.repository.RosterRepository;
import com.jwgasul.repository.SiteRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SiteService {

    private final SiteRepository siteRepository;
    private final RosterRepository rosterRepository;

    public SiteService(SiteRepository siteRepository, RosterRepository rosterRepository) {
        this.siteRepository = siteRepository;
        this.rosterRepository = rosterRepository;
    }

    // 목록. active=null 전체, true 진행 중, false 종료.
    @Transactional(readOnly = true)
    public List<Site> list(Boolean active) {
        return active == null
                ? siteRepository.findAllByOrderByCreatedAtDesc()
                : siteRepository.findByActiveOrderByCreatedAtDesc(active);
    }

    // 진행 중 현장(명부 생성 드롭다운 등)
    @Transactional(readOnly = true)
    public List<Site> activeSites() {
        return siteRepository.findByActiveOrderByCreatedAtDesc(true);
    }

    @Transactional(readOnly = true)
    public Site get(Long id) {
        return siteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("현장을 찾을 수 없습니다: " + id));
    }

    // 신규 등록(현장명 중복 검사)
    @Transactional
    public Site create(SiteForm form) {
        String name = form.getName().trim();
        if (siteRepository.existsByName(name)) {
            throw new DuplicateSiteException("이미 등록된 현장명입니다: " + name);
        }
        Site site = new Site(name, emptyToNull(form.getClientName()), emptyToNull(form.getAddress()),
                form.getStartDate(), form.getEndDate(), form.isActive(), emptyToNull(form.getMemo()));
        return siteRepository.save(site);
    }

    // 수정(현장명 중복 검사 — 자기 자신 제외)
    @Transactional
    public Site update(Long id, SiteForm form) {
        Site site = get(id);
        String name = form.getName().trim();
        if (siteRepository.existsByNameAndIdNot(name, id)) {
            throw new DuplicateSiteException("이미 등록된 현장명입니다: " + name);
        }
        site.update(name, emptyToNull(form.getClientName()), emptyToNull(form.getAddress()),
                form.getStartDate(), form.getEndDate(), form.isActive(), emptyToNull(form.getMemo()));
        return siteRepository.save(site);
    }

    // 종료/재개 처리(is_active 전환)
    @Transactional
    public void setActive(Long id, boolean active) {
        Site site = get(id);
        site.setActive(active);
        siteRepository.save(site);
    }

    // 삭제. 명부 이력이 있으면 차단하고 종료 처리를 유도한다(F-05).
    @Transactional
    public void delete(Long id) {
        if (rosterRepository.countBySiteId(id) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "이 현장으로 생성된 명부 이력이 있어 삭제할 수 없습니다. 종료 처리를 이용하세요.");
        }
        siteRepository.delete(get(id));
    }

    // 수정 폼 채우기
    public SiteForm toForm(Site s) {
        SiteForm f = new SiteForm();
        f.setName(s.getName());
        f.setClientName(s.getClientName());
        f.setAddress(s.getAddress());
        f.setStartDate(s.getStartDate());
        f.setEndDate(s.getEndDate());
        f.setActive(s.isActive());
        f.setMemo(s.getMemo());
        return f;
    }

    private String emptyToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
