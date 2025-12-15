package client.ui;

/**
 * Listener interface for room waiting panel events
 */
public interface RoomWaitingListener {
    /**
     * Called when user wants to mark as ready
     */
    void onReadyRequested();

    /**
     * Called when user wants to cancel ready status
     */
    void onCancelReadyRequested();

    /**
     * Called when user (room master) wants to start the game
     */
    void onStartGameRequested();

    /**
     * Called when user wants to leave the room
     */
    void onLeaveRoomRequested();

    /**
     * Called when user (room master) wants to edit room settings
     */
    void onEditRoomRequested();

    /**
     * Called when a chat message is sent from the room
     * @param message The chat message
     */
    void onRoomChatSent(String message);
}
