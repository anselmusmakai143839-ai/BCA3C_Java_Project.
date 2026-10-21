import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    private static final String URL = "jdbc:mysql://localhost:3306/bus_ticket_db";
    private static final String USER = "root";
    private static final String PASSWORD = "Makai1234"; // GANTI dengan password MySQL kamu

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
