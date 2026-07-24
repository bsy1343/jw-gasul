// RosterController.java — 랜덤/수동 명부 생성·저장·이력(F-06·F-07). 엑셀 다운로드는 Stage 7.
package com.jwgasul.controller;

import com.jwgasul.domain.Roster;
import com.jwgasul.domain.RosterType;
import com.jwgasul.domain.Worker;
import com.jwgasul.dto.RandomResult;
import com.jwgasul.dto.RosterCriteria;
import com.jwgasul.service.RosterService;
import com.jwgasul.service.SiteService;
import com.jwgasul.service.WorkerService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RosterController {

    private final RosterService rosterService;
    private final SiteService siteService;
    private final WorkerService workerService;

    public RosterController(RosterService rosterService, SiteService siteService, WorkerService workerService) {
        this.rosterService = rosterService;
        this.siteService = siteService;
        this.workerService = workerService;
    }

    // 명부 이력
    @GetMapping("/roster")
    public String history(Model model) {
        model.addAttribute("rosters", rosterService.history());
        return "roster/list";
    }

    // 명부 상세(스냅샷 구성원)
    @GetMapping("/roster/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("roster", rosterService.get(id));
        model.addAttribute("members", rosterService.members(id));
        return "roster/detail";
    }

    // 엑셀 다운로드(F-08): mode A 기본 / B 사진 / C 계좌. 파일명 재원가설_{현장}_{yyyyMMdd}.xlsx
    @GetMapping("/roster/{id}/excel")
    public ResponseEntity<byte[]> excel(@PathVariable Long id,
                                        @RequestParam(name = "mode", defaultValue = "A") String mode) {
        Roster roster = rosterService.get(id);
        byte[] bytes = rosterService.exportExcel(id, mode);
        String date = roster.getTargetDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String safeTitle = roster.getTitle().replaceAll("[\\\\/:*?\"<>|]", "_");
        String filename = "재원가설_" + safeTitle + "_" + date + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }

    // ===== 랜덤 명부(F-06) =====

    // 생성 조건 입력 폼
    @GetMapping("/roster/random")
    public String randomForm(Model model) {
        if (!model.containsAttribute("criteria")) {
            model.addAttribute("criteria", new RosterCriteria());
        }
        model.addAttribute("sites", siteService.activeSites());
        return "roster/random-form";
    }

    // 생성 → 결과 화면
    @PostMapping("/roster/random")
    public String generate(@Valid @ModelAttribute("criteria") RosterCriteria criteria,
                           BindingResult binding, Model model) {
        model.addAttribute("sites", siteService.activeSites());
        if (binding.hasErrors()) {
            return "roster/random-form";
        }
        RandomResult result = rosterService.generateRandom(criteria);
        model.addAttribute("result", result);
        model.addAttribute("criteria", criteria);
        model.addAttribute("rosterType", "RANDOM");
        return "roster/result";
    }

    // 교체(다시 뽑기): 현재 선택에 없는 후보 1명을 JSON으로 반환
    @PostMapping("/roster/random/replace")
    @ResponseBody
    public Map<String, Object> replace(@ModelAttribute RosterCriteria criteria,
                                       @RequestParam(name = "currentIds", required = false) List<Long> currentIds) {
        Optional<Worker> picked = rosterService.replaceCandidate(criteria,
                new HashSet<>(currentIds == null ? List.of() : currentIds));
        Map<String, Object> res = new LinkedHashMap<>();
        if (picked.isEmpty()) {
            res.put("ok", false);
            return res;
        }
        Worker w = picked.get();
        res.put("ok", true);
        res.put("id", w.getId());
        res.put("nameKo", w.getNameKo());
        res.put("nameForeign", w.getNameForeign());
        res.put("phone", w.getPhone());
        res.put("birthDate", w.getBirthDate().toString());
        return res;
    }

    // ===== 수동 선택 명부(F-07) =====

    // 선택 화면(근로자 체크박스). 소규모 전제로 최대 500명 표시.
    @GetMapping("/roster/selected")
    public String selectForm(@ModelAttribute RosterCriteria criteria, Model model) {
        model.addAttribute("sites", siteService.activeSites());
        model.addAttribute("workers",
                workerService.list(new com.jwgasul.dto.WorkerFilter(), PageRequest.of(0, 500)).getContent());
        model.addAttribute("criteria", criteria);
        return "roster/select";
    }

    // ===== 저장(랜덤/수동 공통) =====
    @PostMapping("/roster/save")
    public String save(@ModelAttribute RosterCriteria criteria,
                       @RequestParam("rosterType") RosterType rosterType,
                       @RequestParam(name = "workerIds", required = false) List<Long> workerIds,
                       Principal principal, RedirectAttributes ra) {
        try {
            String createdBy = principal != null ? principal.getName() : null;
            Roster saved = rosterService.save(rosterType, criteria, workerIds, createdBy);
            ra.addFlashAttribute("message", "명부가 저장되었습니다");
            return "redirect:/roster/" + saved.getId();
        } catch (ResponseStatusException e) {
            ra.addFlashAttribute("message", e.getReason());
            return "redirect:/roster/random";
        }
    }
}
