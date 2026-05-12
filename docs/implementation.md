# Hoseo LENS 챗봇 구현 문서

## 목차
1. [프로젝트 개요](#프로젝트-개요)
2. [기술 스택](#기술-스택)
3. [전체 구조](#전체-구조)
4. [기능 목록](#기능-목록)
5. [구현 상세](#구현-상세)
6. [API 명세](#api-명세)
7. [데이터베이스 설계](#데이터베이스-설계)
8. [프론트엔드](#프론트엔드)
9. [환경 설정](#환경-설정)

---

## 프로젝트 개요

호서대학교 학생을 위한 AI 기반 챗봇 서비스. RAG(Retrieval-Augmented Generation)로 학교 관련 질문에 실시간 스트리밍 답변을 제공하며, FAQ 관리·채팅 이력·공지 이벤트 수신 기능을 포함한다.

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| 프레임워크 | Spring Boot 4.0.3 |
| 언어 | Java 17 |
| 빌드 | Gradle (Kotlin DSL) |
| DB (개발) | H2 In-Memory |
| DB (운영) | MySQL 8 |
| ORM | Spring Data JPA / Hibernate |
| 비동기 | Spring WebFlux (WebClient + Reactor) |
| 스트리밍 | SSE (Server-Sent Events) |
| 프론트엔드 | Vanilla HTML5 / CSS3 / JavaScript |

---

## 전체 구조

```
hoseo-chatbot/
├── src/main/java/com/hoseo/chatbot/
│   ├── entity/          # JPA 엔티티
│   ├── repository/      # Spring Data 레포지토리
│   ├── service/         # 비즈니스 로직 인터페이스
│   │   └── impl/        # 구현체
│   ├── controller/      # REST 컨트롤러
│   ├── dto/             # 요청/응답 DTO
│   └── config/          # 초기 데이터 설정
└── src/main/resources/
    ├── application.properties
    ├── application-local.properties   # H2
    ├── application-dev.properties     # MySQL
    └── static/test.html               # 프론트엔드
```

---

## 기능 목록

### 1. RAG 기반 채팅 (스트리밍)
- 사용자 질문을 외부 RAG 서버로 전달
- 답변을 단어 단위로 쪼개 SSE로 실시간 스트리밍
- 답변과 함께 참고 출처(sources) 반환
- 채팅방 자동 생성 및 메시지 DB 저장

### 2. FAQ 관리
- FAQ CRUD (관리자 전용 — X-Admin-Key 헤더 인증)
- 카테고리별 조회
- 클릭 수 기반 TOP5 / TOP10 인기 FAQ 조회
- 소프트 삭제 (isActive 플래그)

### 3. 채팅 이력
- 사용자별 채팅방 목록 조회 (최신순)
- 채팅방 내 전체 메시지 조회
- 채팅방 삭제 (메시지 포함 cascade)
- deviceId로 소유권 검증

### 4. 공지 이벤트 수신
- AI 크롤링 서버로부터 신규 공지 이벤트 수신
- noticeId 또는 (제목 + 날짜) 조합으로 중복 방지
- 향후 FCM 키워드 푸시 알림 연동 예정

---

## 구현 상세

### 채팅 스트리밍 (`ChatServiceImpl`)

```
질문 수신
  → userId로 User 조회/생성
  → sessionId로 ChatRoom 조회/생성 (타이틀 = 첫 질문 앞 30자)
  → USER 메시지 저장
  → WebClient로 RAG 서버 POST /ask 호출
  → 응답(answer + sources) 파싱
  → answer를 공백 기준으로 토크나이즈
  → Flux.delayElements(30ms)로 단어 하나씩 SSE 전송
  → 마지막에 sources 이벤트 전송
  → ASSISTANT 메시지 저장
  → [DONE] 이벤트 전송
```

- `SseEmitter` + `Flux`를 subscribe()로 연결해 비동기 스트리밍 구현
- 타임아웃 60초
- 에러 발생 시 `event: error` 이벤트로 클라이언트에 알림

### FAQ 조회/클릭 (`FaqServiceImpl`)

- 전체 조회: `isActive = true` + `sortOrder ASC`
- 카테고리 필터: 동일 조건에 `WHERE category = ?` 추가
- TOP N: `ORDER BY view_count DESC LIMIT ?`
- 클릭: `viewCount++` 후 저장 (트랜잭션)
- 관리자 인증: 컨트롤러에서 `X-Admin-Key` 헤더를 `admin.api-key` 프로퍼티와 비교

### 채팅 이력 조회 (`HistoryServiceImpl`)

- 채팅방 목록: 해당 deviceId의 User → ChatRoom 리스트, `updatedAt DESC`
- 메시지 목록: `chatRoomId` + `createdAt ASC`, **소유권 검증** 후 반환
- 삭제: 메시지 먼저 삭제 후 채팅방 삭제 (FK 제약 충족)
- 역할(role) 값은 프론트 소문자 규격에 맞게 lowercase 변환

### 공지 이벤트 처리 (`NoticeEventServiceImpl`)

```
이벤트 수신 (X-API-Key 인증)
  → items 순회
    → noticeId 존재 && blank 아님 → noticeId로 중복 확인
    → noticeId 없음             → (title + date)로 중복 확인
    → 신규: NoticeEntity 저장
    → 중복: 스킵
  → 처리된 건수 반환
```

---

## API 명세

### 채팅

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | `/api/chat/ask` | 질문 전송 (SSE 스트리밍 응답) | 없음 |

**요청 바디**
```json
{
  "userId": "device-id-string",
  "sessionId": "session-timestamp",
  "question": "수강신청은 어떻게 하나요?"
}
```

**SSE 이벤트 흐름**
```
data: {"chunk": "수강신청은"}
data: {"chunk": " 매 학기"}
...
data: {"sources": [{"title": "2026 수강신청 안내", "url": "..."}]}
data: [DONE]
```

---

### FAQ

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| GET | `/api/faq` | FAQ 목록 (`?category=` 선택) | 없음 |
| GET | `/api/faq/top` | 인기 FAQ (`?limit=5` 또는 `10`) | 없음 |
| POST | `/api/faq` | FAQ 생성 | X-Admin-Key |
| PUT | `/api/faq/{id}` | FAQ 수정 | X-Admin-Key |
| DELETE | `/api/faq/{id}` | FAQ 소프트 삭제 | X-Admin-Key |
| POST | `/api/faq/{id}/click` | 클릭 수 증가 | 없음 |

---

### 채팅 이력

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| GET | `/api/history/{deviceId}` | 채팅방 목록 | 없음 |
| GET | `/api/history/{deviceId}/{chatRoomId}` | 채팅 메시지 목록 | 없음 |
| DELETE | `/api/history/{chatRoomId}` | 채팅방 삭제 | 없음 |

---

### 공지 이벤트

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | `/api/notices/new` | 신규 공지 이벤트 수신 | X-API-Key |

**요청 바디**
```json
{
  "source": "ai_incremental_updater",
  "generated_at": "2026-05-09T15:30:00Z",
  "count": 1,
  "items": [
    {
      "notice_id": "96860",
      "title": "2026 봄학기 수강신청 안내",
      "date": "2026-05-09",
      "url": "https://hoseo.ac.kr/...",
      "category": "학사공지",
      "major_category": "학사",
      "target": "재학생",
      "entity": "교무처"
    }
  ]
}
```

---

## 데이터베이스 설계

### 테이블 관계

```
users (1) ──── (N) chat_rooms (1) ──── (N) chat_messages
faqs (독립)
notices (독립)
```

### users
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| device_id | VARCHAR UNIQUE | 프론트에서 발급한 디바이스 식별자 |
| created_at | DATETIME | |
| updated_at | DATETIME | |

### chat_rooms
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| user_id | BIGINT FK | users.id |
| session_id | VARCHAR | 채팅 세션 식별자 |
| title | VARCHAR | 첫 질문 앞 30자 (사이드바 표시용) |
| created_at | DATETIME | |
| updated_at | DATETIME | 메시지 추가 시 갱신 (최신순 정렬 기준) |

### chat_messages
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| chat_room_id | BIGINT FK | chat_rooms.id |
| role | VARCHAR | USER / ASSISTANT |
| content | TEXT | 질문 또는 AI 답변 전문 |
| created_at | DATETIME | |

### faqs
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| category | VARCHAR | 수강신청 / 장학 / 학적 / 졸업 / 성적 등 |
| question | VARCHAR | 질문 내용 |
| sort_order | INT | 표시 순서 |
| view_count | BIGINT | 클릭 수 (인기 FAQ 산정) |
| is_active | BOOLEAN | 소프트 삭제 플래그 |
| created_at | DATETIME | |
| updated_at | DATETIME | |

### notices
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| notice_id | VARCHAR UNIQUE | 원본 공지 ID (중복 방지) |
| title | VARCHAR | 공지 제목 |
| content | TEXT | 공지 내용 |
| date | VARCHAR | YYYY-MM-DD |
| url | VARCHAR | 원본 링크 |
| category | VARCHAR | 세부 분류 |
| major_category | VARCHAR | 대분류 |
| target | VARCHAR | 대상 (재학생 등) |
| entity | VARCHAR | 담당 부서 |

---

## 프론트엔드

`src/main/resources/static/test.html` — 단일 파일 SPA (빌드 도구 없음)

### 레이아웃
- **사이드바 (260px)**: 로고, 새 채팅 버튼, 채팅 이력 목록, 관리자 패널 버튼
- **메인 영역**: 초기 FAQ 카테고리 화면 → 채팅 메시지 피드
- **입력창 (하단)**: 자동 높이 확장 textarea, 전송 버튼

### 주요 동작
- `loadFaqs()` — API에서 FAQ 목록 로드 후 카테고리 버튼 렌더링
- `loadHistory()` — 채팅 이력 로드, 사이드바에 표시
- `sendQuestion()` — POST `/api/chat/ask` → EventSource로 SSE 파싱 → 단어 단위 DOM 업데이트
- `sendNotice()` — 관리자 모달에서 공지 이벤트 테스트 전송
- `escHtml()` — XSS 방지용 HTML 이스케이프

### SSE 파싱 흐름
```javascript
EventSource 연결
  → "chunk" 이벤트: 메시지 DOM에 텍스트 추가
  → "sources" 이벤트: 참고 출처 박스 렌더링
  → "[DONE]": 연결 종료
  → "error": 에러 메시지 표시
```

- `DEVICE_ID`: 테스트용 고정값 `"test-user-001"`
- `SESSION_ID`: 새 채팅 시 타임스탬프 기반 생성

---

## 환경 설정

### 프로파일

```bash
# H2 인메모리 (기본값)
java -Dspring.profiles.active=local -jar app.jar

# MySQL Docker (포트 3307)
java -Dspring.profiles.active=dev -jar app.jar
```

### 주요 프로퍼티

```properties
# RAG 서버 (ngrok 터널)
rag.server.url=https://wriggle-script-caption.ngrok-free.dev

# 인증 키
notice.api-key=hoseo-lens-secret-key
admin.api-key=hoseo-admin-secret-key

# JSON 네이밍
spring.jackson.property-naming-strategy=SNAKE_CASE
```

### 초기 데이터 (`FaqDataInitializer`)
앱 시작 시 10개 샘플 FAQ 자동 삽입 (수강신청·장학·학적·졸업·비교과·성적·공지·학생지원 카테고리)
