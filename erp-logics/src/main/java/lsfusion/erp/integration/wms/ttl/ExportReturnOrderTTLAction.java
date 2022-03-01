package lsfusion.erp.integration.wms.ttl;

import lsfusion.erp.utils.sql.ExportMSSQLAction;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.ArrayList;

public class ExportReturnOrderTTLAction extends ExportMSSQLAction {
    public ExportReturnOrderTTLAction(ScriptingLogicsModule LM) {
        super(LM, "ReturnOrder", "o", new ArrayList<>(), "connectionStringTTL", "Receipt", false, false);
    }
}
