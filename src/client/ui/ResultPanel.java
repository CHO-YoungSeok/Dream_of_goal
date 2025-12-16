package client.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Game result display screen
 * Shows win/lose/draw with appropriate background
 */
public class ResultPanel extends JPanel {
    private JLabel l_resultMessage;
    private JButton b_stayInRoom, b_leaveToLobby;
    private ResultPanelListener listener;

    public ResultPanel(ResultPanelListener listener) {
        this.listener = listener;
        buildUI();
    }

    private void buildUI() {
        setLayout(new BorderLayout());

        // 결과 메시지
        l_resultMessage = new JLabel("게임 결과", SwingConstants.CENTER);
        l_resultMessage.setFont(new Font("Dialog", Font.BOLD, 48));
        l_resultMessage.setForeground(Color.YELLOW);
        add(l_resultMessage, BorderLayout.CENTER);

        // 하단 버튼 패널
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 20));
        bottomPanel.setOpaque(false);

        b_stayInRoom = new JButton("방에 남기");
        b_stayInRoom.setFont(new Font("Dialog", Font.BOLD, 18));
        b_stayInRoom.setPreferredSize(new Dimension(150, 50));
        b_stayInRoom.addActionListener(e -> listener.onStayInRoom());

        b_leaveToLobby = new JButton("로비로 나가기");
        b_leaveToLobby.setFont(new Font("Dialog", Font.BOLD, 18));
        b_leaveToLobby.setPreferredSize(new Dimension(180, 50));
        b_leaveToLobby.addActionListener(e -> listener.onLeaveToLobby());

        bottomPanel.add(b_stayInRoom);
        bottomPanel.add(b_leaveToLobby);

        add(bottomPanel, BorderLayout.SOUTH);
    }

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

    /**
     * Set the result message
     * @param result Result text (e.g., "승리!", "패배", "무승부")
     */
    public void setResult(String result) {
        l_resultMessage.setText(result);
        repaint(); // Triggers background change
    }
}
