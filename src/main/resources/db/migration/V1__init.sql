-- V1__init.sql — 초기 스키마: 근로자/서류/계좌/계정/현장/명부/감사로그 (PRD 3장)
-- 스키마 jwgasul은 Flyway(create-schemas)가 생성하며, 이 스크립트는 그 안에 테이블을 만든다.

-- ============================================================
-- worker — 근로자 (3.1)
-- ============================================================
CREATE TABLE worker (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    worker_type       VARCHAR(10)  NOT NULL,                 -- FOREIGN / KOREAN
    name_ko           VARCHAR(50)  NOT NULL,                 -- 한국 이름
    name_foreign      VARCHAR(100),                          -- 외국 이름(선택)
    birth_date        DATE         NOT NULL,                 -- 생년월일(시간 없음)
    phone             VARCHAR(20)  NOT NULL,                 -- 휴대폰(숫자만 저장)
    nationality       VARCHAR(50),                           -- 국적
    visa_grade        VARCHAR(20),                           -- 비자등급(자유 입력)
    visa_expire_date  DATE         NOT NULL DEFAULT '9999-12-31', -- 비자 만료일(미상/한국인=9999-12-31)
    edu_complete_date DATE,                                  -- 기초안전보건교육 이수일
    edu_expire_date   DATE,                                  -- 교육 만료일(이수일+유효기간 자동 계산)
    is_fixed          BOOLEAN      NOT NULL DEFAULT FALSE,    -- 고정 인원 여부
    memo              TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ,                           -- soft delete
    CONSTRAINT ck_worker_type CHECK (worker_type IN ('FOREIGN', 'KOREAN'))
);

-- 재입사 대응: 삭제되지 않은 행끼리만 (이름,생년월일,전화) 중복 방지 (3.1)
CREATE UNIQUE INDEX ux_worker_identity
    ON worker (name_ko, birth_date, phone)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_worker_type        ON worker (worker_type);
CREATE INDEX ix_worker_visa_expire ON worker (visa_expire_date);
CREATE INDEX ix_worker_edu_expire  ON worker (edu_expire_date);
CREATE INDEX ix_worker_is_fixed    ON worker (is_fixed);
CREATE INDEX ix_worker_deleted_at  ON worker (deleted_at);

-- ============================================================
-- worker_document — 서류 사진 (3.2)
-- ============================================================
CREATE TABLE worker_document (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    worker_id     BIGINT       NOT NULL REFERENCES worker (id),
    doc_type      VARCHAR(20)  NOT NULL,                     -- ID_FRONT / ID_BACK / EDU_CERT
    file_path     VARCHAR(255) NOT NULL,
    original_name VARCHAR(255),
    file_size     BIGINT,
    uploaded_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_document_type CHECK (doc_type IN ('ID_FRONT', 'ID_BACK', 'EDU_CERT')),
    CONSTRAINT ux_document_slot UNIQUE (worker_id, doc_type)  -- 슬롯당 1건, 재업로드 시 교체
);

CREATE INDEX ix_document_worker ON worker_document (worker_id);

-- ============================================================
-- worker_account — 계좌 (인원당 최대 3개) (3.3)
-- ============================================================
CREATE TABLE worker_account (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    worker_id      BIGINT      NOT NULL REFERENCES worker (id),
    bank_name      VARCHAR(30) NOT NULL,
    account_number VARCHAR(30) NOT NULL,                     -- 하이픈 제외 평문 저장(3.3)
    account_holder VARCHAR(50) NOT NULL,
    is_primary     BOOLEAN     NOT NULL DEFAULT FALSE,
    sort_order     SMALLINT    NOT NULL DEFAULT 1,           -- 표시 순서(1~3)
    memo           VARCHAR(100),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 주계좌는 인원당 1개만 허용 (부분 유니크)
CREATE UNIQUE INDEX ux_account_primary
    ON worker_account (worker_id)
    WHERE is_primary = TRUE;

CREATE INDEX ix_account_worker ON worker_account (worker_id);

-- ============================================================
-- app_user — 계정 (3.4)
-- ============================================================
CREATE TABLE app_user (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username     VARCHAR(50)  NOT NULL UNIQUE,
    password     VARCHAR(100) NOT NULL,                      -- BCrypt
    display_name VARCHAR(50),
    role         VARCHAR(20)  NOT NULL DEFAULT 'ROLE_ADMIN', -- 현재 단일 역할
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE
);

-- ============================================================
-- site — 현장 (3.5)
-- ============================================================
CREATE TABLE site (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    client_name VARCHAR(100),
    address     VARCHAR(255),
    start_date  DATE,
    end_date    DATE,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    memo        TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_site_active ON site (is_active);

-- ============================================================
-- roster / roster_member — 명부 이력 (3.6)
-- ============================================================
CREATE TABLE roster (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    site_id     BIGINT REFERENCES site (id),                 -- 미등록 임시 현장이면 NULL + title 사용
    title       VARCHAR(100) NOT NULL,
    target_date DATE         NOT NULL,
    type        VARCHAR(10)  NOT NULL,                       -- RANDOM / MANUAL
    created_by  VARCHAR(50),                                 -- 로그인 사용자명
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_roster_type CHECK (type IN ('RANDOM', 'MANUAL'))
);

CREATE INDEX ix_roster_site ON roster (site_id);
CREATE INDEX ix_roster_date ON roster (target_date);

CREATE TABLE roster_member (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    roster_id         BIGINT       NOT NULL REFERENCES roster (id) ON DELETE CASCADE,
    worker_id         BIGINT REFERENCES worker (id),         -- 현재 인원 추적용(표시는 스냅샷 사용)
    -- 생성 시점 인적사항 스냅샷 (3.6)
    snap_name_ko      VARCHAR(50)  NOT NULL,
    snap_name_foreign VARCHAR(100),
    snap_phone        VARCHAR(20)  NOT NULL,
    snap_birth_date   DATE         NOT NULL
);

CREATE INDEX ix_roster_member_roster ON roster_member (roster_id);
CREATE INDEX ix_roster_member_worker ON roster_member (worker_id);

-- ============================================================
-- audit_log — 감사 로그 (3.7)
-- ============================================================
CREATE TABLE audit_log (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(50),                               -- 수행자
    entity_type   VARCHAR(30) NOT NULL,                      -- WORKER / DOCUMENT / ACCOUNT / SITE / ROSTER
    entity_id     BIGINT,
    action        VARCHAR(20) NOT NULL,                      -- CREATE / UPDATE / DELETE / VIEW
    changed_field VARCHAR(50),
    old_value     VARCHAR(255),
    new_value     VARCHAR(255),
    ip_address    VARCHAR(45),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_entity  ON audit_log (entity_type, entity_id);
CREATE INDEX ix_audit_created ON audit_log (created_at);
