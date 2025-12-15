package client.network;

import common.Message;
import javax.swing.*;
import java.io.*;
import java.net.Socket;

/**
 * Manages network communication with the server
 * Handles socket connection, message sending/receiving
 */
public class NetworkManager {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Thread receiveThread;
    private MessageHandler messageHandler;

    // Connection info loaded from CONN_INFO.txt
    private String serverAddress;
    private int serverPort;

    /**
     * Constructor
     * @param messageHandler Handler for incoming messages
     */
    public NetworkManager(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
        loadConnectionInfo();
    }

    /**
     * Load connection info from CONN_INFO.txt
     */
    private void loadConnectionInfo() {
        try (FileInputStream fis = new FileInputStream("CONN_INFO.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis))) {
            serverAddress = reader.readLine().trim();
            serverPort = Integer.parseInt(reader.readLine().trim());
        } catch (IOException e) {
            System.err.println("Failed to load connection info: " + e.getMessage());
            System.err.println("Using defaults: localhost:54321");
            serverAddress = "localhost";
            serverPort = 54321;
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number in CONN_INFO.txt");
            System.err.println("Using defaults: localhost:54321");
            serverAddress = "localhost";
            serverPort = 54321;
        }
    }

    /**
     * Connect to the server
     * @return true if connection successful
     */
    public boolean connect() {
        try {
            socket = new Socket(serverAddress, serverPort);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            // Start receive thread
            receiveThread = new Thread(() -> {
                receiveMessage();
            }, "receiveThread");
            receiveThread.start();

            System.out.println("Connected to server at " + serverAddress + ":" + serverPort);
            return true;

        } catch (IOException ex) {
            System.err.println("Connection failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(null,
                "Connection failed: " + ex.getMessage(),
                "Connection Error",
                JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * Disconnect from the server
     */
    public void disconnect() {
        try {
            // Send logout message to server
            if (out != null && socket != null && socket.isConnected()) {
                // MessageHandler will handle sending logout message if needed
            }

            // Close streams
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();

        } catch (IOException ex) {
            System.err.println("Error during disconnect: " + ex.getMessage());
        } finally {
            out = null;
            in = null;
            socket = null;
        }
    }

    /**
     * Send a message to the server
     * @param msg Message to send
     */
    public void sendMessage(Message msg) {
        try {
            if (out != null) {
                out.writeObject(msg);
                out.flush();
            }
        } catch (IOException e) {
            System.err.println("Failed to send message: " + e.getMessage());
        }
    }

    /**
     * Check if connected to server
     * @return true if connected
     */
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * Receive messages from server (runs in separate thread)
     */
    private void receiveMessage() {
        Message message;
        try {
            while ((message = (Message) in.readObject()) != null) {
                final Message msg = message;
                // Handle different message types
                SwingUtilities.invokeLater(() -> {
                    messageHandler.handleMessage(msg);
                });
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
            SwingUtilities.invokeLater(() -> {
                // Notify handler about connection loss
                Message errorMsg = Message.createErrorMessage(
                    Message.ErrorCode.UNKNOWN_ERROR,
                    "Connection lost to server"
                );
                messageHandler.handleMessage(errorMsg);
            });
        } catch (ClassNotFoundException e) {
            System.out.println("ClassNotFoundException: " + e.getMessage());
        }
    }

    /**
     * Get server address
     * @return Server address
     */
    public String getServerAddress() {
        return serverAddress;
    }

    /**
     * Get server port
     * @return Server port
     */
    public int getServerPort() {
        return serverPort;
    }
}
