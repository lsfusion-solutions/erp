package lsfusion.erp.integration.wms.ttl;

import lsfusion.erp.utils.sql.ExportMSSQLAction;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.ArrayList;

public class ExportStoresTTLAction extends ExportMSSQLAction {
    public ExportStoresTTLAction(ScriptingLogicsModule LM) {
        super(LM, "Outlet", "s", new ArrayList<>(), "connectionStringTTL", false, false);
    }
}
