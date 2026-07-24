// BaseUrlAdvice.java — 모든 뷰에 baseUrl(사이트 공개 절대 URL)을 주입하는 횡단 관심사.
// 카카오톡 등 메신저 링크 미리보기(Open Graph)는 og:image/og:url이 절대 URL이어야 하므로
// 도메인을 하드코딩하지 않고 들어온 요청에서 복원한다.
// (server.forward-headers-strategy=framework 이므로 Cloudflare Tunnel 뒤에서도 https://도메인 으로 복원됨)
package com.jwgasul.common.config;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@ControllerAdvice
public class BaseUrlAdvice {

    // 현재 요청 기준 스킴+호스트(+컨텍스트 경로)를 baseUrl 모델 속성으로 노출한다
    @ModelAttribute("baseUrl")
    public String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
