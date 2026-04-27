# 백엔드 소프트웨어 요구사항 명세서 (SRS)

## 호서대학교 스마트 캠퍼스 도우미 (Hoseo-LENS)

---

## 1. 개요

### 1.1 목적

본 문서는 호서대학교 스마트 캠퍼스 도우미(Hoseo-LENS) 서비스의 백엔드 시스템 요구사항을 정의한다. 백엔드는 모바일 앱과 AI 서버 사이의 중계 역할을 담당하며, 채팅, FAQ, 히스토리, 알림 기능을 위한 API를 제공한다.

### 1.2 범위

- 모바일 앱 ↔ Spring Boot 백엔드 간 API
- Spring Boot 백엔드 ↔ Python FastAPI AI 서버 간 연동
- MySQL 기반 데이터 관리

### 1.3 시스템 구조

```
[모바일 앱]
    ↓ ↑
[Spring Boot 백엔드 :8080]  ←→  [MySQL]
    ↓ ↑
[Python FastAPI AI 서버 :8000]
    ↓ ↑
[Milvus 벡터 DB + GPT-4o-mini]
```

### 1.4 백엔드 핵심 원칙

- Spring Boot는 AI 로직을 직접 처리하지 않고 **요청 중계 및 데이터 관리**만 담당
- AI 서버(포트 8000), DB(포트 3306)는 외부에 직접 노출하지 않음
- Spring Boot(포트 8080)만 외부 접점으로 사용

---

## 2. 기술 스택

| 항목 | 기술 |
| --- | --- |
| 언어 | Java 17 |
| 프레임워크 | Spring Boot 3 |
| 빌드 | Gradle Kotlin |
| HTTP 클라이언트 | WebClient (SSE 스트리밍 수신) |
| 스트리밍 | SseEmitter (SSE 스트리밍 전송) |
| DB | MySQL + Spring Data JPA |
| 배포 | 네이버 클라우드 (Ubuntu Server 22.04 LTS) |

---

## 3. 공통 정책

- **Base URL**: `http://<BACKEND_HOST>:8080`
- **Content-Type**: `application/json`
- **인증**: 없음 (로그인 없는 서비스)
- **userId**: 앱 설치 시 자동 생성한 UUID (device_id 기반)
- **sessionId**: 대화 세션 단위 UUID (앱에서 생성)

---

## 4. 기능 요구사항

### 4.1 채팅 API 구현 완료

### 4.1.1 기능 설명

모바일 앱의 질문을 수신하여 Python FastAPI AI 서버로 전달하고, SSE 스트리밍 응답을 실시간으로 모바일 앱에 중계한다.

### 4.1.2 요청

- **Method**: `POST`
- **Path**: `/api/chat/ask`
- **Headers**:

```
Content-Type: application/json
Accept: text/event-stream
```

- **요청 바디**:

```json
{
  "userId": "device-uuid-1234",
  "sessionId": "session-uuid-5678",
  "question": "수강신청은 언제 해?",
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| userId | String |  | 기기 식별값 |
| sessionId | String |  | 세션 식별값 |
| question | String |  | 사용자 질문 |

### 4.1.4 응답 (SSE 스트리밍)

```
data: {"chunk":"수강신청은 "}
data: {"chunk":"3월 2일부터 "}
data: {"chunk":"3월 6일까지입니다."}
data: {"chunk":"","sources":[{"doc_id":"notice_123","title":"2026 수강신청 안내","file_url":"https://..."}]}
data: [DONE]
```

### 4.1.5 SSE 이벤트 규칙

| 이벤트 | 설명 |
| --- | --- |
| `{"chunk":"..."}` | 텍스트 조각. 순서대로 누적하면 완성된 답변 |
| `{"chunk":"","sources":[...]}` | 출처 정보. 스트리밍 마지막에 1회 제공 |
| `[DONE]` | 스트리밍 종료 신호 |
| `{"error":{"code":"...","message":"..."}}` | 오류 발생 시 |

### 4.1.6 에러 코드

| 코드 | 상황 |
| --- | --- |
| `CONNECTION_FAILED` | AI 서버 연결 실패 |
| `UPSTREAM_TIMEOUT` | AI 서버 응답 지연 |
| `INVALID_REQUEST` | 잘못된 요청 |

### 4.1.7 sources 스키마

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| doc_id | String |  | 문서 식별값 |
| title | String |  | 문서 제목 |
| file_url | String |  | 원문 링크 (없을 수 있음) |

---

### 4.2 공지사항 저장 API (구현 완료)

### 4.2.1 기능 설명

AI 담당자의 크롤러가 수집한 공지사항 데이터를 백엔드가 수신하여 MySQL notices 테이블에 저장한다. 중복 공지는 자동으로 방지하며, 저장 완료 후 알림 키워드 매칭 로직을 실행하여 FCM 푸시 알림을 발송한다.

### 4.2.2 동작 흐름

```
AI 크롤러 → POST /api/chatbot/save 호출
  → 중복 체크 (title + date 기준)
  → 중복이면 스킵
  → 중복 아니면 notices 테이블에 저장
  → Keyword 테이블에서 전체 키워드 조회
  → 공지 제목/내용에 키워드 포함 여부 확인
  → 일치하면 FCM 발송 + Notification 테이블 저장
```

### 4.2.3 요청

- **Method**: `POST`
- **Path**: `/api/chatbot/save`

```json
{
  "title": "2026학년도 1학기 장학금 신청 안내",
  "content": "...",
  "date": "2026-03-31",
  "url": "https://www.hoseo.ac.kr/..."
}
```

### 4.2.4 알림 발송 조건

- 공지 제목 또는 내용에 등록된 키워드 포함 시 발송
- notification_yn = true인 기기에만 발송
- 동일 (user_id + keyword_id + url) 조합은 중복 발송 안 함

---

### 4.3 FAQ API 구현 예정

### 4.3.1 기능 설명

카테고리별 자주 묻는 질문 목록을 제공한다. FAQ 질문 선택 시 고정 답변이 아닌 채팅 API를 통해 AI가 동적으로 답변을 생성한다.

### 4.3.2 FAQ 동작 흐름

```
FAQ 탭 진입
  → GET /api/faq?category=장학금 (질문 목록 수신)
  → 질문 선택
  → POST /api/chat/ask (채팅 API 호출)
  → SSE 스트리밍으로 AI 답변
```

### 4.3.3 FAQ 목록 조회

- **Method**: `GET`
- **Path**: `/api/faq`
- **Query**: `?category=장학금` (없으면 전체)

```json
[
  {
    "faqId": 1,
    "category": "장학금",
    "question": "성적장학금 기준이 뭐야?",
    "sortOrder": 1
  }
]
```

### 4.3.4 FAQ 관리 API (관리자용)

| Method | Path | 설명 |
| --- | --- | --- |
| `POST` | `/api/faq` | FAQ 추가 |
| `PUT` | `/api/faq/{id}` | FAQ 수정 |
| `DELETE` | `/api/faq/{id}` | FAQ 삭제 |

### 4.3.5 FAQ 데이터 관리 정책

- 초기 FAQ 데이터는 팀이 직접 DB에 입력
- 카테고리: 수강신청, 장학금, 졸업, 휴학, 비교과
- 향후 사용 빈도 기반 자동 추천 확장 가능

---

### 4.4 히스토리 API 구현 예정

### 4.4.1 기능 설명

기기 기준으로 대화 이력을 저장하고 조회한다. 채팅 완료 시 자동으로 저장되며, 사용자는 이전 대화를 다시 열람하거나 삭제할 수 있다.

### 4.4.2 자동 저장 조건

SSE 스트리밍 완료(`[DONE]`) 시점에 ChatRoom과 ChatMessage를 자동 저장한다.

### 4.4.3 대화 목록 조회

- **Method**: `GET`
- **Path**: `/api/history/{deviceId}`

```json
{
  "status": "success",
  "history": [
    {
      "chatRoomId": 1,
      "firstQuestion": "수강신청은 언제 해?",
      "createdAt": "2026-03-21T12:00:00Z"
    }
  ]
}
```

### 4.4.4 특정 대화 상세 조회

- **Method**: `GET`
- **Path**: `/api/history/{deviceId}/{chatRoomId}`

```json
{
  "status": "success",
  "chatRoomId": 1,
  "messages": [
    {"role": "user", "content": "수강신청은 언제 해?", "createdAt": "2026-03-21T12:00:00Z"},
    {"role": "assistant", "content": "수강신청은 3월 2일부터...", "createdAt": "2026-03-21T12:00:02Z"}
  ]
}
```

### 4.4.5 특정 대화 삭제

- **Method**: `DELETE`
- **Path**: `/api/history/{chatRoomId}`

---

### 4.5 알림 API 구현 예정

### 4.5.1 기능 설명

사용자가 등록한 키워드와 관련된 새 공지 발생 시 FCM 푸시 알림을 발송한다.

### 4.5.2 키워드 등록

- **Method**: `POST`
- **Path**: `/api/notification/keyword`

```json
{
  "userId": "device-uuid-1234",
  "keyword": "장학금"
}
```

### 4.5.3 키워드 목록 조회

- **Method**: `GET`
- **Path**: `/api/notification/keyword/{deviceId}`

### 4.5.4 키워드 삭제

- **Method**: `DELETE`
- **Path**: `/api/notification/keyword/{keywordId}`

### 4.5.5 알림 ON/OFF

- **Method**: `PUT`
- **Path**: `/api/notification/setting/{deviceId}`

```json
{"notificationYn": true}
```

### 4.5.6 알림 발송 조건

- 크롤러가 새 공지 저장 시 등록된 키워드와 비교
- 일치하는 키워드 있으면 FCM 푸시 알림 발송
- 동일 공지에 대한 중복 알림 발송 금지
- `notificationYn = true`인 기기에만 발송

### 4.5.7 FCM 알림 포함 정보

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

### 5.1 notices 테이블 (공지사항 - 백엔드 담당)

```sql
CREATE TABLE notices (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    title   VARCHAR(500),
    content TEXT,
    date    VARCHAR(50),
    url     VARCHAR(1000)
);
```

### 5.2 User 테이블 (기기 기반)

```sql
CREATE TABLE User (
    user_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id       VARCHAR(255) UNIQUE,
    fcm_token       VARCHAR(255),
    notification_yn BOOLEAN DEFAULT TRUE,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 5.3 ChatRoom 테이블

```sql
CREATE TABLE ChatRoom (
    chat_room_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT,
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES User(user_id)
);
```

### 5.4 ChatMessage 테이블

```sql
CREATE TABLE ChatMessage (
    message_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    chat_room_id BIGINT,
    role         VARCHAR(20),  -- user / assistant
    content      TEXT,
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (chat_room_id) REFERENCES ChatRoom(chat_room_id)
);
```

### 5.5 FAQ 테이블

```sql
CREATE TABLE FAQ (
    faq_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    category   VARCHAR(50),
    question   TEXT,
    sort_order INT,
    is_active  BOOLEAN DEFAULT TRUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 5.6 Keyword 테이블

```sql
CREATE TABLE Keyword (
    keyword_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT,
    keyword    VARCHAR(100),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES User(user_id)
);
```

### 5.7 Notification 테이블

```sql
CREATE TABLE Notification (
    notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT,
    keyword_id      BIGINT,
    title           VARCHAR(255),
    content         TEXT,
    url             TEXT,
    is_read         BOOLEAN DEFAULT FALSE,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES User(user_id),
    FOREIGN KEY (keyword_id) REFERENCES Keyword(keyword_id)
);
```

---

## 6. AI 서버 연동 스펙 (AI 담당 확인 필요)

### 6.1 요청

- **Method**: `POST`
- **Path**: `/api/v1/chat/stream`

```json
{
  "user_id": "device-uuid-1234",
  "session_id": "session-uuid-5678",
  "question": "수강신청은 언제 해?",
}
```

### 6.2 확인 필요 사항

| 항목 | 상태 |
| --- | --- |
| `/api/v1/chat/stream` 엔드포인트 구현 여부 | 확인 필요 |
| `sources` 스키마 확정 | 완료 |
| 서버 IP/포트 공유 | 확인 필요 |

---

## 7. 인프라

| 서버 | 스펙 | 용도 |
| --- | --- | --- |
| hoseo-lens-backend | Ubuntu 22.04 / 2vCPU / 8GB / 50GB | Spring Boot + MySQL |

---

## 8. 개발 일정 (8주)

| 주차 | 내용 |
| --- | --- |
| 1~2주차 | 채팅 API 완성 (AI 서버 실연동) |
| 3~4주차 | DB 설계 적용 + 히스토리 API |
| 5주차 | FAQ API |
| 6~7주차 | 알림 서비스 + FCM 연동 |
| 8주차 | 통합 테스트 + 시연 준비 + 서버 배포 |

---

## 9. 구현 현황

| 기능 | API | 상태 |
| --- | --- | --- |
| SSE 채팅 | `POST /api/chat/ask` | 완료 |
| FAQ 목록 조회 | `GET /api/faq` | 예정 |
| 히스토리 저장/조회 | `GET /api/history/{deviceId}` | 예정 |
| 알림 키워드 관리 | `POST /api/notification/keyword` | 예정 |
| FCM 푸시 알림 | 크롤러 저장 시 자동 발송 | 예정 |

---