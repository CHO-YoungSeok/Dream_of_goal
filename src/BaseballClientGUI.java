import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;

public class BaseballClientGUI extends JFrame {

    // UI State Enum for screen management
    private enum UIState {
        LOGIN_SCREEN,          // Authentication (login/register)
        LOBBY_SCREEN,          // Room list, user list, create room
        ROOM_WAITING_SCREEN,   // Room details, ready system, player list
        GAME_SCREEN,           // Active gameplay
        RESULT_SCREEN          // Game outcome, stay/leave options
    }

    // Screen management
    private UIState currentState = UIState.LOGIN_SCREEN;
    private CardLayout cardLayout;
    private JPanel mainPanel;

    // Screen constants
    private static final String LOGIN_PANEL = "LOGIN";
    private static final String LOBBY_PANEL = "LOBBY";
    private static final String ROOM_WAITING_PANEL = "ROOM_WAITING";
    private static final String GAME_PANEL = "GAME";
    private static final String RESULT_PANEL = "RESULT";

    // Existing UI components
    JTextPane t_display;
    private JPanel numberDisplayPanel;
    JTextField t_input;
    JButton b_connect;
    JButton b_send;
    JButton b_exit;
    JButton b_submit;
    Socket socket;
    ObjectOutputStream out;
    ObjectInputStream in;
    JLabel[] selectedNumbers;
    int currentPosition = 0;
    JButton b_backSpace;

    // Connection info loaded from CONN_INFO.txt
    private String serverAddress;
    private int serverPort;

    // ========== Authentication & User Session ==========
    private String currentUserId = null;
    private String currentPassword = null;
    private boolean isAuthenticated = false;
    private Message.UserStatus currentUserStatus = Message.UserStatus.OFFLINE;

    // Login screen components
    private JTextField t_loginUserId;
    private JPasswordField t_loginPassword;
    private JTextField t_registerUserId;
    private JPasswordField t_registerPassword;
    private JTextField t_registerNickname;
    private JButton b_login, b_register;

    // ========== Room State ==========
    private Integer currentRoomId = null;
    private String currentRoomName = null;
    private String roomMasterUserId = null;
    private boolean isRoomMaster = false;
    private Message.GameMode currentGameMode = null;
    private Message.Difficulty currentDifficulty = null;
    private Message.TurnTimeLimit currentTurnTimeLimit = null;
    private boolean currentRoomIsPrivate = false;
    private String currentRoomPassword = null;
    private boolean currentRoomAllowSpectators = false;

    // Room players tracking
    private java.util.List<String> roomPlayersList = new java.util.ArrayList<>();
    private java.util.Map<String, Boolean> playerReadyStatus = new java.util.HashMap<>();

    // Lobby screen components
    private JTable roomListTable;
    private javax.swing.table.DefaultTableModel roomListTableModel;
    private JButton b_createRoom, b_joinRoom, b_refreshRoomList;
    private JButton b_viewStats, b_viewHistory;

    // Room waiting screen components
    private JLabel l_roomTitle;
    private JLabel l_roomSettings;
    private javax.swing.DefaultListModel<String> roomPlayerListModel;
    private JList<String> roomPlayerList;
    private JButton b_ready, b_cancelReady, b_startGame, b_leaveRoom;

    // ========== Game State ==========
    private String currentGameId = null;
    private int digitCount = 3;  // from Difficulty (3, 4, or 5)
    private int turnTimeLimitSeconds = 30;  // from TurnTimeLimit
    private int currentRound = 0;
    private boolean isCurrentlyTopInning = true;  // 초(true) or 말(false)
    private String currentTurnPlayerId = null;
    private String myAnswerKey = null;  // Set when game starts
    private int myTeamNumber = 0;  // 1 or 2 for team mode

    // Game screen components
    private JLabel l_roundInfo;  // "N회 초/말"
    private JLabel l_turnInfo;   // "Your turn" or "Waiting..."
    private JLabel l_timerDisplay;  // Turn countdown
    private Timer turnTimer;
    private int remainingSeconds;

    // Result screen components
    private JLabel l_resultMessage;
    private JTextArea t_gameRecap;
    private JButton b_stayInRoom, b_leaveToLobby;

    void buildGUI() {
        setLayout(new BorderLayout());

        // Create CardLayout container for switching screens
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        // Create all five screens
        mainPanel.add(createLoginPanel(), LOGIN_PANEL);
        mainPanel.add(createLobbyPanel(), LOBBY_PANEL);
        mainPanel.add(createRoomWaitingPanel(), ROOM_WAITING_PANEL);
        mainPanel.add(createGamePanel(), GAME_PANEL);
        mainPanel.add(createResultPanel(), RESULT_PANEL);

        add(mainPanel, BorderLayout.CENTER);

        // Show login screen initially
        cardLayout.show(mainPanel, LOGIN_PANEL);
    }

    private JPanel createLoginPanel() {
        JPanel loginPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                ImageIcon background = new ImageIcon("src/image/intro.jpg");
                Image img = background.getImage();
                g.drawImage(img, 0, 0, getWidth(), getHeight(), this);
            }
        };
        loginPanel.setLayout(new BorderLayout());

        // Exit button in top-left
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setOpaque(false);
        b_exit = new JButton("Exit");
        b_exit.addActionListener(e -> System.exit(0));
        topPanel.add(b_exit);
        loginPanel.add(topPanel, BorderLayout.NORTH);

        // Center panel for login/register
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
        centerPanel.add(Box.createRigidArea(new Dimension(0, 50)));

        // Login Section
        JLabel loginLabel = new JLabel("Login");
        loginLabel.setFont(new Font("Arial", Font.BOLD, 24));
        loginLabel.setForeground(Color.YELLOW);
        loginLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(loginLabel);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // User ID for login
        JPanel loginUserPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        loginUserPanel.setOpaque(false);
        JLabel l_loginUserId = new JLabel("User ID:");
        l_loginUserId.setFont(new Font("Arial", Font.BOLD, 18));
        l_loginUserId.setForeground(Color.WHITE);
        t_loginUserId = new JTextField(15);
        t_loginUserId.setFont(new Font("Arial", Font.PLAIN, 16));
        loginUserPanel.add(l_loginUserId);
        loginUserPanel.add(t_loginUserId);
        centerPanel.add(loginUserPanel);

        // Password for login
        JPanel loginPassPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        loginPassPanel.setOpaque(false);
        JLabel l_loginPassword = new JLabel("Password:");
        l_loginPassword.setFont(new Font("Arial", Font.BOLD, 18));
        l_loginPassword.setForeground(Color.WHITE);
        t_loginPassword = new JPasswordField(15);
        t_loginPassword.setFont(new Font("Arial", Font.PLAIN, 16));
        loginPassPanel.add(l_loginPassword);
        loginPassPanel.add(t_loginPassword);
        centerPanel.add(loginPassPanel);

        // Login button
        JPanel loginButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        loginButtonPanel.setOpaque(false);
        b_login = new JButton("Login");
        b_login.setFont(new Font("Arial", Font.BOLD, 20));
        b_login.setPreferredSize(new Dimension(150, 45));
        b_login.addActionListener(e -> handleLogin());
        loginButtonPanel.add(b_login);
        centerPanel.add(loginButtonPanel);

        centerPanel.add(Box.createRigidArea(new Dimension(0, 40)));

        // Register Section
        JLabel registerLabel = new JLabel("New User? Register");
        registerLabel.setFont(new Font("Arial", Font.BOLD, 24));
        registerLabel.setForeground(Color.YELLOW);
        registerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(registerLabel);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // User ID for register
        JPanel regUserPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        regUserPanel.setOpaque(false);
        JLabel l_regUserId = new JLabel("User ID:");
        l_regUserId.setFont(new Font("Arial", Font.BOLD, 18));
        l_regUserId.setForeground(Color.WHITE);
        t_registerUserId = new JTextField(15);
        t_registerUserId.setFont(new Font("Arial", Font.PLAIN, 16));
        regUserPanel.add(l_regUserId);
        regUserPanel.add(t_registerUserId);
        centerPanel.add(regUserPanel);

        // Password for register
        JPanel regPassPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        regPassPanel.setOpaque(false);
        JLabel l_regPassword = new JLabel("Password:");
        l_regPassword.setFont(new Font("Arial", Font.BOLD, 18));
        l_regPassword.setForeground(Color.WHITE);
        t_registerPassword = new JPasswordField(15);
        t_registerPassword.setFont(new Font("Arial", Font.PLAIN, 16));
        regPassPanel.add(l_regPassword);
        regPassPanel.add(t_registerPassword);
        centerPanel.add(regPassPanel);

        // Nickname for register
        JPanel regNickPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        regNickPanel.setOpaque(false);
        JLabel l_regNickname = new JLabel("Nickname:");
        l_regNickname.setFont(new Font("Arial", Font.BOLD, 18));
        l_regNickname.setForeground(Color.WHITE);
        t_registerNickname = new JTextField(15);
        t_registerNickname.setFont(new Font("Arial", Font.PLAIN, 16));
        regNickPanel.add(l_regNickname);
        regNickPanel.add(t_registerNickname);
        centerPanel.add(regNickPanel);

        // Register button
        JPanel registerButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        registerButtonPanel.setOpaque(false);
        b_register = new JButton("Register");
        b_register.setFont(new Font("Arial", Font.BOLD, 20));
        b_register.setPreferredSize(new Dimension(150, 45));
        b_register.addActionListener(e -> handleRegister());
        registerButtonPanel.add(b_register);
        centerPanel.add(registerButtonPanel);

        centerPanel.add(Box.createVerticalGlue());
        loginPanel.add(centerPanel, BorderLayout.CENTER);

        return loginPanel;
    }

    private JPanel createGamePanel() {
        JPanel gamePanel = new JPanel();
        gamePanel.setLayout(new BorderLayout());

        // Top panel with game info
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);

        // Exit button
        JButton exitButton = new JButton("Exit");
        exitButton.addActionListener(e -> {
            disconnect();
            System.exit(0);
        });
        topPanel.add(exitButton, BorderLayout.WEST);

        // Game Info Display
        JPanel gameInfoPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        gameInfoPanel.setOpaque(false);
        l_roundInfo = new JLabel("1회 초");
        l_roundInfo.setFont(new Font("Arial", Font.BOLD, 18));
        l_roundInfo.setForeground(Color.WHITE);
        l_turnInfo = new JLabel("Your turn");
        l_turnInfo.setFont(new Font("Arial", Font.BOLD, 18));
        l_turnInfo.setForeground(Color.YELLOW);
        l_timerDisplay = new JLabel("30s");
        l_timerDisplay.setFont(new Font("Arial", Font.BOLD, 18));
        l_timerDisplay.setForeground(Color.RED);
        gameInfoPanel.add(l_roundInfo);
        gameInfoPanel.add(l_turnInfo);
        gameInfoPanel.add(l_timerDisplay);
        topPanel.add(gameInfoPanel, BorderLayout.CENTER);

        gamePanel.add(topPanel, BorderLayout.NORTH);

        // Center: Game display
        gamePanel.add(createDisplay(), BorderLayout.CENTER);

        // Bottom: Input panel
        gamePanel.add(createInputPanel(), BorderLayout.SOUTH);

        return gamePanel;
    }


        JPanel createDisplay() {


            JPanel displayPanel = new JPanel() {


                @Override


                protected void paintComponent(Graphics g) {


                    super.paintComponent(g);


                    ImageIcon background = new ImageIcon("src/image/feild.jpg");


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


            numberDisplayPanel = new JPanel();


            numberDisplayPanel.setOpaque(false);


            numberDisplayPanel.setPreferredSize(new Dimension(600, 60));


            numberDisplayPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));


            numberDisplayPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 5));


    


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


    


            // 3. 85~100%: JTextPane (채팅 메시지 표시 with colors)


            t_display = new JTextPane();


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


    


        private void setupGameUI(int digitCount) {


            // 기존 컴포넌트 제거 (Delete, Submit 버튼 제외)


            Component[] components = numberDisplayPanel.getComponents();


            for (Component component : components) {


                if (component instanceof JLabel) {


                    numberDisplayPanel.remove(component);


                }


            }


    


            // 새로운 JLabel 배열 생성


            selectedNumbers = new JLabel[digitCount];


            for (int i = 0; i < digitCount; i++) {


                selectedNumbers[i] = new JLabel("_");


                selectedNumbers[i].setFont(new Font("Arial", Font.BOLD, 36));


                selectedNumbers[i].setForeground(Color.WHITE);


                selectedNumbers[i].setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));


                selectedNumbers[i].setPreferredSize(new Dimension(50, 50));


                selectedNumbers[i].setHorizontalAlignment(SwingConstants.CENTER);


                // Delete, Submit 버튼 전에 추가


                numberDisplayPanel.add(selectedNumbers[i], i);


            }


    


            currentPosition = 0;


    


            // UI 갱신


            numberDisplayPanel.revalidate();


            numberDisplayPanel.repaint();


        }


    

    private void submitGuess() {
        String guess = getSelectedNumStr();

        // Validate guess
        if (!isValidGuess(guess)) {
            showToast(String.format("중복 없이 %d자리 숫자만 입력하세요 (0~9)", digitCount));
            return;
        }

        Message guessMsg = Message.createGuessMessage(currentUserId != null ? currentUserId : "guest", guess);
        try {
            out.writeObject(guessMsg);
            out.flush();
            // 모든 선택된 숫자를 초기화
            for (int i = 0; i < digitCount; i++) {
                selectedNumbers[i].setText("_");
            }
            currentPosition = 0;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isValidGuess(String guess) {
        // Check length
        if (guess.contains("_") || guess.length() != digitCount) {
            return false;
        }

        // Check if all digits
        if (!guess.matches("\\d+")) {
            return false;
        }

        // Check for duplicates
        java.util.Set<Character> seen = new java.util.HashSet<>();
        for (char c : guess.toCharArray()) {
            if (!seen.add(c)) {
                return false;
            }
        }

        return true;
    }

    private String getSelectedNumStr() {
        if (selectedNumbers == null) return "";
        StringBuilder sb = new StringBuilder();
        for (JLabel label : selectedNumbers) {
            sb.append(label.getText());
        }
        return sb.toString();
    }

    private void onNumberClick(int number) {
        if (currentPosition < digitCount) {
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
                if (currentState != UIState.LOGIN_SCREEN) {
                    JOptionPane.showMessageDialog(this,
                        "Connection lost to server",
                        "Connection Error",
                        JOptionPane.ERROR_MESSAGE);
                    switchToLoginScreen();
                }
            });
        } catch (ClassNotFoundException e) {
            System.out.println("ClassNotFoundException: " + e.getMessage());
        }
    }

    private void handleIncomingMessage(Message msg) {
        switch (msg.getType()) {
            // Authentication messages
            case LOGIN_RESPONSE:
                if (msg.isSuccess()) {
                    isAuthenticated = true;
                    currentUserId = msg.getUserId();
                    currentUserStatus = Message.UserStatus.ONLINE;
                    switchToLobbyScreen();
                    showToast("Login successful!");
                } else {
                    JOptionPane.showMessageDialog(this,
                        msg.getErrorMessage() != null ? msg.getErrorMessage() : "Login failed",
                        "Login Error",
                        JOptionPane.ERROR_MESSAGE);
                }
                break;

            case REGISTER_RESPONSE:
                if (msg.isSuccess()) {
                    JOptionPane.showMessageDialog(this,
                        "Registration successful! Please login.",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                    // Clear register fields
                    t_registerUserId.setText("");
                    t_registerPassword.setText("");
                    t_registerNickname.setText("");
                } else {
                    JOptionPane.showMessageDialog(this,
                        msg.getErrorMessage() != null ? msg.getErrorMessage() : "Registration failed",
                        "Registration Error",
                        JOptionPane.ERROR_MESSAGE);
                }
                break;

            // Room messages
            case ROOM_LIST_RESPONSE:
                updateRoomListTable(msg);
                break;

            case CREATE_ROOM_RESPONSE:
                if (msg.isSuccess()) {
                    currentRoomId = msg.getRoomId();
                    currentRoomName = msg.getRoomName();
                    roomMasterUserId = currentUserId;
                    isRoomMaster = true;
                    currentGameMode = msg.getGameMode();
                    currentDifficulty = msg.getDifficulty();
                    currentTurnTimeLimit = msg.getTurnTimeLimit();
                    currentRoomIsPrivate = msg.isPrivate();
                    currentRoomPassword = msg.getRoomPassword();
                    currentRoomAllowSpectators = msg.isAllowSpectators();
                    switchToRoomWaitingScreen();
                } else {
                    JOptionPane.showMessageDialog(this,
                        "Failed to create room: " + msg.getErrorMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                }
                break;

            case JOIN_ROOM_RESPONSE:
                if (msg.isSuccess()) {
                    currentRoomId = msg.getRoomId();
                    currentRoomName = msg.getRoomName();
                    roomMasterUserId = msg.getRoomMaster();
                    isRoomMaster = false;
                    currentGameMode = msg.getGameMode();
                    currentDifficulty = msg.getDifficulty();
                    currentTurnTimeLimit = msg.getTurnTimeLimit();
                    currentRoomIsPrivate = msg.isPrivate();
                    currentRoomPassword = msg.getRoomPassword();
                    currentRoomAllowSpectators = msg.isAllowSpectators();
                    switchToRoomWaitingScreen();
                } else {
                    JOptionPane.showMessageDialog(this,
                        "Failed to join room: " + msg.getErrorMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                }
                break;

            case ROOM_INFO_UPDATE:
                if (msg.getData() instanceof java.util.HashMap) {
                    java.util.HashMap<String, Object> roomData = (java.util.HashMap<String, Object>) msg.getData();
                    roomPlayersList = (java.util.List<String>) roomData.get("players");
                    playerReadyStatus = (java.util.Map<String, Boolean>) roomData.get("readyStatus");
                    roomMasterUserId = msg.getRoomMaster(); // 방장 정보 업데이트
                    
                    roomPlayerListModel.clear();
                    for (String player : roomPlayersList) {
                        roomPlayerListModel.addElement(player);
                    }
                }
                break;

            // Ready and game start
            case READY_STATUS_UPDATE:
                if (msg.getData() instanceof java.util.Map) {
                    playerReadyStatus = (java.util.Map<String, Boolean>) msg.getData();
                    roomPlayerList.repaint(); // 리스트를 다시 그려서 렌더러가 상태를 반영하도록 함
                }
                System.out.println("Ready status updated");
                break;

            case START_GAME:
                // Game is starting!
                currentGameId = msg.getGameId();
                if (msg.getDifficulty() != null) {
                    currentDifficulty = msg.getDifficulty();
                    digitCount = currentDifficulty.getDigitCount();
                }
                if (msg.getTurnTimeLimit() != null) {
                    turnTimeLimitSeconds = msg.getTurnTimeLimit().getSeconds();
                }
                displayMessage("게임 시작! " + msg.getContent());

                // 게임 난이도에 맞게 UI 설정
                setupGameUI(digitCount);

                // 게임 화면으로 전환
                switchToGameScreen();

                // 정답 입력 다이얼로그
                myAnswerKey = promptForAnswerKey();
                displayMessage("정답이 설정되었습니다: " + myAnswerKey);

                // 정답을 서버에 전송 (GUESS 타입 사용)
                Message answerMsg = Message.createGuessMessage(currentUserId, myAnswerKey);
                sendMessage(answerMsg);
                break;

            // Game progress
            case TURN_INFO:
                currentRound = msg.getRound();
                isCurrentlyTopInning = msg.isTop();
                currentTurnPlayerId = msg.getCurrentTurnPlayer();

                // Update round and turn displays
                l_roundInfo.setText(msg.getRoundInfo());
                if (currentUserId.equals(currentTurnPlayerId)) {
                    l_turnInfo.setText("Your Turn");
                    l_turnInfo.setForeground(Color.YELLOW);
                    b_submit.setEnabled(true); // 내 턴일 때만 제출 버튼 활성화
                } else {
                    l_turnInfo.setText("Waiting for " + currentTurnPlayerId);
                    l_turnInfo.setForeground(Color.WHITE);
                    b_submit.setEnabled(false);
                }

                // Start turn timer
                remainingSeconds = turnTimeLimitSeconds;
                l_timerDisplay.setText(remainingSeconds + "s");
                if (turnTimer != null && turnTimer.isRunning()) {
                    turnTimer.stop();
                }
                turnTimer = new Timer(1000, e -> {
                    remainingSeconds--;
                    if (remainingSeconds >= 0) {
                        l_timerDisplay.setText(remainingSeconds + "s");
                    } else {
                        turnTimer.stop();
                    }
                });
                turnTimer.start();
                break;

            case GUESS_RESULT:
                // Display guess result
                displayMessage(msg.toString());
                break;

            case TURN_TIMEOUT:
                showToast("Turn timeout!");
                break;

            case END_GAME:
            case GAME_RESULT:
                // 게임 종료 처리
                String winnerId = msg.getWinnerId();
                boolean isDraw = msg.isDraw();

                if (isDraw) {
                    l_resultMessage.setText("무승부");
                } else if (currentUserId.equals(winnerId)) {
                    l_resultMessage.setText("승리!");
                } else {
                    l_resultMessage.setText("패배");
                }
                
                // 타이머 중지
                if (turnTimer != null) {
                    turnTimer.stop();
                }

                // 결과 화면으로 전환
                switchToResultScreen();
                break;

            // Chat messages
            case CHAT_ALL:
                displayMessage(msg.toString(), Color.RED);
                break;

            case CHAT_TEAM:
                displayMessage(msg.toString(), Color.BLUE);
                break;

            case CHAT_ROOM:
            case CHAT_WHISPER:
                displayMessage(msg.toString(), Color.WHITE);
                break;

            // Stats and history
            case STATS_RESPONSE:
            case GAME_HISTORY_RESPONSE:
            case RANKING_RESPONSE:
                // TODO: Display stats/history
                System.out.println("Stats/History received");
                break;

            // User status
            case USER_STATUS_UPDATE:
            case USER_LIST_RESPONSE:
                // TODO: Update user list
                System.out.println("User status updated");
                break;

            // Error handling
            case ERROR:
                Message.ErrorCode errorCode = msg.getErrorCode();
                String errorMessage = msg.getErrorMessage();
                JOptionPane.showMessageDialog(this,
                    String.format("[%d] %s", errorCode != null ? errorCode.getCode() : 0, errorMessage),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);

                // Handle specific errors that require screen transitions
                if (errorCode == Message.ErrorCode.ALREADY_LOGGED_IN ||
                    errorCode == Message.ErrorCode.LOGIN_FAILED) {
                    if (currentState != UIState.LOGIN_SCREEN) {
                        disconnect();
                        switchToLoginScreen();
                    }
                }
                break;

            default:
                displayMessage(msg.toString());
                System.out.println("Unhandled message type: " + msg.getType());
                break;
        }
    }

    public void sendMessage() {
        String text = t_input.getText();
        if (text.isEmpty()) {
            return;
        }

        Message msg;
        String displayMessagePrefix = "나: ";

        // 1. 명령어 파싱
        if (text.startsWith("/w ") || text.startsWith("/whisper ")) {
            String[] parts = text.split(" ", 3);
            if (parts.length < 3) {
                displayMessage("귓속말 사용법: /w [대상ID] [메시지]", Color.ORANGE);
                return;
            }
            String targetId = parts[1];
            String content = parts[2];
            msg = Message.createChatMessage(Message.MessageType.CHAT_WHISPER, currentUserId, content, targetId);
            displayMessagePrefix = "[To " + targetId + "]: ";
        } else if (text.startsWith("/all ")) {
            String content = text.substring(5);
            msg = new Message(Message.MessageType.CHAT_ALL, currentUserId, content);
            displayMessagePrefix = "[To All]: ";
        } else if (text.startsWith("/team ")) {
            String content = text.substring(6);
            msg = new Message(Message.MessageType.CHAT_TEAM, currentUserId, content);
            displayMessagePrefix = "[To Team]: ";
        } else if (text.startsWith("/room ")) {
            String content = text.substring(6);
            msg = new Message(Message.MessageType.CHAT_ROOM, currentUserId, content);
            displayMessagePrefix = "[To Room]: ";
        } else {
            // 2. 기본 채팅 타입 결정
            if (currentState == UIState.ROOM_WAITING_SCREEN || currentState == UIState.GAME_SCREEN) {
                // 방/게임에 있을 경우 기본은 방 채팅
                msg = new Message(Message.MessageType.CHAT_ROOM, currentUserId, text);
                displayMessagePrefix = "[To Room]: ";
            } else {
                // 로비에 있을 경우 기본은 전체 채팅
                msg = new Message(Message.MessageType.CHAT_ALL, currentUserId, text);
                displayMessagePrefix = "[To All]: ";
            }
        }

        // 3. 메시지 전송
        sendMessage(msg);

        // 4. 로컬에 보낸 메시지 표시
        displayMessage(displayMessagePrefix + msg.getContent(), Color.YELLOW); // 보낸 메시지는 노란색으로
    }

    private void backSpaceNumberSelection() {
        if (currentPosition == 0) {
            return;
        }

        selectedNumbers[--currentPosition].setText("_");
    }

    private void displayMessage(String message) {
        displayMessage(message, Color.WHITE);
    }

    private void displayMessage(String message, Color color) {
        javax.swing.text.StyledDocument doc = t_display.getStyledDocument();
        javax.swing.text.SimpleAttributeSet attrs = new javax.swing.text.SimpleAttributeSet();
        javax.swing.text.StyleConstants.setForeground(attrs, color);

        try {
            doc.insertString(doc.getLength(), message + "\n", attrs);
        } catch (javax.swing.text.BadLocationException e) {
            System.err.println("Error appending message: " + e.getMessage());
        }
        t_display.setCaretPosition(doc.getLength());
    }


    private void disconnect() {
        try {
            // Send logout message to server
            if (out != null && socket != null && socket.isConnected() && currentUserId != null) {
                Message logoutMsg = new Message(Message.MessageType.LOGOUT, currentUserId);
                out.writeObject(logoutMsg);
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
            socket = new Socket(serverAddress, serverPort);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            // Start receive thread
            Thread receiveThread = new Thread(() -> {
                receiveMessage();
            }, "receiveThread");
            receiveThread.start();

            System.out.println("Connected to server at " + serverAddress + ":" + serverPort);

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                "Connection failed: " + ex.getMessage(),
                "Connection Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 게임 시작 시 정답 입력 다이얼로그
     */
    private String promptForAnswerKey() {
        while (true) {
            String answer = JOptionPane.showInputDialog(
                this,
                String.format("정답 숫자를 입력하세요 (%d자리, 0~9, 중복 불가)", digitCount),
                "정답 입력",
                JOptionPane.PLAIN_MESSAGE
            );

            if (answer == null) {
                // 취소 버튼 클릭 시 기본값 사용
                return generateRandomAnswer();
            }

            if (isValidAnswerKey(answer)) {
                return answer;
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    String.format("%d자리 숫자여야 하며, 중복 없이 0~9 범위여야 합니다", digitCount),
                    "잘못된 입력",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    /**
     * 랜덤 정답 생성
     */
    private String generateRandomAnswer() {
        java.util.List<Integer> digits = new java.util.ArrayList<>();
        for (int i = 0; i <= 9; i++) {
            digits.add(i);
        }
        java.util.Collections.shuffle(digits);

        StringBuilder answer = new StringBuilder();
        for (int i = 0; i < digitCount; i++) {
            answer.append(digits.get(i));
        }
        return answer.toString();
    }

    private boolean isValidAnswerKey(String key) {
        // Check length
        if (key.length() != digitCount) {
            return false;
        }

        // Check if all characters are digits
        if (!key.matches("\\d+")) {
            return false;
        }

        // Check for duplicates
        java.util.Set<Character> seen = new java.util.HashSet<>();
        for (char c : key.toCharArray()) {
            if (!seen.add(c)) {
                return false;
            }
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

    // ========== Authentication Handlers ==========

    private void handleLogin() {
        String userId = t_loginUserId.getText().trim();
        String password = new String(t_loginPassword.getPassword());

        if (userId.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter both User ID and Password",
                "Input Required",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Connect to server if not connected
        if (socket == null || !socket.isConnected()) {
            connectToServer();
        }

        // Send LOGIN_REQUEST
        Message loginMsg = Message.createLoginRequest(userId, password);
        sendMessage(loginMsg);

        // Store credentials for session
        currentUserId = userId;
        currentPassword = password;
    }

    private void handleRegister() {
        String userId = t_registerUserId.getText().trim();
        String password = new String(t_registerPassword.getPassword());
        String nickname = t_registerNickname.getText().trim();

        if (userId.isEmpty() || password.isEmpty() || nickname.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please fill in all registration fields",
                "Input Required",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Connect to server if not connected
        if (socket == null || !socket.isConnected()) {
            connectToServer();
        }

        // Send REGISTER_REQUEST
        Message registerMsg = Message.createRegisterRequest(userId, password, nickname);
        sendMessage(registerMsg);
    }

    // ========== Screen Creation Methods (Placeholders) ==========

    private JPanel createLobbyPanel() {
        JPanel lobbyPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                ImageIcon background = new ImageIcon("src/image/intro.jpg");
                Image img = background.getImage();
                g.drawImage(img, 0, 0, getWidth(), getHeight(), this);
            }
        };
        lobbyPanel.setLayout(new BorderLayout());

        // Top panel with title and user info
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("Lobby", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
        titleLabel.setForeground(Color.YELLOW);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        topPanel.add(titleLabel, BorderLayout.CENTER);

        lobbyPanel.add(topPanel, BorderLayout.NORTH);

        // Center panel with room list
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        // Room list table
        String[] columnNames = {"방 번호", "방 이름", "방장", "상태", "인원", "모드", "난이도"};
        roomListTableModel = new javax.swing.table.DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        roomListTable = new JTable(roomListTableModel);
        roomListTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        roomListTable.getTableHeader().setReorderingAllowed(false);
        roomListTable.setFont(new Font("Arial", Font.PLAIN, 14));
        roomListTable.setRowHeight(25);

        JScrollPane scrollPane = new JScrollPane(roomListTable);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        lobbyPanel.add(centerPanel, BorderLayout.CENTER);

        // Bottom panel with buttons
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        bottomPanel.setOpaque(false);

        b_createRoom = new JButton("방 생성");
        b_createRoom.setFont(new Font("Arial", Font.BOLD, 16));
        b_createRoom.setPreferredSize(new Dimension(120, 40));
        b_createRoom.addActionListener(e -> showCreateRoomDialog());

        b_joinRoom = new JButton("방 입장");
        b_joinRoom.setFont(new Font("Arial", Font.BOLD, 16));
        b_joinRoom.setPreferredSize(new Dimension(120, 40));
        b_joinRoom.addActionListener(e -> handleJoinRoom());

        b_refreshRoomList = new JButton("새로고침");
        b_refreshRoomList.setFont(new Font("Arial", Font.BOLD, 16));
        b_refreshRoomList.setPreferredSize(new Dimension(120, 40));
        b_refreshRoomList.addActionListener(e -> requestRoomList());

        bottomPanel.add(b_createRoom);
        bottomPanel.add(b_joinRoom);
        bottomPanel.add(b_refreshRoomList);

        lobbyPanel.add(bottomPanel, BorderLayout.SOUTH);

        return lobbyPanel;
    }

    private JPanel createRoomWaitingPanel() {
        JPanel roomPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                ImageIcon background = new ImageIcon("src/image/intro.jpg");
                Image img = background.getImage();
                g.drawImage(img, 0, 0, getWidth(), getHeight(), this);
            }
        };
        roomPanel.setLayout(new BorderLayout());

        // 상단 패널 (방 정보)
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setOpaque(false);
        topPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));

        // 방 제목
        l_roomTitle = new JLabel("", SwingConstants.CENTER);
        l_roomTitle.setFont(new Font("Arial", Font.BOLD, 24));
        l_roomTitle.setForeground(Color.YELLOW);
        l_roomTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        topPanel.add(l_roomTitle);

        topPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // 방 설정 정보
        l_roomSettings = new JLabel("", SwingConstants.CENTER);
        l_roomSettings.setFont(new Font("Arial", Font.PLAIN, 16));
        l_roomSettings.setForeground(Color.WHITE);
        l_roomSettings.setAlignmentX(Component.CENTER_ALIGNMENT);
        topPanel.add(l_roomSettings);

        roomPanel.add(topPanel, BorderLayout.NORTH);

        // 중앙 패널 (플레이어 리스트)
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 40, 10, 40));

        JLabel l_players = new JLabel("플레이어 목록");
        l_players.setFont(new Font("Arial", Font.BOLD, 18));
        l_players.setForeground(Color.WHITE);
        centerPanel.add(l_players, BorderLayout.NORTH);

        // 플레이어 리스트
        roomPlayerListModel = new javax.swing.DefaultListModel<>();
        roomPlayerList = new JList<>(roomPlayerListModel);
        roomPlayerList.setCellRenderer(new PlayerListCellRenderer()); // 커스텀 렌더러 설정
        roomPlayerList.setFont(new Font("Arial", Font.PLAIN, 16));
        roomPlayerList.setOpaque(false);
        roomPlayerList.setBackground(new Color(0, 0, 0, 100));
        roomPlayerList.setForeground(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(roomPlayerList);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        roomPanel.add(centerPanel, BorderLayout.CENTER);

        // 하단 패널 (버튼들)
        JPanel bottomPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 40, 10, 40));

        // 준비 버튼
        b_ready = new JButton("준비");
        b_ready.setFont(new Font("Arial", Font.BOLD, 16));
        b_ready.addActionListener(e -> handleReady());
        bottomPanel.add(b_ready);

        // 준비 취소 버튼
        b_cancelReady = new JButton("준비 취소");
        b_cancelReady.setFont(new Font("Arial", Font.BOLD, 16));
        b_cancelReady.setEnabled(false);
        b_cancelReady.addActionListener(e -> handleCancelReady());
        bottomPanel.add(b_cancelReady);

        // 게임 시작 버튼
        b_startGame = new JButton("게임 시작");
        b_startGame.setFont(new Font("Arial", Font.BOLD, 16));
        b_startGame.setEnabled(false);
        b_startGame.addActionListener(e -> handleStartGame());
        bottomPanel.add(b_startGame);

        // 방 정보 변경 버튼
        JButton b_editRoom = new JButton("방 정보 변경");
        b_editRoom.setFont(new Font("Arial", Font.BOLD, 16));
        b_editRoom.addActionListener(e -> {
            if (!isRoomMaster) {
                JOptionPane.showMessageDialog(this,
                    "방장만 방 정보를 변경할 수 있습니다",
                    "권한 없음",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            showCreateRoomDialog(true);
        });
        bottomPanel.add(b_editRoom);

        // 방 나가기 버튼 (별도 패널)
        JPanel bottomWrapper = new JPanel(new BorderLayout());
        bottomWrapper.setOpaque(false);
        bottomWrapper.add(bottomPanel, BorderLayout.CENTER);

        JPanel leavePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        leavePanel.setOpaque(false);
        b_leaveRoom = new JButton("방 나가기");
        b_leaveRoom.setFont(new Font("Arial", Font.BOLD, 16));
        b_leaveRoom.setPreferredSize(new Dimension(150, 40));
        b_leaveRoom.addActionListener(e -> handleLeaveRoom());
        leavePanel.add(b_leaveRoom);
        bottomWrapper.add(leavePanel, BorderLayout.SOUTH);

        roomPanel.add(bottomWrapper, BorderLayout.SOUTH);

        return roomPanel;
    }

    private void updateRoomWaitingScreen() {
        // 방 제목 업데이트 (비공개 방이면 🔒 추가)
        String titleText = currentRoomName;
        if (currentRoomIsPrivate) {
            titleText += " 🔒";
        }
        l_roomTitle.setText(titleText);

        // 방 설정 정보 업데이트
        String settingsText = String.format(
            "%s | %s | %s",
            currentGameMode.getDisplayName(),
            currentDifficulty.getDisplayName(),
            currentTurnTimeLimit.getDisplayName()
        );
        if (currentRoomAllowSpectators) {
            settingsText += " | 관전 허용";
        }
        l_roomSettings.setText(settingsText);

        // 버튼 상태 업데이트
        if (isRoomMaster) {
            b_ready.setEnabled(false);
            b_cancelReady.setEnabled(false);
            b_startGame.setEnabled(true);
        } else {
            b_ready.setEnabled(true);
            b_cancelReady.setEnabled(false);
            b_startGame.setEnabled(false);
        }
    }

    private JPanel createResultPanel() {
        JPanel resultPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // l_resultMessage에 따라 배경 이미지 변경
                String resultText = l_resultMessage.getText();
                String imagePath = "src/image/intro.jpg"; // 기본 이미지
                if (resultText.contains("승리")) {
                    imagePath = "src/image/win.jpg";
                } else if (resultText.contains("패배")) {
                    imagePath = "src/image/lose.jpg";
                }
                ImageIcon background = new ImageIcon(imagePath);
                Image img = background.getImage();
                g.drawImage(img, 0, 0, getWidth(), getHeight(), this);
            }
        };
        resultPanel.setLayout(new BorderLayout());

        // 결과 메시지
        l_resultMessage = new JLabel("게임 결과", SwingConstants.CENTER);
        l_resultMessage.setFont(new Font("Arial", Font.BOLD, 48));
        l_resultMessage.setForeground(Color.YELLOW);
        resultPanel.add(l_resultMessage, BorderLayout.CENTER);

        // 하단 버튼 패널
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 20));
        bottomPanel.setOpaque(false);

        b_stayInRoom = new JButton("방에 남기");
        b_stayInRoom.setFont(new Font("Arial", Font.BOLD, 18));
        b_stayInRoom.setPreferredSize(new Dimension(150, 50));
        b_stayInRoom.addActionListener(e -> {
            // 방 대기 화면으로 전환
            switchToRoomWaitingScreen();
        });

        b_leaveToLobby = new JButton("로비로 나가기");
        b_leaveToLobby.setFont(new Font("Arial", Font.BOLD, 18));
        b_leaveToLobby.setPreferredSize(new Dimension(180, 50));
        b_leaveToLobby.addActionListener(e -> {
            handleLeaveRoom(); // 기존의 방 나가기 로직 재사용
        });

        bottomPanel.add(b_stayInRoom);
        bottomPanel.add(b_leaveToLobby);

        resultPanel.add(bottomPanel, BorderLayout.SOUTH);

        return resultPanel;
    }

    // ========== Screen Transition Methods ==========

    private void switchToLoginScreen() {
        currentState = UIState.LOGIN_SCREEN;
        SwingUtilities.invokeLater(() -> {
            cardLayout.show(mainPanel, LOGIN_PANEL);
        });
    }

    private void switchToLobbyScreen() {
        currentState = UIState.LOBBY_SCREEN;
        SwingUtilities.invokeLater(() -> {
            cardLayout.show(mainPanel, LOBBY_PANEL);
            // Request room list when entering lobby
            requestRoomList();
        });
    }

    private void switchToRoomWaitingScreen() {
        currentState = UIState.ROOM_WAITING_SCREEN;
        SwingUtilities.invokeLater(() -> {
            updateRoomWaitingScreen();
            cardLayout.show(mainPanel, ROOM_WAITING_PANEL);
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

    private void switchToResultScreen() {
        currentState = UIState.RESULT_SCREEN;
        SwingUtilities.invokeLater(() -> {
            cardLayout.show(mainPanel, RESULT_PANEL);
        });
    }

    // ========== Helper Methods ==========

    private void sendMessage(Message msg) {
        try {
            if (out != null) {
                out.writeObject(msg);
                out.flush();
            }
        } catch (IOException e) {
            System.err.println("Failed to send message: " + e.getMessage());
        }
    }

    // ========== Lobby Methods ==========

    private void requestRoomList() {
        Message msg = new Message(Message.MessageType.ROOM_LIST_REQUEST, currentUserId);
        sendMessage(msg);
    }

    private void handleJoinRoom() {
        int selectedRow = roomListTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this,
                "방을 선택해주세요",
                "선택 필요",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        int roomId = (Integer) roomListTableModel.getValueAt(selectedRow, 0);
        String roomName = (String) roomListTableModel.getValueAt(selectedRow, 1);
        String roomStatus = (String) roomListTableModel.getValueAt(selectedRow, 3);

        if ("게임 중".equals(roomStatus)) {
            JOptionPane.showMessageDialog(this,
                "게임 진행 중인 방은 입장할 수 없습니다",
                "입장 불가",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Check if room name has 🔒 indicating private room
        String password = null;
        if (roomName != null && roomName.contains("🔒")) {
            JPasswordField passwordField = new JPasswordField(15);
            int result = JOptionPane.showConfirmDialog(this,
                passwordField,
                "비공개 방입니다. 비밀번호를 입력하세요:",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

            if (result == JOptionPane.OK_OPTION) {
                password = new String(passwordField.getPassword());
                if (password.isEmpty()) {
                    JOptionPane.showMessageDialog(this,
                        "비밀번호를 입력해주세요",
                        "입력 필요",
                        JOptionPane.WARNING_MESSAGE);
                    return;
                }
            } else {
                return; // User cancelled
            }
        }

        Message joinMsg = Message.createJoinRoomRequest(currentUserId, roomId, password);
        sendMessage(joinMsg);
    }

    private void showCreateRoomDialog() {
        showCreateRoomDialog(false);
    }

    private void showCreateRoomDialog(boolean isEditMode) {
        // 다이얼로그 생성
        JDialog dialog = new JDialog(this, isEditMode ? "방 정보 변경" : "방 생성", true);
        dialog.setLayout(new BorderLayout());

        // 배경 패널 생성 (intro.jpg)
        JPanel backgroundPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                ImageIcon background = new ImageIcon("src/image/intro.jpg");
                Image img = background.getImage();
                g.drawImage(img, 0, 0, getWidth(), getHeight(), this);
            }
        };
        backgroundPanel.setLayout(new BoxLayout(backgroundPanel, BoxLayout.Y_AXIS));

        // 타이틀
        JLabel titleLabel = new JLabel(isEditMode ? "방 정보 변경" : "방 생성");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
        titleLabel.setForeground(Color.YELLOW);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 방 이름
        JPanel roomNamePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        roomNamePanel.setOpaque(false);
        JLabel l_roomName = new JLabel("방 이름:");
        l_roomName.setFont(new Font("Arial", Font.BOLD, 16));
        l_roomName.setForeground(Color.WHITE);
        JTextField t_roomName = new JTextField(20);
        t_roomName.setFont(new Font("Arial", Font.PLAIN, 14));
        if (isEditMode && currentRoomName != null) {
            t_roomName.setText(currentRoomName);
        }
        roomNamePanel.add(l_roomName);
        roomNamePanel.add(t_roomName);

        // 게임 모드
        JPanel gameModePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        gameModePanel.setOpaque(false);
        JLabel l_gameMode = new JLabel("게임 모드:");
        l_gameMode.setFont(new Font("Arial", Font.BOLD, 16));
        l_gameMode.setForeground(Color.WHITE);
        JComboBox<String> cb_gameMode = new JComboBox<>(new String[]{"1v1", "2v2"});
        cb_gameMode.setFont(new Font("Arial", Font.PLAIN, 14));
        if (isEditMode && currentGameMode != null) {
            cb_gameMode.setSelectedIndex(currentGameMode == Message.GameMode.ONE_VS_ONE ? 0 : 1);
        }
        gameModePanel.add(l_gameMode);
        gameModePanel.add(cb_gameMode);

        // 난이도
        JPanel difficultyPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        difficultyPanel.setOpaque(false);
        JLabel l_difficulty = new JLabel("난이도:");
        l_difficulty.setFont(new Font("Arial", Font.BOLD, 16));
        l_difficulty.setForeground(Color.WHITE);
        JComboBox<String> cb_difficulty = new JComboBox<>(new String[]{"하", "중", "상"});
        cb_difficulty.setFont(new Font("Arial", Font.PLAIN, 14));
        if (isEditMode && currentDifficulty != null) {
            cb_difficulty.setSelectedIndex(currentDifficulty.ordinal());
        }
        difficultyPanel.add(l_difficulty);
        difficultyPanel.add(cb_difficulty);

        // 턴 제한 시간
        JPanel turnTimeLimitPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        turnTimeLimitPanel.setOpaque(false);
        JLabel l_turnTimeLimit = new JLabel("턴 제한 시간:");
        l_turnTimeLimit.setFont(new Font("Arial", Font.BOLD, 16));
        l_turnTimeLimit.setForeground(Color.WHITE);
        JComboBox<String> cb_turnTimeLimit = new JComboBox<>(new String[]{"15초", "30초", "60초"});
        cb_turnTimeLimit.setFont(new Font("Arial", Font.PLAIN, 14));
        if (isEditMode && currentTurnTimeLimit != null) {
            int index = currentTurnTimeLimit == Message.TurnTimeLimit.FIFTEEN ? 0 :
                        currentTurnTimeLimit == Message.TurnTimeLimit.THIRTY ? 1 : 2;
            cb_turnTimeLimit.setSelectedIndex(index);
        }
        turnTimeLimitPanel.add(l_turnTimeLimit);
        turnTimeLimitPanel.add(cb_turnTimeLimit);

        // 비공개 방
        JPanel privateRoomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        privateRoomPanel.setOpaque(false);
        JCheckBox chk_isPrivate = new JCheckBox("비공개 방");
        chk_isPrivate.setFont(new Font("Arial", Font.BOLD, 16));
        chk_isPrivate.setForeground(Color.WHITE);
        chk_isPrivate.setOpaque(false);
        if (isEditMode) {
            chk_isPrivate.setSelected(currentRoomIsPrivate);
        }
        privateRoomPanel.add(chk_isPrivate);

        // 비밀번호
        JPanel passwordPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        passwordPanel.setOpaque(false);
        JLabel l_password = new JLabel("비밀번호:");
        l_password.setFont(new Font("Arial", Font.BOLD, 16));
        l_password.setForeground(Color.WHITE);
        JPasswordField t_password = new JPasswordField(20);
        t_password.setFont(new Font("Arial", Font.PLAIN, 14));
        t_password.setEnabled(chk_isPrivate.isSelected());
        if (isEditMode && currentRoomPassword != null) {
            t_password.setText(currentRoomPassword);
        }
        passwordPanel.add(l_password);
        passwordPanel.add(t_password);

        // 비공개 방 체크박스 리스너
        chk_isPrivate.addActionListener(e -> {
            t_password.setEnabled(chk_isPrivate.isSelected());
            if (!chk_isPrivate.isSelected()) {
                t_password.setText("");
            }
        });

        // 관전 허용
        JPanel spectatorPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        spectatorPanel.setOpaque(false);
        JCheckBox chk_allowSpectators = new JCheckBox("관전 허용");
        chk_allowSpectators.setFont(new Font("Arial", Font.BOLD, 16));
        chk_allowSpectators.setForeground(Color.WHITE);
        chk_allowSpectators.setOpaque(false);
        if (isEditMode) {
            chk_allowSpectators.setSelected(currentRoomAllowSpectators);
        }
        spectatorPanel.add(chk_allowSpectators);

        // 버튼 패널
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        buttonPanel.setOpaque(false);

        JButton b_confirm = new JButton(isEditMode ? "변경" : "생성");
        b_confirm.setFont(new Font("Arial", Font.BOLD, 16));
        b_confirm.setPreferredSize(new Dimension(100, 40));

        JButton b_cancel = new JButton("취소");
        b_cancel.setFont(new Font("Arial", Font.BOLD, 16));
        b_cancel.setPreferredSize(new Dimension(100, 40));
        b_cancel.addActionListener(e -> {
            setBounds(400, 100, 550, 800);
            dialog.dispose();
        });

        // 확인 버튼 액션
        b_confirm.addActionListener(e -> {
            // 입력 검증
            String roomName = t_roomName.getText().trim();
            if (roomName.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                    "방 이름을 입력해주세요",
                    "입력 필요",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }

            boolean isPrivate = chk_isPrivate.isSelected();
            String password = new String(t_password.getPassword()).trim();
            if (isPrivate && password.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                    "비공개 방은 비밀번호를 입력해야 합니다",
                    "입력 필요",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Enum 변환
            Message.GameMode gameMode = cb_gameMode.getSelectedIndex() == 0
                ? Message.GameMode.ONE_VS_ONE
                : Message.GameMode.TWO_VS_TWO;

            Message.Difficulty difficulty = Message.Difficulty.values()[cb_difficulty.getSelectedIndex()];

            Message.TurnTimeLimit turnTimeLimit =
                cb_turnTimeLimit.getSelectedIndex() == 0 ? Message.TurnTimeLimit.FIFTEEN :
                cb_turnTimeLimit.getSelectedIndex() == 1 ? Message.TurnTimeLimit.THIRTY :
                Message.TurnTimeLimit.SIXTY;

            boolean allowSpectators = chk_allowSpectators.isSelected();

            // 메시지 생성 및 전송
            Message msg = Message.createCreateRoomRequest(
                currentUserId,
                roomName,
                gameMode,
                difficulty,
                turnTimeLimit,
                isPrivate,
                isPrivate ? password : null,
                allowSpectators
            );

            // 수정 모드인 경우 roomId 설정
            if (isEditMode) {
                msg.setRoomId(currentRoomId);
            }

            sendMessage(msg);

            // 로컬 상태 업데이트 (수정 모드인 경우)
            if (isEditMode) {
                currentRoomIsPrivate = isPrivate;
                currentRoomPassword = isPrivate ? password : null;
                currentRoomAllowSpectators = allowSpectators;
                currentRoomName = roomName;
                currentGameMode = gameMode;
                currentDifficulty = difficulty;
                currentTurnTimeLimit = turnTimeLimit;
            }

            // 원래 크기로 복원 및 다이얼로그 닫기
            setBounds(400, 100, 550, 800);
            dialog.dispose();
        });

        buttonPanel.add(b_confirm);
        buttonPanel.add(b_cancel);

        // 모든 컴포넌트를 backgroundPanel에 추가
        backgroundPanel.add(Box.createRigidArea(new Dimension(0, 30)));
        backgroundPanel.add(titleLabel);
        backgroundPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        backgroundPanel.add(roomNamePanel);
        backgroundPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        backgroundPanel.add(gameModePanel);
        backgroundPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        backgroundPanel.add(difficultyPanel);
        backgroundPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        backgroundPanel.add(turnTimeLimitPanel);
        backgroundPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        backgroundPanel.add(privateRoomPanel);
        backgroundPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        backgroundPanel.add(passwordPanel);
        backgroundPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        backgroundPanel.add(spectatorPanel);
        backgroundPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        backgroundPanel.add(buttonPanel);
        backgroundPanel.add(Box.createVerticalGlue());

        dialog.add(backgroundPanel);

        // 윈도우 크기 변경
        setBounds(400, 100, 480, 700);

        // 다이얼로그 설정 및 표시
        dialog.setSize(480, 700);
        dialog.setLocationRelativeTo(this);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        // 다이얼로그가 닫힐 때 원래 크기로 복원
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                setBounds(400, 100, 550, 800);
            }
        });

        dialog.setVisible(true);
    }

    private void updateRoomListTable(Message msg) {
        // Clear existing rows
        roomListTableModel.setRowCount(0);

        // Get room list from message data
        if (msg.getData() != null && msg.getData() instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            java.util.List<Message> roomList = (java.util.List<Message>) msg.getData();

            for (Message roomInfo : roomList) {
                Object[] row = new Object[7];
                row[0] = roomInfo.getRoomId();
                row[1] = roomInfo.getRoomName();
                row[2] = roomInfo.getRoomMaster();
                row[3] = roomInfo.getRoomStatus() == Message.RoomStatus.WAITING ? "대기 중" : "게임 중";
                row[4] = roomInfo.getCurrentPlayers() + "/" + roomInfo.getMaxPlayers();
                row[5] = roomInfo.getGameMode() != null ? roomInfo.getGameMode().getDisplayName() : "";
                row[6] = roomInfo.getDifficulty() != null ? roomInfo.getDifficulty().getDisplayName() : "";
                roomListTableModel.addRow(row);
            }
        }
    }

    // ========== Room Waiting Helper Methods ==========

    private void handleReady() {
        Message msg = new Message(Message.MessageType.READY, currentUserId);
        msg.setRoomId(currentRoomId);
        sendMessage(msg);

        b_ready.setEnabled(false);
        b_cancelReady.setEnabled(true);
    }

    private void handleCancelReady() {
        Message msg = new Message(Message.MessageType.READY_CANCEL, currentUserId);
        msg.setRoomId(currentRoomId);
        sendMessage(msg);

        b_ready.setEnabled(true);
        b_cancelReady.setEnabled(false);
    }

    private void handleStartGame() {
        if (!isRoomMaster) {
            showToast("방장만 게임을 시작할 수 있습니다");
            return;
        }

        Message msg = new Message(Message.MessageType.START_GAME_REQUEST, currentUserId);
        msg.setRoomId(currentRoomId);
        sendMessage(msg);
    }

    private void handleLeaveRoom() {
        Message msg = new Message(Message.MessageType.LEAVE_ROOM, currentUserId);
        msg.setRoomId(currentRoomId);
        sendMessage(msg);

        // 로컬 상태 초기화
        currentRoomId = null;
        currentRoomName = null;
        roomMasterUserId = null;
        isRoomMaster = false;
        currentGameMode = null;
        currentDifficulty = null;
        currentTurnTimeLimit = null;
        currentRoomIsPrivate = false;
        currentRoomPassword = null;
        currentRoomAllowSpectators = false;
        roomPlayersList.clear();
        playerReadyStatus.clear();

        switchToLobbyScreen();
    }

    private void resetClientState() {
        isAuthenticated = false;
        currentUserId = null;
        currentPassword = null;
        currentRoomId = null;
        currentRoomName = null;
        roomMasterUserId = null;
        isRoomMaster = false;
        currentGameId = null;
        roomPlayersList.clear();
        playerReadyStatus.clear();
        currentUserStatus = Message.UserStatus.OFFLINE;
    }

    // ========== Custom Cell Renderer for Player List ==========
    class PlayerListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            String playerId = (String) value;
            boolean isReady = playerReadyStatus.getOrDefault(playerId, false);
            boolean isHost = playerId.equals(roomMasterUserId);

            // "PlayerID [Team A] (Ready)"
            String displayText = playerId;

            // 내가 누구인지 표시
            if (playerId.equals(currentUserId)) {
                displayText += " (Me)";
            }

            // 방장 표시
            if (isHost) {
                displayText += " (Host)";
                setForeground(Color.ORANGE);
            } else {
                // 준비 상태 표시
                if (isReady) {
                    displayText += " [Ready]";
                    setForeground(Color.GREEN);
                } else {
                    setForeground(Color.WHITE);
                }
            }
            
            setText(displayText);
            setOpaque(isSelected);
            setBackground(isSelected ? Color.DARK_GRAY : new Color(0,0,0,0));

            return this;
        }
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