package lsfusion.erp.region.by.certificate.declaration;

import lsfusion.base.file.FileDialogUtils;
import lsfusion.base.file.RawFileData;
import lsfusion.interop.action.ClientActionDispatcher;
import lsfusion.interop.action.ExecuteClientAction;

import java.io.IOException;
import java.util.Map;

public class ExportFileClientAction extends ExecuteClientAction {

    // в качестве ключей - имена файлов, не пути к ним
    public Map<String, RawFileData> files;

    public ExportFileClientAction(Map<String, RawFileData> files) {
        this.files = files;
    }

    @Override
    public void execute(ClientActionDispatcher dispatcher) throws IOException {
        Map<String, RawFileData> chosenFiles = FileDialogUtils.showSaveFileDialog(files);
        for(Map.Entry<String, RawFileData> fileEntry : chosenFiles.entrySet()) {
            fileEntry.getValue().write(fileEntry.getKey());
        }
    }
}
