package client.ui;

import common.Message;
import client.util.UIHelper;
import client.state.GameStateManager;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.util.List;

/**
 * Lobby screen showing room list, chat, and user list
 */
public class LobbyPanel extends JPanel {
    private JTable roomListTable;
    private DefaultTableModel roomListTableModel;
    private JButton b_createRoom, b_joinRoom, b_refreshRoomList;
    private LobbyListener listener;

    // 채팅 관련 컴포넌트
    private JTextPane t_chatDisplay;
    private JTextField t_chatInput;
    private JButton b_sendChat;

    // 접속자 목록 관련 컴포폰
    private JList<String> userList;
    private DefaultListModel<String> userListModel;

    // User ID label
    private JLabel userIdLabel;

    public LobbyPanel(LobbyListener listener) {
        this.listener = listener;
        buildUI();
    }

    private void buildUI() {
        setLayout(new BorderLayout());

        // Main vertical container
        JPanel mainContainer = new JPanel();
        mainContainer.setLayout(new BoxLayout(mainContainer, BoxLayout.Y_AXIS));
        mainContainer.setOpaque(false);

        // ===========================================
        // 1. Room List Section (60% of height)
        // ===========================================
        JPanel roomListSection = new JPanel(new BorderLayout());
        roomListSection.setOpaque(false);
        roomListSection.setPreferredSize(new Dimension(800, 360)); // 60% of assumed 600px height

        // Title container with user ID
        JPanel titleContainer = new JPanel(new BorderLayout());
        titleContainer.setOpaque(false);
        titleContainer.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        JLabel titleLabel = new JLabel("Lobby", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
        titleLabel.setForeground(Color.YELLOW);
        titleContainer.add(titleLabel, BorderLayout.CENTER);

        // User ID display
        userIdLabel = new JLabel("User: " + GameStateManager.getInstance().getCurrentUserId());
        userIdLabel.setFont(new Font("Arial", Font.BOLD, 14));
        userIdLabel.setForeground(new Color(0, 180, 0)); // Green
        titleContainer.add(userIdLabel, BorderLayout.EAST);

        roomListSection.add(titleContainer, BorderLayout.NORTH);

        // Room list table
        JPanel roomListPanel = new JPanel(new BorderLayout());
        roomListPanel.setOpaque(false);
        roomListPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 10, 20));

        String[] columnNames = {"방 번호", "방 이름", "방장", "상태", "인원", "모드", "난이도"};
        roomListTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        roomListTable = new JTable(roomListTableModel);
        roomListTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomListTable.getTableHeader().setReorderingAllowed(false);
        roomListTable.setFont(new Font("Arial", Font.PLAIN, 14));
        roomListTable.setRowHeight(25);

        JScrollPane roomScrollPane = new JScrollPane(roomListTable);
        roomScrollPane.setOpaque(false);
        roomScrollPane.getViewport().setOpaque(false);
        roomListPanel.add(roomScrollPane, BorderLayout.CENTER);

        roomListSection.add(roomListPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        buttonPanel.setOpaque(false);

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
        b_refreshRoomList.addActionListener(e -> listener.onRefreshRequested());

        buttonPanel.add(b_createRoom);
        buttonPanel.add(b_joinRoom);
        buttonPanel.add(b_refreshRoomList);

        roomListSection.add(buttonPanel, BorderLayout.SOUTH);

        // ===========================================
        // 2. Chat Section (30% of height)
        // ===========================================
        JPanel chatSection = new JPanel(new BorderLayout());
        chatSection.setOpaque(false);
        chatSection.setPreferredSize(new Dimension(800, 180)); // 30% of assumed 600px height
        chatSection.setBorder(BorderFactory.createEmptyBorder(5, 20, 5, 20));

        // Chat title
        JLabel chatTitleLabel = new JLabel("로비 채팅", SwingConstants.LEFT);
        chatTitleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        chatTitleLabel.setForeground(Color.YELLOW);
        chatTitleLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        chatSection.add(chatTitleLabel, BorderLayout.NORTH);

        // Chat display area
        t_chatDisplay = new JTextPane();
        t_chatDisplay.setEditable(false);
        t_chatDisplay.setFont(new Font("Arial", Font.PLAIN, 15));
        t_chatDisplay.setOpaque(true);
        JScrollPane chatScrollPane = new JScrollPane(t_chatDisplay);
        chatScrollPane.setOpaque(false);
        chatScrollPane.getViewport().setOpaque(false);
        chatScrollPane.setPreferredSize(new Dimension(760, 120));
        chatSection.add(chatScrollPane, BorderLayout.CENTER);

        // Chat input panel
        JPanel chatInputPanel = new JPanel(new BorderLayout(5, 0));
        chatInputPanel.setOpaque(false);
        chatInputPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

        t_chatInput = new JTextField();
        t_chatInput.setFont(new Font("Arial", Font.PLAIN, 14));
        t_chatInput.addActionListener(e -> sendChatMessage());

        b_sendChat = new JButton("전송");
        b_sendChat.setFont(new Font("Arial", Font.BOLD, 14));
        b_sendChat.setPreferredSize(new Dimension(80, 30));
        b_sendChat.addActionListener(e -> sendChatMessage());

        chatInputPanel.add(t_chatInput, BorderLayout.CENTER);
        chatInputPanel.add(b_sendChat, BorderLayout.EAST);

        chatSection.add(chatInputPanel, BorderLayout.SOUTH);

        // ===========================================
        // 3. User List Section (10% of height)
        // ===========================================
        JPanel userListSection = new JPanel(new BorderLayout());
        userListSection.setOpaque(false);
        userListSection.setPreferredSize(new Dimension(800, 60)); // 10% of assumed 600px height
        userListSection.setBorder(BorderFactory.createEmptyBorder(5, 20, 10, 20));

        // User list title
        JLabel userListTitleLabel = new JLabel("접속자", SwingConstants.LEFT);
        userListTitleLabel.setFont(new Font("Arial", Font.BOLD, 14));
        userListTitleLabel.setForeground(Color.YELLOW);
        userListSection.add(userListTitleLabel, BorderLayout.NORTH);

        // User list
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setCellRenderer(new UserListCellRenderer()); // Set custom renderer
        userList.setFont(new Font("Arial", Font.PLAIN, 13));
        userList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        userList.setVisibleRowCount(4);
        userList.setOpaque(false); // Make list transparent
        userList.setBackground(new Color(0,0,0,0)); // Transparent background

        JScrollPane userScrollPane = new JScrollPane(userList);
        userScrollPane.setOpaque(false); // Make scroll pane transparent
        userScrollPane.getViewport().setOpaque(false); // Make viewport transparent
        userScrollPane.setPreferredSize(new Dimension(760, 40));
        userScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        userScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        userListSection.add(userScrollPane, BorderLayout.CENTER);

        // Add all sections to main container
        mainContainer.add(roomListSection);
        mainContainer.add(chatSection);
        mainContainer.add(userListSection);

        add(mainContainer, BorderLayout.CENTER);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        ImageIcon background = new ImageIcon("src/image/intro.jpg");
        Image img = background.getImage();
        g.drawImage(img, 0, 0, getWidth(), getHeight(), this);
    }

    /**
     * Update room list table with new data
     * @param roomList List of room information messages
     */
    public void updateRoomList(List<Message> roomList) {
        // Clear existing rows
        roomListTableModel.setRowCount(0);

        // Add new rows
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

        // Check if room is private (has 🔒 in name)
        String password = null;
        if (roomName != null && roomName.contains("비공개")) {
            password = UIHelper.showPasswordDialog(this, "비공개 방입니다. 비밀번호를 입력하세요:");
            if (password == null) {
                return; // User cancelled
            }
        }

        listener.onJoinRoomRequested(roomId, password);
    }

    private void showCreateRoomDialog() {
        showCreateRoomDialog(false, null);
    }

    /**
     * Send chat message to lobby
     */
    private void sendChatMessage() {
        String message = t_chatInput.getText().trim();
        if (!message.isEmpty()) {
            listener.onLobbyChatSent(message);
            t_chatInput.setText("");
        }
    }

    public void addChatMessage(String message, Color color) {
        StyledDocument doc = t_chatDisplay.getStyledDocument();
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setForeground(attrs, color);

        try {
            doc.insertString(doc.getLength(), message + "\n", attrs);
        } catch (BadLocationException e) {
            System.err.println("Error appending message: " + e.getMessage());
        }
        t_chatDisplay.setCaretPosition(doc.getLength());
    }

    /**
     * Add a chat message to the display
     * @param userName User who sent the message
     * @param message Message content
     */
    public void addChatMessage(String userName, String message) {
        addChatMessage(String.format("%s: %s", userName, message), new Color(50, 50, 50)); // Dark gray
    }

    /**
     * Update the user list
     * @param users List of user names with their status
     */
    public void updateUserList(java.util.List<String> users) {
        userListModel.clear();
        for (String user : users) {
            userListModel.addElement(user);
        }
    }

    /**
     * Show create/edit room dialog
     * @param isEditMode Whether this is edit mode (not create)
     * @param currentSettings Current room settings (for edit mode)
     */
    public void showCreateRoomDialog(boolean isEditMode, Message currentSettings) {
        // Create dialog
        JFrame parentFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(parentFrame, isEditMode ? "방 정보 변경" : "방 생성", true);
        dialog.setLayout(new BorderLayout());

        // Background panel
        JPanel backgroundPanel = UIHelper.createBackgroundPanel("src/image/intro.jpg");
        backgroundPanel.setLayout(new BoxLayout(backgroundPanel, BoxLayout.Y_AXIS));

        // Title
        JLabel titleLabel = new JLabel(isEditMode ? "방 정보 변경" : "방 생성");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
        titleLabel.setForeground(Color.YELLOW);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Room name
        JPanel roomNamePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        roomNamePanel.setOpaque(false);
        JLabel l_roomName = new JLabel("방 이름:");
        l_roomName.setFont(new Font("Arial", Font.BOLD, 16));
        l_roomName.setForeground(Color.WHITE);
        JTextField t_roomName = new JTextField(20);
        t_roomName.setFont(new Font("Arial", Font.PLAIN, 14));
        if (isEditMode && currentSettings != null) {
            t_roomName.setText(currentSettings.getRoomName());
        }
        roomNamePanel.add(l_roomName);
        roomNamePanel.add(t_roomName);

        // Game mode
        JPanel gameModePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        gameModePanel.setOpaque(false);
        JLabel l_gameMode = new JLabel("게임 모드:");
        l_gameMode.setFont(new Font("Arial", Font.BOLD, 16));
        l_gameMode.setForeground(Color.WHITE);
        JComboBox<String> cb_gameMode = new JComboBox<>(new String[]{"1v1", "2v2"});
        cb_gameMode.setFont(new Font("Arial", Font.PLAIN, 14));
        if (isEditMode && currentSettings != null && currentSettings.getGameMode() != null) {
            cb_gameMode.setSelectedIndex(currentSettings.getGameMode() == Message.GameMode.ONE_VS_ONE ? 0 : 1);
        }
        if (isEditMode) {
            cb_gameMode.setEnabled(false);
        }
        gameModePanel.add(l_gameMode);
        gameModePanel.add(cb_gameMode);

        // Difficulty
        JPanel difficultyPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        difficultyPanel.setOpaque(false);
        JLabel l_difficulty = new JLabel("난이도:");
        l_difficulty.setFont(new Font("Arial", Font.BOLD, 16));
        l_difficulty.setForeground(Color.WHITE);
        JComboBox<String> cb_difficulty = new JComboBox<>(new String[]{"하", "중", "상"});
        cb_difficulty.setFont(new Font("Arial", Font.PLAIN, 14));
        if (isEditMode && currentSettings != null && currentSettings.getDifficulty() != null) {
            cb_difficulty.setSelectedIndex(currentSettings.getDifficulty().ordinal());
        }
        difficultyPanel.add(l_difficulty);
        difficultyPanel.add(cb_difficulty);

        // Turn time limit
        JPanel turnTimeLimitPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        turnTimeLimitPanel.setOpaque(false);
        JLabel l_turnTimeLimit = new JLabel("턴 제한 시간:");
        l_turnTimeLimit.setFont(new Font("Arial", Font.BOLD, 16));
        l_turnTimeLimit.setForeground(Color.WHITE);
        JComboBox<String> cb_turnTimeLimit = new JComboBox<>(new String[]{"15초", "30초", "60초"});
        cb_turnTimeLimit.setFont(new Font("Arial", Font.PLAIN, 14));
        if (isEditMode && currentSettings != null && currentSettings.getTurnTimeLimit() != null) {
            int index = currentSettings.getTurnTimeLimit() == Message.TurnTimeLimit.FIFTEEN ? 0 :
                        currentSettings.getTurnTimeLimit() == Message.TurnTimeLimit.THIRTY ? 1 : 2;
            cb_turnTimeLimit.setSelectedIndex(index);
        }
        turnTimeLimitPanel.add(l_turnTimeLimit);
        turnTimeLimitPanel.add(cb_turnTimeLimit);

        // Private room checkbox
        JPanel privateRoomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        privateRoomPanel.setOpaque(false);
        JCheckBox chk_isPrivate = new JCheckBox("비공개 방");
        chk_isPrivate.setFont(new Font("Arial", Font.BOLD, 16));
        chk_isPrivate.setForeground(Color.WHITE);
        chk_isPrivate.setOpaque(false);
        if (isEditMode && currentSettings != null) {
            chk_isPrivate.setSelected(currentSettings.isPrivate());
        }
        privateRoomPanel.add(chk_isPrivate);

        // Password
        JPanel passwordPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        passwordPanel.setOpaque(false);
        JLabel l_password = new JLabel("비밀번호:");
        l_password.setFont(new Font("Arial", Font.BOLD, 16));
        l_password.setForeground(Color.WHITE);
        JPasswordField t_password = new JPasswordField(20);
        t_password.setFont(new Font("Arial", Font.PLAIN, 14));
        t_password.setEnabled(chk_isPrivate.isSelected());
        if (isEditMode && currentSettings != null && currentSettings.getRoomPassword() != null) {
            t_password.setText(currentSettings.getRoomPassword());
        }
        passwordPanel.add(l_password);
        passwordPanel.add(t_password);

        // Private checkbox listener
        chk_isPrivate.addActionListener(e -> {
            t_password.setEnabled(chk_isPrivate.isSelected());
            if (!chk_isPrivate.isSelected()) {
                t_password.setText("");
            }
        });

        // Allow spectators
        JPanel spectatorPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        spectatorPanel.setOpaque(false);
        JCheckBox chk_allowSpectators = new JCheckBox("관전 허용");
        chk_allowSpectators.setFont(new Font("Arial", Font.BOLD, 16));
        chk_allowSpectators.setForeground(Color.WHITE);
        chk_allowSpectators.setOpaque(false);
        if (isEditMode && currentSettings != null) {
            chk_allowSpectators.setSelected(currentSettings.isAllowSpectators());
        }
        spectatorPanel.add(chk_allowSpectators);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        buttonPanel.setOpaque(false);

        JButton b_confirm = new JButton(isEditMode ? "변경" : "생성");
        b_confirm.setFont(new Font("Arial", Font.BOLD, 16));
        b_confirm.setPreferredSize(new Dimension(100, 40));

        JButton b_cancel = new JButton("취소");
        b_cancel.setFont(new Font("Arial", Font.BOLD, 16));
        b_cancel.setPreferredSize(new Dimension(100, 40));
        b_cancel.addActionListener(e -> dialog.dispose());

        // Confirm button action
        b_confirm.addActionListener(e -> {
            // Validate input
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

            // Convert to enums
            Message.GameMode gameMode = cb_gameMode.getSelectedIndex() == 0
                ? Message.GameMode.ONE_VS_ONE
                : Message.GameMode.TWO_VS_TWO;

            Message.Difficulty difficulty = Message.Difficulty.values()[cb_difficulty.getSelectedIndex()];

            Message.TurnTimeLimit turnTimeLimit =
                cb_turnTimeLimit.getSelectedIndex() == 0 ? Message.TurnTimeLimit.FIFTEEN :
                cb_turnTimeLimit.getSelectedIndex() == 1 ? Message.TurnTimeLimit.THIRTY :
                Message.TurnTimeLimit.SIXTY;

            boolean allowSpectators = chk_allowSpectators.isSelected();

            // Notify listener
            if (isEditMode) {
                // 방 정보 변경 (게임 모드와 관전 허용은 변경 불가)
                listener.onEditRoomConfirmed(
                    roomName,
                    difficulty,
                    turnTimeLimit,
                    isPrivate,
                    isPrivate ? password : null
                );
            } else {
                // 방 생성
                listener.onCreateRoomRequested(
                    roomName,
                    gameMode,
                    difficulty,
                    turnTimeLimit,
                    isPrivate,
                    isPrivate ? password : null,
                    allowSpectators
                );
            }

            dialog.dispose();
        });

        buttonPanel.add(b_confirm);
        buttonPanel.add(b_cancel);

        // Add all components
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
        dialog.setSize(480, 700);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    /**
     * Update user ID display
     */
    public void updateUserInfo() {
        String userId = GameStateManager.getInstance().getCurrentUserId();
        userIdLabel.setText("User: " + (userId != null ? userId : "null"));
    }
}
