// LoginController.java — 로그인 화면(GET /login) 렌더링(F-01)
package com.jwgasul.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    // 로그인 페이지. 실패/잠금/로그아웃 파라미터는 템플릿에서 분기 표시한다.
    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
