# 백엔드 통합 SRS (Software Requirements Specification)

## 호서대학교 스마트 캠퍼스 도우미 (Hoseo-LENS)

**작성일:** 2026-05-12  
**백엔드 담당:** 이재화, 구자준 (2인 공동 개발)  
**마감 기한:** 2026년 6월 초

---

## 1. 개요

### 1.1 목적

모바일 앱과 AI 서버 사이의 중계 서버를 구현한다. 채팅·FAQ·히스토리·알림 4개 도메인 API를 제공하며, MySQL에 데이터를 영속화한다.

### 1.2 시스템 구조

```
[모바일 앱]
    ↓ ↑  SSE 스트리밍
[Spring Boot 백엔드 :8080]  ←→  [MySQL]
    ↓ ↑  REST (JSON 단일 응답)
[Python FastAPI AI 서버 :8000]
    ↓ ↑
[Milvus 벡터 DB + LLM]
```

> **확정 사항:** AI 서버는 REST 단일 JSON 응답 방식. 프론트에 SSE 스트리밍은 Spring Boot에서 직접 토큰 분할하여 전달.

### 1.3 핵심 원칙

- Spring Boot는 AI 로직을 처리하지 않고 **요청 중계 + 데이터 관리**만 담당
- AI 서버(8000), DB(3306)는 외부 미노출 — Spring Boot(8080)만 외부 접점
- 인증 없음 (로그인 없는 서비스, device_id 기반 사용자 식별)

---

## 2. 기술 스택

| 항목 | 기술 |
|---|---|
| 언어 | Java 17 |
| 프레임워크 | Spring Boot 3 |
| 빌드 | Gradle Kotlin |
| HTTP 클라이언트 | WebClient (AI 서버 호출) |
| 스트리밍 | SseEmitter (프론트 SSE 전송) |
| DB | MySQL + Spring Data JPA |
| 배포 | 네이버 클라우드 (Ubuntu 22.04 LTS) |

---

## 3. 공통 정책

- **Base URL:** `http://<BACKEND_HOST>:8080`
- **Content-Type:** `application/json`
- **userId (device_id):** 앱 설치 시 자동 생성한 UUID
- **sessionId:** 대화 세션 단위 UUID (앱에서 생성)
- **에러 응답 공통 포맷:**

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "설명"
  }
}
```

---

## 4. 기능 요구사항 및 구현 현황

### 4.1 채팅 API

#### 상태: 구현 완료 (AI 서버 연결 검증 미완료)

#### `POST /api/chat/ask`

**요청 헤더:**
```
Content-Type: application/json
Accept: text/event-stream
```

**요청 바디:**
```json
{
  "userId": "device-uuid-1234",
  "sessionId": "session-uuid-5678",
  "question": "수강신청은 언제 해?"
}
```

**처리 흐름:**
```
1. userId → User 자동 생성 or 조회
2. sessionId → ChatRoom 자동 생성 or 조회 (제목: 첫 질문 앞 30자)
3. USER 메시지 DB 저장
4. AI 서버 POST /ask 호출 (REST JSON)
5. 응답 answer를 단어 단위로 쪼개 SSE chunk 전송 (30ms 간격)
6. ASSISTANT 메시지 DB 저장
7. sources 이벤트 전송
8. [DONE] 이벤트 전송
```

**SSE 이벤트:**
```
data: {"chunk":"수강신청은 "}
data: {"chunk":"3월 2일부터 3월 6일까지입니다."}
data: {"chunk":"","sources":[{"doc_id":"notice_123","title":"제목","file_url":"https://..."}]}
data: [DONE]
```

| 이벤트 | 설명 |
|---|---|
| `{"chunk":"..."}` | 텍스트 조각 |
| `{"chunk":"","sources":[...]}` | 출처 정보 (마지막 1회) |
| `[DONE]` | 스트리밍 종료 |
| `{"error":{"code":"...","message":"..."}}` | 오류 발생 시 |

**에러 코드:**

| 코드 | 상황 |
|---|---|
| `CONNECTION_FAILED` | AI 서버 연결 실패 |
| `UPSTREAM_TIMEOUT` | AI 서버 응답 지연 |
| `INVALID_REQUEST` | 잘못된 요청 |

**AI 서버 연동 스펙 (확정):**

| 항목 | 값 |
|---|---|
| Method | POST |
| Path | `/ask` |
| 응답 방식 | REST JSON 단일 응답 |
| 요청 바디 | `{"question":"...","domain":"notice","use_tv_rag":true}` |
| 응답 필드 | `answer` (String), `sources` (List) |

---

### 4.2 히스토리 API

#### 상태: 구현 완료 (삭제 API 검증 미완료)

#### `GET /api/history/{deviceId}` — 채팅방 목록 조회

- 최근 대화순 (`updatedAt` 내림차순) 정렬

```json
[
  {
    "chatRoomId": 1,
    "firstQuestion": "수강신청 언제야?",
    "createdAt": "2026-04-29T19:11:01.628275"
  }
]
```

#### `GET /api/history/{deviceId}/{chatRoomId}` — 채팅방 상세 메시지 조회

- 다른 사용자의 chatRoomId 접근 시 `403 Forbidden`

```json
{
  "status": "success",
  "chatRoomId": 1,
  "messages": [
    {"role": "user", "content": "수강신청 언제야?", "createdAt": "2026-04-29T19:11:01.640279"},
    {"role": "assistant", "content": "수강신청은 3월 2일부터...", "createdAt": "2026-04-29T19:11:03.000000"}
  ]
}
```

#### `DELETE /api/history/{chatRoomId}` — 채팅방 삭제

- 채팅방 삭제 시 소속 메시지도 함께 삭제 (CASCADE)
- 존재하지 않는 chatRoomId 요청 시 `404 Not Found`
- 응답: `204 No Content`

---

### 4.3 FAQ API

#### 상태: 구현 완료

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/api/faq` | 전체 활성 FAQ 조회 (sort_order 오름차순) |
| `GET` | `/api/faq?category=장학금` | 카테고리별 조회 |
| `GET` | `/api/faq/top?limit=5` | 인기 FAQ (viewCount 내림차순) |
| `POST` | `/api/faq` | FAQ 등록 (관리자) |
| `PUT` | `/api/faq/{id}` | FAQ 수정 (관리자) |
| `DELETE` | `/api/faq/{id}` | FAQ 비활성화 (물리 삭제 없음) |
| `POST` | `/api/faq/{id}/click` | 클릭 수 증가 |

**응답 스키마:**
```json
{
  "faqId": 1,
  "category": "수강신청",
  "question": "수강신청 기간은 언제인가요?",
  "sortOrder": 1,
  "viewCount": 6,
  "isActive": true,
  "createdAt": "2026-04-29T19:26:44.891339",
  "updatedAt": "2026-04-29T19:33:36.696231"
}
```

> `answer` 필드 없음. FAQ 질문 선택 시 채팅 API(`POST /api/chat/ask`)로 AI가 동적 답변 생성.

**설계 원칙:**
- FAQ는 사람이 정제한 대표 질문만 등록 (사용자 질문 자동 등록 없음)
- 삭제는 `isActive = false` 처리 (기록 보존)
- 카테고리: 수강신청, 장학금, 졸업, 휴학, 비교과

---

### 4.4 공지사항 저장 API

#### 상태: 구현 완료 (키워드 매칭·FCM 연동 미구현)

#### `POST /api/notices/new`

**요청 헤더:**
```
X-API-Key: <api-key>
Content-Type: application/json
```

**요청 바디 (AI 크롤러가 전송하는 형식 — 여러 건 일괄 전송):**
```json
{
  "source": "hoseo-crawler",
  "generatedAt": "2026-04-29",
  "count": 2,
  "items": [
    {
      "noticeId": "notice-001",
      "title": "수강신청 안내",
      "date": "2026-04-29",
      "url": "https://www.hoseo.ac.kr/...",
      "category": "공지",
      "majorCategory": "학사",
      "target": "전체",
      "entity": "수강신청"
    }
  ]
}
```

> ⚠️ `content` 필드는 현재 items에 없음. AI팀과 필드 추가 여부 확인 필요 (미결 사항 E 참고).

**응답:**
```json
{
  "status": "success",
  "processed": 2
}
```

**중복 방지 우선순위:**
1. `noticeId`가 있으면 noticeId 기준
2. `noticeId`가 없으면 `title + date` 기준
3. 중복 항목은 스킵, `processed` 수에서 제외

---

### 4.5 알림·키워드 API

#### 상태: 미구현

#### `POST /api/notification/keyword` — 키워드 등록

```json
{
  "userId": "device-uuid-1234",
  "keyword": "장학금"
}
```

**유효성 조건:**

| 조건 | 에러 코드 | HTTP |
|---|---|---|
| 공백·null 입력 | `KEYWORD_BLANK` | 400 |
| 글자 수 10자 초과 | `KEYWORD_TOO_LONG` | 400 |
| 동일 userId + keyword 중복 | `KEYWORD_DUPLICATE` | 409 |
| 사용자당 10개 초과 | `KEYWORD_LIMIT_EXCEEDED` | 400 |

#### `GET /api/notification/keyword/{deviceId}` — 키워드 목록 조회

```json
[
  {"keywordId": 1, "keyword": "장학금", "createdAt": "2026-05-01T10:00:00"}
]
```

#### `DELETE /api/notification/keyword/{keywordId}` — 키워드 삭제

- 응답: `204 No Content`

#### `PUT /api/notification/setting/{deviceId}` — 알림 ON/OFF

```json
{"notificationYn": true}
```

**FCM 발송 조건:**
- 새 공지 저장 시 Keyword 테이블 전수 조회
- 공지 title 또는 content에 keyword 포함 시 발송
- `notification_yn = true`인 기기에만 발송
- 동일 (user_id + keyword_id + notice url) 조합은 중복 발송 금지

**FCM 알림 페이로드:**
```json
{
  "keyword": "장학금",
  "noticeTitle": "2026학년도 1학기 장학금 신청 안내",
  "noticeDate": "2026-03-31",
  "noticeUrl": "https://www.hoseo.ac.kr/..."
}
```

---

## 5. 데이터베이스 설계

### 5.1 users

```sql
CREATE TABLE users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id       VARCHAR(255) UNIQUE NOT NULL,
    fcm_token       VARCHAR(255),
    notification_yn BOOLEAN DEFAULT TRUE,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 5.2 chat_rooms

```sql
CREATE TABLE chat_rooms (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    session_id  VARCHAR(255) UNIQUE NOT NULL,
    title       VARCHAR(255),
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

### 5.3 chat_messages

```sql
CREATE TABLE chat_messages (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    chat_room_id BIGINT NOT NULL,
    role         VARCHAR(20) NOT NULL,   -- USER / ASSISTANT
    content      TEXT,
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (chat_room_id) REFERENCES chat_rooms(id) ON DELETE CASCADE
);
```

### 5.4 faqs

```sql
CREATE TABLE faqs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    category    VARCHAR(50),
    question    TEXT NOT NULL,
    answer      TEXT,
    sort_order  INT,
    view_count  BIGINT DEFAULT 0,
    is_active   BOOLEAN DEFAULT TRUE,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 5.5 notices

```sql
CREATE TABLE notices (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    notice_id       VARCHAR(255) UNIQUE,
    title           VARCHAR(500),
    content         TEXT,
    date            VARCHAR(50),
    url             VARCHAR(1000),
    category        VARCHAR(100),
    major_category  VARCHAR(100),
    target          VARCHAR(100),
    entity          VARCHAR(100),
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 5.6 keywords (미구현)

```sql
CREATE TABLE keywords (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    keyword     VARCHAR(100) NOT NULL,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_user_keyword (user_id, keyword),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

### 5.7 notifications (미구현)

```sql
CREATE TABLE notifications (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    keyword_id      BIGINT NOT NULL,
    title           VARCHAR(255),
    content         TEXT,
    url             TEXT,
    is_read         BOOLEAN DEFAULT FALSE,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_user_keyword_url (user_id, keyword_id, url(255)),
    FOREIGN KEY (user_id)    REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (keyword_id) REFERENCES keywords(id) ON DELETE CASCADE
);
```

---

## 6. Entity 관계

```
users (1)
├── chat_rooms (N)
│   └── chat_messages (N)  ← USER / ASSISTANT
├── keywords (N)
│   └── notifications (N)
faqs          ← 채팅/히스토리와 직접 연결 없음
notices       ← 키워드 매칭 대상 (keywords와 간접 연결)
```

---

## 7. 인프라

### 7.1 운영 서버 (Phase 3 구현)

| 서버 | 스펙 | 용도 |
|---|---|---|
| hoseo-lens-backend | 네이버 클라우드 Ubuntu 22.04 / 2vCPU / 8GB | Spring Boot + MySQL |

**배포 설정:**
- Java 17, systemd 서비스 등록
- `application-secret.yml` 분리 (DB 비밀번호, FCM 키, API Key)
- 운영 환경: `spring.jpa.hibernate.ddl-auto=validate`

### 7.2 로컬 개발 환경 (Docker MySQL)

MySQL을 로컬에 직접 설치하지 않고 Docker로 실행한다.

**실행 명령:**
```bash
docker run -d \
  --name hoseo-mysql \
  -e MYSQL_ROOT_PASSWORD=1234 \
  -e MYSQL_DATABASE=hoseolen \
  -p 3306:3306 \
  mysql:8.0
```

**application.yml (로컬 개발용):**
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/hoseolen
    username: root
    password: 1234
  jpa:
    hibernate:
      ddl-auto: update
```

**컨테이너 재시작 (PC 재부팅 후):**
```bash
docker start hoseo-mysql
```

> Spring Boot 기동 전에 반드시 Docker 컨테이너가 실행 중이어야 한다.

---

## 8. 개발 일정 (4 Phase, 2026-05-12 ~ 2026-06-08)

> 백엔드 2인 공동 개발 기준. PR 단위로 작업 분리 후 머지.

---

### Phase 1 — AI 연결 완성 + 채팅 안정화
**기간:** 5/12 (월) ~ 5/18 (일)

| # | 작업 | 완료 기준 |
|---|---|---|
| 1-1 | AI 서버 실IP/포트 확인 및 `rag.server.url` 설정 | Postman SSE chunk 정상 수신 |
| 1-2 | ASSISTANT 메시지 DB 저장 검증 | AI 응답 후 chat_messages에 ASSISTANT 행 확인 |
| 1-3 | SseEmitter timeout 조정 (현재 60초 → 실 테스트 후 결정) | 긴 응답에서 timeout 없이 완료 |
| 1-4 | AI 서버 장애 시 에러 SSE 이벤트 프론트 전달 검증 | `CONNECTION_FAILED` 이벤트 Postman 확인 |
| 1-5 | `DELETE /api/history/{chatRoomId}` 동작 검증 | Postman 204 + DB 행 삭제 확인 |
| 1-6 | FAQ 카테고리 조회·수정·삭제 Postman 검증 | 3개 API 각각 정상 응답 확인 |

**Phase 1 완료 기준:** 채팅 → AI 응답 → SSE → DB 저장 전체 흐름 정상 동작

---

### Phase 2 — 알림 도메인 Entity + 키워드 매칭 ✅ 완료 (5/12 조기 완료)
**기간:** 5/19 (월) ~ 5/25 (일)

| # | 작업 | 완료 기준 | 상태 |
|---|---|---|---|
| 2-1 | `keywords`, `notifications` 테이블 JPA Entity 생성 | Spring Boot 기동 시 테이블 자동 생성 확인 | ✅ |
| 2-2 | User Entity에 `fcm_token`, `notification_yn` 필드 추가 | DB 컬럼 반영 확인 | ✅ |
| 2-3 | 키워드 CRUD API 구현 (`POST/GET/DELETE /api/notification/keyword`) | Postman 등록·조회·삭제 정상 동작 | ✅ |
| 2-4 | 알림 ON/OFF API 구현 (`PUT /api/notification/setting/{deviceId}`) | `notification_yn` 값 변경 DB 반영 확인 | ✅ |
| 2-5 | Notice 저장 시 키워드 매칭 로직 구현 | 키워드 포함 공지 저장 시 매칭 대상 keyword 조회 확인 | ✅ |
| 2-6 | 중복 키워드 방지 + 최대 10개 제한 검증 | 중복 등록 시 에러 응답, 11번째 등록 시 에러 응답 | ✅ |
| 2-7 | FCM 토큰 등록 API (`POST /api/user/fcm-token`) | 토큰 저장 DB 반영 확인 | ✅ |

**Phase 2 완료 기준:** 키워드 CRUD 동작 + 공지 저장 시 키워드 매칭 콘솔 출력 확인  
**→ 5/12 조기 완료. Phase 3(FCM 실연동) 일정 앞당길 수 있음**

---

### Phase 3 — FCM 연동 + 서버 배포
**기간:** 5/26 (월) ~ 6/1 (일)

| # | 작업 | 완료 기준 |
|---|---|---|
| 3-1 | Firebase 프로젝트 생성 + Admin SDK 의존성 추가 | 빌드 성공 |
| 3-2 | FCM 서비스 클래스 구현 (토큰 기반 단건 발송) | 실기기 푸시 알림 수신 확인 |
| 3-3 | Notice 키워드 매칭 → FCM 발송 + Notification 저장 | 공지 등록 → 알림 수신 E2E 확인 |
| 3-4 | 중복 알림 방지 (uq_user_keyword_url) 검증 | 동일 공지 2회 저장 시 1회만 발송 확인 |
| 3-5 | `application-secret.yml` 분리 + 운영 프로파일 적용 | `ddl-auto=validate`로 기동 성공 |
| 3-6 | 네이버 클라우드 배포 (Ubuntu, systemd, ACG 포트 설정) | 외부 접속 `GET /actuator/health` 200 OK 확인 |
| 3-7 | 운영 서버에서 AI 실서버 SSE 연동 확인 | 운영 서버 기준 채팅 API 정상 동작 |

**Phase 3 완료 기준:** 운영 서버에서 전체 기능(채팅·FAQ·히스토리·알림) 접속 가능

---

### Phase 4 — 통합 테스트 + 시연 준비
**기간:** 6/2 (월) ~ 6/8 (일)

| # | 작업 | 완료 기준 |
|---|---|---|
| 4-1 | 전체 API 시나리오 Postman Collection 구성 | 컬렉션 실행 전체 통과 |
| 4-2 | 에러 시나리오 검증 (400/404/서버 다운) | 공통 에러 포맷으로 정상 응답 |
| 4-3 | 성능 기준 확인 (히스토리 3초, FAQ 2초 이내) | 응답 시간 기준 통과 |
| 4-4 | 프론트엔드팀 연동 이슈 수정 | 프론트 연동 이슈 0건 |
| 4-5 | FAQ 초기 데이터 25개 이상 DB 입력 | 5개 카테고리 × 5개 이상 |
| 4-6 | 24시간 서버 운영 상태 확인 (메모리 누수 등) | systemd 재시작 없이 24시간 안정 운영 |
| 4-7 | 시연용 데이터·계정 세팅 | 시연 시나리오 리허설 통과 |

**Phase 4 완료 기준:** 발표 직전 점검 리스트 전체 통과

---

## 9. 현재 구현 현황 요약

| 기능 | API | 상태 |
|---|---|---|
| 채팅 SSE | `POST /api/chat/ask` | ✅ 구현 완료 |
| AI 서버 연결 | `POST http://<ai-server>/ask` | ⚠️ 실서버 연결 검증 필요 |
| ASSISTANT 메시지 저장 | 채팅 흐름 내 자동 저장 | ⚠️ AI 연결 후 검증 필요 |
| 히스토리 목록 조회 | `GET /api/history/{deviceId}` | ✅ 구현 완료 |
| 히스토리 상세 조회 | `GET /api/history/{deviceId}/{chatRoomId}` | ✅ 구현 완료 |
| 히스토리 삭제 | `DELETE /api/history/{chatRoomId}` | ⚠️ 검증 필요 |
| FAQ CRUD | `GET/POST/PUT/DELETE /api/faq` | ✅ 구현 완료 |
| FAQ 카테고리 조회 | `GET /api/faq?category=...` | ⚠️ 검증 필요 |
| FAQ 클릭 수 | `POST /api/faq/{id}/click` | ✅ 구현 완료 |
| 공지 저장 | `POST /api/notices/new` | ✅ 구현 완료 |
| 키워드 CRUD | `POST/GET/DELETE /api/notification/keyword` | ✅ 구현 완료 |
| 알림 ON/OFF | `PUT /api/notification/setting/{deviceId}` | ✅ 구현 완료 |
| FCM 토큰 등록 | `POST /api/user/fcm-token` | ✅ 구현 완료 |
| FCM 푸시 알림 | 공지 저장 시 자동 발송 (Stub) | ⚠️ Phase 3 Firebase SDK 연동 필요 |
| 키워드 매칭 알림 저장 | 공지 저장 시 NotificationEntity 생성 | ✅ 구현 완료 |
| 서버 배포 | 네이버 클라우드 | ❌ 미구현 |
| 운영 환경 분리 | `application-secret.yml` | ❌ 미구현 |

---

## 10. 미결 사항 (확인 필요)

| # | 항목 | 담당 | 상태 |
|---|---|---|---|
| A | AI 서버 실IP/포트 공유 | AI (조준희) | 미확인 |
| B | FCM 프로젝트 생성 주체 (Firebase 콘솔 접근권) | 구자준 | 미확인 |
| C | 공지 저장 API `X-API-Key` 값 AI팀과 공유 | 이재화 ↔ AI | 미확인 |
| D | 운영 서버 MySQL 비밀번호 및 DB 이름 결정 | 이재화 | 미확인 |
| E | 공지 items에 `content` 필드 포함 여부 (현재 NoticeItemDto에 없음) | 이재화 ↔ AI | 미확인 |
| F | FCM 토큰 등록 API 방식 결정 (별도 API vs 채팅 API에 포함) | 이재화 ↔ 프론트 | ✅ `POST /api/user/fcm-token` 별도 API로 확정 및 구현 완료 |
