package client.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Login and registration screen
 */
public class LoginPanel extends JPanel {
    private JTextField t_loginUserId;
    private JPasswordField t_loginPassword;
    private JTextField t_registerUserId;
    private JPasswordField t_registerPassword;
    private JTextField t_registerNickname;
    private JButton b_login, b_register, b_exit;

    private LoginListener listener;

    public LoginPanel(LoginListener listener) {
        this.listener = listener;
        buildUI();
    }

    private void buildUI() {
        setLayout(new BorderLayout());

        // Exit button in top-left
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setOpaque(false);
        b_exit = new JButton("Exit");
        b_exit.addActionListener(e -> listener.onExitRequested());
        topPanel.add(b_exit);
        add(topPanel, BorderLayout.NORTH);

        // Center panel for login/register
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);
        centerPanel.add(Box.createVerticalGlue());

        // Title
        JLabel titleLabel = new JLabel("Baseball Game");
        titleLabel.setFont(new Font("Dialog", Font.BOLD, 48));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(titleLabel);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 50)));

        // Login Section
        JLabel loginLabel = new JLabel("Login");
        loginLabel.setFont(new Font("Dialog", Font.BOLD, 24));
        loginLabel.setForeground(Color.YELLOW);
        loginLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(loginLabel);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // User ID for login
        JPanel loginUserPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        loginUserPanel.setOpaque(false);
        JLabel l_loginUserId = new JLabel("User ID:");
        l_loginUserId.setFont(new Font("Dialog", Font.BOLD, 18));
        l_loginUserId.setForeground(Color.WHITE);
        t_loginUserId = new JTextField(15);
        t_loginUserId.setFont(new Font("Dialog", Font.PLAIN, 16));
        loginUserPanel.add(l_loginUserId);
        loginUserPanel.add(t_loginUserId);
        centerPanel.add(loginUserPanel);

        // Password for login
        JPanel loginPassPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        loginPassPanel.setOpaque(false);
        JLabel l_loginPassword = new JLabel("Password:");
        l_loginPassword.setFont(new Font("Dialog", Font.BOLD, 18));
        l_loginPassword.setForeground(Color.WHITE);
        t_loginPassword = new JPasswordField(15);
        t_loginPassword.setFont(new Font("Dialog", Font.PLAIN, 16));
        loginPassPanel.add(l_loginPassword);
        loginPassPanel.add(t_loginPassword);
        centerPanel.add(loginPassPanel);

        // Login button
        JPanel loginButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        loginButtonPanel.setOpaque(false);
        b_login = new JButton("Login");
        b_login.setFont(new Font("Dialog", Font.BOLD, 20));
        b_login.setPreferredSize(new Dimension(150, 45));
        b_login.addActionListener(e -> handleLogin());
        loginButtonPanel.add(b_login);
        centerPanel.add(loginButtonPanel);

        centerPanel.add(Box.createRigidArea(new Dimension(0, 40)));

        // Register Section
        JLabel registerLabel = new JLabel("New User? Register");
        registerLabel.setFont(new Font("Dialog", Font.BOLD, 24));
        registerLabel.setForeground(Color.YELLOW);
        registerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(registerLabel);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // User ID for register
        JPanel regUserPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        regUserPanel.setOpaque(false);
        JLabel l_regUserId = new JLabel("User ID:");
        l_regUserId.setFont(new Font("Dialog", Font.BOLD, 18));
        l_regUserId.setForeground(Color.WHITE);
        t_registerUserId = new JTextField(15);
        t_registerUserId.setFont(new Font("Dialog", Font.PLAIN, 16));
        regUserPanel.add(l_regUserId);
        regUserPanel.add(t_registerUserId);
        centerPanel.add(regUserPanel);

        // Password for register
        JPanel regPassPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        regPassPanel.setOpaque(false);
        JLabel l_regPassword = new JLabel("Password:");
        l_regPassword.setFont(new Font("Dialog", Font.BOLD, 18));
        l_regPassword.setForeground(Color.WHITE);
        t_registerPassword = new JPasswordField(15);
        t_registerPassword.setFont(new Font("Dialog", Font.PLAIN, 16));
        regPassPanel.add(l_regPassword);
        regPassPanel.add(t_registerPassword);
        centerPanel.add(regPassPanel);

        // Nickname for register
        JPanel regNickPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        regNickPanel.setOpaque(false);
        JLabel l_regNickname = new JLabel("Nickname:");
        l_regNickname.setFont(new Font("Dialog", Font.BOLD, 18));
        l_regNickname.setForeground(Color.WHITE);
        t_registerNickname = new JTextField(15);
        t_registerNickname.setFont(new Font("Dialog", Font.PLAIN, 16));
        regNickPanel.add(l_regNickname);
        regNickPanel.add(t_registerNickname);
        centerPanel.add(regNickPanel);

        // Register button
        JPanel registerButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        registerButtonPanel.setOpaque(false);
        b_register = new JButton("Register");
        b_register.setFont(new Font("Dialog", Font.BOLD, 20));
        b_register.setPreferredSize(new Dimension(150, 45));
        b_register.addActionListener(e -> handleRegister());
        registerButtonPanel.add(b_register);
        centerPanel.add(registerButtonPanel);

        centerPanel.add(Box.createVerticalGlue());
        add(centerPanel, BorderLayout.CENTER);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        ImageIcon background = new ImageIcon("src/image/intro.jpg");
        Image img = background.getImage();
        g.drawImage(img, 0, 0, getWidth(), getHeight(), this);
    }

    private void handleLogin() {
        String userId = t_loginUserId.getText().trim();
        String password = new String(t_loginPassword.getPassword());

        if (userId.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter both User ID and Password",
                "Input Required",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        listener.onLoginRequested(userId, password);
    }

    private void handleRegister() {
        String userId = t_registerUserId.getText().trim();
        String password = new String(t_registerPassword.getPassword());
        String nickname = t_registerNickname.getText().trim();

        if (userId.isEmpty() || password.isEmpty() || nickname.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please fill in all registration fields",
                "Input Required",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        listener.onRegisterRequested(userId, password, nickname);
    }

    /**
     * Clear registration fields after successful registration
     */
    public void clearRegisterFields() {
        t_registerUserId.setText("");
        t_registerPassword.setText("");
        t_registerNickname.setText("");
    }
}
