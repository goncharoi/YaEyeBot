import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;

public class Sender extends Thread {
    //настройки отслеживания
    protected String sessionUUID = UUID.randomUUID().toString(); //UUID сессии работы программы
    protected String pcName; //имя отслеживаемого ПК
    protected String mtsName; //имя ПК на МТС Fog Play
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
    protected ASListener asListener;
    protected boolean saves;
    protected TreeSet<GameInfo> games = new TreeSet<>();
    protected boolean hardware;

    public Sender(ASListener asListener, Boolean saves, String pcName, String mtsName, String userId, Boolean filterServices, Byte timeout, String dbPath, String pcGuid, Boolean hardware) {
        super();
        //заполнение настроек
        this.pcName = pcName;
        this.mtsName = mtsName;
        this.userId = userId;
        this.filterServices = filterServices;
        this.timeout = timeout;
        this.dbPath = dbPath;
        this.pcGuid = pcGuid;
        this.asListener = asListener;
        this.saves = saves;
        this.hardware = hardware;
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
            int start_line = 0;
            int offset_begin = -1;
            while ((line = input.readLine()) != null) {
                i++;

                if (offset_begin < 0) {
                    offset_begin = line.indexOf("ProcessName");
                    start_line = i+2;
                }
                //отсекаем шапку
                if (i < start_line || offset_begin < 0 || line.length() < offset_begin)
                    continue;

                String pid = line.substring(0, offset_begin - 1).trim();
                line = line.substring(offset_begin);

                info = line.split("[|%#^]", -1);

                if (Arrays.stream(info).count() > 3) {
                    //отсекаем виндовские процессы и записи без exe
                    if (
                            (info[2].trim().startsWith("C:\\Windows\\") || info[1].trim().equals("0")) &&
                                    !info[0].trim().equals("explorer") //нужен для понимания входа игрока
                    ) continue;

                    info[3] = info[3].trim().replaceAll(" ", "_"); //читаемое описание, если есть
                    info[0] = info[0].trim(); //техническое имя процесса
                    if (info[3].equals("")) info[3] = info[0];

                    processes.add(new ProcessInfo(info[0], info[3], info[2].trim(), info[4].trim(), pid));
                }
            }
            input.close();
        } catch (Exception err) {
            err.printStackTrace();
        }
        return processes;
    }

    protected TreeSet<ProcessInfo> filterProcesses(TreeSet<ProcessInfo> procs) {
        TreeSet<ProcessInfo> processes = new TreeSet<>();
        try {
            String line;
            String cmd = "cmdow_run.bat";
            Process p = Runtime.getRuntime().exec(cmd);
            BufferedReader input = new BufferedReader
                    (new InputStreamReader(p.getInputStream(), "866"));
            int i = 0;
            int offset_begin = 0;
            int offset_end = 0;
            while ((line = input.readLine()) != null) {
                i++;

                if (i == 2) {
                    offset_begin = line.indexOf("Lev")+3;
                    offset_end = line.indexOf("-Window") - 1;
                }

                //отсекаем шапку
                if (i < 3) continue;

                String pid = line.substring(offset_begin, offset_end).trim();

                for (ProcessInfo proc : procs) {
                    if (Objects.equals(proc.pid, pid)) {
//                        proc.window = true;
                        processes.add(proc);
                    }
                }
            }
            input.close();
        } catch (Exception err) {
            err.printStackTrace();
        }
        return processes;
//        return procs;
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
        games.addAll(SteamReader.getSteamGames((!Objects.equals(dbPath, "")) ? dbPath : dbPathDefault));

        //добавляем игры-исключения или заменяем ими данные из библиотеки
        TreeSet<GameInfo> excGames = readExcGames();
        for (GameInfo excGame : excGames)
            games.removeIf(game -> Objects.equals(game.gName, excGame.gName));
        games.addAll(excGames);

        //вычисляем актуальную версию ехе
        for (GameInfo game : games) {
            String version = "";
            try {
                version = EXEFileInfo.getVersionInfo(game.gPath);
            } catch (Exception err) {
                err.printStackTrace();
            }
            if (!version.equals(""))
                game.gVers = version;
        }

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

    protected TreeSet<HardwareInfo> listHardwareInfo() {
        TreeSet<HardwareInfo> hardware = new TreeSet<>();

        if (!this.hardware) return hardware;

        try {
            String log_csv_name;

            //получаем имя самого свежего csv-файла в папке OpenHardwareMonitor
            Path dir = Paths.get("ohm");
            if (Files.isDirectory(dir)) {
                Optional<Path> opPath = Files.list(dir)
                        .filter(p -> !Files.isDirectory(p) && p.toString().endsWith(".csv"))
                        .min((p1, p2) -> Long.compare(p2.toFile().lastModified(), p1.toFile().lastModified()));
                if (opPath.isEmpty())
                    return new TreeSet<>();
                else
                    log_csv_name = opPath.get().toString();
            } else
                return new TreeSet<>();

            //берем первую (заголовки) и последнюю (текущие значения) строку
            String current, param_names = null,  param_vals = null;
            BufferedReader bufferedReader = new BufferedReader(new FileReader(log_csv_name));
            int i = 0;
            while ((current = bufferedReader.readLine()) != null) {
                i++;
                if (i == 1)
                    param_names = current;
                else
                    param_vals = current;
            }
            bufferedReader.close();

            if (param_names == null || param_vals == null)
                return new TreeSet<>();

            String[] param_param_names = param_names.split(",", -1);
            String[] param_vals_arr = param_vals.split(",", -1);
            //выбираем данные по всем температурам и загруженности устройств, кроме детальных и загруженности дисков
            for (int j = 0; j < param_param_names.length; j++) {
                if ((param_param_names[j].contains("temperature") || (param_param_names[j].contains("load") && !param_param_names[j].contains("hdd")))
                        && param_param_names[j].endsWith("/0")
                        && (!Objects.equals(param_vals_arr[j], ""))
                ){
                    hardware.add(new HardwareInfo(param_param_names[j], param_vals_arr[j]));
                }
            }
        } catch (Exception err) {
            err.printStackTrace();
        }
        return hardware;
    }

    protected String getJSONString(TreeSet<ProcessInfo> procs, TreeSet<GameInfo> games, TreeSet<HardwareInfo> hardware) {
        JSONArray jProcs = new JSONArray();
        JSONArray jGames = new JSONArray();
        JSONArray jHardware = new JSONArray();

        procs = filterProcesses(procs);

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
        //список параметров оборудования к отправке
        for (HardwareInfo next : hardware) {
            JSONObject jo = new JSONObject();
            jo.put("name", next.name);
            jo.put("value", next.value);
            jHardware.add(jo);
        }

        //сообщение
        JSONObject message = new JSONObject();
        //команда
        message.put("text", "/get_procs");
        //параметры пакета
        message.put("pcName", pcName);
        message.put("mtsName", mtsName);
        message.put("pcGuid", pcGuid);
        message.put("userId", userId);
        message.put("sessionUUID", sessionUUID);
        message.put("time", System.currentTimeMillis());
        //список процессов
        message.put("procs", jProcs);
        //список игр
        message.put("games", jGames);
        //список игр
        message.put("hardware", jHardware);

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
            if (firstTime) {
                games = listGamesFromLib();
                if (saves) asListener.setGames(games);
            }
            TreeSet<ProcessInfo> procs = listRunningProcesses();

            //отсылаем заполненную библиотеку только первый раз
            byte[] input = getJSONString(procs, (firstTime) ? games : new TreeSet<>(), listHardwareInfo()).getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);

            //Дополнительно: включаем запись, как только пропадает процесс explorer - один раз
            //только если, процесс вообще запущен (т.е. запись видео включена пользователем)
            boolean explorer_runing = false;
            for (ProcessInfo proc : procs) {
                if (Objects.equals(proc.name, "explorer")) {
                    explorer_runing = true;
                    break;
                }
            }
            if (!explorer_runing && !explorer_stopped) {
                explorer_stopped = true;
                System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Проводник закрыт - запускаем запись сейвов\n");
                if (saves) asListener.catchAction();
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
