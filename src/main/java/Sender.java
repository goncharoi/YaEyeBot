import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;

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

    public Sender(String pcName, String userId, Boolean filterServices, Byte timeout, String dbPath, String pcGuid) {
        super();
        //заполнение настроек
        this.pcName = pcName;
        this.userId = userId;
        this.filterServices = filterServices;
        this.timeout = timeout;
        this.dbPath = dbPath;
        this.pcGuid = pcGuid;
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

    protected TreeSet<ProcessInfo> listRunningProcesses() {
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

                info = line.split("\\||&|#", -1);

                if (Arrays.stream(info).count() > 3) {
                    //отсекаем виндовские процессы
                    if (info[2].trim().startsWith("C:\\Windows\\") || info[1].trim().equals("0")) continue;

                    info[3] = info[3].trim().replaceAll(" ", "_"); //читаемое описание, если есть
                    info[0] = info[0].trim(); //техническое имя процесса
                    if (info[3].equals("")) info[3] = info[0];

                    ProcessInfo pi = new ProcessInfo(info[0], info[3]);
                    //если вообще нет процесса с таким именем - добавляем
                    if (processes.stream().noneMatch(s -> Objects.equals(s.name, pi.name)))
                        processes.add(pi);
                        //если нет процесса с таким именем и отдельным описанием, а тут он пришел - старый удаляем, а более полный добавляем
                    else if (processes.stream().noneMatch(s -> Objects.equals(s.name, pi.name) && !Objects.equals(s.name, s.type)) && !Objects.equals(pi.name, pi.type)) {
                        processes.remove(new ProcessInfo(info[0], info[0]));
                        processes.add(pi);
                    }
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
        try {
            DBWorker DBW = new DBWorker((!Objects.equals(dbPath, "")) ? dbPath : dbPathDefault);
            games = DBW.readDB();
            DBW.closeDB();
        } catch (ClassNotFoundException | SQLException err) {
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

        return mainObj.toJSONString();
    }

    protected void sendPOST() throws IOException {
        System.out.println("пошла отправка...");

        URL url = new URL("https://bbb.daobots.ru/YaEyebot.php");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json; utf-8");
        con.setRequestProperty("Accept", "application/json");
        con.setConnectTimeout(2000);
        con.setReadTimeout(2000);
        con.setDoOutput(true);

        try (OutputStream os = con.getOutputStream()) {
            byte[] input = getJSONString(listRunningProcesses(), listGamesFromLib()).getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            System.out.println(response);
        }
        con.disconnect();
    }
}
