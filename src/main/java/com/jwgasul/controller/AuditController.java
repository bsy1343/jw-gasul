// AuditController.java — 감사 로그 조회 화면(F-12). 기간·사용자·대상 필터 + 페이지네이션.
package com.jwgasul.controller;

import com.jwgasul.dto.AuditFilter;
import com.jwgasul.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuditController {

    private static final int PAGE_SIZE = 30;

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    // 감사 로그 목록(최신순, 30건씩). 필터는 쿼리 파라미터로 바인딩.
    @GetMapping("/audit")
    public String list(@ModelAttribute("filter") AuditFilter filter,
                       @RequestParam(name = "page", defaultValue = "0") int page,
                       Model model) {
        int safePage = Math.max(page, 0);
        Page<?> logs = auditService.search(
                filter, PageRequest.of(safePage, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")));
        model.addAttribute("logs", logs);
        // 대상(entityType) 필터 드롭다운 옵션
        model.addAttribute("entityTypes", new String[] {"WORKER", "ACCOUNT", "DOCUMENT", "SITE", "ROSTER"});
        return "audit/list";
    }
}
