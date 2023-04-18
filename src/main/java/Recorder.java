import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.function.Predicate;

public class Recorder extends Thread {
    String inPath = "video\\latest";
    String outFNameStorage = "video\\previousRecordTime.txt";
    byte fps;
    int duration;
    byte videosLifeDays;
    boolean firstTime;
    static long screenshot_id = 1;

    public Recorder(boolean firstTime, Byte fps, Integer duration, Byte videosLifeDays) {
        this.firstTime = firstTime;
        this.fps = fps;
        this.duration = duration * 60;
        this.videosLifeDays = videosLifeDays;
    }

    public void encodePreviousVideo() {
        //определяем имя видеозаписи последней сессии
        String fName = "";
        try {
            FileReader fr = new FileReader(outFNameStorage);
            BufferedReader buffReader = new BufferedReader(fr);

            if (buffReader.ready()) fName = buffReader.readLine();

            fr.close();
            buffReader.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            Runtime.getRuntime().exec("Forfiles -p video -s -m *.mp4 -d -" + videosLifeDays + " -c \"cmd /c del /q @path\"");
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        //собираем видео из скриншотов последней сессии с помощью ffmpeg
        if (!Objects.equals(fName, "")){
            String commands = "ffmpeg\\bin\\ffmpeg.exe -start_number 1 -framerate 1/" + fps + " -i \"" + inPath + "\\screenshot%d.jpg\" " +
                    "-c:v libx264 -preset ultrafast -crf 23 " +
                    "-pix_fmt yuv420p -r " + fps + " " +
                    "-hide_banner -y -v error -stats " +
                    "video\\" + fName + ".mp4";

            System.out.println(commands);
            try {
                Runtime.getRuntime().exec(commands);
                //очищаем дату и время начала записи для пользователя
                FileWriter fw = new FileWriter(outFNameStorage);
                fw.write("");
                fw.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void run() {
        //при первой записи в рамках сессии
        if (firstTime) {
            //очищаем скриншоты с предыдущей сессии
            try {
                Arrays.stream(Objects.requireNonNull((new File(inPath)).listFiles()))
                        .filter(Predicate.not(File::isDirectory))
                        .forEach(File::delete);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            //пишем дату и время начала записи для пользователя
            try {
                FileWriter fw = new FileWriter(outFNameStorage);
                SimpleDateFormat formater = new SimpleDateFormat("dd.MM.yyyy_HH.mm");

                fw.write(formater.format(new Date()));
                fw.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        //клепаем новые скриншоты
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();

        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.3f);
        try {
            long i = 1;
            do {
                try {
                    BufferedImage image = new Robot().createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
                    File compressedImageFile = new File(inPath + "\\screenshot" + screenshot_id + ".jpg");
                    OutputStream os =new FileOutputStream(compressedImageFile);
                    ImageOutputStream ios = ImageIO.createImageOutputStream(os);
                    writer.setOutput(ios);
                    writer.write(null, new IIOImage(image, null, null), param);
                    //ImageIO.write(image, "png", new File(inPath + "\\screenshot" + screenshot_id + ".png"));
                    sleep(1000 / fps);
                } catch (InterruptedException ex) {
                    return; //Завершение потока после прерывания
                }
                i++;
                screenshot_id++;
            } while (i <= (long) duration * fps);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
