# Hoseo-LENS 백엔드 구현 현황 보고서

> AI 챗봇 서비스의 중계 서버 + DB 저장 계층까지 구현된 상태

---

## 전체 흐름

```
프론트 → Spring Boot 백엔드 → AI 서버
                  ↓
             MySQL 저장
```

사용자가 질문하면 백엔드가 질문 정보를 DB에 저장하고, 이후 AI 서버에 질문을 전달하는 구조

---

## 1. 구현 완료 기능

### 채팅 API — `POST /api/chat/ask`

1. `userId`, `sessionId`, `question` 수신
2. 사용자 자동 생성
3. 채팅방 자동 생성
4. 사용자 질문 저장
5. AI 서버 호출
6. SSE 방식으로 프론트에 응답 전달

> 현재 USER 질문 저장까지는 정상 동작 확인 완료

**DB 저장 구조**

| 테이블 | 설명 |
|---|---|
| `users` | `userId` 기반 사용자 저장 |
| `chat_rooms` | `sessionId` 기반 채팅방 저장 |
| `chat_messages` | 사용자의 질문을 USER 역할로 저장 |

**DB 검증 결과 (MySQL Workbench)**

`SELECT * FROM users`
```
id | device_id   | created_at                    | updated_at
1  | device-001  | 2026-04-29 18:40:21.634139   | 2026-04-29 18:40:21.634139
```

`SELECT * FROM chat_rooms`
```
id | user_id | session_id   | title          | created_at                    | updated_at
1  | 1       | session-001  | 수강신청 언제야?  | 2026-04-29 19:11:01.628275   | 2026-04-29 19:11:01.651278
```

`SELECT * FROM chat_messages`
```
id | chat_room_id | content        | role | created_at
1  | 1            | 수강신청 언제야? | USER | 2026-04-29 19:11:01.640279
```

Postman으로 `/api/chat/ask` 호출 후 각 테이블에 데이터가 정상 저장되었음을 확인하였다.

---

### History API

| 메서드 | 엔드포인트 | 설명 |
|---|---|---|
| GET | `/api/history/{deviceId}` | 사용자의 채팅방 목록 조회 |
| GET | `/api/history/{deviceId}/{chatRoomId}` | 특정 채팅방 메시지 조회 |
| DELETE | `/api/history/{chatRoomId}` | 채팅방 삭제 |

**테스트 결과 — 목록 조회 `GET /api/history/device-001`**

- 상태: `200 OK` / 101ms / 324B

```json
[
  {
    "chatRoomId": 1,
    "sessionId": "session-001",
    "title": "수강신청 언제야?",
    "createdAt": "2026-04-29T19:11:01.628275",
    "updatedAt": "2026-04-29T19:11:01.651278"
  }
]
```

**테스트 결과 — 상세 조회 `GET /api/history/device-001/1`**

- 상태: `200 OK`

```json
{
  "chatRoomId": 1,
  "sessionId": "session-001",
  "messages": [
    {
      "role": "USER",
      "content": "수강신청 언제야?",
      "createdAt": "2026-04-29T19:11:01.640279"
    }
  ]
}
```

---

### FAQ API

| 메서드 | 엔드포인트 | 설명 |
|---|---|---|
| GET | `/api/faq` | FAQ 목록 조회 |
| GET | `/api/faq?category=...` | 카테고리별 조회 |
| GET | `/api/faq/top?limit=5` | 인기 FAQ 조회 |
| POST | `/api/faq` | 정제된 FAQ 등록 |
| PUT | `/api/faq/{id}` | FAQ 수정 |
| DELETE | `/api/faq/{id}` | FAQ 비활성화 |
| POST | `/api/faq/{id}/click` | FAQ 클릭 수 증가 |

**중요 설계 원칙**
- 사용자 질문을 FAQ에 자동 등록하지 않음
- FAQ는 사람이 정제한 대표 질문만 저장
- 인기 FAQ는 `viewCount` 기준으로 산출

**테스트 결과 — FAQ 등록 `POST /api/faq`**

- 상태: `201 Created` / 302ms / 470B

Request Body:
```json
{
  "category": "수강신청",
  "question": "수강신청 기간은 언제인가요?",
  "answer": "수강신청 기간은 학사 공지를 통해 확인할 수 있습니다."
}
```

Response:
```json
{
  "id": 1,
  "category": "수강신청",
  "question": "수강신청 기간은 언제인가요?",
  "answer": "수강신청 기간은 학사 공지를 통해 확인할 수 있습니다.",
  "sortOrder": 1,
  "viewCount": 0,
  "isActive": true,
  "createdAt": "2026-04-29T19:26:44.8913391",
  "updatedAt": "2026-04-29T19:26:44.8913391"
}
```

**테스트 결과 — 전체 조회 `GET /api/faq`**

- 상태: `200 OK` / 13ms / 465B

```json
[
  {
    "id": 1,
    "category": "수강신청",
    "question": "수강신청 기간은 언제인가요?",
    "answer": "수강신청 기간은 학사 공지를 통해 확인할 수 있습니다.",
    "sortOrder": 1,
    "viewCount": 0,
    "isActive": true,
    "createdAt": "2026-04-29T19:26:44.891339",
    "updatedAt": "2026-04-29T19:26:44.891339"
  }
]
```

**테스트 결과 — 클릭 수 증가 `POST /api/faq/1/click`**

- 상태: `200 OK`
- 4번 클릭 후 `viewCount` +4 정상 반영

중간 클릭 응답 예시 (2회 클릭 시점):
```json
{
  "id": 1,
  "category": "수강신청",
  "question": "수강신청 기간은 언제인가요?",
  "answer": "수강신청 기간은 학사 공지를 통해 확인할 수 있습니다.",
  "sortOrder": 1,
  "viewCount": 2,
  "isActive": true,
  "createdAt": "2026-04-29T19:26:44.891339"
}
```

**테스트 결과 — 인기 FAQ 조회 `GET /api/faq/top?limit=5`**

- 상태: `200 OK` / 12ms / 465B

```json
[
  {
    "id": 1,
    "category": "수강신청",
    "question": "수강신청 기간은 언제인가요?",
    "answer": "수강신청 기간은 학사 공지를 통해 확인할 수 있습니다.",
    "sortOrder": 1,
    "viewCount": 6,
    "isActive": true,
    "createdAt": "2026-04-29T19:26:44.891339",
    "updatedAt": "2026-04-29T19:33:36.696231"
  }
]
```

`viewCount` 기준 내림차순 정렬로 반환됨을 확인하였다.

---

### Notice API

| 메서드 | 엔드포인트 | 설명 |
|---|---|---|
| POST | `/api/notices/new` | 공지 데이터를 DB에 저장 |

- `X-API-Key`로 인증
- 추후 키워드 알림 / FCM 연동 준비

**테스트 결과 — 공지 등록 `POST /api/notices/new`**

- 상태: `200 OK` / 90ms / 214B

Request Header:
```
X-API-Key: local-test-key
Content-Type: application/json
```

Request Body:
```json
{
  "noticeId": "notice-001",
  "title": "수강신청 안내",
  "content": "2026학년도 수강신청 일정 안내",
  "date": "2026-04-29",
  "url": "https://www.hoseo.ac.kr",
  "category": "공지",
  "majorCategory": "학사",
  "target": "전체",
  "entity": "수강신청"
}
```

Response:
```json
{
  "id": 1,
  "created": true,
  "message": "notice created"
}
```

---

## 2. Entity 관계 구조

```
User (1명)
└── 여러 ChatRoom 보유 가능

ChatRoom (1개)
└── 여러 ChatMessage 보유 가능

ChatMessage
├── USER
└── ASSISTANT

FAQ
└── 채팅 로그와 직접 연결하지 않음

Notice
└── AI 검색용이 아니라 알림/키워드 매칭용
```

---

## 3. 검증 완료 항목

- [x] Spring Boot 서버 정상 실행 확인
- [x] MySQL 서버 연결 성공
- [x] `hoseolen` 데이터베이스 연결 확인
- [x] JPA를 통한 테이블 자동 생성 성공 (`users`, `chat_rooms`, `chat_messages`, `faqs`, `notices`)
- [x] `POST /api/chat/ask` 호출 성공
- [x] `userId` 기준 사용자(User) 자동 생성 확인
- [x] `sessionId` 기준 채팅방(ChatRoom) 자동 생성 확인
- [x] 사용자 질문(ChatMessage - USER) DB 저장 확인
- [x] `GET /api/history/{deviceId}` — 채팅방 목록 조회 정상 동작
- [x] `GET /api/history/{deviceId}/{chatRoomId}` — 채팅방 상세 메시지 조회 정상 동작
- [x] `POST /api/faq` — FAQ 데이터 DB 저장 확인
- [x] `GET /api/faq` — FAQ 전체 조회 정상 동작
- [x] `POST /api/faq/{id}/click` — `viewCount` 증가 정상 반영
- [x] `GET /api/faq/top?limit=5` — `viewCount` 기준 인기 FAQ 정렬 조회 정상 동작
- [x] `POST /api/notices/new` — 공지 데이터 DB 저장 정상 동작

---

## 4. 미검증 항목

- [ ] AI 서버(RAG 서버)와의 실제 연결 검증
  - 현재 Python 서버 연결 실패 상태
  - `rag.server.url` (`http://localhost:8000`) 미동작
- [ ] AI 응답(ASSISTANT 메시지) DB 저장 검증
  - USER 메시지는 저장 확인됨
  - ASSISTANT 메시지는 AI 연결 후 검증 필요
- [ ] SSE 스트리밍 응답 실제 chunk 단위 검증 (Postman에서 완전한 스트리밍 확인 미진행)
- [ ] `GET /api/faq?category=...` — FAQ 카테고리별 조회 검증
- [ ] `PUT /api/faq/{id}` — FAQ 수정 검증
- [ ] `DELETE /api/faq/{id}` — FAQ 삭제 검증
- [ ] `DELETE /api/history/{chatRoomId}` — History 삭제 API 검증
- [ ] Notice 데이터 중복 방지 로직 검증
  - `noticeId` 기준
  - `title + date + url` 기준
- [ ] Notice API 인증(`X-API-Key`) 보안 검증
- [ ] 실제 크롤러/AI 서버와 Notice 자동 연동 검증
- [ ] FCM 또는 알림 기능 (미구현 상태)
- [ ] 운영 환경 설정 분리 (`ddl-auto=create` → `validate` 변경)
