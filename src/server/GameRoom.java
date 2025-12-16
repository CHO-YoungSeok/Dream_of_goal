package server;

import common.Message;
import java.io.*;
import java.util.Hashtable;
import java.util.Optional;
import java.util.Vector;
import java.util.stream.Collectors;
import java.io.Serializable;

// 게임 방 클래스

public class GameRoom {
    // 방 정보
    int roomId;
    String roomName;
    String roomMaster;
    Message.GameMode gameMode;
    Message.Difficulty difficulty;
    Message.TurnTimeLimit turnTimeLimit;
    String roomPassword;

    // 플레이어 관리
    Vector<ClientHandler> players = new Vector<>();
    Hashtable<String, Boolean> readyStatus = new Hashtable<>();

    // 게임 진행 상태
    boolean isGameRunning = false;
    public Hashtable<String, String> playerAnswers = new Hashtable<>();

    // 2v2 팀 점수 추적
    public int team1Score = 0;
    public int team2Score = 0;

    Hashtable<String, String> teamAnswers = new Hashtable<>();
    Hashtable<Integer, String> teamLeaders = new Hashtable<>();
    private final java.util.Set<Integer> answeredTeams = new java.util.HashSet<>();

    Hashtable<String, Integer> playerTeams = new Hashtable<>();
    int currentRound = 1;
    boolean isTopHalf = true;
    String gameId;

    // 정답이 설정된 플레이어 수 확인
    private final java.util.Set<String> answeredPlayers = new java.util.HashSet<>();
    private java.util.Timer turnTimer;
    private ServerCore serverCore;

    // 턴 진행 관리
    private String currentTurnPlayerId = null;

    public GameRoom(int roomId, String roomName, String roomMaster,
                    Message.GameMode gameMode, Message.Difficulty difficulty,
                    Message.TurnTimeLimit turnTimeLimit,
                    String roomPassword, ServerCore serverCore) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.roomMaster = roomMaster;
        this.gameMode = gameMode;
        this.difficulty = difficulty;
        this.turnTimeLimit = turnTimeLimit;
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

        // 2v2 팀 배정 및 팀 대표 지정
        if (gameMode == Message.GameMode.TWO_VS_TWO) {
            int teamNum = (players.size() <= 2) ? 1 : 2;
            playerTeams.put(player.userId, teamNum);

            // 팀 대표 지정 (해당 팀에 가장 먼저 입장한 플레이어)
            if (!teamLeaders.containsKey(teamNum)) {
                teamLeaders.put(teamNum, player.userId);
                serverCore.printDisplay(player.userId + " -> Team " + teamNum + " (Leader)");
            } else {
                serverCore.printDisplay(player.userId + " -> Team " + teamNum);
            }
        }

        // 1v1 모드도 팀 1로 간주하고 대표 지정
        if (gameMode == Message.GameMode.ONE_VS_ONE && players.size() == 1) {
            playerTeams.put(player.userId, 1);
            teamLeaders.put(1, player.userId);
        }

        Message updateMsg = createRoomUpdateMessage(player.userId + "님이 입장하셨습니다.");
        broadcastToRoom(updateMsg);
        return true;
    }

    // 플레이어 제거
    public void removePlayer(ClientHandler player) {
        String disconnectedUserId = player.userId;
        int disconnectedTeam = playerTeams.getOrDefault(disconnectedUserId, 0);

        // 데이터 구조에서 제거
        players.remove(player);
        readyStatus.remove(disconnectedUserId);
        playerTeams.remove(disconnectedUserId);
        answeredPlayers.remove(disconnectedUserId);

        // 2v2 팀전 관련 초기화
        if (gameMode == Message.GameMode.TWO_VS_TWO) {
            answeredTeams.remove(disconnectedTeam);
            teamAnswers.remove(String.valueOf(disconnectedTeam)); // 해당 팀 정답 초기화
        }

        if (players.isEmpty()) {
            serverCore.getRoomManager().removeRoom(this);
            return;
        }

        // 방장 위임
        if (disconnectedUserId.equals(roomMaster)) {
            roomMaster = players.get(0).userId;
            readyStatus.put(roomMaster, true);
            Message msg = createRoomUpdateMessage(roomMaster + "님이 새로운 방장이 되었습니다.");
            broadcastToRoom(msg);
        } else {
            Message msg = createRoomUpdateMessage(disconnectedUserId + "님이 퇴장했습니다.");
            broadcastToRoom(msg);
        }

        // 2v2 팀 전멸 및 팀 대표 위임 (게임 중일 때만)
        if (gameMode == Message.GameMode.TWO_VS_TWO && disconnectedTeam != 0) {

            // 팀 대표 위임
            if (disconnectedUserId.equals(teamLeaders.get(disconnectedTeam))) {
                // 남아있는 팀원 중 첫 번째 플레이어에게 대표 위임
                players.stream()
                        .filter(p -> playerTeams.getOrDefault(p.userId, 0) == disconnectedTeam)
                        .findFirst()
                        .ifPresent(newLeader -> {
                            teamLeaders.put(disconnectedTeam, newLeader.userId);
                            broadcastToRoom(createRoomUpdateMessage(newLeader.userId + "님이 새로운 팀 대표가 되었습니다."));
                        });
            }

            // 팀 전멸 체크
            if (isGameRunning) {
                long remainingInTeam = players.stream()
                        .filter(p -> playerTeams.getOrDefault(p.userId, 0) == disconnectedTeam)
                        .count();

                if (remainingInTeam == 0) {
                    // 팀 전멸 -> 상대 팀 승리
                    int winnerTeam = (disconnectedTeam == 1) ? 2 : 1;
                    endGame(null, false, winnerTeam);
                    return;
                }
            }
        }
    }

    // 팀 리더 정보 재설정
    public void updateTeamLeadersFromPlayerTeams() {
        if (gameMode != Message.GameMode.TWO_VS_TWO) return;

        teamLeaders.clear();

        // 플레이어 목록을 순회하며 팀별로 첫 번째 플레이어를 팀 리더로 지정
        for (ClientHandler p : players) {
            Integer teamNum = playerTeams.get(p.userId);
            if (teamNum != null && !teamLeaders.containsKey(teamNum)) {
                teamLeaders.put(teamNum, p.userId);
            }
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
        if (gameMode == Message.GameMode.TWO_VS_TWO) {
            int playerTeam = playerTeams.getOrDefault(userId, 0);
            if (playerTeam == 0) return false;

            String leaderId = teamLeaders.get(playerTeam);

            // 2v2: 팀 대표만 정답 설정 가능, 이미 해당 팀 정답이 설정되었다면 무시
            if (!userId.equals(leaderId) || answeredTeams.contains(playerTeam)) return false;

            // 정답 설정
            teamAnswers.put(String.valueOf(playerTeam), answer); // 팀 정답 저장
            answeredTeams.add(playerTeam); // 팀 정답 설정 완료

            serverCore.printDisplay("팀 정답 등록: Team " + playerTeam + " - " + answer);

            // 팀 전체에 정답 설정 완료 알림
            Message completeMsg = new Message(Message.MessageType.ROOM_INFO_UPDATE, "SERVER",
                    "Team " + playerTeam + " 정답 설정 완료.");
            broadcastToRoom(completeMsg);

            // 양 팀 모두 정답 설정 완료 확인
            return answeredTeams.size() == 2;
        } else {
            // 1v1 정답 설정
            if (playerAnswers.containsKey(userId)) return false;

            playerAnswers.put(userId, answer);
            answeredPlayers.add(userId);
            serverCore.printDisplay("정답 등록: " + userId + " - " + answer);

            return answeredPlayers.size() == gameMode.getMaxPlayers();
        }
    }

    // 모든 플레이어 정답 설정 후, 첫 턴을 시작
    public void startFirstTurn() {
        if (!isGameRunning) return;

        currentRound = 1;
        isTopHalf = true;

        ClientHandler startPlayer = null;
        if (gameMode == Message.GameMode.ONE_VS_ONE) {
            // 1v1: 첫 번째 플레이어(players.get(0))가 1회 초
            startPlayer = players.get(0);
        } else if (gameMode == Message.GameMode.TWO_VS_TWO) {
            // 2v2: Team 1의 팀 대표가 1회 초
            String team1LeaderId = teamLeaders.get(1);
            startPlayer = players.stream()
                    .filter(p -> p.userId.equals(team1LeaderId))
                    .findFirst().orElse(null);
        } else {
            // 기본적으로 첫 번째 플레이어
            startPlayer = players.get(0);
        }

        sendTurnInfo(startPlayer); // startPlayer에게 첫 턴 부여
        serverCore.printDisplay("게임 턴 시작: " + gameId);
        serverCore.broadcastUserList();
    }

    // 게임 시작
    public void startGame() {
        isGameRunning = true;
        gameId = "G" + System.currentTimeMillis();

        answeredPlayers.clear();
        answeredTeams.clear();
        teamAnswers.clear();

        // 팀 점수 초기화
        team1Score = 0;
        team2Score = 0;

        // 플레이어 상태 변경
        for (ClientHandler player : players) {
            player.userStatus = Message.UserStatus.IN_GAME;
        }

        // 정답 설정 메시지 브로드캐스트
        for (ClientHandler player : players) {
            Message startMsg = new Message(Message.MessageType.START_GAME, "SERVER");
            startMsg.setGameMode(gameMode);
            startMsg.setDifficulty(difficulty);
            startMsg.setTurnTimeLimit(turnTimeLimit);
            startMsg.setGameId(gameId);

            int playerTeam = playerTeams.getOrDefault(player.userId, 0);
            String leaderId = teamLeaders.get(playerTeam);

            if (playerTeam != 0 && player.userId.equals(leaderId)) {
                // 팀 대표: 정답 설정 요청
                startMsg.setContent("정답을 설정해주세요. 게임 시작!");
                startMsg.setTeamLeaderId(leaderId);
                startMsg.setWaitingForAnswer(false);
            } else if (gameMode == Message.GameMode.TWO_VS_TWO) {
                // 비대표 팀원: 대기
                startMsg.setContent(leaderId + "님이 정답을 설정 중입니다.");
                startMsg.setTeamLeaderId(leaderId);
                startMsg.setWaitingForAnswer(true);
            } else {
                // 1v1 모드: 모든 플레이어에게 정답 요청
                startMsg.setContent("정답을 설정해주세요. 게임 시작!");
                startMsg.setTeamLeaderId(player.userId);
                startMsg.setWaitingForAnswer(false);
            }

            player.sendMessage(startMsg);
        }

        serverCore.broadcastUserList();
    }

    // 턴 정보 생성 및 전송
    public void sendTurnInfo(ClientHandler turnPlayer) {
        // 기존 타이머 중지
        if(turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null;
        }

        currentTurnPlayerId = (turnPlayer != null) ? turnPlayer.userId : null;

        // 서버 턴 타이머 시작
        if (turnPlayer != null && isGameRunning) {

            int delay = turnTimeLimit.getSeconds() * 1000;
            turnTimer = new java.util.Timer();

            ClientHandler finalTurnPlayer = turnPlayer;
            turnTimer.schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    serverCore.printDisplay("Turn Timeout: " + finalTurnPlayer.userId + " in Room " + roomId);
                    finalTurnPlayer.sendMessage(new Message(Message.MessageType.TURN_TIMEOUT, "SERVER"));

                    // 다음 턴으로 강제 진행
                    ClientHandler nextPlayer = getNextTurnPlayer(finalTurnPlayer);
                    if (nextPlayer != null) {
                        sendTurnInfo(nextPlayer);
                    }
                }
            }, delay);
        }

        Message turnMsg = new Message(Message.MessageType.TURN_INFO, "SERVER");
        turnMsg.setRound(currentRound);
        turnMsg.setTop(isTopHalf);
        turnMsg.setCurrentTurnPlayer(currentTurnPlayerId);
        turnMsg.setContent(currentRound + "회 " + (isTopHalf ? "초" : "말"));
        broadcastToRoom(turnMsg);
    }

    // 추측 처리
    public void handleGuess(ClientHandler player, String guess) {
        if (!isGameRunning) {
            return;
        }

        // 턴 제약: 현재 턴 플레이어만 입력 가능 (1v1, 2v2 모두 적용)
        if (!player.userId.equals(getCurrentTurnPlayerId())) {
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

        // 추측이 완료되면 타이머 중지
        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null;
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

        // 결과 공유 로직
        if (gameMode == Message.GameMode.TWO_VS_TWO) {
            int myTeam = playerTeams.getOrDefault(player.userId, 0);

            for (ClientHandler p : players) {
                // 같은 팀원에게는 추측 숫자와 결과를 모두 공유
                if (playerTeams.getOrDefault(p.userId, 0) == myTeam) {
                    // 팀 채팅이 아닌 일반 메시지로 전송하여 기록에 남김
                    p.sendMessage(resultMsg);
                } else {
                    // 상대 팀에게는 추측 숫자를 비공개하고 결과만 공개
                    Message opponentResultMsg = Message.createGuessResult(player.userId, "???", strike, ball);
                    // Content를 사용하여 상대 팀에게 추측 결과를 명시적으로 알림
                    opponentResultMsg.setContent("상대 팀 (" + player.userId + ") 추측: " + strike + "S " + ball + "B");
                    p.sendMessage(opponentResultMsg);
                }
            }
        } else {
            // 1v1: 기존 로직 유지 (모두에게 공개)
            broadcastToRoom(resultMsg);
        }

        // 기록 저장
        saveGameDetail(gameId, currentRound, player.userId, guess, strike + "S " + ball + "B");

        // 승리 체크
        if (strike == difficulty.getDigitCount()) {
            if (gameMode == Message.GameMode.TWO_VS_TWO) {
                endGame(player.userId, false, playerTeams.getOrDefault(player.userId, 0));
            } else {
                endGame(player.userId, false, 0);
            }
            return;
        }

        // 다음 턴으로 이동
        ClientHandler nextPlayer = getNextTurnPlayer(player);
        if (nextPlayer != null) {
            sendTurnInfo(nextPlayer);
        }
    }

    // 현재 턴 플레이어 ID
    public String getCurrentTurnPlayerId() {
        return currentTurnPlayerId;
    }

    // 상대방 정답 찾기
    private String getTargetAnswer(ClientHandler currentPlayer) {
        if (gameMode == Message.GameMode.ONE_VS_ONE) {
            // 1v1: 상대방의 playerAnswers 찾기
            for (ClientHandler p : players) {
                if (!p.userId.equals(currentPlayer.userId)) {
                    return playerAnswers.get(p.userId);
                }
            }
        } else if (gameMode == Message.GameMode.TWO_VS_TWO) {
            // 2v2: 상대 팀의 teamAnswers 찾기
            int myTeam = playerTeams.getOrDefault(currentPlayer.userId, 0);
            if (myTeam == 0) return null;

            int targetTeam = (myTeam == 1) ? 2 : 1;
            return teamAnswers.get(String.valueOf(targetTeam));
        }
        return null;
    }

    private ClientHandler getNextTurnPlayer(ClientHandler currentPlayer) {
        if (players.isEmpty()) return null;

        // 1v1 모드
        if (gameMode == Message.GameMode.ONE_VS_ONE) {
            int currentPlayerIndex = players.indexOf(currentPlayer);
            int nextPlayerIndex = (currentPlayerIndex + 1) % players.size();

            isTopHalf = !isTopHalf;

            currentRound++;

            // 9회말(18턴) 종료 시 무승부 처리
            if (currentRound > 9 * 2) {
                // 9회말까지 진행하려면 총 18턴이 필요
                // 현재 라운드가 매 턴 증가하므로, 18턴을 넘어 19번째 라운드가 될 때 종료 처리
                endGame(null, true, 0);
                return null;
            }

            return players.get(nextPlayerIndex);
        }

        // 2v2 모드
        int currentTeam = playerTeams.getOrDefault(currentPlayer.userId, 0);

        //  현재 팀의 다음 입력 순번을 계산하고 teamLeaders에 저장
        int currentPlayerIndex = players.indexOf(currentPlayer);
        int nextPlayerInTeamIndex = -1;

        // 현재 팀 내에서 다음 순번 플레이어 찾기
        for (int i = currentPlayerIndex + 1; i < players.size(); i++) {
            if (playerTeams.getOrDefault(players.get(i).userId, 0) == currentTeam) {
                nextPlayerInTeamIndex = i;
                break;
            }
        }

        // 만약 다음 순번 플레이어가 없다면, 팀의 첫 번째 플레이어(팀 대표)로 순서를 순환
        if (nextPlayerInTeamIndex == -1) {
            for (int i = 0; i < players.size(); i++) {
                if (playerTeams.getOrDefault(players.get(i).userId, 0) == currentTeam) {
                    nextPlayerInTeamIndex = i;
                    break;
                }
            }
        }

        // 다음 순번 플레이어 ID를 teamLeaders에 저장
        if (nextPlayerInTeamIndex != -1) {
            String nextPlayerInTeamId = players.get(nextPlayerInTeamIndex).userId;
            teamLeaders.put(currentTeam, nextPlayerInTeamId);
        }


        // 공수 전환 및 회차 변경
        if (isTopHalf) {
            isTopHalf = false; // 말로 변경
        } else {
            isTopHalf = true;
            currentRound++; // 다음 회차 초로 변경
        }

        if (currentRound > 9) {
            endGame(null, true, 0); // 9회말 종료 시 무승부
            return null;
        }

        // 다음 팀 결정
        int nextTeam = (currentTeam == 1) ? 2 : 1;


        //다음 팀의 입력 권한자를 결정
        String nextTurnPlayerId = teamLeaders.get(nextTeam);

        return players.stream()
                .filter(p -> p.userId.equals(nextTurnPlayerId))
                .findFirst()
                .orElse(null);
    }

    // 게임 종료
    public void endGame(String winnerId, boolean isDraw, int winnerTeam) {
        isGameRunning = false;

        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null;
        }

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

    // 난이도에 따른 정답 자릿수 반환
    public int getDigitCount() {
        if (difficulty == null) return 3;
        return difficulty.getDigitCount();
    }

    // 특정 팀이 정답 설정을 완료했는지 확인
    public boolean isTeamAnswerSet(int teamNumber) {
        return answeredTeams.contains(teamNumber);
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