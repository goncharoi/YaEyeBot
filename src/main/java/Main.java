import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

import static javax.swing.JOptionPane.ERROR_MESSAGE;

public class Main extends JFrame {
    public static ServerSocket lock;

    public static Main app;
    private final TrayIcon iconTr;
    private final SystemTray sT = SystemTray.getSystemTray();
    public boolean chetTray = false; //переменная, чтобы был вывод сообщения в трее только при первом сворачивании
    private Sender sender;
    private Listener listener;
    private ASListener asListener;
    private Chat chat;

    protected JLabel pcNameLabel, mtsNameLabel, userIdLabel, dbPathLabel, dstPathLabel, approveLabel, fpsLabel, durationLabel, offsetLabel, vldLabel, sldLabel;
    protected JTextField pcNameField, mtsNameField, userIdField, dbPathField, dstPathField, fpsField, durationField, offsetField, vldField, sldField;
    protected JButton dbPathButton, dstPathButton, approveButton;
    protected JCheckBox videoCheckBox, chatCheckBox, savesCheckBox;
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

    public Main() throws IOException {
        super("Отслеживание ПК v2.1.8");
        //архивируем логи прошлой сессии
        zipLogs();
        //перенаправляем вывод в файлы
        System.setOut(new PrintStream(new FileOutputStream("video\\logs\\latest\\out.log")));
        System.setErr(new PrintStream(new FileOutputStream("video\\logs\\latest\\err.log")));

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

        //чтение настроек
        readSetting();
        //создание интерфейса пользователя
        initFace();

        //создаем потоки для отсылки данных и записи видео, если есть все настройки
        if (!Objects.equals(pcGuid, "")) {
            asListener = new ASListener(dstPath, offset, savesLifeDays);
            listener = new Listener(fps, duration, videosLifeDays);
            sender = new Sender(asListener, saves, pcName, mtsName, userId, filterServices, timeout, dbPath, pcGuid);
            if (chatFlag) chat = new Chat(pcName, userId, pcGuid);
        }
    }

    public void zipLogs() {
        //удаляем старые логи
        try {
            Runtime.getRuntime().exec("Forfiles -p " + "video\\logs" + " -s -m *.zip -d -" + 3 + " -c \"cmd /c del /q @path\"");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        //архивируем логи прошлой сессии
        SimpleDateFormat formater = new SimpleDateFormat("dd.MM.yyyy_HH.mm");
        String fName = formater.format(new Date());
        ZippingVisitor.zipWalking(
                new File("video\\logs\\latest").toPath(),
                new File("video\\logs\\" + fName + ".zip").toPath()
        );
    }

    public void initFace() {
        int inset = 4;
        // Создание полей
        // --имя ПК
        pcNameLabel = new JLabel("Введите уникальное имя ПК");
        if (Objects.equals(pcName, "")) {
            try {
                pcName = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException err) {
                err.printStackTrace();
            }
        }
        pcNameField = new JTextField(pcName, 20);
        // --имя ПК на сайте МТС
        mtsNameLabel = new JLabel("Введите имя ПК на МТС Fog Play");
        mtsNameField = new JTextField(mtsName, 20);
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
                    "Не забудьте включить папку video в исключения ПО," +
                            "\nсохраняющего дефолтное состояние ПК (см. ссылку)");
    }

    private void onSavesCheckBox(boolean showMsg) {
        dstPathField.setEnabled(savesCheckBox.isSelected());
        durationField.setEnabled(savesCheckBox.isSelected());
        vldField.setEnabled(savesCheckBox.isSelected());
        if (savesCheckBox.isSelected() && showMsg)
            JOptionPane.showMessageDialog(null,
                    "Не забудьте включить выбранную папку в исключения ПО," +
                            "\nсохраняющего дефолтное состояние ПК");
    }

    private void onApproveNewSettings() {
        //проверки корректности ввода
        String msg = "";
        if (Objects.equals(pcNameField.getText(), "")) msg = "Уникальное имя ПК - обязательное поле";
        if (Objects.equals(userIdField.getText(), "")) msg = "Ваш id в телеграмм - обязательное поле";
        if (videoCheckBox.isSelected()) {
            if (Objects.equals(fpsField.getText(), "")) msg = "Частота кадров - обязательное поле";
            if (Objects.equals(durationField.getText(), "")) msg = "Длительность записи - обязательное поле";
            if (Objects.equals(vldField.getText(), "")) msg = "Срок хранения видео - обязательное поле";

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

            fw.write(jo.toJSONString());
            fw.close();
        } catch (Exception err) {
            err.printStackTrace();
            return;
        }

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
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        //защита от двойного запуска: занимаем порт
        try {
            lock = new ServerSocket(55555);
        } catch (Exception ex) {
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

        //запускаем пересылку данных и отслеживание мыши для захвата видео при движении
        if (app.listener != null && app.video) app.listener.start();
        if (app.asListener != null && app.saves) app.asListener.start();
        if (app.sender != null) {
            app.sender.setDaemon(true);
            app.sender.start();
        }

        //ловушка на перезагрузку\выключение ПК
        Runtime.getRuntime().addShutdownHook(new Thread(() -> app.onExit()));
    }

    public void onExit() {
        if (chat != null) chat.finish();
        if (listener != null) listener.finish();
        if (sender != null) sender.interrupt();
        if (asListener != null) asListener.finish();

        //перед выходом - отпускаем порт
        try {
            lock.close();
        } catch (IOException ex) {
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
        } catch (Exception err) {
            err.printStackTrace();
        }
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
}