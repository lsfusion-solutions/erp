package lsfusion.erp.integration.wms.ttl;

import lsfusion.erp.utils.sql.ExportMSSQLAction;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.ArrayList;

public class ExportInvoiceDetailTTLAction extends ExportMSSQLAction {
    public ExportInvoiceDetailTTLAction(ScriptingLogicsModule LM) {
        super(LM, "ReceiptGoods", "i", new ArrayList<>(), "connectionStringTTL", false, false);
    }
}
