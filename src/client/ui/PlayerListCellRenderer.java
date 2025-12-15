package client.ui;

import client.state.GameStateManager;
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
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        String playerId = (String) value;
        Map<String, Boolean> readyStatus = stateManager.getPlayerReadyStatus();
        boolean isReady = readyStatus.getOrDefault(playerId, false);
        boolean isHost = playerId.equals(stateManager.getRoomMasterUserId());

        // "PlayerID [Team A] (Ready)"
        String displayText = playerId;

        // 내가 누구인지 표시
        if (playerId.equals(stateManager.getCurrentUserId())) {
            displayText += " (Me)";
        }

        // 방장 표시
        if (isHost) {
            displayText += " (Host)";
            setForeground(Color.ORANGE);
        } else {
            // 준비 상태 표시
            if (isReady) {
                displayText += " [Ready]";
                setForeground(Color.GREEN);
            } else {
                setForeground(Color.WHITE);
            }
        }

        setText(displayText);
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
