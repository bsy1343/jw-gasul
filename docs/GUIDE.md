# Claude Code 프로젝트 워크플로우 가이드

## 초기 셋업

### 1. 프로젝트 생성 + 공용 문서 복사

```
mkdir my-project && cd my-project
docs/ 에 아래 3개 파일 복사 (프로젝트 간 내용 변경 없이 재사용)
  ├── SCAFFOLD.md
  ├── AUTOMATION.md
  └── GUIDE.md
```

### 2. PRD.md 작성

```
docs/SCAFFOLD.md 지침에 따라 PRD.md를 작성해줘
```
→ 프로젝트 설명 → Claude Code가 질문 → 답변 → PRD.md 초안 생성 → 리뷰

심층 보강이 필요하면:
```
docs/SCAFFOLD.md의 심층 인터뷰 모드에 따라 docs/PRD.md를 심층 인터뷰해줘
```

### 3. 프로젝트 기본 구조 생성

```
docs/PRD.md를 읽고 프로젝트 기본 구조를 생성해줘
```

### 4. CLAUDE.md 생성

```
/init
```

보강이 필요하면:
```
docs/SCAFFOLD.md의 심층 인터뷰 모드에 따라 CLAUDE.md를 심층 인터뷰해줘
```

### 5. .claude/ 자동화 구성 생성

```
docs/AUTOMATION.md 지침에 따라 분석하고 생성해줘
```
→ commands, skills, (필요 시) agents 자동 생성

---

## 개발 중 유지보수

| 상황 | 할 일 | 명령어 예시 |
|------|-------|-----------|
| 요건 변경 | `docs/PRD.md` 업데이트 | `docs/PRD.md에 ○○ 기능을 추가해줘` |
| 구조/패턴 변경 | `CLAUDE.md` 업데이트 | `CLAUDE.md에 ○○ 패턴을 반영해줘` |
| 새 반복 패턴 발생 | `AUTOMATION.md` 재실행 | `docs/AUTOMATION.md 지침에 따라 재분석해줘` |
| 문서 보강 필요 | 심층 인터뷰 | `docs/SCAFFOLD.md의 심층 인터뷰 모드에 따라 ○○를 인터뷰해줘` |

---

## AUTOMATION.md 재실행 시점

- 새로운 도메인 영역이 추가되었을 때
- 같은 패턴의 코드를 3회 이상 반복 작성했을 때
- 기존 commands/skills로 커버 안 되는 워크플로우가 생겼을 때

---

## 파일 역할

| 파일 | 역할 | 프로젝트별 작성 |
|------|------|:--------------:|
| `docs/SCAFFOLD.md` | PRD.md, CLAUDE.md 작성 지침 | 공용 (복사) |
| `docs/AUTOMATION.md` | .claude/ 자동 생성 지침 | 공용 (복사) |
| `docs/GUIDE.md` | 이 문서 (워크플로우 가이드) | 공용 (복사) |
| `docs/PRD.md` | 요구사항 정의서 | **매번 새로 작성** |
| `CLAUDE.md` | Claude Code 메인 에이전트 지침 | **매번 새로 생성** |
| `.claude/commands/` | 커스텀 슬래시 커맨드 | 자동 생성 |
| `.claude/skills/` | 반복 패턴 가이드 | 자동 생성 |
| `.claude/agents/` | 서브에이전트 (필요 시) | 자동 생성 |
| `/output/` | 작업 산출물 (검증 결과, 리포트) | 자동 생성 |

---

## 빠른 참조: 주요 명령어

| 목적 | 명령어 |
|------|--------|
| PRD 작성 | `docs/SCAFFOLD.md 지침에 따라 PRD.md를 작성해줘` |
| PRD 심층 인터뷰 | `docs/SCAFFOLD.md의 심층 인터뷰 모드에 따라 docs/PRD.md를 심층 인터뷰해줘` |
| 프로젝트 구조 생성 | `docs/PRD.md를 읽고 프로젝트 기본 구조를 생성해줘` |
| CLAUDE.md 생성 | `/init` |
| CLAUDE.md 보강 | `docs/SCAFFOLD.md의 심층 인터뷰 모드에 따라 CLAUDE.md를 심층 인터뷰해줘` |
| 자동화 구성 생성 | `docs/AUTOMATION.md 지침에 따라 분석하고 생성해줘` |
| 자동화 구성 재실행 | `docs/AUTOMATION.md 지침에 따라 재분석해줘` |