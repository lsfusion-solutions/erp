package lsfusion.erp.integration.wms.ttl;

import lsfusion.erp.utils.sql.ExportMSSQLAction;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.ArrayList;

public class ExportSaleOrderTTLAction extends ExportMSSQLAction {
    public ExportSaleOrderTTLAction(ScriptingLogicsModule LM) {
        super(LM, "Order", "i", new ArrayList<>(), "connectionStringTTL", false, false);
    }
}
