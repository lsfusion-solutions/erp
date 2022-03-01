package lsfusion.erp.integration.wms.ttl;

import lsfusion.erp.utils.sql.ExportMSSQLAction;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.ArrayList;

public class ExportItemsTTLAction extends ExportMSSQLAction {
    public ExportItemsTTLAction(ScriptingLogicsModule LM) {
        super(LM, "Goods", "i", new ArrayList<>(), "connectionStringTTL", false, false);
    }
}
