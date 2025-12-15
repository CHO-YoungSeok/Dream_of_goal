package client;

import common.Message;
import client.network.MessageHandler;
import client.network.NetworkManager;
import client.state.GameStateManager;
import client.ui.*;
import client.util.UIHelper;

import javax.swing.*;
import java.awt.*;

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
                gamePanel.displayMessage(msg.toString(), Color.RED);
                break;
            case CHAT_TEAM:
                gamePanel.displayMessage(msg.toString(), Color.BLUE);
                break;
            case CHAT_ROOM:
            case CHAT_WHISPER:
                gamePanel.displayMessage(msg.toString(), Color.WHITE);
                break;
            case ERROR:
                handleError(msg);
                break;
            default:
                System.out.println("Unhandled message type: " + msg.getType());
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
        if (msg.getData() instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            java.util.List<Message> roomList = (java.util.List<Message>) msg.getData();
            lobbyPanel.updateRoomList(roomList);
        }
    }

    private void handleCreateRoomResponse(Message msg) {
        if (msg.isSuccess()) {
            stateManager.setCurrentRoomId(msg.getRoomId());
            stateManager.setCurrentRoomName(msg.getRoomName());
            stateManager.setRoomMasterUserId(msg.getRoomMaster());
            stateManager.setCurrentGameMode(msg.getGameMode());
            stateManager.setCurrentDifficulty(msg.getDifficulty());
            stateManager.setCurrentTurnTimeLimit(msg.getTurnTimeLimit());
            stateManager.setCurrentRoomIsPrivate(msg.isPrivate());
            stateManager.setCurrentRoomPassword(msg.getRoomPassword());
            stateManager.setCurrentRoomAllowSpectators(msg.isAllowSpectators());

            // 플레이어 리스트 처리
            if (msg.getData() instanceof java.util.HashMap) {
                @SuppressWarnings("unchecked")
                java.util.HashMap<String, Object> roomData = (java.util.HashMap<String, Object>) msg.getData();
                @SuppressWarnings("unchecked")
                java.util.List<String> players = (java.util.List<String>) roomData.get("players");
                @SuppressWarnings("unchecked")
                java.util.Map<String, Boolean> readyStatus = (java.util.Map<String, Boolean>) roomData.get("readyStatus");

                stateManager.setRoomPlayersList(players);
                stateManager.setPlayerReadyStatus(readyStatus);
            }

            switchToRoomWaitingScreen();
        } else {
            JOptionPane.showMessageDialog(this,
                "Failed to create room: " + msg.getErrorMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleJoinRoomResponse(Message msg) {
        if (msg.isSuccess()) {
            stateManager.setCurrentRoomId(msg.getRoomId());
            stateManager.setCurrentRoomName(msg.getRoomName());
            stateManager.setRoomMasterUserId(msg.getRoomMaster());
            stateManager.setCurrentGameMode(msg.getGameMode());
            stateManager.setCurrentDifficulty(msg.getDifficulty());
            stateManager.setCurrentTurnTimeLimit(msg.getTurnTimeLimit());
            stateManager.setCurrentRoomIsPrivate(msg.isPrivate());
            stateManager.setCurrentRoomPassword(msg.getRoomPassword());
            stateManager.setCurrentRoomAllowSpectators(msg.isAllowSpectators());

            // 플레이어 리스트 처리
            if (msg.getData() instanceof java.util.HashMap) {
                @SuppressWarnings("unchecked")
                java.util.HashMap<String, Object> roomData = (java.util.HashMap<String, Object>) msg.getData();
                @SuppressWarnings("unchecked")
                java.util.List<String> players = (java.util.List<String>) roomData.get("players");
                @SuppressWarnings("unchecked")
                java.util.Map<String, Boolean> readyStatus = (java.util.Map<String, Boolean>) roomData.get("readyStatus");

                stateManager.setRoomPlayersList(players);
                stateManager.setPlayerReadyStatus(readyStatus);
            }

            switchToRoomWaitingScreen();
        } else {
            JOptionPane.showMessageDialog(this,
                "Failed to join room: " + msg.getErrorMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleEditRoomResponse(Message msg) {
        if (msg.isSuccess()) {
            // 방 정보 업데이트
            stateManager.setCurrentRoomId(msg.getRoomId());
            stateManager.setCurrentRoomName(msg.getRoomName());
            stateManager.setRoomMasterUserId(msg.getRoomMaster());
            stateManager.setCurrentDifficulty(msg.getDifficulty());
            stateManager.setCurrentTurnTimeLimit(msg.getTurnTimeLimit());
            stateManager.setCurrentRoomIsPrivate(msg.isPrivate());
            stateManager.setCurrentRoomPassword(msg.getRoomPassword());

            // 플레이어 리스트 처리
            if (msg.getData() instanceof java.util.HashMap) {
                @SuppressWarnings("unchecked")
                java.util.HashMap<String, Object> roomData = (java.util.HashMap<String, Object>) msg.getData();
                @SuppressWarnings("unchecked")
                java.util.List<String> players = (java.util.List<String>) roomData.get("players");
                @SuppressWarnings("unchecked")
                java.util.Map<String, Boolean> readyStatus = (java.util.Map<String, Boolean>) roomData.get("readyStatus");

                stateManager.setRoomPlayersList(players);
                stateManager.setPlayerReadyStatus(readyStatus);
            }

            roomWaitingPanel.updateRoomInfo();
            roomWaitingPanel.updatePlayerList();
            UIHelper.showToast(this, "방 정보가 변경되었습니다.");
        } else {
            JOptionPane.showMessageDialog(this,
                "Failed to edit room: " + msg.getErrorMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleRoomInfoUpdate(Message msg) {
        if (msg.getData() instanceof java.util.HashMap) {
            @SuppressWarnings("unchecked")
            java.util.HashMap<String, Object> roomData = (java.util.HashMap<String, Object>) msg.getData();
            @SuppressWarnings("unchecked")
            java.util.List<String> players = (java.util.List<String>) roomData.get("players");
            @SuppressWarnings("unchecked")
            java.util.Map<String, Boolean> readyStatus = (java.util.Map<String, Boolean>) roomData.get("readyStatus");

            stateManager.setRoomPlayersList(players);
            stateManager.setPlayerReadyStatus(readyStatus);
            stateManager.setRoomMasterUserId(msg.getRoomMaster());

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

        gamePanel.displayMessage("게임 시작! " + msg.getContent());
        gamePanel.setupForGame(stateManager.getDigitCount());
        switchToGameScreen();

        // Prompt for answer key
        String myAnswerKey = UIHelper.promptForAnswerKey(this, stateManager.getDigitCount());
        stateManager.setMyAnswerKey(myAnswerKey);
        gamePanel.displayMessage("정답이 설정되었습니다: " + myAnswerKey);

        // Send answer to server
        Message answerMsg = Message.createGuessMessage(stateManager.getCurrentUserId(), myAnswerKey);
        networkManager.sendMessage(answerMsg);
    }

    private void handleTurnInfo(Message msg) {
        stateManager.setCurrentRound(msg.getRound());
        stateManager.setCurrentlyTopInning(msg.isTop());
        stateManager.setCurrentTurnPlayerId(msg.getCurrentTurnPlayer());

        String roundInfo = msg.getRoundInfo();
        boolean isMyTurn = stateManager.getCurrentUserId().equals(msg.getCurrentTurnPlayer());
        String turnInfo = isMyTurn ? "Your Turn" : "Waiting for " + msg.getCurrentTurnPlayer();

        gamePanel.updateTurnInfo(roundInfo, turnInfo, isMyTurn);

        // Start turn timer
        stateManager.setRemainingSeconds(stateManager.getTurnTimeLimitSeconds());
        gamePanel.updateTimer(stateManager.getRemainingSeconds());

        if (stateManager.getTurnTimer() != null && stateManager.getTurnTimer().isRunning()) {
            stateManager.getTurnTimer().stop();
        }

        Timer turnTimer = new Timer(1000, e -> {
            int remaining = stateManager.getRemainingSeconds() - 1;
            stateManager.setRemainingSeconds(remaining);
            if (remaining >= 0) {
                gamePanel.updateTimer(remaining);
            } else {
                stateManager.getTurnTimer().stop();
            }
        });
        stateManager.setTurnTimer(turnTimer);
        turnTimer.start();
    }

    private void handleGuessResult(Message msg) {
        gamePanel.displayMessage(msg.toString());
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
                                      boolean isPrivate, String password, boolean allowSpectators) {
        Message msg = Message.createCreateRoomRequest(
            stateManager.getCurrentUserId(),
            roomName,
            gameMode,
            difficulty,
            turnTimeLimit,
            isPrivate,
            password,
            allowSpectators
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
                                    boolean isPrivate, String password) {
        Message msg = Message.createEditRoomRequest(
            stateManager.getCurrentUserId(),
            stateManager.getCurrentRoomId(),
            roomName,
            difficulty,
            turnTimeLimit,
            isPrivate,
            password
        );
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
        currentSettings.setPrivate(stateManager.isCurrentRoomIsPrivate());
        currentSettings.setRoomPassword(stateManager.getCurrentRoomPassword());
        currentSettings.setAllowSpectators(stateManager.isCurrentRoomAllowSpectators());

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

        // Parse commands
        if (message.startsWith("/w ") || message.startsWith("/whisper ")) {
            String[] parts = message.split(" ", 3);
            if (parts.length < 3) {
                gamePanel.displayMessage("귓속말 사용법: /w [대상ID] [메시지]", Color.ORANGE);
                return;
            }
            String targetId = parts[1];
            String content = parts[2];
            msg = Message.createChatMessage(Message.MessageType.CHAT_WHISPER, stateManager.getCurrentUserId(), content, targetId);
            displayMessagePrefix = "[To " + targetId + "]: ";
        } else if (message.startsWith("/all ")) {
            String content = message.substring(5);
            msg = new Message(Message.MessageType.CHAT_ALL, stateManager.getCurrentUserId(), content);
            displayMessagePrefix = "[To All]: ";
        } else if (message.startsWith("/team ")) {
            String content = message.substring(6);
            msg = new Message(Message.MessageType.CHAT_TEAM, stateManager.getCurrentUserId(), content);
            displayMessagePrefix = "[To Team]: ";
        } else if (message.startsWith("/room ")) {
            String content = message.substring(6);
            msg = new Message(Message.MessageType.CHAT_ROOM, stateManager.getCurrentUserId(), content);
            displayMessagePrefix = "[To Room]: ";
        } else {
            // Default: room chat if in room/game
            if (currentState == UIState.ROOM_WAITING_SCREEN || currentState == UIState.GAME_SCREEN) {
                msg = new Message(Message.MessageType.CHAT_ROOM, stateManager.getCurrentUserId(), message);
                displayMessagePrefix = "[To Room]: ";
            } else {
                msg = new Message(Message.MessageType.CHAT_ALL, stateManager.getCurrentUserId(), message);
                displayMessagePrefix = "[To All]: ";
            }
        }

        networkManager.sendMessage(msg);
        gamePanel.displayMessage(displayMessagePrefix + msg.getContent(), Color.YELLOW);
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
            cardLayout.show(mainPanel, LOBBY_PANEL);
            onRefreshRequested();
        });
    }

    private void switchToRoomWaitingScreen() {
        currentState = UIState.ROOM_WAITING_SCREEN;
        SwingUtilities.invokeLater(() -> {
            roomWaitingPanel.updateRoomInfo();
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
