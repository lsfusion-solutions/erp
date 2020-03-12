package lsfusion.erp.region.by.finance.evat;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

class EVATWorker {
   private Map<String, Map<Long, List<Object>>> files;
   public Map<String, Map<Long, String>> invoices;
   private String serviceUrl;
   private String path;
   private String exportPath;
   private String password;
   private String certNumber;
   private int certIndex;
   private boolean useActiveX;
   private int type;

   EVATWorker(final Map<String, Map<Long, List<Object>>> files, Map<String, Map<Long, String>> invoices,
              final String serviceUrl, final String path, final String exportPath, final String password,
              final String certNumber, final int certIndex, final boolean useActiveX, final int type) {
      this.files = files;
      this.invoices = invoices;
      this.serviceUrl = serviceUrl;
      this.path = path;
      this.exportPath = exportPath;
      this.password = password;
      this.certNumber = certNumber;
      this.certIndex = certIndex;
      this.useActiveX = useActiveX;
      this.type = type;
   }

   public List<List<Object>> execute() throws ExecutionException, InterruptedException {

      final JDialog dialog = new ProgressDialog();

      SwingWorker<List<List<Object>>, Void> mySwingWorker = new SwingWorker<List<List<Object>>, Void>(){
         @Override
         protected List<List<Object>> doInBackground() {
            switch (type) {
               case 0:
                  if(useActiveX)
                     return new EVATActiveXHandler().signAndSend(files, serviceUrl, path, exportPath, password, certNumber);
                  else
                     return new EVATHandler().signAndSend(files, serviceUrl, path, exportPath, password, certNumber, certIndex);
               case 1:
                  if(useActiveX)
                     return new EVATActiveXHandler().getStatus(invoices, serviceUrl, password, certNumber);
                  else
                     return new EVATHandler().getStatus(invoices, serviceUrl, password, certNumber, certIndex);
               default:
                  return null;
            }
         }

         @Override
         protected void done() {
            dialog.dispose();
         }
      };

      mySwingWorker.execute();

      if(!useActiveX)
         dialog.setVisible(true);

      return mySwingWorker.get();
   }

   private class ProgressDialog extends JDialog {

      ProgressDialog() {
         super((Frame) null, "Ожидайте", true);
         setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
         setLocationRelativeTo(null);
         setAlwaysOnTop(true);

         JPanel messagePanel = new JPanel();
         messagePanel.add(new JLabel("<html><center>Инициализация соединения с сервисом<br>" + serviceUrl + "</center></html>"));

         JProgressBar progressBar = new JProgressBar();
         progressBar.setIndeterminate(true);
         JPanel progressPanel = new JPanel();
         progressPanel.add(progressBar);

         Container contentPane = getContentPane();
         contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));
         contentPane.add(messagePanel);
         contentPane.add(progressPanel);

         pack();
         setMinimumSize(new Dimension(300, 100));
         setResizable(false);

         setFocusableWindowState(false);
      }
   }

}