package lsfusion.erp.integration.universal;


import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import java.util.Map;
import java.util.Set;

public class ImportPreviewClientAction implements ClientAction {

    Map<String, Object[]> overridingArticles;
    Set<String> articleSet;
    
    public ImportPreviewClientAction(Map<String, Object[]> overridingArticles, Set<String> articleSet) {
        this.overridingArticles = overridingArticles;
        this.articleSet = articleSet;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) {

        ImportPreviewDialog dialog = new ImportPreviewDialog(overridingArticles, articleSet);
        return dialog.execute();

    }
}
