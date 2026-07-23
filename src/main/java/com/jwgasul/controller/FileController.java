// FileController.java — 업로드 이미지 서빙(GET /files/**). 인증된 요청만 스트리밍(6장 보안)
package com.jwgasul.controller;

import com.jwgasul.service.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FileController {

    private final StorageService storageService;

    public FileController(StorageService storageService) {
        this.storageService = storageService;
    }

    // /files/ 이후 경로를 저장 루트 기준 상대 경로로 해석해 이미지를 반환한다(트래버설은 스토리지에서 차단)
    @GetMapping("/files/**")
    public ResponseEntity<Resource> serve(HttpServletRequest request) {
        String prefix = request.getContextPath() + "/files/";
        String relative = URLDecoder.decode(
                request.getRequestURI().substring(prefix.length()), StandardCharsets.UTF_8);
        Resource resource = storageService.loadAsResource(relative);

        MediaType mediaType = relative.toLowerCase().endsWith(".png")
                ? MediaType.IMAGE_PNG
                : MediaType.IMAGE_JPEG;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(resource);
    }
}
