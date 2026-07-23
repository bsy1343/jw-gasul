// ImportResult.java — 근로자 엑셀 일괄등록 결과. 생성/중복스킵 건수 + 행별 오류 메시지.
package com.jwgasul.dto;

import java.util.List;

public record ImportResult(
        int totalRows,   // 처리한 데이터 행 수(헤더 제외)
        int created,     // 신규 등록된 근로자 수
        int skipped,     // 중복 등으로 건너뛴 수
        List<String> errors // 행별 오류/경고 메시지(사람이 읽는 문구)
) {
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
}
