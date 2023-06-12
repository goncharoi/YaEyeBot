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

    protected JLabel pcNameLabel, userIdLabel, dbPathLabel, approveLabel, fpsLabel, mtfLabel1, mtfLabel2, durationLabel, vldLabel;
    protected JTextField pcNameField, userIdField, dbPathField, fpsField, mtfField, durationField, vldField;
    protected JButton dbPathButton, approveButton;
    protected JCheckBox videoCheckBox;
    protected JHyperlink goToBot, goToChat, toVideoDir;
    protected JLabel qrToBotLabel, qrToChatLabel;

    //настройки отслеживания
    protected String pcName = ""; //имя отслеживаемого ПК
    protected String userId = ""; //id пользователя телеграмма
    protected Boolean filterServices = true; //фильтровать процессы типа Services
    protected Byte timeout = 20; //отсылать данные раз в ... сек.
    protected String dbPathDefault = System.getenv("USERPROFILE") +
            "\\AppData\\Local\\rds-wrtc\\UserGame.db"; //путь к библиотеке игр МТС-лаунчера по умолчанию
    protected String dbPath = ""; //путь к библиотеке игр МТС-лаунчера
    protected String pcGuid = "";
    protected Boolean video = false; //видеозапись сессий включена
    protected Byte fps = 1; //частота кадров/сек. видеозаписи сессий
    protected Byte minutesToFreeze = 3; //максимальная пауза в движении, после которой может начаться новая видеозапись (в минутах)
    protected Integer duration = 15; //максимальная длительность одной видеозаписи (в минутах)
    protected Byte videosLifeDays = 1; //длительность хранения видеозаписей (в днях)

    public Main() throws IOException {
        super("Отслеживание ПК v2.1.3");

        //перенаправляем вывод в файлы
        System.setOut(new PrintStream(new FileOutputStream("video\\out.log")));
        System.setErr(new PrintStream(new FileOutputStream("video\\err.log")));

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
            listener = new Listener(fps, minutesToFreeze, duration, videosLifeDays);
            sender = new Sender(listener, video, pcName, userId, filterServices, timeout, dbPath, pcGuid);
        }
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
        // --максимальная пуаза
        mtfLabel1 = new JLabel("Минимальная пуаза в движении, после которой");
        mtfLabel2 = new JLabel("может начаться новая видеозапись (в минутах)");
        mtfField = new NumericTextField();
        mtfField.setInputVerifier(new NumVerifier(NumVerifier.fieldType.BYTE));
        mtfField.setColumns(2);
        mtfField.setText(minutesToFreeze.toString());
        // --частота кадров
        durationLabel = new JLabel("Максимальная длительность одной видеозаписи (в минутах)");
        durationField = new NumericTextField();
        durationField.setInputVerifier(new NumVerifier(NumVerifier.fieldType.INT));
        durationField.setColumns(3);
        durationField.setText(duration.toString());
        // --частота кадров
        vldLabel = new JLabel("Длительность хранения видеозаписей (в днях) ");
        vldField = new NumericTextField();
        vldField.setInputVerifier(new NumVerifier(NumVerifier.fieldType.BYTE));
        vldField.setColumns(2);
        vldField.setText(videosLifeDays.toString());
        //включение/отключение полей
        videoCheckBox.addActionListener(e -> onVideoCheckBox(true));
        onVideoCheckBox(false);

        // --ссылки
        goToBot = new JHyperlink("Телеграм-бот для отчетов программы (так же выдает id пользователя)",
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
        constraints.insets = new Insets(inset, inset, 0, inset);
        videoPanel.add(mtfLabel1, constraints);
        constraints.gridx = 1;
        videoPanel.add(mtfField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.insets = new Insets(0, inset, inset, inset);
        videoPanel.add(mtfLabel2, constraints);

        constraints.gridy = 4;
        constraints.insets = new Insets(inset, inset, inset, inset);
        videoPanel.add(durationLabel, constraints);
        constraints.gridx = 1;
        videoPanel.add(durationField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 5;
        videoPanel.add(vldLabel, constraints);
        constraints.gridx = 1;
        videoPanel.add(vldField, constraints);
        videoPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Настройки видеозаписи сессий"));

        // --кнопка подтверждения
        JPanel approve = new JPanel(new GridBagLayout());
        constraints.gridx = 0;
        constraints.gridy = 0;
        approve.add(approveLabel, constraints);
        constraints.gridy = 1;
        approve.add(approveButton, constraints);

        //собираем вместе
        JPanel bodyPanel = new JPanel(new GridBagLayout());
        constraints.gridx = 0;
        constraints.gridy = 0;
        bodyPanel.add(optionsPanel, constraints);
        constraints.gridy = 1;
        bodyPanel.add(videoPanel, constraints);
        constraints.gridy = 2;
        bodyPanel.add(goToBot, constraints);
        constraints.gridy = 3;
        bodyPanel.add(qrToBotLabel, constraints);
        constraints.gridy = 4;
        bodyPanel.add(goToChat, constraints);
        constraints.gridy = 5;
        bodyPanel.add(qrToChatLabel, constraints);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(bodyPanel, BorderLayout.NORTH);
        mainPanel.add(approve, BorderLayout.SOUTH);
        setContentPane(mainPanel);
    }

    private void onVideoCheckBox(boolean showMsg) {
        fpsField.setEnabled(videoCheckBox.isSelected());
        mtfField.setEnabled(videoCheckBox.isSelected());
        durationField.setEnabled(videoCheckBox.isSelected());
        vldField.setEnabled(videoCheckBox.isSelected());
        if (videoCheckBox.isSelected() && showMsg)
            JOptionPane.showMessageDialog(null,
                    "Не забудьте включить папку video в исключения ПО,"+
                            "\nсохраняющего дефолтное состояние ПК (см. ссылку)");
    }

    private void onApproveNewSettings() {
        //проверки корректности ввода
        String msg = "";
        if (Objects.equals(pcNameField.getText(), "")) msg = "Уникальное имя ПК - обязательное поле";
        if (Objects.equals(userIdField.getText(), "")) msg = "Ваш id в телеграмм - обязательное поле";
        if (videoCheckBox.isSelected()){
            if (Objects.equals(fpsField.getText(), "")) msg = "Частота кадров - обязательное поле";
            if (Objects.equals(mtfField.getText(), "")) msg = "Задержка - обязательное поле";
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
            jo.put("userId", userIdField.getText());
            jo.put("filterServices", (filterServices) ? "1" : "0");
            jo.put("timeout", timeout.toString());
            jo.put("dbPath", dbPathField.getText());
            jo.put("video", (videoCheckBox.isSelected()) ? "1" : "0");
            jo.put("fps", fpsField.getText());
            jo.put("minutesToFreeze", mtfField.getText());
            jo.put("duration", durationField.getText());
            jo.put("videosLifeDays", vldField.getText());
            //генерация pcGuid если он пустой
            jo.put("pcGuid", (Objects.equals(pcGuid, "")) ? UUID.randomUUID().toString() : pcGuid);

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
        app.setSize(700, 800);
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
        if (app.listener != null && app.sender != null) {
            if (app.video) app.listener.start();

            app.sender.setDaemon(true);
            app.sender.start();
        }

        //ловушка на перезагрузку\выключение ПК
        Runtime.getRuntime().addShutdownHook(new Thread(() -> app.onExit()));
    }

    public void onExit() {
        if (app.listener != null && app.sender != null) {
            app.listener.finish();
            app.sender.interrupt();
            //перед выходом - отпускаем порт
            try {
                lock.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
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
            userId = (jsonObject.get("userId") != null) ? jsonObject.get("userId").toString() : "";
            filterServices = jsonObject.get("filterServices") == null &&
                    Objects.equals(jsonObject.get("filterServices").toString(), "1");
            timeout = (jsonObject.get("timeout") != null)
                    ? Byte.valueOf(jsonObject.get("timeout").toString())
                    : 20;
            dbPath = (jsonObject.get("dbPath") != null) ? jsonObject.get("dbPath").toString() : "";
            pcGuid = (jsonObject.get("pcGuid") != null) ? jsonObject.get("pcGuid").toString() : "";

            //настройки видеозаписи сессий
            video = (jsonObject.get("video") != null) &&
                    Objects.equals(jsonObject.get("video").toString(), "1");
            fps = (jsonObject.get("fps") != null)
                    ? Byte.valueOf(jsonObject.get("fps").toString())
                    : 1;
            minutesToFreeze = (jsonObject.get("minutesToFreeze") != null)
                    ? Byte.valueOf(jsonObject.get("minutesToFreeze").toString())
                    : 3;
            duration = (jsonObject.get("duration") != null)
                    ? Integer.parseInt(jsonObject.get("duration").toString())
                    : 5;
            videosLifeDays = (jsonObject.get("videosLifeDays") != null)
                    ? Byte.valueOf(jsonObject.get("videosLifeDays").toString())
                    : 1;
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    private static class NumVerifier extends InputVerifier {
        public enum fieldType {
            INT, BYTE, LONG
        }

        private fieldType ft;
        public NumVerifier(fieldType ft) {
            this.ft = ft;
        }
        public boolean verify(JComponent input) {
            try {
                switch (ft) {
                    case INT:
                        Integer.parseInt(((JTextField) input).getText());
                        break;
                    case BYTE:
                        Byte.parseByte(((JTextField) input).getText());
                        break;
                    case LONG:
                        Long.parseLong(((JTextField) input).getText());
                        break;
                    default:
                        break;
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