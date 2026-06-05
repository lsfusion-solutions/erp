package lsfusion.erp.integration;

import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.classes.ValueClass;

public class DefaultExportAction extends DefaultIntegrationAction {

    public DefaultExportAction(ScriptingLogicsModule LM) {
        super(LM);
    }
    
    public DefaultExportAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }
}