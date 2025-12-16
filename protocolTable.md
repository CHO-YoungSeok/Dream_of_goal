# 서버-클라이언트 통신 프로토콜 테이블

## 1. 인증 (Authentication)

| 방향 | 메시지 타입 | 용도 | 파라미터 |
|---|---|---|---|
| C→S | LOGIN_REQUEST | 로그인 요청 | userId(String), password(String) |
| S→C | LOGIN_RESPONSE | 로그인 응답 | success(boolean), errorCode(ErrorCode), errorMessage(String) |
| C→S | REGISTER_REQUEST | 회원가입 요청 | userId(String), password(String), character(String) |
| S→C | REGISTER_RESPONSE | 회원가입 응답 | success(boolean), errorCode(ErrorCode), errorMessage(String) |
| C→S | LOGOUT | 로그아웃 | userId(String) |

**에러 코드**: DUPLICATE_ID(1001), LOGIN_FAILED(1002), ALREADY_LOGGED_IN(1003)

---

## 2. 방 관리 (Room Management)

| 방향 | 메시지 타입 | 용도 | 파라미터 |
|---|---|---|---|
| C→S | ROOM_LIST_REQUEST | 방 목록 요청 | userId(String) |
| S→C | ROOM_LIST_RESPONSE | 방 목록 응답 | roomList(Vector&lt;Message&gt;) |
| C→S | CREATE_ROOM_REQUEST | 방 생성 요청 | userId(String), roomName(String), gameMode(GameMode), difficulty(Difficulty), turnTimeLimit(TurnTimeLimit), roomPassword(String) |
| S→C | CREATE_ROOM_RESPONSE | 방 생성 응답 | success(boolean), roomId(int), roomName(String), gameMode(GameMode), difficulty(Difficulty), turnTimeLimit(TurnTimeLimit) |
| C→S | JOIN_ROOM_REQUEST | 방 참가 요청 | userId(String), roomId(int), roomPassword(String) |
| S→C | JOIN_ROOM_RESPONSE | 방 참가 응답 | success(boolean), roomId(int), roomName(String), currentPlayers(int), maxPlayers(int), roomStatus(RoomStatus) |
| C→S | EDIT_ROOM_REQUEST | 방 설정 변경 요청 | userId(String), roomId(int), roomName(String), difficulty(Difficulty), turnTimeLimit(TurnTimeLimit), roomPassword(String) |
| S→C | EDIT_ROOM_RESPONSE | 방 설정 변경 응답 | success(boolean), roomId(int), updated room info |
| C→S | LEAVE_ROOM | 방 나가기 | userId(String) |
| S→C | ROOM_INFO_UPDATE | 방 정보 업데이트 | roomId(int), roomName(String), currentPlayers(int), maxPlayers(int), roomStatus(RoomStatus), playerList(List&lt;String&gt;), readyStatus(Hashtable&lt;String, Boolean&gt;) |
| C→S | KICK_PLAYER | 플레이어 강퇴 | userId(String), roomId(int), targetPlayerId(String) |

**게임 모드**: ONE_VS_ONE(2인), TWO_VS_TWO(4인)
**난이도**: EASY(3자리), MEDIUM(4자리), HARD(5자리)
**턴 제한시간**: FIFTEEN(15초), THIRTY(30초), SIXTY(60초)
**방 상태**: WAITING(대기중), IN_GAME(게임중)

**에러 코드**: ROOM_FULL(2001), ROOM_NOT_FOUND(2002), ROOM_IN_GAME(2003), WRONG_PASSWORD(2004), NOT_ENOUGH_PLAYERS(2005), NOT_ROOM_MASTER(2006), CANNOT_KICK_PLAYER(2008)

---

## 3. 게임 준비 (Game Preparation)

| 방향 | 메시지 타입 | 용도 | 파라미터 |
|---|---|---|---|
| C→S | READY | 준비 완료 | userId(String) |
| C→S | READY_CANCEL | 준비 취소 | userId(String) |
| S→C | READY_STATUS_UPDATE | 준비 상태 업데이트 | readyStatus(Hashtable&lt;String, Boolean&gt;) |
| C→S | START_GAME_REQUEST | 게임 시작 요청 | userId(String) - 방장만 가능 |
| S→C | START_GAME | 게임 시작 알림 | gameMode(GameMode), difficulty(Difficulty), turnTimeLimit(TurnTimeLimit), gameId(String), content(String) |

---

## 4. 게임 진행 (Game Progression)

| 방향 | 메시지 타입 | 용도 | 파라미터 |
|---|---|---|---|
| S→C | TURN_INFO | 턴 정보 전달 | round(int), isTop(boolean), currentTurnPlayer(String), content(String) |
| C→S | GUESS | 숫자 추측 전달 | userId(String), guess(String) |
| S→C | GUESS_RESULT | 판정 결과 전달 | userId(String), guess(String), strike(int), ball(int) |
| S→C | TURN_TIMEOUT | 턴 타임아웃 | (empty) |
| S→C | END_GAME | 게임 종료 | winnerId(String), winnerTeam(int), isDraw(boolean), content(String) |
| S→C | GAME_RESULT | 게임 결과 | winnerId(String), winnerTeam(int), isDraw(boolean), content(String) |
| C→S | STAY_IN_ROOM | 게임 후 방에 남기 | userId(String) |

**게임 파라미터**:
- **round**: 1-9 (야구 이닝)
- **isTop**: true(초 = 공격), false(말 = 수비)
- **strike/ball**: 스트라이크/볼 개수
- **guess**: 3-5자리 숫자 (난이도에 따라)

**에러 코드**: TURN_TIMEOUT(3001), INVALID_INPUT_FORMAT(3002), DUPLICATE_DIGITS(3003), OUT_OF_RANGE(3004)

---

## 5. 채팅 (Chat)

| 방향 | 메시지 타입 | 용도 | 파라미터 |
|---|---|---|---|
| C→S | CHAT_ALL | 전체 채팅 전송 | userId(String), content(String) |
| S→C | CHAT_ALL | 전체 채팅 중계 | userId(String), content(String) |
| C→S | CHAT_ROOM | 방 채팅 전송 | userId(String), content(String), roomId(int) |
| S→C | CHAT_ROOM | 방 채팅 중계 | userId(String), content(String) |
| C→S | CHAT_TEAM | 팀 채팅 전송 | userId(String), content(String), teamNumber(int) |
| S→C | CHAT_TEAM | 팀 채팅 중계 | userId(String), content(String) |
| C→S | CHAT_WHISPER | 귓속말 전송 | userId(String), targetUserId(String), content(String) |
| S→C | CHAT_WHISPER | 귓속말 중계 | userId(String), targetUserId(String), content(String) |

**특수 명령어**: `/stats [userId]` - 플레이어 통계 조회

---

## 6. 통계 및 기록 (Statistics & History)

| 방향 | 메시지 타입 | 용도 | 파라미터 |
|---|---|---|---|
| C→S | STATS_REQUEST | 사용자 통계 요청 | userId(String) - optional in content |
| S→C | STATS_RESPONSE | 통계 응답 | data(Hashtable: wins, losses, draws, winRate) |
| C→S | GAME_HISTORY_REQUEST | 게임 기록 요청 | userId(String) |
| S→C | GAME_HISTORY_RESPONSE | 게임 기록 응답 | data(Vector&lt;Hashtable&gt;) |
| C→S | RANKING_REQUEST | 랭킹 요청 | (empty) |
| S→C | RANKING_RESPONSE | 랭킹 응답 | data(Vector&lt;Hashtable&gt;) |

**통계 데이터 구조**:
```
{
  "wins": int,
  "losses": int,
  "draws": int,
  "winRate": double (%)
}
```

**게임 기록 구조**:
```
{
  "gameId": String,
  "timestamp": String (yyyy-MM-dd HH:mm:ss),
  "participants": String (쉼표로 구분된 userId),
  "gameMode": String (1v1 or 2v2),
  "difficulty": String (하/중/상),
  "winner": String (플레이어 ID or "Team#" or "Draw")
}
```

---

## 7. 매칭 (Matching)

| 방향 | 메시지 타입 | 용도 | 파라미터 |
|---|---|---|---|
| C→S | QUICK_MATCH_REQUEST | 빠른 매칭 요청 | userId(String), gameMode(GameMode), difficulty(Difficulty) |
| C→S | QUICK_MATCH_CANCEL | 빠른 매칭 취소 | userId(String) |
| S→C | MATCH_FOUND | 매칭 성공 | roomId(int), matched players |

---

## 8. 사용자 상태 (User Status)

| 방향 | 메시지 타입 | 용도 | 파라미터 |
|---|---|---|---|
| C→S | USER_LIST_REQUEST | 온라인 사용자 목록 요청 | userId(String) |
| S→C | USER_LIST_RESPONSE | 사용자 목록 응답 | data(Vector&lt;Hashtable&gt;) |
| S→C | USER_STATUS_UPDATE | 사용자 상태 업데이트 | userStatusMap(Hashtable&lt;String, UserStatus&gt;) |

**사용자 상태**: ONLINE(온라인), IN_ROOM(방 대기중), IN_GAME(게임중), OFFLINE(오프라인)

**사용자 목록 데이터 구조**:
```
{
  "userId": String,
  "status": UserStatus
}
```

---

## 9. 에러 처리 (Error Handling)

| 방향 | 메시지 타입 | 용도 | 파라미터 |
|---|---|---|---|
| S→C | ERROR | 에러 전달 | errorCode(ErrorCode), errorMessage(String) |

**에러 코드**:
- SERVER_FULL(9001) - 서버 정원 초과 (최대 100명)
- UNKNOWN_ERROR(9999) - 알 수 없는 에러

---

## 네트워크 설정 (Network Configuration)

- **프로토콜**: TCP Sockets
- **직렬화**: Java Object Serialization (ObjectInputStream/ObjectOutputStream)
- **설정 파일**: `server.txt`
  - 1행: 서버 IP 주소 (기본값: localhost)
  - 2행: 서버 포트 (기본값: 54321)
- **최대 동시 접속**: 100명

---

## Message 클래스 주요 필드

```java
// 공통 필드
MessageType type              // 메시지 유형
String userId                 // 발신자/대상 사용자 ID
String content                // 텍스트 내용
String timestamp              // 타임스탬프 (ISO LocalDateTime)
boolean success               // 응답 성공 여부
ErrorCode errorCode           // 에러 분류
String errorMessage           // 에러 설명
Serializable data             // 복합 데이터 (Vector, Hashtable)

// 인증 필드
String password               // 비밀번호
String character              // 캐릭터 선택

// 방 필드
int roomId                    // 방 ID
String roomName               // 방 이름
String roomMaster             // 방장 ID
String roomPassword           // 방 비밀번호
int currentPlayers            // 현재 플레이어 수
int maxPlayers                // 최대 플레이어 수
RoomStatus roomStatus         // 방 상태
GameMode gameMode             // 게임 모드
Difficulty difficulty         // 난이도
TurnTimeLimit turnTimeLimit   // 턴 제한시간

// 게임 필드
String gameId                 // 게임 세션 ID
int round                     // 현재 라운드 (1-9)
boolean isTop                 // 초/말
String currentTurnPlayer      // 현재 턴 플레이어
int teamNumber                // 팀 번호
String guess                  // 추측 숫자
int strike                    // 스트라이크 수
int ball                      // 볼 수
String winnerId               // 승자 ID
int winnerTeam                // 승리 팀 번호
boolean isDraw                // 무승부 여부
String myAnswerKey            // 플레이어의 정답 숫자

// 채팅/통신 필드
String targetUserId           // 귓속말 대상
List<String> connectedUsers   // 온라인 플레이어 목록
Map<String, UserStatus> userStatusMap  // 사용자 상태 맵
```
