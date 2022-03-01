package lsfusion.erp.integration.wms.ttl;

import lsfusion.erp.utils.sql.ExportMSSQLAction;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.Arrays;

public class ExportSaleOrderTTNTTLAction extends ExportMSSQLAction {
    public ExportSaleOrderTTNTTLAction(ScriptingLogicsModule LM) {
        super(LM, "OrderTTN", "i", Arrays.asList("Id_Order"), "connectionStringTTL", false, false);
    }
}
