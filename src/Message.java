// 2271439 조영석

import java.io.Serializable;

public class Message implements Serializable {
    public enum MessageType {
        CONNECT, // 클라이언트 연결
        CHAT, // 일반 채팅 메시지
        GUESS, // 숫자야구 추측 입력
        RESULT, // 숫자야구 결과 반환
        START_GAME, // 게임 시작 알림
        DISCONNECT, // 클라이언트 연결 해제
    }

    private String userId; //메시지를 보낸 클라이언트 ID
    private String content; // 메시지 본문
    private MessageType type; // 메시지 타입 구분

    private int strike; // 숫자야구 결과: 스트라이크
    private int ball; // 숫자야구 결과: 볼

    // 생성자
    public Message(MessageType type, String userId, String content) {
        this.type = type;
        this.userId = userId;
        this.content = content;
    }

    // Getter / Setter
    public String getUserId() {
        return userId;
    }
    public String getContent() {
        return content;
    }
    public MessageType getType() {
        return type;
    }
    public int getStrick() { return strike; }
    public int getBall() { return ball; }

    // 숫자야구 결과 저장
    public void setResult(int strike, int ball) {
        this.strike = strike;
        this.ball = ball;
    }

    // 디버깅용 출력
    @Override
    public String toString() {
        switch (type) {
            case CONNECT:
                return "newClient is connected: " + userId;
            case CHAT:
                return userId + ": " + content;
            case RESULT:
                return userId + ": " + content + " (" + strike + "S " + ball + "B)";
            case START_GAME:
                return "[START GAME]" + content;
            case DISCONNECT:
                return userId + " is disconnected";
            default:
                return content;
        }
    }

}
