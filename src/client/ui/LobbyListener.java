package client.ui;

import common.Message;

/**
 * Listener interface for lobby panel events
 */
public interface LobbyListener {
    /**
     * Called when user wants to create a new room
     * @param roomName Room name
     * @param gameMode Game mode
     * @param difficulty Difficulty level
     * @param turnTimeLimit Turn time limit
     * @param password Room password
     */
    void onCreateRoomRequested(String roomName, Message.GameMode gameMode,
                                Message.Difficulty difficulty, Message.TurnTimeLimit turnTimeLimit,
                                String password);

    /**
     * Called when user wants to join a room
     * @param roomId Room ID
     * @param password Password (if needed)
     */
    void onJoinRoomRequested(int roomId, String password);

    /**
     * Called when user wants to refresh room list
     */
    void onRefreshRequested();

    /**
     * Called when user confirms editing room settings
     * @param roomName New room name
     * @param difficulty New difficulty level
     * @param turnTimeLimit New turn time limit
     * @param password Room password
     */
    void onEditRoomConfirmed(String roomName, Message.Difficulty difficulty,
                            Message.TurnTimeLimit turnTimeLimit,
                            String password);

    /**
     * Called when user sends a lobby chat message
     * @param message Chat message content
     */
    void onLobbyChatSent(String message);
}
