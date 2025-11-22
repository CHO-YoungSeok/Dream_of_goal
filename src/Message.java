// 2271439 조영석

import java.io.Serializable;

public class Message implements Serializable {
    public enum MessageType {
        CONNECT,    // 클라이언트 연결
        DISCONNECT, // 클라이언트 연결 해제
        CHAT        // 일반 채팅 메시지
    }

    private String userId;
    private String content;
    private MessageType type;

    public Message(MessageType type, String userId, String content) {
        this.type = type;
        this.userId = userId;
        this.content = content;
    }

    public String getUserId() {
        return userId;
    }

    public String getContent() {
        return content;
    }

    public MessageType getType() {
        return type;
    }

    @Override
    public String toString() {
        switch (type) {
            case CONNECT:
                return "newClient is connected: " + userId;
            case DISCONNECT:
                return userId + " is disconnected";
            case CHAT:
                return userId + ": " + content;
            default:
                return content;
        }
    }
}
