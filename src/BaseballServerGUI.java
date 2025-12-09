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

    // 접속한 클라이언트 관리
    private Vector<ClientHandler> clients = new Vector<>();

    // 방 관리
    private Vector<GameRoom> rooms = new Vector<>();
    private int nextRoomId = 1;

    // 데이터 파일 경로
    private static final String USERS_FILE = "server_data/users.csv"; // 회원 정보
    private static final String STATS_FILE = "server_data/user_stats.csv"; // 전적 정보
    private static final String HISTORY_FILE = "server_data/game_history.csv"; // 게임 이력
    private static final String DETAILS_FILE = "server_data/game_details.csv"; // 게임 상세 기록

    public BaseballServerGUI(int port) {
        super("Baseball Game Server");
        this.port = port;

        initDataFiles();

        buildGUI();
        setSize(600, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);
    }

    // 데이터 파일 초기화
    private void initDataFiles() {
        new File("server_data").mkdirs();

        // 파일이 없으면 헤더와 함께 생성
        createFileIfNotExists(USERS_FILE, "user_id, password, character\n");
        createFileIFNotExists(STATS_FILE, "user_id, wins, looses, draws, win_rate\n ");
        createFileIFNotExists(HISTORY_FILE, "game_id, timestamp, participants, game_mode, difficulty, winner\n");
        createFileIFNotExists(DETAILS_FILE, "game_id, round, player_id, guess, result\n");
    }

    // 파일이 없으면 생성
    private void createFileIfNotExists(String filePath, String header) {
        File file = new File(filePath);
        if(!file.exists()) {
            try{
                FileWriter fw = new FileWriter(filePath);
                fw.write(header);
                fw.close();
                printDisplay("파일 생성: " + filePath);
            } catch (IOException e) {
                printDisplay("파일 생성 실패: " + filePath);
            }
        }
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

    // 서버 시작
    private void startServer() {
        acceptThread = new Thread(() -> {
            try{
                serverSocket = new ServerSocket(port);
                printDisplay("서버 시작 (포트: " + port + ")");

                b_start.setEnabled(false);
                b_stop.setEnabled(true);

                while(true) {
                    Socket socket = serverSocket.accept();
                    printDisplay("클라이언트 연결: " + socket.getInetAddress());

                    ClientHandler handler = new ClientHandler(socket);
                    clients.add(handler);
                    new Thread(handler).start();
                }
            } catch(IOException e) {
                printDisplay("서버 종료됨");
            }
        });
        acceptThread.start();
    }

    // 서버 중지
    private void stopServer() {
        try{
            for(ClientHandler client : clients) {
                client.close();
            }
            clients.clear();

            if(serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            printDisplay("서버 중지");
            b_stop.setEnabled(false);
            b_start.setEnabled(true);
        } catch(IOException e) {
            printDisplay("서버 종료 오류: " + e.getMessage());
        }
    }

    private void printDisplay(String msg) {
        t_display.append(msg + "\n");
        t_display.setCaretPosition(t_display.getDocument().getLength());
    }

    // --- 인증 관련 ---

    // 회원가입
    private boolean registerUser(String userId, String password, String character) {
        // ID 중복 체크
        if (!isUserExists(userId)) {
            return false;
        }

        // users.csv에 저장
        try{
            FileWriter fw = new FileWriter(USERS_FILE, true);
            fw.write(userId, + "," + password + "," + character + "\n");
            fw.close();

            // 전적 초기화
            FilwWriter statsFw = new FileWriter(STATS_FILE, true);
            statsFw.write(userId, ",0,0,0,0.0\n");
            statsFww.close();

            return true;
        } catch (IOException e) {
            printDisplay("회원가입 저장 실패: " + e.getMessage());
            return false;
        }
    }

    // ID 중복 확인
    private boolean isUserExists(String userId) {
        try {
            File file = new File(USERS_FILE);
            if(!file.exists()) {
                return false;
            }

            BufferedReader br = new BufferedReader(new FileReader(USERS_FILE));
            String line;
            while((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if(parts[0].equals(userId)) {
                    br.close();
                    return true;
                }
            }
            br.close();
        }catch(IOException e) {
            printDisplay("파일 읽기 오류: " + e.getMessage());
        }
        return false;
    }

    // 로그인 인증
    private boolean authenticateUser(String userId, String password) {
        try{
            File file = new File(USERS_FILE);
            if(!file.exists()) {
                return false;
            }

            BufferedReader br = new BufferedReader(new FileReader(USERS_FILE));
            String line;
            while((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if(parts[0].equals(userId) && parts[1].equals(password)) {
                    br.close();
                    return true;
                }
            }
            br.close();
        } catch(IOException e) {
            printDisplay("인증 오류: " + e.getMessage());
        }
        return false;
    }

    // 중목 로그인 체크
    private boolean isAlreadyLoggedIn(String userId) {
        for(ClientHandler client : clients) {
            if(client.userId != null && client.userId.equals(userId)) {
                return true;
            }
        }
        return false;
    }

    // --- 방 관련 ---
    private GameRoom createRoom(String roomName, String masterUserId, Massage.GameMode gameMode,
                                Message.Difficulty difficulty, Massage.TurnTimeLimit turnTimeLimit) {
        if(rooms.size() >= 20){
            return null;
        }

        GamrRoom room = new GameRoom(nextRoodId++, roomName, masterUserId, gameMode, difficulty, turnTimeLimit);

        rooms.add(room);
        printDisplay("방 생성: [" + room.roomId + "] " + roomName);
        return room;
    }

    // 방 찾기
    private GameRoom findRoom(int roomId) {
        for(GameRoom room : rooms) {
            if(room.roomId == roomId) {
                return room;
            }
        }
        return null;
    }

    // 방 삭제
    private void removeRoom(GameRoom room) {
        rooms.remove(room);
        printDisplay("방 삭제: [" + room.roomId + "]");
    }

    // --- 게임 로직 ---
    // 정답 생성(1~9 중 중복 없이)
    private String generateAnswer(int digitCount) {
        Vector<Integer> numbers = new Vector<>();
        for(int i = 1; i <= 9; i++){
            numbers.add(i);
        }

        // 섞기
        for(int i = 0; i < numbers.size(); i++) {
            int randomIdx = (int)(Math.random() * numbers.size());
            int temp = numbers.get(i);
            numbers.set(i, numbers.get(randomIdx));
            numbers.set(randomIdx, temp);
        }

        // 필요한 자릿수만큼 추출
        String answer = "";
        for(int i = 0; i < digitCount; i++) {
            answer += numbers.get(i);
        }
        return answer;
    }

    // 스트라이크, 볼 계산
    private int[] calculateResult(String target, String guess) {
        int strike = 0;
        int ball = 0;

        for(int i = 0; i < target.length(); i++) {
            char targetChar = target.charAt(i);
            char guessChar = guess.charAt(i);

            if(targetChar == guessChar) {
                strike++;
            } else if(target.indexOf(guessChar) >= 0) {
                ball++;
            }
        }
        return new int[]{strike, ball};
    }

    // 입력 검증
    private boolean isValidGuess(String guess, int digitCount) {
        // 길이 체크
        if (guess.length() != digitCount) {
            return false;
        }

        // 숫자인지 체크 & 중복 체크
        boolean[] used = new boolean[10];
        for(int i = 0; i < guess.lenth(); i++) {
            char c = guess.charAt(i);

            // 숫자가 아니거나 0이면 false
            if(c < '1' || c > '9') {
                return false;
            }

            // 중복체크
            int digit = c -'0';
            if(used[digit]) {
                return false;
            }
            used[digit] = true;
        }
        return true;
    }



    // -------------------------

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

