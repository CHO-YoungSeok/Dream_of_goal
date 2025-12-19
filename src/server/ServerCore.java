package server;

import common.Message;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;

// 서버 핵심 기능 클래스

public class ServerCore {
    private int port;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    // 매니저들
    private AuthManager authManager;
    private RoomManager roomManager;

    // 클라이언트 관리
    private final Vector<ClientHandler> clientHandlers = new Vector<>();
    private final int maxClients = 100;

    // GUI 콜백
    private DisplayCallback displayCallback;

    // 데이터 파일 경로
    private static final String HISTORY_FILE = "server_data/game_history.csv";
    private static final String DETAILS_FILE = "server_data/game_details.csv";

    public interface DisplayCallback {
        void printDisplay(String msg);
    }

    public ServerCore(int port, DisplayCallback callback) {
        this.port = port;
        this.displayCallback = callback;
        this.authManager = new AuthManager(this);
        this.roomManager = new RoomManager(this);
        initDataFiles();
    }

    // 데이터 파일 초기화
    private void initDataFiles() {
        new File("server_data").mkdirs();

        createFileIfNotExists("server_data/users.csv", "user_id,password,character\n");
        createFileIfNotExists("server_data/user_stats.csv", "user_id,wins,losses,draws,win_rate\n");
        createFileIfNotExists(HISTORY_FILE, "game_id,timestamp,participants,game_mode,difficulty,winner\n");
        createFileIfNotExists(DETAILS_FILE, "game_id,round,player_id,guess,result\n");
    }

    private void createFileIfNotExists(String filePath, String header) {
        File file = new File(filePath);
        if (!file.exists()) {
            try (FileWriter fw = new FileWriter(filePath)) {
                fw.write(header);
                printDisplay("파일 생성: " + filePath);
            } catch (IOException e) {
                printDisplay("파일 생성 실패: " + filePath);
            }
        }
    }

    // 서버 시작
    public void startServer() {
        acceptThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                printDisplay("서버 시작 (포트: " + port + ")");

                while (!serverSocket.isClosed()) {
                    Socket socket = serverSocket.accept();

                    // 최대 접속자 수 체크
                    if (clientHandlers.size() >= maxClients) {
                        printDisplay("최대 접속자 수 초과. 연결 거부: " + socket.getInetAddress());
                        ObjectOutputStream tempOut = new ObjectOutputStream(socket.getOutputStream());
                        tempOut.writeObject(Message.createErrorMessage(Message.ErrorCode.SERVER_FULL));
                        tempOut.flush();
                        socket.close();
                        continue;
                    }

                    printDisplay("클라이언트 연결: " + socket.getInetAddress());

                    ClientHandler handler = new ClientHandler(socket, this);
                    new Thread(handler).start();
                }
            } catch (IOException e) {
                printDisplay("서버 종료됨");
            }
        });
        acceptThread.start();
    }

    // 서버 중지
    public void stopServer() {
        try {
            for (ClientHandler client : clientHandlers) {
                client.close();
            }
            clientHandlers.clear();

            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            printDisplay("서버 중지");
        } catch (IOException e) {
            printDisplay("서버 종료 오류: " + e.getMessage());
        }
    }

    // 클라이언트 추가
    public synchronized void addClient(ClientHandler client) {
        clientHandlers.add(client);
    }

    // 클라이언트 제거
    public synchronized void removeClient(ClientHandler client) {
        clientHandlers.remove(client);
    }

    // 중복 로그인 체크
    public boolean isAlreadyLoggedIn(String userId) {
        for (ClientHandler client : clientHandlers) {
            if (client.userId != null && client.userId.equals(userId)) {
                return true;
            }
        }
        return false;
    }

    // 방 목록 브로드캐스트
    public synchronized void broadcastRoomList() {
        Vector<Message> roomList = roomManager.getRoomList();
        Message msg = new Message(Message.MessageType.ROOM_LIST_RESPONSE, "SERVER");
        msg.setData(roomList);
        for(ClientHandler client : clientHandlers) {
            if (client.userStatus == Message.UserStatus.ONLINE) {
                client.sendMessage(msg);
            }
        }
    }

    // 접속자 목록 브로드캐스트
    public synchronized void broadcastUserList() {
        java.util.List<String> userIds = new java.util.ArrayList<>();
        java.util.Map<String, Message.UserStatus> statusMap = new java.util.HashMap<>();

        synchronized (clientHandlers) {
            for (ClientHandler client : clientHandlers) {
                if (client.userId != null) {
                    userIds.add(client.userId);
                    statusMap.put(client.userId, client.userStatus);
                }
            }
        }

        Message msg = new Message(Message.MessageType.USER_LIST_RESPONSE);
        msg.setConnectedUsers(userIds);
        msg.setUserStatusMap(statusMap);
        msg.setSuccess(true);

        // 메시지 전송
        synchronized (clientHandlers) {
            for (ClientHandler client : clientHandlers) {
                if (client.userId != null) {
                    client.sendMessage(msg);
                }
            }
        }
        printDisplay("[USER_LIST_BROADCAST] 접속자 목록 업데이트 (" + userIds.size() + "명)");
    }

    public synchronized void sendUserListToClient(ClientHandler targetClient) {
        java.util.List<String> userIds = new java.util.ArrayList<>();
        java.util.Map<String, Message.UserStatus> statusMap = new java.util.HashMap<>();

        // 1. 접속자 목록 및 상태 생성
        synchronized (clientHandlers) {
            for (ClientHandler client : clientHandlers) {
                if (client.userId != null) {
                    userIds.add(client.userId);
                    statusMap.put(client.userId, client.userStatus);
                }
            }
        }

        // 2. 응답 메시지 생성
        Message msg = new Message(Message.MessageType.USER_LIST_RESPONSE);
        msg.setConnectedUsers(userIds);
        msg.setUserStatusMap(statusMap);
        msg.setSuccess(true);

        // 3. 타겟 클라이언트에게만 전송
        targetClient.sendMessage(msg);

        // 4. 단일 응답 로그 출력 (ClientHandler에서 중복 로그는 막아두었음)
        printDisplay("[USER_LIST_RESPONSE] SERVER → " + targetClient.userId + " | 접속자 목록 응답 (" + userIds.size() + "명)");
    }

    // 전체 채팅 브로드캐스트
    public void broadcastChatAll(Message chatMsg) {
        for (ClientHandler client : clientHandlers) {
            client.sendMessage(chatMsg);
        }
    }

    // 특정 클라이언트 제외하고 전체 채팅 브로드캐스트
    public void broadcastChatAllExcept(Message chatMsg, ClientHandler except) {
        for (ClientHandler client : clientHandlers) {
            if (client != except) {
                client.sendMessage(chatMsg);
            }
        }
    }

    // 게임 로직 메서드들
    public String generateAnswer(int digitCount) {
        Vector<Integer> numbers = new Vector<>();
        for (int i = 0; i <= 9; i++) {
            numbers.add(i);
        }
        java.util.Collections.shuffle(numbers);

        StringBuilder answer = new StringBuilder();
        int count = 0;
        for (int num : numbers) {
            if (count == 0 && num ==0) {
                continue;
            }
            answer.append(num);
            count++;
            if(count == digitCount) {
                break;
            }
        }
        while(answer.length() < digitCount) {
            return generateAnswer(digitCount);
        }
        return answer.toString();
    }

    public int[] calculateResult(String target, String guess) {
        int strike = 0, ball = 0;

        for (int i = 0; i < target.length(); i++) {
            char targetChar = target.charAt(i);
            char guessChar = guess.charAt(i);

            if (targetChar == guessChar) {
                strike++;
            } else if (target.indexOf(guessChar) >= 0) {
                ball++;
            }
        }
        return new int[]{strike, ball};
    }

    public boolean isValidGuess(String guess, int digitCount) {
        if (guess == null || guess.length() != digitCount) {
            return false;
        }

        boolean[] used = new boolean[10];
        for (int i = 0; i < guess.length(); i++) {
            char c = guess.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }

            if (i == 0 && c == '0') {
                return false;
            }

            int digit = c - '0';
            if (used[digit]) {
                return false;
            }
            used[digit] = true;
        }
        return true;
    }

    // 로그 출력
    public void printDisplay(String msg) {
        if (displayCallback != null) {
            displayCallback.printDisplay(msg);
        }
    }

    // Getter 메서드들
    public AuthManager getAuthManager() {
        return authManager;
    }

    public RoomManager getRoomManager() {
        return roomManager;
    }

    public Vector<ClientHandler> getClientHandlers() {
        return clientHandlers;
    }

    public static String getHistoryFile() {
        return HISTORY_FILE;
    }

    public static String getDetailsFile() {
        return DETAILS_FILE;
    }
}