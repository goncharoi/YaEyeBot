import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;

public class Main extends JFrame {
    public static ServerSocket lock;

    public static Main app;
    private final TrayIcon iconTr;
    private final SystemTray sT = SystemTray.getSystemTray();
    public boolean chetTray = false; //переменная, чтобы был вывод сообщения в трее только при первом сворачивании
    private final Sender sender = new Sender();

    public Main() throws IOException {
        super("Отслеживание ПК");
        iconTr = new TrayIcon(ImageIO.read(new File("icon.png")), "Отслеживание ПК");
        iconTr.addActionListener(ev -> {
            setVisible(true);
            setState(JFrame.NORMAL);
            removeTr();
        });
        //обработчик мыши
        MouseListener mouS = new MouseListener() {
            public void mouseClicked(MouseEvent ev) {
            }

            public void mouseEntered(MouseEvent ev) {
            }

            public void mouseExited(MouseEvent ev) {
            }

            public void mousePressed(MouseEvent ev) {
            }

            public void mouseReleased(MouseEvent ev) {
            }
        };
        iconTr.addMouseListener(mouS);
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
        app.setSize(400, 100);
        app.setVisible(false);
        app.addTr();

        //обработчик основного окна - здесь необходимо перечислить все возможные действия - раз взялись обрабатывать, надо делать до конца :)
        app.addWindowListener(new WindowListener() {
            public void windowClosing(WindowEvent winEvent) {
                app.sender.interrupt();
                //перед выходом - отпускаем порт
                try {
                    lock.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

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

        app.sender.setDaemon(true);
        app.sender.start();
    }
}