package client.network;

import common.Message;

/**
 * Interface for handling incoming messages from the server
 * Implemented by the main GUI class to process server responses
 */
public interface MessageHandler {
    /**
     * Handle an incoming message from the server
     * @param msg The message received from the server
     */
    void handleMessage(Message msg);
}
