| 방향 | 메시지 타입 | 용도 | 파라미터 |
|---|---|---|---|
| C↔S | CONNECT_REQUEST | 연결 요청 | userld(String) |
| S↔C | START_GAME | 게임 시작 알림 | content(String) |
| C↔S | GUESS | 추측 전달 | userID(String), content(String) |
| S↔C | RESULT | 판정 결과 전달 | userID(String), strike(int), ball(int) |
| C↔S | CHAT | 채팅 전송 | userID(String), content(String) |
| S↔C | CHAT | 채팅 중계 | userID(String), content(String) |