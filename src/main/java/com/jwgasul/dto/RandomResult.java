// RandomResult.java — 랜덤 명부 생성 결과(선택된 근로자 + 경고 메시지)(F-06)
package com.jwgasul.dto;

import com.jwgasul.domain.Worker;
import java.util.List;

public record RandomResult(List<Worker> selected, String warning) {
}
