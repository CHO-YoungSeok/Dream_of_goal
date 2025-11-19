import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.util.*;
import javax.imageio.ImageIO;

public class SoccerGameClient extends JFrame {
    private static final int SCREEN_WIDTH = 800;
    private static final int SCREEN_HEIGHT = 600;
    private static final int FIELD_WIDTH = 1200;
    private static final int FIELD_HEIGHT = 800;
    private static final int PLAYER_SIZE = 50;
    private static final int BALL_SIZE = 30;
    private static final double FRICTION = 0.98;
    private static final double PLAYER_SPEED = 5.0;
    private static final double KICK_POWER = 15.0;
    private static final int MAX_SCORE = 5;
    private static final long GAME_TIME = 120000;
    
    private JPanel currentPanel;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    
    private int myPlayerIndex;
    private int myTeam = 0;
    private String myPlayerName;
    private String myPlayerImage = "";
    private Player myPlayer;
    private Ball ball;
    private Map<Integer, Player> allPlayers = new HashMap<>();
    
    private Image fieldImage;
    private Image ballImage;
    private Image introImage;
    private Map<String, Image> playerImageMap = new HashMap<>();
    
    private boolean gameStarted = false;
    private boolean teamSelected = false;
    private int team1Score = 0;
    private int team2Score = 0;
    private int playerCount = 0;
    private int maxPlayers = 4;
    private long gameStartTime = 0;
    
    class Player {
        double x, y;
        int team;
        String name;
        int index;
        String imageName;
        
        Player(double x, double y, int team, String name, int index, String imageName) {
            this.x = x;
            this.y = y;
            this.team = team;
            this.name = name;
            this.index = index;
            this.imageName = imageName;
        }
    }
    
    class Ball {
        double x, y;
        double velX, velY;
        
        Ball(double x, double y) {
            this.x = x;
            this.y = y;
            this.velX = 0;
            this.velY = 0;
        }
        
        void update() {
            x += velX;
            y += velY;
            velX *= FRICTION;
            velY *= FRICTION;
            
            if (Math.abs(velX) < 0.1) velX = 0;
            if (Math.abs(velY) < 0.1) velY = 0;
            
            if (x < BALL_SIZE) x = BALL_SIZE;
            if (x > FIELD_WIDTH - BALL_SIZE) x = FIELD_WIDTH - BALL_SIZE;
            if (y < BALL_SIZE) y = BALL_SIZE;
            if (y > FIELD_HEIGHT - BALL_SIZE) y = FIELD_HEIGHT - BALL_SIZE;
            
            checkGoal();
        }
        
        void checkGoal() {
            boolean scored = false;
            int scoringTeam = 0;
            
            if (y > FIELD_HEIGHT / 2 - 100 && y < FIELD_HEIGHT / 2 + 100) {
                if (x < 50) {
                    scored = true;
                    scoringTeam = 2;
                } else if (x > FIELD_WIDTH - 50) {
                    scored = true;
                    scoringTeam = 1;
                }
            }
            
            if (scored) {
                sendMessage("GOAL:" + scoringTeam);
                reset();
            }
        }
        
        void reset() {
            x = FIELD_WIDTH / 2.0;
            y = FIELD_HEIGHT / 2.0;
            velX = 0;
            velY = 0;
        }
    }
    
    class TeamSelectPanel extends JPanel {
        private java.util.List<String> connectedPlayers = new ArrayList<>();
        private String[] availableImages = {"mario.webp", "nyan.webp", "ruigi.webp", "yoshi.webp"};
        private String selectedImage = availableImages[0];
        private int selectedTeam = 1;
        private JButton readyButton;
        private JTextArea playerListArea;
        
        TeamSelectPanel() {
            setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
            setBackground(new Color(50, 50, 50));
            setLayout(new BorderLayout());
            
            JPanel centerPanel = new JPanel();
            centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
            centerPanel.setBackground(new Color(50, 50, 50));
            
            JLabel titleLabel = new JLabel("팀 선택");
            titleLabel.setFont(new Font("Arial", Font.BOLD, 36));
            titleLabel.setForeground(Color.WHITE);
            titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            centerPanel.add(Box.createVerticalStrut(20));
            centerPanel.add(titleLabel);
            
            JLabel countLabel = new JLabel("플레이어: " + playerCount + "/" + maxPlayers);
            countLabel.setFont(new Font("Arial", Font.PLAIN, 20));
            countLabel.setForeground(Color.YELLOW);
            countLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            centerPanel.add(Box.createVerticalStrut(10));
            centerPanel.add(countLabel);
            
            playerListArea = new JTextArea(8, 30);
            playerListArea.setEditable(false);
            playerListArea.setFont(new Font("Arial", Font.PLAIN, 16));
            playerListArea.setBackground(new Color(30, 30, 30));
            playerListArea.setForeground(Color.WHITE);
            JScrollPane scrollPane = new JScrollPane(playerListArea);
            centerPanel.add(Box.createVerticalStrut(20));
            centerPanel.add(scrollPane);
            
            JPanel teamPanel = new JPanel();
            teamPanel.setBackground(new Color(50, 50, 50));
            JButton team1Btn = new JButton("팀 1 (레드)");
            JButton team2Btn = new JButton("팀 2 (블루)");
            team1Btn.setFont(new Font("Arial", Font.BOLD, 18));
            team2Btn.setFont(new Font("Arial", Font.BOLD, 18));
            team1Btn.setBackground(Color.RED);
            team2Btn.setBackground(Color.BLUE);
            team1Btn.setForeground(Color.WHITE);
            team2Btn.setForeground(Color.WHITE);
            
            team1Btn.addActionListener(e -> {
                selectedTeam = 1;
                team1Btn.setBorder(BorderFactory.createLineBorder(Color.YELLOW, 3));
                team2Btn.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
            });
            team2Btn.addActionListener(e -> {
                selectedTeam = 2;
                team2Btn.setBorder(BorderFactory.createLineBorder(Color.YELLOW, 3));
                team1Btn.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
            });
            
            team1Btn.setBorder(BorderFactory.createLineBorder(Color.YELLOW, 3));
            teamPanel.add(team1Btn);
            teamPanel.add(team2Btn);
            centerPanel.add(Box.createVerticalStrut(20));
            centerPanel.add(teamPanel);
            
            JPanel imagePanel = new JPanel();
            imagePanel.setBackground(new Color(50, 50, 50));
            JLabel imageLabel = new JLabel("캐릭터 선택:");
            imageLabel.setForeground(Color.WHITE);
            imageLabel.setFont(new Font("Arial", Font.PLAIN, 18));
            imagePanel.add(imageLabel);
            
            JComboBox<String> imageCombo = new JComboBox<>(availableImages);
            imageCombo.setFont(new Font("Arial", Font.PLAIN, 16));
            imageCombo.addActionListener(e -> selectedImage = (String) imageCombo.getSelectedItem());
            imagePanel.add(imageCombo);
            centerPanel.add(Box.createVerticalStrut(20));
            centerPanel.add(imagePanel);
            
            readyButton = new JButton("준비 완료");
            readyButton.setFont(new Font("Arial", Font.BOLD, 24));
            readyButton.setBackground(new Color(0, 200, 0));
            readyButton.setForeground(Color.WHITE);
            readyButton.setAlignmentX(Component.CENTER_ALIGNMENT);
            readyButton.addActionListener(e -> {
                myTeam = selectedTeam;
                myPlayerImage = selectedImage;
                sendMessage("TEAM_SELECT:" + myTeam + ":" + myPlayerImage);
                sendMessage("READY");
                readyButton.setEnabled(false);
                readyButton.setText("대기 중...");
                teamSelected = true;
            });
            
            centerPanel.add(Box.createVerticalStrut(30));
            centerPanel.add(readyButton);
            
            add(centerPanel, BorderLayout.CENTER);
        }
        
        void updatePlayerList(String playerList) {
            connectedPlayers.clear();
            String[] players = playerList.split(":");
            StringBuilder sb = new StringBuilder("접속한 플레이어:\n\n");
            for (int i = 1; i < players.length; i++) {
                String[] info = players[i].split(",");
                if (info.length == 2) {
                    connectedPlayers.add(info[1]);
                    sb.append((i) + ". " + info[1] + "\n");
                }
            }
            playerListArea.setText(sb.toString());
        }
    }
    
    class GamePanel extends JPanel {
        private Set<Integer> pressedKeys = new HashSet<>();
        
        GamePanel() {
            setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
            setBackground(Color.GREEN);
            setFocusable(true);
            
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    pressedKeys.add(e.getKeyCode());
                }
                
                @Override
                public void keyReleased(KeyEvent e) {
                    pressedKeys.remove(e.getKeyCode());
                }
            });
            
            javax.swing.Timer timer = new javax.swing.Timer(16, e -> {
                if (gameStarted && myPlayer != null) {
                    updatePlayer();
                    ball.update();
                    checkBallCollision();
                    repaint();
                }
            });
            timer.start();
        }
        
        private void updatePlayer() {
            double oldX = myPlayer.x;
            double oldY = myPlayer.y;
            
            if (pressedKeys.contains(KeyEvent.VK_LEFT) || pressedKeys.contains(KeyEvent.VK_A)) {
                myPlayer.x -= PLAYER_SPEED;
            }
            if (pressedKeys.contains(KeyEvent.VK_RIGHT) || pressedKeys.contains(KeyEvent.VK_D)) {
                myPlayer.x += PLAYER_SPEED;
            }
            if (pressedKeys.contains(KeyEvent.VK_UP) || pressedKeys.contains(KeyEvent.VK_W)) {
                myPlayer.y -= PLAYER_SPEED;
            }
            if (pressedKeys.contains(KeyEvent.VK_DOWN) || pressedKeys.contains(KeyEvent.VK_S)) {
                myPlayer.y += PLAYER_SPEED;
            }
            
            myPlayer.x = Math.max(PLAYER_SIZE / 2, Math.min(FIELD_WIDTH - PLAYER_SIZE / 2, myPlayer.x));
            myPlayer.y = Math.max(PLAYER_SIZE / 2, Math.min(FIELD_HEIGHT - PLAYER_SIZE / 2, myPlayer.y));
            
            if (oldX != myPlayer.x || oldY != myPlayer.y) {
                sendMessage("MOVE:" + myPlayer.x + ":" + myPlayer.y);
            }
        }
        
        private void checkBallCollision() {
            double dx = ball.x - myPlayer.x;
            double dy = ball.y - myPlayer.y;
            double distance = Math.sqrt(dx * dx + dy * dy);
            
            if (distance < PLAYER_SIZE / 2 + BALL_SIZE / 2) {
                double angle = Math.atan2(dy, dx);
                ball.velX = Math.cos(angle) * KICK_POWER;
                ball.velY = Math.sin(angle) * KICK_POWER;
                sendMessage("BALL:" + ball.x + ":" + ball.y + ":" + ball.velX + ":" + ball.velY);
            }
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            double cameraX = ball.x - SCREEN_WIDTH / 2.0;
            double cameraY = ball.y - SCREEN_HEIGHT / 2.0;
            
            cameraX = Math.max(0, Math.min(FIELD_WIDTH - SCREEN_WIDTH, cameraX));
            cameraY = Math.max(0, Math.min(FIELD_HEIGHT - SCREEN_HEIGHT, cameraY));
            
            g2d.translate(-cameraX, -cameraY);
            
            drawField(g2d);
            drawBall(g2d);
            drawPlayers(g2d);
            
            g2d.translate(cameraX, cameraY);
            drawUI(g2d);
        }
        
        private void drawField(Graphics2D g) {
            if (fieldImage != null) {
                g.drawImage(fieldImage, 0, 0, FIELD_WIDTH, FIELD_HEIGHT, null);
            } else {
                g.setColor(new Color(34, 139, 34));
                g.fillRect(0, 0, FIELD_WIDTH, FIELD_HEIGHT);
            }
            
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(3));
            g.drawRect(0, 0, FIELD_WIDTH, FIELD_HEIGHT);
            g.drawLine(FIELD_WIDTH / 2, 0, FIELD_WIDTH / 2, FIELD_HEIGHT);
            g.drawOval(FIELD_WIDTH / 2 - 80, FIELD_HEIGHT / 2 - 80, 160, 160);
            
            g.drawRect(0, FIELD_HEIGHT / 2 - 100, 80, 200);
            g.drawRect(FIELD_WIDTH - 80, FIELD_HEIGHT / 2 - 100, 80, 200);
        }
        
        private void drawBall(Graphics2D g) {
            if (ballImage != null) {
                int drawX = (int) ball.x - BALL_SIZE / 2;
                int drawY = (int) ball.y - BALL_SIZE / 2;
                g.drawImage(ballImage, drawX, drawY, BALL_SIZE, BALL_SIZE, null);
            } else {
                g.setColor(Color.WHITE);
                g.fillOval((int) ball.x - BALL_SIZE / 2, (int) ball.y - BALL_SIZE / 2, BALL_SIZE, BALL_SIZE);
            }
        }
        
        private void drawPlayers(Graphics2D g) {
            for (Player p : allPlayers.values()) {
                drawPlayer(g, p);
            }
        }
        
        private void drawPlayer(Graphics2D g, Player p) {
            int drawX = (int) p.x - PLAYER_SIZE / 2;
            int drawY = (int) p.y - PLAYER_SIZE / 2;
            
            Image img = playerImageMap.get(p.imageName);
            if (img != null) {
                g.drawImage(img, drawX, drawY, PLAYER_SIZE, PLAYER_SIZE, null);
            } else {
                g.setColor(p.team == 1 ? Color.RED : Color.BLUE);
                g.fillOval(drawX, drawY, PLAYER_SIZE, PLAYER_SIZE);
            }
            
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 14));
            FontMetrics fm = g.getFontMetrics();
            int textX = (int) p.x - fm.stringWidth(p.name) / 2;
            g.drawString(p.name, textX, (int) p.y - PLAYER_SIZE / 2 - 5);
        }
        
        private void drawUI(Graphics2D g) {
            g.setColor(new Color(0, 0, 0, 180));
            g.fillRect(10, 10, 250, 90);
            
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 22));
            g.drawString("Team 1: " + team1Score, 20, 40);
            g.drawString("Team 2: " + team2Score, 20, 70);
            
            long elapsed = System.currentTimeMillis() - gameStartTime;
            long remaining = Math.max(0, GAME_TIME - elapsed) / 1000;
            g.drawString("Time: " + remaining + "s", 20, 95);
        }
    }
    
    private void loadImages() {
        try {
            fieldImage = ImageIO.read(new File("src/image/feild.jpg"));
            ballImage = ImageIO.read(new File("src/image/ball.png"));
            introImage = ImageIO.read(new File("src/image/intro_image.jpg"));
            
            String[] imageNames = {"mario.webp", "nyan.webp", "ruigi.webp", "yoshi.webp"};
            for (String imgName : imageNames) {
                try {
                    Image img = ImageIO.read(new File("src/image/players/" + imgName));
                    playerImageMap.put(imgName, img);
                } catch (IOException e) {
                    System.out.println("Failed to load player image: " + imgName);
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to load images: " + e.getMessage());
        }
    }
    
    private void connectToServer(String serverAddress, int port, String playerName) {
        try {
            socket = new Socket(serverAddress, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            myPlayerName = playerName;
            out.println(playerName);
            
            new Thread(() -> {
                try {
                    String message;
                    while ((message = in.readLine()) != null) {
                        handleServerMessage(message);
                    }
                } catch (IOException e) {
                    System.out.println("Disconnected from server");
                }
            }).start();
            
        } catch (IOException e) {
            System.out.println("Failed to connect to server: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "서버에 연결할 수 없습니다: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private void handleServerMessage(String message) {
        String[] parts = message.split(":");
        
        switch (parts[0]) {
            case "INIT":
                myPlayerIndex = Integer.parseInt(parts[1]);
                break;
                
            case "PLAYER_COUNT":
                playerCount = Integer.parseInt(parts[1]);
                maxPlayers = Integer.parseInt(parts[2]);
                if (playerCount == maxPlayers && currentPanel instanceof TeamSelectPanel) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "모든 플레이어가 접속했습니다!\n팀을 선택하고 준비를 눌러주세요.");
                    });
                }
                break;
                
            case "PLAYER_LIST":
                if (currentPanel instanceof TeamSelectPanel) {
                    SwingUtilities.invokeLater(() -> ((TeamSelectPanel) currentPanel).updatePlayerList(message));
                }
                break;
                
            case "PLAYER_UPDATE":
                int index = Integer.parseInt(parts[1]);
                String name = parts[2];
                int team = Integer.parseInt(parts[3]);
                String imageName = parts[4];
                
                if (index == myPlayerIndex) {
                    double x = (team == 1) ? FIELD_WIDTH / 4.0 : FIELD_WIDTH * 3.0 / 4.0;
                    double y = FIELD_HEIGHT / 2.0;
                    myPlayer = new Player(x, y, team, myPlayerName, myPlayerIndex, imageName);
                    allPlayers.put(myPlayerIndex, myPlayer);
                } else {
                    double x = (team == 1) ? FIELD_WIDTH / 4.0 : FIELD_WIDTH * 3.0 / 4.0;
                    double y = FIELD_HEIGHT / 2.0;
                    Player p = new Player(x, y, team, name, index, imageName);
                    allPlayers.put(index, p);
                }
                break;
                
            case "PLAYER_MOVE":
                int pIndex = Integer.parseInt(parts[1]);
                if (pIndex != myPlayerIndex && allPlayers.containsKey(pIndex)) {
                    Player p = allPlayers.get(pIndex);
                    p.x = Double.parseDouble(parts[2]);
                    p.y = Double.parseDouble(parts[3]);
                }
                break;
                
            case "BALL_UPDATE":
                ball.x = Double.parseDouble(parts[1]);
                ball.y = Double.parseDouble(parts[2]);
                ball.velX = Double.parseDouble(parts[3]);
                ball.velY = Double.parseDouble(parts[4]);
                break;
                
            case "SCORE":
                team1Score = Integer.parseInt(parts[1]);
                team2Score = Integer.parseInt(parts[2]);
                break;
                
            case "GAME_START":
                if (!gameStarted) {
                    gameStarted = true;
                    gameStartTime = System.currentTimeMillis();
                    SwingUtilities.invokeLater(() -> {
                        getContentPane().removeAll();
                        GamePanel gamePanel = new GamePanel();
                        currentPanel = gamePanel;
                        add(gamePanel);
                        revalidate();
                        repaint();
                        gamePanel.requestFocusInWindow();
                    });
                }
                break;
                
            case "GAME_END":
                int winner = Integer.parseInt(parts[1]);
                String resultMsg;
                if (winner == 0) {
                    resultMsg = "무승부!";
                } else {
                    resultMsg = "팀 " + winner + " 승리!";
                }
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, resultMsg + "\n최종 점수 - Team 1: " + team1Score + " vs Team 2: " + team2Score);
                });
                break;
                
            case "PLAYER_LEFT":
                int leftIndex = Integer.parseInt(parts[1]);
                allPlayers.remove(leftIndex);
                break;
        }
    }
    
    private void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }
    
    public SoccerGameClient() {
        setTitle("Soccer Game");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        ball = new Ball(FIELD_WIDTH / 2.0, FIELD_HEIGHT / 2.0);
        
        loadImages();
        
        String serverAddress = JOptionPane.showInputDialog(this, "서버 주소 입력:", "localhost");
        String playerName = JOptionPane.showInputDialog(this, "플레이어 이름 입력:", "Player");
        
        if (serverAddress != null && playerName != null) {
            connectToServer(serverAddress, 54322, playerName);
            
            TeamSelectPanel teamSelectPanel = new TeamSelectPanel();
            currentPanel = teamSelectPanel;
            add(teamSelectPanel);
            
            pack();
            setLocationRelativeTo(null);
            setResizable(false);
            setVisible(true);
        } else {
            System.exit(0);
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SoccerGameClient());
    }
}
