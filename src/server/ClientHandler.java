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
                serverCore.broadcastUserList(); // 접속자 목록 요청 시 전체 브로드캐스트
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
                if (currentRoom != null && !currentRoom.isGameRunning && currentRoom.players.size() == currentRoom.gameMode.getMaxPlayers()) {
                    handleGameStartAnswer(msg);
                } else {
                    handleGuess(msg);
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
                msg.isPrivate(),
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

            // 갱신된 방 정보를 클라이언트에게 다시 전송하여 목록 업데이트 강제
            Message updateMsg = room.createRoomUpdateMessage(null);
            sendMessage(updateMsg);

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

        // 비공개 방 비밀번호 확인
        if (room.isPrivate) {
            String inputPassword = msg.getRoomPassword();
            if (inputPassword == null || !inputPassword.equals(room.roomPassword)) {
                sendMessage(Message.createErrorMessage(Message.ErrorCode.WRONG_PASSWORD));
                return;
            }
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
                msg.isPrivate(),
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
            Message chatMsg = Message.createChatMessage(
                    Message.MessageType.CHAT_ROOM, userId, msg.getContent(), null);
            currentRoom.broadcastToRoom(chatMsg);
        }
    }

    // 팀 채팅
    private void handleTeamChat(Message msg) {
        if (currentRoom != null && currentRoom.gameMode == Message.GameMode.TWO_VS_TWO) {
            int myTeam = currentRoom.playerTeams.getOrDefault(userId, 0);
            if (myTeam == 0) return;

            Message chatMsg = Message.createChatMessage(
                    Message.MessageType.CHAT_TEAM, userId, msg.getContent(), null);

            for (ClientHandler p : currentRoom.players) {
                if (currentRoom.playerTeams.getOrDefault(p.userId, 0) == myTeam) {
                    p.sendMessage(chatMsg);
                }
            }
        }
    }

    // 전체 채팅
    private void handleAllChat(Message msg) {
        Message chatMsg = Message.createChatMessage(
                Message.MessageType.CHAT_ALL, userId, msg.getContent(), null);
        serverCore.broadcastChatAll(chatMsg);
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
            sendMessage(whisperMsg);
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
        } catch (IOException e) {
            serverCore.printDisplay("메시지 전송 오류 (" + userId + "): " + e.getMessage());
        }
    }

    // 연결 종료
    public void close() {
        try {
            // 게임 중 접속 끊김 처리
            if (currentRoom != null && currentRoom.isGameRunning) {
                if (currentRoom.gameMode == Message.GameMode.ONE_VS_ONE) {
                    for (ClientHandler p : currentRoom.players) {
                        if (!p.userId.equals(userId)) {
                            currentRoom.endGame(p.userId, false, 0);
                            break;
                        }
                    }
                } else {
                    int disconnectedTeam = currentRoom.playerTeams.getOrDefault(userId, 0);
                    if (disconnectedTeam != 0) {
                        int winnerTeam = (disconnectedTeam == 1) ? 2 : 1;
                        currentRoom.endGame(null, false, winnerTeam);
                    }
                }
            }

            // 방에서 제거
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