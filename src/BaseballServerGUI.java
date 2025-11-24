import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BaseballServerGUI extends JFrame {
    int port = 54321;
    Thread serverThread;
    ServerSocket serverSocket;

    JTextArea t_display;
    JButton b_serverStart, b_serverClose, b_exit;

    // 방 관리
    Map<ClientHandler, String> roomMap = new HashMap<>();

    // Client Handler
    class ClientHandler implements Runnable {
        static private final Set<ClientHandler> clientHandlers = new HashSet<>();

        private String uid;
        private Socket clientSocket;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private volatile boolean running = true;
        private String answerKey;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
            clientHandlers.add(this);
            try {
                out = new ObjectOutputStream(clientSocket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(clientSocket.getInputStream());
            } catch (IOException e) {
                displayMessage("Error creating I/O streams: " + e.getMessage());
                running = false;
            }
        }

        @Override
        public void run() {
            // 새로 들어올 때 아이디 출력.
            try {
                Message connectMsg = (Message) in.readObject();
                uid = connectMsg.getUserId();
                displayMessage(connectMsg.toString() + ", answerKEY: " + connectMsg.getContent());
                broadcasting(connectMsg);
                this.answerKey = connectMsg.getContent(); // connectMsg에는 answerKey가 들어있다.
                AnswerKeyMap.put(this, answerKey);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            receiveMessage();
        }

        void receiveMessage() {
            Message message;
            try {
                while (running && (message = (Message) in.readObject()) != null) {
                    String answerKey = message.getContent().toString();
                    StringBuilder sb = new StringBuilder(message.toString());
                    if (checkAnswerKey(answerKey)) {
                        sb.append("Correct answer: " + answerKey);
                        sb.append(message.getUserId() + " WIN!!!");
                    } else {
                        sb.append("Wrong answer: " + answerKey);
                    }
                    displayMessage(sb.toString());
                    broadcasting(message);
                }
            } catch (IOException e) {
                displayMessage("Client disconnected unexpectedly: " + clientSocket.getInetAddress());
            } catch (ClassNotFoundException e) {
                displayMessage("ClassNotFoundException: " + e.getMessage());
            } finally {
                closeClientConnection();
            }
        }

        private void broadcasting(Message message) {
            for (ClientHandler ch : clientHandlers) {
                if (ch == this) continue;
                ch.sendMessage(message);
            }
        }

        private void sendMessage(Message message) {
            try {
                out.writeObject(message);
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
            Message disconnectMsg = new Message(Message.MessageType.DISCONNECT, uid, clientSocket.getInetAddress().toString());
            displayMessage(disconnectMsg.toString());
            broadcasting(disconnectMsg);
        }

        public Boolean checkAnswerKey(String answer) {
            for (Map.Entry<ClientHandler, String> entry : AnswerKeyMap.entrySet()) {
                if (this == entry.getKey()) continue;
                return answer.equals(entry.getValue());
            }

            System.out.println("ERR: checkAnswerKey");
            return false;
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

