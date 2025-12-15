package client.ui;

import client.state.GameStateManager;
import client.util.UIHelper;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Game screen with number selection and chat
 */
public class GamePanel extends JPanel {
    private JTextPane t_display;
    private JPanel numberDisplayPanel;
    private JTextField t_input;
    private JButton b_send, b_submit, b_backSpace;
    private JLabel[] selectedNumbers;
    private JLabel l_roundInfo, l_turnInfo, l_timerDisplay;
    private int currentPosition = 0;
    private int digitCount = 3;

    private GamePanelListener listener;
    private GameStateManager stateManager;

    // User ID label
    private JLabel userIdLabel;

    // Prediction history
    private JPanel predictionHistoryPanel;
    private JPanel predictionCardsContainer;
    private List<PredictionEntry> predictionHistory;

    public GamePanel(GamePanelListener listener, GameStateManager stateManager) {
        this.listener = listener;
        this.stateManager = stateManager;
        this.predictionHistory = new ArrayList<>();
        buildUI();
    }

    private void buildUI() {
        setLayout(new BorderLayout());

        // Top panel with game info
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);

        // Exit button
        JButton exitButton = new JButton("Exit");
        exitButton.addActionListener(e -> listener.onDisconnectRequested());
        topPanel.add(exitButton, BorderLayout.WEST);

        // Game info display
        JPanel gameInfoPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        gameInfoPanel.setOpaque(false);
        l_roundInfo = new JLabel("1회 초");
        l_roundInfo.setFont(new Font("Arial", Font.BOLD, 18));
        l_roundInfo.setForeground(Color.BLACK);
        l_turnInfo = new JLabel("Your turn");
        l_turnInfo.setFont(new Font("Arial", Font.BOLD, 18));
        l_turnInfo.setForeground(Color.GREEN);
        l_timerDisplay = new JLabel("30s");
        l_timerDisplay.setFont(new Font("Arial", Font.BOLD, 18));
        l_timerDisplay.setForeground(Color.RED);
        gameInfoPanel.add(l_roundInfo);
        gameInfoPanel.add(l_turnInfo);
        gameInfoPanel.add(l_timerDisplay);
        topPanel.add(gameInfoPanel, BorderLayout.CENTER);

        // User ID display
        userIdLabel = new JLabel("User: " + stateManager.getCurrentUserId());
        userIdLabel.setFont(new Font("Arial", Font.BOLD, 14));
        userIdLabel.setForeground(new Color(0, 180, 0)); // Green
        topPanel.add(userIdLabel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        // Center: Game display
        add(createDisplay(), BorderLayout.CENTER);

        // Bottom: Input panel
        add(createInputPanel(), BorderLayout.SOUTH);
    }

    private JPanel createDisplay() {
        JPanel displayPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                ImageIcon background = new ImageIcon("src/image/feild.jpg");
                Image img = background.getImage();
                g.drawImage(img, 0, 0, getWidth(), getHeight(), this);
            }
        };
        displayPanel.setLayout(new BoxLayout(displayPanel, BoxLayout.Y_AXIS));

        // Top spacer (0~20%)
        JPanel spacer1 = new JPanel();
        spacer1.setOpaque(false);
        spacer1.setPreferredSize(new Dimension(600, 60));
        spacer1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        displayPanel.add(spacer1);

        // Number display panel (20~40%)
        numberDisplayPanel = new JPanel();
        numberDisplayPanel.setOpaque(false);
        numberDisplayPanel.setPreferredSize(new Dimension(600, 60));
        numberDisplayPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        numberDisplayPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 5));

        b_backSpace = new JButton("Delete");
        b_backSpace.setFont(new Font("Arial", Font.BOLD, 12));
        b_backSpace.setPreferredSize(new Dimension(70, 50));
        b_backSpace.addActionListener(e -> backSpaceNumberSelection());
        numberDisplayPanel.add(b_backSpace);

        b_submit = new JButton("Submit");
        b_submit.setFont(new Font("Arial", Font.BOLD, 12));
        b_submit.setPreferredSize(new Dimension(80, 60));
        b_submit.addActionListener(e -> submitGuess());
        numberDisplayPanel.add(b_submit);

        displayPanel.add(numberDisplayPanel);

        // Spacer (40~50%)
        JPanel spacer2 = new JPanel();
        spacer2.setOpaque(false);
        spacer2.setPreferredSize(new Dimension(600, 15));
        spacer2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 15));
        displayPanel.add(spacer2);

        // Number card panel (50~85%)
        JPanel numberCardPanel = new JPanel();
        numberCardPanel.setOpaque(false);
        numberCardPanel.setLayout(new GridLayout(2, 5, 10, 10));
        numberCardPanel.setPreferredSize(new Dimension(600, 220));
        numberCardPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        numberCardPanel.setBorder(BorderFactory.createEmptyBorder(10, 75, 10, 75));

        for (int i = 0; i < 10; i++) {
            final int number = i;
            JButton numberButton = new JButton(String.valueOf(i));
            numberButton.setFont(new Font("Arial", Font.BOLD, 24));
            numberButton.addActionListener(e -> onNumberClick(number));
            numberCardPanel.add(numberButton);
        }
        displayPanel.add(numberCardPanel);

        // Prediction history panel
        displayPanel.add(createPredictionHistoryPanel());

        // Spacer (85~85%)
        JPanel spacer3 = new JPanel();
        spacer3.setOpaque(false);
        spacer3.setPreferredSize(new Dimension(600, 40));
        spacer3.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        displayPanel.add(spacer3);

        // Chat display (85~100%)
        t_display = new JTextPane();
        t_display.setEditable(false);
        t_display.setOpaque(true);
        t_display.setFont(new Font("Arial", Font.PLAIN, 15));
        t_display.setBorder(null);
        JScrollPane scrollPane = new JScrollPane(t_display);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.setPreferredSize(new Dimension(600, 120));
        scrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        displayPanel.add(scrollPane);

        return displayPanel;
    }

    private JPanel createInputPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        t_input = new JTextField(20);
        b_send = new JButton("Send");
        ActionListener sendActionListener = (e) -> {
            sendMessage();
            t_input.setText("");
        };
        t_input.addActionListener(sendActionListener);
        b_send.addActionListener(sendActionListener);
        panel.add(t_input, BorderLayout.CENTER);
        panel.add(b_send, BorderLayout.EAST);
        return panel;
    }

    private JPanel createPredictionHistoryPanel() {
        // Main container panel
        predictionHistoryPanel = new JPanel();
        predictionHistoryPanel.setLayout(new BorderLayout());
        predictionHistoryPanel.setOpaque(false);
        predictionHistoryPanel.setPreferredSize(new Dimension(600, 120));
        predictionHistoryPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        // Title label
        JLabel titleLabel = new JLabel("📊 My Predictions");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        predictionHistoryPanel.add(titleLabel, BorderLayout.NORTH);

        // Cards container with GridLayout (5 columns)
        predictionCardsContainer = new JPanel();
        predictionCardsContainer.setLayout(new GridLayout(0, 5, 8, 8)); // 0 rows = unlimited, 5 columns
        predictionCardsContainer.setOpaque(false);
        predictionCardsContainer.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        // Scroll pane for overflow
        JScrollPane scrollPane = new JScrollPane(predictionCardsContainer);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        predictionHistoryPanel.add(scrollPane, BorderLayout.CENTER);

        return predictionHistoryPanel;
    }

    private JPanel createPredictionCard(PredictionEntry entry) {
        JPanel card = new JPanel();
        card.setLayout(new BorderLayout(3, 3));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        card.setPreferredSize(new Dimension(100, 55));
        card.setMaximumSize(new Dimension(100, 55));

        // Top: Baseball icon + guess
        JLabel guessLabel = new JLabel("⚾ " + entry.getGuess());
        guessLabel.setFont(new Font("Arial", Font.BOLD, 14));
        guessLabel.setForeground(new Color(33, 33, 33));
        card.add(guessLabel, BorderLayout.NORTH);

        // Bottom: Strike and Ball badges
        JPanel resultPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        resultPanel.setOpaque(false);

        // Strike badge
        JLabel strikeLabel = new JLabel(entry.getStrike() + "S");
        strikeLabel.setFont(new Font("Arial", Font.BOLD, 11));
        strikeLabel.setForeground(Color.WHITE);
        strikeLabel.setBackground(new Color(46, 125, 50)); // Green
        strikeLabel.setOpaque(true);
        strikeLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        resultPanel.add(strikeLabel);

        // Ball badge
        JLabel ballLabel = new JLabel(entry.getBall() + "B");
        ballLabel.setFont(new Font("Arial", Font.BOLD, 11));
        ballLabel.setForeground(new Color(33, 33, 33));
        ballLabel.setBackground(new Color(251, 192, 45)); // Yellow
        ballLabel.setOpaque(true);
        ballLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        resultPanel.add(ballLabel);

        card.add(resultPanel, BorderLayout.CENTER);

        return card;
    }

    /**
     * Setup UI for game with specified digit count
     * @param digitCount Number of digits (3, 4, or 5)
     */
    public void setupForGame(int digitCount) {
        this.digitCount = digitCount;

        // Remove existing labels
        Component[] components = numberDisplayPanel.getComponents();
        for (Component component : components) {
            if (component instanceof JLabel) {
                numberDisplayPanel.remove(component);
            }
        }

        // Create new labels
        selectedNumbers = new JLabel[digitCount];
        for (int i = 0; i < digitCount; i++) {
            selectedNumbers[i] = new JLabel("_");
            selectedNumbers[i].setFont(new Font("Arial", Font.BOLD, 36));
            selectedNumbers[i].setForeground(Color.WHITE);
            selectedNumbers[i].setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));
            selectedNumbers[i].setPreferredSize(new Dimension(50, 50));
            selectedNumbers[i].setHorizontalAlignment(SwingConstants.CENTER);
            // Add before Delete and Submit buttons
            numberDisplayPanel.add(selectedNumbers[i], i);
        }

        currentPosition = 0;

        // Clear prediction history when new game starts
        clearPredictionHistory();

        // UI update
        numberDisplayPanel.revalidate();
        numberDisplayPanel.repaint();
    }

    /**
     * Display message in chat area
     * @param message Message to display
     */
    public void displayMessage(String message) {
        displayMessage(message, new Color(50, 50, 50)); // Dark gray
    }

    /**
     * Display message in chat area with color
     * @param message Message to display
     * @param color Text color
     */
    public void displayMessage(String message, Color color) {
        StyledDocument doc = t_display.getStyledDocument();
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setForeground(attrs, color);

        try {
            doc.insertString(doc.getLength(), message + "\n", attrs);
        } catch (BadLocationException e) {
            System.err.println("Error appending message: " + e.getMessage());
        }
        t_display.setCaretPosition(doc.getLength());
    }

    /**
     * Update turn information display
     * @param roundInfo Round info text (e.g., "1회 초")
     * @param turnInfo Turn info text (e.g., "Your Turn")
     * @param isMyTurn Whether it's current user's turn
     */
    public void updateTurnInfo(String roundInfo, String turnInfo, boolean isMyTurn) {
        // Update user ID
        String userId = stateManager.getCurrentUserId();
        userIdLabel.setText("User: " + (userId != null ? userId : "null"));

        l_roundInfo.setText(roundInfo);
        l_turnInfo.setText(turnInfo);
        l_turnInfo.setForeground(isMyTurn ? new Color(0, 128, 0) : new Color(50, 50, 50)); // Dark green if my turn, dark gray otherwise
        b_submit.setEnabled(isMyTurn);
    }

    /**
     * Update timer display
     * @param seconds Remaining seconds
     */
    public void updateTimer(int seconds) {
        l_timerDisplay.setText(seconds + "s");
    }

    /**
     * Clear input fields
     */
    public void clearInput() {
        if (selectedNumbers != null) {
            for (int i = 0; i < digitCount; i++) {
                selectedNumbers[i].setText("_");
            }
        }
        currentPosition = 0;
    }

    /**
     * Add a prediction to the history display
     * @param guess The guessed number
     * @param strike Strike count
     * @param ball Ball count
     */
    public void addPrediction(String guess, int strike, int ball) {
        if (predictionHistory == null) {
            predictionHistory = new ArrayList<>();
        }

        // Add to data model
        PredictionEntry entry = new PredictionEntry(guess, strike, ball);
        predictionHistory.add(entry);

        // Create and add card to UI
        JPanel card = createPredictionCard(entry);
        predictionCardsContainer.add(card);

        // Refresh display
        predictionCardsContainer.revalidate();
        predictionCardsContainer.repaint();

        // Auto-scroll to bottom
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = ((JScrollPane)predictionHistoryPanel.getComponent(1))
                .getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    /**
     * Clear all predictions from history
     */
    public void clearPredictionHistory() {
        if (predictionHistory != null) {
            predictionHistory.clear();
        }
        if (predictionCardsContainer != null) {
            predictionCardsContainer.removeAll();
            predictionCardsContainer.revalidate();
            predictionCardsContainer.repaint();
        }
    }

    private void onNumberClick(int number) {
        if (currentPosition < digitCount) {
            selectedNumbers[currentPosition].setText(String.valueOf(number));
            currentPosition++;
        }
    }

    private void backSpaceNumberSelection() {
        if (currentPosition == 0) {
            return;
        }
        selectedNumbers[--currentPosition].setText("_");
    }

    private void submitGuess() {
        String guess = UIHelper.getSelectedNumStr(selectedNumbers);

        // Validate guess
        if (!UIHelper.isValidGuess(guess, digitCount)) {
            UIHelper.showToast(this, String.format("중복 없이 %d자리 숫자만 입력하세요 (0~9)", digitCount));
            return;
        }

        listener.onGuessSubmitted(guess);
        clearInput();
    }

    private void sendMessage() {
        String text = t_input.getText();
        if (text.isEmpty()) {
            return;
        }

        listener.onChatSent(text);
    }
}
