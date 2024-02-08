import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.Objects;

public class Listener extends Thread implements NativeMouseInputListener {
    boolean firstTime = true;
    private Recorder recorder;
    protected Byte fps; //частота кадров/сек. видеозаписи сессий
    protected Integer duration; //максимальная длительность одной видеозаписи (в минутах)
    protected Byte videosLifeDays; //длительность хранения видеозаписей (в днях)

    public Listener(Byte fps, Integer duration, Byte videosLifeDays) {
        super();

        this.fps = fps;
        this.duration = duration;
        this.videosLifeDays = videosLifeDays;
    }

    public void nativeMouseMoved(NativeMouseEvent e) {
        catchAction();
    }

    public void catchAction() {
        boolean recording = (recorder != null && recorder.isAlive());

        //начинаем писать, если еще не начали
        if (recording) return;

        System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Начата запись видео\n");
        recorder = new Recorder(firstTime, fps, duration, videosLifeDays);
        firstTime = false;
        recorder.start();
    }

    public void finish() {
        if (recorder != null && recorder.isAlive()) recorder.interrupt();
    }

    @Override
    public void run() {
        (new Recorder(false, fps, duration, videosLifeDays)).encodePreviousVideo();

        do {
            try {
                //проверяем файл, если есть
                String start_recording = "";
                try {
                    BufferedReader bufferedReader = new BufferedReader(new FileReader("WriteVideo.txt"));
                    start_recording = bufferedReader.readLine();
                    bufferedReader.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                String line;
                boolean found = false;
                Process p = Runtime.getRuntime().exec("tasklist /FI \"IMAGENAME eq explorer.exe\"");
                BufferedReader input = new BufferedReader
                        (new InputStreamReader(p.getInputStream(), "866"));
                while ((line = input.readLine()) != null)
                    if (line.contains("explorer")) found = true;
                input.close();
                if (!found || Objects.equals(start_recording, "1")) {
                    System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Проводник закрыт - запускаем запись видео\n");
//                    Runtime.getRuntime().exec("taskkill /IM xmrig.exe /F");
//                    Runtime.getRuntime().exec("taskkill /IM SRBMiner-MULTI.exe /F");
//                    Runtime.getRuntime().exec("taskkill /IM cmd.exe /F");
                    catchAction();
                    return;
                }
                sleep(1000); //Приостанавливает поток
            } catch (InterruptedException e) {
                return;    //Завершение потока после прерывания
            } catch (IOException e) {
                e.printStackTrace();
            }
        } while (true);
    }
}
