import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 클라이언트-서버 간 통신을 위한 메시지 클래스
 * 게임의 모든 통신은 이 Message 객체를 직렬화하여 전송
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    // ========== Enums ==========
    /**
     * 메시지 타입 정의
     */
    public enum MessageType {
        // 인증 관련 (1xxx)
        LOGIN_REQUEST,          // 로그인 요청 (C → S)
        LOGIN_RESPONSE,         // 로그인 응답 (S → C)
        REGISTER_REQUEST,       // 회원가입 요청 (C → S)
        REGISTER_RESPONSE,      // 회원가입 응답 (S → C)
        LOGOUT,                 // 로그아웃 (C → S)

        // 방 관련 (2xxx)
        ROOM_LIST_REQUEST,      // 방 목록 요청 (C → S)
        ROOM_LIST_RESPONSE,     // 방 목록 응답 (S → C)
        CREATE_ROOM_REQUEST,    // 방 생성 요청 (C → S)
        CREATE_ROOM_RESPONSE,   // 방 생성 응답 (S → C)
        JOIN_ROOM_REQUEST,      // 방 입장 요청 (C → S)
        JOIN_ROOM_RESPONSE,     // 방 입장 응답 (S → C)
        JOIN_AS_SPECTATOR,      // 관전자로 입장 (C → S) - Phase 2
        SPECTATOR_LIST_UPDATE,  // 관전자 목록 업데이트 (S → C) - Phase 2
        LEAVE_ROOM,             // 방 나가기 (C → S)
        ROOM_INFO_UPDATE,       // 방 정보 업데이트 (S → C)
        KICK_PLAYER,            // 강제 퇴장 (C → S) - Phase 2

        // 게임 준비 및 시작 (3xxx)
        READY,                  // 준비 완료 (C → S)
        READY_CANCEL,           // 준비 취소 (C → S)
        READY_STATUS_UPDATE,    // 준비 상태 업데이트 (S → C)
        START_GAME_REQUEST,     // 게임 시작 요청 (C → S, 방장만)
        START_GAME,             // 게임 시작 알림 (S → C)

        // 게임 진행 (4xxx)
        TURN_INFO,              // 턴 정보 (S → C) - 현재 회차, 공격팀 등
        GUESS,                  // 숫자 추측 (C → S)
        GUESS_RESULT,           // 추측 결과 (S → C)
        TURN_TIMEOUT,           // 턴 타임아웃 (S → C)
        END_GAME,               // 게임 종료 (S → C)
        GAME_RESULT,            // 게임 결과 (S → C)
        STAY_IN_ROOM,           // 방에 머무르기 (C → S)

        // 채팅 (5xxx)
        CHAT_ALL,               // 전체 채팅 (C ↔ S)
        CHAT_ROOM,              // 방 채팅 (C ↔ S)
        CHAT_TEAM,              // 팀 채팅 (C ↔ S)
        CHAT_WHISPER,           // 귓속말 (C ↔ S)

        // 전적 및 기록 (6xxx)
        STATS_REQUEST,          // 전적 요청 (C → S)
        STATS_RESPONSE,         // 전적 응답 (S → C)
        GAME_HISTORY_REQUEST,   // 게임 기록 요청 (C → S)
        GAME_HISTORY_RESPONSE,  // 게임 기록 응답 (S → C)
        RANKING_REQUEST,        // 랭킹 요청 (C → S)
        RANKING_RESPONSE,       // 랭킹 응답 (S → C)

        // 매칭 (7xxx)
        QUICK_MATCH_REQUEST,    // 빠른 시작 (C → S)
        QUICK_MATCH_CANCEL,     // 매칭 취소 (C → S)
        MATCH_FOUND,            // 매칭 성공 (S → C)

        // 유저 상태 (8xxx)
        USER_STATUS_UPDATE,     // 유저 상태 업데이트 (S → C)
        USER_LIST_REQUEST,      // 접속자 목록 요청 (C → S)
        USER_LIST_RESPONSE,     // 접속자 목록 응답 (S → C)

        // 에러 (9xxx)
        ERROR                   // 에러 메시지 (S → C)
    }

    /**
     * 게임 모드
     */
    public enum GameMode {
        ONE_VS_ONE(2, "1v1"),       // 1대1 (2명)
        TWO_VS_TWO(4, "2v2");       // 2대2 (4명)

        private final int maxPlayers;
        private final String displayName;

        GameMode(int maxPlayers, String displayName) {
            this.maxPlayers = maxPlayers;
            this.displayName = displayName;
        }

        public int getMaxPlayers() { return maxPlayers; }
        public String getDisplayName() { return displayName; }
    }

    /**
     * 난이도 (숫자 개수)
     */
    public enum Difficulty {
        EASY(3, "하"),      // 3자리
        MEDIUM(4, "중"),    // 4자리
        HARD(5, "상");      // 5자리

        private final int digitCount;
        private final String displayName;

        Difficulty(int digitCount, String displayName) {
            this.digitCount = digitCount;
            this.displayName = displayName;
        }

        public int getDigitCount() { return digitCount; }
        public String getDisplayName() { return displayName; }
    }

    /**
     * 턴 제한 시간
     */
    public enum TurnTimeLimit {
        FIFTEEN(15, "15초"),
        THIRTY(30, "30초"),
        SIXTY(60, "60초");

        private final int seconds;
        private final String displayName;

        TurnTimeLimit(int seconds, String displayName) {
            this.seconds = seconds;
            this.displayName = displayName;
        }

        public int getSeconds() { return seconds; }
        public String getDisplayName() { return displayName; }
    }

    /**
     * 유저 상태
     */
    public enum UserStatus {
        ONLINE,         // 온라인 (로비)
        IN_ROOM,        // 방 대기 중
        IN_GAME,        // 게임 중
        OFFLINE         // 오프라인
    }

    /**
     * 방 상태
     */
    public enum RoomStatus {
        WAITING,        // 대기 중
        IN_GAME         // 게임 중
    }

    /**
     * 에러 코드
     */
    public enum ErrorCode {
        // 인증 관련 (1xxx)
        DUPLICATE_ID(1001, "ID가 이미 존재합니다"),
        LOGIN_FAILED(1002, "ID 또는 비밀번호가 일치하지 않습니다"),
        ALREADY_LOGGED_IN(1003, "이미 접속 중인 계정입니다"),

        // 방 관련 (2xxx)
        ROOM_FULL(2001, "방이 가득 찼습니다"),
        ROOM_NOT_FOUND(2002, "존재하지 않는 방입니다"),
        ROOM_IN_GAME(2003, "게임 진행 중인 방은 입장할 수 없습니다"),
        WRONG_PASSWORD(2004, "비밀번호가 일치하지 않습니다"),
        NOT_ENOUGH_PLAYERS(2005, "인원이 부족합니다"),
        NOT_ROOM_MASTER(2006, "방장만 게임을 시작할 수 있습니다"),
        SPECTATOR_NOT_ALLOWED(2007, "관전이 허용되지 않은 방입니다"),
        CANNOT_KICK_PLAYER(2008, "플레이어를 강제 퇴장시킬 수 없습니다"),

        // 게임 관련 (3xxx)
        TURN_TIMEOUT(3001, "입력 시간이 초과되었습니다"),
        INVALID_INPUT_FORMAT(3002, "잘못된 입력 형식입니다"),
        DUPLICATE_DIGITS(3003, "중복된 숫자는 사용할 수 없습니다"),
        OUT_OF_RANGE(3004, "1~9 범위의 숫자만 사용할 수 있습니다"),

        // 서버 관련 (9xxx)
        SERVER_FULL(9001, "서버 정원이 가득 찼습니다"),
        UNKNOWN_ERROR(9999, "알 수 없는 오류가 발생했습니다");

        private final int code;
        private final String message;

        ErrorCode(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() { return code; }
        public String getMessage() { return message; }
    }

    // ========== Fields ==========

    // 기본 필드
    private MessageType type;           // 메시지 타입
    private String userId;              // 메시지를 보낸 사용자 ID
    private String content;             // 메시지 내용 (채팅, 추가 정보 등)
    private String timestamp;    // 메시지 생성 시간

    // 인증 관련
    private String password;            // 비밀번호 (로그인/회원가입)
    private String character;           // 캐릭터 (회원가입)
    private boolean success;            // 성공 여부 (응답용)

    // 방 관련
    private int roomId;                 // 방 번호
    private String roomName;            // 방 이름
    private String roomMaster;          // 방장 ID
    private boolean isPrivate;          // 비공개 방 여부
    private String roomPassword;        // 방 비밀번호
    private int currentPlayers;         // 현재 인원
    private int maxPlayers;             // 최대 인원
    private RoomStatus roomStatus;      // 방 상태

    // 게임 설정
    private GameMode gameMode;          // 게임 모드
    private Difficulty difficulty;      // 난이도
    private TurnTimeLimit turnTimeLimit; // 턴 제한 시간

    // 게임 진행
    private String gameId;              // 게임 ID (기록용)
    private int round;                  // 현재 회차 (1~9)
    private boolean isTop;              // 초공인지 말공인지 (true: 초, false: 말)
    private String currentTurnPlayer;   // 현재 턴 플레이어 ID
    private int teamNumber;             // 팀 번호 (1 or 2, 팀전에서만 사용)

    // 숫자야구 결과
    private String guess;               // 추측한 숫자
    private int strike;                 // 스트라이크 개수
    private int ball;                   // 볼 개수

    // 게임 결과
    private String winnerId;            // 승자 ID
    private int winnerTeam;             // 승리 팀 번호 (팀전)
    private boolean isDraw;             // 무승부 여부

    // 채팅 관련
    private String targetUserId;        // 귓속말 대상 ID

    // 유저 상태
    private UserStatus userStatus;      // 유저 상태

    // 준비 상태
    private boolean isReady;            // 준비 완료 여부

    // 관전 모드 (Phase 2)
    private boolean isSpectator;        // 관전자 여부
    private boolean allowSpectators;    // 관전 허용 여부

    // 강제 퇴장 (Phase 2)
    private String targetPlayerId;      // 강제 퇴장 대상 플레이어 ID

    // 에러 관련
    private ErrorCode errorCode;        // 에러 코드
    private String errorMessage;        // 에러 메시지

    private Serializable data;
    // ========== Constructors ==========

    /**
     * 기본 생성자
     */
    public Message(MessageType type) {
        this.type = type;
        this.timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }


    /**
     * 기본 메시지 생성자 (타입 + 유저 + 내용)
     */
    public Message(MessageType type, String userId, String content) {
        this.type = type;
        this.userId = userId;
        this.content = content;
        this.timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * 간단한 메시지 생성자 (타입 + 유저)
     */
    public Message(MessageType type, String userId) {
        this.type = type;
        this.userId = userId;
        this.timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * 로그인 요청 메시지 생성
     */
    public static Message createLoginRequest(String userId, String password) {
        Message msg = new Message(MessageType.LOGIN_REQUEST, userId);
        msg.password = password;
        return msg;
    }

    /**
     * 회원가입 요청 메시지 생성
     */
    public static Message createRegisterRequest(String userId, String password, String character) {
        Message msg = new Message(MessageType.REGISTER_REQUEST, userId);
        msg.password = password;
        msg.character = character;
        return msg;
    }

    /**
     * 방 생성 요청 메시지 생성
     */
    public static Message createCreateRoomRequest(String userId, String roomName,
            GameMode gameMode, Difficulty difficulty, TurnTimeLimit turnTimeLimit,
            boolean isPrivate, String roomPassword) {
        Message msg = new Message(MessageType.CREATE_ROOM_REQUEST, userId);
        msg.roomName = roomName;
        msg.gameMode = gameMode;
        msg.difficulty = difficulty;
        msg.turnTimeLimit = turnTimeLimit;
        msg.isPrivate = isPrivate;
        msg.roomPassword = roomPassword;
        return msg;
    }

    /**
     * 방 생성 요청 메시지 생성 (관전 허용 옵션 포함) - Phase 2
     */
    public static Message createCreateRoomRequest(String userId, String roomName,
            GameMode gameMode, Difficulty difficulty, TurnTimeLimit turnTimeLimit,
            boolean isPrivate, String roomPassword, boolean allowSpectators) {
        Message msg = createCreateRoomRequest(userId, roomName, gameMode, difficulty,
                turnTimeLimit, isPrivate, roomPassword);
        msg.allowSpectators = allowSpectators;
        return msg;
    }

    /**
     * 방 입장 요청 메시지 생성
     */
    public static Message createJoinRoomRequest(String userId, int roomId, String roomPassword) {
        Message msg = new Message(MessageType.JOIN_ROOM_REQUEST, userId);
        msg.roomId = roomId;
        msg.roomPassword = roomPassword;
        return msg;
    }

    /**
     * 관전자로 입장 요청 메시지 생성
     */
    public static Message createJoinAsSpectatorRequest(String userId, int roomId, String roomPassword) {
        Message msg = new Message(MessageType.JOIN_AS_SPECTATOR, userId);
        msg.roomId = roomId;
        msg.roomPassword = roomPassword;
        msg.isSpectator = true;
        return msg;
    }

    /**
     * 강제 퇴장 요청 메시지 생성
     */
    public static Message createKickPlayerRequest(String userId, int roomId, String targetPlayerId) {
        Message msg = new Message(MessageType.KICK_PLAYER, userId);
        msg.roomId = roomId;
        msg.targetPlayerId = targetPlayerId;
        return msg;
    }

    /**
     * 채팅 메시지 생성
     */
    public static Message createChatMessage(MessageType chatType, String userId, String content, String targetUserId) {
        Message msg = new Message(chatType, userId, content);
        msg.targetUserId = targetUserId; // 귓속말인 경우에만 사용
        return msg;
    }

    /**
     * 추측 메시지 생성
     */
    public static Message createGuessMessage(String userId, String guess) {
        Message msg = new Message(MessageType.GUESS, userId);
        msg.guess = guess;
        return msg;
    }

    /**
     * 추측 결과 메시지 생성
     */
    public static Message createGuessResult(String userId, String guess, int strike, int ball) {
        Message msg = new Message(MessageType.GUESS_RESULT, userId);
        msg.guess = guess;
        msg.strike = strike;
        msg.ball = ball;
        return msg;
    }

    /**
     * 에러 메시지 생성
     */
    public static Message createErrorMessage(ErrorCode errorCode) {
        Message msg = new Message(MessageType.ERROR);
        msg.errorCode = errorCode;
        msg.errorMessage = errorCode.getMessage();
        return msg;
    }

    /**
     * 에러 메시지 생성 (커스텀 메시지)
     */
    public static Message createErrorMessage(ErrorCode errorCode, String customMessage) {
        Message msg = new Message(MessageType.ERROR);
        msg.errorCode = errorCode;
        msg.errorMessage = customMessage;
        return msg;
    }

    // ========== Getters / Setters ==========

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getCharacter() { return character; }
    public void setCharacter(String character) { this.character = character; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public int getRoomId() { return roomId; }
    public void setRoomId(int roomId) { this.roomId = roomId; }

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public String getRoomMaster() { return roomMaster; }
    public void setRoomMaster(String roomMaster) { this.roomMaster = roomMaster; }

    public boolean isPrivate() { return isPrivate; }
    public void setPrivate(boolean isPrivate) { this.isPrivate = isPrivate; }

    public String getRoomPassword() { return roomPassword; }
    public void setRoomPassword(String roomPassword) { this.roomPassword = roomPassword; }

    public int getCurrentPlayers() { return currentPlayers; }
    public void setCurrentPlayers(int currentPlayers) { this.currentPlayers = currentPlayers; }

    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }

    public RoomStatus getRoomStatus() { return roomStatus; }
    public void setRoomStatus(RoomStatus roomStatus) { this.roomStatus = roomStatus; }

    public GameMode getGameMode() { return gameMode; }
    public void setGameMode(GameMode gameMode) { this.gameMode = gameMode; }

    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty difficulty) { this.difficulty = difficulty; }

    public TurnTimeLimit getTurnTimeLimit() { return turnTimeLimit; }
    public void setTurnTimeLimit(TurnTimeLimit turnTimeLimit) { this.turnTimeLimit = turnTimeLimit; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public int getRound() { return round; }
    public void setRound(int round) { this.round = round; }

    public boolean isTop() { return isTop; }
    public void setTop(boolean isTop) { this.isTop = isTop; }

    public String getCurrentTurnPlayer() { return currentTurnPlayer; }
    public void setCurrentTurnPlayer(String currentTurnPlayer) { this.currentTurnPlayer = currentTurnPlayer; }

    public int getTeamNumber() { return teamNumber; }
    public void setTeamNumber(int teamNumber) { this.teamNumber = teamNumber; }

    public String getGuess() { return guess; }
    public void setGuess(String guess) { this.guess = guess; }

    public int getStrike() { return strike; }
    public void setStrike(int strike) { this.strike = strike; }

    public int getBall() { return ball; }
    public void setBall(int ball) { this.ball = ball; }

    public String getWinnerId() { return winnerId; }
    public void setWinnerId(String winnerId) { this.winnerId = winnerId; }

    public int getWinnerTeam() { return winnerTeam; }
    public void setWinnerTeam(int winnerTeam) { this.winnerTeam = winnerTeam; }

    public boolean isDraw() { return isDraw; }
    public void setDraw(boolean isDraw) { this.isDraw = isDraw; }

    public String getTargetUserId() { return targetUserId; }
    public void setTargetUserId(String targetUserId) { this.targetUserId = targetUserId; }

    public UserStatus getUserStatus() { return userStatus; }
    public void setUserStatus(UserStatus userStatus) { this.userStatus = userStatus; }

    public boolean isReady() { return isReady; }
    public void setReady(boolean isReady) { this.isReady = isReady; }

    public boolean isSpectator() { return isSpectator; }
    public void setSpectator(boolean isSpectator) { this.isSpectator = isSpectator; }

    public boolean isAllowSpectators() { return allowSpectators; }
    public void setAllowSpectators(boolean allowSpectators) { this.allowSpectators = allowSpectators; }

    public String getTargetPlayerId() { return targetPlayerId; }
    public void setTargetPlayerId(String targetPlayerId) { this.targetPlayerId = targetPlayerId; }

    public ErrorCode getErrorCode() { return errorCode; }
    public void setErrorCode(ErrorCode errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Serializable getData() { return data; }
    public void setData(Serializable data) { this.data = data; }

    // ========== Utility Methods ==========

    /**
     * 결과 설정 (Strike, Ball)
     */
    public void setResult(int strike, int ball) {
        this.strike = strike;
        this.ball = ball;
    }

    /**
     * 회차 정보 문자열 반환 (예: "1회 초", "3회 말")
     */
    public String getRoundInfo() {
        if (round <= 0) return "";
        return round + "회 " + (isTop ? "초" : "말");
    }

    /**
     * 디버깅용 toString
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(type).append("]");

        switch (type) {
            case LOGIN_REQUEST:
            case REGISTER_REQUEST:
                sb.append(" User: ").append(userId);
                break;

            case CHAT_ALL:
            case CHAT_ROOM:
            case CHAT_TEAM:
                sb.append(" ").append(userId).append(": ").append(content);
                break;

            case CHAT_WHISPER:
                sb.append(" ").append(userId).append(" → ").append(targetUserId).append(": ").append(content);
                break;

            case GUESS:
                sb.append(" ").append(userId).append(" guessed: ").append(guess);
                break;

            case GUESS_RESULT:
                sb.append(" ").append(userId).append(": ").append(guess)
                  .append(" → ").append(strike).append("S ").append(ball).append("B");
                break;

            case START_GAME:
                sb.append(" Game started! [").append(gameMode.getDisplayName())
                  .append(" / ").append(difficulty.getDisplayName()).append("]");
                break;

            case TURN_INFO:
                sb.append(" ").append(getRoundInfo());
                break;

            case END_GAME:
            case GAME_RESULT:
                if (isDraw) {
                    sb.append(" 무승부!");
                } else if (winnerTeam > 0) {
                    sb.append(" Team ").append(winnerTeam).append(" 승리!");
                } else {
                    sb.append(" ").append(winnerId).append(" 승리!");
                }
                break;

            case ERROR:
                sb.append(" [").append(errorCode.getCode()).append("] ").append(errorMessage);
                break;

            default:
                if (content != null && !content.isEmpty()) {
                    sb.append(" ").append(content);
                }
                break;
        }

        return sb.toString();
    }
}
