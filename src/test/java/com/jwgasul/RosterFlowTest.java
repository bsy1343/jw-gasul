// RosterFlowTest.java — 명부 생성 규칙(고정 포함·제외 필터)·스냅샷·저장·현장 삭제 차단 검증(F-06·F-07·F-05)
package com.jwgasul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jwgasul.domain.Roster;
import com.jwgasul.domain.RosterMember;
import com.jwgasul.domain.RosterType;
import com.jwgasul.domain.Site;
import com.jwgasul.domain.Worker;
import com.jwgasul.dto.RosterCriteria;
import com.jwgasul.dto.SiteForm;
import com.jwgasul.dto.WorkerForm;
import com.jwgasul.domain.WorkerType;
import com.jwgasul.service.RosterService;
import com.jwgasul.service.SiteService;
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
import org.springframework.web.server.ResponseStatusException;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class RosterFlowTest {

    private static final AtomicInteger SEQ = new AtomicInteger(6000);

    @Autowired private MockMvc mockMvc;
    @Autowired private WorkerService workerService;
    @Autowired private SiteService siteService;
    @Autowired private RosterService rosterService;

    private Worker createKorean(boolean fixed) {
        int n = SEQ.incrementAndGet();
        WorkerForm f = new WorkerForm();
        f.setWorkerType(WorkerType.KOREAN);
        f.setNameKo("명부테스트" + n);
        f.setBirthDate(LocalDate.of(1990, 1, 1));
        f.setPhone("010" + String.format("%08d", n));
        f.setFixed(fixed);
        return workerService.create(f);
    }

    // 고정 인원은 항상 결과에 포함된다
    @Test
    void fixedWorkerAlwaysIncluded() {
        Worker fixed = createKorean(true);
        RosterCriteria c = new RosterCriteria();
        c.setType(WorkerType.KOREAN);
        c.setCount(1000);
        c.setTargetDate(LocalDate.now());
        var selected = rosterService.generateRandom(c).selected();
        assertTrue(selected.stream().anyMatch(w -> w.getId().equals(fixed.getId())));
    }

    // 비자 만료자 제외 옵션이 동작한다
    @Test
    void visaExpiredExcluded() {
        int n = SEQ.incrementAndGet();
        WorkerForm f = new WorkerForm();
        f.setWorkerType(WorkerType.FOREIGN);
        f.setNameKo("만료외국인" + n);
        f.setBirthDate(LocalDate.of(1990, 1, 1));
        f.setPhone("010" + String.format("%08d", n));
        f.setVisaExpireDate(LocalDate.now().minusDays(10)); // 만료
        Long id = workerService.create(f).getId();

        RosterCriteria c = new RosterCriteria();
        c.setType(WorkerType.FOREIGN);
        c.setCount(1000);
        c.setTargetDate(LocalDate.now());
        c.setExcludeVisaExpired(true);
        assertFalse(rosterService.generateRandom(c).selected().stream().anyMatch(w -> w.getId().equals(id)),
                "비자 만료자는 제외되어야 한다");

        c.setExcludeVisaExpired(false);
        assertTrue(rosterService.generateRandom(c).selected().stream().anyMatch(w -> w.getId().equals(id)),
                "제외 해제 시 포함되어야 한다");
    }

    // 저장 시 인적사항 스냅샷이 보존된다(이후 근로자 수정과 무관)
    @Test
    void snapshotIsStableAfterWorkerChange() {
        Worker w = createKorean(false);
        RosterCriteria c = new RosterCriteria();
        c.setTitle("스냅샷현장");
        c.setTargetDate(LocalDate.now());
        Roster roster = rosterService.save(RosterType.MANUAL, c, List.of(w.getId()), "tester");

        List<RosterMember> before = rosterService.members(roster.getId());
        assertEquals(1, before.size());
        String snapName = before.get(0).getSnapNameKo();

        // 근로자 이름 변경
        WorkerForm ef = workerService.toForm(w);
        ef.setNameKo(snapName + "_변경");
        workerService.update(w.getId(), ef);

        // 명부 스냅샷은 그대로
        assertEquals(snapName, rosterService.members(roster.getId()).get(0).getSnapNameKo());
    }

    // 수동 저장(엔드포인트) → 명부 생성
    @Test
    void manualSaveViaEndpoint() throws Exception {
        Worker w = createKorean(false);
        mockMvc.perform(post("/roster/save").with(csrf())
                        .param("rosterType", "MANUAL")
                        .param("title", "수동저장현장")
                        .param("targetDate", LocalDate.now().toString())
                        .param("workerIds", w.getId().toString()))
                .andExpect(status().is3xxRedirection());
    }

    // 같은 현장·같은 날짜로 명부를 두 번 저장하면 차단된다(중복 방지)
    @Test
    void sameSiteAndDateIsRejected() {
        SiteForm sf = new SiteForm();
        sf.setName("중복체크현장-" + SEQ.incrementAndGet());
        Site site = siteService.create(sf);

        RosterCriteria c = new RosterCriteria();
        c.setSiteId(site.getId());
        c.setTargetDate(LocalDate.now());
        rosterService.save(RosterType.MANUAL, c, List.of(createKorean(false).getId()), "tester");

        Long otherWorkerId = createKorean(false).getId();
        assertThrows(ResponseStatusException.class,
                () -> rosterService.save(RosterType.MANUAL, c, List.of(otherWorkerId), "tester"));

        // 날짜가 다르면 정상 저장된다
        c.setTargetDate(LocalDate.now().plusDays(1));
        assertEquals(site.getId(),
                rosterService.save(RosterType.MANUAL, c, List.of(otherWorkerId), "tester").getSiteId());
    }

    // 명부 이력이 있는 현장은 삭제가 차단된다(F-05)
    @Test
    void siteWithRosterCannotBeDeleted() throws Exception {
        SiteForm sf = new SiteForm();
        sf.setName("삭제불가현장-" + SEQ.incrementAndGet());
        Site site = siteService.create(sf);

        Worker w = createKorean(false);
        RosterCriteria c = new RosterCriteria();
        c.setSiteId(site.getId());
        c.setTargetDate(LocalDate.now());
        rosterService.save(RosterType.MANUAL, c, List.of(w.getId()), "tester");

        mockMvc.perform(post("/sites/" + site.getId() + "/delete").with(csrf()))
                .andExpect(status().is3xxRedirection());
        // 여전히 존재(삭제 차단)
        assertEquals(site.getId(), siteService.get(site.getId()).getId());
    }
}
