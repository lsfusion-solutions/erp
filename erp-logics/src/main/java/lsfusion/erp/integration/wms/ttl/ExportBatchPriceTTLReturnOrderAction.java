package lsfusion.erp.integration.wms.ttl;

import lsfusion.erp.utils.sql.ExportMSSQLAction;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.ArrayList;

public class ExportBatchPriceTTLReturnOrderAction extends ExportMSSQLAction {
    public ExportBatchPriceTTLReturnOrderAction(ScriptingLogicsModule LM) {
        super(LM, "Price2PartReturnOrder", "i", new ArrayList<>(), "connectionStringTTL", "Price2Part", false, false);
    }
}
