import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.Objects;

public class Listener extends Thread{
    boolean firstTime = true;
    private Recorder recorder;
    protected Byte fps; //частота кадров/сек. видеозаписи сессий
    protected Integer duration; //максимальная длительность одной видеозаписи (в минутах)
    protected Byte videosLifeDays; //длительность хранения видеозаписей (в днях)
    protected boolean restartIfNotWorking; //длительность хранения видеозаписей (в днях)

    private final Chat chat;
    private  final  boolean videoFlag;

    public Listener(boolean videoFlag, Byte fps, Integer duration, Byte videosLifeDays, String pcName, String userId, String pcGuid, boolean chatFlag, boolean autoUpdateMTS, boolean restartIfNotWorking) {
        super();

        this.videoFlag = videoFlag;
        this.fps = fps;
        this.duration = duration;
        this.videosLifeDays = videosLifeDays;
        this.restartIfNotWorking = restartIfNotWorking;

        this.chat = new Chat(pcName, userId, pcGuid, chatFlag, autoUpdateMTS);
    }

    public void catchAction() {
        //ускоряем проверку истории чата
        chat.gameModeOn();

        //начинаем писать видео, если еще не начали
        boolean recording = (recorder != null && recorder.isAlive());
        if (recording || !videoFlag) return;

        System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Начата запись видео\n");
        recorder = new Recorder(firstTime, fps, duration, videosLifeDays);
        firstTime = false;
        recorder.start();
    }

    public void finish() {
        if (recorder != null && recorder.isAlive())
            recorder.interrupt();
        if (chat != null)
            chat.finish();
    }

    @Override
    public void run() {
        (new Recorder(false, fps, duration, videosLifeDays)).encodePreviousVideo();

        long time_gone = 0L;
        final long hour = 60 * 60; //час

        do {
            try {
                //проверяем файл, если есть
                String start_recording = "";
                try {
                    BufferedReader bufferedReader = new BufferedReader(new FileReader("WriteVideo.txt"));
                    start_recording = bufferedReader.readLine();
                    bufferedReader.close();
                } catch (Exception e) {
                    System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
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
                    System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Запускаем запись видео\n");
                    catchAction();
                    return;
                }

                //Если включена такая опция, перезагружаем комп каждый час в простое
                if (time_gone >= hour && restartIfNotWorking && Objects.equals(start_recording, "0")) {
                    System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Перезагружаем комп по расписанию\n");
                    Utils.restartPC();
                }

                time_gone++;
                sleep(1000); //Приостанавливает поток
            } catch (InterruptedException e) {
                return;    //Завершение потока после прерывания
            } catch (IOException e) {
                System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
                e.printStackTrace();
            }
        } while (true);
    }
}
