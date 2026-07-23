// HomeController.java — 인증 후 대시보드 진입점(GET /). 상세 대시보드는 Stage 8에서 구현(F-11).
package com.jwgasul.web;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    // 대시보드 자리표시자 화면. 로그인 사용자명을 표시한다.
    @GetMapping("/")
    public String home(Principal principal, Model model) {
        model.addAttribute("username", principal != null ? principal.getName() : "");
        return "home";
    }
}
