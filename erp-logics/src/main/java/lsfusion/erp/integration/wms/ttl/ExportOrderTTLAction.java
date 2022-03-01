package lsfusion.erp.integration.wms.ttl;

import lsfusion.erp.utils.sql.ExportMSSQLAction;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.ArrayList;

public class ExportOrderTTLAction extends ExportMSSQLAction {
    public ExportOrderTTLAction(ScriptingLogicsModule LM) {
        super(LM, "ReceiptOrder", "o", new ArrayList<>(), "connectionStringTTL", "Receipt", false, false);
    }
}
