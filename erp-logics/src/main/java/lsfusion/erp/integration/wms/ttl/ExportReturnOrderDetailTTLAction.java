package lsfusion.erp.integration.wms.ttl;

import lsfusion.erp.utils.sql.ExportMSSQLAction;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.ArrayList;

public class ExportReturnOrderDetailTTLAction extends ExportMSSQLAction {
    public ExportReturnOrderDetailTTLAction(ScriptingLogicsModule LM) {
        super(LM, "ReceiptGoodsReturnOrder", "o", new ArrayList<>(), "connectionStringTTL", "ReceiptGoods", false, false);
    }
}
