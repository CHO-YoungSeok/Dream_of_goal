// 게임 로직 관리, 클라이언트 간 통신 중계, 결과 계산

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;

public class BaseballServerGUI extends JFrame {
    private int port = 54321;
    private Thread acceptThread;
    private ServerSocket serverSocket;

    private JTextArea t_display;
    private JButton b_start, b_stop;


    public BaseballServerGUI(int port) {
        super("Baseball Game Server");

        this.port = port;

        buildGUI();

        setSize(600, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);
    }

    private void buildGUI() {
        // 로그 영역
        t_display = new  JTextArea();
        t_display.setEditable(false);
        add(new JScrollPane(t_display), BorderLayout.CENTER);

        // 버튼 패널
        JPanel btnPanel = new JPanel();
        b_start = new JButton("서버 시작");
        b_stop = new JButton("서버 중지");
        b_stop.setEnabled(false);

        b_start.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startServer();
            }
        });

        b_stop.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stopServer();
            }
        });

        btnPanel.add(b_start);
        btnPanel.add(b_stop);
        add(btnPanel, BorderLayout.SOUTH);
    }

    private void startServer() {
        acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    serverSocket = new ServerSocket(port);
                    printDisplay("서버 시작 (포트: " + port + ")");

                    b_start.setEnabled(false);
                    b_stop.setEnabled(true);

                    while(acceptThread == Thread.currentThread()){
                        Socket socket = serverSocket.accept();
                        printDisplay("클라이언트 연결: " + socket.getInetAddress());

                        socket.close();
                    }

                } catch(IOException e) {
                    printDisplay("서버 오류: " + e.getMessage());
                }
            }
        });

        acceptThread.start();
    }

    private void stopServer() {
        try{
            if (acceptThread != null) {
                acceptThread = null;
            }

            if(serverSocket != null && !serverSocket.isClosed()){
                serverSocket.close();
            }

            printDisplay("서버 중지");
            b_stop.setEnabled(false);
            b_start.setEnabled(true);

        }catch (IOException e) {
            printDisplay("서버 종류 오류: " + e.getMessage());
        }
    }

    private void printDisplay(String msg) {
        t_display.append(msg + "\n");
        t_display.setCaretPosition(t_display.getDocument().getLength());
    }

    // 모든 클라이언트에게 브로드캐스트
    private void broadcasting() {

    }

    // 정답 생성 (3자리 중복 없는 숫자)
    private String generateAnswer() {
        return "";
    }

    // 결과 계산 (스트라이크, 볼)
    private int calculateResult() {
        return 0;
    }

    /*
    // 클라이언트 핸들러
    class ClientHandler implements Runnable {

        private Socket socket;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private String uid;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }
        */

        public void run() {

        }

        // 클라이언트가 보낸 메시지 처리
        private void handleMessage(Message msg) {

        }

        // 사용자의 숫자 추측 처리
        private void handleGuess(Message msg) {
            // 상대방 찾기
            // 결과 계산
            // 결과 전송
            // 모두에게 알림
            // 3 스트라이크면 승리
        }

        //클라이언트에게 메시지 전송
        private void sendMessage(Message msg) {

        }

        // 클라이언트 연결 종료
        public void closeConnection() {
    }

    public static void main(String[] args) {
        new BaseballServerGUI(54321);
    }
}

