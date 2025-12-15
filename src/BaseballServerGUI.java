import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.List;
import java.io.Serializable;

public class BaseballServerGUI extends JFrame {
    private int port = 54321;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    private JTextArea t_display;
    private JButton b_start, b_stop;

    // 접속한 클라이언트 관리 (Vector -> List<ClientHandler>로 변경)
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final int maxClients = 100; // 최대 동시 접속자 수

    // 방 관리
    private final Vector<GameRoom> rooms = new Vector<>();
    private int nextRoomId = 1;
    private final int maxRooms = 20;

    // 데이터 파일 경로
    private static final String USERS_FILE = "server_data/users.csv";
    private static final String STATS_FILE = "server_data/user_stats.csv";
    private static final String HISTORY_FILE = "server_data/game_history.csv";
    private static final String DETAILS_FILE = "server_data/game_details.csv"; // 상세 기록 경로

    public BaseballServerGUI(int port) {
        super("Baseball Game Server");
        this.port = port;

        initDataFiles();

        buildGUI();
        setSize(700, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);
    }

    // 데이터 파일 초기화
    private void initDataFiles() {
        new File("server_data").mkdirs();
        createFileIfNotExists(USERS_FILE, "user_id,password,character\n");
        createFileIfNotExists(STATS_FILE, "user_id,wins,losses,draws,win_rate\n");
        createFileIfNotExists(HISTORY_FILE, "game_id,timestamp,participants,game_mode,difficulty,winner\n");
        createFileIfNotExists(DETAILS_FILE, "game_id,round,player_id,guess,result\n");
    }

    // 파일이 없으면 생성
    private void createFileIfNotExists(String filePath, String header) {
        File file = new File(filePath);
        if (!file.exists()) {
            try {
                FileWriter fw = new FileWriter(filePath);
                fw.write(header);
                fw.close();
                printDisplay("파일 생성: " +filePath);
            } catch (IOException e) {
                printDisplay("파일 생성 실패: " + filePath + " - " + e.getMessage());
            }
        }
    }

    private void buildGUI() {
        t_display = new JTextArea();
        t_display.setEditable(false);
        add(new JScrollPane(t_display), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel();
        b_start = new JButton("서버 시작");
        b_stop = new JButton("서버 중지");
        b_stop.setEnabled(false);

        b_start.addActionListener(e -> startServer());
        b_stop.addActionListener(e -> stopServer());

        btnPanel.add(b_start);
        btnPanel.add(b_stop);
        add(btnPanel, BorderLayout.SOUTH);
    }

    // 서버 시작
    private void startServer() {
        acceptThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                printDisplay("서버 시작 (포트: " + port + ")");

                SwingUtilities.invokeLater(() -> {
                    b_start.setEnabled(false);
                    b_stop.setEnabled(true);
                });

                while(!serverSocket.isClosed()) {
                    Socket socket = serverSocket.accept();

                    if(clients.size() >= maxClients) {
                        printDisplay("최대 접속자 수 초과. 연결 거부: " + socket.getInetAddress());
                        ObjectOutputStream tempOut = new ObjectOutputStream(socket.getOutputStream());
                        tempOut.writeObject(Message.createErrorMessage(Message.ErrorCode.SERVER_FULL));
                        tempOut.flush();
                        socket.close();
                        continue;
                    }

                    printDisplay("클라이언트 연결 대기: " + socket.getInetAddress());
                    ClientHandler handler = new ClientHandler(socket);
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
        try {
            for (ClientHandler client : clients) {
                client.close();
            }
            clients.clear();
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            printDisplay("서버 중지");
            SwingUtilities.invokeLater(() -> {
                b_stop.setEnabled(false);
                b_start.setEnabled(true);
            });
        } catch (IOException e) {
            printDisplay("서버 종료 오류: " + e.getMessage());
        }
    }

    // 로그 출력 (Swing Utilities를 사용하여 스레드 안전하게 출력)
    private void printDisplay(String msg) {
        SwingUtilities.invokeLater(() -> {
            t_display.append(msg + "\n");
            t_display.setCaretPosition(t_display.getDocument().getLength());
        });
    }

    // --- 인증 관련 메서드 ---
    private boolean registerUser(String userId, String password, String character) {
        if (isUserExists(userId)) return false;
        try {
            FileWriter fw = new FileWriter(USERS_FILE, true);
            fw.write(userId + "," + password + "," + character + "\n");
            fw.close();

            FileWriter statsFw = new FileWriter(STATS_FILE, true);
            statsFw.write(userId + ",0,0,0,0.0\n");
            statsFw.close();
            return true;
        } catch(IOException e) {
            printDisplay("회원가입 저장 실패: " + e.getMessage());
            return false;
        }
    }

    private boolean isUserExists(String userId) {
        try (BufferedReader br = new BufferedReader(new FileReader(USERS_FILE))) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length > 0 && parts[0].trim().equals(userId)) return true;
            }
        } catch (IOException e) {
            printDisplay("파일 읽기 오류: " + e.getMessage());
        }
        return false;
    }

    private boolean authenticateUser(String userId, String password) {
        try (BufferedReader br = new BufferedReader(new FileReader(USERS_FILE))) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 2 && parts[0].trim().equals(userId) && parts[1].trim().equals(password)) return true;
            }
        } catch (IOException e) {
            printDisplay("인증 오류: " + e.getMessage());
        }
        return false;
    }

    private boolean isAlreadyLoggedIn(String userId) {
        for(ClientHandler client : clients) {
            if(client.userId != null && client.userId.equals(userId)) return true;
        }
        return false;
    }

    // --- 방 관련 메서드 ---
    private GameRoom createRoom(String roomName, String masterUserId,
                                Message.GameMode gameMode, Message.Difficulty difficulty,
                                Message.TurnTimeLimit turnTimeLimit, boolean isPrivate, String roomPassword) {
        if (rooms.size() >= maxRooms) return null;

        GameRoom room = new GameRoom(nextRoomId ++, roomName, masterUserId,
                gameMode, difficulty, turnTimeLimit, isPrivate, roomPassword);
        rooms.add(room);
        printDisplay("방 생성: [" + room.roomId + "] " + roomName);
        return room;
    }

    private GameRoom findRoom(int roomId) {
        for(GameRoom room : rooms) {
            if (room.roomId == roomId) return room;
        }
        return null;
    }

    private void removeRoom(GameRoom room) {
        rooms.remove(room);
        printDisplay("방 삭제: [" + room.roomId + "]");
    }

    // --- 게임 로직 ---
    private String generateAnswer(int digitCount) {
        Vector<Integer> numbers = new Vector<>();
        for(int i = 1; i <= 9; i++) numbers.add(i);
        java.util.Collections.shuffle(numbers);

        String answer = "";
        for (int i = 0; i < digitCount; i++) answer += numbers.get(i);
        return answer;
    }

    private int[] calculateResult(String target, String guess) {
        int strike = 0;
        int ball = 0;

        for(int i = 0; i < target.length(); i++) {
            char targetChar = target.charAt(i);
            char guessChar = guess.charAt(i);

            if(targetChar == guessChar) strike++;
            else if(target.indexOf(guessChar) >= 0) ball++;
        }
        return new int[]{strike, ball};
    }

    private boolean isValidGuess(String guess, int digitCount) {
        if(guess == null || guess.length() != digitCount) return false;

        boolean[] used = new boolean[10];
        for (int i = 0; i < guess.length(); i++) {
            char c = guess.charAt(i);

            if (c < '1' || c > '9') return false;

            int digit = c - '0';
            if (used[digit]) return false;
            used[digit] = true;
        }
        return true;
    }

    // --- 내부 클래스: GameRoom ---
    class GameRoom {
        int roomId;
        String roomName;
        String roomMaster;
        Message.GameMode gameMode;
        Message.Difficulty difficulty;
        Message.TurnTimeLimit turnTimeLimit;
        boolean isPrivate;
        String roomPassword;

        Vector<ClientHandler> players = new Vector<>();
        Hashtable<String, Boolean> readyStatus = new Hashtable<>();

        boolean isGameRunning = false;
        Hashtable<String, String> playerAnswers = new Hashtable<>();
        Hashtable<String, Integer> playerTeams = new Hashtable<>();
        int currentRound = 1;
        boolean isTopHalf = true;
        String gameId;

        public GameRoom(int roomId, String roomName, String roomMaster,
                        Message.GameMode gameMode, Message.Difficulty difficulty,
                        Message.TurnTimeLimit turnTimeLimit, boolean isPrivate, String roomPassword) {
            this.roomId = roomId;
            this.roomName = roomName;
            this.roomMaster = roomMaster;
            this.gameMode = gameMode;
            this.difficulty = difficulty;
            this.turnTimeLimit = turnTimeLimit;
            this.isPrivate = isPrivate;
            this.roomPassword = roomPassword;
        }

        public Message createRoomUpdateMessage(String content) {
            Message msg = new Message(Message.MessageType.ROOM_INFO_UPDATE, "SERVER", content);

            msg.setRoomId(roomId);
            msg.setRoomName(roomName);
            msg.setRoomMaster(roomMaster);
            msg.setGameMode(gameMode);
            msg.setDifficulty(difficulty);
            msg.setTurnTimeLimit(turnTimeLimit);
            msg.setPrivate(isPrivate);
            msg.setRoomStatus(isGameRunning ? Message.RoomStatus.IN_GAME : Message.RoomStatus.WAITING);
            msg.setCurrentPlayers(players.size());
            msg.setMaxPlayers(gameMode.getMaxPlayers());

            Hashtable<String, Serializable> roomData = new Hashtable<>();
            Vector<String> playerIds = players.stream().map(p -> p.userId).collect(Collectors.toCollection(Vector::new));
            roomData.put("players", playerIds);
            roomData.put("readyStatus", new Hashtable<>(readyStatus));

            msg.setData(roomData);
            return msg;
        }

        public boolean addPlayer(ClientHandler player) {
            if (players.size() >= gameMode.getMaxPlayers()) return false;

            players.add(player);
            readyStatus.put(player.userId, false);

            if (gameMode == Message.GameMode.TWO_VS_TWO) {
                int teamNum = (players.size() <= 2) ? 1 : 2;
                playerTeams.put(player.userId, teamNum);
                BaseballServerGUI.this.printDisplay(player.userId + " -> Team " + teamNum);
            }

            Message updateMsg = createRoomUpdateMessage(player.userId + "님이 입장하셨습니다.");
            broadcastToRoom(updateMsg);
            return true;
        }

        public void removePlayer(ClientHandler player) {
            players.remove(player);
            readyStatus.remove(player.userId);
            playerTeams.remove(player.userId);

            if (players.isEmpty()) {
                BaseballServerGUI.this.removeRoom(this);
                return;
            }

            if (player.userId.equals(roomMaster)) {
                roomMaster = players.get(0).userId;
                Message msg = createRoomUpdateMessage(roomMaster + "님이 새로운 방장이 되었습니다.");
                broadcastToRoom(msg);
            } else {
                Message msg = createRoomUpdateMessage(player.userId + "님이 퇴장했습니다.");
                broadcastToRoom(msg);
            }
        }

        public void setReady(String userId, boolean ready) {
            readyStatus.put(userId, ready);
            String msg = userId + "님이 " + (ready ? "준비완료" : "준비취소") + " 했습니다.";

            Message statusUpdate = new Message(Message.MessageType.READY_STATUS_UPDATE, "SERVER", msg);
            statusUpdate.setData(new Hashtable<>(readyStatus));
            broadcastToRoom(statusUpdate);
        }

        public boolean canStartGame() {
            if (players.size() != gameMode.getMaxPlayers()) return false;

            for(ClientHandler player : players) {
                Boolean ready = readyStatus.get(player.userId);
                if(ready == null || !ready) return false;
            }
            return true;
        }

        public void startGame() {
            isGameRunning = true;
            gameId = "G" + System.currentTimeMillis();

            if (gameMode == Message.GameMode.ONE_VS_ONE){
                for (ClientHandler player : players) {
                    String answer = BaseballServerGUI.this.generateAnswer(difficulty.getDigitCount());
                    playerAnswers.put(player.userId, answer);
                    BaseballServerGUI.this.printDisplay("게임 시작 - " + player.userId + "의 정답: " + answer);
                }
            } else{
                String team1Answer = BaseballServerGUI.this.generateAnswer(difficulty.getDigitCount());
                String team2Answer = BaseballServerGUI.this.generateAnswer(difficulty.getDigitCount());

                for (ClientHandler player : players) {
                    int teamNum = playerTeams.get(player.userId);
                    String answer = (teamNum == 1) ? team1Answer : team2Answer;
                    playerAnswers.put(player.userId, answer);
                    BaseballServerGUI.this.printDisplay("게임 시작 - " + player.userId + " (Team " + teamNum + ")의 정답: " + answer);
                }
            }

            currentRound = 1;
            isTopHalf = true;

            Message startMsg = new Message(Message.MessageType.START_GAME, "SERVER");
            startMsg.setGameMode(gameMode);
            startMsg.setDifficulty(difficulty);
            startMsg.setTurnTimeLimit(turnTimeLimit);
            startMsg.setContent("게임이 시작되었습니다!");
            broadcastToRoom(startMsg);

            sendTurnInfo();
        }

        public void sendTurnInfo() {
            ClientHandler turnPlayer = null;
            if(players.size() > 0) {
                int playerIndex = ((currentRound - 1) * 2 + (isTopHalf ? 0 : 1)) % players.size();
                turnPlayer = players.get(playerIndex);
            }

            Message turnMsg = new Message(Message.MessageType.TURN_INFO, "SERVER");
            turnMsg.setRound(currentRound);
            turnMsg.setTop(isTopHalf);
            turnMsg.setCurrentTurnPlayer(turnPlayer != null ? turnPlayer.userId : null);
            turnMsg.setContent(currentRound + "회 " + (isTopHalf ? "초" : "말"));
            broadcastToRoom(turnMsg);
        }

        public void handleGuess(ClientHandler player, String guess) {
            if (!isGameRunning) return;

            if (gameMode == Message.GameMode.ONE_VS_ONE && !player.userId.equals(getCurrentTurnPlayerId())) {
                player.sendMessage(Message.createErrorMessage(Message.ErrorCode.TURN_TIMEOUT,
                        "당신의 턴이 아닙니다."));
                return;
            }

            if (!BaseballServerGUI.this.isValidGuess(guess, difficulty.getDigitCount())) {
                player.sendMessage(Message.createErrorMessage(Message.ErrorCode.INVALID_INPUT_FORMAT));
                return;
            }

            String targetAnswer = getTargetAnswer(player);
            if (targetAnswer == null) return;

            int[] result = BaseballServerGUI.this.calculateResult(targetAnswer, guess);
            int strike = result[0];
            int ball = result[1];

            Message resultMsg = Message.createGuessResult(player.userId, guess, strike, ball);

            if (gameMode == Message.GameMode.TWO_VS_TWO) {
                int myTeam = playerTeams.get(player.userId);
                for (ClientHandler p : players) {
                    if (playerTeams.get(p.userId) == myTeam) {
                        p.sendMessage(resultMsg);
                    }
                }
            } else {
                broadcastToRoom(resultMsg);
            }

            saveGameDetail(gameId, currentRound, player.userId, guess, strike + "S " + ball + "B");

            if (strike == difficulty.getDigitCount()) {
                if (gameMode == Message.GameMode.TWO_VS_TWO) {
                    endGame(player.userId, false, playerTeams.get(player.userId));
                } else {
                    endGame(player.userId, false, 0);
                }
                return;
            }

            nextTurn();
        }

        public String getCurrentTurnPlayerId() {
            if (players.isEmpty()) return null;
            int playerIndex = ((currentRound - 1) * 2 + (isTopHalf ? 0 : 1)) % players.size();
            return players.get(playerIndex).userId;
        }

        private String getTargetAnswer(ClientHandler currentPlayer) {
            if (gameMode == Message.GameMode.ONE_VS_ONE) {
                for (ClientHandler p : players) {
                    if (!p.userId.equals(currentPlayer.userId)) return playerAnswers.get(p.userId);
                }
            } else if (gameMode == Message.GameMode.TWO_VS_TWO) {
                int myTeam = playerTeams.get(currentPlayer.userId);
                int targetTeam = (myTeam == 1) ? 2 : 1;
                for (ClientHandler p : players) {
                    if (playerTeams.get(p.userId) == targetTeam) return playerAnswers.get(p.userId);
                }
            }
            return null;
        }

        private void nextTurn() {
            if (isTopHalf) isTopHalf = false;
            else { isTopHalf = true; currentRound++; }

            if (currentRound > 9) endGame(null, true, 0);
            else sendTurnInfo();
        }

        public void endGame(String winnerId, boolean isDraw, int winnerTeam) {
            isGameRunning = false;

            Message endMsg = new Message(Message.MessageType.END_GAME, "SERVER");
            if (isDraw) {
                endMsg.setDraw(true);
                endMsg.setContent("9회말 종료! 무승부입니다.");
            } else {
                endMsg.setWinnerId(winnerId);
                if (gameMode == Message.GameMode.TWO_VS_TWO) {
                    endMsg.setWinnerTeam(winnerTeam);
                    endMsg.setContent("Team " + winnerTeam + " 승리! (" + (winnerId != null ? winnerId + "님이 맞춤" : "상대팀 접속 종료") + ")");
                } else {
                    endMsg.setContent(winnerId + "님이 승리했습니다!");
                }
            }
            broadcastToRoom(endMsg);

            saveGameHistory(winnerId, isDraw, winnerTeam);

            for (ClientHandler player : players) readyStatus.put(player.userId, false);
        }

        private void saveGameHistory(String winnerId, boolean isDraw, int winnerTeam) {
            try {
                String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
                String participants = players.stream().map(p -> p.userId).collect(Collectors.joining(","));

                String winner;
                if (isDraw) winner = "Draw";
                else if (gameMode == Message.GameMode.TWO_VS_TWO) winner = "Team" + winnerTeam;
                else winner = winnerId;

                FileWriter fw = new FileWriter(HISTORY_FILE, true);
                fw.write(gameId + "," + timestamp + ",\"" + participants + "\"," +
                        gameMode.getDisplayName() + "," + difficulty.getDisplayName() + "," + winner + "\n");
                fw.close();

                BaseballServerGUI.this.printDisplay("게임 기록 저장: " + gameId);
            } catch (IOException e) {
                BaseballServerGUI.this.printDisplay("게임 기록 저장 실패: " + e.getMessage());
            }
        }

        private void saveGameDetail(String gameId, int round, String playerId, String guess, String result) {
            try {
                FileWriter fw = new FileWriter(DETAILS_FILE, true); // DETAILS_FILE 사용
                fw.write(gameId + "," + round + "," + playerId + "," + guess + "," + result + "\n");
                fw.close();
            } catch (IOException e) {
                BaseballServerGUI.this.printDisplay("게임 상세 기록 저장 실패: " + e.getMessage());
            }
        }

        public void broadcastToRoom(Message msg) {
            for (ClientHandler player : players) player.sendMessage(msg);
        }
    }


    // --- 내부 클래스: ClientHandler ---
    class ClientHandler implements Runnable {
        private Socket socket;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private String userId;
        private GameRoom currentRoom;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());
            } catch (IOException e) {
                printDisplay("스트림 생성 오류: " + e.getMessage());
            }
        }

        public void run() {
            try {
                Message msg;
                // 로그인 전: LOGIN_REQUEST / REGISTER_REQUEST 처리
                while (userId == null && (msg = (Message) in.readObject()) != null) {
                    if(msg.getType() == Message.MessageType.LOGIN_REQUEST || msg.getType() == Message.MessageType.REGISTER_REQUEST) {
                        handleMessage(msg);
                    } else {
                        // 로그인 전 다른 메시지 요청 시 에러 응답
                        sendMessage(Message.createErrorMessage(Message.ErrorCode.UNKNOWN_ERROR,
                                "로그인 또는 회원가입 요청만 가능합니다."));
                    }
                }

                // 로그인 후: 메인 메시지 처리
                while (userId != null && (msg = (Message) in.readObject()) != null) {
                    handleMessage(msg);
                }
            } catch (IOException e) {
                if (userId != null) printDisplay(userId + " 연결 종료");
            } catch (ClassNotFoundException e) {
                printDisplay("메시지 클래스 오류: " + e.getMessage());
            } finally {
                close();
            }
        }

        private void handleMessage(Message msg) {
            switch (msg.getType()) {
                case LOGIN_REQUEST:
                    handleLogin(msg);
                    break;
                case REGISTER_REQUEST:
                    handleRegister(msg);
                    break;
                case ROOM_LIST_REQUEST:
                    handleRoomListRequest();
                    break;
                case CREATE_ROOM_REQUEST:
                    handleCreateRoom(msg);
                    break;
                case JOIN_ROOM_REQUEST:
                    handleJoinRoom(msg);
                    break;
                case LEAVE_ROOM:
                    handleLeaveRoom();
                    break;
                case READY:
                    handleReady(true);
                    break;
                case READY_CANCEL:
                    handleReady(false);
                    break;
                case START_GAME_REQUEST:
                    handleStartGameRequest();
                    break;
                case KICK_PLAYER:
                    handleKickPlayer(msg);
                    break;
                case GUESS:
                    handleGuess(msg);
                    break;
                case CHAT_ROOM:
                    handleRoomChat(msg);
                    break;
                case CHAT_TEAM:
                    handleTeamChat(msg);
                    break;
                case CHAT_ALL:
                    handleAllChat(msg);
                    break;
                case CHAT_WHISPER:
                    handleWhisper(msg);
                    break;
                case LOGOUT:
                    close();
                    break;
                case STATS_REQUEST:
                    handleStatsRequest(msg);
                    break;
                case GAME_HISTORY_REQUEST:
                    handleGameHistoryRequest(msg);
                    break;
                default:
                    printDisplay(userId + "로부터 알 수 없는 메시지 타입 수신: " + msg.getType());
            }
        }

        private void handleLogin(Message msg) {
            String userId = msg.getUserId();
            String password = msg.getPassword();

            if (isAlreadyLoggedIn(userId)) {
                sendMessage(Message.createErrorMessage(Message.ErrorCode.ALREADY_LOGGED_IN));
                return;
            }

            if (authenticateUser(userId, password)) {
                this.userId = userId;
                clients.add(this);

                Message response = new Message(Message.MessageType.LOGIN_RESPONSE, userId);
                response.setSuccess(true);
                response.setContent("로그인 성공");
                sendMessage(response);
                printDisplay(userId + " 로그인 성공");
            } else {
                sendMessage(Message.createErrorMessage(Message.ErrorCode.LOGIN_FAILED));
            }
        }

        private void handleRegister(Message msg) {
            String userId = msg.getUserId();
            String password = msg.getPassword();
            String character = msg.getCharacter();

            if(registerUser(userId, password, character)) {
                Message response = new Message(Message.MessageType.REGISTER_RESPONSE, userId);
                response.setSuccess(true);
                response.setContent("회원가입 성공");
                sendMessage(response);
                printDisplay(userId + " 회원가입 성공");
            } else {
                sendMessage(Message.createErrorMessage(Message.ErrorCode.DUPLICATE_ID));
            }
        }

        private void handleRoomListRequest() {
            Vector<Message> roomList = new Vector<>();
            for (GameRoom room : rooms) {
                Message roomInfo = new Message(Message.MessageType.ROOM_LIST_RESPONSE, room.roomMaster);
                roomInfo.setRoomId(room.roomId);
                roomInfo.setRoomName(room.roomName + (room.isPrivate ? " 🔒" : ""));
                roomInfo.setRoomStatus(room.isGameRunning ? Message.RoomStatus.IN_GAME : Message.RoomStatus.WAITING);
                roomInfo.setCurrentPlayers(room.players.size());
                roomInfo.setMaxPlayers(room.gameMode.getMaxPlayers());
                roomInfo.setGameMode(room.gameMode);
                roomInfo.setDifficulty(room.difficulty);
                roomInfo.setRoomMaster(room.roomMaster);
                roomInfo.setPrivate(room.isPrivate);

                roomList.add(roomInfo);
            }

            Message response = new Message(Message.MessageType.ROOM_LIST_RESPONSE, "SERVER");
            response.setData(roomList);
            sendMessage(response);
        }

        private void handleCreateRoom(Message msg) {
            if (currentRoom != null) return;

            GameRoom room = createRoom(
                    msg.getRoomName(),
                    userId,
                    msg.getGameMode(),
                    msg.getDifficulty(),
                    msg.getTurnTimeLimit(),
                    msg.isPrivate(),
                    msg.getRoomPassword()
            );

            if (room != null) {
                currentRoom = room;
                room.addPlayer(this);

                Message response = room.createRoomUpdateMessage("방 생성 성공");
                response.setType(Message.MessageType.CREATE_ROOM_RESPONSE);
                response.setSuccess(true);
                sendMessage(response);
            } else {
                sendMessage(Message.createErrorMessage(Message.ErrorCode.SERVER_FULL,
                        "방 생성 실패 (최대 " + maxRooms + "개)"));
            }
        }

        private void handleJoinRoom(Message msg) {
            int roomId = msg.getRoomId();
            GameRoom room = findRoom(roomId);

            if (room == null) {
                sendMessage(Message.createErrorMessage(Message.ErrorCode.ROOM_NOT_FOUND));
                return;
            }
            if (room.isGameRunning) {
                sendMessage(Message.createErrorMessage(Message.ErrorCode.ROOM_IN_GAME));
                return;
            }
            if (room.players.size() >= room.gameMode.getMaxPlayers()) {
                sendMessage(Message.createErrorMessage(Message.ErrorCode.ROOM_FULL));
                return;
            }

            if (room.isPrivate) {
                String inputPassword = msg.getRoomPassword();
                if (inputPassword == null || !inputPassword.equals(room.roomPassword)) {
                    sendMessage(Message.createErrorMessage(Message.ErrorCode.WRONG_PASSWORD));
                    return;
                }
            }

            currentRoom = room;
            room.addPlayer(this);

            Message response = room.createRoomUpdateMessage("방 입장 성공");
            response.setType(Message.MessageType.JOIN_ROOM_RESPONSE);
            response.setSuccess(true);
            sendMessage(response);
        }

        private void handleLeaveRoom() {
            if (currentRoom != null) {
                currentRoom.removePlayer(this);
                currentRoom = null;
                sendMessage(new Message(Message.MessageType.LEAVE_ROOM, "SERVER",
                        "방에서 나갔습니다."));
            }
        }

        private void handleReady(boolean ready) {
            if (currentRoom != null && !currentRoom.isGameRunning) {
                currentRoom.setReady(userId, ready);
            }
        }

        private void handleStartGameRequest() {
            if(currentRoom == null) return;

            if (!userId.equals(currentRoom.roomMaster)) {
                sendMessage(Message.createErrorMessage(Message.ErrorCode.NOT_ROOM_MASTER));
                return;
            }

            if (!currentRoom.canStartGame()) {
                sendMessage(Message.createErrorMessage(Message.ErrorCode.NOT_ENOUGH_PLAYERS,
                        "모든 플레이어가 준비되지 않았거나 인원(" + currentRoom.gameMode.getMaxPlayers() + "명)이 부족합니다."));
                return;
            }
            currentRoom.startGame();
        }

        private void handleKickPlayer(Message msg) {
            if (currentRoom == null || !userId.equals(currentRoom.roomMaster)) {
                sendMessage(Message.createErrorMessage(Message.ErrorCode.NOT_ROOM_MASTER));
                return;
            }

            String targetUserId = msg.getTargetUserId();
            if (targetUserId == null || targetUserId.equals(userId)) return;

            ClientHandler targetPlayer = null;
            for (ClientHandler p : currentRoom.players) {
                if (p.userId.equals(targetUserId)) {
                    targetPlayer = p;
                    break;
                }
            }

            if (targetPlayer != null) {
                Message kickMsg = new Message(Message.MessageType.ROOM_INFO_UPDATE, "SERVER",
                        "방장에 의해 강제 퇴장되었습니다.");
                targetPlayer.sendMessage(kickMsg);

                currentRoom.removePlayer(targetPlayer);
                targetPlayer.currentRoom = null;

                printDisplay(userId + "가 " + targetUserId + "를 강제 퇴장시킴");
            }
        }

        private void handleGuess(Message msg) {
            if (currentRoom != null && currentRoom.isGameRunning) {
                currentRoom.handleGuess(this, msg.getGuess());
            } else {
                sendMessage(Message.createErrorMessage(Message.ErrorCode.UNKNOWN_ERROR,
                        "현재 게임 중이 아니거나 방에 속해있지 않습니다."));
            }
        }

        private void handleRoomChat(Message msg) {
            if (currentRoom != null) {
                Message chatMsg = new Message(Message.MessageType.CHAT_ROOM, userId, msg.getContent());
                currentRoom.broadcastToRoom(chatMsg);
            }
        }

        private void handleTeamChat(Message msg) {
            if (currentRoom != null && currentRoom.gameMode == Message.GameMode.TWO_VS_TWO) {
                int myTeam = currentRoom.playerTeams.getOrDefault(userId, 0);
                if(myTeam == 0) return;

                Message chatMsg = new Message(Message.MessageType.CHAT_TEAM, userId, msg.getContent());

                for (ClientHandler p : currentRoom.players) {
                    if (currentRoom.playerTeams.getOrDefault(p.userId, 0) == myTeam) {
                        p.sendMessage(chatMsg);
                    }
                }
            }
        }

        private void handleAllChat(Message msg) {
            Message chatMsg = new Message(Message.MessageType.CHAT_ALL, userId, msg.getContent());
            for (ClientHandler client : clients) {
                client.sendMessage(chatMsg);
            }
        }

        private void handleWhisper(Message msg) {
            String targetUserId = msg.getTargetUserId();
            if(targetUserId == null || targetUserId.isEmpty()) return;

            ClientHandler targetClient = null;
            for (ClientHandler client : clients) {
                if (client.userId != null && client.userId.equals(targetUserId)) {
                    targetClient = client;
                    break;
                }
            }

            if (targetClient != null) {
                Message whisperMsg = new Message(Message.MessageType.CHAT_WHISPER, userId, msg.getContent());
                whisperMsg.setTargetUserId(targetUserId);
                targetClient.sendMessage(whisperMsg);
                sendMessage(whisperMsg);
            } else {
                sendMessage(Message.createErrorMessage(Message.ErrorCode.UNKNOWN_ERROR,
                        "사용자 '" + targetUserId + "'를 찾을 수 없습니다."));
            }
        }

        private void handleStatsRequest(Message msg) {
            String targetUserId = msg.getContent();
            if (targetUserId == null || targetUserId.isEmpty()) targetUserId = userId;

            try (BufferedReader br  = new BufferedReader(new FileReader(STATS_FILE))) {
                br.readLine();
                String line;

                while((line = br.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length >= 5 && parts[0].trim().equals(targetUserId)) {
                        Message response = new Message(Message.MessageType.STATS_RESPONSE, "SERVER");
                        Hashtable<String, String> stats = new Hashtable<>();
                        stats.put("userId", parts[0].trim());
                        stats.put("wins", parts[1].trim());
                        stats.put("losses", parts[2].trim());
                        stats.put("draws", parts[3].trim());
                        stats.put("winRate", parts[4].trim());

                        response.setData(stats);
                        sendMessage(response);
                        return;
                    }
                }

                Message response = new Message(Message.MessageType.STATS_RESPONSE, "SERVER");
                Hashtable<String, String> stats = new Hashtable<>();
                stats.put("userId", targetUserId);
                stats.put("wins", "0");
                stats.put("losses", "0");
                stats.put("draws", "0");
                stats.put("winRate", "0.0");
                response.setData(stats);
                sendMessage(response);
            } catch (IOException e) {
                printDisplay("전적 조회 오류: " + e.getMessage());
            }
        }

        private void handleGameHistoryRequest(Message msg) {
            try (BufferedReader br = new BufferedReader(new FileReader(HISTORY_FILE))) {
                Vector<Hashtable<String, String>> historyList = new Vector<>();
                br.readLine();

                int count = 0;
                int maxRecords = 20;

                while((br.ready()) && count < maxRecords) {
                    String line = br.readLine();
                    String[] parts = line.split(",");
                    if(parts.length >= 6) {
                        Hashtable<String, String> record = new Hashtable<>();
                        record.put("gameId", parts[0]);
                        record.put("timestamp", parts[1]);
                        record.put("participants", parts[2].replace("\"", ""));
                        record.put("gameMode", parts[3]);
                        record.put("difficulty", parts[4]);
                        record.put("winner", parts[5]);
                        historyList.add(record);
                        count++;
                    }
                }

                Message response = new Message(Message.MessageType.GAME_HISTORY_RESPONSE, "SERVER");
                response.setData(historyList);
                sendMessage(response);
            } catch (IOException e) {
                printDisplay("게임 기록 조회 오류: " + e.getMessage());
            }
        }

        private void sendMessage(Message msg) {
            try {
                out.writeObject(msg);
                out.flush();
            } catch (IOException e) {
                printDisplay("메시지 전송 오류 (" + userId + "): " + e.getMessage());
            }
        }

        public void close() {
            try {
                if (currentRoom != null && currentRoom.isGameRunning) {
                    if (currentRoom.gameMode == Message.GameMode.ONE_VS_ONE) {
                        for (ClientHandler p : currentRoom.players) {
                            if(!p.userId.equals(userId)) {
                                currentRoom.endGame(p.userId, false, 0);
                                break;
                            }
                        }
                    } else {
                        int disconnectedTeam = currentRoom.playerTeams.getOrDefault(userId, 0);
                        if (disconnectedTeam != 0) {
                            int winnerTeam = (disconnectedTeam == 1) ? 2 : 1;
                            currentRoom.endGame(null, false, winnerTeam);
                        }
                    }
                }

                if (currentRoom != null) {
                    currentRoom.removePlayer(this);
                    currentRoom = null;
                }

                if(userId != null) {
                    clients.remove(this);
                    printDisplay(userId + " 연결 종료");
                }

                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                printDisplay("소켓 종료 오류: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        new BaseballServerGUI(54321);
    }
}