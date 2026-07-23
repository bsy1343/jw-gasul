// WorkerDocumentService.java — 근로자 서류 사진 업로드/교체/삭제(F-02, 3.2).
// 서버는 jpg/png만 허용(HEIC 등 거부) — 리사이즈/변환은 클라이언트가 수행한다는 전제.
package com.jwgasul.worker;

import com.jwgasul.storage.StorageService;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkerDocumentService {

    // 서버 수용 형식(클라이언트에서 JPEG로 변환 후 업로드하는 것을 전제)
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    private final WorkerDocumentRepository documentRepository;
    private final StorageService storageService;

    public WorkerDocumentService(WorkerDocumentRepository documentRepository, StorageService storageService) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
    }

    // 근로자의 서류를 슬롯(DocType)별 맵으로 반환(상세 화면용)
    @Transactional(readOnly = true)
    public Map<DocType, WorkerDocument> documentsByType(Long workerId) {
        Map<DocType, WorkerDocument> map = new EnumMap<>(DocType.class);
        for (WorkerDocument doc : documentRepository.findByWorkerId(workerId)) {
            map.put(doc.getDocType(), doc);
        }
        return map;
    }

    // 슬롯에 파일을 업로드/교체한다. 기존 파일이 있으면 스토리지에서 지우고 메타데이터를 갱신한다.
    @Transactional
    public WorkerDocument upload(Long workerId, DocType docType, MultipartFile file) {
        validateImage(file);
        StorageService.StoredFile stored = storageService.store(workerId, docType.name(), file);

        WorkerDocument doc = documentRepository.findByWorkerIdAndDocType(workerId, docType).orElse(null);
        if (doc == null) {
            doc = new WorkerDocument(workerId, docType, stored.relativePath(), stored.originalName(), stored.size());
        } else {
            storageService.delete(doc.getFilePath()); // 이전 파일 제거
            doc.replace(stored.relativePath(), stored.originalName(), stored.size());
        }
        return documentRepository.save(doc);
    }

    // 슬롯의 서류를 삭제한다(파일 + 레코드)
    @Transactional
    public void delete(Long workerId, DocType docType) {
        documentRepository.findByWorkerIdAndDocType(workerId, docType).ifPresent(doc -> {
            storageService.delete(doc.getFilePath());
            documentRepository.delete(doc);
        });
    }

    // 업로드 파일이 허용 이미지(jpg/png)인지 검증. 아니면 400.
    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일이 비어 있습니다");
        }
        String contentType = file.getContentType();
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        boolean okType = contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase());
        boolean okExt = ext != null && ALLOWED_EXTENSIONS.contains(ext.toLowerCase());
        if (!okType || !okExt) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "지원하지 않는 형식입니다. jpg 또는 png로 변환 후 업로드하세요");
        }
    }
}
