package client.ui;

/**
 * Listener interface for login panel events
 */
public interface LoginListener {
    /**
     * Called when user requests login
     * @param userId User ID entered
     * @param password Password entered
     */
    void onLoginRequested(String userId, String password);

    /**
     * Called when user requests registration
     * @param userId User ID entered
     * @param password Password entered
     * @param nickname Nickname entered
     */
    void onRegisterRequested(String userId, String password, String nickname);

    /**
     * Called when user requests to exit the application
     */
    void onExitRequested();
}
