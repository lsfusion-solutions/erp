package lsfusion.erp.integration;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.logics.scripted.ScriptingModuleErrorLog;

import java.sql.SQLException;

public class DefaultExportActionProperty extends DefaultIntegrationActionProperty {

    public DefaultExportActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public DefaultExportActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) throws ScriptingModuleErrorLog.SemanticError {
        super(LM, valueClass);
    }
    
    public DefaultExportActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }
    
    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
    }

    protected String formatString(Object value, int length) {
        return value == null ? "" : ((String) value).trim().substring(0, Math.min(((String) value).trim().length(), length));
    }
}