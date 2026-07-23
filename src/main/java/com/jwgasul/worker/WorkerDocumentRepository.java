// WorkerDocumentRepository.java — 근로자 서류 사진 조회 리포지토리(3.2)
package com.jwgasul.worker;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerDocumentRepository extends JpaRepository<WorkerDocument, Long> {

    // 근로자의 전체 서류
    List<WorkerDocument> findByWorkerId(Long workerId);

    // 슬롯(유형)별 서류 — 재업로드 시 교체 판단에 사용
    Optional<WorkerDocument> findByWorkerIdAndDocType(Long workerId, DocType docType);
}
