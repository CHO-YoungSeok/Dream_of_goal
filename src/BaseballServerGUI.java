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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class BaseballServerGUI extends JFrame {
    private int port = 54321;
    private Thread acceptThread;
    private ServerSocket serverSocket;

    private JTextArea t_display;
    private JButton b_start, b_stop;

    private final List<ClientHandler> clientHandlers = new CopyOnWriteArrayList<>();

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

        b_stop.addActionListener(new ActionListener(){
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

                    while(acceptThread == Thread.currentThread()) {
                        Socket socket = serverSocket.accept();
                        printDisplay("클라이언트 연결: " + socket.getInetAddress());

                        // ClientHandler 생성 및 실행
                        ClientHandler handler = new ClientHandler(socket);
                        new Thread(handler).start();
                    }

                } catch(SocketException se) {

                } catch(IOException e) {
                    printDisplay("서버 오류: " + e.getMessage());
                } finally {
                    // 루프 종료 후 정리
                    stopServerLogic();
                }
            }
        }, "AcceptThread");

        acceptThread.start();
    }

    private void stopServer() {
        // 클라이언트 연결 먼저 종료
        for(ClientHandler handler : clientHandlers) {
            handler.closeConnection();
        }
        clientHandlers.clear();

        stopServerLogic();
    }

    private void stopServerLogic() {
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
    private void broadcasting(Message msg) {
        for(ClientHandler handler : clientHandlers) {
            handler.sendMessage(msg);
        }
    }

    // 게임 시작 알림 (2명이 모였을 때 호출)
    private void startGame() {
        if(clientHandlers.size() < 2) return;

        printDisplay("2명이 모여 게임을 시작합니다.");

        // S -> C, START_GAME 프로토콜 전송
        String msgContent = "2명이 모였습니다. 숫자를 추측하여 채팅창에 입력하세요.";
        Message startMsg = new Message(Message.MessageType.START_GAME, "SERVER", msgContent);
        broadcasting(startMsg);

        // 각 클라이언트에게 상대방 ID 알림
        ClientHandler p1 = clientHandlers.get(0);
        ClientHandler p2 = clientHandlers.get(1);

        p1.sendMessage(new Message(Message.MessageType.CHAT, "SYSTEM", p2.getUid() + "의 숫자를 맞춰야 합니다."));
        p2.sendMessage(new Message(Message.MessageType.CHAT, "SYSTEM", p1.getUid() + "의 숫자를 맞춰야 합니다."));
    }

    // 결과 계산 (스트라이크, 볼)
    private int[] calculateResult(String target, String guess) {

        int strike = 0;
        int ball = 0;

        if(target.length() != guess.length()) return new int[]{0, 0};

        for(int i = 0; i < target.length(); i++) {
            char gChar = guess.charAt(i);

            // 스트라이크 확인: 숫자와 위치가 모두 일치
            if(gChar == target.charAt(i)) {
                strike++;
            }
            // 볼 확인: 숫자 일치, 위치 불일치
            else if (target.contains(String.valueOf(gChar))) {
                ball++;
            }
        }
        return new int[]{strike, ball}; // [스트라이크, 볼]
    }

    // 클라이언트 핸들러
    class ClientHandler implements Runnable {

        private Socket socket;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private String uid;
        private String answerKey; // 클라이언트가 설정한 정답 숫자

        public String getUid() {
            return uid;
        }

        public ClientHandler(Socket socket) {
            this.socket = socket;

            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());
            } catch(IOException e) {
                printDisplay("스트림 생성 오류: " + e.getMessage());
            }
        }

        public void run() {
            try{
                // 1. 초기 CONNECT_REQUEST 메시지 수신 및 처리
                Message msg = (Message) in.readObject();
                if (msg.getType() == Message.MessageType.CONNECT) {
                    this.uid = msg.getUserId();
                    this.answerKey = msg.getContent(); //클라이언트가 설정한 정답키
                    printDisplay(uid + "님이 접속했습니다. 정답: " + answerKey);

                    // 핸들러 리스트에 추가하고 게임 시작 여부 확인
                    clientHandlers.add(this);
                    if (clientHandlers.size() == 2) {
                        startGame();
                    }
                } else{
                    printDisplay("잘못된 초기 메시지 수신: " + msg.getType());
                    return;
                }

                // 2. 메인 메시지 수신 루프
                while (socket.isConnected()) {
                    msg = (Message) in.readObject();
                    handleMessage(msg);
                }
            } catch (SocketException se) {
                printDisplay(uid + "의 연결이 강제로 종료");
            } catch (IOException e) {
                printDisplay(uid + " 통신 오류: " +  e.getMessage());
            } catch (ClassNotFoundException e) {
                printDisplay("메시지 클래스를 찾을 수 없습니다.");
            } finally {
                closeConnection();
            }
        }

        // 클라이언트가 보낸 메시지 처리
        private void handleMessage(Message msg) {
            switch (msg.getType()) {
                case CHAT:
                    handleChat(msg);
                    break;
                case GUESS:
                    handleGuess(msg);
                    break;
                case DISCONNECT:
                    printDisplay(uid + "가 연결을 종료했습니다.");
                    break;
                default:
                    printDisplay(uid + "로부터 알 수 없는 메시지 타입 수신: " + msg.getType());
            }
        }

        // 채팅 전송 (C->S) 메시지를 받아 채팅 중계 (S->C)
        private void handleChat(Message msg) {
            // 채팅 중계 프로토콜 (S->C, CHAT)
            broadcasting(msg);
            printDisplay("[채팅] " + msg.getUserId() + ": " + msg.getContent());
        }

        // 사용자의 숫자 추측 처리
        private void handleGuess(Message msg) {
            String guess = msg.getContent();
            printDisplay(uid + "의 추측: " + guess);

            // 1. 상대방(target) ClientHandler 찾기 (2인 게임 가정)
            ClientHandler targetHandler = null;
            for(ClientHandler handler : clientHandlers) {
                if(handler != this) {
                    targetHandler = handler;
                    break;
                }
            }

            if(targetHandler == null) {
                sendMessage(new Message(Message.MessageType.CHAT, "SYSTEM", "상대방이 접속 중이 아닙니다."));
                return;
            }

            String targetAnswer = targetHandler.answerKey;

            // 2. 결과 계산
            int[] result = calculateResult(targetAnswer, guess);
            int strike = result[0];
            int ball = result[1];

            // 3. 결과 메시지 생성 (S->C, RESULT)
            Message resultMsg = new Message(Message.MessageType.RESULT, uid, guess);
            resultMsg.setResult(strike, ball);

            // 4. 추측을 보낸 클라이언트에게 결과 전송
            sendMessage(resultMsg);

            // 5. 게임 로그 브로드캐스트
            Message logMsg = new Message(Message.MessageType.CHAT, "GAME_LOG",
                    uid + "가 " + guess + "를 추측: " + strike + "S " + ball + "B");
            broadcasting(logMsg);

            // 6. 3스트라이크 시 승리 처리
            if (strike == 3) {
                Message winMsg = new Message(Message.MessageType.CHAT, "SYSTEM",
                        uid + "가 정답(" + targetAnswer + ")을 맞추고 승리했습니다! 게임 종료.");
                broadcasting(winMsg);

                // 게임 재시작 or 창 닫기 로직 구현
            }
        }

        //클라이언트에게 메시지 전송
        private void sendMessage(Message msg) {
            try{
                out.writeObject(msg);
                out.flush();
            }catch (IOException e) {
                printDisplay("메시지 전송 오류(" + uid +"): " + e.getMessage());
            }
        }

        // 클라이언트 연결 종료
        public void closeConnection() {
            try{
                if(socket != null && !socket.isClosed()) {
                    socket.close();
                }

                // 리스트에서 제거
                clientHandlers.remove(this);

                // 연결 종료 브로드캐스트
                if (uid != null) {
                    printDisplay(uid + " 연결 종료. 현재 접속자: " + clientHandlers.size() + "명");
                    Message disconnectMsg = new Message(Message.MessageType.DISCONNECT, this.uid);
                    broadcasting(disconnectMsg);
                }
            } catch (IOException ex) {
                printDisplay("소켓 종료 오류: " + ex.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        new BaseballServerGUI(54321);
    }
}

