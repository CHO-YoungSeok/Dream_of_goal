package client.state;

import common.Message;
import javax.swing.Timer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton class for managing all game state
 * Centralizes authentication, room, and game state management
 */
public class GameStateManager {
    private static GameStateManager instance;

    // ========== Authentication & User Session ==========
    private String currentUserId = null;
    private String currentPassword = null;
    private boolean isAuthenticated = false;
    private Message.UserStatus currentUserStatus = Message.UserStatus.OFFLINE;

    // ========== Room State ==========
    private Integer currentRoomId = null;
    private String currentRoomName = null;
    private String roomMasterUserId = null;
    private boolean isRoomMaster = false;
    private Message.GameMode currentGameMode = null;
    private Message.Difficulty currentDifficulty = null;
    private Message.TurnTimeLimit currentTurnTimeLimit = null;
    private String currentRoomPassword = null;

    // Room players tracking
    private List<String> roomPlayersList = new ArrayList<>();
    private Map<String, Boolean> playerReadyStatus = new HashMap<>();

    // Room players team tracking
    private Map<String, Integer> playerTeamMap = new HashMap<>();

    // ========== Game State ==========
    private String currentGameId = null;
    private int digitCount = 3;  // from Difficulty (3, 4, or 5)
    private int turnTimeLimitSeconds = 30;  // from TurnTimeLimit
    private int currentRound = 0;
    private boolean isCurrentlyTopInning = true;  // 초(true) or 말(false)
    private String currentTurnPlayerId = null;
    private String myAnswerKey = null;  // Set when game starts

    private int myTeamNumber = 0;  // 1 or 2 for team mode

    // 2v2 team
    private String teamLeaderId = null;
    private boolean isWaitingForAnswer = false;

    // Turn timer
    private Timer turnTimer = null;
    private int remainingSeconds = 0;

    // Private constructor for singleton
    private GameStateManager() {
    }

    /**
     * Get singleton instance
     */
    public static GameStateManager getInstance() {
        if (instance == null) {
            instance = new GameStateManager();
        }
        return instance;
    }

    // ========== Reset Methods ==========

    /**
     * Reset all state (logout)
     */
    public void resetAllState() {
        resetAuthState();
        resetRoomState();
        resetGameState();
    }

    /**
     * Reset authentication state
     */
    public void resetAuthState() {
        currentUserId = null;
        currentPassword = null;
        isAuthenticated = false;
        currentUserStatus = Message.UserStatus.OFFLINE;
    }

    /**
     * Reset room state (when leaving room)
     */
    public void resetRoomState() {
        currentRoomId = null;
        currentRoomName = null;
        roomMasterUserId = null;
        isRoomMaster = false;
        currentGameMode = null;
        currentDifficulty = null;
        currentTurnTimeLimit = null;
        currentRoomPassword = null;
        roomPlayersList.clear();
        playerReadyStatus.clear();
        playerTeamMap.clear();
        resetGameState();
    }

    /**
     * Reset game state (when game ends)
     */
    public void resetGameState() {
        currentGameId = null;
        digitCount = 3;
        turnTimeLimitSeconds = 30;
        currentRound = 0;
        isCurrentlyTopInning = true;
        currentTurnPlayerId = null;
        myAnswerKey = null;
        myTeamNumber = 0;
        teamLeaderId  = null;
        isWaitingForAnswer = false;
        remainingSeconds = 0;
        if (turnTimer != null && turnTimer.isRunning()) {
            turnTimer.stop();
        }
    }

    // ========== State Check Methods ==========

    public boolean isInRoom() {
        return currentRoomId != null;
    }

    public boolean isInGame() {
        return currentGameId != null;
    }

    // ========== Authentication Getters/Setters ==========

    public String getCurrentUserId() {
        return currentUserId;
    }

    public void setCurrentUserId(String currentUserId) {
        this.currentUserId = currentUserId;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    public void setAuthenticated(boolean authenticated, String userId, String password) {
        this.isAuthenticated = authenticated;
        this.currentUserId = userId;
        this.currentPassword = password;
        if (authenticated) {
            this.currentUserStatus = Message.UserStatus.ONLINE;
        }
    }

    public Message.UserStatus getCurrentUserStatus() {
        return currentUserStatus;
    }

    public void setCurrentUserStatus(Message.UserStatus currentUserStatus) {
        this.currentUserStatus = currentUserStatus;
    }

    // ========== Room Getters/Setters ==========

    public Integer getCurrentRoomId() {
        return currentRoomId;
    }

    public void setCurrentRoomId(Integer currentRoomId) {
        this.currentRoomId = currentRoomId;
    }

    public String getCurrentRoomName() {
        return currentRoomName;
    }

    public void setCurrentRoomName(String currentRoomName) {
        this.currentRoomName = currentRoomName;
    }

    public String getRoomMasterUserId() {
        return roomMasterUserId;
    }

    public void setRoomMasterUserId(String roomMasterUserId) {
        this.roomMasterUserId = roomMasterUserId;
        this.isRoomMaster = (currentUserId != null && currentUserId.equals(roomMasterUserId));
    }

    public boolean isRoomMaster() {
        return isRoomMaster;
    }

    public Message.GameMode getCurrentGameMode() {
        return currentGameMode;
    }

    public void setCurrentGameMode(Message.GameMode currentGameMode) {
        this.currentGameMode = currentGameMode;
    }

    public Message.Difficulty getCurrentDifficulty() {
        return currentDifficulty;
    }

    public void setCurrentDifficulty(Message.Difficulty currentDifficulty) {
        this.currentDifficulty = currentDifficulty;
        if (currentDifficulty != null) {
            this.digitCount = currentDifficulty.getDigitCount();
        }
    }

    public Message.TurnTimeLimit getCurrentTurnTimeLimit() {
        return currentTurnTimeLimit;
    }

    public void setCurrentTurnTimeLimit(Message.TurnTimeLimit currentTurnTimeLimit) {
        this.currentTurnTimeLimit = currentTurnTimeLimit;
        if (currentTurnTimeLimit != null) {
            this.turnTimeLimitSeconds = currentTurnTimeLimit.getSeconds();
        }
    }

    public String getCurrentRoomPassword() {
        return currentRoomPassword;
    }

    public void setCurrentRoomPassword(String currentRoomPassword) {
        this.currentRoomPassword = currentRoomPassword;
    }

    public List<String> getRoomPlayersList() {
        return roomPlayersList;
    }

    public void setRoomPlayersList(List<String> roomPlayersList) {
        this.roomPlayersList = roomPlayersList;
    }

    public Map<String, Boolean> getPlayerReadyStatus() {
        return playerReadyStatus;
    }

    public void setPlayerReadyStatus(Map<String, Boolean> playerReadyStatus) {
        this.playerReadyStatus = playerReadyStatus;
    }

    public Map<String, Integer> getPlayerTeamMap() {
        return playerTeamMap;
    }

    public void setPlayerTeamMap(Map<String, Integer> playerTeamMap) {
        this.playerTeamMap = playerTeamMap;
    }

    // ========== Game Getters/Setters ==========

    public String getCurrentGameId() {
        return currentGameId;
    }

    public void setCurrentGameId(String currentGameId) {
        this.currentGameId = currentGameId;
    }

    public int getDigitCount() {
        return digitCount;
    }

    public void setDigitCount(int digitCount) {
        this.digitCount = digitCount;
    }

    public int getTurnTimeLimitSeconds() {
        return turnTimeLimitSeconds;
    }

    public void setTurnTimeLimitSeconds(int turnTimeLimitSeconds) {
        this.turnTimeLimitSeconds = turnTimeLimitSeconds;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }

    public boolean isCurrentlyTopInning() {
        return isCurrentlyTopInning;
    }

    public void setCurrentlyTopInning(boolean currentlyTopInning) {
        isCurrentlyTopInning = currentlyTopInning;
    }

    public String getCurrentTurnPlayerId() {
        return currentTurnPlayerId;
    }

    public void setCurrentTurnPlayerId(String currentTurnPlayerId) {
        this.currentTurnPlayerId = currentTurnPlayerId;
    }

    public String getMyAnswerKey() {
        return myAnswerKey;
    }

    public void setMyAnswerKey(String myAnswerKey) {
        this.myAnswerKey = myAnswerKey;
    }

    public int getMyTeamNumber() {
        return myTeamNumber;
    }

    public void setMyTeamNumber(int myTeamNumber) {
        this.myTeamNumber = myTeamNumber;
    }

    public String getTeamLeaderId() { return teamLeaderId; }

    public void setTeamLeaderId(String teamLeaderId) { this.teamLeaderId = teamLeaderId; }

    public boolean isWaitingForAnswer() { return isWaitingForAnswer; }

    public void setWaitingForAnswer(boolean waitingForAnswer) { this.isWaitingForAnswer = waitingForAnswer; }

    public Timer getTurnTimer() {
        return turnTimer;
    }

    public void setTurnTimer(Timer turnTimer) {
        this.turnTimer = turnTimer;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(int remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }
}
