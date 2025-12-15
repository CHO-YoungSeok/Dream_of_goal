package server;

import common.Message;
import java.io.*;
import java.util.Vector;

// 인증 및 회원 관리

public class AuthManager {
    private static final String USERS_FILE = "server_data/users.csv";
    private static final String STATS_FILE = "server_data/user_stats.csv";

    private ServerCore serverCore;

    public AuthManager(ServerCore serverCore) {
        this.serverCore = serverCore;
    }

    /**
     * 회원가입
     */
    public boolean registerUser(String userId, String password, String character) {
        if (isUserExists(userId)) {
            return false;
        }

        try (FileWriter fw = new FileWriter(USERS_FILE, true)) {
            fw.write(userId + "," + password + "," + character + "\n");

            // 전적 초기화
            try (FileWriter statsFw = new FileWriter(STATS_FILE, true)) {
                statsFw.write(userId + ",0,0,0,0.0\n");
            }

            return true;
        } catch (IOException e) {
            serverCore.printDisplay("회원가입 저장 실패: " + e.getMessage());
            return false;
        }
    }

    /**
     * ID 중복 확인
     */
    public boolean isUserExists(String userId) {
        File file = new File(USERS_FILE);
        if (!file.exists()) {
            return false;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(USERS_FILE))) {
            br.readLine(); // 헤더 스킵
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length > 0 && parts[0].trim().equals(userId)) {
                    return true;
                }
            }
        } catch (IOException e) {
            serverCore.printDisplay("파일 읽기 오류: " + e.getMessage());
        }
        return false;
    }

    /**
     * 로그인 인증
     */
    public boolean authenticateUser(String userId, String password) {
        File file = new File(USERS_FILE);
        if (!file.exists()) {
            return false;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(USERS_FILE))) {
            br.readLine(); // 헤더 스킵
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 2 &&
                        parts[0].trim().equals(userId) &&
                        parts[1].trim().equals(password)) {
                    return true;
                }
            }
        } catch (IOException e) {
            serverCore.printDisplay("인증 오류: " + e.getMessage());
        }
        return false;
    }

    /**
     * 전적 업데이트
     */
    public synchronized void updateUserStats(String userId, boolean isWin, boolean isDraw) {
        try {
            Vector<String> lines = new Vector<>();

            try (BufferedReader br = new BufferedReader(new FileReader(STATS_FILE))) {
                String header = br.readLine();
                lines.add(header);

                String line;
                boolean userFound = false;

                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length >= 5 && parts[0].trim().equals(userId)) {
                        int wins = Integer.parseInt(parts[1].trim());
                        int losses = Integer.parseInt(parts[2].trim());
                        int draws = Integer.parseInt(parts[3].trim());

                        if (isWin) {
                            wins++;
                        } else if (isDraw) {
                            draws++;
                        } else {
                            losses++;
                        }

                        int totalGames = wins + losses + draws;
                        double winRate = totalGames > 0 ? (wins * 100.0 / totalGames) : 0.0;

                        lines.add(userId + "," + wins + "," + losses + "," + draws + "," +
                                String.format("%.1f", winRate));
                        userFound = true;
                    } else {
                        lines.add(line);
                    }
                }

                if (!userFound) {
                    int wins = isWin ? 1 : 0;
                    int losses = (!isWin && !isDraw) ? 1 : 0;
                    int draws = isDraw ? 1 : 0;
                    double winRate = wins * 100.0;
                    lines.add(userId + "," + wins + "," + losses + "," + draws + "," +
                            String.format("%.1f", winRate));
                }
            }

            try (FileWriter fw = new FileWriter(STATS_FILE)) {
                for (String l : lines) {
                    fw.write(l + "\n");
                }
            }

        } catch (IOException e) {
            serverCore.printDisplay("전적 업데이트 실패 (" + userId + "): " + e.getMessage());
        }
    }

    /**
     * 전적 조회
     */
    public Message getStats(String userId) {
        try (BufferedReader br = new BufferedReader(new FileReader(STATS_FILE))) {
            br.readLine(); // 헤더 스킵
            String line;

            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 5 && parts[0].trim().equals(userId)) {
                    Message response = new Message(Message.MessageType.STATS_RESPONSE, "SERVER");
                    java.util.Hashtable<String, String> stats = new java.util.Hashtable<>();
                    stats.put("userId", parts[0].trim());
                    stats.put("wins", parts[1].trim());
                    stats.put("losses", parts[2].trim());
                    stats.put("draws", parts[3].trim());
                    stats.put("winRate", parts[4].trim());
                    response.setData(stats);
                    return response;
                }
            }

            // 전적이 없는 경우
            Message response = new Message(Message.MessageType.STATS_RESPONSE, "SERVER");
            java.util.Hashtable<String, String> stats = new java.util.Hashtable<>();
            stats.put("userId", userId);
            stats.put("wins", "0");
            stats.put("losses", "0");
            stats.put("draws", "0");
            stats.put("winRate", "0.0");
            response.setData(stats);
            return response;

        } catch (IOException e) {
            serverCore.printDisplay("전적 조회 오류: " + e.getMessage());
            return null;
        }
    }
}