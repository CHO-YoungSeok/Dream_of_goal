package server;

import common.Message;
import java.io.*;
import java.net.Socket;
import java.util.Hashtable;
import java.util.Vector;

// 클라이언트 핸들러

public class ClientHandler implements Runnable {
    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;

    // 클라이언트 세션 정보
    String userId;
    GameRoom currentRoom;
    Message.UserStatus userStatus = Message.UserStatus.ONLINE;

    private ServerCore serverCore;

    public ClientHandler(Socket socket, ServerCore serverCore) {
        this.socket = socket;
        this.serverCore = serverCore;

        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            serverCore.printDisplay("스트림 생성 오류: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            Message msg;

            // 초기 로그인/회원가입 대기
            while (userId == null && (msg = (Message) in.readObject()) != null) {
                if (msg.getType() == Message.MessageType.LOGIN_REQUEST ||
                        msg.getType() == Message.MessageType.REGISTER_REQUEST) {
                    handleMessage(msg);
                } else {
                    sendMessage(Message.createErrorMessage(Message.ErrorCode.UNKNOWN_ERROR,
                            "로그인 또는 회원가입 요청만 가능합니다."));
                }
            }

            // 로그인 후 메인 루프
            while (userId != null && (msg = (Message) in.readObject()) != null) {
                handleMessage(msg);
            }
        } catch (IOException e) {
            if (userId != null) {
                serverCore.printDisplay(userId + " 연결 종료");
            }
        } catch (ClassNotFoundException e) {
            serverCore.printDisplay("메시지 클래스 오류: " + e.getMessage());
        } finally {
            close();
        }
    }

    // 메시지 처리
    private void handleMessage(Message msg) {
        // 모든 수신 메시지 로깅
        logClientMessage(msg);

        switch (msg.getType()) {
            // 인증 및 기본 관리
            case LOGIN_REQUEST:
                handleLogin(msg);
                break;
            case REGISTER_REQUEST:
                handleRegister(msg);
                break;
            case LOGOUT:
                close();
                break;
            case USER_LIST_REQUEST:
                serverCore.sendUserListToClient(this);
                break;

            // 방 목록 및 생성/입장/퇴장/수정
            case ROOM_LIST_REQUEST:
                handleRoomListRequest();
                break;
            case CREATE_ROOM_REQUEST:
                handleCreateRoom(msg);
                break;
            case JOIN_ROOM_REQUEST:
                handleJoinRoom(msg);
                break;
            case EDIT_ROOM_REQUEST:
                handleEditRoom(msg);
                break;
            case LEAVE_ROOM:
                handleLeaveRoom();
                break;

            // 게임 준비 및 시작
            case READY:
                handleReady(true);
                break;
            case READY_CANCEL:
                handleReady(false);
                break;
            case START_GAME_REQUEST:
                handleStartGameRequest();
                break;
            case KICK_PLAYER:
                handleKickPlayer(msg);
                break;

            // 게임 진행
            case GUESS:
                if (currentRoom != null) {
                    boolean isAnswerSetupPhase = false;

                    if (currentRoom.isGameRunning) {
                        if (currentRoom.gameMode == Message.GameMode.ONE_VS_ONE) {
                            // 1v1: 아직 내 정답을 설정하지 않았을 때
                            isAnswerSetupPhase = !currentRoom.playerAnswers.containsKey(userId);
                        } else if (currentRoom.gameMode == Message.GameMode.TWO_VS_TWO) {
                            int playerTeam = currentRoom.playerTeams.getOrDefault(userId, 0);
                            String leaderId = currentRoom.teamLeaders.get(playerTeam);

                            // 2v2: 아직 우리 팀 정답을 설정하지 않았고, 내가 팀 대표일 때
                            isAnswerSetupPhase = playerTeam != 0 &&
                                    !currentRoom.isTeamAnswerSet(playerTeam) &&
                                    userId.equals(leaderId);
                        }
                    }

                    if (isAnswerSetupPhase) {
                        handleGameStartAnswer(msg);
                    } else {
                        handleGuess(msg);
                    }
                }
                break;

            // 채팅
            case CHAT_ROOM:
                handleRoomChat(msg);
                break;
            case CHAT_TEAM:
                handleTeamChat(msg);
                break;
            case CHAT_ALL:
                handleAllChat(msg);
                break;
            case CHAT_WHISPER:
                handleWhisper(msg);
                break;

            // 전적 /기록
            case STATS_REQUEST:
                handleStatsRequest(msg);
                break;
            case GAME_HISTORY_REQUEST:
                handleGameHistoryRequest(msg);
                break;
            default:
                serverCore.printDisplay(userId + "로부터 알 수 없는 메시지: " + msg.getType());
        }
    }

    // --- 인증 관련 ---

    // 로그인 처리
    private void handleLogin(Message msg) {
        String userId = msg.getUserId();
        String password = msg.getPassword();

        if (serverCore.isAlreadyLoggedIn(userId)) {
            sendMessage(Message.createErrorMessage(Message.ErrorCode.ALREADY_LOGGED_IN));
            return;
        }

        if (serverCore.getAuthManager().authenticateUser(userId, password)) {
            this.userId = userId;
            serverCore.addClient(this);

            Message response = new Message(Message.MessageType.LOGIN_RESPONSE, userId);
            response.setSuccess(true);
            response.setContent("로그인 성공");
            sendMessage(response);
            serverCore.printDisplay(userId + " 로그인 성공");
            serverCore.broadcastUserList();
        } else {
            sendMessage(Message.createErrorMessage(Message.ErrorCode.LOGIN_FAILED));
        }
    }

    // 회원가입 처리
    private void handleRegister(Message msg) {
        String userId = msg.getUserId();
        String password = msg.getPassword();
        String character = msg.getCharacter();

        if (serverCore.getAuthManager().registerUser(userId, password, character)) {
            Message response = new Message(Message.MessageType.REGISTER_RESPONSE, userId);
            response.setSuccess(true);
            response.setContent("회원가입 성공");
            sendMessage(response);
            serverCore.printDisplay(userId + " 회원가입 성공");
        } else {
            sendMessage(Message.createErrorMessage(Message.ErrorCode.DUPLICATE_ID));
        }
    }

    // --- 방 관련 ---
    // 방 목록 요청 처리
    private void handleRoomListRequest() {
        Vector<Message> roomList = serverCore.getRoomManager().getRoomList();
        Message response = new Message(Message.MessageType.ROOM_LIST_RESPONSE, "SERVER");
        response.setData(roomList);
        sendMessage(response);
    }

    // 방 생성 처리
    private void handleCreateRoom(Message msg) {
        if (currentRoom != null) return;

        GameRoom room = serverCore.getRoomManager().createRoom(
                msg.getRoomName(),
                userId,
                msg.getGameMode(),
                msg.getDifficulty(),
                msg.getTurnTimeLimit(),
                msg.getRoomPassword()
        );

        if (room != null) {
            currentRoom = room;
            room.addPlayer(this); // 방에 플레이어 등록
            userStatus = Message.UserStatus.IN_ROOM;

            Message response = room.createRoomUpdateMessage("방 생성 성공");
            response.setType(Message.MessageType.CREATE_ROOM_RESPONSE);
            response.setSuccess(true);
            sendMessage(response);

            //Message updateMsg = room.createRoomUpdateMessage(null);
            //sendMessage(updateMsg);

            serverCore.broadcastUserList(); // 상태 변경 브로드캐스트
        } else {
            sendMessage(Message.createErrorMessage(Message.ErrorCode.SERVER_FULL,
                    "방 생성 실패 (최대 " + serverCore.getRoomManager().getMaxRooms() + "개)"));
        }
    }

    // 방 입장 처리
    private void handleJoinRoom(Message msg) {
        int roomId = msg.getRoomId();
        GameRoom room = serverCore.getRoomManager().findRoom(roomId);

        if (room == null) {
            sendMessage(Message.createErrorMessage(Message.ErrorCode.ROOM_NOT_FOUND));
            return;
        }

        if (room.isGameRunning) {
            sendMessage(Message.createErrorMessage(Message.ErrorCode.ROOM_IN_GAME));
            return;
        }

        if (room.players.size() >= room.gameMode.getMaxPlayers()) {
            sendMessage(Message.createErrorMessage(Message.ErrorCode.ROOM_FULL));
            return;
        }


        currentRoom = room;
        room.addPlayer(this);
        userStatus = Message.UserStatus.IN_ROOM;

        Message response = room.createRoomUpdateMessage("방 입장 성공");
        response.setType(Message.MessageType.JOIN_ROOM_RESPONSE);
        response.setSuccess(true);
        sendMessage(response);

        Message updateMsg = room.createRoomUpdateMessage(null);
        sendMessage(updateMsg);

        serverCore.broadcastUserList();
    }

    // 방 정보 수정 처리
    private void handleEditRoom(Message msg) {
        if (currentRoom == null) {
            sendMessage(Message.createErrorMessage(Message.ErrorCode.ROOM_NOT_FOUND,
                    "방에 속해있지 않습니다."));
            return;
        }

        if (!userId.equals(currentRoom.roomMaster)) {
            sendMessage(Message.createErrorMessage(Message.ErrorCode.NOT_ROOM_MASTER,
                    "방장만 방 정보를 변경할 수 있습니다."));
            return;
        }

        if (currentRoom.isGameRunning) {
            sendMessage(Message.createErrorMessage(Message.ErrorCode.ROOM_IN_GAME,
                    "게임 중에는 방 정보를 변경할 수 없습니다."));
            return;
        }

        GameRoom oldRoom = currentRoom;
        GameRoom newRoom = serverCore.getRoomManager().editRoom(
                oldRoom,
                msg.getRoomName(),
                msg.getDifficulty(),
                msg.getTurnTimeLimit(),
                msg.getRoomPassword()
        );

        if (newRoom != null) {
            Message updateMsg = newRoom.createRoomUpdateMessage("방 정보가 변경되었습니다.");
            updateMsg.setType(Message.MessageType.EDIT_ROOM_RESPONSE);
            updateMsg.setSuccess(true);
            newRoom.broadcastToRoom(updateMsg);
            serverCore.printDisplay("방 정보 변경 완료: [" + newRoom.roomId + "]");
        } else {
            sendMessage(Message.createErrorMessage(Message.ErrorCode.UNKNOWN_ERROR,
                    "방 정보 변경 실패"));
        }
    }

    // 방 퇴장 처리
    private void handleLeaveRoom() {
        if (currentRoom != null) {
            currentRoom.removePlayer(this);
            currentRoom = null;
            userStatus = Message.UserStatus.ONLINE;
            sendMessage(new Message(Message.MessageType.LEAVE_ROOM, "SERVER",
                    "방에서 나갔습니다."));
            serverCore.broadcastUserList();
        }
    }

    // --- 게임 관련 ---
    // 준비/준비 취소 처리
    private void handleReady(boolean ready) {
        if (currentRoom != null && !currentRoom.isGameRunning) {
            currentRoom.setReady(userId, ready);
        }
    }

    // 게임 시작 요청 처리
    private void handleStartGameRequest() {
        if (currentRoom == null) return;

        if (!userId.equals(currentRoom.roomMaster)) {
            sendMessage(Message.createErrorMessage(Message.ErrorCode.NOT_ROOM_MASTER));
            return;
        }

        if (!currentRoom.canStartGame()) {
            sendMessage(Message.createErrorMessage(Message.ErrorCode.NOT_ENOUGH_PLAYERS,
                    "모든 플레이어가 준비되지 않았거나 인원(" +
                            currentRoom.gameMode.getMaxPlayers() + "명)이 부족합니다."));
            return;
        }

        currentRoom.startGame();
    }

    // 강제 퇴장 처리
    private void handleKickPlayer(Message msg) {
        if (currentRoom == null || !userId.equals(currentRoom.roomMaster)) {
            sendMessage(Message.createErrorMessage(Message.ErrorCode.NOT_ROOM_MASTER));
            return;
        }

        String targetUserId = msg.getTargetUserId();
        if (targetUserId == null || targetUserId.equals(userId)) {
            return;
        }

        ClientHandler targetPlayer = null;
        for (ClientHandler p : currentRoom.players) {
            if (p.userId.equals(targetUserId)) {
                targetPlayer = p;
                break;
            }
        }

        if (targetPlayer != null) {
            Message kickMsg = new Message(Message.MessageType.ROOM_INFO_UPDATE, "SERVER",
                    "방장에 의해 강제 퇴장되었습니다.");
            targetPlayer.sendMessage(kickMsg);

            currentRoom.removePlayer(targetPlayer);
            targetPlayer.currentRoom = null;
            serverCore.printDisplay(userId + "가 " + targetUserId + "를 강제 퇴장시킴");
        }
    }

    // 정답 처리
    private void handleGameStartAnswer(Message msg) {
        if (currentRoom == null) return;

        // GameRoom에 정답을 등록하고, 모든 플레이어의 정답이 등록되었는지 확인
        boolean allAnswered = currentRoom.setPlayerAnswer(userId, msg.getGuess());
        serverCore.printDisplay(userId + " 정답 설정 완료. [" + msg.getGuess() + "]");
        if (allAnswered) {
            currentRoom.startFirstTurn(); // 모든 정답이 등록되면 첫 턴을 시작
        }
    }

    // 추측 처리
    private void handleGuess(Message msg) {
        if (currentRoom != null && currentRoom.isGameRunning) {
            currentRoom.handleGuess(this, msg.getGuess());
        } else {
            sendMessage(Message.createErrorMessage(Message.ErrorCode.UNKNOWN_ERROR,
                    "현재 게임 중이 아니거나 방에 속해있지 않습니다."));
        }
    }

    // --- 채팅 관련 ---
    // 방 채팅
    private void handleRoomChat(Message msg) {
        if (currentRoom != null) {
            String content = msg.getContent();

            // /stats 명령어 처리
            if (content != null && content.startsWith("/stats")) {
                handleStatsChatCommand(content, Message.MessageType.CHAT_ROOM);
                return;
            }

            Message chatMsg = Message.createChatMessage(
                    Message.MessageType.CHAT_ROOM, userId, msg.getContent(), null);
            currentRoom.broadcastToRoomExcept(chatMsg, this);
        }
    }

    // 팀 채팅
    private void handleTeamChat(Message msg) {
        if (currentRoom != null && currentRoom.gameMode == Message.GameMode.TWO_VS_TWO) {
            int myTeam = currentRoom.playerTeams.getOrDefault(userId, 0);
            if (myTeam == 0) {
                // 팀 배정이 안 된 상태라면 에러 메시지 전송
                sendMessage(Message.createErrorMessage(Message.ErrorCode.UNKNOWN_ERROR,
                        "팀 채팅은 팀 배정 후 가능합니다."));
                return;
            }

            Message chatMsg = Message.createChatMessage(
                    Message.MessageType.CHAT_TEAM, userId, msg.getContent(), null);

            // 같은 팀원에게만 메시지 전송
            for (ClientHandler p : currentRoom.players) {
                if (currentRoom.playerTeams.getOrDefault(p.userId, 0) == myTeam) {
                    p.sendMessage(chatMsg);
                }
            }
        } else {
            // 1v1 모드에서 팀 채팅 시도 시 알림
            sendMessage(Message.createErrorMessage(Message.ErrorCode.UNKNOWN_ERROR,
                    "팀 채팅은 2v2 모드에서만 가능합니다."));
        }
    }

    // 전체 채팅
    private void handleAllChat(Message msg) {
        String content = msg.getContent();

        // /stats 명령어 처리
        if (content != null && content.startsWith("/stats")) {
            handleStatsChatCommand(content, Message.MessageType.CHAT_ALL);
            return;
        }

        Message chatMsg = Message.createChatMessage(
                Message.MessageType.CHAT_ALL, userId, msg.getContent(), null);
        serverCore.broadcastChatAllExcept(chatMsg, this);
    }

    // 귓속말
    private void handleWhisper(Message msg) {
        String targetUserId = msg.getTargetUserId();
        if (targetUserId == null || targetUserId.isEmpty()) {
            return;
        }

        ClientHandler targetClient = null;
        for (ClientHandler client : serverCore.getClientHandlers()) {
            if (client.userId != null && client.userId.equals(targetUserId)) {
                targetClient = client;
                break;
            }
        }

        if (targetClient != null) {
            Message whisperMsg = Message.createChatMessage(
                    Message.MessageType.CHAT_WHISPER, userId, msg.getContent(), targetUserId);

            targetClient.sendMessage(whisperMsg);

        } else {
            sendMessage(Message.createErrorMessage(Message.ErrorCode.UNKNOWN_ERROR,
                    "사용자 '" + targetUserId + "'를 찾을 수 없습니다."));
        }
    }

    // --- 전적/기록 관련 ---
    // 전적 조회 요청
    private void handleStatsRequest(Message msg) {
        String targetUserId = msg.getContent();
        if (targetUserId == null || targetUserId.isEmpty()) {
            targetUserId = userId;
        }

        Message response = serverCore.getAuthManager().getStats(targetUserId);
        if (response != null) {
            sendMessage(response);
        }
    }

    // 채팅 명령어로 전적 조회 (/stats [id])
    private void handleStatsChatCommand(String content, Message.MessageType responseType) {
        String[] parts = content.trim().split("\\s+");
        String targetUserId;

        // /stats 만 입력한 경우 자기 자신의 전적 조회
        if (parts.length == 1) {
            targetUserId = userId;
        } else {
            targetUserId = parts[1];
        }

        // AuthManager를 통해 전적 조회
        Message statsResponse = serverCore.getAuthManager().getStats(targetUserId);
        if (statsResponse != null && statsResponse.getData() != null) {
            @SuppressWarnings("unchecked")
            java.util.Hashtable<String, String> stats = (java.util.Hashtable<String, String>) statsResponse.getData();

            String wins = stats.getOrDefault("wins", "0");
            String losses = stats.getOrDefault("losses", "0");
            String draws = stats.getOrDefault("draws", "0");
            String winRate = stats.getOrDefault("winRate", "0.0");

            // 채팅 메시지로 전적 정보 전송
            String statsMessage = String.format(
                    "[%s님의 전적]\n승: %s | 패: %s | 무: %s | 승률: %s%%",
                    targetUserId, wins, losses, draws, winRate
            );

            Message chatResponse = new Message(responseType, "SERVER", statsMessage);
            sendMessage(chatResponse);
        } else {
            Message errorMsg = new Message(responseType, "SERVER",
                    "전적 정보를 조회할 수 없습니다.");
            sendMessage(errorMsg);
        }
    }

    // 게임 기록 조회 요청
    private void handleGameHistoryRequest(Message msg) {
        try {
            Vector<Hashtable<String, String>> historyList = new Vector<>();

            try (BufferedReader br = new BufferedReader(
                    new FileReader(ServerCore.getHistoryFile()))) {
                br.readLine(); // 헤더 스킵
                String line;
                int count = 0;
                int maxRecords = 20;

                while ((line = br.readLine()) != null && count < maxRecords) {
                    String[] parts = line.split(",");
                    if (parts.length >= 6) {
                        Hashtable<String, String> record = new Hashtable<>();
                        record.put("gameId", parts[0]);
                        record.put("timestamp", parts[1]);
                        record.put("participants", parts[2].replace("\"", ""));
                        record.put("gameMode", parts[3]);
                        record.put("difficulty", parts[4]);
                        record.put("winner", parts[5]);
                        historyList.add(record);
                        count++;
                    }
                }
            }

            Message response = new Message(Message.MessageType.GAME_HISTORY_RESPONSE, "SERVER");
            response.setData(historyList);
            sendMessage(response);

        } catch (IOException e) {
            serverCore.printDisplay("게임 기록 조회 오류: " + e.getMessage());
        }
    }

    // --- 유틸리티 ---
    // 메시지 전송
    public void sendMessage(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
            // 서버 -> 클라이언트 메시지 로깅
            logServerMessage(msg);
        } catch (IOException e) {
            serverCore.printDisplay("메시지 전송 오류 (" + userId + "): " + e.getMessage());
        }
    }

    // --- 로깅 관련 ---
    private void logClientMessage(Message msg) {
        try {
            String userIdStr = (userId != null) ? userId : "미인증";
            String logMessage = formatLogMessage(msg, userIdStr);
            serverCore.printDisplay(logMessage);
        } catch (Exception e) {
            // 로깅 오류가 메시지 처리를 방해하지 않도록 예외 처리
            serverCore.printDisplay("[로그오류] " + msg.getType());
        }
    }

    private void logServerMessage(Message msg) {
        if (msg.getType() == Message.MessageType.USER_LIST_RESPONSE ||
                msg.getType() == Message.MessageType.ROOM_LIST_RESPONSE) {
            return;
        }

        try {
            String userIdStr = (userId != null) ? userId : "미인증";
            String logMessage = formatServerLogMessage(msg, userIdStr);
            if (logMessage != null && !logMessage.isEmpty()) {
                serverCore.printDisplay(logMessage);
            }
        } catch (Exception e) {
            // 로깅 오류가 메시지 처리를 방해하지 않도록 예외 처리
        }
    }

    private String formatLogMessage(Message msg, String userIdStr) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(msg.getType()).append("] ").append(userIdStr);

        switch (msg.getType()) {
            case LOGIN_REQUEST:
                sb.append(" | 로그인 시도");
                break;

            case REGISTER_REQUEST:
                sb.append(" | 캐릭터: ").append(msg.getCharacter()).append(" | 회원가입 시도");
                break;

            case LOGOUT:
                sb.append(" | 로그아웃");
                break;

            case CREATE_ROOM_REQUEST:
                sb.append(" | 방: \"").append(msg.getRoomName()).append("\"");
                sb.append(" | ").append(msg.getGameMode().getDisplayName());
                sb.append(" | 난이도: ").append(msg.getDifficulty().getDisplayName());
                sb.append(" | 시간: ").append(msg.getTurnTimeLimit().getDisplayName());
                break;

            case JOIN_ROOM_REQUEST:
                sb.append(" | 방ID: ").append(msg.getRoomId());
                break;

            case EDIT_ROOM_REQUEST:
                sb.append(" | 방ID: ").append(msg.getRoomId());
                sb.append(" | 새이름: \"").append(msg.getRoomName()).append("\"");
                break;

            case LEAVE_ROOM:
                if (currentRoom != null) {
                    sb.append(" | 방ID: ").append(currentRoom.roomId);
                }
                break;

            case KICK_PLAYER:
                sb.append(" | 대상: ").append(msg.getTargetUserId());
                if (currentRoom != null) {
                    sb.append(" | 방ID: ").append(currentRoom.roomId);
                }
                break;

            case READY:
                sb.append(" | 준비 완료");
                if (currentRoom != null) {
                    sb.append(" | 방ID: ").append(currentRoom.roomId);
                }
                break;

            case READY_CANCEL:
                sb.append(" | 준비 취소");
                if (currentRoom != null) {
                    sb.append(" | 방ID: ").append(currentRoom.roomId);
                }
                break;

            case START_GAME_REQUEST:
                sb.append(" | 게임 시작 요청");
                if (currentRoom != null) {
                    sb.append(" | 방ID: ").append(currentRoom.roomId);
                }
                break;

            case GUESS:
                if (currentRoom != null) {

                    boolean isAnswerSetupPhase = false;

                    if (currentRoom.isGameRunning) {
                        if (currentRoom.gameMode == Message.GameMode.ONE_VS_ONE) {
                            // 1v1: 아직 내 정답을 설정하지 않았을 때
                            isAnswerSetupPhase = !currentRoom.playerAnswers.containsKey(userId);
                        } else if (currentRoom.gameMode == Message.GameMode.TWO_VS_TWO) {
                            int playerTeam = currentRoom.playerTeams.getOrDefault(userId, 0);
                            String leaderId = currentRoom.teamLeaders.get(playerTeam);

                            // 2v2: 아직 우리 팀 정답을 설정하지 않았고, 내가 팀 대표일 때
                            isAnswerSetupPhase = playerTeam != 0 &&
                                    !currentRoom.isTeamAnswerSet(playerTeam) &&
                                    userId.equals(leaderId);
                        }
                    }

                    if (isAnswerSetupPhase) {
                        sb.append(" | 정답 설정: ").append(msg.getGuess()); // 정답 설정 로깅
                    } else {
                        sb.append(" | 추측: ").append(msg.getGuess()); // 일반 추측 로깅
                    }

                    if (currentRoom != null) {
                        sb.append(" | 방ID: ").append(currentRoom.roomId);
                    }
                }
                break;

            case CHAT_ROOM:
                sb.append(" | 방채팅");
                if (currentRoom != null) {
                    sb.append(" | 방ID: ").append(currentRoom.roomId);
                }
                appendChatPreview(sb, msg.getContent());
                break;

            case CHAT_TEAM:
                sb.append(" | 팀채팅");
                if (currentRoom != null) {
                    sb.append(" | 방ID: ").append(currentRoom.roomId);
                }
                appendChatPreview(sb, msg.getContent());
                break;

            case CHAT_ALL:
                sb.append(" | 전체채팅");
                appendChatPreview(sb, msg.getContent());
                break;

            case CHAT_WHISPER:
                sb.append(" → ").append(msg.getTargetUserId());
                sb.append(" | 귓속말");
                appendChatPreview(sb, msg.getContent());
                break;

            case STATS_REQUEST:
                String target = msg.getContent();
                if (target != null && !target.isEmpty()) {
                    sb.append(" | 조회대상: ").append(target);
                } else {
                    sb.append(" | 본인 전적 조회");
                }
                break;

            case GAME_HISTORY_REQUEST:
                sb.append(" | 게임기록 조회");
                break;

            case USER_LIST_REQUEST:
                sb.append(" | 접속자 목록 요청");
                break;

            case ROOM_LIST_REQUEST:
                sb.append(" | 방 목록 요청");
                break;

            default:
                // 기본 메시지는 타입만 로깅
                break;
        }

        return sb.toString();
    }

    private void appendChatPreview(StringBuilder sb, String content) {
        if (content != null && !content.isEmpty()) {
            String preview = content.length() > 50
                    ? content.substring(0, 47) + "..."
                    : content;
            sb.append(" | \"").append(preview).append("\"");
        }
    }

    private String formatServerLogMessage(Message msg, String userIdStr) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(msg.getType()).append("] SERVER → ").append(userIdStr);

        switch (msg.getType()) {
            case LOGIN_RESPONSE:
                if (msg.isSuccess()) {
                    sb.append(" | 로그인 성공 응답");
                } else {
                    sb.append(" | 로그인 실패 응답");
                }
                break;

            case REGISTER_RESPONSE:
                if (msg.isSuccess()) {
                    sb.append(" | 회원가입 성공 응답");
                } else {
                    sb.append(" | 회원가입 실패 응답");
                }
                break;

            case ROOM_LIST_RESPONSE:
                sb.append(" | 방 목록 전송");
                if (msg.getData() != null) {
                    @SuppressWarnings("unchecked")
                    Vector<Message> roomList = (Vector<Message>) msg.getData();
                    sb.append(" (").append(roomList.size()).append("개 방)");
                }
                break;

            case CREATE_ROOM_RESPONSE:
                if (msg.isSuccess()) {
                    sb.append(" | 방 생성 성공 응답");
                    if (msg.getRoomId() != 0) {
                        sb.append(" | 방ID: ").append(msg.getRoomId());
                    }
                } else {
                    sb.append(" | 방 생성 실패 응답");
                }
                break;

            case JOIN_ROOM_RESPONSE:
                if (msg.isSuccess()) {
                    sb.append(" | 방 입장 성공 응답");
                    if (msg.getRoomId() != 0) {
                        sb.append(" | 방ID: ").append(msg.getRoomId());
                    }
                } else {
                    sb.append(" | 방 입장 실패 응답");
                }
                break;

            case EDIT_ROOM_RESPONSE:
                if (msg.isSuccess()) {
                    sb.append(" | 방 수정 성공 응답");
                } else {
                    sb.append(" | 방 수정 실패 응답");
                }
                break;

            case LEAVE_ROOM:
                sb.append(" | 방 퇴장 통지");
                break;

            case ERROR:
                sb.append(" | 에러: ");
                if (msg.getErrorCode() != null) {
                    sb.append(msg.getErrorCode());
                }
                if (msg.getContent() != null) {
                    sb.append(" - ").append(msg.getContent());
                }
                break;

            case CHAT_ROOM:
                sb.append(" | 방채팅 전달");
                if (currentRoom != null) {
                    sb.append(" | 방ID: ").append(currentRoom.roomId);
                }
                appendChatPreview(sb, msg.getContent());
                break;

            case CHAT_TEAM:
                sb.append(" | 팀채팅 전달");
                if (currentRoom != null) {
                    sb.append(" | 방ID: ").append(currentRoom.roomId);
                }
                appendChatPreview(sb, msg.getContent());
                break;

            case CHAT_ALL:
                sb.append(" | 전체채팅 전달");
                appendChatPreview(sb, msg.getContent());
                break;

            case CHAT_WHISPER:
                sb.append(" | 귓속말 전달");
                if (msg.getTargetUserId() != null) {
                    sb.append(" | 발신: ").append(msg.getUserId());
                }
                appendChatPreview(sb, msg.getContent());
                break;

            case ROOM_INFO_UPDATE:
                sb.append(" | 방 정보 업데이트");
                if (msg.getRoomId() != 0) {
                    sb.append(" | 방ID: ").append(msg.getRoomId());
                }
                break;

            case START_GAME:
                sb.append(" | 게임 시작 통지");
                if (currentRoom != null) {
                    sb.append(" | 방ID: ").append(currentRoom.roomId);
                }
                break;

            case TURN_INFO:
                sb.append(" | 턴 시작 통지");
                if (msg.getCurrentTurnPlayer() != null) {
                    sb.append(" | 현재 플레이어: ").append(msg.getCurrentTurnPlayer());
                }
                break;

            case GUESS_RESULT:
                sb.append(" | 추측 결과");
                if (msg.getGuess() != null) {
                    sb.append(" | 숫자: ").append(msg.getGuess());
                    sb.append(" | ").append(msg.getStrike()).append("S ").append(msg.getBall()).append("B");
                }
                break;

            case END_GAME:
            case GAME_RESULT:
                sb.append(" | 게임 종료 통지");
                if (msg.getWinnerId() != null) {
                    sb.append(" | 승자: ").append(msg.getWinnerId());
                } else if (msg.getWinnerTeam() != 0) {
                    sb.append(" | 승리 팀: ").append(msg.getWinnerTeam());
                } else if (msg.isDraw()) {
                    sb.append(" | 무승부");
                }
                break;

            case STATS_RESPONSE:
                sb.append(" | 전적 정보 응답");
                break;

            case GAME_HISTORY_RESPONSE:
                sb.append(" | 게임 기록 응답");
                if (msg.getData() != null) {
                    @SuppressWarnings("unchecked")
                    Vector<Hashtable<String, String>> history = (Vector<Hashtable<String, String>>) msg.getData();
                    sb.append(" (").append(history.size()).append("개 기록)");
                }
                break;

            default:
                // 기타 메시지는 타입만 로깅
                break;
        }

        return sb.toString();
    }

    // 연결 종료
    public void close() {
        try {
            // 게임 중 접속 끊김 처리
            if (currentRoom != null && currentRoom.isGameRunning) {

                // 1v1 모드에서만 명시적으로 상대방 승리 처리
                // 2v2 모드에서는 removePlayer() 호출 시 GameRoom 내부에서 팀 전멸을 체크하고 처리함.
                if (currentRoom.gameMode == Message.GameMode.ONE_VS_ONE) {
                    for (ClientHandler p : currentRoom.players) {
                        if (!p.userId.equals(userId)) {
                            // 나가지 않은 상대방이 승리
                            currentRoom.endGame(p.userId, false, 0);
                            break;
                        }
                    }
                }
            }

            // 방에서 제거
            // 2v2 모드에서 이 호출이 GameRoom의 removePlayer()를 실행하고,
            // 그 안에서 팀 전멸 로직(endGame)이 실행됩니다.
            if (currentRoom != null) {
                currentRoom.removePlayer(this);
                currentRoom = null;
            }

            // 서버 리스트에서 제거 및 로그 출력
            if (userId != null) {
                serverCore.removeClient(this);
                serverCore.printDisplay(userId + " 연결 종료");
                serverCore.broadcastUserList();
            }

            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            serverCore.printDisplay("소켓 종료 오류: " + e.getMessage());
        }
    }
}