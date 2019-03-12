package lsfusion.erp.region.by.integration.edi;

import lsfusion.base.file.RawFileData;
import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class SignEDIClientAction implements ClientAction {

    List<RawFileData> files;
    String signerPath;
    String outputDir;
    String certificate;
    String password;

    public SignEDIClientAction(List<RawFileData> files, String signerPath, String outputDir, String certificate, String password) {
        this.files = files;
        this.signerPath = signerPath;
        this.outputDir = outputDir;
        this.certificate = certificate;
        this.password = password;
    }

    @Override
    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {
        try {
            return new SignEDIWorker(files, signerPath, outputDir, certificate, password).execute();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }
}