package lsfusion.erp.region.by.integration.edi;

import lsfusion.base.file.RawFileData;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.ExecutionException;

class SignEDIWorker {
   List<RawFileData> files;
   String signerPath;
   String outputDir;
   String certificate;
   String password;

   SignEDIWorker(final List<RawFileData> files, String signerPath, String outputDir, String certificate, String password) {
      this.files = files;
      this.signerPath = signerPath;
      this.outputDir = outputDir;
      this.certificate = certificate;
      this.password = password;
   }

   public List<Object> execute() throws ExecutionException, InterruptedException {

      SwingWorker<List<Object>, Void> mySwingWorker = new SwingWorker<List<Object>, Void>() {
         @Override
         protected List<Object> doInBackground() throws Exception {
            return new SignEDIHandler().sign(files, signerPath, outputDir, certificate, password);
         }
      };
      mySwingWorker.execute();
      return mySwingWorker.get();
   }
}