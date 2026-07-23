// SiteController.java — 현장 목록/등록/상세·수정/종료·재개/삭제(F-05)
package com.jwgasul.controller;

import com.jwgasul.common.exception.DuplicateSiteException;
import com.jwgasul.domain.Site;
import com.jwgasul.dto.SiteForm;
import com.jwgasul.service.RosterService;
import com.jwgasul.service.SiteService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SiteController {

    private final SiteService siteService;
    private final RosterService rosterService;

    public SiteController(SiteService siteService, RosterService rosterService) {
        this.siteService = siteService;
        this.rosterService = rosterService;
    }

    // 목록: 진행 중 / 종료 / 전체 탭
    @GetMapping("/sites")
    public String list(@RequestParam(name = "active", required = false) Boolean active, Model model) {
        model.addAttribute("sites", siteService.list(active));
        model.addAttribute("active", active);
        return "sites/list";
    }

    // 등록 폼
    @GetMapping("/sites/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("siteForm")) {
            model.addAttribute("siteForm", new SiteForm());
        }
        model.addAttribute("mode", "new");
        return "sites/form";
    }

    // 등록 처리
    @PostMapping("/sites")
    public String create(@Valid @ModelAttribute("siteForm") SiteForm form,
                         BindingResult binding, Model model, RedirectAttributes ra) {
        if (binding.hasErrors()) {
            model.addAttribute("mode", "new");
            return "sites/form";
        }
        try {
            Site saved = siteService.create(form);
            ra.addFlashAttribute("message", "현장이 등록되었습니다");
            return "redirect:/sites/" + saved.getId();
        } catch (DuplicateSiteException e) {
            binding.rejectValue("name", "duplicate", e.getMessage());
            model.addAttribute("mode", "new");
            return "sites/form";
        }
    }

    // 상세·수정(명부 이력은 Stage 6에서)
    @GetMapping("/sites/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Site site = siteService.get(id);
        model.addAttribute("site", site);
        if (!model.containsAttribute("siteForm")) {
            model.addAttribute("siteForm", siteService.toForm(site));
        }
        model.addAttribute("rosters", rosterService.historyForSite(id));
        return "sites/detail";
    }

    // 수정 처리
    @PostMapping("/sites/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("siteForm") SiteForm form,
                         BindingResult binding, Model model, RedirectAttributes ra) {
        if (binding.hasErrors()) {
            model.addAttribute("site", siteService.get(id));
            return "sites/detail";
        }
        try {
            siteService.update(id, form);
            ra.addFlashAttribute("message", "수정되었습니다");
            return "redirect:/sites/" + id;
        } catch (DuplicateSiteException e) {
            binding.rejectValue("name", "duplicate", e.getMessage());
            model.addAttribute("site", siteService.get(id));
            return "sites/detail";
        }
    }

    // 종료 처리
    @PostMapping("/sites/{id}/deactivate")
    public String deactivate(@PathVariable Long id, RedirectAttributes ra) {
        siteService.setActive(id, false);
        ra.addFlashAttribute("message", "현장을 종료 처리했습니다");
        return "redirect:/sites/" + id;
    }

    // 재개 처리
    @PostMapping("/sites/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes ra) {
        siteService.setActive(id, true);
        ra.addFlashAttribute("message", "현장을 다시 진행 중으로 변경했습니다");
        return "redirect:/sites/" + id;
    }

    // 삭제(명부 이력 있으면 차단 — 종료 처리 유도)
    @PostMapping("/sites/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            siteService.delete(id);
            ra.addFlashAttribute("message", "현장이 삭제되었습니다");
            return "redirect:/sites";
        } catch (ResponseStatusException e) {
            ra.addFlashAttribute("message", e.getReason());
            return "redirect:/sites/" + id;
        }
    }
}
