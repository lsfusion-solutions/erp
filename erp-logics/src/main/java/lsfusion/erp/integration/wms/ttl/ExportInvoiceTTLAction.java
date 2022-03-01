package lsfusion.erp.integration.wms.ttl;

import lsfusion.erp.utils.sql.ExportMSSQLAction;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.ArrayList;

public class ExportInvoiceTTLAction extends ExportMSSQLAction {
    public ExportInvoiceTTLAction(ScriptingLogicsModule LM) {
        super(LM, "Receipt", "i", new ArrayList<>(), "connectionStringTTL", false, false);
    }
}
