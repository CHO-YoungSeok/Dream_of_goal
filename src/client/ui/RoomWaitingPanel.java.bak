package client.ui;

import client.state.GameStateManager;
import javax.swing.*;
import java.awt.*;

/**
 * Room waiting screen with ready system
 */
public class RoomWaitingPanel extends JPanel {
    private JLabel l_roomTitle, l_roomSettings;
    private DefaultListModel<String> roomPlayerListModel;
    private JList<String> roomPlayerList;
    private JButton b_ready, b_cancelReady, b_startGame, b_leaveRoom;
    private RoomWaitingListener listener;
    private GameStateManager stateManager;

    public RoomWaitingPanel(RoomWaitingListener listener, GameStateManager stateManager) {
        this.listener = listener;
        this.stateManager = stateManager;
        buildUI();
    }

    private void buildUI() {
        setLayout(new BorderLayout());

        // Top panel (room info)
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setOpaque(false);
        topPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));

        // Room title
        l_roomTitle = new JLabel("", SwingConstants.CENTER);
        l_roomTitle.setFont(new Font("Arial", Font.BOLD, 24));
        l_roomTitle.setForeground(Color.YELLOW);
        l_roomTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        topPanel.add(l_roomTitle);

        topPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Room settings info
        l_roomSettings = new JLabel("", SwingConstants.CENTER);
        l_roomSettings.setFont(new Font("Arial", Font.PLAIN, 16));
        l_roomSettings.setForeground(Color.WHITE);
        l_roomSettings.setAlignmentX(Component.CENTER_ALIGNMENT);
        topPanel.add(l_roomSettings);

        add(topPanel, BorderLayout.NORTH);

        // Center panel (player list)
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 40, 10, 40));

        JLabel l_players = new JLabel("플레이어 목록");
        l_players.setFont(new Font("Arial", Font.BOLD, 18));
        l_players.setForeground(Color.WHITE);
        centerPanel.add(l_players, BorderLayout.NORTH);

        // Player list
        roomPlayerListModel = new DefaultListModel<>();
        roomPlayerList = new JList<>(roomPlayerListModel);
        roomPlayerList.setCellRenderer(new PlayerListCellRenderer(stateManager));
        roomPlayerList.setFont(new Font("Arial", Font.PLAIN, 16));
        roomPlayerList.setOpaque(false);
        roomPlayerList.setBackground(new Color(0, 0, 0, 100));
        roomPlayerList.setForeground(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(roomPlayerList);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        // Bottom panel (buttons)
        JPanel bottomPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 40, 10, 40));

        // Ready button
        b_ready = new JButton("준비");
        b_ready.setFont(new Font("Arial", Font.BOLD, 16));
        b_ready.addActionListener(e -> handleReady());
        bottomPanel.add(b_ready);

        // Cancel ready button
        b_cancelReady = new JButton("준비 취소");
        b_cancelReady.setFont(new Font("Arial", Font.BOLD, 16));
        b_cancelReady.setEnabled(false);
        b_cancelReady.addActionListener(e -> handleCancelReady());
        bottomPanel.add(b_cancelReady);

        // Start game button
        b_startGame = new JButton("게임 시작");
        b_startGame.setFont(new Font("Arial", Font.BOLD, 16));
        b_startGame.setEnabled(false);
        b_startGame.addActionListener(e -> listener.onStartGameRequested());
        bottomPanel.add(b_startGame);

        // Edit room button
        JButton b_editRoom = new JButton("방 정보 변경");
        b_editRoom.setFont(new Font("Arial", Font.BOLD, 16));
        b_editRoom.addActionListener(e -> {
            if (!stateManager.isRoomMaster()) {
                JOptionPane.showMessageDialog(this,
                    "방장만 방 정보를 변경할 수 있습니다",
                    "권한 없음",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            listener.onEditRoomRequested();
        });
        bottomPanel.add(b_editRoom);

        // Bottom wrapper with leave button
        JPanel bottomWrapper = new JPanel(new BorderLayout());
        bottomWrapper.setOpaque(false);
        bottomWrapper.add(bottomPanel, BorderLayout.CENTER);

        JPanel leavePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        leavePanel.setOpaque(false);
        b_leaveRoom = new JButton("방 나가기");
        b_leaveRoom.setFont(new Font("Arial", Font.BOLD, 16));
        b_leaveRoom.setPreferredSize(new Dimension(150, 40));
        b_leaveRoom.addActionListener(e -> listener.onLeaveRoomRequested());
        leavePanel.add(b_leaveRoom);
        bottomWrapper.add(leavePanel, BorderLayout.SOUTH);

        add(bottomWrapper, BorderLayout.SOUTH);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        ImageIcon background = new ImageIcon("src/image/intro.jpg");
        Image img = background.getImage();
        g.drawImage(img, 0, 0, getWidth(), getHeight(), this);
    }

    /**
     * Update room information display
     */
    public void updateRoomInfo() {
        // Room title (with lock icon if private)
        String titleText = stateManager.getCurrentRoomName();
        if (stateManager.isCurrentRoomIsPrivate()) {
            titleText += " 🔒";
        }
        l_roomTitle.setText(titleText);

        // Room settings
        String settingsText = String.format(
            "%s | %s | %s",
            stateManager.getCurrentGameMode() != null ? stateManager.getCurrentGameMode().getDisplayName() : "",
            stateManager.getCurrentDifficulty() != null ? stateManager.getCurrentDifficulty().getDisplayName() : "",
            stateManager.getCurrentTurnTimeLimit() != null ? stateManager.getCurrentTurnTimeLimit().getDisplayName() : ""
        );
        if (stateManager.isCurrentRoomAllowSpectators()) {
            settingsText += " | 관전 허용";
        }
        l_roomSettings.setText(settingsText);

        // Button states
        if (stateManager.isRoomMaster()) {
            b_ready.setEnabled(false);
            b_cancelReady.setEnabled(false);
            b_startGame.setEnabled(true);
        } else {
            b_ready.setEnabled(true);
            b_cancelReady.setEnabled(false);
            b_startGame.setEnabled(false);
        }
    }

    /**
     * Update player list
     */
    public void updatePlayerList() {
        roomPlayerListModel.clear();
        for (String player : stateManager.getRoomPlayersList()) {
            roomPlayerListModel.addElement(player);
        }
        roomPlayerList.repaint(); // Trigger renderer update
    }

    private void handleReady() {
        listener.onReadyRequested();
        b_ready.setEnabled(false);
        b_cancelReady.setEnabled(true);
    }

    private void handleCancelReady() {
        listener.onCancelReadyRequested();
        b_ready.setEnabled(true);
        b_cancelReady.setEnabled(false);
    }
}
