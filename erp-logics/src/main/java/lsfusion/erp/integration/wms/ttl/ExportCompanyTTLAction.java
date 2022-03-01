package lsfusion.erp.integration.wms.ttl;

import lsfusion.erp.utils.sql.ExportMSSQLAction;
import lsfusion.server.language.ScriptingLogicsModule;

import java.util.ArrayList;

public class ExportCompanyTTLAction extends ExportMSSQLAction {
    public ExportCompanyTTLAction(ScriptingLogicsModule LM) {
        super(LM, "Client", "l", new ArrayList<>(), "connectionStringTTL", false, false);
    }
}
