package server;

import javax.swing.*;
import java.awt.*;

// 서버 GUI 메인 클래스

public class BaseballServerGUI extends JFrame {
    private int port = 54321;

    private JTextArea t_display;
    private JButton b_start, b_stop;

    private ServerCore serverCore;

    public BaseballServerGUI(int port) {
        super("Baseball Game Server");
        this.port = port;

        // ServerCore 초기화
        serverCore = new ServerCore(port, this::printDisplay);

        buildGUI();
        setSize(700, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);
    }

    /**
     * GUI 구성
     */
    private void buildGUI() {
        // 로그 출력 영역
        t_display = new JTextArea();
        t_display.setEditable(false);
        add(new JScrollPane(t_display), BorderLayout.CENTER);

        // 버튼 패널
        JPanel btnPanel = new JPanel();
        b_start = new JButton("서버 시작");
        b_stop = new JButton("서버 중지");
        b_stop.setEnabled(false);

        b_start.addActionListener(e -> {
            serverCore.startServer();
            SwingUtilities.invokeLater(() -> {
                b_start.setEnabled(false);
                b_stop.setEnabled(true);
            });
        });

        b_stop.addActionListener(e -> {
            serverCore.stopServer();
            SwingUtilities.invokeLater(() -> {
                b_stop.setEnabled(false);
                b_start.setEnabled(true);
            });
        });

        btnPanel.add(b_start);
        btnPanel.add(b_stop);
        add(btnPanel, BorderLayout.SOUTH);
    }

    /**
     * 로그 출력
     */
    private void printDisplay(String msg) {
        SwingUtilities.invokeLater(() -> {
            t_display.append(msg + "\n");
            t_display.setCaretPosition(t_display.getDocument().getLength());
        });
    }

    /**
     * 메인 메서드
     */
    public static void main(String[] args) {
        new BaseballServerGUI(54321);
    }
}