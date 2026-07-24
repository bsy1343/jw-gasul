// WebMimeConfig.java — 정적 리소스 MIME 매핑 보정.
// 톰캣 기본 매핑에 .webmanifest가 없어 application/octet-stream으로 나가면
// 브라우저가 홈 화면 추가(PWA) 매니페스트로 인식하지 못한다.
package com.jwgasul.common.config;

import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebMimeConfig {

    // .webmanifest 확장자를 표준 MIME 타입(application/manifest+json)으로 매핑한다.
    // ※ Boot 4: MIME 설정은 web.server.servlet.ConfigurableServletWebServerFactory로 이동했고
    //    기본 매핑을 보존하는 addMimeMappings를 쓴다(3.x의 ConfigurableWebServerFactory.setMimeMappings 아님).
    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> webManifestMimeMapping() {
        return factory -> {
            MimeMappings mappings = new MimeMappings();
            mappings.add("webmanifest", "application/manifest+json");
            factory.addMimeMappings(mappings);
        };
    }
}
