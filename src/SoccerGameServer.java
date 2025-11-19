import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class SoccerGameServer {
    private static final int PORT = 54322;
    private static final int MAX_PLAYERS = 4;
    private static final int FIELD_WIDTH = 1200;
    private static final int FIELD_HEIGHT = 800;
    private static final int MAX_SCORE = 5;
    private static final long GAME_TIME = 120000; // 2분
    
    private ServerSocket serverSocket;
    private List<PlayerHandler> players = Collections.synchronizedList(new ArrayList<>());
    private GameState gameState;
    private long gameStartTime = 0;
    
    class GameState {
        double ballX = FIELD_WIDTH / 2.0;
        double ballY = FIELD_HEIGHT / 2.0;
        double ballVelX = 0;
        double ballVelY = 0;
        int team1Score = 0;
        int team2Score = 0;
        boolean gameStarted = false;
        int readyCount = 0;
    }
    
    class PlayerHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String playerName;
        private int team;
        private double x, y;
        private int playerIndex;
        private String playerImage = "";
        private boolean ready = false;
        
        public PlayerHandler(Socket socket, int playerIndex) {
            this.socket = socket;
            this.playerIndex = playerIndex;
            this.team = 0;
            this.x = FIELD_WIDTH / 2.0;
            this.y = FIELD_HEIGHT / 2.0;
        }
        
        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                
                playerName = in.readLine();
                out.println("INIT:" + playerIndex);
                
                broadcastPlayerList();
                sendGameState();
                
                String message;
                while ((message = in.readLine()) != null) {
                    handleMessage(message);
                }
            } catch (IOException e) {
                System.out.println("Player disconnected: " + playerName);
            } finally {
                cleanup();
            }
        }
        
        private void handleMessage(String message) {
            String[] parts = message.split(":");
            if (parts[0].equals("TEAM_SELECT")) {
                team = Integer.parseInt(parts[1]);
                playerImage = parts[2];
                x = (team == 1) ? FIELD_WIDTH / 4.0 : FIELD_WIDTH * 3.0 / 4.0;
                y = FIELD_HEIGHT / 2.0;
                broadcastMessage("PLAYER_UPDATE:" + playerIndex + ":" + playerName + ":" + team + ":" + playerImage);
            } else if (parts[0].equals("READY")) {
                ready = true;
                gameState.readyCount++;
                broadcastMessage("PLAYER_READY:" + playerIndex);
                checkGameStart();
            } else if (parts[0].equals("MOVE")) {
                x = Double.parseDouble(parts[1]);
                y = Double.parseDouble(parts[2]);
                broadcastMessage("PLAYER_MOVE:" + playerIndex + ":" + x + ":" + y);
            } else if (parts[0].equals("BALL")) {
                gameState.ballX = Double.parseDouble(parts[1]);
                gameState.ballY = Double.parseDouble(parts[2]);
                gameState.ballVelX = Double.parseDouble(parts[3]);
                gameState.ballVelY = Double.parseDouble(parts[4]);
                broadcastMessage("BALL_UPDATE:" + gameState.ballX + ":" + gameState.ballY + ":" + 
                               gameState.ballVelX + ":" + gameState.ballVelY);
            } else if (parts[0].equals("GOAL")) {
                int scoringTeam = Integer.parseInt(parts[1]);
                if (scoringTeam == 1) gameState.team1Score++;
                else gameState.team2Score++;
                broadcastMessage("SCORE:" + gameState.team1Score + ":" + gameState.team2Score);
                checkGameEnd();
            }
        }
        
        private void checkGameStart() {
            if (gameState.readyCount == players.size() && players.size() >= 2) {
                gameState.gameStarted = true;
                gameStartTime = System.currentTimeMillis();
                broadcastMessage("GAME_START");
                startGameTimer();
            }
        }
        
        private void checkGameEnd() {
            if (gameState.team1Score >= MAX_SCORE) {
                broadcastMessage("GAME_END:1");
            } else if (gameState.team2Score >= MAX_SCORE) {
                broadcastMessage("GAME_END:2");
            }
        }
        
        private void cleanup() {
            players.remove(this);
            broadcastMessage("PLAYER_LEFT:" + playerIndex);
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    private void broadcastMessage(String message) {
        synchronized (players) {
            for (PlayerHandler player : players) {
                try {
                    player.out.println(message);
                } catch (Exception e) {
                    System.out.println("Error broadcasting to player: " + e.getMessage());
                }
            }
        }
    }
    
    private void sendGameState() {
        synchronized (players) {
            for (int i = 0; i < players.size(); i++) {
                PlayerHandler p = players.get(i);
                for (PlayerHandler other : players) {
                    if (p != other) {
                        p.out.println("PLAYER_UPDATE:" + other.playerIndex + ":" + other.playerName + 
                                    ":" + other.team + ":" + other.playerImage);
                    }
                }
            }
        }
    }
    
    private void broadcastPlayerList() {
        StringBuilder playerList = new StringBuilder("PLAYER_LIST");
        synchronized (players) {
            for (PlayerHandler p : players) {
                playerList.append(":").append(p.playerIndex).append(",").append(p.playerName);
            }
        }
        broadcastMessage(playerList.toString());
    }
    
    private void startGameTimer() {
        new Thread(() -> {
            try {
                Thread.sleep(GAME_TIME);
                if (gameState.gameStarted) {
                    if (gameState.team1Score > gameState.team2Score) {
                        broadcastMessage("GAME_END:1");
                    } else if (gameState.team2Score > gameState.team1Score) {
                        broadcastMessage("GAME_END:2");
                    } else {
                        broadcastMessage("GAME_END:0");
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    public void start() {
        gameState = new GameState();
        
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Soccer Game Server started on port " + PORT);
            System.out.println("Waiting for " + MAX_PLAYERS + " players...");
            
            while (players.size() < MAX_PLAYERS) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Player " + (players.size() + 1) + " connected: " + clientSocket.getInetAddress());
                
                PlayerHandler handler = new PlayerHandler(clientSocket, players.size());
                players.add(handler);
                new Thread(handler).start();
                
                broadcastMessage("PLAYER_COUNT:" + players.size() + ":" + MAX_PLAYERS);
            }
            
            System.out.println("All players connected! Waiting for team selection...");
            
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        new SoccerGameServer().start();
    }
}
