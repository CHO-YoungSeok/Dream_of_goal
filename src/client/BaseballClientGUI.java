package client;

import common.Message;
import client.network.MessageHandler;
import client.network.NetworkManager;
import client.state.GameStateManager;
import client.ui.*;
import client.util.UIHelper;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.List;

/**
 * Main client GUI class - coordinates all components
 */
public class BaseballClientGUI extends JFrame implements MessageHandler,
        LoginListener, LobbyListener, RoomWaitingListener,
        GamePanelListener, ResultPanelListener {

    // UI State
    private enum UIState {
        LOGIN_SCREEN,
        LOBBY_SCREEN,
        ROOM_WAITING_SCREEN,
        GAME_SCREEN,
        RESULT_SCREEN
    }

    private UIState currentState = UIState.LOGIN_SCREEN;
    private CardLayout cardLayout;
    private JPanel mainPanel;

    // Screen constants
    private static final String LOGIN_PANEL = "LOGIN";
    private static final String LOBBY_PANEL = "LOBBY";
    private static final String ROOM_WAITING_PANEL = "ROOM_WAITING";
    private static final String GAME_PANEL = "GAME";
    private static final String RESULT_PANEL = "RESULT";

    // UI Components
    private LoginPanel loginPanel;
    private LobbyPanel lobbyPanel;
    private RoomWaitingPanel roomWaitingPanel;
    private GamePanel gamePanel;
    private ResultPanel resultPanel;

    // Managers
    private NetworkManager networkManager;
    private GameStateManager stateManager;

    public BaseballClientGUI() {
        super("Multi ClientGUI");
        stateManager = GameStateManager.getInstance();
        networkManager = new NetworkManager(this);
        buildGUI();
        setBounds(400, 100, 550, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }

    private void buildGUI() {
        setLayout(new BorderLayout());

        // Create CardLayout container
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        // Create all panels
        loginPanel = new LoginPanel(this);
        lobbyPanel = new LobbyPanel(this);
        roomWaitingPanel = new RoomWaitingPanel(this, stateManager);
        gamePanel = new GamePanel(this, stateManager);
        resultPanel = new ResultPanel(this);

        // Add to card layout
        mainPanel.add(loginPanel, LOGIN_PANEL);
        mainPanel.add(lobbyPanel, LOBBY_PANEL);
        mainPanel.add(roomWaitingPanel, ROOM_WAITING_PANEL);
        mainPanel.add(gamePanel, GAME_PANEL);
        mainPanel.add(resultPanel, RESULT_PANEL);

        add(mainPanel, BorderLayout.CENTER);

        // Show login screen initially
        cardLayout.show(mainPanel, LOGIN_PANEL);
    }

    // ========== MessageHandler Implementation ==========

    @Override
    public void handleMessage(Message msg) {
        switch (msg.getType()) {
            case LOGIN_RESPONSE:
                handleLoginResponse(msg);
                break;
            case REGISTER_RESPONSE:
                handleRegisterResponse(msg);
                break;
            case ROOM_LIST_RESPONSE:
                handleRoomListResponse(msg);
                break;
            case USER_LIST_RESPONSE:
                handleUserListResponse(msg);
                break;
            case CREATE_ROOM_RESPONSE:
                handleCreateRoomResponse(msg);
                break;
            case JOIN_ROOM_RESPONSE:
                handleJoinRoomResponse(msg);
                break;
            case EDIT_ROOM_RESPONSE:
                handleEditRoomResponse(msg);
                break;
            case ROOM_INFO_UPDATE:
                handleRoomInfoUpdate(msg);
                break;
            case READY_STATUS_UPDATE:
                handleReadyStatusUpdate(msg);
                break;
            case START_GAME:
                handleStartGame(msg);
                break;
            case TURN_INFO:
                handleTurnInfo(msg);
                break;
            case GUESS_RESULT:
                handleGuessResult(msg);
                break;
            case TURN_TIMEOUT:
                UIHelper.showToast(this, "Turn timeout!");
                break;
            case END_GAME:
            case GAME_RESULT:
                handleGameResult(msg);
                break;
            case CHAT_ALL:
                handleChatAll(msg);
                break;
            case CHAT_TEAM:
                String teamChatMsg = String.format("[To team] %s: %s", msg.getUserId(), msg.getContent());

                // 현재 게임 중인 경우
                if (currentState == UIState.GAME_SCREEN) {
                    gamePanel.displayMessage(teamChatMsg, new Color(0, 0, 139)); // Dark blue
                } else if (currentState == UIState.ROOM_WAITING_SCREEN) {
                    roomWaitingPanel.addChatMessage(teamChatMsg, new Color(0, 0, 139));
                }
                break;
            case CHAT_ROOM:
                handleChatRoom(msg);
                break;
            case CHAT_WHISPER:
                handleWhisper(msg);
                break;
            case ERROR:
                handleError(msg);
                break;
            default:
                System.out.println("Unhandled message type: " + msg.getType());
                break;
        }
    }

    private void handleWhisper(Message msg) {
        String displayMessage;
        // Check if the message is from the current user
        if (msg.getUserId().equals(stateManager.getCurrentUserId())) {
            displayMessage = String.format("[To %s] %s", msg.getTargetUserId(), msg.getContent());
        } else {
            displayMessage = String.format("[From %s] %s", msg.getUserId(), msg.getContent());
        }

        switch (currentState) {
            case LOBBY_SCREEN:
                lobbyPanel.addChatMessage(displayMessage, new Color(139, 0, 0)); // Dark red
                break;
            case ROOM_WAITING_SCREEN:
                roomWaitingPanel.addChatMessage(displayMessage, new Color(139, 0, 0)); // Dark red
                break;
            case GAME_SCREEN:
                gamePanel.displayMessage(displayMessage, new Color(139, 0, 0)); // Dark red
                break;
        }
    }

    private void handleLoginResponse(Message msg) {
        if (msg.isSuccess()) {
            stateManager.setAuthenticated(true, msg.getUserId(), null);
            switchToLobbyScreen();
            UIHelper.showToast(this, "Login successful!");
        } else {
            JOptionPane.showMessageDialog(this,
                msg.getErrorMessage() != null ? msg.getErrorMessage() : "Login failed",
                "Login Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleRegisterResponse(Message msg) {
        if (msg.isSuccess()) {
            JOptionPane.showMessageDialog(this,
                "Registration successful! Please login.",
                "Success",
                JOptionPane.INFORMATION_MESSAGE);
            loginPanel.clearRegisterFields();
        } else {
            JOptionPane.showMessageDialog(this,
                msg.getErrorMessage() != null ? msg.getErrorMessage() : "Registration failed",
                "Registration Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleRoomListResponse(Message msg) {
        if (msg.getData() instanceof List) {
            java.util.List<Message> roomList = (java.util.List<Message>) msg.getData();
            if (currentState == UIState.LOBBY_SCREEN) {
                SwingUtilities.invokeLater(() -> lobbyPanel.updateRoomList(roomList));
            }
        }
    }

    private void handleUserListResponse(Message msg) {
        if (msg.isSuccess()) {
            java.util.List<String> connectedUsers = msg.getConnectedUsers();
            java.util.Map<String, Message.UserStatus> statusMap = msg.getUserStatusMap();

            if (connectedUsers != null && statusMap != null) {
                // Format user list with status indicators
                java.util.List<String> formattedUsers = new java.util.ArrayList<>();
                for (String userId : connectedUsers) {
                    Message.UserStatus status = statusMap.get(userId);
                    String statusIndicator = "";

                    if (status == Message.UserStatus.ONLINE) {
                        statusIndicator = "\u2B24"; // Green dot (online in lobby)
                    } else if (status == Message.UserStatus.IN_ROOM) {
                        statusIndicator = "\u25FC"; // Yellow square (in room waiting)
                    } else if (status == Message.UserStatus.IN_GAME) {
                        statusIndicator = "\u25B2"; // Red triangle (in game)
                    }

                    formattedUsers.add(statusIndicator + " " + userId);
                }

                // Update lobby panel if in lobby state
                if (currentState == UIState.LOBBY_SCREEN) {
                    SwingUtilities.invokeLater(() -> {
                        lobbyPanel.updateUserList(formattedUsers);
                    });
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleCreateRoomResponse(Message msg) {
        if (msg.isSuccess()) {
            stateManager.setCurrentRoomId(msg.getRoomId());
            stateManager.setCurrentRoomName(msg.getRoomName());
            stateManager.setRoomMasterUserId(msg.getRoomMaster());
            stateManager.setCurrentGameMode(msg.getGameMode());
            stateManager.setCurrentDifficulty(msg.getDifficulty());
            stateManager.setCurrentTurnTimeLimit(msg.getTurnTimeLimit());
            stateManager.setCurrentRoomPassword(msg.getRoomPassword());

            java.util.Map<String, Integer> playerTeams = null;

            // 플레이어 리스트 처리
            if (msg.getData() instanceof java.util.Map) {
                java.util.Map<String, Object> roomData = (java.util.Map<String, Object>) msg.getData();
                java.util.List<String> players = (java.util.List<String>) roomData.get("players");
                java.util.Map<String, Boolean> readyStatus = (java.util.Map<String, Boolean>) roomData.get("readyStatus");
                playerTeams = (java.util.Map<String, Integer>) roomData.get("playerTeams");

                stateManager.setRoomPlayersList(players);
                stateManager.setPlayerReadyStatus(readyStatus);
            }

            roomWaitingPanel.updateRoomInfo();
            roomWaitingPanel.updatePlayerList();

            if (playerTeams != null) {
                stateManager.setPlayerTeamMap(playerTeams);
                stateManager.setMyTeamNumber(playerTeams.getOrDefault(stateManager.getCurrentUserId(), 0));
            }

            switchToRoomWaitingScreen();
        } else {
            JOptionPane.showMessageDialog(this,
                "Failed to create room: " + msg.getErrorMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleJoinRoomResponse(Message msg) {
        if (msg.isSuccess()) {
            stateManager.setCurrentRoomId(msg.getRoomId());
            stateManager.setCurrentRoomName(msg.getRoomName());
            stateManager.setRoomMasterUserId(msg.getRoomMaster());
            stateManager.setCurrentGameMode(msg.getGameMode());
            stateManager.setCurrentDifficulty(msg.getDifficulty());
            stateManager.setCurrentTurnTimeLimit(msg.getTurnTimeLimit());
            stateManager.setCurrentRoomPassword(msg.getRoomPassword());

            java.util.Map<String, Integer> playerTeams = null;

            // 플레이어 리스트 처리
            if (msg.getData() instanceof java.util.Map) {
                java.util.Map<String, Object> roomData = (java.util.Map<String, Object>) msg.getData();
                java.util.List<String> players = (java.util.List<String>) roomData.get("players");
                java.util.Map<String, Boolean> readyStatus = (java.util.Map<String, Boolean>) roomData.get("readyStatus");
                playerTeams = (java.util.Map<String, Integer>) roomData.get("playerTeams");

                stateManager.setRoomPlayersList(players);
                stateManager.setPlayerReadyStatus(readyStatus);
            }

            roomWaitingPanel.updateRoomInfo();
            roomWaitingPanel.updatePlayerList();

            if (playerTeams != null) {
                stateManager.setPlayerTeamMap(playerTeams);
                stateManager.setMyTeamNumber(playerTeams.getOrDefault(stateManager.getCurrentUserId(), 0));
            }

            switchToRoomWaitingScreen();
        } else {
            JOptionPane.showMessageDialog(this,
                "Failed to join room: " + msg.getErrorMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleEditRoomResponse(Message msg) {
        if (msg.isSuccess()) {
            // 방 정보 업데이트
            stateManager.setCurrentRoomId(msg.getRoomId());
            stateManager.setCurrentRoomName(msg.getRoomName());
            stateManager.setRoomMasterUserId(msg.getRoomMaster());
            stateManager.setCurrentDifficulty(msg.getDifficulty());
            stateManager.setCurrentTurnTimeLimit(msg.getTurnTimeLimit());
            stateManager.setCurrentRoomPassword(msg.getRoomPassword());

            java.util.Map<String, Integer> playerTeams = null;

            // 플레이어 리스트 처리
            if (msg.getData() instanceof java.util.Map) {
                java.util.Map<String, Object> roomData = (java.util.Map<String, Object>) msg.getData();
                java.util.List<String> players = (java.util.List<String>) roomData.get("players");
                java.util.Map<String, Boolean> readyStatus = (java.util.Map<String, Boolean>) roomData.get("readyStatus");
                playerTeams = (java.util.Map<String, Integer>) roomData.get("playerTeams");

                stateManager.setRoomPlayersList(players);
                stateManager.setPlayerReadyStatus(readyStatus);
            }

            roomWaitingPanel.updateRoomInfo();
            roomWaitingPanel.updatePlayerList();

            if (playerTeams != null) {
                stateManager.setPlayerTeamMap(playerTeams);
                stateManager.setMyTeamNumber(playerTeams.getOrDefault(stateManager.getCurrentUserId(), 0));
            }

            UIHelper.showToast(this, "방 정보가 변경되었습니다.");
        } else {
            JOptionPane.showMessageDialog(this,
                "Failed to edit room: " + msg.getErrorMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleRoomDataResponse(Message msg) {
        if (msg.isSuccess()) {
            stateManager.setCurrentRoomId(msg.getRoomId());
            stateManager.setCurrentRoomName(msg.getRoomName());
            stateManager.setRoomMasterUserId(msg.getRoomMaster());
            stateManager.setCurrentGameMode(msg.getGameMode());
            stateManager.setCurrentDifficulty(msg.getDifficulty());
            stateManager.setCurrentTurnTimeLimit(msg.getTurnTimeLimit());

            if (msg.getData() instanceof Map) {
                Map<String, Object> roomData = (Map<String, Object>) msg.getData();
                stateManager.setRoomPlayersList((List<String>) roomData.get("players"));
                stateManager.setPlayerReadyStatus((Map<String, Boolean>) roomData.get("readyStatus"));

                Map<String, Integer> playerTeams = (Map<String, Integer>) roomData.get("playerTeams");
                if (playerTeams != null) {
                    stateManager.setPlayerTeamMap(playerTeams);
                    int myTeam = playerTeams.getOrDefault(stateManager.getCurrentUserId(), 0);
                    stateManager.setMyTeamNumber(myTeam);
                }
            }
            roomWaitingPanel.updateRoomInfo();
            roomWaitingPanel.updatePlayerList();
            switchToRoomWaitingScreen();
        } else {
            JOptionPane.showMessageDialog(this, msg.getErrorMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }


    @SuppressWarnings("unchecked")
    private void handleRoomInfoUpdate(Message msg) {
        if (msg.getData() instanceof Map) {
            Map<String, Object> roomData = (Map<String, Object>) msg.getData();
            stateManager.setRoomPlayersList((List<String>) roomData.get("players"));
            stateManager.setPlayerReadyStatus((Map<String, Boolean>) roomData.get("readyStatus"));
            stateManager.setRoomMasterUserId(msg.getRoomMaster());

            Map<String, Integer> playerTeams = (Map<String, Integer>) roomData.get("playerTeams");
            if (playerTeams != null) {
                int myTeam = playerTeams.getOrDefault(stateManager.getCurrentUserId(), 0);
                stateManager.setMyTeamNumber(myTeam);
                System.out.println("DEBUG: 팀 정보 갱신 - MyID: " + stateManager.getCurrentUserId() + ", MyTeam: " + myTeam);
            }
            roomWaitingPanel.updatePlayerList();
            roomWaitingPanel.updateRoomInfo();
        }
    }

    private void handleReadyStatusUpdate(Message msg) {
        if (msg.getData() instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Boolean> readyStatus = (java.util.Map<String, Boolean>) msg.getData();
            stateManager.setPlayerReadyStatus(readyStatus);
            roomWaitingPanel.updatePlayerList();
        }
    }

    private void handleStartGame(Message msg) {
        stateManager.setCurrentGameId(msg.getGameId());
        if (msg.getDifficulty() != null) {
            stateManager.setCurrentDifficulty(msg.getDifficulty());
        }
        if (msg.getTurnTimeLimit() != null) {
            stateManager.setCurrentTurnTimeLimit(msg.getTurnTimeLimit());
        }

        stateManager.setTeamLeaderId(msg.getTeamLeaderId());
        stateManager.setWaitingForAnswer(msg.isWaitingForAnswer());

        gamePanel.displayMessage("게임 시작! " + msg.getContent());
        gamePanel.setupForGame(stateManager.getDigitCount());
        switchToGameScreen();

        if (!stateManager.isWaitingForAnswer()) {
            String myAnswerKey = UIHelper.promptForAnswerKey(this, stateManager.getDigitCount());
            stateManager.setMyAnswerKey(myAnswerKey);
            gamePanel.displayMessage("정답이 설정되었습니다: " + myAnswerKey);

            Message answerMsg = Message.createGuessMessage(stateManager.getCurrentUserId(), myAnswerKey);
            networkManager.sendMessage(answerMsg);
        } else {
            gamePanel.displayMessage("팀 대표(" + stateManager.getTeamLeaderId() + ")가 정답을 설정 중입니다. 대기 중...", Color.GRAY);
        }
    }

    private void handleTurnInfo(Message msg) {
        stateManager.setCurrentRound(msg.getRound());
        stateManager.setCurrentlyTopInning(msg.isTop());
        stateManager.setCurrentTurnPlayerId(msg.getCurrentTurnPlayer());

        String myId = stateManager.getCurrentUserId().trim();
        String turnId = msg.getCurrentTurnPlayer().trim();
        boolean isMyTurn = myId.equalsIgnoreCase(turnId);
        String roundInfo = msg.getRoundInfo();
        String turnStatus = isMyTurn ? "★ Your Turn ★" : "Waiting for " + msg.getCurrentTurnPlayer();

        gamePanel.updateTurnInfo(roundInfo, turnStatus, isMyTurn);
        stateManager.setRemainingSeconds(stateManager.getTurnTimeLimitSeconds());
    }

    /*
    private void handleGuessResult(Message msg) {
        String guessUserId = msg.getUserId();
        int myTeam = stateManager.getMyTeamNumber();
        int guessUserTeam = stateManager.getPlayerTeamMap().getOrDefault(guessUserId, 0);
        Message.GameMode mode = stateManager.getCurrentGameMode();

        SwingUtilities.invokeLater(() -> {
            // 판정 조건: 1v1이거나 같은 팀인 경우
            boolean isSameTeam = (mode == Message.GameMode.ONE_VS_ONE) || (myTeam != 0 && myTeam == guessUserTeam);

            if (isSameTeam) {
                gamePanel.addPrediction(guessUserId, msg.getGuess(), msg.getStrike(), msg.getBall());
                String teamLog = String.format("[우리팀] %s: %s -> %dS %dB",
                        guessUserId, msg.getGuess(), msg.getStrike(), msg.getBall());
                gamePanel.displayMessage(teamLog, new Color(0, 0, 139));
            } else {
                String otherLog = String.format("[상대팀] %s: %s -> %dS %dB",
                        guessUserId, msg.getGuess(), msg.getStrike(), msg.getBall());
                gamePanel.displayMessage(otherLog, new Color(139, 0, 0));
            }
        });
    } */

    private void handleGuessResult(Message msg) {
    String guessUserId = msg.getUserId();
    String guessDigit = msg.getGuess();

    int myTeam = stateManager.getMyTeamNumber();
    int attackerTeam = stateManager.getPlayerTeamMap().getOrDefault(guessUserId, 0);

    SwingUtilities.invokeLater(() -> {
        if (myTeam != 0 && myTeam == attackerTeam) {
            gamePanel.addPrediction(guessUserId, guessDigit, msg.getStrike(), msg.getBall());

            gamePanel.displayMessage(String.format("[우리팀] %s: %s -> %dS %dB",
                guessUserId, guessDigit, msg.getStrike(), msg.getBall()), new Color(0, 0, 139));
        } else {
            // 상대 팀은 채팅으로만 숫자 공개
            gamePanel.displayMessage(String.format("[상대팀] %s: %s -> %dS %dB",
                guessUserId, guessDigit, msg.getStrike(), msg.getBall()), new Color(139, 0, 0));
        }
    });
}


    private void handleGameResult(Message msg) {
        String winnerId = msg.getWinnerId();
        boolean isDraw = msg.isDraw();

        String resultText;
        if (isDraw) {
            resultText = "무승부";
        } else if (stateManager.getCurrentUserId().equals(winnerId)) {
            resultText = "승리!";
        } else {
            resultText = "패배";
        }

        // Stop timer
        if (stateManager.getTurnTimer() != null) {
            stateManager.getTurnTimer().stop();
        }

        resultPanel.setResult(resultText);
        switchToResultScreen();
    }

    private void handleChatAll(Message msg) {
        String senderUserId = msg.getUserId();
        String content = msg.getContent();
        String formattedMessage = String.format("[전체] %s: %s", senderUserId, content);

        if (currentState == UIState.LOBBY_SCREEN) {
            SwingUtilities.invokeLater(() -> lobbyPanel.addChatMessage(formattedMessage, new Color(0, 128, 0))); // Dark green
        } else if (currentState == UIState.ROOM_WAITING_SCREEN) {
            SwingUtilities.invokeLater(() -> roomWaitingPanel.addChatMessage(formattedMessage, new Color(0, 128, 0))); // Dark green
        } else if (currentState == UIState.GAME_SCREEN) {
            SwingUtilities.invokeLater(() -> gamePanel.displayMessage(formattedMessage, new Color(0, 128, 0))); // Dark green
        }
    }

    private void handleChatRoom(Message msg) {
        String senderUserId = msg.getUserId();
        String content = msg.getContent();
        String formattedMessage = String.format("%s: %s", senderUserId, content);

        if (currentState == UIState.ROOM_WAITING_SCREEN) {
            SwingUtilities.invokeLater(() -> roomWaitingPanel.addChatMessage(formattedMessage, new Color(50, 50, 50))); // Dark gray
        } else if (currentState == UIState.GAME_SCREEN) {
            SwingUtilities.invokeLater(() -> gamePanel.displayMessage(formattedMessage, new Color(50, 50, 50))); // Dark gray
        }
    }

    private void handleChatTeam(Message msg) {
        String teamChatMsg = String.format("[Team] %s: %s", msg.getUserId(), msg.getContent());
        Color teamColor = new Color(0, 0, 139);
        if (currentState == UIState.GAME_SCREEN) gamePanel.displayMessage(teamChatMsg, teamColor);
        else if (currentState == UIState.ROOM_WAITING_SCREEN) roomWaitingPanel.addChatMessage(teamChatMsg, teamColor);
    }

    private void handleError(Message msg) {
        Message.ErrorCode errorCode = msg.getErrorCode();
        String errorMessage = msg.getErrorMessage();
        JOptionPane.showMessageDialog(this,
            String.format("[%d] %s", errorCode != null ? errorCode.getCode() : 0, errorMessage),
            "Error",
            JOptionPane.ERROR_MESSAGE);

        // Handle specific errors
        if (errorCode == Message.ErrorCode.ALREADY_LOGGED_IN ||
            errorCode == Message.ErrorCode.LOGIN_FAILED) {
            if (currentState != UIState.LOGIN_SCREEN) {
                networkManager.disconnect();
                switchToLoginScreen();
            }
        }
    }

    // ========== LoginListener Implementation ==========

    @Override
    public void onLoginRequested(String userId, String password) {
        if (!networkManager.isConnected()) {
            networkManager.connect();
        }
        Message loginMsg = Message.createLoginRequest(userId, password);
        networkManager.sendMessage(loginMsg);
        stateManager.setCurrentUserId(userId);
        stateManager.setCurrentPassword(password);
    }

    @Override
    public void onRegisterRequested(String userId, String password, String nickname) {
        if (!networkManager.isConnected()) {
            networkManager.connect();
        }
        Message registerMsg = Message.createRegisterRequest(userId, password, nickname);
        networkManager.sendMessage(registerMsg);
    }

    @Override
    public void onExitRequested() {
        System.exit(0);
    }

    // ========== LobbyListener Implementation ==========

    @Override
    public void onCreateRoomRequested(String roomName, Message.GameMode gameMode,
                                      Message.Difficulty difficulty, Message.TurnTimeLimit turnTimeLimit,
                                      String password) {
        Message msg = Message.createCreateRoomRequest(
            stateManager.getCurrentUserId(),
            roomName,
            gameMode,
            difficulty,
            turnTimeLimit,
            password
        );
        networkManager.sendMessage(msg);
    }

    @Override
    public void onJoinRoomRequested(int roomId, String password) {
        Message msg = Message.createJoinRoomRequest(stateManager.getCurrentUserId(), roomId, password);
        networkManager.sendMessage(msg);
    }

    @Override
    public void onRefreshRequested() {
        Message msg = new Message(Message.MessageType.ROOM_LIST_REQUEST, stateManager.getCurrentUserId());
        networkManager.sendMessage(msg);
    }

    @Override
    public void onEditRoomConfirmed(String roomName, Message.Difficulty difficulty,
                                    Message.TurnTimeLimit turnTimeLimit,
                                    String password) {
        Message msg = Message.createEditRoomRequest(
            stateManager.getCurrentUserId(),
            stateManager.getCurrentRoomId(),
            roomName,
            difficulty,
            turnTimeLimit,
            password
        );
        networkManager.sendMessage(msg);
    }

    @Override
    public void onLobbyChatSent(String message) {
        Message msg;
        if (message.equals("/help")) {
            lobbyPanel.addChatMessage(" ", Color.BLACK);
            lobbyPanel.addChatMessage(" ", Color.BLACK);
            lobbyPanel.addChatMessage(" ", Color.BLACK);
            lobbyPanel.addChatMessage("/stats [user_id]: [user_id]사용자의 전적 확인하기", new Color(0, 139, 139)); // Dark cyan
            lobbyPanel.addChatMessage("/all: 전체 채팅하기", new Color(0, 139, 139)); // Dark cyan
            lobbyPanel.addChatMessage("/team: 팀 채팅하기", new Color(0, 139, 139)); // Dark cyan
            lobbyPanel.addChatMessage("/w [user_id]: 귓속말 하기", new Color(0, 139, 139)); // Dark cyan
            return;
        } else if (message.startsWith("/w ")) {
            String[] parts = message.split(" ", 3);
            if (parts.length < 3) {
                lobbyPanel.addChatMessage("Usage: /w [ID] [Message]", new Color(255, 140, 0)); // Dark orange
                return;
            }
            String targetId = parts[1];
            String content = parts[2];
            msg = Message.createChatMessage(Message.MessageType.CHAT_WHISPER, stateManager.getCurrentUserId(), content, targetId);
            lobbyPanel.addChatMessage(String.format("[To %s] %s", targetId, content), new Color(139, 0, 0)); // Dark red
        } else if (message.startsWith("/all ")) {
            String content = message.substring(5);
            msg = new Message(Message.MessageType.CHAT_ALL, stateManager.getCurrentUserId(), content);
            lobbyPanel.addChatMessage(String.format("[전체] %s: %s", stateManager.getCurrentUserId(), content), new Color(0, 128, 0)); // Dark green
        } else {
            msg = new Message(Message.MessageType.CHAT_ALL, stateManager.getCurrentUserId(), message);
            lobbyPanel.addChatMessage(String.format("[전체] %s: %s", stateManager.getCurrentUserId(), message), new Color(0, 128, 0)); // Dark green
        }
        networkManager.sendMessage(msg);
    }

    @Override
    public void onRoomChatSent(String message) {
        Message msg;
        if (message.equals("/help")) {
            roomWaitingPanel.addChatMessage(" ", Color.BLACK);
            roomWaitingPanel.addChatMessage(" ", Color.BLACK);
            roomWaitingPanel.addChatMessage(" ", Color.BLACK);
            roomWaitingPanel.addChatMessage("/stats [user_id]: [user_id]사용자의 전적 확인하기", new Color(0, 139, 139)); // Dark cyan
            roomWaitingPanel.addChatMessage("/all: 전체 채팅하기", new Color(0, 139, 139)); // Dark cyan
            roomWaitingPanel.addChatMessage("/team: 팀 채팅하기", new Color(0, 139, 139)); // Dark cyan
            roomWaitingPanel.addChatMessage("/w [user_id]: 귓속말 하기", new Color(0, 139, 139)); // Dark cyan
            return;
        } else if (message.startsWith("/w ")) {
            String[] parts = message.split(" ", 3);
            if (parts.length < 3) {
                roomWaitingPanel.addChatMessage("Usage: /w [ID] [Message]", new Color(255, 140, 0)); // Dark orange
                return;
            }
            String targetId = parts[1];
            String content = parts[2];
            msg = Message.createChatMessage(Message.MessageType.CHAT_WHISPER, stateManager.getCurrentUserId(), content, targetId);
            roomWaitingPanel.addChatMessage(String.format("[To %s] %s", targetId, content), new Color(139, 0, 0)); // Dark red
        } else if (message.startsWith("/all ")) {
            String content = message.substring(5);
            msg = new Message(Message.MessageType.CHAT_ALL, stateManager.getCurrentUserId(), content);
            roomWaitingPanel.addChatMessage(String.format("[전체] %s: %s", stateManager.getCurrentUserId(), content), new Color(0, 128, 0)); // Dark green
        } else if (message.startsWith("/team ")) {
            if (stateManager.getCurrentGameMode() != Message.GameMode.TWO_VS_TWO) {
                roomWaitingPanel.addChatMessage("팀 채팅은 2v2 모드에서만 가능합니다.", Color.RED);
                return;
            }
            String content = message.substring(6);
            msg = new Message(Message.MessageType.CHAT_TEAM, stateManager.getCurrentUserId(), content);
            // 보낸 메시지는 로컬에서 파란색으로 표시
            roomWaitingPanel.addChatMessage(String.format("[To Team] %s: %s", stateManager.getCurrentUserId(), content), new Color(0, 0, 139)); // Dark blue
        } else {
            msg = new Message(Message.MessageType.CHAT_ROOM, stateManager.getCurrentUserId(), message);
            roomWaitingPanel.addChatMessage(String.format("%s: %s", stateManager.getCurrentUserId(), message), new Color(50, 50, 50)); // Dark gray
        }
        networkManager.sendMessage(msg);
    }

    // ========== RoomWaitingListener Implementation ==========

    @Override
    public void onReadyRequested() {
        Message msg = new Message(Message.MessageType.READY, stateManager.getCurrentUserId());
        msg.setRoomId(stateManager.getCurrentRoomId());
        networkManager.sendMessage(msg);
    }

    @Override
    public void onCancelReadyRequested() {
        Message msg = new Message(Message.MessageType.READY_CANCEL, stateManager.getCurrentUserId());
        msg.setRoomId(stateManager.getCurrentRoomId());
        networkManager.sendMessage(msg);
    }

    @Override
    public void onStartGameRequested() {
        if (!stateManager.isRoomMaster()) {
            UIHelper.showToast(this, "방장만 게임을 시작할 수 있습니다");
            return;
        }
        Message msg = new Message(Message.MessageType.START_GAME_REQUEST, stateManager.getCurrentUserId());
        msg.setRoomId(stateManager.getCurrentRoomId());
        networkManager.sendMessage(msg);
    }

    @Override
    public void onLeaveRoomRequested() {
        Message msg = new Message(Message.MessageType.LEAVE_ROOM, stateManager.getCurrentUserId());
        msg.setRoomId(stateManager.getCurrentRoomId());
        networkManager.sendMessage(msg);

        stateManager.resetRoomState();
        switchToLobbyScreen();
    }

    @Override
    public void onEditRoomRequested() {
        // Create current settings message
        Message currentSettings = new Message(Message.MessageType.CREATE_ROOM_REQUEST, stateManager.getCurrentUserId());
        currentSettings.setRoomName(stateManager.getCurrentRoomName());
        currentSettings.setGameMode(stateManager.getCurrentGameMode());
        currentSettings.setDifficulty(stateManager.getCurrentDifficulty());
        currentSettings.setTurnTimeLimit(stateManager.getCurrentTurnTimeLimit());
        currentSettings.setRoomPassword(stateManager.getCurrentRoomPassword());

        lobbyPanel.showCreateRoomDialog(true, currentSettings);
    }

    // ========== GamePanelListener Implementation ==========

    @Override
    public void onGuessSubmitted(String guess) {
        Message guessMsg = Message.createGuessMessage(stateManager.getCurrentUserId(), guess);
        networkManager.sendMessage(guessMsg);
    }

    @Override
    public void onChatSent(String message) {
        Message msg;
        String displayMessagePrefix = "나: ";
        Color displayColor = new Color(50, 50, 50); // Default: Dark gray

        // Parse commands
        if (message.equals("/help")) {
            gamePanel.displayMessage(" ", Color.BLACK);
            gamePanel.displayMessage(" ", Color.BLACK);
            gamePanel.displayMessage(" ", Color.BLACK);
            gamePanel.displayMessage("/stats [user_id]: [user_id]사용자의 전적 확인하기", new Color(0, 139, 139)); // Dark cyan
            gamePanel.displayMessage("/all: 전체 채팅하기", new Color(0, 139, 139)); // Dark cyan
            gamePanel.displayMessage("/team: 팀 채팅하기", new Color(0, 139, 139)); // Dark cyan
            gamePanel.displayMessage("/w [user_id]: 귓속말 하기", new Color(0, 139, 139)); // Dark cyan
            return;
        } else if (message.startsWith("/w ") || message.startsWith("/whisper ")) {
            String[] parts = message.split(" ", 3);
            if (parts.length < 3) {
                gamePanel.displayMessage("귓속말 사용법: /w [대상ID] [메시지]", new Color(255, 140, 0)); // Dark orange
                return;
            }
            String targetId = parts[1];
            String content = parts[2];
            msg = Message.createChatMessage(Message.MessageType.CHAT_WHISPER, stateManager.getCurrentUserId(), content, targetId);
            displayMessagePrefix = "[To " + targetId + "]: ";
            displayColor = new Color(139, 0, 0); // Dark red
        } else if (message.startsWith("/all ")) {
            String content = message.substring(5);
            msg = new Message(Message.MessageType.CHAT_ALL, stateManager.getCurrentUserId(), content);
            displayMessagePrefix = "[To All]: ";
            displayColor = new Color(0, 128, 0); // Dark green
        }  else if (message.startsWith("/room ")) {
            String content = message.substring(6);
            msg = new Message(Message.MessageType.CHAT_ROOM, stateManager.getCurrentUserId(), content);
            displayMessagePrefix = "[To Room]: ";
            displayColor = new Color(50, 50, 50); // Dark gray
        }  else if (message.startsWith("/team ")) {
            if (stateManager.getCurrentGameMode() != Message.GameMode.TWO_VS_TWO) {
                gamePanel.displayMessage("팀 채팅은 2v2 모드에서만 가능합니다.", Color.RED);
                return;
            }
            String content = message.substring(6);
            msg = new Message(Message.MessageType.CHAT_TEAM, stateManager.getCurrentUserId(), content);
            displayMessagePrefix = "[To Team]: ";
            displayColor = new Color(0, 0, 139); // Dark blue
        } else {
            // Default: room chat if in room/game
            if (currentState == UIState.ROOM_WAITING_SCREEN || currentState == UIState.GAME_SCREEN) {
                msg = new Message(Message.MessageType.CHAT_ROOM, stateManager.getCurrentUserId(), message);
                displayMessagePrefix = "[To Room]: ";
                displayColor = new Color(50, 50, 50); // Dark gray
            } else {
                msg = new Message(Message.MessageType.CHAT_ALL, stateManager.getCurrentUserId(), message);
                displayMessagePrefix = "[To All]: ";
                displayColor = new Color(0, 128, 0); // Dark green
            }
        }

        networkManager.sendMessage(msg);
        gamePanel.displayMessage(displayMessagePrefix + msg.getContent(), displayColor);
    }

    @Override
    public void onDisconnectRequested() {
        networkManager.disconnect();
        System.exit(0);
    }

    // ========== ResultPanelListener Implementation ==========

    @Override
    public void onStayInRoom() {
        switchToRoomWaitingScreen();
    }

    @Override
    public void onLeaveToLobby() {
        onLeaveRoomRequested();
    }

    // ========== Screen Transition Methods ==========

    private void switchToLoginScreen() {
        currentState = UIState.LOGIN_SCREEN;
        SwingUtilities.invokeLater(() -> {
            cardLayout.show(mainPanel, LOGIN_PANEL);
        });
    }

    private void switchToLobbyScreen() {
        currentState = UIState.LOBBY_SCREEN;
        SwingUtilities.invokeLater(() -> {
            lobbyPanel.updateUserInfo();
            cardLayout.show(mainPanel, LOBBY_PANEL);
            onRefreshRequested();

            // Request user list
            Message userListRequest = new Message(Message.MessageType.USER_LIST_REQUEST, stateManager.getCurrentUserId());
            networkManager.sendMessage(userListRequest);
        });
    }

    private void switchToRoomWaitingScreen() {
        currentState = UIState.ROOM_WAITING_SCREEN;
        SwingUtilities.invokeLater(() -> {
            roomWaitingPanel.updateRoomInfo();
            roomWaitingPanel.updatePlayerList(); // 플레이어 목록 업데이트 추가
            cardLayout.show(mainPanel, ROOM_WAITING_PANEL);
        });
    }

    private void switchToGameScreen() {
        currentState = UIState.GAME_SCREEN;
        SwingUtilities.invokeLater(() -> {
            cardLayout.show(mainPanel, GAME_PANEL);
        });
    }

    private void switchToResultScreen() {
        currentState = UIState.RESULT_SCREEN;
        SwingUtilities.invokeLater(() -> {
            cardLayout.show(mainPanel, RESULT_PANEL);
        });
    }

    // ========== Main Method ==========

    public static void main(String[] args) {
        new BaseballClientGUI();
    }
}
