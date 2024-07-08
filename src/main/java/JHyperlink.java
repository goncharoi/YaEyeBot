import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;

public class JHyperlink extends JLabel {
    private String url;
    private String html = "<html><a href=''>%s</a></html>";

    public JHyperlink(String text, String url) {
        this(text, url, null);
    }

    public void setURL(String url) {
        this.url = url;
    }

    public JHyperlink(String text, String url, String tooltip) {
        super(text);
        this.url = url;

        setForeground(Color.BLUE.darker());

        setToolTipText(tooltip);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                setText(String.format(html, text));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setText(text);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new URI(JHyperlink.this.url));
                } catch (IOException | URISyntaxException e1) {
                    try {
                        Desktop.getDesktop().open(new File(JHyperlink.this.url));
                    } catch (IOException ioe) {
                        System.err.printf("%1$tF %1$tT %2$s", new Date(), ":: Ошибка:");
                        ioe.printStackTrace();
                        JOptionPane.showMessageDialog(JHyperlink.this,
                                "Could not open the hyperlink. Error: " + e1.getMessage(),
                                "Error",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });
    }
}
