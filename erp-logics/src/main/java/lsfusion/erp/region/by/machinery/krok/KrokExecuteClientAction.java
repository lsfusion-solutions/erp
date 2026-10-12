package lsfusion.erp.region.by.machinery.krok;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.Map;

// Выполняет запрос к TitanKrokLib на клиенте. Пока идёт запрос (синхронный, до минуты), показывает окно с QR-кодом для плательщика,
// текстом прогресса, вводом КПСО вручную и кнопкой прерывания. Возвращает {ответ, ошибка}
public class KrokExecuteClientAction implements ClientAction {

    private static final int QR_SIZE = 320;

    private final String request;
    private final String libraryDir;

    public KrokExecuteClientAction(String request, String libraryDir) {
        this.request = request;
        this.libraryDir = libraryDir;
    }

    // без @Override: в ClientAction этот метод есть только в новых версиях платформы
    public boolean isUserInteraction() {
        return true;
    }

    @Override
    public Object dispatch(ClientActionDispatcher dispatcher) {
        final TitanKrokLib.TitanKrokDLL lib;
        try {
            lib = TitanKrokLib.getLib(libraryDir);
        } catch (Throwable t) {
            return new String[]{null, "Не удалось загрузить библиотеку TitanKrokLib: " + t.getMessage()};
        }

        final String[] result = new String[2];
        KrokDialog dialog = new KrokDialog(lib);
        TitanKrokLib.setListener(dialog);
        Thread worker = new Thread(() -> {
            try {
                result[0] = TitanKrokLib.execute(lib, request);
            } catch (Throwable t) {
                result[1] = t.getMessage() != null ? t.getMessage() : t.toString();
            } finally {
                dialog.finish();
            }
        }, "TitanKrokLib");
        worker.setDaemon(true);
        worker.start();

        dialog.setVisible(true); // модальное окно, закрывается по завершении запроса
        try {
            worker.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        TitanKrokLib.setListener(null);
        return result;
    }

    private static class KrokDialog extends JDialog implements TitanKrokLib.Listener {
        private final TitanKrokLib.TitanKrokDLL lib;
        private final JLabel qrLabel = new JLabel("", SwingConstants.CENTER);
        private final JLabel progressLabel = new JLabel("Выполняется запрос КРОК...", SwingConstants.CENTER);
        private final JTextField cncpField = new JTextField(6);
        private final JButton interruptButton = new JButton("Прервать");
        private volatile boolean finished;

        KrokDialog(TitanKrokLib.TitanKrokDLL lib) {
            super(getOwnerWindow(), "Оплата КРОК", ModalityType.APPLICATION_MODAL);
            this.lib = lib;

            qrLabel.setPreferredSize(new Dimension(QR_SIZE, QR_SIZE));
            progressLabel.setFont(progressLabel.getFont().deriveFont(Font.BOLD, 18f));

            JButton cncpButton = new JButton("Передать КПСО");
            cncpButton.addActionListener(e -> sendCncp());
            cncpField.addActionListener(e -> sendCncp());
            interruptButton.addActionListener(e -> interrupt());

            JPanel cncpPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            cncpPanel.add(new JLabel("КПСО:"));
            cncpPanel.add(cncpField);
            cncpPanel.add(cncpButton);
            cncpPanel.add(interruptButton);

            JPanel content = new JPanel(new BorderLayout(10, 10));
            content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            content.add(progressLabel, BorderLayout.NORTH);
            content.add(qrLabel, BorderLayout.CENTER);
            content.add(cncpPanel, BorderLayout.SOUTH);
            setContentPane(content);

            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowOpened(WindowEvent e) {
                    if (finished) // запрос мог завершиться раньше, чем окно открылось
                        dispose();
                }

                @Override
                public void windowClosing(WindowEvent e) {
                    interrupt();
                }
            });
            pack();
            setLocationRelativeTo(getOwner());
        }

        private static Window getOwnerWindow() {
            Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            return window != null ? window : JOptionPane.getRootFrame();
        }

        private void sendCncp() {
            String text = cncpField.getText().trim();
            int cncp;
            try {
                cncp = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                cncp = 0;
            }
            if (cncp < 1000 || cncp > 9999) {
                progressLabel.setText("КПСО должен быть числом от 1000 до 9999");
                return;
            }
            lib.setCncp(cncp);
            cncpField.setText("");
        }

        private void interrupt() {
            interruptButton.setEnabled(false);
            progressLabel.setText("Прерывание...");
            lib.interrupt();
        }

        void finish() {
            finished = true;
            SwingUtilities.invokeLater(this::dispose);
        }

        @Override
        public void showQr(String qr) {
            Icon icon = null;
            try {
                if (!qr.isEmpty()) {
                    Map<EncodeHintType, Object> hints = new HashMap<>();
                    hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
                    hints.put(EncodeHintType.MARGIN, 1);
                    icon = new ImageIcon(MatrixToImageWriter.toBufferedImage(new QRCodeWriter().encode(qr, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints)));
                }
            } catch (Exception e) {
                showProgress("Не удалось построить QR-код: " + e.getMessage());
            }
            Icon qrIcon = icon;
            SwingUtilities.invokeLater(() -> qrLabel.setIcon(qrIcon));
        }

        @Override
        public void showProgress(String text) {
            SwingUtilities.invokeLater(() -> progressLabel.setText("<html><center>" + escape(text) + "</center></html>"));
        }

        private static String escape(String text) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
        }
    }
}
