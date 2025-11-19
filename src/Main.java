import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        String[] options = {"Start Server", "Start Client", "Exit"};
        int choice = JOptionPane.showOptionDialog(null,
                "Welcome to Soccer Game!\nChoose an option:",
                "Soccer Game",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[1]);
        
        if (choice == 0) {
            new Thread(() -> SoccerGameServer.main(args)).start();
        } else if (choice == 1) {
            SwingUtilities.invokeLater(() -> new SoccerGameClient());
        } else {
            System.exit(0);
        }
    }
}