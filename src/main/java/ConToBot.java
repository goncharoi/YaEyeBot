import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class ConToBot {
    private static final String strUrl = "https://bbb.daobots.ru/YaEyebot.php";

    private final HttpURLConnection con;

    public ConToBot() throws Exception {
        URL url = new URI(strUrl).toURL();
        con = (HttpURLConnection) url.openConnection();

        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json; utf-8");
        con.setRequestProperty("Accept", "application/json");
        con.setConnectTimeout(2000);
        con.setReadTimeout(2000);
        con.setDoOutput(true);
    }

    public HttpURLConnection getCon() {
        return con;
    }

    public boolean closeCon() {
        if (con == null) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: соединение пропало: con == null");
            return true;
        }

        System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: закрываем соединение\n");
        try {
            int code = con.getResponseCode();
            System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: сервер вернул код: " + code + "\n");
            if (code != 200) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: ответ сервера: " + response + "\n");
                } catch (Exception err) {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(con.getErrorStream(), StandardCharsets.UTF_8))) {
                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }
                        System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: описание ошибки: " + response + "\n");
                    } catch (Exception err_on_err) {
                        System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
                        err_on_err.printStackTrace();
                    }
                }
                return false;
            }
        } catch (IOException e) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            e.printStackTrace();
        }

        con.disconnect();
        return true;
    }
}
