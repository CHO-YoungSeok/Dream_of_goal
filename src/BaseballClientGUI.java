import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class BaseballClientGUI extends JFrame {

    // UI State Enum for screen management
    private enum UIState {
        WELCOME_SCREEN,    // Initial login screen
        WAITING_SCREEN,    // Waiting for second player
        GAME_SCREEN        // Active game screen
    }

    // Screen management
    private UIState currentState = UIState.WELCOME_SCREEN;
    private CardLayout cardLayout;
    private JPanel mainPanel;

    // Screen constants
    private static final String WELCOME_PANEL = "WELCOME";
    private static final String WAITING_PANEL = "WAITING";
    private static final String GAME_PANEL = "GAME";

    JTextArea t_display;
    JTextField t_input;
    JButton b_connect;
    JButton b_send;
    JButton b_exit;
    JButton b_submit;
    Socket socket;
    ObjectOutputStream out;
    ObjectInputStream in;
    JTextField t_userID;
    JTextField t_answerKey;

    // Connection info loaded from CONN_INFO.txt
    private String serverAddress;
    private int serverPort;
    JLabel[] selectedNumbers;

    int currentPosition = 0;
    JButton b_backSpace;

    void buildGUI() {
        setLayout(new BorderLayout());

        // Create CardLayout container for switching screens
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        // Create all three screens
        mainPanel.add(createWelcomePanel(), WELCOME_PANEL);
        mainPanel.add(createWaitingPanel(), WAITING_PANEL);
        mainPanel.add(createGamePanel(), GAME_PANEL);

        add(mainPanel, BorderLayout.CENTER);

        // Show welcome screen initially
        cardLayout.show(mainPanel, WELCOME_PANEL);
    }

    private JPanel createWelcomePanel() {
        JPanel welcomePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                ImageIcon background = new ImageIcon("src/image/intro.jpg");
                Image img = background.getImage();
                g.drawImage(img, 0, 0, getWidth(), getHeight(), this);
            }
        };
        welcomePanel.setLayout(new BorderLayout());

        // Exit button in top-left
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setOpaque(false);
        b_exit = new JButton("Exit");
        b_exit.addActionListener(e -> System.exit(0));
        topPanel.add(b_exit);
        welcomePanel.add(topPanel, BorderLayout.NORTH);

        // Center panel for login components
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);
        centerPanel.add(Box.createVerticalGlue());

        // Title
        JLabel titleLabel = new JLabel("Baseball Game");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 48));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(titleLabel);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 40)));

        // UserID input panel (centered)
        JPanel userIDPanel = new JPanel();
        userIDPanel.setOpaque(false);
        userIDPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10));

        JLabel l_userID = new JLabel("User ID:");
        l_userID.setFont(new Font("Arial", Font.BOLD, 20));
        l_userID.setForeground(Color.WHITE);

        try {
            InetAddress local = InetAddress.getLocalHost();
            String addr = local.getHostAddress();
            String[] part = addr.split("\\.");
            t_userID = new JTextField("guest" + part[3], 15);
        } catch (UnknownHostException e) {
            t_userID = new JTextField("guest", 15);
        }
        t_userID.setFont(new Font("Arial", Font.PLAIN, 18));

        userIDPanel.add(l_userID);
        userIDPanel.add(t_userID);
        centerPanel.add(userIDPanel);

        // Answer Key configuration panel - closer spacing and centered
        centerPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        JLabel l_answerKey = new JLabel("Your Answer Key:");
        l_answerKey.setFont(new Font("Arial", Font.BOLD, 24));
        l_answerKey.setForeground(Color.WHITE);
        l_answerKey.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(l_answerKey);

        centerPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Instruction message
        JLabel l_instruction = new JLabel("중복 없이 3개의 숫자만을 입력하세요.");
        l_instruction.setFont(new Font("Arial", Font.PLAIN, 14));
        l_instruction.setForeground(Color.YELLOW);
        l_instruction.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(l_instruction);

        centerPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        inputPanel.setOpaque(false);
        t_answerKey = new JTextField("", 10);
        t_answerKey.setFont(new Font("Arial", Font.PLAIN, 18));
        t_answerKey.setHorizontalAlignment(JTextField.CENTER);
        inputPanel.add(t_answerKey);
        centerPanel.add(inputPanel);

        centerPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        // Connect button
        JPanel connectPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        connectPanel.setOpaque(false);
        b_connect = new JButton("Connect");
        b_connect.setFont(new Font("Arial", Font.BOLD, 24));
        b_connect.setPreferredSize(new Dimension(200, 60));
        b_connect.addActionListener(e -> {
            String userId = t_userID.getText().trim();
            if (userId.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "Please enter a user ID",
                    "Input Required",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Validate answer key
            String answerKey = t_answerKey.getText().trim();
            if (!isValidAnswerKey(answerKey)) {
                showToast("중복 없이 3개의 숫자만을 입력하세요.");
                return;
            }

            connectToServer();
        });
        connectPanel.add(b_connect);
        centerPanel.add(connectPanel);

        centerPanel.add(Box.createVerticalGlue());
        welcomePanel.add(centerPanel, BorderLayout.CENTER);

        return welcomePanel;
    }

    private JPanel createWaitingPanel() {
        JPanel waitingPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                ImageIcon background = new ImageIcon("src/image/intro.jpg");
                Image img = background.getImage();
                g.drawImage(img, 0, 0, getWidth(), getHeight(), this);
            }
        };
        waitingPanel.setLayout(new BorderLayout());

        // Exit button in top-left
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setOpaque(false);
        JButton exitButton = new JButton("Exit");
        exitButton.addActionListener(e -> {
            disconnect();
            switchToWelcomeScreen();
        });
        topPanel.add(exitButton);
        waitingPanel.add(topPanel, BorderLayout.NORTH);

        // Center panel for waiting message
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);
        centerPanel.add(Box.createVerticalGlue());

        // Waiting message
        JLabel waitingLabel = new JLabel("다른 플레이어 기다리는 중...");
        waitingLabel.setFont(new Font("Arial", Font.BOLD, 32));
        waitingLabel.setForeground(Color.WHITE);
        waitingLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(waitingLabel);

        centerPanel.add(Box.createRigidArea(new Dimension(0, 30)));

        // Status label
        JLabel statusLabel = new JLabel("Connected as: " + (t_userID != null ? t_userID.getText() : ""));
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 18));
        statusLabel.setForeground(Color.LIGHT_GRAY);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(statusLabel);

        // Animated dots
        JLabel animationLabel = new JLabel("●  ○  ○");
        animationLabel.setFont(new Font("Arial", Font.BOLD, 24));
        animationLabel.setForeground(Color.YELLOW);
        animationLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        centerPanel.add(animationLabel);

        // Animation timer
        Timer animationTimer = new Timer(500, new ActionListener() {
            private int state = 0;
            @Override
            public void actionPerformed(ActionEvent e) {
                switch (state % 3) {
                    case 0: animationLabel.setText("●  ○  ○"); break;
                    case 1: animationLabel.setText("○  ●  ○"); break;
                    case 2: animationLabel.setText("○  ○  ●"); break;
                }
                state++;
            }
        });
        animationTimer.start();

        centerPanel.add(Box.createVerticalGlue());
        waitingPanel.add(centerPanel, BorderLayout.CENTER);

        return waitingPanel;
    }

    private JPanel createGamePanel() {
        JPanel gamePanel = new JPanel();
        gamePanel.setLayout(new BorderLayout());

        // Top panel with exit button
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setOpaque(false);
        JButton exitButton = new JButton("Exit");
        exitButton.addActionListener(e -> {
            disconnect();
            System.exit(0);
        });
        topPanel.add(exitButton);
        gamePanel.add(topPanel, BorderLayout.NORTH);

        // Center: Game display
        gamePanel.add(createDisplay(), BorderLayout.CENTER);

        // Bottom: Input panel
        gamePanel.add(createInputPanel(), BorderLayout.SOUTH);

        return gamePanel;
    }

    private void switchToWelcomeScreen() {
        currentState = UIState.WELCOME_SCREEN;
        SwingUtilities.invokeLater(() -> {
            cardLayout.show(mainPanel, WELCOME_PANEL);
            // Reset UI state
            b_connect.setEnabled(true);
            t_userID.setEnabled(true);
            t_answerKey.setEnabled(true);
        });
    }

    private void switchToWaitingScreen() {
        currentState = UIState.WAITING_SCREEN;
        SwingUtilities.invokeLater(() -> {
            cardLayout.show(mainPanel, WAITING_PANEL);
            // Disable connection fields
            b_connect.setEnabled(false);
            t_userID.setEnabled(false);
            t_answerKey.setEnabled(false);
        });
    }

    private void switchToGameScreen() {
        currentState = UIState.GAME_SCREEN;
        SwingUtilities.invokeLater(() -> {
            cardLayout.show(mainPanel, GAME_PANEL);
            // Enable game controls
            if (b_send != null) b_send.setEnabled(true);
            if (t_input != null) {
                t_input.setEnabled(true);
                t_input.requestFocusInWindow();
            }
            if (b_submit != null) b_submit.setEnabled(true);
        });
    }


    JPanel createDisplay() {
        JPanel displayPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                ImageIcon background = new ImageIcon("src/image/intro.jpg");
                Image img = background.getImage();
                g.drawImage(img, 0, 0, getWidth(), getHeight(), this);
            }
        };
        displayPanel.setLayout(new BoxLayout(displayPanel, BoxLayout.Y_AXIS));

        // 0. 상단 0~20%: 배경을 보여주는 공간
        JPanel spacer1 = new JPanel();
        spacer1.setOpaque(false);
        spacer1.setPreferredSize(new Dimension(600, 80));
        spacer1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        displayPanel.add(spacer1);

        // 1. 20~40%: 선택된 숫자 표시 패널 + Clear 버튼
        JPanel numberDisplayPanel = new JPanel();
        numberDisplayPanel.setOpaque(false);
        numberDisplayPanel.setPreferredSize(new Dimension(600, 60));
        numberDisplayPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        numberDisplayPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 5));

        selectedNumbers = new JLabel[3];
        for (int i = 0; i < 3; i++) {
            selectedNumbers[i] = new JLabel("_");
            selectedNumbers[i].setFont(new Font("Arial", Font.BOLD, 36));
            selectedNumbers[i].setForeground(Color.WHITE);
            selectedNumbers[i].setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));
            selectedNumbers[i].setPreferredSize(new Dimension(50, 50));
            selectedNumbers[i].setHorizontalAlignment(SwingConstants.CENTER);
            numberDisplayPanel.add(selectedNumbers[i]);
        }

        b_backSpace = new JButton("Delete");
        b_backSpace.setFont(new Font("Arial", Font.BOLD, 12));
        b_backSpace.setPreferredSize(new Dimension(70, 50));
        b_backSpace.addActionListener(e -> backSpaceNumberSelection());
        numberDisplayPanel.add(b_backSpace);

        b_submit = new JButton("Submit");
        b_submit.setFont(new Font("Arial", Font.BOLD, 12));
        b_submit.setPreferredSize(new Dimension(80, 60));
        b_submit.addActionListener(e -> submitGuess());
        numberDisplayPanel.add(b_submit);

        displayPanel.add(numberDisplayPanel);

        // 1-5. 40~50%: 배경을 보여주는 공간
        JPanel spacer2 = new JPanel();
        spacer2.setOpaque(false);
        spacer2.setPreferredSize(new Dimension(600, 20));
        spacer2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        displayPanel.add(spacer2);

        // 2. 50~85%: 숫자 카드 패널 (0-9, 5개씩 2줄)
        JPanel numberCardPanel = new JPanel();
        numberCardPanel.setOpaque(false);
        numberCardPanel.setLayout(new GridLayout(2, 5, 10, 10));
        numberCardPanel.setPreferredSize(new Dimension(600, 240));
        numberCardPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 240));
        numberCardPanel.setBorder(BorderFactory.createEmptyBorder(10, 75, 10, 75));

        for (int i = 0; i < 10; i++) {
            final int number = i;
            JButton numberButton = new JButton(String.valueOf(i));
            numberButton.setFont(new Font("Arial", Font.BOLD, 24));
            numberButton.addActionListener(e -> onNumberClick(number));
            numberCardPanel.add(numberButton);
        }
        displayPanel.add(numberCardPanel);

        // 2-5. 85~85%: 배경을 보여주는 공간
        JPanel spacer3 = new JPanel();
        spacer3.setOpaque(false);
        spacer3.setPreferredSize(new Dimension(600, 10));
        spacer3.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
        displayPanel.add(spacer3);

        // 3. 85~100%: JTextArea (채팅 메시지 표시)
        t_display = new JTextArea(3, 20);
        t_display.setEditable(false);
        t_display.setOpaque(false);
        t_display.setBorder(null);
        JScrollPane scrollPane = new JScrollPane(t_display);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.setPreferredSize(new Dimension(600, 60));
        scrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        displayPanel.add(scrollPane);

        return displayPanel;
    }

    private void submitGuess() {
        // Todo: 입력 값 유효성 검사 구현하기
        Message guessMsg = new Message(Message.MessageType.GUESS, t_userID.getText(), getSelectedNumStr());
        try {
            out.writeObject(guessMsg);
            out.flush();
            // 모든 선택된 숫자를 초기화
            for (int i = 0; i < selectedNumbers.length; i++) {
                selectedNumbers[i].setText("_");
            }
            currentPosition = 0;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getSelectedNumStr() {
        return selectedNumbers[0].getText() + selectedNumbers[1].getText() + selectedNumbers[2].getText();
    }

    private void onNumberClick(int number) {
        if (currentPosition < 3) {
            selectedNumbers[currentPosition].setText(String.valueOf(number));
            currentPosition++;
        }
    }

    JPanel createInputPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        t_input = new JTextField(20);
        b_send = new JButton("Send");
        ActionListener sendActionListener =  (e) -> {
            sendMessage();
            t_input.setText("");
        };
        t_input.addActionListener(sendActionListener);
        b_send.addActionListener(sendActionListener);
        panel.add(t_input, BorderLayout.CENTER);
        panel.add(b_send, BorderLayout.EAST);
        return panel;
    }

    private void receiveMessage() {
        Message message;
        try {
            while ((message = (Message) in.readObject()) != null) {
                final Message msg = message;
                // Handle different message types
                SwingUtilities.invokeLater(() -> {
                    handleIncomingMessage(msg);
                });
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
            SwingUtilities.invokeLater(() -> {
                if (currentState != UIState.WELCOME_SCREEN) {
                    JOptionPane.showMessageDialog(this,
                        "Connection lost to server",
                        "Connection Error",
                        JOptionPane.ERROR_MESSAGE);
                    switchToWelcomeScreen();
                }
            });
        } catch (ClassNotFoundException e) {
            System.out.println("ClassNotFoundException: " + e.getMessage());
        }
    }

    private void handleIncomingMessage(Message msg) {
        switch (msg.getType()) {
            case START_GAME:
                // Server sends this when 2 players are ready
                displayMessage(msg.toString());
                switchToGameScreen();
                break;

            case CHAT:
                // Display chat message
                displayMessage(msg.toString());
                break;

            case RESULT:
                // Display guess result
                displayMessage(msg.toString());
                break;

            case DISCONNECT:
                // Handle player disconnect
                displayMessage(msg.toString());
                if (currentState == UIState.GAME_SCREEN) {
                    JOptionPane.showMessageDialog(this,
                        "Other player disconnected. Returning to welcome screen.",
                        "Player Disconnected",
                        JOptionPane.INFORMATION_MESSAGE);
                    disconnect();
                    switchToWelcomeScreen();
                }
                break;

            default:
                displayMessage(msg.toString());
                break;
        }
    }

    public void sendMessage() {
        String text = t_input.getText();
        if (text.isEmpty()) {
            return;
        }

        Message message = new Message(Message.MessageType.CHAT, t_userID.getText(), text);
        try {
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        displayMessage("나: " + text);
    }

    private void backSpaceNumberSelection() {
        if (currentPosition == 0) {
            return;
        }

        selectedNumbers[--currentPosition].setText("_");
    }

    private void displayMessage(String message) {
        t_display.append(message + "\n");
        t_display.setCaretPosition(t_display.getDocument().getLength());
    }


    private void disconnect() {
        try {
            // Send disconnect message to server
            if (out != null && socket != null && socket.isConnected()) {
                Message disconnectMsg = new Message(Message.MessageType.DISCONNECT, t_userID.getText());
                out.writeObject(disconnectMsg);
                out.flush();
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

    private void loadConnectionInfo() {
        try (FileInputStream fis = new FileInputStream("CONN_INFO.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis))) {
            serverAddress = reader.readLine().trim();
            serverPort = Integer.parseInt(reader.readLine().trim());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                "Failed to load connection info: " + e.getMessage() +
                "\nUsing defaults: localhost:54321",
                "Configuration Error",
                JOptionPane.WARNING_MESSAGE);
            serverAddress = "localhost";
            serverPort = 54321;
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                "Invalid port number in CONN_INFO.txt\nUsing defaults: localhost:54321",
                "Configuration Error",
                JOptionPane.WARNING_MESSAGE);
            serverAddress = "localhost";
            serverPort = 54321;
        }
    }

    private void connectToServer() {
        try {
            // Show connecting status
            b_connect.setText("Connecting...");
            b_connect.setEnabled(false);

            socket = new Socket(serverAddress, serverPort);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            Message connectMsg = new Message(Message.MessageType.CONNECT, t_userID.getText(), t_answerKey.getText());
            out.writeObject(connectMsg);
            out.flush();

            // Start receive thread
            Thread receiveThread = new Thread(() -> {
                receiveMessage();
            }, "receiveThread");
            receiveThread.start();

            // Switch to WAITING screen (not game screen!)
            switchToWaitingScreen();

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                "Connection failed: " + ex.getMessage(),
                "Connection Error",
                JOptionPane.ERROR_MESSAGE);
            b_connect.setText("Connect");
            b_connect.setEnabled(true);
        }
    }

    private boolean isValidAnswerKey(String key) {
        // Check if exactly 3 characters
        if (key.length() != 3) {
            return false;
        }

        // Check if all characters are digits
        if (!key.matches("\\d{3}")) {
            return false;
        }

        // Check for duplicates
        if (key.charAt(0) == key.charAt(1) ||
            key.charAt(0) == key.charAt(2) ||
            key.charAt(1) == key.charAt(2)) {
            return false;
        }

        return true;
    }

    private void showToast(String message) {
        JWindow toast = new JWindow();
        toast.setAlwaysOnTop(true);

        JPanel panel = new JPanel();
        panel.setBackground(new Color(50, 50, 50, 230));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JLabel label = new JLabel(message);
        label.setForeground(Color.WHITE);
        label.setFont(new Font("Arial", Font.BOLD, 16));
        panel.add(label);

        toast.add(panel);
        toast.pack();

        // Position toast at bottom-center of the screen
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (screenSize.width - toast.getWidth()) / 2;
        int y = screenSize.height - toast.getHeight() - 100;
        toast.setLocation(x, y);

        toast.setVisible(true);

        // Auto-hide after 2.5 seconds
        Timer timer = new Timer(2500, e -> {
            toast.setVisible(false);
            toast.dispose();
        });
        timer.setRepeats(false);
        timer.start();
    }

    BaseballClientGUI() {
        setTitle("Multi ClientGUI");
        loadConnectionInfo();
        buildGUI();
        setBounds(400, 100, 550, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }

    public static void main(String[] args) {
        new BaseballClientGUI();
    }

}