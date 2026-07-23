# Claude Code 프로젝트 자동화 구성 생성 지침서

이 문서는 기존 프로젝트의 `CLAUDE.md`와 프로젝트 문서 `docs/PRD.md`를 분석하여,
Claude Code에서 활용할 **커스텀 커맨드(commands)**, **스킬(skills)**, 필요 시 **서브에이전트(agents)**를 자동 생성하는 지침이다.

---

## 목적

프로젝트에 `CLAUDE.md`(에이전트 지침)와 `docs/PRD.md`(요구사항 정의서)가 작성된 상태에서,
반복 작업을 자동화하고 코드 일관성을 보장하기 위해 `.claude/` 하위 구성을 생성한다.

---

## 산출물

```
/project-root
  ├── CLAUDE.md                        # 메인 에이전트 지침 (기존, 섹션 추가)
  ├── /docs/                           # PRD, 설계서 등 프로젝트 문서
  │   └── PRD.md
  ├── /output/                         # 작업 산출물 (검증 결과, 리포트 등)
  └── /.claude/
      ├── /commands/                   # 사용자가 /명령어로 호출하는 워크플로우
      │   └── <command>.md
      ├── /skills/<skill-name>/        # Claude Code가 자동 참조하는 패턴 가이드
      │   ├── SKILL.md
      │   └── /references/             # (선택) 참고 자료
      └── /agents/<agent-name>/        # (필요 시) 독립 역할의 서브에이전트
          └── AGENT.md
```

---

## 프로젝트 단계별 적용

이 지침서는 **프로젝트 진행 단계에 따라 생성 대상이 달라진다.**
처음부터 모든 것을 만들려 하지 말고, 현재 단계에 맞는 것만 생성한다.

| 프로젝트 단계           | 주요 생성 대상            | 이유                                                                |
| ----------------------- | ------------------------- | ------------------------------------------------------------------- |
| **초기** (구조 잡는 중) | Commands 위주             | 코드가 적어 반복 패턴이 아직 없음. 기능 추가/검증 워크플로우가 필요 |
| **중기** (기능 개발 중) | Commands + Skills         | 반복 패턴이 보이기 시작. 일관성 유지를 위한 스킬 생성 시점          |
| **후기** (안정화/확장)  | Skills + Agents (필요 시) | 패턴이 확립됨. 복잡한 역할 분리가 필요할 수 있음                    |

**재실행 권장 시점:**
- 새로운 도메인 영역이 추가되었을 때
- 반복 패턴이 3회 이상 발생했을 때
- 기존 commands/skills로 커버되지 않는 워크플로우가 생겼을 때

---

## 역할 구분

생성 전에 반드시 세 구성의 차이를 이해한다.

| 구분          | Command                        | Skill                           | Agent                                |
| ------------- | ------------------------------ | ------------------------------- | ------------------------------------ |
| **정체**      | 워크플로우 진입점              | 참조 패턴 가이드                | 독립 역할 수행자                     |
| **호출 방식** | 사용자가 `/명령어`로 명시 호출 | Claude Code가 작업 중 자동 참조 | 메인 에이전트(CLAUDE.md)가 위임 호출 |
| **크기**      | 절차 기술 (짧음)               | 패턴 + 템플릿 (중간)            | 역할 + 지침 + 판단 기준 (큼)         |
| **비유**      | 버튼 (누르면 실행)             | 매뉴얼 (필요할 때 펼침)         | 팀원 (일을 맡김)                     |
| **예시**      | `/add-connection`, `/verify-prd` | `factory-service`, `stub-default-pair` | `code-reviewer`, `test-writer` |

---

## 분석 → 생성 절차

### 0단계: 문서 최신화 (사전 동기화)

**분석에 앞서 `CLAUDE.md`와 `docs/PRD.md`가 현재 코드 구현과 일치하는지 확인하고, 불일치가 있으면 먼저 업데이트한다.**
문서가 최신 상태여야 이후 분석(1단계)에서 정확한 판단이 가능하다.

#### 0-1. 변경사항 감지

현재 코드베이스를 스캔하여 문서와의 차이를 식별한다.

| 점검 항목 | 비교 대상 | 확인 방법 |
|-----------|-----------|-----------|
| **기술 스택** | 언어 버전, 의존성, 빌드/린트 도구 | 프로젝트 설정 파일(`pyproject.toml`, `package.json`, `build.gradle` 등) vs 문서 기재 내용 |
| **CLI/실행 인터페이스** | 플래그, 인자, 실행 방법 | 엔트리포인트 소스의 인자 파싱 vs 문서 사용법 |
| **아키텍처** | 모듈 구조, 데이터 흐름, API 엔드포인트 | 소스 디렉토리 구조 vs Architecture 섹션 |
| **데이터 모델** | 필드 추가/변경/삭제 | 모델/엔티티 소스 파일 vs PRD 데이터 모델 섹션 |
| **기능 변경** | 새 기능 추가, 기존 기능 수정/제거 | 소스 코드 vs PRD 기능 요구사항 |
| **설정/상수** | 배치 크기, 동시성, 타임아웃 등 | 각 모듈 상수/설정 vs 문서 기재 값 |
| **커맨드/스킬** | 새로 추가된 커맨드나 스킬 | `.claude/commands/`, `.claude/skills/` vs CLAUDE.md 목록 |
| **자동화 구성** | 기존 commands, skills, agents | `.claude/` 하위 구조 vs CLAUDE.md 목록 |

#### 0-2. 문서 업데이트 실행

불일치가 발견되면 아래 순서로 업데이트한다.

1. **`docs/PRD.md` 먼저 업데이트**
   - 변경된 기능 요구사항, 데이터 모델, 기술 스택을 현재 구현에 맞게 수정
   - 제거된 기능은 삭제하고, 새 기능은 추가
   - `/verify-prd` 커맨드로 PRD vs 구현 불일치를 자동 감지할 수 있음
   - `docs/PRD-REVIEW.md`가 존재하면 설계 이슈 추적 상태도 함께 갱신
2. **`CLAUDE.md` 업데이트**
   - 프로젝트 개요, 아키텍처, 설정/실행 방법, 컨벤션 등 코드 관련 섹션을 현재 구현에 맞게 수정
   - API Endpoints 테이블의 경로가 실제 Controller `@RequestMapping`과 일치하는지 확인
   - Custom Commands, Skills 테이블이 실제 `.claude/` 구성과 일치하는지 확인 및 수정
3. **변경 내역 기록**
   - 업데이트한 항목을 간략히 정리 (이후 6단계 리뷰에서 참조)

#### 0-3. 최신화 건너뛰기 조건

아래 경우에는 0단계를 건너뛰고 바로 1단계로 진행한다.
- 문서가 방금 작성/업데이트되어 코드와 일치하는 것이 확실한 경우
- 사용자가 명시적으로 "문서 최신화 불필요"라고 지정한 경우

---

### 1단계: 프로젝트 분석

**0단계에서 최신화된** `CLAUDE.md`와 `docs/PRD.md`를 읽고 아래 세 관점으로 분석한다.
**현재 프로젝트 단계를 먼저 판단하고, 해당 단계에 맞는 항목을 우선 분석한다.**

#### 1-1. 반복 워크플로우 식별 (→ Commands 후보)

개발자가 **반복적으로 Claude Code에 요청할 작업 시나리오**를 찾는다.
Commands는 프로젝트 초기부터 유용하므로 항상 먼저 분석한다.

| 분석 관점       | 찾을 것                                     |
| --------------- | ------------------------------------------- |
| **기능 추가**   | 새 기능 추가 시 생성해야 할 파일 묶음       |
| **테스트**      | 특정 대상에 대한 테스트 작성                |
| **리팩토링**    | 코드 품질 개선 반복 작업                    |
| **확인/검증**   | 프로젝트 상태 점검 (빌드, TODO, 진행률)     |
| **문서 동기화** | 코드 변경 후 CLAUDE.md/PRD 등 문서 업데이트 |

**생성 기준:**
- 3단계 이상의 절차를 포함하는 워크플로우
- 매번 같은 순서로 진행해야 하는 작업
- 실수하기 쉬운 체크리스트성 작업

**생성하지 않는 것:**
- 일회성 요청 ("이 버그 고쳐줘")
- 단순 질문 ("이 코드 뭐하는 거야?")
- 1단계로 끝나는 작업

#### 1-2. 반복 패턴 식별 (→ Skills 후보)

프로젝트에서 **2회 이상 반복되는 구현 패턴**을 찾는다.
**코드가 충분히 쌓인 후에 의미 있다.** 프로젝트 초기에는 건너뛸 수 있다.

| 분석 관점       | 찾을 것                                                        |
| --------------- | -------------------------------------------------------------- |
| **레이어 패턴** | 같은 구조로 반복 생성되는 계층 (컨트롤러, 서비스, 리포지토리)  |
| **UI 패턴**     | 반복되는 프론트엔드 컴포넌트 구조 (컴포넌트, 페이지, fragment) |
| **데이터 패턴** | DTO, Domain, Mapper 등의 반복 구조                             |
| **테스트 패턴** | 테스트 작성 시 반복되는 설정/보일러플레이트                    |
| **인프라 패턴** | 외부 연동, 설정, 배포의 반복 구조                              |

**생성 기준:**
- 같은 패턴으로 3개 이상의 파일이 이미 존재하거나 향후 추가될 가능성이 높음
- CLAUDE.md에 설명은 있지만 매번 전체를 읽기엔 비효율적인 긴 패턴
- 새 기능 추가 시 기존 코드와 일관성을 맞춰야 하는 컨벤션

**생성하지 않는 것:**
- 프로젝트에서 1회만 사용된 고유 로직
- CLAUDE.md에서 1~2줄로 설명 가능한 단순 규칙
- 이미 완성되어 변경 가능성이 없는 모듈
- 아직 코드가 충분히 없어서 패턴이 확립되지 않은 경우

#### 1-3. 독립 역할 식별 (→ Agents 후보)

**대부분의 프로젝트에서는 agents가 필요 없다.** 아래 조건을 **모두** 만족할 때만 생성을 검토한다.

| 조건                   | 설명                                                           |
| ---------------------- | -------------------------------------------------------------- |
| **독립된 전문 역할**   | 메인 에이전트와 명확히 다른 판단 기준/도메인 지식이 필요       |
| **컨텍스트 분리 이점** | 해당 역할의 지침이 길어서(수백 줄 이상) 항상 로드하면 비효율적 |
| **반복 위임**          | 메인 에이전트가 이 역할에 작업을 여러 번 위임하는 패턴이 존재  |

**Agents가 필요한 전형적 사례:**
- 코드 리뷰 전담 (리뷰 체크리스트, 보안 규칙, 스타일 가이드가 방대)
- 테스트 작성 전담 (테스트 전략, 커버리지 기준, mock 규칙이 복잡)
- 문서 생성 전담 (문서 스타일 가이드, 템플릿이 길고 독립적)
- 배포/운영 전담 (환경별 설정, 배포 절차가 복잡)

**Agents가 필요 없는 경우 (대부분):**
- Skill + Command 조합으로 충분히 처리 가능
- 역할 분리 없이 메인 에이전트가 순차 처리하면 되는 경우
- 지침이 짧아서 CLAUDE.md에 포함해도 부담 없는 경우

---

### 2단계: Commands 생성

식별된 각 워크플로우에 대해 `/.claude/commands/<command>.md`를 생성한다.

#### Command 파일 작성 규칙

```markdown
<워크플로우 설명 — 한 문단으로>

## 입력
$ARGUMENTS — <인자 설명>

## 절차

1. <구체적 단계>
2. <구체적 단계>
   - 관련 스킬이 있으면: `.claude/skills/<스킬명>/SKILL.md`를 참조하여 수행
3. ...
n. 검증: <빌드/테스트/결과 확인>

## 참조
- CLAUDE.md
- docs/PRD.md (필요 시)
- .claude/skills/<관련 스킬>/SKILL.md

## 산출물
작업 결과를 `/output/` 에 저장한다 (해당하는 경우):
- 검증 결과 리포트
- 분석 결과 요약
- 생성된 파일 목록

## 완료 보고
작업 결과를 요약하여 보고한다:
- 생성/수정된 파일 목록
- 검증 결과 (성공/실패)
- 주의사항 (있으면)
```

**네이밍 규칙:**
- lowercase-kebab-case: `add-feature`, `verify-prd`
- 동사-명사 형식: `add-<대상>`, `verify-<대상>`, `update-<대상>`
- 약어 지양: `add-connection` (O) / `add-conn` (X)

**인자 패턴:**
- 엔티티/기능명: `/add-feature payment-refund`
- 파일 경로: `/analyze-code src/service/impl/`
- 타입 지정: `/add-connection scm` (scm|ci|registry|cluster|sonarqube|notification)
- 복합: `/add-service-impl gitlab scm` (vendor + type)

**작성 원칙:**
- **`$ARGUMENTS` 활용**: 사용자가 대상을 유연하게 지정할 수 있게
- **스킬 참조**: 커맨드 안에 패턴 전체를 넣지 말고 스킬을 참조
- **검증 단계 필수**: 마지막에 반드시 빌드/컴파일/테스트 확인 포함
- **산출물 저장**: 검증 결과, 리포트 등 추적이 필요한 결과물은 `/output/`에 파일로 남김
- **완료 보고**: 무엇을 했는지 요약하는 단계 포함

#### `/output/` 활용 가이드

커맨드 실행 결과 중 파일로 남길 가치가 있는 것은 `/output/`에 저장한다.

| 산출물 유형     | 파일명 패턴 예시                   | 설명                                   |
| --------------- | ---------------------------------- | -------------------------------------- |
| **검증 결과**   | `output/verify-<대상>-<날짜>.md`   | 빌드/테스트/코드 검증 결과             |
| **분석 리포트** | `output/analysis-<주제>-<날짜>.md` | 코드 분석, 진행률 점검 결과            |
| **변경 이력**   | `output/changelog-<날짜>.md`       | 대규모 리팩토링/마이그레이션 변경 내역 |
| **비교 결과**   | `output/diff-<대상>-<날짜>.md`     | PRD 대비 구현 차이, 코드 비교 결과     |

모든 커맨드가 산출물을 만들 필요는 없다. 단순 코드 생성 커맨드는 완료 보고만으로 충분하다.

**주의:** `/output/` 디렉토리는 `.gitignore`에 추가한다 (커밋 대상이 아닌 임시 산출물).

---

### 3단계: Skills 생성

식별된 각 패턴에 대해 `/.claude/skills/<skill-name>/SKILL.md`를 생성한다.
**프로젝트 초기에 패턴이 아직 없으면 이 단계는 건너뛴다.**

#### SKILL.md 작성 규칙

```markdown
# <스킬명>

## 언제 참조하는가
- 트리거 조건을 구체적으로 기술
- 예: "새 도메인 영역의 컨트롤러를 추가할 때"

## 패턴 구조
- 생성해야 할 파일 목록과 위치
- 각 파일의 역할과 관계

## 구현 규칙
- 이 패턴에서 반드시 지켜야 할 규칙
- 네이밍 컨벤션
- 의존성, 임포트, 어노테이션 규칙

## 코드 템플릿
- 프로젝트 실제 코드에서 추출한 코드 스니펫
- 복사 후 이름만 바꾸면 동작하는 수준의 구체성
- 기존 코드의 참조 파일 경로 명시 (예: "참조 구현: src/.../UserController.*")

## 참조 구현 (필수)
| 역할 | 파일 경로 |
|------|----------|
| 대표 구현 | `src/.../ExampleService.java` |
| 테스트 | `src/test/.../ExampleServiceTest.java` |

## 체크리스트
- [ ] 파일 위치가 기존 패턴과 일치하는가
- [ ] 네이밍이 프로젝트 컨벤션을 따르는가
- [ ] 필수 어노테이션/설정이 빠지지 않았는가
- [ ] 빌드가 통과하는가
```

**작성 원칙:**
- **프로젝트 실제 코드에서 추출**: 추상적 설명이 아니라 현재 프로젝트의 실제 파일을 기반으로
- **복사-수정 가능한 수준**: 새 기능 추가 시 기존 코드를 복사해서 이름만 바꾸면 되는 구체성
- **CLAUDE.md와 중복 최소화**: CLAUDE.md의 내용은 참조만 하고, 스킬에는 구현 패턴에 집중
- **참조 파일 경로 명시**: "이 패턴의 참조 구현: `src/.../UserController.*`" 형태로 실제 파일을 가리킴

---

### 4단계: Agents 생성 (필요한 경우만)

1단계 분석에서 agents가 필요하다고 판단된 경우에만 수행한다.

#### AGENT.md 작성 규칙

```markdown
# <에이전트명>

## 역할
이 에이전트의 책임과 전문 영역을 명확히 기술한다.

## 트리거 조건
메인 에이전트(CLAUDE.md)가 이 에이전트에 작업을 위임하는 조건.
- 조건 1
- 조건 2

## 입력
이 에이전트가 받는 입력 (파일 경로, 지시 내용 등)

## 출력
이 에이전트가 반환하는 결과물 형식

## 판단 기준
이 에이전트가 독립적으로 내리는 판단과 그 기준.
메인 에이전트와 다른 관점/기준이 있다면 명시.

## 참조 스킬
작업 수행 시 참조할 스킬 목록.
- .claude/skills/<스킬명>/SKILL.md

## 제약
- 이 에이전트가 하지 않는 것
- 에스컬레이션 조건 (판단 불확실 시 메인에 반환)
```

**작성 원칙:**
- **메인과의 관계 명확화**: CLAUDE.md에 이 에이전트를 언제/어떻게 호출하는지 기술
- **독립 판단 영역 명시**: 이 에이전트만의 판단 기준이 없으면 agent로 분리할 이유 없음
- **스킬 재사용**: agent 전용 로직 외에는 공용 스킬을 참조

---

### 5단계: CLAUDE.md 업데이트

생성된 모든 구성을 CLAUDE.md에 반영한다. 기존 내용은 수정하지 않고 **섹션을 추가**한다.
생성하지 않은 항목의 섹션은 추가하지 않는다.

**삽입 위치:** `## 참조 문서` 섹션 바로 위에 추가한다. 기존 내용의 중간에 삽입하지 않는다.

```markdown
## Custom Commands

| 커맨드  | 용도   | 사용법                |
| ------- | ------ | --------------------- |
| /<name> | <설명> | `/<name> <인자 설명>` |

## Skills

| 스킬   | 트리거      | 위치                             |
| ------ | ----------- | -------------------------------- |
| <name> | <언제 참조> | `.claude/skills/<name>/SKILL.md` |

## Sub-Agents (해당 시에만 추가)

| 에이전트 | 역할   | 트리거      |
| -------- | ------ | ----------- |
| <name>   | <설명> | <언제 위임> |
```

---

### 6단계: 리뷰

생성된 전체 구조를 아래 형식으로 요약하고, 수정/추가할 부분이 있는지 확인한다.

```
## 생성 결과 요약

### 문서 최신화 (0단계)
- CLAUDE.md: <변경 있음/변경 없음> — <변경 내역 요약>
- PRD.md: <변경 있음/변경 없음> — <변경 내역 요약>

### 프로젝트 단계 판단
- 현재 단계: <초기/중기/후기>
- 판단 근거: <코드량, 반복 패턴 유무 등>

### Commands (N개)
- /<command>: <한 줄 설명>

### Skills (N개 또는 "현재 단계에서 해당 없음")
- <skill-name>: <한 줄 설명>

### Agents (N개 또는 "해당 없음")
- <agent-name>: <한 줄 설명>

### CLAUDE.md 변경사항
- <추가된 섹션>

수정하거나 추가할 부분이 있나요?
```

---

## 품질 기준

### Commands

- [ ] 3단계 이상의 절차가 있는가?
- [ ] `$ARGUMENTS`로 대상을 유연하게 지정할 수 있는가?
- [ ] 관련 스킬을 적절히 참조하는가? (스킬이 존재하는 경우)
- [ ] 검증 + 완료 보고 단계가 있는가?
- [ ] 산출물이 필요한 커맨드는 `/output/` 저장이 명시되어 있는가?

### Skills

- [ ] 프로젝트 실제 코드에서 패턴을 추출했는가?
- [ ] 이 스킬만 읽으면 일관된 코드를 생성할 수 있는가?
- [ ] CLAUDE.md와 불필요한 중복이 없는가?
- [ ] 코드 템플릿이 복사-수정 가능한 구체성인가?
- [ ] 참조 파일 경로가 실제로 존재하는가?

### Agents

- [ ] Skill + Command로는 부족한 명확한 이유가 있는가?
- [ ] 메인 에이전트와 다른 독립 판단 기준이 존재하는가?
- [ ] 지침이 충분히 길어서 분리할 가치가 있는가? (수백 줄 이상)
- [ ] CLAUDE.md에 호출 조건과 방법이 등록되었는가?

### 전체

- [ ] command, skill, agent의 역할이 명확히 분리되어 있는가?
- [ ] 불필요하게 많은 파일을 생성하지 않았는가? (실제 사용될 것만)
- [ ] CLAUDE.md에 생성된 전체 목록이 반영되었는가?
- [ ] 현재 프로젝트 단계에 맞는 것만 생성했는가?

---

## 유지보수

### 업데이트 트리거
- 참조 패턴 파일이 구조적으로 변경되었을 때 → Skill 갱신
- 워크플로우 절차가 바뀌었을 때 → Command 갱신
- 새 반복 패턴이 3회 이상 출현했을 때 → Skill 신규 생성 검토

### 삭제 기준
- 3개월 이상 미사용 command/skill
- 참조하던 패턴이 프로젝트에서 완전히 제거됨
- 다른 command/skill로 통합됨

### 재실행 시점
- 새로운 도메인 영역 추가 후
- 대규모 리팩토링/마이그레이션 완료 후
- 기존 자동화로 커버 안 되는 워크플로우 발견 시
- PRD 설계 이슈 일괄 해결 등 대규모 문서·코드 동기화 이후

---

## 주의사항

- **과도한 생성 금지**: 실제 사용 빈도가 낮은 것은 만들지 않는다
- **점진적 확장**: 처음에는 핵심 3~5개만 만들고, 필요에 따라 추가한다
- **단계에 맞게**: 프로젝트 초기에 Skills를 억지로 만들지 않는다. 패턴이 보일 때 만든다
- **Agents는 최후 수단**: 대부분의 프로젝트에서 Skills + Commands만으로 충분하다
- **프로젝트 맥락 우선**: 일반적 베스트 프랙티스보다 현재 프로젝트의 실제 패턴을 우선한다
- **스킬 내 코드는 실제 코드 기반**: 가상의 예시가 아니라 프로젝트의 기존 코드에서 추출한다
- **기존 CLAUDE.md 보존**: 기존 내용은 수정하지 않고 섹션만 추가한다
- **글로벌 설정 공존**: 프로젝트 `.claude/` > 사용자 `~/.claude/` > 시스템 기본값 순으로 우선. 동일 이름 시 프로젝트 레벨이 우선한다

---

## 부록: 현재 자동화 현황 (2026-04-15 기준)

> **프로젝트 단계**: 완성 (Java 158개, HTML 58개, 구현 완성도 100%)
> **자동화 커버리지**: Commands 7개 + Skills 6개, Agents 없음
> **구현 현황**: 전체 21개 기능 완료 (High 5개, Mid 11개, Low 5개)

### 최근 주요 변경 (2026-04-15)

**Harness Engineering Phase 1-3 완료 (2026-04-14~15):**

**Phase 1 - 문서 구조 재편성:**
- CLAUDE.md 슬림화 (~100줄 목차형)
- docs/ 구조화: ARCHITECTURE.md, CONVENTIONS.md, FRONTEND.md, SECURITY.md, QUALITY_SCORE.md 신규
- design-docs/, exec-plans/, references/ 디렉토리 체계 구축

**Phase 2 - 아키텍처 강제:**
- ArchUnit 1.3.0 의존성 추가 + LayerDependencyTest (5개 테스트) + NamingConventionTest (8개 테스트)
- H-1 해결: Controller→Repository 직접 호출 147건 리팩토링
  - 5개 Service 신규 생성 (ProjectQueryService, DeployQueryService, PlatformSettingService, ConfigEntryService, UserActivityQueryService)
  - DomainService에 `findBySystemIdAndName()`, UserService에 `isDomainLeader()` 추가
  - 13개 Controller 전체 리팩토링 (Repository 직접 주입 → Service 주입)
- H-2 해결: NamingConventionTest에 합리적 예외 규칙 추가 (Helper, Registry, Advice, DTO 서브패키지)

**Phase 3 - 지속적 관리 체계:**
- 신규 Commands: `/doc-gardening`, `/quality-check`, `/arch-validate`
- 신규 Skill: `layer-architecture`
- tech-debt-tracker.md 생성 및 갱신

---

### 이전 변경 (2026-04-13)

**Low 우선순위 기능 완료 (100% 달성):**

**Low #20 - Cluster Metrics (클러스터 메트릭 수집):**
- K8s: `kubectl top nodes/pods` 실행 및 파싱 (CPU/Memory 사용률)
- VM: SSH로 `top`, `docker stats` 실행 및 파싱
- Stub 지원: test 프로필에서 Mock 데이터 반환
- 신규 파일: `ClusterMetricsService.java`, `ClusterMetricsDto.java`, `clusters/metrics.html`
- 수정 파일: `AdminController.java`, `clusters/list.html`

**Low #21 - API Documentation (Swagger UI):**
- SpringDoc OpenAPI 2.7.0 통합
- Swagger UI: `/swagger-ui.html` (공개 접근)
- OpenAPI JSON: `/v3/api-docs`
- 예시 REST API 3개: SystemApiController, ProjectApiController, HealthApiController
- 신규 파일: `OpenApiConfig.java`, `api/SystemApiController.java`, `api/ProjectApiController.java`, `api/HealthApiController.java`
- 수정 파일: `pom.xml`, `application.yml`, `SecurityConfig.java`

**CI·CD 파이프라인 중심 UI 재설계 (2026-04-09):**
- 프로젝트 상세(`/projects/{id}`)에서 CI·CD 탭 제거 → 단일 뷰(기본정보+연동정보)로 단순화
- CI·CD 메뉴(`/cicd`)에 프로젝트별 통합 파이프라인 뷰(`/cicd/pipeline/{id}`) 신규 추가
- CI·CD 사이드바에 시스템/도메인/프로젝트 트리 추가 (프로젝트 클릭 → 파이프라인 뷰)
- DeployController 리다이렉트 대상 `/cicd/pipeline/{id}`로 변경
- 변경 파일: `ProjectController`, `detail.html`, `DeployController`, `CiCdController`, `sidebar-cicd.html`, `SidebarController` (수정 6개 + 신규 `pipeline.html` 1개)

### Commands (7개)

| 커맨드 | 용도 | 인자 |
|--------|------|------|
| `/add-connection` | 새 Connection 엔티티 + CRUD 전체 생성 | `$ARGUMENTS` = 연동 유형 (scm\|ci\|registry\|cluster\|sonarqube\|notification) |
| `/add-service-impl` | Factory 라우팅 서비스 구현체 추가 | `$ARGUMENTS` = vendor type (예: `gitlab scm`) |
| `/add-wizard-step` | 시스템 생성 Wizard 단계 추가/수정 | `$ARGUMENTS` = step번호 이름 (예: `10 배포전략`) |
| `/verify-prd` | PRD vs 실제 구현 비교 검증 | 인자 없음 |
| `/doc-gardening` | 문서-코드 일치성 검증 및 갱신 | 인자 없음 |
| `/quality-check` | 품질 점수 갱신 및 기술부채 추적 | 인자 없음 |
| `/arch-validate` | ArchUnit 아키텍처 테스트 실행 및 결과 분석 | 인자 없음 |

### Skills (6개)

| 스킬 | 트리거 | 참조 구현 |
|------|--------|----------|
| `connection-entity` | 새 Connection 유형 엔티티 추가 시 | `ScmConnection.java`, `IntegrationService.java` |
| `factory-service` | Factory 라우팅 서비스 구현체 추가 시 | `ScmServiceFactory.java`, `GitLabScmService.java` |
| `stub-default-pair` | Stub/Default 구현체 쌍 생성 시 | `StubScmService.java`, `DefaultScmService.java` |
| `wizard-step` | Wizard 단계 추가/수정 시 | `SystemWizardController.java`, `step1.html` |
| `admin-crud-ui` | Admin 설정 CRUD UI 섹션 추가 시 | `AdminSettingsController.java`, `admin-settings.html` |
| `layer-architecture` | Controller/Service/Repository 추가 시 | `LayerDependencyTest.java`, `NamingConventionTest.java` |

### 향후 검토 가능 항목

| 후보 | 유형 | 조건 |
|------|------|------|
| E2E 플로우 검증 커맨드 | Command | E2E 테스트 패턴 확립 후 |
| 배포 파이프라인 디버그 | Command | CI/CD 실 연동 시작 후 |
| 환경별 설정 패턴 | Skill | 환경 프리셋별 설정 파일 3개 이상 생성 시 |
