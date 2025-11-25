import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class BaseballClientGUI extends JFrame {

    JTextArea t_display;
    JTextField t_input;
    JButton b_connect;
    JButton b_disconnect;
    JButton b_send;
    JButton b_exit;
    Socket socket;
    ObjectOutputStream out;
    ObjectInputStream in;
    JTextField t_userID;
    JTextField t_serverAddress;
    JTextField t_serverPort;
    JTextField t_answerKey;
    JLabel[] selectedNumbers;
    int currentPosition = 0;
    JButton b_clear;

    void buildGUI() {
        setLayout(new BorderLayout());
        add(createDisplay(), BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new GridLayout(0, 1));
//        bottomPanel.add(createInfoPanel());
//        bottomPanel.add(createInputPanel());
        return bottomPanel;
    }

    JPanel createInfoPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        JLabel l_userID = new JLabel("아이디: ");
        JLabel l_serverAddress = new JLabel("서버 주소: ");
        JLabel l_serverPort = new JLabel("포트번호: ");
        JLabel l_answerKey = new JLabel("answerKey: ");
        try {
            InetAddress local  = InetAddress.getLocalHost();
            String addr = local.getHostAddress();
            String[] part = addr.split("\\.");
            System.out.println("local: " + local + "");
            System.out.println("addr: " + addr + "");
            t_userID = new JTextField("guest" + part[3], 6);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        t_serverAddress = new JTextField("localhost", 6);
        t_serverPort = new JTextField("54321", 6);
        t_answerKey = new JTextField("1357", 6);

        panel.add(l_userID);
        panel.add(t_userID);
        panel.add(l_serverAddress);
        panel.add(t_serverAddress);
        panel.add(l_serverPort);
        panel.add(t_serverPort);
        panel.add(l_answerKey);
        panel.add(t_answerKey);
        return panel;
    }

    JPanel createDisplay() {
        JPanel displayPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                ImageIcon background = new ImageIcon("src/image/baseball_field.jpg");
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

        b_clear = new JButton("Clear");
        b_clear.setFont(new Font("Arial", Font.BOLD, 14));
        b_clear.setPreferredSize(new Dimension(70, 50));
        b_clear.addActionListener(e -> resetNumberSelection());
        numberDisplayPanel.add(b_clear);

        displayPanel.add(numberDisplayPanel);

        // 1-5. 40~50%: 배경을 보여주는 공간
        JPanel spacer2 = new JPanel();
        spacer2.setOpaque(false);
        spacer2.setPreferredSize(new Dimension(600, 50));
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

    private void onNumberClick(int number) {
        if (currentPosition < 3) {
            selectedNumbers[currentPosition].setText(String.valueOf(number));
            currentPosition++;

            if (currentPosition == 3) {
                String guessNumber = "";
                for (int i = 0; i < 3; i++) {
                    guessNumber += selectedNumbers[i].getText();
                }
                t_input.setText(guessNumber);
            }
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
                displayMessage(message.toString());
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.out.println("ClassNotFoundException: " + e.getMessage());
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

        // 숫자 선택 초기화
        resetNumberSelection();
    }

    private void resetNumberSelection() {
        currentPosition = 0;
        for (int i = 0; i < 3; i++) {
            selectedNumbers[i].setText("_");
        }
    }

    private void displayMessage(String message) {
        t_display.append(message + "\n");
        t_display.setCaretPosition(t_display.getDocument().getLength());
    }

    JPanel createControlPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(1, 3));
        b_connect = new JButton("Connect");
        b_disconnect = new JButton("Disconnect");
        b_exit = new JButton("Exit");
        b_connect.setEnabled(true);
        b_disconnect.setEnabled(false);
        b_exit.setEnabled(true);

        b_connect.addActionListener(e -> {
            connectToServer();
            b_connect.setEnabled(false);
            b_disconnect.setEnabled(true);
            b_exit.setEnabled(false);
            b_send.setEnabled(true);
            t_input.setEnabled(true);
            t_input.requestFocusInWindow();
            t_userID.setEnabled(false);
            t_serverAddress.setEnabled(false);
            t_serverPort.setEnabled(false);
            t_answerKey.setEnabled(false);
        });

        b_disconnect.addActionListener(e -> {
            disconnect();
            b_connect.setEnabled(true);
            b_disconnect.setEnabled(false);
            b_exit.setEnabled(true);
            b_send.setEnabled(false);
            t_input.setEnabled(false);
            t_input.setText("");
            t_display.setCaretPosition(t_display.getDocument().getLength());
        });

        b_exit.addActionListener(e -> System.exit(0));
        panel.add(b_connect);
        panel.add(b_disconnect);
        panel.add(b_exit);
        return panel;
    }

    private void disconnect() {
        try {
            socket.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        displayMessage("Disconnected from server: " + socket.getInetAddress() + "\n\n");
    }

    private void connectToServer() {
        try {
            socket = new Socket(t_serverAddress.getText(), Integer.parseInt(t_serverPort.getText()));
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            Message connectMsg = new Message(Message.MessageType.CONNECT, t_userID.getText(), t_answerKey.getText());
            out.writeObject(connectMsg);
            out.flush();

            Thread receiveThread = new Thread( () -> {
                receiveMessage();
            }, "receiveThread");
            receiveThread.start();
            displayMessage("Connected to server: " + socket.getInetAddress());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    BaseballClientGUI() {
        setTitle("Multi ClientGUI");
        buildGUI();
        setBounds(200, 100, 600, 450);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }

    public static void main(String[] args) {
        new BaseballClientGUI();
    }

}