#!/usr/bin/env bash
# backup.sh — jw-gasul 로컬 백업(PRD 6): PostgreSQL 덤프 + 업로드 디렉터리 아카이브.
#   홈서버 로컬에만 보관하고 외부 클라우드로 반출하지 않는다.
#   ⚠️ 계좌번호는 평문 저장 → 덤프 유출이 유일한 실질 위험. 외부 반출 시 gpg 암호화 필수.
#
# 사용:
#   DB_URL, DB_USERNAME, DB_PASSWORD (운영 prod 값) + 선택 UPLOAD_DIR, BACKUP_DIR, RETENTION_DAYS
#   예) BACKUP_DIR=/Volumes/backup/jw-gasul ./scripts/backup.sh
#
# cron 예(매일 03:00, 다른 물리 디스크로):
#   0 3 * * * BACKUP_DIR=/Volumes/backup/jw-gasul DB_URL=jdbc:postgresql://... \
#             DB_USERNAME=... DB_PASSWORD=... /path/to/scripts/backup.sh >> /var/log/jw-backup.log 2>&1
set -euo pipefail

# ── 설정 ──────────────────────────────────────────────
BACKUP_DIR="${BACKUP_DIR:-./backups}"
UPLOAD_DIR="${UPLOAD_DIR:-./data/uploads}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
STAMP="$(date +%Y%m%d-%H%M%S)"

# jdbc:postgresql://host:port/db → host/port/db 파싱(PG* 환경변수가 있으면 그것을 우선)
if [[ -n "${DB_URL:-}" ]]; then
  _u="${DB_URL#jdbc:postgresql://}"
  _hostport="${_u%%/*}"
  _db="${_u##*/}"; _db="${_db%%\?*}"
  PGHOST="${PGHOST:-${_hostport%%:*}}"
  _p="${_hostport##*:}"; [[ "$_p" == "$_hostport" ]] && _p=5432
  PGPORT="${PGPORT:-$_p}"
  PGDATABASE="${PGDATABASE:-$_db}"
fi
export PGHOST="${PGHOST:-localhost}"
export PGPORT="${PGPORT:-5432}"
export PGDATABASE="${PGDATABASE:-jwgasul}"
export PGUSER="${PGUSER:-${DB_USERNAME:-jwgasul}}"
export PGPASSWORD="${PGPASSWORD:-${DB_PASSWORD:-}}"

mkdir -p "$BACKUP_DIR"

# ── 1) DB 덤프 (gzip) ─────────────────────────────────
DUMP_FILE="$BACKUP_DIR/db-$STAMP.sql.gz"
echo "[backup] pg_dump $PGDATABASE@$PGHOST:$PGPORT → $DUMP_FILE"
pg_dump --no-owner --no-privileges | gzip > "$DUMP_FILE"
echo "[backup] DB 덤프 완료 ($(du -h "$DUMP_FILE" | cut -f1))"

# ── 2) 업로드 디렉터리 아카이브 ────────────────────────
if [[ -d "$UPLOAD_DIR" ]]; then
  UP_FILE="$BACKUP_DIR/uploads-$STAMP.tar.gz"
  echo "[backup] 업로드 아카이브 $UPLOAD_DIR → $UP_FILE"
  tar -czf "$UP_FILE" -C "$(dirname "$UPLOAD_DIR")" "$(basename "$UPLOAD_DIR")"
  echo "[backup] 업로드 아카이브 완료 ($(du -h "$UP_FILE" | cut -f1))"
else
  echo "[backup] 업로드 디렉터리 없음($UPLOAD_DIR) — 스킵"
fi

# ── 3) 보관정리(로컬 전용, 기본 30일) ─────────────────
echo "[backup] $RETENTION_DAYS일 경과 백업 정리"
find "$BACKUP_DIR" -maxdepth 1 -type f \( -name 'db-*.sql.gz' -o -name 'uploads-*.tar.gz' \) \
  -mtime +"$RETENTION_DAYS" -print -delete || true

echo "[backup] 완료. 외부 반출 금지(반출 시 gpg 암호화 필수)."
