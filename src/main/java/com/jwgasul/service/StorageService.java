// StorageService.java — 업로드 파일 저장(로컬 파일시스템). 저장 루트는 app.upload.dir(웹 루트 외부).
// 경로 규칙: {root}/{workerId}/{slot}_{uuid}.{ext} (3.2). 외부 시스템이 아니라 인터페이스 없이 단일 클래스.
package com.jwgasul.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StorageService {

    // 저장 결과 메타데이터(웹 루트 외부의 상대 저장 경로 포함)
    public record StoredFile(String relativePath, String originalName, long size) {
    }

    private final Path root;

    public StorageService(@Value("${app.upload.dir:./data/uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    // 파일을 {root}/{workerId}/{slot}_{uuid}.{ext}로 저장한다
    public StoredFile store(Long workerId, String slot, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "빈 파일은 저장할 수 없습니다");
        }
        try {
            Path dir = root.resolve(String.valueOf(workerId));
            Files.createDirectories(dir);
            String ext = extensionOf(file.getOriginalFilename());
            String filename = slot + "_" + UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
            Path target = dir.resolve(filename);
            file.transferTo(target);
            // 저장 루트 기준 상대 경로를 반환(DB에는 상대 경로만 보관)
            String relative = root.relativize(target).toString();
            return new StoredFile(relative, file.getOriginalFilename(), file.getSize());
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장 실패", e);
        }
    }

    // 상대 경로를 저장 루트 안으로 한정해 로드한다(디렉터리 트래버설 차단)
    public Resource loadAsResource(String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 경로");
        }
        try {
            Resource resource = new UrlResource(resolved.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다");
            }
            return resource;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다", e);
        }
    }

    // 상대 경로 파일을 삭제한다(루트 밖 경로는 무시, 없어도 예외 없음)
    public void delete(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return;
        }
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(resolved);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 삭제 실패", e);
        }
    }

    // 원본 파일명에서 확장자 추출(소문자)
    private String extensionOf(String originalName) {
        String ext = StringUtils.getFilenameExtension(originalName);
        return ext == null ? "" : ext.toLowerCase();
    }

    // 저장 이미지의 축소 썸네일(JPEG, 장변 maxEdge). 엑셀 삽입 메모리 보호(F-08).
    // 파일이 없거나 이미지가 아니면 null.
    public byte[] thumbnailJpeg(String relativePath, int maxEdge) {
        if (!StringUtils.hasText(relativePath)) {
            return null;
        }
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root) || !Files.exists(resolved)) {
            return null;
        }
        try {
            BufferedImage src = ImageIO.read(resolved.toFile());
            if (src == null) {
                return null;
            }
            double scale = Math.min(1.0, (double) maxEdge / Math.max(src.getWidth(), src.getHeight()));
            int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
            int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
            BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(dst, "jpg", bos);
            return bos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }
}

