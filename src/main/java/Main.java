import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static javax.swing.JOptionPane.ERROR_MESSAGE;

public class Main extends JFrame {
    //guid for Inno Setup: A2C1BDFF-AC19-402E-90EC-B56B00036870
    public static final String version = "3.3.3";
    public static final String outNUStorage = "BotNeedUpdate.txt";
    public static final String outMTSNUStorage = "MTSNeedUpdate.txt";
    public static final String updaterDir = "../YaEyeBotUpdater/";
    public static final int pcNameLimit = 20;

    public static ServerSocket lock;

    public static Main app;
    private final TrayIcon iconTr;
    private final SystemTray sT = SystemTray.getSystemTray();
    public boolean chetTray = false; //переменная, чтобы был вывод сообщения в трее только при первом сворачивании
    private Sender sender;
    private Listener listener;
    private ASListener asListener;

    protected JLabel pcNameLabel, mtsNameLabel, userIdLabel, dbPathLabel, dstPathLabel, approveLabel, fpsLabel, durationLabel, offsetLabel, vldLabel, sldLabel;
    protected JTextField pcNameField, mtsNameField, userIdField, dbPathField, dstPathField, fpsField, durationField, offsetField, vldField, sldField;
    protected JButton dbPathButton, dstPathButton, approveButton;
    protected JCheckBox videoCheckBox, chatCheckBox, savesCheckBox, hardwareCheckBox, hwiShortCheckBox, autoUpdateCheckBox, autoUpdateMTSCheckBox;
    protected JHyperlink goToBot, goToChat, toVideoDir;
    protected JLabel qrToBotLabel, qrToChatLabel;

    //настройки отслеживания
    protected String pcName = ""; //имя отслеживаемого ПК
    protected String mtsName = ""; //имя ПК на МТС Fog Play
    protected String userId = ""; //id пользователя телеграмма
    protected Boolean filterServices = true; //фильтровать процессы типа Services
    protected Byte timeout = 20; //отсылать данные раз в ... сек.
    protected String dbPathDefault = System.getenv("USERPROFILE") +
            "\\AppData\\Local\\rds-wrtc\\UserGame.db"; //путь к библиотеке игр МТС-лаунчера по умолчанию
    protected String dbPath = ""; //путь к библиотеке игр МТС-лаунчера
    protected String pcGuid = "";
    protected Boolean video = false; //видеозапись сессий включена
    protected Boolean chatFlag = false; //чат с владельцем включен
    protected Byte fps = 1; //частота кадров/сек. видеозаписи сессий
    protected Integer duration = 15; //максимальная длительность одной видеозаписи (в минутах)
    protected Byte videosLifeDays = 1; //длительность хранения видеозаписей (в днях)
    protected Boolean saves = false; //запись сейвов включена
    protected String dstPath = ""; //папка сохранений
    protected Byte savesLifeDays = 3; //сколько хранить сейвы прошлых сессий (дней)
    protected Integer offset = 10; //сколько ждать перед началом записи сейвов (мин.)
    protected Boolean hardware = false; //отслеживать показатели железа
    protected Boolean hwiShort = true; //только видеокарта (1), или все (0)
    protected Boolean autoUpdate = false; //автообновление программы-датчика
    protected Boolean autoUpdateMTS = false; //автообновление МТС Remote Play

    public Main() throws IOException {
        super("Отслеживание ПК v" + version);
        //архивируем логи прошлой сессии (в самом начале, чтоб не перетереть текущими)
        zipLogs();
        //перенаправляем вывод в файлы
        System.setOut(new PrintStream(new FileOutputStream("video\\logs\\latest\\out.log")));
        System.setErr(new PrintStream(new FileOutputStream("video\\logs\\latest\\err.log")));
        //чтение настроек
        readSetting();
        //проверяем необходимость установки лаунчера-обновлятора - происходит только при первом зауске
        checkNeedUpdateInstall();

        System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: настраиваем отображение в трее\n");
        iconTr = new TrayIcon(ImageIO.read(new File("icon.png")), "Отслеживание ПК");
        iconTr.addActionListener(ev -> {
            setVisible(true);
            setState(JFrame.NORMAL);
            removeTr();
        });
        //обработчик мыши
        MouseMotionListener mouM = new MouseMotionListener() {
            public void mouseDragged(MouseEvent ev) {
            }

            //при наведении
            public void mouseMoved(MouseEvent ev) {
                iconTr.setToolTip("Двойной щелчок - развернуть");
            }
        };
        iconTr.addMouseMotionListener(mouM);
        addWindowStateListener(ev -> {
            if (ev.getNewState() == JFrame.ICONIFIED) {
                setVisible(false);
                addTr();
            }
        });

        //создаем интерфейс пользователя
        initFace();
        //запускаем LibreHardwareMonitor.exe
        if (hardware && !hwiShort)
            runLHM();
        //создаем потоки для отсылки данных и записи видео, если есть все настройки
        if (!Objects.equals(pcGuid, "")) {
            asListener = new ASListener(dstPath, offset, savesLifeDays);
            listener = new Listener(video, fps, duration, videosLifeDays, pcName, userId, pcGuid, chatFlag, autoUpdateMTS);
            sender = new Sender(asListener, saves, pcName, mtsName, userId, filterServices, timeout, dbPath, pcGuid, hardware, hwiShort);
        }
    }

    public void runLHM() {
        try {
            //удаляем старые логи
            Runtime.getRuntime().exec("Forfiles -p lhm -s -m *.csv -d -" + 3 + " -c \"cmd /c del /q @path\"");

            String path = getJarPath(Main.class);
            if (path != null) {
                path += "\\lhm";
                ProcessBuilder pb = new ProcessBuilder(path + "\\LibreHardwareMonitor.exe");
                pb.redirectErrorStream(true);
                pb.start();
            } else
                throw new Exception("не удалось определить путь к утилите сбора физических данных");
        } catch (Exception ex) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            ex.printStackTrace();
        }
    }

    public void installUpdater() {
        //копируем обновлятор в отдельную папку, под защиту дефендера
        copyFileToDir("Updater.jar", updaterDir);
        copyFileToDir("vipFiles.ini", updaterDir);

        //создаем там же резервные копии настроек бота
        copyFileToDir("settings.json", updaterDir);
        copyFileToDir("MTSUpdate.ps1", updaterDir);

        try {
            System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: создаем там же файл, где будут контрольные данные\n");
            //создаем там же файл, где будет прописан путь до папки бота (на всякий случай)
            FileWriter fwYaEyeBotCheckData = new FileWriter(updaterDir + "YaEyeBotCheckData.txt");
            String jarPath = getJarPath(Main.class);
            //и в него же пишем контрольные суммы и прочие контрольные данные
            BotCheckData botCheckData = new BotCheckData(new File(jarPath));
            fwYaEyeBotCheckData.write(jarPath + "\n" + botCheckData.checkSum);
            for (var vipFile : botCheckData.vipFiles)
                fwYaEyeBotCheckData.write("\n" + vipFile);
            fwYaEyeBotCheckData.close();

            //сбрасываем флаг необходимости обновления
            FileWriter fwBotNeedUpdate = new FileWriter("BotNeedUpdate.txt");
            fwBotNeedUpdate.write("0");
            fwBotNeedUpdate.close();
        } catch (IOException e) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            e.printStackTrace();
        }

        System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: создаем ярлык для запуска в автозагрузке обновлятора\n");
        //создаем ярлык для запуска в автозагрузке обновлятора
        try {
            String startupPath = System.getProperty("user.home") +
                    "\\AppData\\Roaming\\Microsoft\\Windows\\Start Menu\\Programs\\Startup";

            createShortcut(updaterDir + "\\Updater.jar", startupPath + "\\Updater.lnk", updaterDir);
        } catch (Exception e) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            e.printStackTrace();
        }
    }

    /**
     * Creates a Shortcut at the passed location linked to the passed source<br>
     * Note - this will pause thread until shortcut has been created
     *
     * @param source   - The path to the source file to create a Shortcut to
     * @param linkPath - The path of the Shortcut that will be created
     * @throws FileNotFoundException if the source file cannot be found
     */
    public static void createShortcut(String source, String linkPath, String workingDir) throws FileNotFoundException {
        File sourceFile = new File(source);
        if (!sourceFile.exists()) {
            throw new FileNotFoundException("The Path: " + sourceFile.getAbsolutePath() + " does not exist!");
        }
        File workingDirFile = new File(workingDir);
        if (!workingDirFile.exists()) {
            throw new FileNotFoundException("The Path: " + workingDirFile.getAbsolutePath() + " does not exist!");
        }
        try {
            String absSource = sourceFile.getAbsolutePath();
            String absWorkingDir = workingDirFile.getAbsolutePath();

            String vbsCode = String.format(
                    "Set wsObj = WScript.CreateObject(\"WScript.shell\")%n"
                            + "scPath = \"%s\"%n"
                            + "Set scObj = wsObj.CreateShortcut(scPath)%n"
                            + "\tscObj.TargetPath = \"%s\"%n"
                            + "\tscObj.WorkingDirectory = \"%s\"%n"
                            + "scObj.Save%n",
                    linkPath, absSource, absWorkingDir
            );

            newVBS(vbsCode);
        } catch (Exception e) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            e.printStackTrace();
        }
    }

    /**
     * Creates a VBS file with the passed code and runs it, deleting it after the run has completed
     */
    private static void newVBS(String code) throws IOException, InterruptedException {
        File script = File.createTempFile("scvbs", ".vbs"); // File where script will be created

        // Writes to script file
        FileWriter writer = new FileWriter(script);
        writer.write(code);
        writer.close();

        Process p = Runtime.getRuntime().exec("wscript \"" + script.getAbsolutePath() + "\""); // executes vbs code via cmd
        p.waitFor(); // waits for process to finish
        if (!script.delete()) { // deletes script
            System.err.println("Warning Failed to delete temporary VBS File at: \"" + script.getAbsolutePath() + "\"");
        }
    }

    public static void copyFileToDir(String sourceFileName, String destinationDirectory) {
        File sourceFile = new File(sourceFileName);
        File destinationFile = new File(destinationDirectory + sourceFile.getName());
        System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: копируем " + sourceFile.toPath() + " в " + destinationFile.toPath() + "\n");

        try {
            File destinationFolder = new File(destinationDirectory);
            if (!destinationFolder.exists())
                if (!destinationFolder.mkdirs())
                    throw new Exception("Не удалось создать " + destinationDirectory);

            Files.copy(sourceFile.toPath(), destinationFile.toPath(), REPLACE_EXISTING);
        } catch (Exception e) {
            System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Error copying file: " + e.getMessage() + "\n");
        }
    }

    public void zipLogs() {
        //удаляем старые логи
        try {
            Runtime.getRuntime().exec("Forfiles -p " + "video\\logs" + " -s -m *.zip -d -" + 3 + " -c \"cmd /c del /q @path\"");
        } catch (IOException ex) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            ex.printStackTrace();
        }
        //архивируем логи прошлой сессии
        try {
            SimpleDateFormat formater = new SimpleDateFormat("dd.MM.yyyy_HH.mm");
            String fName = formater.format(new Date());
            ZippingVisitor.zipWalking(
                    new File("video\\logs\\latest").toPath(),
                    new File("video\\logs\\" + fName + ".zip").toPath()
            );
        } catch (Exception ex) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            ex.printStackTrace();
        }
    }

    public void initFace() {
        System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: создаем интерфейс пользователя\n");
        int inset = 4;
        // Создание полей
        // --имя ПК
        pcNameLabel = new JLabel("Введите уникальное имя ПК");
        if (Objects.equals(pcName, "")) {
            try {
                pcName = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException err) {
                System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
                err.printStackTrace();
            }
        }
        pcNameField = new JTextField(pcName, pcNameLimit);
        pcNameField.addKeyListener(new TextMaxLengthVerifier(pcNameField, pcNameLimit));
        // --имя ПК на сайте МТС
        mtsNameLabel = new JLabel("Введите имя ПК на МТС Fog Play");
        mtsNameField = new JTextField(mtsName, pcNameLimit);
        mtsNameField.addKeyListener(new TextMaxLengthVerifier(mtsNameField, pcNameLimit));
        //если pcGuid уже выделен - запрещаем смену имени
        if (!Objects.equals(pcGuid, "")) pcNameField.setEnabled(false);

        // --id в телеграмм
        userIdLabel = new JLabel("Введите id Вашего аккаунта в телеграмм");
        userIdField = new NumericTextField();
        userIdField.setInputVerifier(new NumVerifier(NumVerifier.fieldType.LONG));
        userIdField.setColumns(15);
        userIdField.setText(userId);

        // --путь к библиотеке
        dbPathLabel = new JLabel("Путь к библиотеке игр МТС");
        dbPathField = new JTextField((Objects.equals(dbPath, "")) ? dbPathDefault : dbPath, 35);
        dbPathField.setEnabled(false); //вручную вводить нельзя
        dbPathButton = new JButton("Указать путь к библиотеке");
        dbPathButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser((Objects.equals(dbPath, "")) ? dbPathDefault : dbPath);
            fileChooser.setDialogTitle("Укажите файл библиотеки игр UserGame.db");
            fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("SQLite база данных", "db"));
            int ret = fileChooser.showDialog(null, "Ok");
            if (ret == JFileChooser.APPROVE_OPTION) {
                dbPath = fileChooser.getSelectedFile().getAbsolutePath();
                dbPathField.setText(dbPath);
            }
        });

        // --галка включения чата с владельцем ПК
        chatCheckBox = new JCheckBox("Позволить вызывать диалог с владельцем");
        chatCheckBox.setSelected(chatFlag);

        //Настройки видеозаписи
        // --галка включения видеозаписи
        videoCheckBox = new JCheckBox("Вести ведиозапись сессий");
        videoCheckBox.setSelected(video);
        // --частота кадров
        fpsLabel = new JLabel("Частота кадров/сек. (рекомендуется менее 5)");
        fpsField = new NumericTextField();
        fpsField.setInputVerifier(new NumVerifier(NumVerifier.fieldType.BYTE));
        fpsField.setColumns(3);
        fpsField.setText(fps.toString());
        // --максимальная длительность одной видеозаписи
        durationLabel = new JLabel("Максимальная длительность одной видеозаписи (в минутах)");
        durationField = new NumericTextField();
        durationField.setInputVerifier(new NumVerifier(NumVerifier.fieldType.INT));
        durationField.setColumns(3);
        durationField.setText(duration.toString());
        // --длительность хранения видеозаписей
        vldLabel = new JLabel("Длительность хранения видеозаписей (в днях) ");
        vldField = new NumericTextField();
        vldField.setInputVerifier(new NumVerifier(NumVerifier.fieldType.BYTE));
        vldField.setColumns(2);
        vldField.setText(videosLifeDays.toString());
        //включение/отключение полей
        videoCheckBox.addActionListener(e -> onVideoCheckBox(true));
        onVideoCheckBox(false);

        //Настройки записи сейвов
        // --галка включения записи
        savesCheckBox = new JCheckBox("Хранить сейвы между сессиями");
        savesCheckBox.setSelected(saves);
        // --путь к папке хранилища
        dstPathLabel = new JLabel("Папка для хранения сейвов");
        dstPathField = new JTextField(dstPath, 35);
        dstPathField.setEnabled(false); //вручную вводить нельзя
        dstPathButton = new JButton("Указать папку");
        dstPathButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser(dstPath);
            fileChooser.setDialogTitle("Укажите папку для хранения сейвов");
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int ret = fileChooser.showDialog(null, "Ok");
            if (ret == JFileChooser.APPROVE_OPTION) {
                dstPath = fileChooser.getSelectedFile().getAbsolutePath();
                dstPathField.setText(dstPath);
            }
        });
        // --сколько ждать перед началом записи
        offsetLabel = new JLabel("Сколько ждать перед началом записи (в минутах)");
        offsetField = new NumericTextField();
        offsetField.setInputVerifier(new NumVerifier(NumVerifier.fieldType.INT));
        offsetField.setColumns(3);
        offsetField.setText(offset.toString());
        // --длительность хранения сейвов
        sldLabel = new JLabel("Длительность хранения сейвов (в днях) ");
        sldField = new NumericTextField();
        sldField.setInputVerifier(new NumVerifier(NumVerifier.fieldType.BYTE));
        sldField.setColumns(2);
        sldField.setText(savesLifeDays.toString());
        //включение/отключение полей
        savesCheckBox.addActionListener(e -> onSavesCheckBox(true));
        onSavesCheckBox(false);

        //Галка включения отслеживания показателей железа
        hardwareCheckBox = new JCheckBox("Отслеживать показатели железа");
        hardwareCheckBox.setSelected(hardware);
        hardwareCheckBox.addActionListener(e -> onHardwareCheckBox());
        hwiShortCheckBox = new JCheckBox("Только видеокарта");
        hwiShortCheckBox.setSelected(hwiShort);
        hwiShortCheckBox.addActionListener(e -> onHWIShortCheckBox());

        //Галка включения автообновления программы-датчика
        autoUpdateCheckBox = new JCheckBox("Автообновление программы-датчика");
        autoUpdateCheckBox.setSelected(autoUpdate);
        autoUpdateCheckBox.addActionListener(e -> onAutoUpdateCheckBox());
        //Галка включения автообновления МТС Remote Play
        autoUpdateMTSCheckBox = new JCheckBox("Автообновление МТС Remote Play");
        autoUpdateMTSCheckBox.setSelected(autoUpdateMTS);
        autoUpdateMTSCheckBox.addActionListener(e -> onAutoUpdateMTSCheckBox());

        // --ссылки
        goToBot = new JHyperlink("Бот для отчетов (так же выдает id пользователя)",
                "https://t.me/YaEyebot?start");
        qrToBotLabel = new JLabel();
        qrToBotLabel.setIcon(new ImageIcon("QRbot.png"));
        goToChat = new JHyperlink("Чат для вопросов и новостей", "https://t.me/YadrenoChat/6");
        qrToChatLabel = new JLabel();
        qrToChatLabel.setIcon(new ImageIcon("QRchat.png"));
        toVideoDir = new JHyperlink("Папка с видео", "video");

        // --кнопка подтверждения введенных значений
        approveLabel = new JLabel("При нажатии на эту кнопку все настройки будут сохранены, а программа закрыта");
        approveButton = new JButton("Подтвердить настройки");
        approveButton.addActionListener(e -> onApproveNewSettings());

        // Создание панелей с полями
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(inset, inset, inset, inset);

        // --панель основных настроек
        JPanel optionsPanel = new JPanel(new GridBagLayout());
        constraints.gridx = 0;
        constraints.gridy = 0;
        optionsPanel.add(pcNameLabel, constraints);
        constraints.gridx = 1;
        optionsPanel.add(pcNameField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        optionsPanel.add(userIdLabel, constraints);
        constraints.gridx = 1;
        optionsPanel.add(userIdField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 2;
        optionsPanel.add(dbPathLabel, constraints);
        constraints.gridx = 1;
        optionsPanel.add(dbPathField, constraints);
        constraints.gridx = 1;
        constraints.gridy = 3;
        optionsPanel.add(dbPathButton, constraints);
        optionsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Основные настройки"));
        constraints.gridx = 0;
        constraints.gridy = 4;
        optionsPanel.add(chatCheckBox, constraints);
        constraints.gridx = 0;
        constraints.gridy = 5;
        optionsPanel.add(mtsNameLabel, constraints);
        constraints.gridx = 1;
        optionsPanel.add(mtsNameField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 6;
        optionsPanel.add(hardwareCheckBox, constraints);
        constraints.gridx = 1;
        optionsPanel.add(hwiShortCheckBox, constraints);
        constraints.gridx = 0;
        constraints.gridy = 7;
        optionsPanel.add(autoUpdateCheckBox, constraints);
        constraints.gridx = 0;
        constraints.gridy = 8;
        optionsPanel.add(autoUpdateMTSCheckBox, constraints);

        // --панель настроек видеозаписи сессий
        JPanel videoPanel = new JPanel(new GridBagLayout());
        constraints.gridx = 0;
        constraints.gridy = 0;
        videoPanel.add(videoCheckBox, constraints);
        constraints.gridx = 1;
        videoPanel.add(toVideoDir, constraints);
        constraints.gridx = 0;
        constraints.gridy = 1;
        videoPanel.add(fpsLabel, constraints);
        constraints.gridx = 1;
        videoPanel.add(fpsField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.insets = new Insets(inset, inset, inset, inset);
        videoPanel.add(durationLabel, constraints);
        constraints.gridx = 1;
        videoPanel.add(durationField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 3;
        videoPanel.add(vldLabel, constraints);
        constraints.gridx = 1;
        videoPanel.add(vldField, constraints);
        videoPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Настройки видеозаписи сессий"));

        // --панель настроек видеозаписи сессий
        JPanel savesPanel = new JPanel(new GridBagLayout());
        constraints.gridx = 0;
        constraints.gridy = 0;
        savesPanel.add(savesCheckBox, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        savesPanel.add(dstPathLabel, constraints);
        constraints.gridx = 1;
        savesPanel.add(dstPathField, constraints);
        constraints.gridx = 1;
        constraints.gridy = 2;
        savesPanel.add(dstPathButton, constraints);

        constraints.gridx = 0;
        constraints.gridy = 3;
        savesPanel.add(offsetLabel, constraints);
        constraints.gridx = 1;
        savesPanel.add(offsetField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 4;
        savesPanel.add(sldLabel, constraints);
        constraints.gridx = 1;
        savesPanel.add(sldField, constraints);
        savesPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Настройки записи сейвов"));

        // --кнопка подтверждения
        JPanel approve = new JPanel(new GridBagLayout());
        constraints.gridx = 0;
        constraints.gridy = 0;
        approve.add(approveLabel, constraints);
        constraints.gridy = 1;
        approve.add(approveButton, constraints);

        // --контакты
        JPanel linksPanel = new JPanel(new GridBagLayout());
        constraints.gridx = 0;
        constraints.gridy = 0;
        linksPanel.add(goToBot, constraints);
        constraints.gridx = 1;
        linksPanel.add(goToChat, constraints);
        constraints.gridx = 0;
        constraints.gridy = 1;
        linksPanel.add(qrToBotLabel, constraints);
        constraints.gridx = 1;
        linksPanel.add(qrToChatLabel, constraints);
        linksPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Полезные ссылки"));

        //собираем вместе
        JPanel bodyPanel = new JPanel(new GridBagLayout());
        constraints.gridx = 0;
        constraints.gridy = 0;
        bodyPanel.add(optionsPanel, constraints);
        constraints.gridy = 1;
        bodyPanel.add(videoPanel, constraints);
        constraints.gridy = 2;
        bodyPanel.add(savesPanel, constraints);
        constraints.gridy = 3;
        bodyPanel.add(linksPanel, constraints);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(bodyPanel, BorderLayout.NORTH);
        mainPanel.add(approve, BorderLayout.SOUTH);
        setContentPane(mainPanel);
    }

    private void onVideoCheckBox(boolean showMsg) {
        fpsField.setEnabled(videoCheckBox.isSelected());
        durationField.setEnabled(videoCheckBox.isSelected());
        vldField.setEnabled(videoCheckBox.isSelected());
        if (videoCheckBox.isSelected() && showMsg)
            JOptionPane.showMessageDialog(null,
                    "Необходимо включить папку video в исключения ПО," +
                            "\nсохраняющего дефолтное состояние ПК (см. ссылку)");
    }

    private void onSavesCheckBox(boolean showMsg) {
        dstPathField.setEnabled(savesCheckBox.isSelected());
        offsetField.setEnabled(savesCheckBox.isSelected());
        sldField.setEnabled(savesCheckBox.isSelected());
        dstPathButton.setEnabled(savesCheckBox.isSelected());
        if (savesCheckBox.isSelected() && showMsg)
            JOptionPane.showMessageDialog(null,
                    "Необходимо включить выбранную папку в исключения ПО," +
                            "\nсохраняющего дефолтное состояние ПК");
    }

    private void onHardwareCheckBox() {
        hwiShortCheckBox.setEnabled(hardwareCheckBox.isSelected());
        if (hardwareCheckBox.isSelected())
            onHWIShortCheckBox();
    }

    private void onHWIShortCheckBox() {
        if (!hwiShortCheckBox.isSelected())
            JOptionPane.showMessageDialog(null,
                    """
                            Данные о процессоре, памяти и т.д. - доступны только при запуске с определенными правами.
                            Если вы настраивали систему по инструкции MTS FogPlay и у вас полностью выключен UAC,
                            то должно работать.
                                                        
                            В противном случае работа не гарантируется. Можете попробовать настроить задание
                            в Планировщике заданий по инструкции: YaEyeBot\\lhm\\manual.docx
                                                        
                            Если же галку включить, проблем с правами быть не должно, но вы будете получать только
                            данные по температуре и загрузке карты""");
    }

    private void onAutoUpdateCheckBox() {
        if (autoUpdateCheckBox.isSelected())
            JOptionPane.showMessageDialog(null,
                    """
                            Необходимо включить всю папку YaEyeBot в исключения ПО,
                            сохраняющего дефолтное состояние ПК.
                                                        
                            Если злоумышленник удалит бота, программа автообновления
                            восстановит его со всеми настройками.""");
    }

    private void onAutoUpdateMTSCheckBox() {
        if (autoUpdateMTSCheckBox.isSelected())
            JOptionPane.showMessageDialog(null,
                    """
                            Галка включает реакцию программы-датчика на оповещение бота о появлении
                            новой версии МТС Remote Play, которая заключается в запуске скрипта MTSUpdate.ps1
                            (лежит в папке YaEyeBot).
                                                        
                            Поскольку настройки игровых ПК сильно отличаются, программа
                            НЕ ГАРАНТИРУЕТ работоспособность скрипта именно на вашем ПК.
                            По сути, это просто пример того, как оно работает на ПК автора.
                                                        
                            Нужно самостоятельно изменить скрипт MTSUpdate.ps1 с учетом своих настроек,
                            путей и т.п. Этот файл не будет затираться при обновлении бота.
                                                        
                            (В случае изменения MTSUpdate.ps1 - сделайте его резервную копию в папку
                            ../YaEyeBotUpdater/ - по соседству с папкой бота. Так он окажется под защитой
                            ПО, сохраняющего дефолтное состояние ПК.)
                                                        
                            В любом случае, необходимо включить папку, куда установлен МТС Remote Play,
                            в исключения ПО, сохраняющего дефолтное состояние ПК
                            (папку с настройками МТС Remote Play включать не нужно).""");
    }

    private void onApproveNewSettings() {
        //проверки корректности ввода
        String msg = "";
        if (Objects.equals(pcNameField.getText(), ""))
            msg = "Уникальное имя ПК - обязательное поле";
        if (pcNameField.getText().length() > pcNameLimit)
            msg = "Превышена максимальная длина имени ПК (" + pcNameLimit + ")";
        if (mtsNameField.getText().length() > pcNameLimit)
            msg = "Превышена максимальная длина имени ПК на МТС Fog Play (" + pcNameLimit + ")";
        if (Objects.equals(userIdField.getText(), ""))
            msg = "Ваш id в телеграмм - обязательное поле";
        if (videoCheckBox.isSelected()) {
            if (Objects.equals(fpsField.getText(), ""))
                msg = "Частота кадров - обязательное поле";
            if (Objects.equals(durationField.getText(), ""))
                msg = "Длительность записи - обязательное поле";
            if (Objects.equals(vldField.getText(), ""))
                msg = "Срок хранения видео - обязательное поле";

        }
        if (!msg.equals("")) {
            JOptionPane.showMessageDialog(null, msg, "Ошибка", ERROR_MESSAGE);
            return;
        }
        //сохранение настроек
        try {
            FileWriter fw = new FileWriter("settings.json");

            JSONObject jo = new JSONObject();
            jo.put("pcName", pcNameField.getText());
            jo.put("mtsName", mtsNameField.getText());
            jo.put("userId", userIdField.getText());
            jo.put("filterServices", (filterServices) ? "1" : "0");
            jo.put("timeout", timeout.toString());
            jo.put("dbPath", dbPathField.getText());
            jo.put("chatFlag", (chatCheckBox.isSelected()) ? "1" : "0");
            //видео-настройки
            jo.put("video", (videoCheckBox.isSelected()) ? "1" : "0");
            jo.put("fps", fpsField.getText());
            jo.put("duration", durationField.getText());
            jo.put("videosLifeDays", vldField.getText());
            //генерация pcGuid если он пустой
            jo.put("pcGuid", (Objects.equals(pcGuid, "")) ? UUID.randomUUID().toString() : pcGuid);
            //настройки сейвов
            jo.put("saves", (savesCheckBox.isSelected()) ? "1" : "0");
            jo.put("dstPath", dstPathField.getText());
            jo.put("offset", offsetField.getText());
            jo.put("savesLifeDays", sldField.getText());
            //настройки отслеживания показателей железа
            jo.put("hardware", (hardwareCheckBox.isSelected()) ? "1" : "0");
            jo.put("hwiShort", (hwiShortCheckBox.isSelected()) ? "1" : "0");
            //настройки автообновлений
            jo.put("autoUpdate", (autoUpdateCheckBox.isSelected()) ? "1" : "0");
            jo.put("autoUpdateMTS", (autoUpdateMTSCheckBox.isSelected()) ? "1" : "0");

            fw.write(jo.toJSONString());
            fw.close();
        } catch (Exception err) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            err.printStackTrace();
            return;
        }

        //обновляем резервные копии настроек бота в папке обновлятора - под защитой дефендера
        copyFileToDir("settings.json", updaterDir);
        copyFileToDir("MTSUpdate.ps1", updaterDir);

        JOptionPane.showMessageDialog(null, "Настройки сохранены и вступят в силу после перезапуска программы");
        this.dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
    }

    // метод удаления из трея
    private void removeTr() {
        sT.remove(iconTr);
    }

    // метод добавления в трей
    private void addTr() {
        try {
            sT.add(iconTr);
            if (!chetTray) {
                iconTr.displayMessage("Отслеживание ПК", "Программа свернулась", TrayIcon.MessageType.INFO);
            }
            chetTray = true;
        } catch (AWTException ex) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        //защита от двойного запуска: занимаем порт
        try {
            lock = new ServerSocket(55555);
        } catch (Exception ex) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            ex.printStackTrace();
            System.exit(0);//завершаем программу
        }

        app = new Main();
        app.setSize(750, 930);
        app.setResizable(false);
        //если pcGuid уже выделен - сворачиваем в трей
        if (!Objects.equals(app.pcGuid, "")) {
            app.setVisible(false);
            app.addTr();
        } else
            app.setVisible(true);

        app.addWindowListener(new WindowListener() {
            public void windowClosing(WindowEvent winEvent) {
                app.onExit();
                System.exit(0);//при закрытии окна завершаем программу
            }

            public void windowActivated(WindowEvent winEvent) {
            }

            public void windowClosed(WindowEvent winEvent) {
            }

            public void windowDeactivated(WindowEvent winEvent) {
            }

            public void windowDeiconified(WindowEvent winEvent) {
            }

            public void windowIconified(WindowEvent winEvent) {
            }

            public void windowOpened(WindowEvent winEvent) {
            }
        });

        //запускаем отслеживание событий для начала захвата видео
        if (app.listener != null) app.listener.start();
        //запускаем отслеживание событий для начала резервирования сейвов
        if (app.asListener != null && app.saves) app.asListener.start();
        //запускаем пересылку данных
        if (app.sender != null) {
            app.sender.setDaemon(true);
            app.sender.start();
        }

        //ловушка на перезагрузку\выключение ПК
        Runtime.getRuntime().addShutdownHook(new Thread(() -> app.onExit()));
    }

    public void onExit() {
        if (listener != null) listener.finish();
        if (sender != null) sender.interrupt();
        if (asListener != null) asListener.finish();

        try {
            //перед выходом - гасим LibreHardwareMonitor
            java.util.List<String> command = new ArrayList<>();
            command.add("taskkill");
            command.add("/IM");
            command.add("LibreHardwareMonitor.exe");
            command.add("/F");
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.start();
            //и отпускаем порт
            lock.close();
        } catch (IOException ex) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            ex.printStackTrace();
        }
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

            //настройки передачи данных
            pcName = (jsonObject.get("pcName") != null) ? jsonObject.get("pcName").toString() : "";
            mtsName = (jsonObject.get("mtsName") != null) ? jsonObject.get("mtsName").toString() : "";
            userId = (jsonObject.get("userId") != null) ? jsonObject.get("userId").toString() : "";
            filterServices = jsonObject.get("filterServices") == null &&
                    Objects.equals(jsonObject.get("filterServices").toString(), "1");
            timeout = (jsonObject.get("timeout") != null)
                    ? Byte.valueOf(jsonObject.get("timeout").toString())
                    : 20;
            dbPath = (jsonObject.get("dbPath") != null) ? jsonObject.get("dbPath").toString() : "";
            pcGuid = (jsonObject.get("pcGuid") != null) ? jsonObject.get("pcGuid").toString() : "";

            //чат с владельцем
            chatFlag = (jsonObject.get("chatFlag") != null) &&
                    Objects.equals(jsonObject.get("chatFlag").toString(), "1");

            //настройки видеозаписи сессий
            video = (jsonObject.get("video") != null) &&
                    Objects.equals(jsonObject.get("video").toString(), "1");
            fps = (jsonObject.get("fps") != null)
                    ? Byte.valueOf(jsonObject.get("fps").toString())
                    : 1;
            duration = (jsonObject.get("duration") != null)
                    ? Integer.parseInt(jsonObject.get("duration").toString())
                    : 5;
            videosLifeDays = (jsonObject.get("videosLifeDays") != null)
                    ? Byte.valueOf(jsonObject.get("videosLifeDays").toString())
                    : 1;

            //настройки хранения сейвов
            saves = (jsonObject.get("saves") != null) &&
                    Objects.equals(jsonObject.get("saves").toString(), "1");
            dstPath = (jsonObject.get("dstPath") != null) ? jsonObject.get("dstPath").toString() : "";
            offset = (jsonObject.get("offset") != null)
                    ? Integer.parseInt(jsonObject.get("offset").toString())
                    : 10;
            savesLifeDays = (jsonObject.get("savesLifeDays") != null)
                    ? Byte.valueOf(jsonObject.get("savesLifeDays").toString())
                    : 3;

            hardware = (jsonObject.get("hardware") != null) &&
                    Objects.equals(jsonObject.get("hardware").toString(), "1");
            hwiShort = (jsonObject.get("hwiShort") != null) &&
                    Objects.equals(jsonObject.get("hwiShort").toString(), "1");

            autoUpdate = (jsonObject.get("autoUpdate") != null) &&
                    Objects.equals(jsonObject.get("autoUpdate").toString(), "1");
            autoUpdateMTS = (jsonObject.get("autoUpdateMTS") != null) &&
                    Objects.equals(jsonObject.get("autoUpdateMTS").toString(), "1");
        } catch (Exception err) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            err.printStackTrace();
        }
    }

    private void checkNeedUpdateInstall() {
        String needUpdate = "0";
        try {
            FileReader fr = new FileReader(outNUStorage);
            BufferedReader buffReader = new BufferedReader(fr);
            if (buffReader.ready())
                needUpdate = buffReader.readLine();
            fr.close();
            buffReader.close();
        } catch (Exception ex) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            ex.printStackTrace();
        }
        if (Objects.equals(needUpdate, "install")) {
            //при первом запуске, в BotNeedUpdate.txt записана метка, требующая установки обновлятора
            installUpdater();
        }
    }

    public static String getJarPath(Class aclass) {
        try {
            return new File(aclass.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        } catch (Exception e) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            e.printStackTrace();
        }
        return System.getProperty("user.dir");
    }

    private static class NumVerifier extends InputVerifier {
        public enum fieldType {
            INT, BYTE, LONG
        }

        private final fieldType ft;

        public NumVerifier(fieldType ft) {
            this.ft = ft;
        }

        public boolean verify(JComponent input) {
            try {
                switch (ft) {
                    case INT -> Integer.parseInt(((JTextField) input).getText());
                    case BYTE -> Byte.parseByte(((JTextField) input).getText());
                    case LONG -> Long.parseLong(((JTextField) input).getText());
                    default -> {
                    }
                }
                input.setBackground(Color.WHITE);
                return true;
            } catch (NumberFormatException err) {
                JOptionPane.showMessageDialog(null, "Значение слишком большое, либо отсутствует", "Ошибка", ERROR_MESSAGE);
                input.setBackground(Color.PINK);
                return false;
            }
        }
    }

    private static class TextMaxLengthVerifier extends KeyAdapter {
        private final JTextField input;
        private final int limit;

        public TextMaxLengthVerifier(JTextField input, int limit) {
            this.input = input;
            this.limit = limit;
        }

        @Override
        public void keyTyped(KeyEvent evt) {
            if (input.getText().length() > limit &&
                    evt.getKeyChar() != KeyEvent.VK_BACK_SPACE &&
                    evt.getKeyChar() != KeyEvent.VK_DELETE)
                evt.consume();
        }
    }
}