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

    protected String formatString(Object value, int length) {
        return value == null ? "" : ((String) value).trim().substring(0, Math.min(((String) value).trim().length(), length));
    }
}