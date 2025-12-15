package client.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Custom cell renderer for the user list in the lobby.
 * Displays users in different colors based on their status.
 */
public class UserListCellRenderer extends DefaultListCellRenderer {

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        String userText = (String) value;

        if (userText.startsWith("\u2B24")) { // Online (in lobby)
            setForeground(Color.GREEN);
        } else if (userText.startsWith("\u25FC")) { // In Room
            setForeground(Color.WHITE);
        } else if (userText.startsWith("\u25B2")) { // In Game
            setForeground(Color.magenta);
        } else {
            setForeground(list.getForeground());
        }

        setText(userText);
        setOpaque(isSelected); // Make non-selected items transparent

        if (isSelected) {
            setBackground(list.getSelectionBackground());
        } else {
            setBackground(new Color(0, 0, 0, 0)); // Transparent background
        }

        return this;
    }
}
