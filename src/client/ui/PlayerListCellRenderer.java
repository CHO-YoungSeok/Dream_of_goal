package client.ui;

import client.state.GameStateManager;
import common.Message;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * Custom cell renderer for player list in room waiting screen
 * Shows player status (host, ready, current user)
 */
public class PlayerListCellRenderer extends DefaultListCellRenderer {
    private GameStateManager stateManager;

    public PlayerListCellRenderer(GameStateManager stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        // 기본 렌더링 설정 호출
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        String playerId = (String) value;
        Map<String, Boolean> readyStatus = stateManager.getPlayerReadyStatus();
        boolean isReady = readyStatus.getOrDefault(playerId, false);
        boolean isRoomHost = playerId.equals(stateManager.getRoomMasterUserId());

        Message.GameMode gameMode = stateManager.getCurrentGameMode();
        boolean isTeamMode = (gameMode == Message.GameMode.TWO_VS_TWO);

        Integer playerTeam = stateManager.getPlayerTeamMap().get(playerId);
        boolean isTeamLeader = isTeamMode && playerId.equals(stateManager.getTeamLeaderId());

        String displayText = playerId;
        Color foregroundColor = Color.WHITE;

        // 1. 팀 정보 표시 (2v2 모드일 경우)
        if (isTeamMode && playerTeam != null) {
            String teamLabel = (isTeamLeader ? "★" : "") + "Team " + playerTeam;
            displayText = String.format("[%s] %s", teamLabel, playerId);

            // 팀별 색상 지정
            if (playerTeam == 1) {
                foregroundColor = new Color(135, 206, 250); // 하늘색 (Team 1)
            } else if (playerTeam == 2) {
                foregroundColor = new Color(255, 105, 180); // 핫핑크 (Team 2)
            }
        }

        // 2. 준비 상태 및 방장 표시

        // 방장 표시 (Host)
        if (isRoomHost) {
            displayText += " (Host)";
            foregroundColor = Color.ORANGE; // Host는 팀색상보다 우선
        } else {
            // 준비 상태 표시
            if (isReady) {
                displayText += " [Ready]";
                if (foregroundColor.equals(Color.WHITE)) {
                    foregroundColor = Color.GREEN; // 팀 색상이 없을 때만 Ready 색상 적용
                }
            } else if (isTeamMode && playerTeam != null) {
                // 팀 모드이고 아직 준비가 안 됐더라도 팀 색상을 유지 (Host가 아니므로)
            } else {
                foregroundColor = Color.WHITE;
            }
        }

        // 3. 현재 유저 표시 (Me)
        if (playerId.equals(stateManager.getCurrentUserId())) {
            displayText += " (You)";
            if (playerTeam == null || !isTeamMode) {
                // 팀 모드가 아니거나 팀 배정 전이라면 현재 유저임을 강조 (노란색)
                foregroundColor = Color.YELLOW;
            }
        }

        setText(displayText);
        setForeground(foregroundColor); // 최종 결정된 색상 적용

        setOpaque(true); // Ensure the renderer paints its own background.
        if (isSelected) {
            setBackground(Color.DARK_GRAY);
        } else {
            // Provide a semi-transparent background for readability over the background image.
            setBackground(new Color(10, 10, 10, 80));
        }

        return this;
    }
}
