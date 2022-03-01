package lsfusion.erp.integration.wms.ttl;

import lsfusion.erp.utils.sql.ExportMSSQLAction;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.ArrayList;

public class ExportSaleOrderDetailTTLAction extends ExportMSSQLAction {
    public ExportSaleOrderDetailTTLAction(ScriptingLogicsModule LM) {
        super(LM, "OrderGoods", "i", new ArrayList<>(), "connectionStringTTL", false, false);
    }
}
