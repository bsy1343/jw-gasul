// WorkerDocument.java — 근로자 서류 사진 엔티티(worker_document, 3.2). 슬롯당 1건, 재업로드 시 교체
package com.jwgasul.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "worker_document")
public class WorkerDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "worker_id", nullable = false)
    private Long workerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 20)
    private DocType docType;

    @Column(name = "file_path", nullable = false, length = 255)
    private String filePath;

    @Column(name = "original_name", length = 255)
    private String originalName;

    @Column(name = "file_size")
    private Long fileSize;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected WorkerDocument() {
    }

    public WorkerDocument(Long workerId, DocType docType, String filePath, String originalName, Long fileSize) {
        this.workerId = workerId;
        this.docType = docType;
        this.filePath = filePath;
        this.originalName = originalName;
        this.fileSize = fileSize;
    }

    // 저장 경로·메타데이터 교체(재업로드 시)
    public void replace(String filePath, String originalName, Long fileSize) {
        this.filePath = filePath;
        this.originalName = originalName;
        this.fileSize = fileSize;
    }

    public Long getId() {
        return id;
    }

    public Long getWorkerId() {
        return workerId;
    }

    public DocType getDocType() {
        return docType;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getOriginalName() {
        return originalName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
