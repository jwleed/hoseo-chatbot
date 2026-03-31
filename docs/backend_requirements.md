# 백엔드 연동 요구사항 명세서
## 호서대학교 스마트 캠퍼스 도우미

문서 버전: v1.0  
작성일: 2026-03-31  
작성: 백엔드 담당  
대상: 프론트엔드 팀, AI 담당

---

## 1. 개요

본 문서는 백엔드(Spring Boot)를 기준으로 프론트엔드(모바일 앱) 및 AI 서버(Python FastAPI)와의 연동에 필요한 요구사항을 정의한다.

### 시스템 구조

```
[모바일 앱] ←→ [Spring Boot 백엔드] ←→ [Python FastAPI AI 서버]
                        ↓
                 [PostgreSQL DB]
```

---

## 2. 공통 정책

- **Base URL (백엔드)**: `http://<BACKEND_HOST>:8080`
- **Content-Type**: `application/json`
- **인증**: 현재 미적용 (로그인 없는 서비스)
- **user_id**: 로그인 없이 기기 식별값으로 대체 (앱에서 UUID 자동 생성 후 사용)
- **session_id**: 대화 세션 단위 식별값 (앱에서 UUID 자동 생성 후 사용)

---

## 3. SSE 채팅 API

### 3.1 요청

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
  "question": "2026학년도 장학금 신청 기한이 언제야?"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| userId | String | ✅ | 기기 식별값 (앱에서 UUID 생성) |
| sessionId | String | ✅ | 세션 식별값 (앱에서 UUID 생성) |
| question | String | ✅ | 사용자 질문 |

### 3.2 응답 (SSE)

응답은 `text/event-stream`으로 내려오며 `data:` 라인 단위로 파싱한다.

```
data: {"chunk":"2026학년도 "}
data: {"chunk":"1학기 장학금 신청 기한은 "}
data: {"chunk":"3월 31일까지입니다."}
data: {"chunk":"","sources":[{"docId":"notice_123","title":"2026 장학금 안내","fileUrl":"https://...","docType":"공지사항","page":1}]}
data: [DONE]
```

### 3.3 SSE 이벤트 규칙

| 이벤트 | 설명 |
|---|---|
| `{"chunk":"..."}` | 텍스트 조각. 순서대로 누적하면 완성된 답변 |
| `{"chunk":"","sources":[...]}` | 출처 정보. 스트리밍 마지막에 1회 제공 |
| `[DONE]` | 스트리밍 종료 신호 |
| `{"error":{"code":"...","message":"..."}}` | 오류 발생 시 |

---

## 4. 에러 처리

### 4.1 에러 이벤트 포맷

오류 발생 시 아래 형식으로 SSE 이벤트가 내려오고 이후 `[DONE]`으로 종료된다.

```
data: {"error":{"code":"CONNECTION_FAILED","message":"Python 서버에 연결할 수 없습니다."}}
data: [DONE]
```

### 4.2 에러 코드 목록

| 코드 | 상황 | 프론트 처리 |
|---|---|---|
| `CONNECTION_FAILED` | AI 서버 연결 실패 | "잠시 후 다시 시도해주세요" 안내 |
| `UPSTREAM_TIMEOUT` | AI 서버 응답 지연 | "응답 시간이 초과되었습니다" 안내 |
| `INVALID_REQUEST` | 잘못된 요청 바디 | 입력값 검증 메시지 노출 |

### 4.3 프론트 에러 처리 필수 사항

- 에러 이벤트 수신 시 지금까지 받은 `chunk` 텍스트는 **버리지 말고 유지**할 것
- `[DONE]` 없이 연결이 끊겨도 부분 응답 보존할 것
- "다시 시도" 버튼 제공할 것

---

## 5. 출처(sources) 처리

### 5.1 sources 스키마

```json
{
  "docId": "notice_123",
  "title": "2026 장학금 안내",
  "fileUrl": "https://www.hoseo.ac.kr/...",
  "docType": "공지사항",
  "page": 1
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| docId | String | ✅ | 문서 식별값 |
| title | String | ✅ | 문서 제목 |
| fileUrl | String | ❌ | 원문 링크 (없을 수 있음) |
| docType | String | ✅ | 문서 유형 (공지사항/규정집 등) |
| page | Integer | ❌ | 페이지 번호 (없을 수 있음) |

### 5.2 프론트 처리 필수 사항

- `sources`가 없는 경우에도 UI가 깨지지 않을 것
- `fileUrl`이 없는 경우 문서명만 표시할 것
- `fileUrl`이 있는 경우 새 탭으로 열 수 있을 것
- 복수의 출처는 목록 형태로 표시할 것

---

## 6. 알림 서비스

> 현재 미구현. 아래는 향후 구현 예정 표준이다.

### 6.1 키워드 등록

- **Method**: `POST`
- **Path**: `/api/notification/keyword`

```json
{
  "userId": "device-uuid-1234",
  "keyword": "장학금"
}
```

### 6.2 키워드 목록 조회

- **Method**: `GET`
- **Path**: `/api/notification/keyword/{userId}`

### 6.3 키워드 삭제

- **Method**: `DELETE`
- **Path**: `/api/notification/keyword/{keywordId}`

### 6.4 알림 발송 조건

- 크롤러가 새 공지 저장 시 등록된 키워드와 비교
- 일치하면 FCM 푸시 알림 발송
- 동일 문서에 대한 중복 알림 방지

### 6.5 FCM 알림 포함 정보

```json
{
  "keyword": "장학금",
  "noticeTitle": "2026학년도 1학기 장학금 신청 안내",
  "noticeDate": "2026-03-31",
  "noticeUrl": "https://www.hoseo.ac.kr/..."
}
```

---

## 7. AI 서버 연동 요구사항 (AI 담당 확인 필요)

백엔드(Spring Boot)가 AI 서버(Python FastAPI)에 요청하는 스펙이다.

### 7.1 요청

- **Method**: `POST`
- **Path**: `/api/v1/chat/stream`

```json
{
  "user_id": "device-uuid-1234",
  "session_id": "session-uuid-5678",
  "question": "장학금 신청 기한이 언제야?"
}
```

### 7.2 응답 (SSE)

```
data: {"chunk":"2026학년도 "}
data: {"chunk":"","sources":[...]}
data: [DONE]
```

### 7.3 AI 서버 확인 필요 사항

| 항목 | 상태 |
|---|---|
| `/api/v1/chat/stream` 엔드포인트 구현 여부 | ❓ 확인 필요 |
| `sources` 스키마 확정 (`page`, `docType` 포함 여부) | ❓ 확인 필요 |
| 오류 이벤트 포맷 통일 | ❓ 확인 필요 |
| 서버 IP/포트 공유 | ❓ 확인 필요 |

---

## 8. 현재 구현 상태

| 기능 | 상태 |
|---|---|
| SSE 채팅 API (`/api/chat/ask`) | ✅ 구현 완료 |
| AI 서버 SSE 중계 | ✅ 구현 완료 |
| 에러 처리 (SSE 형식) | ✅ 구현 완료 |
| 대화 이력 저장/조회 | ⏳ 예정 (AWS/네이버 클라우드 배포 시) |
| FAQ API | ⏳ 예정 |
| 알림 서비스 API | ⏳ 예정 |
| 헬스체크 (`/health`) | ⏳ 예정 |

---

## 9. 프론트엔드 체크포인트

- [ ] SSE 파서에서 `[DONE]` 처리 구현
- [ ] 에러 이벤트(`{"error":{...}}`) 처리 구현
- [ ] 스트리밍 중단 시 부분 응답 보존
- [ ] `sources` 없는 경우 UI 예외 처리
- [ ] `userId`, `sessionId` 앱 자체에서 UUID 생성 후 유지
- [ ] `Accept: text/event-stream` 헤더 필수 포함
