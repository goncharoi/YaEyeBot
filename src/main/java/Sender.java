import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;

public class Sender extends Thread {
    //настройки отслеживания
    protected String sessionUUID = UUID.randomUUID().toString(); //UUID сессии работы программы
    protected String pcName; //имя отслеживаемого ПК
    protected String userId; //id пользователя телеграмма
    protected Boolean filterServices; //фильтровать процессы типа Services
    protected Byte timeout; //отсылать данные раз в ... сек.
    protected String dbPathDefault = System.getenv("USERPROFILE") +
            "\\AppData\\Local\\rds-wrtc\\UserGame.db"; //путь к библиотеке игр МТС-лаунчера
    protected String dbPath;
    protected String pcGuid;
    protected boolean firstTime = true;
    protected Listener listener;
    protected boolean explorer_stopped = false;
    protected boolean video;
    protected TreeSet<GameInfo> games = new TreeSet<>();

    public Sender(Listener listener, Boolean video, String pcName, String userId, Boolean filterServices, Byte timeout, String dbPath, String pcGuid) {
        super();
        //заполнение настроек
        this.pcName = pcName;
        this.userId = userId;
        this.filterServices = filterServices;
        this.timeout = timeout;
        this.dbPath = dbPath;
        this.pcGuid = pcGuid;
        this.listener = listener;
        this.video = video;
    }

    @Override
    public void run() {
        //проверка заполнения настроек
        if (Objects.equals(pcName, "") || Objects.equals(userId, "") || Objects.equals(pcGuid, "")) return;

        do {
            try {
                sendPOST();
                try {
                    sleep(timeout * 1000);        //Приостанавливает поток
                } catch (InterruptedException e) {
                    return;    //Завершение потока после прерывания
                }
            } catch (Exception err) {
                err.printStackTrace();
            }
        } while (true);
    }

    protected TreeSet<ProcessInfo> listRunningProcesses(TreeSet<GameInfo> games) {
        TreeSet<ProcessInfo> processes = new TreeSet<>();
        try {
            String line;
            String[] info;
            String cmd = "getprocs.bat";
            Process p = Runtime.getRuntime().exec(cmd);
            BufferedReader input = new BufferedReader
                    (new InputStreamReader(p.getInputStream(), "866"));
            int i = 0;
            while ((line = input.readLine()) != null) {
                i++;
                //отсекаем шапку
                if (i < 4) continue;

                info = line.split("[|&#^]", -1);

                //часть игр запускаются из-под админа и путь для них не пишется, поэтому сверямся по библиотеке,
                //где эти игры могут быть добавлены через исключения в excGames.json
                boolean found = false;
                for (GameInfo game : games)
                    if (game.gPath.contains(info[0].trim())) {
                        found = true;
                        break;
                    }

                if (Arrays.stream(info).count() > 3) {
                    //отсекаем виндовские процессы и записи без exe
                    if (
                            (info[2].trim().startsWith("C:\\Windows\\") ||
                                    info[1].trim().equals("0") ||
                                    info[2].trim().equals("") && !found) &&
                                    !info[0].trim().equals("explorer") //нужен для понимания входа игрока
                    ) continue;

                    info[3] = info[3].trim().replaceAll(" ", "_"); //читаемое описание, если есть
                    info[0] = info[0].trim(); //техническое имя процесса
                    if (info[3].equals("")) info[3] = info[0];

                    processes.add(new ProcessInfo(info[0], info[3], info[2].trim(), info[4].trim()));
                }
            }
            input.close();
        } catch (Exception err) {
            err.printStackTrace();
        }
        return processes;
    }

    protected TreeSet<GameInfo> listGamesFromLib() {
        TreeSet<GameInfo> games = new TreeSet<>();
        //получаем игры из библиотеки
        try {
            DBWorker DBW = new DBWorker((!Objects.equals(dbPath, "")) ? dbPath : dbPathDefault);
            games = DBW.readDB();
            DBW.closeDB();
        } catch (ClassNotFoundException | SQLException err) {
            err.printStackTrace();
        }
        //добавляем игры из Steam
        games.addAll(SteamReader.getSteamGames());

        //добавляем игры-исключения или заменяем ими данные из библиотеки
        TreeSet<GameInfo> excGames = readExcGames();
        for (GameInfo excGame : excGames)
            games.removeIf(game -> Objects.equals(game.gName, excGame.gName));
        games.addAll(excGames);
        return games;
    }

    //чтение данных для игр-исключений
    public TreeSet<GameInfo> readExcGames() {
        TreeSet<GameInfo> games = new TreeSet<>();

        JSONParser parser = new JSONParser();
        FileReader fr;

        try {
            fr = new FileReader("excGames.json");
            Object obj = parser.parse(fr);
            fr.close();

            JSONObject jsonObject = (JSONObject) obj;
            JSONArray excGames = (jsonObject.get("excGames") != null) ? (JSONArray) jsonObject.get("excGames") : new JSONArray();

            for (Object excGame : excGames) {
                JSONObject JSONexcGame = (JSONObject) excGame;
                GameInfo gi = new GameInfo(
                        (JSONexcGame.get("name") != null) ? JSONexcGame.get("name").toString() : "",
                        (JSONexcGame.get("exePath") != null) ? JSONexcGame.get("exePath").toString() : "",
                        "",
                        ""
                );
                games.add(gi);
            }
        } catch (Exception err) {
            err.printStackTrace();
        }
        return games;
    }

    protected String getJSONString(TreeSet<ProcessInfo> procs, TreeSet<GameInfo> games) {
        JSONArray jProcs = new JSONArray();
        JSONArray jGames = new JSONArray();

        //список процессов к отправке
        for (ProcessInfo next : procs) {
            JSONObject jo = new JSONObject();
            jo.put("name", next.name);
            jo.put("type", next.type);
            jo.put("exe", next.exe);
            jo.put("version", next.version);
            jProcs.add(jo);
        }
        //список игр к отправке
        for (GameInfo next : games) {
            JSONObject jo = new JSONObject();
            jo.put("gName", next.gName);
            jo.put("gPath", next.gPath);
            jo.put("sPath", next.sPath);
            jo.put("gVers", next.gVers);
            jGames.add(jo);
        }

        //сообщение
        JSONObject message = new JSONObject();
        //команда
        message.put("text", "/get_procs");
        //параметры пакета
        message.put("pcName", pcName);
        message.put("pcGuid", pcGuid);
        message.put("userId", userId);
        message.put("sessionUUID", sessionUUID);
        message.put("time", System.currentTimeMillis());
        //список процессов
        message.put("procs", jProcs);
        //список игр
        message.put("games", jGames);

        //пакет для сообщения
        JSONObject mainObj = new JSONObject();
        mainObj.put("message", message);

        String result = mainObj.toJSONString();

        System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Сформированы данные для отправки\n");
        System.out.println(result);

        return result;
    }

    protected void sendPOST() throws IOException {
        URL url = new URL("https://bbb.daobots.ru/YaEyebot.php");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json; utf-8");
        con.setRequestProperty("Accept", "application/json");
        con.setConnectTimeout(2000);
        con.setReadTimeout(2000);
        con.setDoOutput(true);

        try (OutputStream os = con.getOutputStream()) {
            //получаем данные о библиотеке лишь один раз
            if (firstTime) games = listGamesFromLib();
            TreeSet<ProcessInfo> procs = listRunningProcesses(games);

            //отсылаем заполненную библиотеку только первый раз
            byte[] input = getJSONString(procs, (firstTime) ? games : new TreeSet<>()).getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);

            //Дополнительно: включаем запись, как только пропадает процесс explorer - один раз
            //только если, процесс вообще запущен (т.е. запись видео включена пользователем)
            if (video){
                boolean explorer_runing = false;
                for (ProcessInfo proc : procs) {
                    if (Objects.equals(proc.name, "explorer")) {
                        explorer_runing = true;
                        break;
                    }
                }
                if (!explorer_runing && !explorer_stopped) {
                    explorer_stopped = true;
                    listener.catchAction(true);
                }
            }
        } catch (Exception err) {
            err.printStackTrace();
        }

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            System.out.println(response);
        } catch (Exception err) {
            err.printStackTrace();
        }
        con.disconnect();

        firstTime = false;
    }
}
