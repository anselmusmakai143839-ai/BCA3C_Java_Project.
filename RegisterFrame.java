import javax.swing.*;
import java.awt.*;
import java.sql.*;

public class RegisterFrame extends JFrame {
    private final JTextField nameField = new JTextField(22);
    private final JTextField emailField = new JTextField(22);
    private final JPasswordField passwordField = new JPasswordField(22);

    public RegisterFrame() {
        setTitle("Create Account");
        setSize(620, 660);
        setMinimumSize(new Dimension(480, 600));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        BackgroundPanel bg = new BackgroundPanel("bus-station.png", 90);
        bg.setLayout(new GridBagLayout());
        setContentPane(bg);

        CardPanel card = new CardPanel(new GridBagLayout(), 28, Color.WHITE);
        card.setBorder(BorderFactory.createEmptyBorder(28, 36, 30, 36));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(6, 0, 6, 0);

        JLabel title = new JLabel("Create New Account", JLabel.CENTER);
        title.setFont(Theme.bold(26));
        title.setForeground(Theme.RED);
        c.gridy = 0;
        c.insets = new Insets(0, 0, 18, 0);
        card.add(title, c);

        c.insets = new Insets(6, 0, 6, 0);
        c.gridy = 1;
        card.add(Theme.box("Name", nameField), c);
        c.gridy = 2;
        card.add(Theme.box("Email", emailField), c);
        c.gridy = 3;
        card.add(Theme.box("Password (at least 4 characters)", passwordField), c);

        Theme.RoundButton registerBtn = Theme.primary("Register");
        registerBtn.setFont(Theme.bold(16));
        registerBtn.setPreferredSize(new Dimension(100, 46));
        c.gridy = 4;
        c.insets = new Insets(16, 0, 6, 0);
        card.add(registerBtn, c);

        JPanel backRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        backRow.setOpaque(false);
        JLabel q = new JLabel("Already have an account?");
        q.setForeground(Theme.MUTED);
        JButton backBtn = Theme.link("Back to Login");
        backRow.add(q);
        backRow.add(backBtn);
        c.gridy = 5;
        c.insets = new Insets(4, 0, 0, 0);
        card.add(backRow, c);

        bg.add(card, new GridBagConstraints());

        registerBtn.addActionListener(e -> register());
        backBtn.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(registerBtn);
    }

    private void register() {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String pass = new String(passwordField.getPassword());
        if (name.isEmpty() || email.isEmpty() || pass.length() < 4) {
            JOptionPane.showMessageDialog(this, "Please fill in all fields. Password must be at least 4 characters.");
            return;
        }
        if (!email.contains("@")) {
            JOptionPane.showMessageDialog(this, "Invalid email format.");
            return;
        }
        String sql = "INSERT INTO users (name, email, password) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, PasswordUtil.hash(pass));
            ps.executeUpdate();
            JOptionPane.showMessageDialog(this, "Registration successful! Please log in.");
            dispose();
        } catch (SQLIntegrityConstraintViolationException ex) {
            JOptionPane.showMessageDialog(this, "Email is already registered.");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database error: " + ex.getMessage());
        }
    }
}