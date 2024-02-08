import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import org.json.simple.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;

import static java.lang.Thread.sleep;

public class Chat extends JFrame implements NativeKeyListener {
    private boolean ctrlPressed = false;
    private boolean shiftPressed = false;
    private boolean f1Pressed = false;

    protected String pcName; //имя отслеживаемого ПК
    protected String userId; //id пользователя телеграмма
    protected String pcGuid;

    protected JTextArea historyBox;
    protected JScrollPane historyScrollPane;
    protected JTextArea messageBox;
    protected JButton sendButton;

    protected Thread listener;

    public Chat(String pcName, String userId, String pcGuid) {
        super("Чат с владельцем ПК");
        //заполнение настроек
        this.pcName = pcName;
        this.userId = userId;
        this.pcGuid = pcGuid;

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
        if (!GlobalScreen.isNativeHookRegistered()) {
            try {
                GlobalScreen.registerNativeHook();
            } catch (NativeHookException e) {
                e.printStackTrace();
            }
        }
        GlobalScreen.addNativeKeyListener(this);

        listener = new Thread(() -> {
            do {
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
                            StringBuilder response = new StringBuilder();

                            while ((inputLine = in.readLine()) != null) {
                                inputLine = inputLine.replace("⚡\uD83D\uDDA5#" + pcName, "Вы");
                                response.append((inputLine.equals("")) ? "\n" : inputLine + "\n");
//                                setVisible(true);
                            }
                            in.close();

                            historyBox.setText(response.toString());
                            JScrollBar vsb = historyScrollPane.getVerticalScrollBar();
                            vsb.setValue( vsb.getMaximum() );

                            con.disconnect();
                        }
                    } catch (IOException err) {
                        err.printStackTrace();
                    }
                    sleep(3000);        //Приостанавливает поток
                } catch (InterruptedException e) {
                    return;    //Завершение потока после прерывания
                }
            } while (true);
        });
        listener.start();
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
            err.printStackTrace();
        }
        messageBox.setText("");
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
            //отсылаем заполненную библиотеку только первый раз
            byte[] input = getJSONString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
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
