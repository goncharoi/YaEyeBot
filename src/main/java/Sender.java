import org.apache.commons.io.FilenameUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;

public class Sender extends Thread {
    private static final String hwReportFile = "AIDA64\\rep_log.csv";
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
    protected TreeSet<ProcessInfo> previousProcs = new TreeSet<>();

    protected boolean hardware;
    protected boolean hwiShort;

    public Sender(ASListener asListener, Boolean saves, String pcName, String mtsName, String userId, Boolean filterServices, Byte timeout, String dbPath, String pcGuid, Boolean hardware, Boolean hwiShort) {
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
        this.hwiShort = hwiShort;
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
                System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
                err.printStackTrace();
            }
        } while (true);
    }

    protected TreeSet<ProcessInfo> listRunningProcesses() {
        TreeSet<ProcessInfo> processes = new TreeSet<>();
        try {
            String line;
            String[] info;
            ProcessBuilder pbProcsMan = new ProcessBuilder("BBB_ProcsMan.exe");
            Process p = pbProcsMan.start();

            BufferedReader input = new BufferedReader
                    (new InputStreamReader(p.getInputStream(), "866"));
            while ((line = input.readLine()) != null) {
                info = line.split(" ## ", -1);

                if (Arrays.stream(info).count() > 3) {
                    for (int i = 0; i < info.length; i++)
                        info[i] = info[i].trim();

                    info[4] = info[4].replaceAll(" ", "_"); //читаемое описание, если есть
                    if (info[2].equals("")) info[2] = info[3]; //если версия файла пустая, берется версия продукта
                    String name = FilenameUtils.getBaseName(info[4].replaceAll(":", "_"));
                    if (info[4].equals("")) info[4] = name; //если описание файла пустое, берется его имя

                    processes.add(
                            new ProcessInfo(
                                    name, //name
                                    info[4], //type
                                    info[1], //exe
                                    info[2], //version
                                    info[0]  //handle
                            )
                    );
                }
            }
            input.close();
        } catch (Exception err) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
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
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            err.printStackTrace();
        }
        //добавляем игры из Steam
        games.addAll(SteamReader.getSteamGames((!Objects.equals(dbPath, "")) ? dbPath : dbPathDefault));

        //вычисляем актуальную версию ехе
        for (GameInfo game : games) {
            String version = "";
            try {
                version = EXEFileInfo.getVersionInfo(game.gPath);
            } catch (Exception err) {
                System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
                err.printStackTrace();
            }
            if (!version.equals(""))
                game.gVers = version;
        }

        return games;
    }

    protected TreeSet<HardwareInfo> listHardwareInfoShort() {
        TreeSet<HardwareInfo> hardware = new TreeSet<>();
        String line;

        if (!this.hardware) return hardware;

        String[] info;
        try {
            ProcessBuilder pbProcsMan = new ProcessBuilder("GetHWInfo_Run.bat");
            Process p = pbProcsMan.start();

            BufferedReader input = new BufferedReader
                    (new InputStreamReader(p.getInputStream(), "866"));
            while ((line = input.readLine()) != null) {
                info = line.split(" ", -1);
                if (
                        Objects.equals(info[0], "GPU_temperature") ||
                                Objects.equals(info[0], "GPU_load")
                )
                    hardware.add(new HardwareInfo(info[0], info[1]));
            }
            input.close();
        } catch (IOException ex) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            ex.printStackTrace();
        }

        return hardware;
    }

    protected TreeSet<HardwareInfo> listHardwareInfoFull() {
        TreeSet<HardwareInfo> hardware = new TreeSet<>();
        String log_csv_name;

        if (!this.hardware) return hardware;

        try {
            //получаем имя самого свежего csv-файла в папке OpenHardwareMonitor
            Path dir = Paths.get("lhm");
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
            String current, param_names = null, param_vals = null;
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
                ) {
                    hardware.add(new HardwareInfo(param_param_names[j], param_vals_arr[j]));
                }
            }
        } catch (Exception err) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            err.printStackTrace();
        }
        return hardware;
    }


    protected String getJSONString(boolean procsChanged, TreeSet<ProcessInfo> procs, TreeSet<GameInfo> games, TreeSet<HardwareInfo> hardware) {
        JSONArray jProcs = new JSONArray();
        JSONArray jGames = new JSONArray();
        JSONArray jHardware = new JSONArray();

        //список процессов к отправке, если есть изменения
        if (procsChanged)
            for (ProcessInfo next : procs) {
                JSONObject jo = new JSONObject();
                jo.put("handle", next.handle);
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
        //список параметров оборудования к отправке, если есть изменения
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
        message.put("procsChanged", procsChanged);
        message.put("procs", jProcs);
        //список игр
        message.put("games", jGames);
        //список параметров оборудования
        message.put("hardware", jHardware);
        message.put("frontendVersion", Main.version);

        //пакет для сообщения
        JSONObject mainObj = new JSONObject();
        mainObj.put("message", message);

        String result = mainObj.toJSONString();

        System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Сформированы данные для отправки\n");
        System.out.println(result);

        return result;
    }

    protected void sendPOST() throws Exception {
        //получаем данные о библиотеке лишь один раз
        if (firstTime) {
            games = listGamesFromLib();
            if (saves)
                asListener.setGames(games);
        }

        TreeSet<ProcessInfo> procs = listRunningProcesses();
        boolean procsChanged = !(procs.containsAll(previousProcs) && previousProcs.containsAll(procs));
        if (procsChanged)
            previousProcs = procs;

        TreeSet<HardwareInfo> hw = (hwiShort) ? listHardwareInfoShort() : listHardwareInfoFull();

        //отсылаем заполненную библиотеку только первый раз
        byte[] input = getJSONString(
                procsChanged,
                procs,
                (firstTime) ? games : new TreeSet<>(),
                hw
        ).getBytes(StandardCharsets.UTF_8);

        ConToBot conToBot = new ConToBot();
        HttpURLConnection con = conToBot.getCon();
        try (OutputStream os = con.getOutputStream()) {
            os.write(input, 0, input.length);
        } catch (Exception err) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            err.printStackTrace();
        }
        //если что-то пошло не так - повторим в следующий раз отправку библиотеки,
        //но только если она еще не была отправлена в эту сессию
        firstTime = !conToBot.closeCon() && firstTime;

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
            if (saves)
                asListener.catchAction();
        }
    }
}
