import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class MultiClientGUI extends JFrame {

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


    void buildGUI() {
        setLayout(new BorderLayout());
        add(createDisplay(), BorderLayout.CENTER);
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new GridLayout(0, 1));
        bottomPanel.add(createInfoPanel());
        bottomPanel.add(createInputPanel());
        bottomPanel.add(createControlPanel());
        add(bottomPanel, BorderLayout.SOUTH);
    }

    JPanel createInfoPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        JLabel l_userID = new JLabel("아이디: ");
        JLabel l_serverAddress = new JLabel("서버 주소: ");
        JLabel l_serverPort = new JLabel("포트번호: ");
        try {
            InetAddress local  = InetAddress.getLocalHost();
            String addr = local.getHostAddress();
            String[] part = addr.split("\\.");
            System.out.println("local: " + local + "");
            System.out.println("addr: " + addr + "");
            t_userID = new JTextField("guest" + part[3], 10);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        t_serverAddress = new JTextField("localhost", 10);
        t_serverPort = new JTextField("54321", 10);

        panel.add(l_userID);
        panel.add(t_userID);
        panel.add(l_serverAddress);
        panel.add(t_serverAddress);
        panel.add(l_serverPort);
        panel.add(t_serverPort);
        return panel;
    }

    JPanel createDisplay() {
        JPanel displayPanel = new JPanel();
        displayPanel.setLayout(new BorderLayout());
        t_display = new JTextArea(10, 20);
        t_display.setEditable(false);
        displayPanel.add(new JScrollPane(t_display), BorderLayout.CENTER);
        return displayPanel;
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

            Message connectMsg = new Message(Message.MessageType.CONNECT, t_userID.getText(), "");
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

    MultiClientGUI() {
        setTitle("Multi ClientGUI");
        buildGUI();
        setBounds(200, 100, 600, 450);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }

    public static void main(String[] args) {
        new MultiClientGUI();
    }

}