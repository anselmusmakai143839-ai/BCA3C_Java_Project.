import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            new LoginFrame().setVisible(true);
        });
    }
}