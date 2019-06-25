package lsfusion.erp.integration;

import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.SQLException;

public class DefaultExportAction extends DefaultIntegrationActionProperty {

    public DefaultExportAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public DefaultExportAction(ScriptingLogicsModule LM, ValueClass valueClass) {
        super(LM, valueClass);
    }
    
    public DefaultExportAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }
    
    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
    }

    protected String formatString(Object value, int length) {
        return value == null ? "" : ((String) value).trim().substring(0, Math.min(((String) value).trim().length(), length));
    }
}