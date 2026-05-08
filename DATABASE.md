# Hoseo LENS — DB 구조 & 기능 정리

## 실행 환경

| 프로필 | DB | 실행 방법 |
|---|---|---|
| `local` | H2 인메모리 (앱 종료 시 데이터 사라짐) | 기본 개발용 |
| `dev` | MySQL 8.4 Docker (데이터 유지) | 팀 협업 / 통합 테스트 |

```bash
# Docker MySQL 시작
docker run -d --name hoseo-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=hoseo_chatbot \
  -p 3307:3306 mysql:8
```

---

## 테이블 구조

### users
사용자를 식별하는 테이블. 로그인 없이 deviceId(userId)로 구분.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | auto increment |
| device_id | VARCHAR UNIQUE | 프론트에서 전달하는 사용자 식별자 |
| created_at | DATETIME | 최초 접속 시각 |
| updated_at | DATETIME | 마지막 갱신 시각 |

---

### chat_rooms
사용자별 채팅방. sessionId 기준으로 생성/재사용.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | auto increment |
| user_id | BIGINT FK | users.id 참조 |
| session_id | VARCHAR | 프론트에서 생성한 세션 식별자 |
| title | VARCHAR | 첫 질문 앞 30자 (채팅방 목록 표시용) |
| created_at | DATETIME | 채팅방 생성 시각 |
| updated_at | DATETIME | 마지막 메시지 시각 (목록 정렬 기준) |

---

### chat_messages
채팅방에 속한 개별 메시지.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | auto increment |
| chat_room_id | BIGINT FK | chat_rooms.id 참조 |
| role | VARCHAR | `USER` 또는 `ASSISTANT` |
| content | TEXT | 질문 또는 AI 답변 전문 |
| created_at | DATETIME | 메시지 저장 시각 |

> ⚠️ 현재 ChatServiceImpl이 메시지를 DB에 저장하지 않음. 저장 로직 미구현.

---

### faqs
관리자가 등록한 자주 묻는 질문.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | auto increment |
| category | VARCHAR | 분류 (예: 학사, 장학, 졸업) |
| question | VARCHAR | 대표 질문 |
| answer | TEXT | 답변 |
| sort_order | INT | 목록 정렬 순서 (낮을수록 우선) |
| view_count | BIGINT | 클릭 수 (인기 FAQ 산출 기준) |
| is_active | BOOLEAN | false = soft delete |
| created_at | DATETIME | 등록 시각 |
| updated_at | DATETIME | 수정 시각 |

---

### notices
AI 서버가 크롤링한 공지사항 이벤트 수신 테이블.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT PK | auto increment |
| notice_id | VARCHAR UNIQUE | 원본 공지 ID (중복 방지 기준) |
| title | VARCHAR | 공지 제목 |
| content | TEXT | 공지 본문 |
| date | VARCHAR | 공지 날짜 (YYYY-MM-DD) |
| url | VARCHAR | 원문 링크 |
| category | VARCHAR | 세부 카테고리 (예: AI 글쓰기 클리닉) |
| major_category | VARCHAR | 대분류 (예: 비교과/특강/행사) |
| target | VARCHAR | 대상 (예: 재학생) |
| entity | VARCHAR | 주관 부서 |

---

## API 기능 목록

### Chat — `/api/chat`
| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/chat/ask` | RAG 서버에 질문 전달, SSE 스트리밍으로 답변 반환 |

---

### FAQ — `/api/faq`
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/faq` | 전체 또는 카테고리별 활성 FAQ 조회 |
| GET | `/api/faq/top` | 클릭 수 기준 TOP5/TOP10 조회 |
| POST | `/api/faq` | FAQ 등록 |
| PUT | `/api/faq/{id}` | FAQ 수정 |
| DELETE | `/api/faq/{id}` | FAQ 비활성화 (soft delete) |
| POST | `/api/faq/{id}/click` | FAQ 클릭 수 +1 |

---

### History — `/api/history`
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/history/{deviceId}` | 사용자의 채팅방 목록 (최신순) |
| GET | `/api/history/{deviceId}/{chatRoomId}` | 특정 채팅방 메시지 목록 |
| DELETE | `/api/history/{chatRoomId}` | 채팅방 + 메시지 삭제 |

---

### Notice — `/api/notices`
| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/notices/new` | AI 서버로부터 신규 공지 수신 및 저장 |

인증: `X-API-Key: hoseo-lens-secret-key` 헤더 필요.

중복 처리: `notice_id` 있으면 그 기준, 없으면 `title + date` 조합으로 중복 판단 후 무시(skip).

---

## 미구현 항목

| 항목 | 설명 |
|---|---|
| 채팅 메시지 DB 저장 | ChatServiceImpl에서 USER/ASSISTANT 메시지를 DB에 저장하는 로직 없음 |
| 사이드바 히스토리 UI | 이전 대화를 사이드바에서 클릭해 불러오는 기능 |
| 공지 알림 | 사용자가 등록한 키워드/카테고리와 신규 공지 매칭 후 FCM 푸시 발송 |
