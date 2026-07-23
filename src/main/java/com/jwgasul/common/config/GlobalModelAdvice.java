// GlobalModelAdvice.java — 모든 화면 공통 모델 값. 교육 듣기 링크 업체 코드(F-10)를 노출한다.
package com.jwgasul.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAdvice {

    private final String eduEntCode;

    public GlobalModelAdvice(@Value("${app.edu.ent-code}") String eduEntCode) {
        this.eduEntCode = eduEntCode;
    }

    // 교육 듣기 링크 업체 코드(모든 뷰에서 사용 가능)
    @ModelAttribute("eduEntCode")
    public String eduEntCode() {
        return eduEntCode;
    }
}
