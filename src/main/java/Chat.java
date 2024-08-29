import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import org.json.simple.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;

import static java.lang.Thread.sleep;

public class Chat extends JFrame implements NativeKeyListener {
    private final static int defaultPause = 60000; //по умолчанию запрашиваем историю чата раз в минуту
    private boolean ctrlPressed = false;
    private boolean shiftPressed = false;
    private boolean f1Pressed = false;

    protected String pcName; //имя отслеживаемого ПК
    protected String userId; //id пользователя телеграмма
    protected String pcGuid;

    protected boolean chatFlag;
    protected int pause; //пауза между запросами истории чата
    protected boolean gameMode;
    protected boolean autoUpdateMTS;

    protected JTextArea historyBox;
    protected JScrollPane historyScrollPane;
    protected JTextArea messageBox;
    protected JButton sendButton;

    protected Thread listener;

    public Chat(String pcName, String userId, String pcGuid, boolean chatFlag, boolean autoUpdateMTS) {
        super("Чат с владельцем ПК");
        //заполнение настроек
        this.pcName = pcName;
        this.userId = userId;
        this.pcGuid = pcGuid;
        this.chatFlag = chatFlag;
        this.autoUpdateMTS = autoUpdateMTS;
        this.pause = defaultPause;
        this.gameMode = false;

        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(430, 500);
        setResizable(false);

        sendButton = new JButton("Отправить (Ctrl+Enter)");
        sendButton.addActionListener(e -> sendMessage());

        historyBox = new JTextArea(20, 35);
        historyBox.setEditable(false); //вручную вводить нельзя
        historyBox.setLineWrap(true);
        historyBox.setWrapStyleWord(true);
        historyScrollPane = new JScrollPane(historyBox);

        messageBox = new JTextArea(3, 35);
        messageBox.setEnabled(true); //вручную вводить можно
        messageBox.setLineWrap(true);
        messageBox.setWrapStyleWord(true);
        messageBox.setFocusable(true);

        messageBox.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && e.isControlDown()) sendMessage();
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }
        });

        // Создание панелей с полями
        JPanel mainPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        mainPanel.add(historyScrollPane, BorderLayout.NORTH);
        mainPanel.add(new JScrollPane(messageBox), BorderLayout.CENTER);
        mainPanel.add(sendButton, BorderLayout.SOUTH);
        setContentPane(mainPanel);

        setVisible(false);
        setAlwaysOnTop(true);
        //отслеживание нажатий запускаем, если использование чата разрешено на ПК
        if (!GlobalScreen.isNativeHookRegistered() && this.chatFlag) {
            try {
                GlobalScreen.registerNativeHook();
            } catch (NativeHookException e) {
                System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
                e.printStackTrace();
            }
        }
        GlobalScreen.addNativeKeyListener(this);

        listener = new Thread(() -> {
            int prevBotNeedUpdateCount = 0;
            int prevMTSNeedUpdateCount = 0;
            int prevAlarmsCount = 0;
            //на всякий случай подождем обнуления чата на сервере
            try {
                sleep(10000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            //погнали обновлять историю чата
            do {
                int lastBotNeedUpdateCount = 0;
                int lastAlarmsCount = 0;
                String prevLen = "";
                String newLen = "";
                String lastAlarm = "";
                StringBuilder response = new StringBuilder();

                try {
                    try {
                        URL url = new URL("https://bbb.daobots.ru/dialog.php?pcGuid=" + pcGuid);
                        HttpURLConnection con = (HttpURLConnection) url.openConnection();

                        con.setRequestMethod("GET");
                        con.setRequestProperty("User-Agent", "Mozilla/5.0");
                        int responseCode = con.getResponseCode();
                        if (responseCode == HttpURLConnection.HTTP_OK) { // success
                            BufferedReader in = new BufferedReader(
                                    new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
                            String inputLine;

                            //первая строка - всегда содержит длину дистрибутива
                            newLen = in.readLine();
                            while ((inputLine = in.readLine()) != null) {
                                //пришел сигнал об обновлении бота - считаем сколько таких сигналов в истории чата
                                if (inputLine.equals("BotNeedUpdate")) {
                                    lastBotNeedUpdateCount++;
                                } else {
                                    if (inputLine.startsWith("#alarm")) {
                                        //пришло оповещение от админа - считаем сколько таких оповещений в истории чата
                                        //и запоминаем последнее
                                        lastAlarmsCount++;
                                        lastAlarm = inputLine;
                                    } else if (inputLine.startsWith("#restart")) {
                                        //пришел сигнал на перезагрузку - проверяем pcGuid и перезагружаем комп
                                        restartPC(inputLine);
                                    } else {
                                        inputLine = inputLine.replace("⚡\uD83D\uDDA5#" + pcName, "Вы");
                                        response.append((inputLine.equals("")) ? "\n" : inputLine + "\n");
                                    }
                                }
                            }
                            in.close();
                        }
                        con.disconnect();
                    } catch (IOException err) {
                        System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
                        err.printStackTrace();
                    }

                    System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Было/стало: оповещений (" + prevAlarmsCount + "/" + lastAlarmsCount +
                            "), сигналов об обновлении бота (" + prevBotNeedUpdateCount + "/" + lastBotNeedUpdateCount + ")\n");
                    //если сигналов об обновлении бота больше, чем при прошлой проверке истории, значит пришел новый -
                    //ставим метку, о необходимости обновления бота
                    if (lastBotNeedUpdateCount > prevBotNeedUpdateCount) {
                        prevBotNeedUpdateCount = lastBotNeedUpdateCount;
                        try {
                            FileWriter fw = new FileWriter(Main.outNUStorage);
                            fw.write("1");
                            fw.close();
                        } catch (Exception ex) {
                            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
                            ex.printStackTrace();
                        }
                    }
                    //если оповещений от админа больше, чем при прошлой проверке истории, значит пришло новое -
                    //показываем его
                    if (lastAlarmsCount > prevAlarmsCount) {
                        prevAlarmsCount = lastAlarmsCount;
                        showAlarmMessage(lastAlarm);
                    }

                    historyBox.setText(response.toString());
                    JScrollBar vsb = historyScrollPane.getVerticalScrollBar();
                    vsb.setValue(vsb.getMaximum());

                    //если ПК не в игре и включено обновление МТС, проверяем обновку МТС раз в минуту
                    if (!gameMode && autoUpdateMTS) {
                        try {
                            if (Objects.equals(newLen, "") || newLen == null) {
                                URL url = new URL("https://fogplay.mts.ru/download/MTS_Remoteplay-install-win64.exe");
                                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                                con.setRequestMethod("GET");
                                con.setRequestProperty("User-Agent", "Mozilla/5.0");
                                if (con.getResponseCode() == HttpURLConnection.HTTP_OK)  // success
                                    newLen = String.valueOf(con.getHeaderFields().get("Content-Length").get(0));
                                con.disconnect();
                                System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Новый размер дистрибутива определен через сайт МТС и = " + newLen + "\n");
                            } else
                                System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Новый размер дистрибутива определен через сервер бота и = " + newLen + "\n");

                            FileReader fr = new FileReader(Main.outMTSNUStorage);
                            BufferedReader buffReader = new BufferedReader(fr);
                            if (buffReader.ready())
                                prevLen = buffReader.readLine();
                            fr.close();
                            buffReader.close();

                            System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Старый размер дистрибутива = " + prevLen + "\n");
                            //если предыдущих замеров не было, просто инициализируем
                            if (Objects.equals(prevLen, "") ||
                                    Objects.equals(prevLen, "0") ||
                                    Objects.equals(prevLen, "1")
                            ) {
                                FileWriter fw = new FileWriter(Main.outMTSNUStorage);
                                fw.write(newLen);
                                fw.close();

                                prevLen = newLen;
                            }
                            //если изменился размер дистрибутива - запускаем обновление МТС
                            if (!Objects.equals(newLen, prevLen)) {
                                System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Запускаем обновление МТС\n");
                                java.util.List<String> command = new ArrayList<>();
                                command.add("powershell");
                                command.add("-ExecutionPolicy");
                                command.add("ByPass");
                                command.add("-file");
                                command.add("\"MTSUpdate.ps1\"");
                                ProcessBuilder pb = new ProcessBuilder(command);
                                pb.redirectErrorStream(true);
                                Process process = pb.start();
                                //ждем завершения процесса
                                process.waitFor();

                                FileWriter fw = new FileWriter(Main.outMTSNUStorage);
                                fw.write(newLen);
                                fw.close();
                            }
                        } catch (IOException err) {
                            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
                            err.printStackTrace();
                        }
                    }
                    sleep(pause);        //Приостанавливает поток
                } catch (InterruptedException e) {
                    return;    //Завершение потока после прерывания
                }
            } while (true);
        });
        listener.start();
    }

    public void gameModeOn() {
        gameMode = true;
        //если включена опция чата с игроком, ускоряем получение истории чата - обновляем её раз в 5 сек.,
        //иначе - слушаем только системные сообщения, которые получаем раз в минуту
        if (chatFlag)
            pause = 5000;
    }

    private void showAlarmMessage(String inputLine) {
        System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Показываем срочное оповещение от админа\n");
//формат оповещения должен быть строго: #alarm <guid ПК>:<текст сообщения>
        String[] parts = inputLine.replace("#alarm", "").trim().split(":", 2);
        System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Сообщение разбито на " + Arrays.stream(parts).count() + "частей, где гуид = " + parts[0] + "\n");
        if (Arrays.stream(parts).count() > 1 && Objects.equals(parts[0].trim(), pcGuid)) {
            final JDialog dialog = new JDialog(this, "ВНИМАНИЕ!", false);

            JTextArea textBox = new JTextArea(1, 35);
            textBox.setEditable(false);
            textBox.setLineWrap(true);
            textBox.setWrapStyleWord(true);
            JScrollPane textScrollPane = new JScrollPane(textBox);
            textBox.setFont(new Font("Dialog", Font.PLAIN, 20));
            textBox.setText("Это окно закроется само через 10 сек.\n\n" + parts[1].trim());
            JScrollBar vsb = textScrollPane.getVerticalScrollBar();
            vsb.setValue(vsb.getMinimum());
            dialog.add(textScrollPane);

            dialog.setSize(500, 200);
            dialog.setVisible(true);
            dialog.setAlwaysOnTop(true);
            Timer timer = new Timer(10000, e -> {
                dialog.setVisible(false);
                dialog.dispose();
            });
            timer.setRepeats(false);
            timer.start();

            dialog.setVisible(true); // if modal, application will pause here
        }
    }

    private void restartPC(String inputLine) throws IOException {
        System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Перезагружаем комп по команде\n");
//формат оповещения должен быть строго: #restart <guid ПК>
        String pcGuidFromMessage = inputLine.replace("#restart", "").trim();
        if (Objects.equals(pcGuidFromMessage, pcGuid))
            Utils.restartPC();
    }

    public void finish() {
        listener.interrupt();
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        NativeKeyListener.super.nativeKeyReleased(e);

        if (e.getKeyCode() == NativeKeyEvent.VC_F1) f1Pressed = false;
        if (e.getKeyCode() == NativeKeyEvent.VC_CONTROL) ctrlPressed = false;
        if (e.getKeyCode() == NativeKeyEvent.VC_SHIFT) shiftPressed = false;
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        NativeKeyListener.super.nativeKeyPressed(e);

        if (e.getKeyCode() == NativeKeyEvent.VC_F1) f1Pressed = true;
        if (e.getKeyCode() == NativeKeyEvent.VC_CONTROL) ctrlPressed = true;
        if (e.getKeyCode() == NativeKeyEvent.VC_SHIFT) shiftPressed = true;

        if (f1Pressed && ctrlPressed && shiftPressed) setVisible(!isVisible());
    }

    public void sendMessage() {
        if (Objects.equals(messageBox.getText(), "")) return;

        historyBox.append("Вы: " + messageBox.getText() + "\n----------\n\n");
        try {
            sendPOST();
        } catch (Exception err) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            err.printStackTrace();
        }
        messageBox.setText("");
    }

    protected void sendPOST() throws Exception {
        ConToBot conToBot = new ConToBot();
        HttpURLConnection con = conToBot.getCon();

        try (OutputStream os = con.getOutputStream()) {
            //отсылаем заполненную библиотеку только первый раз
            byte[] input = getJSONString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        } catch (Exception err) {
            System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
            err.printStackTrace();
        }

        conToBot.closeCon();
    }

    protected String getJSONString() {
        //сообщение
        JSONObject message = new JSONObject();
        //команда
        message.put("text", "/gamer_message");
        //параметры пакета
        message.put("pcName", pcName);
        message.put("pcGuid", pcGuid);
        message.put("userId", userId);
        message.put("gamerMessage", messageBox.getText());
        //пакет для сообщения
        JSONObject mainObj = new JSONObject();
        mainObj.put("message", message);

        String result = mainObj.toJSONString();

        System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Сформировано сообщение игрока на отправку\n");
        System.out.println(result);

        return result;
    }
}
