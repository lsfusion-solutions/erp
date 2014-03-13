package lsfusion.erp.integration.universal;


import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.io.IOException;
import java.util.Map;

public class ImportPreviewClientAction implements ClientAction {

    Map<String, Object[]> overridingArticles;

    public ImportPreviewClientAction(Map<String, Object[]> overridingArticles) {
        this.overridingArticles = overridingArticles;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        ImportPreviewDialog dialog = new ImportPreviewDialog(overridingArticles);
        return dialog.execute();

    }
}
