package lsfusion.erp.integration.wms.ttl;

import lsfusion.erp.utils.sql.ExportMSSQLAction;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.ArrayList;

public class ExportBatchPriceTTLAction extends ExportMSSQLAction {
    public ExportBatchPriceTTLAction(ScriptingLogicsModule LM) {
        super(LM, "Price2Part", "i", new ArrayList<>(), "connectionStringTTL", false, false);
    }
}
