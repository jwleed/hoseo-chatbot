# 2026-05-09 작업 내역

## 1. 공지 이벤트 수신 기능 정비

AI 서버에서 크롤링한 공지를 백엔드로 전송할 때 제대로 저장되도록 수정.

- `NoticeEntity` — `noticeId`, `category`, `majorCategory`, `target`, `entity` 컬럼 추가
- `NoticeRepository` — `existsByNoticeId` 쿼리 메서드 추가
- `NoticeEventServiceImpl` — `noticeId` 기준 중복 체크, 각 필드 올바른 컬럼에 저장 (기존 `category`를 `content`에 저장하던 버그 수정)
- `application.properties` — `spring.jackson.property-naming-strategy=SNAKE_CASE` 추가 (AI 서버가 보내는 snake_case JSON이 Java camelCase DTO에 매핑되도록)

---

## 2. HistoryService 구현

채팅 히스토리 조회/삭제 서비스 로직 작성.

- `getRooms` — deviceId로 채팅방 목록 최신순 반환
- `getMessages` — chatRoomId로 메시지 목록 반환, 다른 사용자 접근 시 403
- `deleteRoom` — 메시지 먼저 삭제 후 채팅방 삭제, 없으면 404

---

## 3. 테스트 UI 개편

`test.html`을 ChatGPT/Claude 스타일로 전면 개편.

- 좌측 다크 사이드바 — 새 대화 버튼, 테스트 도구 패널 전환
- 채팅 영역 — 아바타 + 텍스트 피드 방식 (말풍선 제거)
- 입력창 — 하단 중앙 고정, Enter 전송 / Shift+Enter 줄바꿈
- FAQ / History / Notice 테스트 패널 통합

---

## 4. MySQL Docker 환경 구성

로컬 H2와 MySQL을 Spring Profile로 분리.

- `local` 프로필 — H2 인메모리 (기본 개발용, 앱 종료 시 데이터 사라짐)
- `dev` 프로필 — MySQL 8 Docker (데이터 유지, 팀 협업용)
- `build.gradle.kts` — MySQL 커넥터 의존성 추가

```bash
# Docker MySQL 실행 (포트 3307)
docker run -d --name hoseo-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=hoseo_chatbot \
  -p 3307:3306 mysql:8
```

---

## 5. FAQ 관리자 보호

FAQ 등록/수정/삭제 API에 `X-Admin-Key` 헤더 인증 추가.

| 엔드포인트 | 접근 |
|---|---|
| `GET /api/faq`, `GET /api/faq/top` | 누구나 |
| `POST /api/faq/{id}/click` | 누구나 |
| `POST`, `PUT`, `DELETE /api/faq` | `X-Admin-Key` 필요 |

---

## 미구현 (다음 작업)

- 채팅 메시지 DB 저장 (ChatServiceImpl에서 USER/ASSISTANT 저장 로직)
- 사이드바 이전 대화 목록 UI
- 공지 카테고리 기반 알림 (FCM 푸시)
