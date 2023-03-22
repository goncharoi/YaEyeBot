import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;

public class Sender extends Thread {
    //настройки отслеживания
    protected String sessionUUID = UUID.randomUUID().toString(); //UUID сессии работы программы
    protected String pcName = ""; //имя отслеживаемого ПК
    protected String userId = ""; //id пользователя телеграмма
    protected Boolean filterServices = true; //фильтровать процессы типа Services
    protected Byte timeout = 60; //отсылать данные раз в ... сек.
    protected String dbPathDefault = System.getenv("USERPROFILE") +
            "\\AppData\\Local\\rds-wrtc\\UserGame.db"; //путь к библиотеке игр МТС-лаунчера
    protected String dbPath = "";
    protected String pcGuid = "";

    public Sender() {
        super();
        //чтение настроек
        readSetting();

        if (Objects.equals(pcGuid, "")) initSetttings();
        System.out.println(
                "pcName = " + pcName +
                        ", userId = " + userId +
                        ", filterServices = " + filterServices +
                        ", timeout = " + timeout +
                        ", dbPath = " + dbPath +
                        ", pcGuid = " + pcGuid
        );
    }

    //чтение настроек
    public void readSetting() {
        JSONParser parser = new JSONParser();
        FileReader fr;

        try {
            fr = new FileReader("settings.json");
            Object obj = parser.parse(fr);
            fr.close();

            JSONObject jsonObject = (JSONObject) obj;

            pcName = (String) jsonObject.get("pcName");
            userId = (String) jsonObject.get("userId");
            filterServices = Boolean.valueOf(jsonObject.get("filterServices").toString());
            timeout = Byte.valueOf(jsonObject.get("timeout").toString());
            dbPath = (String) jsonObject.get("dbPath");
            if (dbPath == null) dbPath = "";
            pcGuid = (String) jsonObject.get("pcGuid");
            if (pcGuid == null) pcGuid = "";
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    public void initSetttings() {
        //уникальный идентификатор ПК
        pcGuid = UUID.randomUUID().toString();
        //id аккаунта в телеграмм
        while (Objects.equals(userId, "")) {
            userId = JOptionPane.showInputDialog("Введите id Вашего аккаунта в телеграмм");
            try {
                Long.parseLong(userId);
            } catch (NumberFormatException err) {
                JOptionPane.showMessageDialog(null, "id должен содержать только цифры");
                userId = "";
            }
        }
        //имя ПК
        if (!Objects.equals(pcName, "")) {
            try {
                pcName = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException err) {
                err.printStackTrace();
            }
        }
        boolean firstTime = true;
        while (firstTime || Objects.equals(pcName, "")) {
            pcName = JOptionPane.showInputDialog("Введите уникальное имя ПК", pcName);
            if (pcName == null) pcName = "";
            firstTime = false;
        }
        //выбор БД игр
        JFileChooser fileChooser = new JFileChooser(dbPathDefault);
        fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("SQLite база данных", "db"));
        fileChooser.setDialogTitle("Укажите файл библиотеки игр UserGame.db");
        int ret = fileChooser.showDialog(null, "Ok");
        if (ret == JFileChooser.APPROVE_OPTION) {
            dbPath = fileChooser.getSelectedFile().getAbsolutePath();
        }
        //сохраняем настройки
        try {
            FileWriter fw = new FileWriter("settings.json");

            JSONObject jo = new JSONObject();
            jo.put("pcName", pcName);
            jo.put("userId", userId);
            jo.put("filterServices", (filterServices) ? "1" : "0");
            jo.put("timeout", timeout.toString());
            jo.put("dbPath", dbPath);
            jo.put("pcGuid", pcGuid);

            fw.write(jo.toJSONString());
            fw.close();
        } catch (Exception err) {
            err.printStackTrace();
        }
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
