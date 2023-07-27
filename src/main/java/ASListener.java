import java.util.Date;
import java.util.TreeSet;

public class ASListener extends Thread
//        implements NativeMouseInputListener
{
    private AutoSaver autoSaver = null;
    private final String dstPath; //папка сохранений
    private final byte savesLifeDays; //сколько хранить сейвы прошлых сессий (дней)
    private final int offset; //сколько ждать перед началом записи сейвов (мин.)
    private TreeSet<GameInfo> games = null;

    public ASListener(String dstPath, Integer offset, Byte savesLifeDays) {
        super();

        this.dstPath = dstPath;
        this.offset = offset;
        this.savesLifeDays = savesLifeDays;
    }

    public void setGames(TreeSet<GameInfo> games) {
        this.games = games;
    }

//    public void nativeMouseMoved(NativeMouseEvent e) {
//        catchAction();
//    }

    public void catchAction() {
        boolean recording = (autoSaver != null && autoSaver.isAlive());

        //начинаем писать, если еще не начали
        if (recording || games == null) return;

        System.out.printf("%1$tF %1$tT %2$s", new Date(), ":: Начата запись сейвов\n");
        autoSaver = new AutoSaver(dstPath, games, savesLifeDays, offset);
        autoSaver.start();
    }

    public void finish() {
        if (autoSaver != null && autoSaver.isAlive()) autoSaver.interrupt();
//        GlobalScreen.removeNativeMouseMotionListener(this);
    }

    @Override
    public void run() {
        (new AutoSaver(dstPath, new TreeSet<>(), savesLifeDays, offset)).zipPreviousSaves();

        //на мышь не реагирует, чтобы лишнего не писать - только на завершение explorer
//        if (!GlobalScreen.isNativeHookRegistered()) {
//            try {
//                GlobalScreen.registerNativeHook();
//            } catch (NativeHookException ex) {
//                ex.printStackTrace();
//                return;
//            }
//        }
//
//        GlobalScreen.addNativeMouseMotionListener(this);
    }
}
