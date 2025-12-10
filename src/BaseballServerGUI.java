import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class BaseballServerGUI extends JFrame {
    private int port = 54321;
    private ServerSocket serverSocket;

    // GUI 컴포넌트
    private JTextArea t_display;
    private JButton b_start, b_stop;

    // 접속한 클라이언트 관리
    private Vector<ClientHandler> clients = new Vector<>();
    private int maxClients = 10; // 최대 동시 접속자 수

    // 방 관리 (방번호 -> 방객체)
    private Vector<GameRoom> rooms = new Vector<>();
    private int nextRoomId = 1;

    // 데이터 파일 경로
    private static final String USERS_FILE = "server_data/users.csv";
    private static final String STATS_FILE = "server_data/user_stats.csv";
    private static final String HISTORY_FILE = "server_data/game_history.csv";
    private static final String DETAILS_FILE = "server_data/game_details.csv";

    public BaseballServerGUI(int port) {
        super("Baseball Game Server");
        this.port = port;

        // 데이터 디렉토리 및 파일 초기화
        initDataFiles();

        buildGUI();
        setSize(700, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);
    }

    // 데이터 파일 초기화
    private void initDataFiles() {
        // 디렉토리 생성
        new File("server_data").mkdirs();

        // 파일이 없으면 헤더와 함께 생성
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
                printDisplay("파일 생성: " + filePath);
            } catch (IOException e) {
                printDisplay("파일 생성 실패: " + filePath);
            }
        }
    }

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

        b_start.addActionListener(e -> startServer());
        b_stop.addActionListener(e -> stopServer());

        btnPanel.add(b_start);
        btnPanel.add(b_stop);
        add(btnPanel, BorderLayout.SOUTH);
    }

    // 서버 시작
    private void startServer() {
        Thread acceptThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                printDisplay("서버 시작 (포트: " + port + ")");

                b_start.setEnabled(false);
                b_stop.setEnabled(true);

                // 클라이언트 접속 대기
                while (true) {
                    Socket socket = serverSocket.accept();

                    // 최대 접속자 수 체크
                    if (clients.size() >= maxClients) {
                        printDisplay("최대 접속자 수 초과. 연결 거부: " + socket.getInetAddress());
                        ObjectOutputStream tempOut = new ObjectOutputStream(socket.getOutputStream());
                        tempOut.writeObject(Message.createErrorMessage(Message.ErrorCode.SERVER_FULL));
                        tempOut.flush();
                        socket.close();
                        continue;
                    }

                    printDisplay("클라이언트 연결: " + socket.getInetAddress());

                    ClientHandler handler = new ClientHandler(socket);
                    clients.add(handler);
                    new Thread(handler).start();
                }
            } catch (IOException e) {
                printDisplay("서버 종료됨");
            }
        });
        acceptThread.start();
    }

    // 서버 중지
    private void stopServer() {
        try {
            // 모든 클라이언트 연결 종료
            for (ClientHandler client : clients) {
                client.close();
            }
            clients.clear();

            // 서버 소켓 닫기
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            printDisplay("서버 중지");
            b_stop.setEnabled(false);
            b_start.setEnabled(true);
        } catch (IOException e) {
            printDisplay("서버 종료 오류: " + e.getMessage());
        }
    }

    // 로그 출력
    private void printDisplay(String msg) {
        t_display.append(msg + "\n");
        t_display.setCaretPosition(t_display.getDocument().getLength());
    }

    // ==================== 인증 관련 메서드 ====================

    // 회원가입
    private boolean registerUser(String userId, String password, String character) {
        // ID 중복 체크
        if (isUserExists(userId)) {
            return false;
        }

        // users.csv에 저장
        try {
            FileWriter fw = new FileWriter(USERS_FILE, true);
            fw.write(userId + "," + password + "," + character + "\n");
            fw.close();

            // 전적 초기화
            FileWriter statsFw = new FileWriter(STATS_FILE, true);
            statsFw.write(userId + ",0,0,0,0.0\n");
            statsFw.close();

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
            if (!file.exists()) {
                return false;
            }

            BufferedReader br = new BufferedReader(new FileReader(USERS_FILE));
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts[0].equals(userId)) {
                    br.close();
                    return true;
                }
            }
            br.close();
        } catch (IOException e) {
            printDisplay("파일 읽기 오류: " + e.getMessage());
        }
        return false;
    }

    // 로그인 인증
    private boolean authenticateUser(String userId, String password) {
        try {
            File file = new File(USERS_FILE);
            if (!file.exists()) {
                return false;
            }

            BufferedReader br = new BufferedReader(new FileReader(USERS_FILE));
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts[0].equals(userId) && parts[1].equals(password)) {
                    br.close();
                    return true;
                }
            }
            br.close();
        } catch (IOException e) {
            printDisplay("인증 오류: " + e.getMessage());
        }
        return false;
    }

    // 중복 로그인 체크
    private boolean isAlreadyLoggedIn(String userId) {
        for (ClientHandler client : clients) {
            if (client.userId != null && client.userId.equals(userId)) {
                return true;
            }
        }
        return false;
    }

    // ==================== 방 관련 메서드 ====================

    // 방 생성
    private GameRoom createRoom(String roomName, String masterUserId,
                                Message.GameMode gameMode, Message.Difficulty difficulty,
                                Message.TurnTimeLimit turnTimeLimit, boolean isPrivate, String roomPassword) {

        if (rooms.size() >= 5) {
            return null; // 최대 5개 제한
        }

        GameRoom room = new GameRoom(nextRoomId++, roomName, masterUserId,
                gameMode, difficulty, turnTimeLimit, isPrivate, roomPassword);
        rooms.add(room);
        printDisplay("방 생성: [" + room.roomId + "] " + roomName);
        return room;
    }

    // 방 찾기
    private GameRoom findRoom(int roomId) {
        for (GameRoom room : rooms) {
            if (room.roomId == roomId) {
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

    // ==================== 게임 로직 ====================

    // 정답 생성 (1~9 중 중복 없이)
    private String generateAnswer(int digitCount) {
        Vector<Integer> numbers = new Vector<>();
        for (int i = 1; i <= 9; i++) {
            numbers.add(i);
        }

        // 섞기 (Fisher-Yates shuffle 알고리즘)
        for (int i = 0; i < numbers.size(); i++) {
            int randomIdx = (int)(Math.random() * numbers.size());
            int temp = numbers.get(i);
            numbers.set(i, numbers.get(randomIdx));
            numbers.set(randomIdx, temp);
        }

        // 필요한 자릿수만큼 추출
        String answer = "";
        for (int i = 0; i < digitCount; i++) {
            answer += numbers.get(i);
        }
        return answer;
    }

    // 스트라이크, 볼 계산
    private int[] calculateResult(String target, String guess) {
        int strike = 0;
        int ball = 0;

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

    // 입력 검증
    private boolean isValidGuess(String guess, int digitCount) {
        // 길이 체크
        if (guess.length() != digitCount) {
            return false;
        }

        // 숫자인지 체크 & 중복 체크
        boolean[] used = new boolean[10];
        for (int i = 0; i < guess.length(); i++) {
            char c = guess.charAt(i);

            // 숫자가 아니거나 0이면 false
            if (c < '1' || c > '9') {
                return false;
            }

            // 중복 체크
            int digit = c - '0';
            if (used[digit]) {
                return false;
            }
            used[digit] = true;
        }

        return true;
    }

    // ==================== 내부 클래스: GameRoom ====================

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

        // 게임 진행 상태
        boolean isGameRunning = false;
        Hashtable<String, String> playerAnswers = new Hashtable<>();
        Hashtable<String, Integer> playerTeams = new Hashtable<>(); // 플레이어 -> 팀번호 (1 or 2)
        int currentRound = 1;
        boolean isTopHalf = true; // true: 초공, false: 말공
        String gameId; // 게임 기록용 ID

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

        // 플레이어 추가
        public boolean addPlayer(ClientHandler player) {
            if (players.size() >= gameMode.getMaxPlayers()) {
                return false;
            }

            players.add(player);
            readyStatus.put(player.userId, false);

            // 2v2 모드면 팀 배정
            if (gameMode == Message.GameMode.TWO_VS_TWO) {
                int teamNum = (players.size() <= 2) ? 1 : 2;
                playerTeams.put(player.userId, teamNum);
                printDisplay(player.userId + " -> Team " + teamNum);
            }

            Message msg = new Message(Message.MessageType.ROOM_INFO_UPDATE, "SERVER",
                    player.userId + "님이 입장했습니다.");
            broadcastToRoom(msg);
            return true;
        }

        // 플레이어 제거
        public void removePlayer(ClientHandler player) {
            players.remove(player);
            readyStatus.remove(player.userId);

            // 방이 비었으면 삭제
            if (players.isEmpty()) {
                BaseballServerGUI.this.removeRoom(this);
                return;
            }

            // 방장이 나가면 위임
            if (player.userId.equals(roomMaster)) {
                roomMaster = players.get(0).userId;
                Message msg = new Message(Message.MessageType.ROOM_INFO_UPDATE, "SERVER",
                        roomMaster + "님이 새로운 방장이 되었습니다.");
                broadcastToRoom(msg);
            } else {
                Message msg = new Message(Message.MessageType.ROOM_INFO_UPDATE, "SERVER",
                        player.userId + "님이 퇴장했습니다.");
                broadcastToRoom(msg);
            }
        }

        // 준비 상태 변경
        public void setReady(String userId, boolean ready) {
            readyStatus.put(userId, ready);
            String msg = userId + "님이 " + (ready ? "준비완료" : "준비취소") + " 했습니다.";
            broadcastToRoom(new Message(Message.MessageType.READY_STATUS_UPDATE, "SERVER", msg));
        }

        // 게임 시작 가능한지 체크
        public boolean canStartGame() {
            // 인원 체크
            if (players.size() != gameMode.getMaxPlayers()) {
                return false;
            }

            // 방장 제외 모두 준비 완료인지 체크
            for (int i = 0; i < players.size(); i++) {
                String userId = players.get(i).userId;
                if (!userId.equals(roomMaster)) {
                    Boolean ready = readyStatus.get(userId);
                    if (ready == null || !ready) {
                        return false;
                    }
                }
            }
            return true;
        }

        // 게임 시작
        public void startGame() {
            isGameRunning = true;
            gameId = "G" + System.currentTimeMillis();

            if (gameMode == Message.GameMode.ONE_VS_ONE) {
                // 1v1: 각 플레이어에게 정답 생성
                for (int i = 0; i < players.size(); i++) {
                    ClientHandler player = players.get(i);
                    String answer = generateAnswer(difficulty.getDigitCount());
                    playerAnswers.put(player.userId, answer);
                    printDisplay("게임 시작 - " + player.userId + "의 정답: " + answer);
                }
            } else {
                // 2v2: 각 팀에 하나의 정답 생성
                String team1Answer = generateAnswer(difficulty.getDigitCount());
                String team2Answer = generateAnswer(difficulty.getDigitCount());

                for (int i = 0; i < players.size(); i++) {
                    ClientHandler player = players.get(i);
                    int teamNum = playerTeams.get(player.userId);
                    String answer = (teamNum == 1) ? team1Answer : team2Answer;
                    playerAnswers.put(player.userId, answer);
                    printDisplay("게임 시작 - " + player.userId + " (Team " + teamNum + ")의 정답: " + answer);
                }
            }

            currentRound = 1;
            isTopHalf = true;

            // 게임 시작 알림
            Message startMsg = new Message(Message.MessageType.START_GAME, "SERVER");
            startMsg.setGameMode(gameMode);
            startMsg.setDifficulty(difficulty);
            startMsg.setContent("게임이 시작되었습니다!");
            broadcastToRoom(startMsg);

            // 턴 정보 전송
            sendTurnInfo();
        }

        // 턴 정보 전송
        public void sendTurnInfo() {
            Message turnMsg = new Message(Message.MessageType.TURN_INFO, "SERVER");
            turnMsg.setRound(currentRound);
            turnMsg.setTop(isTopHalf);
            turnMsg.setContent(currentRound + "회 " + (isTopHalf ? "초" : "말"));
            broadcastToRoom(turnMsg);
        }

        // 추측 처리
        public void handleGuess(ClientHandler player, String guess) {
            if (!isGameRunning) {
                return;
            }

            // 입력 검증
            if (!isValidGuess(guess, difficulty.getDigitCount())) {
                player.sendMessage(Message.createErrorMessage(
                        Message.ErrorCode.INVALID_INPUT_FORMAT));
                return;
            }

            String targetAnswer = null;

            if (gameMode == Message.GameMode.ONE_VS_ONE) {
                // 1v1: 상대방 정답 찾기
                for (int i = 0; i < players.size(); i++) {
                    ClientHandler p = players.get(i);
                    if (!p.userId.equals(player.userId)) {
                        targetAnswer = playerAnswers.get(p.userId);
                        break;
                    }
                }
            } else {
                // 2v2: 상대 팀 정답 찾기
                int myTeam = playerTeams.get(player.userId);
                int targetTeam = (myTeam == 1) ? 2 : 1;

                // 상대 팀원 중 아무나의 정답 (팀은 같은 정답 공유)
                for (int i = 0; i < players.size(); i++) {
                    ClientHandler p = players.get(i);
                    if (playerTeams.get(p.userId) == targetTeam) {
                        targetAnswer = playerAnswers.get(p.userId);
                        break;
                    }
                }
            }

            if (targetAnswer == null) {
                return;
            }

            // 결과 계산
            int[] result = calculateResult(targetAnswer, guess);
            int strike = result[0];
            int ball = result[1];

            // 결과 전송
            Message resultMsg = Message.createGuessResult(player.userId, guess, strike, ball);

            if (gameMode == Message.GameMode.TWO_VS_TWO) {
                // 2v2: 같은 팀원끼리만 공유
                int myTeam = playerTeams.get(player.userId);
                for (int i = 0; i < players.size(); i++) {
                    ClientHandler p = players.get(i);
                    if (playerTeams.get(p.userId) == myTeam) {
                        p.sendMessage(resultMsg);
                    }
                }
            } else {
                // 1v1: 모두에게 전송
                broadcastToRoom(resultMsg);
            }

            // 게임 상세 기록 저장
            saveGameDetail(gameId, currentRound, player.userId, guess, strike + "S " + ball + "B");

            // 승리 체크
            if (strike == difficulty.getDigitCount()) {
                if (gameMode == Message.GameMode.TWO_VS_TWO) {
                    int winnerTeam = playerTeams.get(player.userId);
                    endGame(player.userId, false, winnerTeam);
                } else {
                    endGame(player.userId, false, 0);
                }
                return;
            }

            // 다음 턴
            nextTurn();
        }

        // 다음 턴
        private void nextTurn() {
            if (isTopHalf) {
                isTopHalf = false;
            } else {
                isTopHalf = true;
                currentRound++;
            }

            // 9회말 종료면 무승부
            if (currentRound > 9) {
                endGame(null, true, 0);
            } else {
                sendTurnInfo();
            }
        }

        // 게임 종료
        private void endGame(String winnerId, boolean isDraw, int winnerTeam) {
            isGameRunning = false;

            Message endMsg = new Message(Message.MessageType.END_GAME, "SERVER");
            if (isDraw) {
                endMsg.setDraw(true);
                endMsg.setContent("9회말 종료! 무승부입니다.");
            } else {
                endMsg.setWinnerId(winnerId);
                if (gameMode == Message.GameMode.TWO_VS_TWO) {
                    endMsg.setWinnerTeam(winnerTeam);
                    endMsg.setContent("Team " + winnerTeam + " 승리! (" + winnerId + "님이 맞춤)");
                } else {
                    endMsg.setContent(winnerId + "님이 승리했습니다!");
                }
            }
            broadcastToRoom(endMsg);

            // 게임 기록 저장
            saveGameHistory(winnerId, isDraw, winnerTeam);

            // 준비 상태 초기화
            for (int i = 0; i < players.size(); i++) {
                readyStatus.put(players.get(i).userId, false);
            }
        }

        // 게임 기록 저장 (game_history.csv)
        private void saveGameHistory(String winnerId, boolean isDraw, int winnerTeam) {
            try {
                String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());

                // 참여자 목록
                String participants = "";
                for (int i = 0; i < players.size(); i++) {
                    if (i > 0) participants += ",";
                    participants += players.get(i).userId;
                }

                String winner;
                if (isDraw) {
                    winner = "Draw";
                } else if (gameMode == Message.GameMode.TWO_VS_TWO) {
                    winner = "Team" + winnerTeam;
                } else {
                    winner = winnerId;
                }

                FileWriter fw = new FileWriter(HISTORY_FILE, true);
                fw.write(gameId + "," + timestamp + ",\"" + participants + "\"," +
                        gameMode.getDisplayName() + "," + difficulty.getDisplayName() + "," + winner + "\n");
                fw.close();

                printDisplay("게임 기록 저장: " + gameId);
            } catch (IOException e) {
                printDisplay("게임 기록 저장 실패: " + e.getMessage());
            }
        }

        // 게임 상세 기록 저장 (game_details.csv)
        private void saveGameDetail(String gameId, int round, String playerId, String guess, String result) {
            try {
                FileWriter fw = new FileWriter(DETAILS_FILE, true);
                fw.write(gameId + "," + round + "," + playerId + "," + guess + "," + result + "\n");
                fw.close();
            } catch (IOException e) {
                printDisplay("게임 상세 기록 저장 실패: " + e.getMessage());
            }
        }

        // 방 전체에 메시지 전송
        public void broadcastToRoom(Message msg) {
            for (int i = 0; i < players.size(); i++) {
                players.get(i).sendMessage(msg);
            }
        }
    }

    // ==================== 내부 클래스: ClientHandler ====================

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

        @Override
        public void run() {
            try {
                Message msg;
                while ((msg = (Message) in.readObject()) != null) {
                    handleMessage(msg);
                }
            } catch (IOException e) {
                printDisplay(userId + " 연결 종료");
            } catch (ClassNotFoundException e) {
                printDisplay("메시지 클래스 오류: " + e.getMessage());
            } finally {
                close();
            }
        }

        // 메시지 처리
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
            }
        }

        // 로그인 처리
        private void handleLogin(Message msg) {
            String userId = msg.getUserId();
            String password = msg.getPassword();

            // 중복 로그인 체크
            if (isAlreadyLoggedIn(userId)) {
                sendMessage(Message.createErrorMessage(Message.ErrorCode.ALREADY_LOGGED_IN));
                return;
            }

            // 인증
            if (authenticateUser(userId, password)) {
                this.userId = userId;
                Message response = new Message(Message.MessageType.LOGIN_RESPONSE, userId);
                response.setSuccess(true);
                response.setContent("로그인 성공");
                sendMessage(response);
                printDisplay(userId + " 로그인 성공");
            } else {
                sendMessage(Message.createErrorMessage(Message.ErrorCode.LOGIN_FAILED));
            }
        }

        // 회원가입 처리
        private void handleRegister(Message msg) {
            String userId = msg.getUserId();
            String password = msg.getPassword();
            String character = msg.getCharacter();

            if (registerUser(userId, password, character)) {
                Message response = new Message(Message.MessageType.REGISTER_RESPONSE, userId);
                response.setSuccess(true);
                response.setContent("회원가입 성공");
                sendMessage(response);
                printDisplay(userId + " 회원가입 성공");
            } else {
                sendMessage(Message.createErrorMessage(Message.ErrorCode.DUPLICATE_ID));
            }
        }

        // 방 목록 요청 처리
        private void handleRoomListRequest() {
            Vector<Hashtable<String, Object>> roomList = new Vector<>();

            for (int i = 0; i < rooms.size(); i++) {
                GameRoom room = rooms.get(i);
                Hashtable<String, Object> roomInfo = new Hashtable<>();
                roomInfo.put("roomId", room.roomId);
                roomInfo.put("roomName", room.roomName);
                roomInfo.put("roomMaster", room.roomMaster);
                roomInfo.put("currentPlayers", room.players.size());
                roomInfo.put("maxPlayers", room.gameMode.getMaxPlayers());
                roomInfo.put("gameMode", room.gameMode.getDisplayName());
                roomInfo.put("difficulty", room.difficulty.getDisplayName());
                roomInfo.put("turnTimeLimit", room.turnTimeLimit.getDisplayName());
                roomInfo.put("isPrivate", room.isPrivate);
                roomInfo.put("isGameRunning", room.isGameRunning);
                roomList.add(roomInfo);
            }

            Message response = new Message(Message.MessageType.ROOM_LIST_RESPONSE, "SERVER");
            response.setData(roomList);
            sendMessage(response);
        }

        // 방 생성 처리
        private void handleCreateRoom(Message msg) {
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

                Message response = new Message(Message.MessageType.CREATE_ROOM_RESPONSE, "SERVER");
                response.setSuccess(true);
                response.setRoomId(room.roomId);
                sendMessage(response);
            } else {
                sendMessage(Message.createErrorMessage(Message.ErrorCode.UNKNOWN_ERROR,
                        "방 생성 실패 (최대 5개)"));
            }
        }

        // 방 입장 처리
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

            // 비공개 방 비밀번호 확인
            if (room.isPrivate) {
                String inputPassword = msg.getRoomPassword();
                if (inputPassword == null || !inputPassword.equals(room.roomPassword)) {
                    sendMessage(Message.createErrorMessage(Message.ErrorCode.WRONG_PASSWORD));
                    return;
                }
            }

            currentRoom = room;
            room.addPlayer(this);

            Message response = new Message(Message.MessageType.JOIN_ROOM_RESPONSE, "SERVER");
            response.setSuccess(true);
            response.setRoomId(roomId);
            sendMessage(response);
        }

        // 방 나가기 처리
        private void handleLeaveRoom() {
            if (currentRoom != null) {
                currentRoom.removePlayer(this);
                currentRoom = null;
                sendMessage(new Message(Message.MessageType.LEAVE_ROOM, "SERVER",
                        "방에서 나갔습니다."));
            }
        }

        // 준비 처리
        private void handleReady(boolean ready) {
            if (currentRoom != null && !userId.equals(currentRoom.roomMaster)) {
                currentRoom.setReady(userId, ready);
            }
        }

        // 게임 시작 요청 처리
        private void handleStartGameRequest() {
            if (currentRoom == null) {
                return;
            }

            if (!userId.equals(currentRoom.roomMaster)) {
                sendMessage(Message.createErrorMessage(Message.ErrorCode.NOT_ROOM_MASTER));
                return;
            }

            if (!currentRoom.canStartGame()) {
                sendMessage(Message.createErrorMessage(Message.ErrorCode.NOT_ENOUGH_PLAYERS,
                        "모든 플레이어가 준비되지 않았습니다."));
                return;
            }

            currentRoom.startGame();
        }

        // 강제 퇴장 처리 (방장 권한)
        private void handleKickPlayer(Message msg) {
            if (currentRoom == null) {
                return;
            }

            // 방장만 가능
            if (!userId.equals(currentRoom.roomMaster)) {
                sendMessage(Message.createErrorMessage(Message.ErrorCode.NOT_ROOM_MASTER));
                return;
            }

            String targetUserId = msg.getTargetUserId();
            if (targetUserId == null || targetUserId.equals(userId)) {
                return; // 자기 자신은 강퇴 불가
            }

            // 대상 플레이어 찾기
            ClientHandler targetPlayer = null;
            for (int i = 0; i < currentRoom.players.size(); i++) {
                ClientHandler p = currentRoom.players.get(i);
                if (p.userId.equals(targetUserId)) {
                    targetPlayer = p;
                    break;
                }
            }

            if (targetPlayer != null) {
                // 강퇴 메시지 전송
                Message kickMsg = new Message(Message.MessageType.ROOM_INFO_UPDATE, "SERVER",
                        "방장에 의해 강제 퇴장되었습니다.");
                targetPlayer.sendMessage(kickMsg);

                // 방에서 제거
                currentRoom.removePlayer(targetPlayer);
                targetPlayer.currentRoom = null;

                printDisplay(userId + "가 " + targetUserId + "를 강제 퇴장시킴");
            }
        }

        // 추측 처리
        private void handleGuess(Message msg) {
            if (currentRoom != null && currentRoom.isGameRunning) {
                currentRoom.handleGuess(this, msg.getGuess());
            }
        }

        // 방 채팅 처리
        private void handleRoomChat(Message msg) {
            if (currentRoom != null) {
                Message chatMsg = new Message(Message.MessageType.CHAT_ROOM, userId, msg.getContent());
                currentRoom.broadcastToRoom(chatMsg);
            }
        }

        // 팀 채팅 처리 (2v2 전용)
        private void handleTeamChat(Message msg) {
            if (currentRoom != null && currentRoom.gameMode == Message.GameMode.TWO_VS_TWO) {
                int myTeam = currentRoom.playerTeams.get(userId);
                Message chatMsg = new Message(Message.MessageType.CHAT_TEAM, userId, msg.getContent());

                // 같은 팀원에게만 전송
                for (int i = 0; i < currentRoom.players.size(); i++) {
                    ClientHandler p = currentRoom.players.get(i);
                    if (currentRoom.playerTeams.get(p.userId) == myTeam) {
                        p.sendMessage(chatMsg);
                    }
                }
            }
        }

        // 전체 채팅 처리
        private void handleAllChat(Message msg) {
            Message chatMsg = new Message(Message.MessageType.CHAT_ALL, userId, msg.getContent());
            for (int i = 0; i < clients.size(); i++) {
                clients.get(i).sendMessage(chatMsg);
            }
        }

        // 귓속말 처리
        private void handleWhisper(Message msg) {
            String targetUserId = msg.getTargetUserId();
            if (targetUserId == null || targetUserId.isEmpty()) {
                return;
            }

            // 대상 찾기
            ClientHandler targetClient = null;
            for (int i = 0; i < clients.size(); i++) {
                if (clients.get(i).userId != null && clients.get(i).userId.equals(targetUserId)) {
                    targetClient = clients.get(i);
                    break;
                }
            }

            if (targetClient != null) {
                Message whisperMsg = new Message(Message.MessageType.CHAT_WHISPER, userId, msg.getContent());
                whisperMsg.setTargetUserId(targetUserId);
                targetClient.sendMessage(whisperMsg);

                // 본인에게도 전송 (발신 확인)
                sendMessage(whisperMsg);
            } else {
                Message errorMsg = new Message(Message.MessageType.ERROR, "SERVER",
                        "사용자 '" + targetUserId + "'를 찾을 수 없습니다.");
                sendMessage(errorMsg);
            }
        }

        // 전적 조회 처리
        private void handleStatsRequest(Message msg) {
            String targetUserId = msg.getContent();
            if (targetUserId == null || targetUserId.isEmpty()) {
                targetUserId = userId; // 본인 전적 조회
            }

            try {
                BufferedReader br = new BufferedReader(new FileReader(STATS_FILE));
                String line;
                br.readLine(); // 헤더 스킵

                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts[0].equals(targetUserId)) {
                        Message response = new Message(Message.MessageType.STATS_RESPONSE, "SERVER");

                        Hashtable<String, String> stats = new Hashtable<>();
                        stats.put("userId", parts[0]);
                        stats.put("wins", parts[1]);
                        stats.put("losses", parts[2]);
                        stats.put("draws", parts[3]);
                        stats.put("winRate", parts[4]);

                        response.setData(stats);
                        sendMessage(response);
                        br.close();
                        return;
                    }
                }
                br.close();

                // 전적이 없는 경우
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

        // 게임 기록 조회 처리
        private void handleGameHistoryRequest(Message msg) {
            try {
                Vector<Hashtable<String, String>> historyList = new Vector<>();
                BufferedReader br = new BufferedReader(new FileReader(HISTORY_FILE));
                String line;
                br.readLine(); // 헤더 스킵

                int count = 0;
                int maxRecords = 20; // 최근 20개만

                while ((line = br.readLine()) != null && count < maxRecords) {
                    String[] parts = line.split(",");
                    if (parts.length >= 6) {
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
                br.close();

                Message response = new Message(Message.MessageType.GAME_HISTORY_RESPONSE, "SERVER");
                response.setData(historyList);
                sendMessage(response);

            } catch (IOException e) {
                printDisplay("게임 기록 조회 오류: " + e.getMessage());
            }
        }

        // 메시지 전송
        public void sendMessage(Message msg) {
            try {
                out.writeObject(msg);
                out.flush();
            } catch (IOException e) {
                printDisplay("메시지 전송 오류(" + userId + "): " + e.getMessage());
            }
        }

        // 연결 종료
        public void close() {
            try {
                // 게임 중이었다면 자동 패배 처리
                if (currentRoom != null && currentRoom.isGameRunning) {
                    if (currentRoom.gameMode == Message.GameMode.ONE_VS_ONE) {
                        // 1v1: 끊긴 플레이어 패배
                        for (int i = 0; i < currentRoom.players.size(); i++) {
                            ClientHandler p = currentRoom.players.get(i);
                            if (!p.userId.equals(userId)) {
                                currentRoom.endGame(p.userId, false, 0);
                                break;
                            }
                        }
                    } else {
                        // 2v2: 끊긴 플레이어의 팀 패배
                        int disconnectedTeam = currentRoom.playerTeams.get(userId);
                        int winnerTeam = (disconnectedTeam == 1) ? 2 : 1;

                        Message msg = new Message(Message.MessageType.END_GAME, "SERVER");
                        msg.setWinnerTeam(winnerTeam);
                        msg.setContent("상대 팀원이 접속 종료하여 Team " + winnerTeam + " 승리!");
                        currentRoom.broadcastToRoom(msg);
                    }
                }

                if (currentRoom != null) {
                    currentRoom.removePlayer(this);
                    currentRoom = null;
                }

                if (userId != null) {
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

    // ==================== main 메서드 ====================

    public static void main(String[] args) {
        new BaseballServerGUI(54321);
    }
}