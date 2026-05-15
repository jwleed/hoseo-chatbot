# Hoseo Chatbot Backend — 구조 정리

## 기술 스택

| 항목 | 내용 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 4.0.3 |
| DB (개발) | H2 (in-memory) |
| DB (운영) | MySQL |
| ORM | Spring Data JPA |
| 비동기 HTTP | Spring WebFlux (WebClient) |
| 푸시 알림 | Firebase Admin SDK (FCM) 9.4.2 |
| 빌드 | Gradle (Kotlin DSL) |

---

## 아키텍처

```
┌─────────────────────────────────────────────────────┐
│                  모바일 앱 (Flutter)                  │
└────────────┬───────────────────┬────────────────────┘
             │ HTTPS (443)       │ HTTPS (443)
             ▼                   ▼
┌──────────────────────────────────────────────────────┐
│              Spring Boot Backend (8080)               │
│                                                      │
│  ChatController       → SSE 스트리밍 채팅              │
│  FaqController        → FAQ CRUD                     │
│  HistoryController    → 채팅 히스토리                  │
│  KeywordController    → 키워드/알림 설정               │
│  UserController       → FCM 토큰, 카테고리 설정        │
│  NoticeEventController→ 공지 웹훅 수신                 │
└──────┬──────────────────────┬────────────────────────┘
       │                      │
       ▼                      ▼
┌─────────────┐     ┌──────────────────────┐
│    MySQL    │     │   RAG AI 서버 (외부)   │
│  (운영 DB)  │     │  /ask → 질문 답변 생성  │
└─────────────┘     └──────────────────────┘
                             ▲
┌────────────────────────────┘
│  공지 크롤러 (외부)
│  새 공지 발생 시 POST /api/notices/new 호출
└────────────────────────────

       FCM 발송
Spring Boot ──→ Firebase FCM ──→ 모바일 앱 푸시 알림
```

---

## DB 스키마

### users
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | auto_increment |
| device_id | VARCHAR UNIQUE NOT NULL | 앱 기기 식별자 |
| fcm_token | VARCHAR | FCM 푸시 토큰 |
| notification_yn | BOOLEAN DEFAULT TRUE | 알림 수신 여부 |
| created_at | DATETIME NOT NULL | 생성일 |
| updated_at | DATETIME NOT NULL | 수정일 |

### chat_rooms
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | auto_increment |
| user_id | BIGINT FK → users.id | 소유 사용자 |
| session_id | VARCHAR NOT NULL | 앱에서 전달하는 세션 식별자 |
| title | VARCHAR | 첫 질문 앞 30자 |
| created_at | DATETIME NOT NULL | 생성일 |
| updated_at | DATETIME NOT NULL | 마지막 메시지 시각 |

### chat_messages
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | auto_increment |
| chat_room_id | BIGINT FK → chat_rooms.id | 소속 채팅방 |
| role | ENUM(USER, ASSISTANT) NOT NULL | 발화자 구분 |
| content | TEXT NOT NULL | 질문 또는 답변 본문 |
| created_at | DATETIME NOT NULL | 저장 시각 |

### faqs
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | auto_increment |
| category | VARCHAR | 수강신청/장학/학적/졸업 등 |
| question | VARCHAR NOT NULL | 대표 질문 |
| sort_order | INT | 표시 순서 (오름차순) |
| is_active | BOOLEAN DEFAULT TRUE | 비활성화 시 목록 미노출 (소프트 삭제) |
| created_at | DATETIME NOT NULL | 생성일 |
| updated_at | DATETIME NOT NULL | 수정일 |

### keywords
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | auto_increment |
| user_id | BIGINT FK → users.id | 소유 사용자 |
| keyword | VARCHAR(10) NOT NULL | 알림 키워드 (최대 10자) |
| created_at | DATETIME NOT NULL | 등록일 |

> UNIQUE 제약: (user_id, keyword)  
> 사용자당 최대 10개 등록 가능

### notifications
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | auto_increment |
| user_id | BIGINT FK → users.id | 수신 사용자 |
| keyword_id | BIGINT FK → keywords.id | 매칭된 키워드 |
| title | VARCHAR | 공지 제목 |
| content | TEXT | 공지 내용 |
| url | VARCHAR(500) | 공지 원문 URL |
| is_read | BOOLEAN DEFAULT FALSE | 읽음 여부 |
| created_at | DATETIME NOT NULL | 생성일 |

### 테이블 관계도
```
users ──< chat_rooms ──< chat_messages
users ──< keywords ──< notifications
users ──< notifications
```

---

## API 목록

### 채팅

#### POST /api/chat/ask
SSE 스트리밍으로 AI 답변을 반환합니다.  
Response: `text/event-stream`

**Request Body**
```json
{
  "user_id": "device-uuid",
  "session_id": "session-uuid",
  "question": "수강신청 기간이 언제인가요?",
  "category": "notice"
}
```

**SSE 이벤트 흐름**
```
data: {"chunk": "수강"}
data: {"chunk": "신청 "}
data: {"chunk": "기간은 "}
...
data: {"chunk": "", "sources": [...]}   ← 마지막 청크에 출처 포함
data: [DONE]
```

**에러 이벤트**
```json
{"error": {"code": "CONNECTION_FAILED", "message": "..."}}
```

---

### FAQ

#### GET /api/faq
활성 FAQ 목록 조회. `category` 쿼리 파라미터로 필터링 가능.

```
GET /api/faq
GET /api/faq?category=수강신청
```

**Response**
```json
[
  {
    "id": 1,
    "category": "수강신청",
    "question": "수강신청 기간은 언제인가요?",
    "sort_order": 1,
    "is_active": true,
    "created_at": "2025-05-01T10:00:00",
    "updated_at": "2025-05-01T10:00:00"
  }
]
```

#### POST /api/faq
FAQ 등록. 관리자 인증 필요.  
Header: `X-Admin-Key: {admin_api_key}`

**Request Body**
```json
{
  "category": "장학",
  "question": "장학금 신청은 어디서 하나요?",
  "sort_order": 3
}
```

#### PUT /api/faq/{id}
FAQ 수정. 관리자 인증 필요.  
Header: `X-Admin-Key: {admin_api_key}`

#### DELETE /api/faq/{id}
FAQ 비활성화 (소프트 삭제). 관리자 인증 필요.  
Header: `X-Admin-Key: {admin_api_key}`

---

### 채팅 히스토리

#### GET /api/history/{deviceId}
사용자의 채팅방 목록 조회. updatedAt 내림차순.

**Response**
```json
{
  "status": "success",
  "history": [
    {
      "id": 1,
      "title": "수강신청 기간이 언제인가요?",
      "created_at": "2025-05-01T10:00:00"
    }
  ]
}
```

#### GET /api/history/{deviceId}/{chatRoomId}
특정 채팅방의 메시지 목록 조회.

**Response**
```json
{
  "status": "success",
  "chat_room_id": 1,
  "messages": [
    {
      "role": "user",
      "content": "수강신청 기간이 언제인가요?",
      "created_at": "2025-05-01T10:00:00"
    },
    {
      "role": "assistant",
      "content": "수강신청 기간은 ...",
      "created_at": "2025-05-01T10:00:05"
    }
  ]
}
```

#### DELETE /api/history/{chatRoomId}
채팅방 및 메시지 삭제. 204 No Content 반환.

---

### 알림 키워드

#### POST /api/notification/keyword
키워드 등록. 사용자당 최대 10개, 키워드 최대 10자.

**Request Body**
```json
{
  "user_id": "device-uuid",
  "keyword": "장학금"
}
```

**에러 코드**
| 상황 | 응답 |
|------|------|
| 키워드 공백 | 400 KEYWORD_BLANK |
| 10자 초과 | 400 KEYWORD_TOO_LONG |
| 중복 키워드 | 409 KEYWORD_DUPLICATE |
| 10개 초과 | 400 KEYWORD_LIMIT_EXCEEDED |

#### GET /api/notification/keyword/{deviceId}
사용자의 키워드 목록 조회.

**Response**
```json
[
  {"id": 1, "keyword": "장학금", "created_at": "2025-05-01T10:00:00"}
]
```

#### DELETE /api/notification/keyword/{keywordId}
키워드 삭제. 관련 알림 기록도 함께 삭제. 204 No Content 반환.

#### PUT /api/notification/setting/{deviceId}
알림 수신 ON/OFF 설정.

**Request Body**
```json
{"notification_yn": false}
```

---

### 사용자

#### POST /api/user/fcm-token
FCM 토큰 등록 또는 갱신. 사용자가 없으면 자동 생성.

**Request Body**
```json
{
  "user_id": "device-uuid",
  "fcm_token": "firebase-token-string"
}
```

#### POST /api/user/categories
카테고리(키워드) 일괄 교체. 기존 키워드 전체 삭제 후 새로 저장.

**Request Body**
```json
{
  "user_id": "device-uuid",
  "categories": ["장학금", "수강신청", "졸업"]
}
```

---

### 공지 이벤트 (내부/웹훅)

#### POST /api/notices/new
외부 공지 크롤러가 새 공지 발생 시 호출하는 웹훅.  
Header: `X-API-Key: {notice_api_key}`

**Request Body**
```json
{
  "source": "hoseo",
  "generated_at": "2025-05-01T10:00:00",
  "count": 2,
  "items": [
    {
      "notice_id": "12345",
      "title": "2025학년도 2학기 장학금 신청 안내",
      "date": "2025-05-01",
      "url": "https://...",
      "category": "장학",
      "major_category": "학사",
      "target": "전체",
      "entity": "교학처"
    }
  ]
}
```

**처리 흐름**
1. 모든 키워드 목록 조회 (N+1 방지: JOIN FETCH)
2. 각 공지 제목에 키워드 포함 여부 확인
3. 매칭 시 → 알림 ON인 사용자에게 FCM 푸시 발송
4. 중복 알림 방지 (user + keyword + url 조합으로 체크)

**Response**
```json
{"status": "success", "processed": 2}
```

---

## 핵심 기능 설명

### SSE 스트리밍 채팅
- RAG AI 서버에 질문을 POST로 전달하고 응답(answer, sources)을 받아 단어 단위로 쪼개 SSE로 스트리밍
- 30ms 딜레이로 타이핑 효과 구현
- 3초마다 heartbeat(ping) 전송으로 연결 유지
- 타임아웃: 180초

### 사용자 자동 생성
- `device_id` 기반으로 사용자 식별
- 채팅 요청 또는 FCM 토큰 등록 시 사용자가 없으면 자동 생성

### FAQ 소프트 삭제
- DELETE 시 DB에서 실제 삭제하지 않고 `is_active = false` 처리
- 조회 API는 `is_active = true`인 것만 반환

### 공지 알림 중복 방지
- `notifications` 테이블에서 (user, keyword, url) 조합으로 중복 체크
- 같은 공지가 여러 번 들어와도 한 번만 발송

### 키워드 일괄 교체 (categories)
- `POST /api/user/categories` 호출 시 기존 키워드와 관련 알림을 모두 삭제 후 새로 저장
- 앱 초기 설정 화면에서 관심 카테고리 선택 시 사용
