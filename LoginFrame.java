import javax.swing.*;
import java.awt.*;
import java.sql.*;

public class LoginFrame extends JFrame {
    private final JTextField emailField = new JTextField(22);
    private final JPasswordField passwordField = new JPasswordField(22);

    public LoginFrame() {
        setTitle("Online Bus Ticket App - Ansel Makai");
        setSize(1000, 640);
        setMinimumSize(new Dimension(520, 560));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
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

        JLabel brand = new JLabel("BusTiket", JLabel.CENTER);
        brand.setFont(Theme.bold(30));
        brand.setForeground(Theme.RED);
        c.gridy = 0;
        card.add(brand, c);

        JLabel subtitle = new JLabel("Welcome To Bus Ticket Online", JLabel.CENTER);
        subtitle.setFont(Theme.plain(14));
        subtitle.setForeground(Theme.MUTED);
        c.gridy = 1;
        c.insets = new Insets(0, 0, 18, 0);
        card.add(subtitle, c);

        c.insets = new Insets(6, 0, 6, 0);
        c.gridy = 2;
        card.add(Theme.box("Email", emailField), c);
        c.gridy = 3;
        card.add(Theme.box("Password", passwordField), c);

        Theme.RoundButton loginBtn = Theme.primary("Login");
        loginBtn.setFont(Theme.bold(16));
        loginBtn.setPreferredSize(new Dimension(100, 46));
        c.gridy = 4;
        c.insets = new Insets(16, 0, 6, 0);
        card.add(loginBtn, c);

        JPanel registerRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        registerRow.setOpaque(false);
        JLabel q = new JLabel("Don't have an account?");
        q.setForeground(Theme.MUTED);
        JButton registerBtn = Theme.link("Register");
        registerRow.add(q);
        registerRow.add(registerBtn);
        c.gridy = 5;
        c.insets = new Insets(4, 0, 0, 0);
        card.add(registerRow, c);

        bg.add(card, new GridBagConstraints());

        loginBtn.addActionListener(e -> login());
        registerBtn.addActionListener(e -> new RegisterFrame().setVisible(true));
        getRootPane().setDefaultButton(loginBtn);   // pressing Enter = Login
    }

    private void login() {
        String email = emailField.getText().trim();
        String pass = new String(passwordField.getPassword());
        if (email.isEmpty() || pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Email and password are required.");
            return;
        }
        String sql = "SELECT id, name FROM users WHERE email = ? AND password = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, PasswordUtil.hash(pass));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int userId = rs.getInt("id");
                    String name = rs.getString("name");
                    new DashboardFrame(userId, name).setVisible(true);
                    dispose();
                } else {
                    JOptionPane.showMessageDialog(this, "Incorrect email or password.");
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database error: " + ex.getMessage());
        }
    }
}