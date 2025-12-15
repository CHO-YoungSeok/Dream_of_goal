package client.ui;

/**
 * Listener interface for result panel events
 */
public interface ResultPanelListener {
    /**
     * Called when user wants to stay in the room after game
     */
    void onStayInRoom();

    /**
     * Called when user wants to leave to lobby after game
     */
    void onLeaveToLobby();
}
