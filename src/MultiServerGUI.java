import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

public class MultiServerGUI extends JFrame {
    int port = 54321;
    Thread serverThread;
    JTextArea t_display;
    ServerSocket serverSocket;
    JButton b_serverStart;
    JButton b_serverClose;
    JButton b_exit;

    class ClientHandler implements Runnable {
        static private final Set<ClientHandler> clientHandlers = new HashSet<>();

        private String uid;
        private Socket clientSocket;
        private BufferedReader in;
        private BufferedWriter out;
        private volatile boolean running = true;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
            clientHandlers.add(this);
            try {
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            } catch (IOException e) {
                displayMessage("Error creating I/O streams: " + e.getMessage());
                running = false;
            }
        }

        @Override
        public void run() {
            // 새로 들어올 때 아이디 출력.
            try {
                uid = in.readLine();
                displayMessage("newClient is connected : " + uid);
                broadcasting("newClient is connected: " + uid);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            receiveMessage();
        }

        void receiveMessage() {
            String message;
            try {
                while (running && (message = in.readLine()) != null) {
                    displayMessage(uid +": " + message);
                    broadcasting(uid +": " + message);
                }
            } catch (IOException e) {
                displayMessage("Client disconnected unexpectedly: " + clientSocket.getInetAddress());
            } finally {
                closeClientConnection();
            }
        }

        private void broadcasting(String message) {
            for (ClientHandler ch : clientHandlers) {
                if (ch == this) continue;
                ch.sendMessage(message);
            }
        }

        private void sendMessage(String message) {
            try {
                out.write(message + "\n");
                out.flush();
            } catch (IOException e) {
                displayMessage("Error sending message to client: " + e.getMessage());
                closeClientConnection();
            }
        }

        public void closeClientConnection() {
            running = false;
            clientHandlers.remove(this);
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
            } catch (IOException e) {
                displayMessage("Error closing client socket: " + e.getMessage());
            }
            displayMessage(uid + " is disconnected " + clientSocket.getInetAddress());
            broadcasting(uid + " is disconnected " + clientSocket.getInetAddress());
        }
    }

    class StartServer implements Runnable {

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(port);
                displayMessage("Server started on port " + port);
                while (!Thread.currentThread().isInterrupted() && !serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    displayMessage("Client connected: " + clientSocket.getInetAddress());
                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    new Thread(clientHandler).start();
                }
            } catch (IOException e) {
                if (serverSocket != null && serverSocket.isClosed()) {
                    // 정상 종료
                } else {
                    displayMessage("Server error: " + e.getMessage());
                }
            } finally {
                disconnect();
            }
        }
    }

    void displayMessage(String message) {
        t_display.append(message + "\n");
        t_display.setCaretPosition(t_display.getDocument().getLength());
    }

    void buildGUI() {
        setLayout(new BorderLayout());
        add(createDisplayPanel(), BorderLayout.CENTER);
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new GridLayout(0, 1));
        bottomPanel.add(createControlPanel());
        add(bottomPanel, BorderLayout.SOUTH);
    }

    JPanel createControlPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(1, 3));
        b_serverStart = new JButton("Server Start");
        b_serverClose = new JButton("Server Disconnect");
        b_exit = new JButton("Exit");

        b_serverStart.setEnabled(true);
        b_serverClose.setEnabled(false);
        b_exit.setEnabled(true);

        b_serverStart.addActionListener(e -> {
            serverThread = new Thread(new StartServer(), "serverThread");
            serverThread.start();
            b_serverStart.setEnabled(false);
            b_serverClose.setEnabled(true);
            b_exit.setEnabled(true);
        });

        b_serverClose.addActionListener(e -> {
            disconnect();
            b_serverStart.setEnabled(true);
            b_serverClose.setEnabled(false);
            b_exit.setEnabled(true);
        });

        b_exit.addActionListener(e -> {
            disconnect();
            System.exit(0);
        });

        panel.add(b_serverStart);
        panel.add(b_serverClose);
        panel.add(b_exit);
        return panel;
    }

    private void disconnect() {
        try {
            if (serverThread != null && serverThread.isAlive()) {
                serverThread.interrupt();
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            for (ClientHandler ch : ClientHandler.clientHandlers) {
                ch.closeClientConnection();
            }
            ClientHandler.clientHandlers.clear();
            displayMessage("Server Disconnected");
        } catch (IOException e) {
            displayMessage("Error during server disconnect: " + e.getMessage());
        }
    }

    JPanel createDisplayPanel() {
        JPanel displayPanel = new JPanel();
        displayPanel.setLayout(new BorderLayout());
        t_display = new JTextArea(10, 20);
        t_display.setEditable(false);
        displayPanel.add(new JScrollPane(t_display), BorderLayout.CENTER);
        return displayPanel;
    }

    public MultiServerGUI() {
        super("Multi ServerGUI");
        buildGUI();
        setBounds(800, 100, 450, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }

    public static void main(String[] args) {
        new MultiServerGUI();
    }
}

