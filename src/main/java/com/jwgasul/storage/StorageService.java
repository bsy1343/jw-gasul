// StorageService.java — 업로드 파일 저장 추상화. 외부 시스템(파일시스템) 격리(SCAFFOLD).
// 현재 구현은 로컬 파일시스템(LocalFileStorageService). 향후 오브젝트 스토리지 등으로 교체 가능.
package com.jwgasul.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    // 저장 결과 메타데이터(웹 루트 외부의 상대 저장 경로 포함)
    record StoredFile(String relativePath, String originalName, long size) {
    }

    // 파일을 지정 하위 경로 규칙으로 저장하고 메타데이터를 반환한다
    StoredFile store(Long workerId, String slot, MultipartFile file);

    // 상대 경로로 저장된 파일을 스트리밍용 Resource로 로드한다(경로 트래버설 차단)
    Resource loadAsResource(String relativePath);

    // 상대 경로의 파일을 삭제한다(없어도 예외 없음)
    void delete(String relativePath);
}
