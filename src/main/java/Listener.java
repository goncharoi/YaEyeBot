import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;

import java.util.Date;

public class Listener extends Thread implements NativeMouseInputListener {
    long lastMoving = new Date().getTime();
    int millsToFreeze;
    boolean firstTime = true;
    private Recorder recorder;
    protected Byte fps; //частота кадров/сек. видеозаписи сессий
    protected Integer duration; //максимальная длительность одной видеозаписи (в минутах)
    protected Byte videosLifeDays; //длительность хранения видеозаписей (в днях)

    public Listener(Byte fps, Byte minutesToFreeze, Integer duration, Byte videosLifeDays) {
        this.millsToFreeze = minutesToFreeze * 60 * 1000;
        this.fps = fps;
        this.duration = duration;
        this.videosLifeDays = videosLifeDays;
    }

    public void nativeMouseMoved(NativeMouseEvent e) {
        catchAction();
    }

    public void catchAction(){
        long now = new Date().getTime();
        boolean wakedUp = (now - lastMoving < millsToFreeze);
        boolean recording = (recorder != null && recorder.isAlive());

        lastMoving = now;

        //начинаем писать после ... мсек простоя, если еще не начали
        if (wakedUp || recording) return;

        System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Начата запись видео\n");
        recorder = new Recorder(firstTime, fps, duration, videosLifeDays);
        firstTime = false;
        recorder.start();
    }

    public void finish() {
        if (recorder != null && recorder.isAlive()) recorder.interrupt();
        GlobalScreen.removeNativeMouseMotionListener(this);
    }

    @Override
    public void run() {
        (new Recorder(false, fps, duration, videosLifeDays)).encodePreviousVideo();

        try {
            GlobalScreen.registerNativeHook();
        } catch (NativeHookException ex) {
            ex.printStackTrace();
            return;
        }

        GlobalScreen.addNativeMouseMotionListener(this);
    }
}
