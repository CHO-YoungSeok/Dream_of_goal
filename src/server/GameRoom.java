package server;

import common.Message;
import java.io.*;
import java.util.Hashtable;
import java.util.Vector;
import java.util.stream.Collectors;

// 게임 방 클래스

public class GameRoom {
    // 방 정보
    int roomId;
    String roomName;
    String roomMaster;
    Message.GameMode gameMode;
    Message.Difficulty difficulty;
    Message.TurnTimeLimit turnTimeLimit;
    boolean isPrivate;
    String roomPassword;

    // 플레이어 관리
    Vector<ClientHandler> players = new Vector<>();
    Hashtable<String, Boolean> readyStatus = new Hashtable<>();

    // 게임 진행 상태
    boolean isGameRunning = false;
    Hashtable<String, String> playerAnswers = new Hashtable<>();
    Hashtable<String, Integer> playerTeams = new Hashtable<>();
    int currentRound = 1;
    boolean isTopHalf = true;
    String gameId;

    // 정답이 설정된 플레이어 수 확인
    private final java.util.Set<String> answeredPlayers = new java.util.HashSet<>();

    private ServerCore serverCore;

    public GameRoom(int roomId, String roomName, String roomMaster,
                    Message.GameMode gameMode, Message.Difficulty difficulty,
                    Message.TurnTimeLimit turnTimeLimit, boolean isPrivate,
                    String roomPassword, ServerCore serverCore) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.roomMaster = roomMaster;
        this.gameMode = gameMode;
        this.difficulty = difficulty;
        this.turnTimeLimit = turnTimeLimit;
        this.isPrivate = isPrivate;
        this.roomPassword = roomPassword;
        this.serverCore = serverCore;
    }

    // 방 업데이트 메시지 생성
    public Message createRoomUpdateMessage(String content) {
        Message msg = new Message(Message.MessageType.ROOM_INFO_UPDATE, "SERVER", content);
        msg.setRoomId(roomId);
        msg.setRoomName(roomName);
        msg.setRoomMaster(roomMaster);
        msg.setGameMode(gameMode);
        msg.setDifficulty(difficulty);
        msg.setTurnTimeLimit(turnTimeLimit);
        msg.setPrivate(isPrivate);
        msg.setRoomStatus(isGameRunning ? Message.RoomStatus.IN_GAME : Message.RoomStatus.WAITING);
        msg.setCurrentPlayers(players.size());
        msg.setMaxPlayers(gameMode.getMaxPlayers());

        Hashtable<String, Serializable> roomData = new Hashtable<>();
        Vector<String> playerIds = players.stream()
                .map(p -> p.userId)
                .collect(Collectors.toCollection(Vector::new));
        roomData.put("players", playerIds);
        roomData.put("readyStatus", new Hashtable<>(readyStatus));
        msg.setData(roomData);

        return msg;
    }

    // 플레이어 추가
    public boolean addPlayer(ClientHandler player) {
        if (players.size() >= gameMode.getMaxPlayers()) {
            return false;
        }

        players.add(player);

        boolean isMaster = player.userId.equals(roomMaster);
        readyStatus.put(player.userId, isMaster);

        // 2v2 팀 배정
        if (gameMode == Message.GameMode.TWO_VS_TWO) {
            int teamNum = (players.size() <= 2) ? 1 : 2;
            playerTeams.put(player.userId, teamNum);
            serverCore.printDisplay(player.userId + " -> Team " + teamNum);
        }

        Message updateMsg = createRoomUpdateMessage(player.userId + "님이 입장하셨습니다.");
        broadcastToRoom(updateMsg);
        return true;
    }

    // 플레이어 제거
    public void removePlayer(ClientHandler player) {
        players.remove(player);
        readyStatus.remove(player.userId);
        playerTeams.remove(player.userId);
        answeredPlayers.remove(player.userId);

        if (players.isEmpty()) {
            serverCore.getRoomManager().removeRoom(this);
            return;
        }

        if (player.userId.equals(roomMaster)) {
            roomMaster = players.get(0).userId;
            readyStatus.put(roomMaster, true);
            Message msg = createRoomUpdateMessage(roomMaster + "님이 새로운 방장이 되었습니다.");
            broadcastToRoom(msg);
        } else {
            Message msg = createRoomUpdateMessage(player.userId + "님이 퇴장했습니다.");
            broadcastToRoom(msg);
        }
    }

    // 준비 상태 변경
    public void setReady(String userId, boolean ready) {

        readyStatus.put(userId, ready);
        String msg = userId + "님이 " + (ready ? "준비완료" : "준비취소") + " 했습니다.";

        Message statusUpdate = new Message(Message.MessageType.READY_STATUS_UPDATE, "SERVER", msg);
        statusUpdate.setData(new Hashtable<>(readyStatus));
        broadcastToRoom(statusUpdate);
    }

    // 게임 시작 가능 체크
    public boolean canStartGame() {
        if (players.size() != gameMode.getMaxPlayers()) {
            return false;
        }

        for (ClientHandler player : players) {
            Boolean ready = readyStatus.get(player.userId);
            if (ready == null || !ready) {
                return false;
            }
        }
        return true;
    }

    // 플레이어 정답 설정 및 완료 확인
    public boolean setPlayerAnswer(String userId, String answer) {
        playerAnswers.put(userId, answer);
        answeredPlayers.add(userId);
        serverCore.printDisplay("정답 등록: " + userId + " - " + answer);

        return answeredPlayers.size() == gameMode.getMaxPlayers();
    }

    // 모든 플레이어 정답 설정 후, 첫 턴을 시작
    public void startFirstTurn() {
        if (!isGameRunning) return;

        currentRound = 1;
        isTopHalf = true;
        sendTurnInfo();
        serverCore.printDisplay("게임 턴 시작: " + gameId);
    }

    // 게임 시작
    public void startGame() {
        isGameRunning = true;
        gameId = "G" + System.currentTimeMillis();

        answeredPlayers.clear();

        // 플레이어 상태 변경
        for (ClientHandler player : players) {
            player.userStatus = Message.UserStatus.IN_GAME;
        }

        // 정답 생성
        if (gameMode == Message.GameMode.ONE_VS_ONE) {
            for (ClientHandler player : players) {
                String answer = serverCore.generateAnswer(difficulty.getDigitCount());
                //playerAnswers.put(player.userId, answer);
                //serverCore.printDisplay("게임 시작 - " + player.userId + "의 정답: " + answer);
            }
        } else {
            String team1Answer = serverCore.generateAnswer(difficulty.getDigitCount());
            String team2Answer = serverCore.generateAnswer(difficulty.getDigitCount());

            /*
            for (ClientHandler player : players) {
                int teamNum = playerTeams.get(player.userId);
                String answer = (teamNum == 1) ? team1Answer : team2Answer;
                playerAnswers.put(player.userId, answer);
                serverCore.printDisplay("게임 시작 - " + player.userId + " (Team " + teamNum + ")의 정답: " + answer);
            } */
        }

        //currentRound = 1;
        //isTopHalf = true;

        Message startMsg = new Message(Message.MessageType.START_GAME, "SERVER");
        startMsg.setGameMode(gameMode);
        startMsg.setDifficulty(difficulty);
        startMsg.setTurnTimeLimit(turnTimeLimit);
        startMsg.setContent("게임이 시작되었습니다!");
        broadcastToRoom(startMsg);

        serverCore.broadcastUserList();
    }

    // 턴 정보 생성
    public void sendTurnInfo() {
        ClientHandler turnPlayer = null;
        if (!players.isEmpty()) {
            int playerIndex = ((currentRound - 1) * 2 + (isTopHalf ? 0 : 1)) % players.size();
            turnPlayer = players.get(playerIndex);
        }

        Message turnMsg = new Message(Message.MessageType.TURN_INFO, "SERVER");
        turnMsg.setRound(currentRound);
        turnMsg.setTop(isTopHalf);
        turnMsg.setCurrentTurnPlayer(turnPlayer != null ? turnPlayer.userId : null);
        turnMsg.setContent(currentRound + "회 " + (isTopHalf ? "초" : "말"));
        broadcastToRoom(turnMsg);
    }

    // 추측 처리
    public void handleGuess(ClientHandler player, String guess) {
        if (!isGameRunning) {
            return;
        }

        // 턴 제약 (1v1만)
        if (gameMode == Message.GameMode.ONE_VS_ONE &&
                !player.userId.equals(getCurrentTurnPlayerId())) {
            player.sendMessage(Message.createErrorMessage(
                    Message.ErrorCode.TURN_TIMEOUT, "당신의 턴이 아닙니다."));
            return;
        }

        // 입력 검증
        if (!serverCore.isValidGuess(guess, difficulty.getDigitCount())) {
            player.sendMessage(Message.createErrorMessage(
                    Message.ErrorCode.INVALID_INPUT_FORMAT));
            return;
        }

        // 상대방 정답 찾기
        String targetAnswer = getTargetAnswer(player);
        if (targetAnswer == null) return;

        // 결과 계산
        int[] result = serverCore.calculateResult(targetAnswer, guess);
        int strike = result[0];
        int ball = result[1];

        // 결과 전송
        Message resultMsg = Message.createGuessResult(player.userId, guess, strike, ball);

        if (gameMode == Message.GameMode.TWO_VS_TWO) {
            int myTeam = playerTeams.get(player.userId);
            for (ClientHandler p : players) {
                if (playerTeams.get(p.userId) == myTeam) {
                    p.sendMessage(resultMsg);
                }
            }
        } else {
            broadcastToRoom(resultMsg);
        }

        // 기록 저장
        saveGameDetail(gameId, currentRound, player.userId, guess, strike + "S " + ball + "B");

        // 승리 체크
        if (strike == difficulty.getDigitCount()) {
            if (gameMode == Message.GameMode.TWO_VS_TWO) {
                endGame(player.userId, false, playerTeams.get(player.userId));
            } else {
                endGame(player.userId, false, 0);
            }
            return;
        }

        nextTurn();
    }

    // 현재 턴 플레이어 ID
    public String getCurrentTurnPlayerId() {
        if (players.isEmpty()) return null;
        int playerIndex = ((currentRound - 1) * 2 + (isTopHalf ? 0 : 1)) % players.size();
        return players.get(playerIndex).userId;
    }

    // 상대방 정답 찾기
    private String getTargetAnswer(ClientHandler currentPlayer) {
        if (gameMode == Message.GameMode.ONE_VS_ONE) {
            for (ClientHandler p : players) {
                if (!p.userId.equals(currentPlayer.userId)) {
                    return playerAnswers.get(p.userId);
                }
            }
        } else {
            int myTeam = playerTeams.get(currentPlayer.userId);
            int targetTeam = (myTeam == 1) ? 2 : 1;
            for (ClientHandler p : players) {
                if (playerTeams.get(p.userId) == targetTeam) {
                    return playerAnswers.get(p.userId);
                }
            }
        }
        return null;
    }

    // 다음 턴
    private void nextTurn() {
        if (isTopHalf) {
            isTopHalf = false;
        } else {
            isTopHalf = true;
            currentRound++;
        }

        if (currentRound > 9) {
            endGame(null, true, 0);
        } else {
            sendTurnInfo();
        }
    }

    // 게임 종료
    public void endGame(String winnerId, boolean isDraw, int winnerTeam) {
        isGameRunning = false;

        Message endMsg = new Message(Message.MessageType.END_GAME, "SERVER");
        if (isDraw) {
            endMsg.setDraw(true);
            endMsg.setContent("9회말 종료! 무승부입니다.");
        } else {
            endMsg.setWinnerId(winnerId);
            if (gameMode == Message.GameMode.TWO_VS_TWO) {
                endMsg.setWinnerTeam(winnerTeam);
                endMsg.setContent("Team " + winnerTeam + " 승리! (최초 정답자: " + winnerId + ")");
            } else {
                endMsg.setContent(winnerId + "님이 승리했습니다!");
            }
        }
        broadcastToRoom(endMsg);

        // 상태 변경
        for (ClientHandler player : players) {
            player.userStatus = Message.UserStatus.IN_ROOM;
        }

        serverCore.broadcastUserList();
        saveGameHistory(winnerId, isDraw, winnerTeam);

        // 준비 초기화
        for (ClientHandler player : players) {
            readyStatus.put(player.userId, false);
        }
    }

    // 게임 기록 저장
    private void saveGameHistory(String winnerId, boolean isDraw, int winnerTeam) {
        try {
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date());

            String participants = players.stream()
                    .map(p -> p.userId)
                    .collect(Collectors.joining(","));

            String winner;
            if (isDraw) {
                winner = "Draw";
            } else if (gameMode == Message.GameMode.TWO_VS_TWO) {
                winner = "Team" + winnerTeam;
            } else {
                winner = winnerId;
            }

            try (FileWriter fw = new FileWriter(ServerCore.getHistoryFile(), true)) {
                fw.write(gameId + "," + timestamp + ",\"" + participants + "\"," +
                        gameMode.getDisplayName() + "," + difficulty.getDisplayName() +
                        "," + winner + "\n");
            }

            serverCore.printDisplay("게임 기록 저장: " + gameId);

            // 전적 업데이트
            for (ClientHandler player : players) {
                boolean isWin = false;
                boolean isDrawResult = isDraw;

                if (!isDraw) {
                    if (gameMode == Message.GameMode.TWO_VS_TWO) {
                        int playerTeam = playerTeams.get(player.userId);
                        isWin = (playerTeam == winnerTeam);
                    } else {
                        isWin = player.userId.equals(winnerId);
                    }
                }

                serverCore.getAuthManager().updateUserStats(player.userId, isWin, isDrawResult);
            }

            serverCore.printDisplay("전적 업데이트 완료");

        } catch (IOException e) {
            serverCore.printDisplay("게임 기록 저장 실패: " + e.getMessage());
        }
    }

    // 게임 상세 기록 저장
    private void saveGameDetail(String gameId, int round, String playerId,
                                String guess, String result) {
        try (FileWriter fw = new FileWriter(ServerCore.getDetailsFile(), true)) {
            fw.write(gameId + "," + round + "," + playerId + "," + guess + "," + result + "\n");
        } catch (IOException e) {
            serverCore.printDisplay("게임 상세 기록 저장 실패: " + e.getMessage());
        }
    }

    // 방 전체 브로드 캐스트
    public void broadcastToRoom(Message msg) {
        for (ClientHandler player : players) {
            player.sendMessage(msg);
        }
    }

    // 특정 플레이어 제외하고 방 전체 브로드캐스트
    public void broadcastToRoomExcept(Message msg, ClientHandler except) {
        for (ClientHandler player : players) {
            if (player != except) {
                player.sendMessage(msg);
            }
        }
    }
}
