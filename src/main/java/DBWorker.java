
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.TreeSet;

public class DBWorker {
    public static Connection conn;

    // --------ПОДКЛЮЧЕНИЕ К БАЗЕ ДАННЫХ--------
    public DBWorker(String dbPath) throws ClassNotFoundException, SQLException {
        conn = null;
        Class.forName("org.sqlite.JDBC");
        conn = DriverManager.getConnection("jdbc:sqlite:"+dbPath);

        System.out.println("База Подключена!");
    }

    // -------- Вывод таблицы--------
    public TreeSet<GameInfo> readDB() throws SQLException {
        Statement statmt = conn.createStatement();
        ResultSet resSet = statmt.executeQuery("SELECT * FROM games");

        TreeSet<GameInfo> games = new TreeSet<>();
        while (resSet.next()) {
            GameInfo gi = new GameInfo(
                    resSet.getString("name"),
                    resSet.getString("path"),
                    resSet.getString("saves_path"),
                    resSet.getString("version")
            );
            games.add(gi);
        }

        resSet.close();
        statmt.close();

        return games;
    }

    // --------Закрытие--------
    public void closeDB() throws ClassNotFoundException, SQLException {
        conn.close();
        System.out.println("Соединения закрыты");
    }

}