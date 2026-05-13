# 📋 AI Challenge Backend

본 프로젝트는 **Spring Boot 4.1.0-M4 (WebFlux)** 및 **Spring AI 2.0.0-M4**를 기반으로 구축된 차세대 기업용 AI 어시스턴트 백엔드 시스템입니다. 그룹웨어와 연동되어 사용자의 의도를 실시간으로 분석하고, 비즈니스 워크플로우를 자동화하며, 지식 기반(RAG)의 답변을 제공하는 데 최적화되어 있습니다.

---

## 🚀 핵심 기능 (Key Features)

### 1. 지능형 의도 기반 라우팅 (Intent-Driven Routing)
- **IntentRouter**: 사용자의 자연어 요청을 분석하여 '휴가 신청', '잔업 신청', '이메일 요약', '회의실 예약' 등 10개 이상의 도메인으로 자동 분류합니다.
- **Agent Workflow SOP**: 각 도메인별로 정의된 **표준 운영 절차(SOP)**에 따라 AI 에이전트가 단계별로 작업을 수행하며, 필요 시 사용자에게 추가 정보를 요청하거나 최종 승인을 받습니다.

### 2. 하이브리드 RAG & 지식 관리
- **Knowledge Management**: 로컬 문서 폴더를 실시간으로 감시(`DocumentFolderWatcher`)하여 새로운 정책 문서를 자동으로 인덱싱합니다.
- **Vector Search**: **PostgreSQL + PGVector**를 사용하여 사내 규정 및 지침에 대한 정확한 시맨틱 검색 결과를 제공합니다.

### 3. 실시간 비동기 작업 및 알림
- **SSE (Server-Sent Events)**: 이메일 요약과 같이 시간이 소요되는 AI 작업을 백그라운드에서 처리하고, 완료 시 `SseBroadcaster`를 통해 사용자에게 실시간 결과를 전송합니다.
- **JobRunr**: 예약된 알림(생일 알림, 근태 체크 등) 및 비동기 작업을 안정적으로 관리합니다.

### 4. 강력한 에이전트 도구 (Agentic Tools)
- **MCP (Model Context Protocol)**: 외부 크롤링 엔진 및 그룹웨어 API와 표준 프로토콜을 통해 통신하여 실시간 데이터를 조회하고 액션을 수행합니다.
- **Scheduler Tools**: AI가 직접 일정을 예약하거나 근태 기록을 조회할 수 있는 전용 도구 세트를 제공합니다.

---

## 🛠 기술 스택 (Tech Stack)

| 구분 | 기술 |
| :--- | :--- |
| **Framework** | Spring Boot 4.1.0-M4, Spring WebFlux |
| **AI Engine** | Spring AI 2.0.0-M4, Google Gemini 2.x/3.x (Vertex AI) |
| **Data** | PostgreSQL 16 (PGVector), Redis 7 (Reactive), R2DBC |
| **Migration** | Liquibase |
| **Scheduler** | JobRunr |
| **Protocol** | MCP (Model Context Protocol), SSE, JWT |
| **Monitoring** | Grafana Alloy, OpenTelemetry |

---

## 📂 프로젝트 구조 (Project Structure)

```text
src/main/java/com/brycenkorea/template/
├── config/           # 인프라 설정 (Security, RAG, MCP, IntentRouter 등)
├── contants/         # SOP 정의 및 시스템 상수
├── controller/       # 비액티브/액티브 API 엔드포인트
├── dto/              # 데이터 전송 객체 및 이벤트 정의
├── entity/           # R2DBC 엔티티 (User, Action, ChatRoom 등)
├── event/            # 비동기 이벤트 리스너
├── repository/       # 데이터 액세스 계층 (Redis, R2DBC)
├── service/          # AI 오케스트레이션 및 비즈니스 로직
├── tools/            # AI 에이전트 전용 MCP/Custom 도구
└── util/             # 유틸리티 (JWT, Prompt, FolderWatcher)
```

---

## ⚙️ 시작하기 (Getting Started)

### 1. 사전 요구 사항
- **Java 21**
- **Docker & Docker Compose**
- **Google Gemini API Key** (환경 변수 `GEMINI_API_KEY` 설정 필요)

### 2. 인프라 실행
```bash
# PostgreSQL(PGVector), Redis, Chrome(Browserless) 실행
docker-compose up -d
```

### 3. 애플리케이션 실행
```bash
./gradlew bootRun
```
*기본 포트: 8080 (dev 프로필 활성화 시)*

---

## 💡 개발 가이드

### 새로운 AI 시나리오 추가하기
1. `AgentWorkflowSOP` 상수에 새로운 시나리오의 단계(Step)와 제약 사항(Constraint)을 정의합니다.
2. `IntentRouter`의 `fastMatch` 또는 `initSOPRegistry`에 해당 시나리오를 등록합니다.
3. 필요한 액션이 있다면 `tools/` 패키지에 새로운 MCP 도구 또는 Spring AI `@Tool`을 구현합니다.

### 데이터베이스 마이그레이션
본 프로젝트는 **Liquibase**를 사용하여 DB 스키마를 관리합니다. `src/main/resources/db/changelog/` 폴더에 새로운 XML 변경 로그를 추가하여 스키마를 확장할 수 있습니다.

---

## 📊 모니터링 및 관측성
- **Swagger**: `/swagger-ui.html`에서 API 명세 확인 가능 (Dev 전용)
- **JobRunr Dashboard**: `http://localhost:8002`에서 백그라운드 작업 현황 모니터링
- **Observability**: Grafana Alloy와 OpenTelemetry를 통해 로그, 트레이스, 메트릭을 수집합니다. (환경 설정 필요)

---

## ⚠️ 주의 사항
- **MCP 통신**: MCP 클라이언트는 `http://localhost:8000/mcp`에 실행 중인 외부 서비스(예: Python Crawler)를 참조합니다. 해당 서비스가 구동 중인지 확인하십시오.
- **RAG 문서 경로**: `DocumentFolderWatcher`는 프로젝트 루트의 `ingest/` 폴더를 감시합니다. PDF/Text 문서를 해당 폴더에 넣으면 자동으로 벡터 DB에 반영됩니다.
