package lsfusion.erp.integration.wms.ttl;

import lsfusion.erp.utils.sql.ExportMSSQLAction;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.ArrayList;

public class ExportOrderDetailTTLAction extends ExportMSSQLAction {
    public ExportOrderDetailTTLAction(ScriptingLogicsModule LM) {
        super(LM, "ReceiptGoodsOrder", "o", new ArrayList<>(), "connectionStringTTL", "ReceiptGoods", false, false);
    }
}
