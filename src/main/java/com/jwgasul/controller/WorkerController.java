// WorkerController.java — 근로자 목록/등록/상세·수정/삭제 및 서류 업로드 화면(F-02, F-03)
package com.jwgasul.controller;

import com.jwgasul.common.exception.DuplicateWorkerException;
import com.jwgasul.domain.DocType;
import com.jwgasul.domain.Worker;
import com.jwgasul.domain.WorkerType;
import com.jwgasul.dto.AccountForm;
import com.jwgasul.dto.WorkerForm;
import com.jwgasul.service.WorkerService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WorkerController {

    private final WorkerService workerService;

    public WorkerController(WorkerService workerService) {
        this.workerService = workerService;
    }

    // 목록: 전체/외국인/한국인 탭 + 이름·연락처 검색, 비자만료일 오름차순 기본(F-03)
    @GetMapping("/workers")
    public String list(
            @RequestParam(name = "type", required = false) WorkerType type,
            @RequestParam(name = "q", required = false) String q,
            @PageableDefault(size = 20, sort = "visaExpireDate", direction = Sort.Direction.ASC) Pageable pageable,
            Model model) {
        Page<Worker> page = workerService.list(type, q, pageable);
        model.addAttribute("page", page);
        model.addAttribute("workers", page.getContent());
        model.addAttribute("type", type);
        model.addAttribute("q", q);
        model.addAttribute("summary", workerService.expirySummary());
        return "workers/list";
    }

    // 등록 폼(유형 선택 → 동적 필드)
    @GetMapping("/workers/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("workerForm")) {
            model.addAttribute("workerForm", new WorkerForm());
        }
        model.addAttribute("mode", "new");
        return "workers/form";
    }

    // 등록 처리
    @PostMapping("/workers")
    public String create(
            @Valid @ModelAttribute("workerForm") WorkerForm form,
            BindingResult binding,
            Model model,
            RedirectAttributes ra) {
        if (binding.hasErrors()) {
            model.addAttribute("mode", "new");
            return "workers/form";
        }
        try {
            Worker saved = workerService.create(form);
            ra.addFlashAttribute("message", "근로자가 등록되었습니다");
            return "redirect:/workers/" + saved.getId();
        } catch (DuplicateWorkerException e) {
            binding.reject("duplicate", e.getMessage());
            model.addAttribute("mode", "new");
            return "workers/form";
        }
    }

    // 상세·수정 화면(사진 + 수정 폼)
    @GetMapping("/workers/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Worker worker = workerService.getActive(id);
        model.addAttribute("worker", worker);
        if (!model.containsAttribute("workerForm")) {
            model.addAttribute("workerForm", workerService.toForm(worker));
        }
        model.addAttribute("documents", workerService.documentsByType(id));
        model.addAttribute("docTypes", DocType.values());
        model.addAttribute("accounts", workerService.accounts(id));
        if (!model.containsAttribute("accountForm")) {
            model.addAttribute("accountForm", new AccountForm());
        }
        return "workers/detail";
    }

    // 수정 처리
    @PostMapping("/workers/{id}")
    public String update(
            @PathVariable Long id,
            @Valid @ModelAttribute("workerForm") WorkerForm form,
            BindingResult binding,
            Model model,
            RedirectAttributes ra) {
        if (binding.hasErrors()) {
            model.addAttribute("worker", workerService.getActive(id));
            model.addAttribute("documents", workerService.documentsByType(id));
            model.addAttribute("docTypes", DocType.values());
            return "workers/detail";
        }
        workerService.update(id, form);
        ra.addFlashAttribute("message", "수정되었습니다");
        return "redirect:/workers/" + id;
    }

    // 삭제(soft delete)
    @PostMapping("/workers/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        workerService.softDelete(id);
        ra.addFlashAttribute("message", "삭제되었습니다");
        return "redirect:/workers";
    }

    // 서류 슬롯 업로드/교체
    @PostMapping("/workers/{id}/documents/{docType}")
    public String uploadDocument(
            @PathVariable Long id,
            @PathVariable DocType docType,
            @RequestParam("file") MultipartFile file,
            RedirectAttributes ra) {
        workerService.uploadDocument(id, docType, file);
        ra.addFlashAttribute("message", docType.getLabel() + " 업로드 완료");
        return "redirect:/workers/" + id;
    }

    // 서류 슬롯 삭제
    @PostMapping("/workers/{id}/documents/{docType}/delete")
    public String deleteDocument(
            @PathVariable Long id,
            @PathVariable DocType docType,
            RedirectAttributes ra) {
        workerService.deleteDocument(id, docType);
        ra.addFlashAttribute("message", docType.getLabel() + " 삭제됨");
        return "redirect:/workers/" + id;
    }

    // ===== 계좌 (F-09) — 근로자 상세 화면 내 =====

    // 계좌 추가
    @PostMapping("/workers/{id}/accounts")
    public String addAccount(@PathVariable Long id,
                             @Valid @ModelAttribute("accountForm") AccountForm form,
                             BindingResult binding, RedirectAttributes ra) {
        if (binding.hasErrors()) {
            ra.addFlashAttribute("message", binding.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/workers/" + id;
        }
        try {
            workerService.addAccount(id, form);
            ra.addFlashAttribute("message", "계좌가 추가되었습니다");
        } catch (ResponseStatusException e) {
            ra.addFlashAttribute("message", e.getReason());
        }
        return "redirect:/workers/" + id;
    }

    // 계좌 수정
    @PostMapping("/workers/{id}/accounts/{accountId}")
    public String updateAccount(@PathVariable Long id, @PathVariable Long accountId,
                                @Valid @ModelAttribute("accountForm") AccountForm form,
                                BindingResult binding, RedirectAttributes ra) {
        if (binding.hasErrors()) {
            ra.addFlashAttribute("message", binding.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/workers/" + id;
        }
        workerService.updateAccount(id, accountId, form);
        ra.addFlashAttribute("message", "계좌가 수정되었습니다");
        return "redirect:/workers/" + id;
    }

    // 계좌 삭제
    @PostMapping("/workers/{id}/accounts/{accountId}/delete")
    public String deleteAccount(@PathVariable Long id, @PathVariable Long accountId, RedirectAttributes ra) {
        workerService.deleteAccount(id, accountId);
        ra.addFlashAttribute("message", "계좌가 삭제되었습니다");
        return "redirect:/workers/" + id;
    }

    // 주계좌 지정
    @PostMapping("/workers/{id}/accounts/{accountId}/primary")
    public String setPrimaryAccount(@PathVariable Long id, @PathVariable Long accountId, RedirectAttributes ra) {
        workerService.setPrimaryAccount(id, accountId);
        ra.addFlashAttribute("message", "주계좌로 지정되었습니다");
        return "redirect:/workers/" + id;
    }

    // 계좌번호 전체 노출(하이픈 제외 숫자) — 클릭 시 호출, 감사 로그(VIEW) 기록. JS가 표시/복사에 사용.
    @GetMapping("/workers/{id}/accounts/{accountId}/reveal")
    @ResponseBody
    public String revealAccount(@PathVariable Long id, @PathVariable Long accountId) {
        return workerService.revealAccount(id, accountId);
    }
}
