// HomeController.java — 대시보드(GET /, F-11). 요약 카드·주의 인원·최근 명부.
package com.jwgasul.controller;

import com.jwgasul.service.DashboardService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final DashboardService dashboardService;

    public HomeController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    // 대시보드 화면. 로그인 사용자명 + 집계 데이터.
    @GetMapping("/")
    public String home(Principal principal, Model model) {
        model.addAttribute("username", principal != null ? principal.getName() : "");
        model.addAttribute("dashboard", dashboardService.load());
        return "home";
    }
}
