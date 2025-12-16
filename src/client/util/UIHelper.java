package client.util;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility class for common UI operations
 * All methods are static - this class should not be instantiated
 */
public class UIHelper {

    // Private constructor to prevent instantiation
    private UIHelper() {
    }

    /**
     * Display a toast message at the bottom-center of the screen
     * @param parent Parent component (can be null)
     * @param message Message to display
     */
    public static void showToast(Component parent, String message) {
        JWindow toast = new JWindow();
        toast.setAlwaysOnTop(true);

        JPanel panel = new JPanel();
        panel.setBackground(new Color(50, 50, 50, 230));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JLabel label = new JLabel(message);
        label.setForeground(Color.WHITE);
        label.setFont(new Font("Dialog", Font.BOLD, 16));
        panel.add(label);

        toast.add(panel);
        toast.pack();

        // Position toast at bottom-center of the screen
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (screenSize.width - toast.getWidth()) / 2;
        int y = screenSize.height - toast.getHeight() - 100;
        toast.setLocation(x, y);

        toast.setVisible(true);

        // Auto-hide after 2.5 seconds
        Timer timer = new Timer(2500, e -> {
            toast.setVisible(false);
            toast.dispose();
        });
        timer.setRepeats(false);
        timer.start();
    }

    /**
     * Prompt user to input answer key for the game
     * @param parent Parent component
     * @param digitCount Number of digits required
     * @return Valid answer key, or randomly generated one if user cancels
     */
    public static String promptForAnswerKey(Component parent, int digitCount) {
        while (true) {
            String answer = JOptionPane.showInputDialog(
                parent,
                String.format("정답 숫자를 입력하세요 (%d자리, 0~9, 중복 불가)", digitCount),
                "정답 입력",
                JOptionPane.PLAIN_MESSAGE
            );

            if (answer == null) {
                // 취소 버튼 클릭 시 기본값 사용
                return generateRandomAnswer(digitCount);
            }

            if (isValidAnswerKey(answer, digitCount)) {
                return answer;
            } else {
                JOptionPane.showMessageDialog(
                    parent,
                    String.format("%d자리 숫자여야 하며, 중복 없이 0~9 범위여야 합니다", digitCount),
                    "잘못된 입력",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    /**
     * Generate random answer key
     * @param digitCount Number of digits
     * @return Random answer string
     */
    public static String generateRandomAnswer(int digitCount) {
        List<Integer> digits = new ArrayList<>();
        for (int i = 0; i <= 9; i++) {
            digits.add(i);
        }
        Collections.shuffle(digits);

        StringBuilder answer = new StringBuilder();
        for (int i = 0; i < digitCount; i++) {
            answer.append(digits.get(i));
        }
        return answer.toString();
    }

    /**
     * Validate answer key format
     * @param key Answer key to validate
     * @param digitCount Expected digit count
     * @return true if valid
     */
    public static boolean isValidAnswerKey(String key, int digitCount) {
        // Check length
        if (key == null || key.length() != digitCount) {
            return false;
        }

        // Check if all characters are digits
        if (!key.matches("\\d+")) {
            return false;
        }

        // Check for duplicates
        Set<Character> seen = new HashSet<>();
        for (char c : key.toCharArray()) {
            if (!seen.add(c)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Validate guess format
     * @param guess Guess to validate
     * @param digitCount Expected digit count
     * @return true if valid
     */
    public static boolean isValidGuess(String guess, int digitCount) {
        // Check length
        if (guess == null || guess.contains("_") || guess.length() != digitCount) {
            return false;
        }

        // Check if all digits
        if (!guess.matches("\\d+")) {
            return false;
        }

        // Check for duplicates
        Set<Character> seen = new HashSet<>();
        for (char c : guess.toCharArray()) {
            if (!seen.add(c)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Convert JLabel array to string
     * @param selectedNumbers Array of labels containing digits
     * @return Concatenated string
     */
    public static String getSelectedNumStr(JLabel[] selectedNumbers) {
        if (selectedNumbers == null) return "";
        StringBuilder sb = new StringBuilder();
        for (JLabel label : selectedNumbers) {
            sb.append(label.getText());
        }
        return sb.toString();
    }

    /**
     * Create a panel with background image
     * @param imagePath Path to background image
     * @return JPanel with custom paintComponent
     */
    public static JPanel createBackgroundPanel(String imagePath) {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                ImageIcon background = new ImageIcon(imagePath);
                Image img = background.getImage();
                g.drawImage(img, 0, 0, getWidth(), getHeight(), this);
            }
        };
    }

    /**
     * Show password input dialog
     * @param parent Parent component
     * @param title Dialog title
     * @return Password entered, or null if cancelled
     */
    public static String showPasswordDialog(Component parent, String title) {
        JPasswordField passwordField = new JPasswordField(15);
        int result = JOptionPane.showConfirmDialog(
            parent,
            passwordField,
            title,
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            String password = new String(passwordField.getPassword()).trim();
            return password.isEmpty() ? null : password;
        }
        return null;
    }
}
