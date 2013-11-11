package lsfusion.erp.integration;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.sql.SQLException;

public class DefaultExportActionProperty extends ScriptingActionProperty {

    public DefaultExportActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public DefaultExportActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, valueClass);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {
    }

    protected String trim(String input) {
        return input == null ? null : trim(input, input.trim().length());
    }
    
    protected String trim(String input, Integer length) {
        return input == null ? null : (length == null || length >= input.trim().length() ? input.trim() : input.trim().substring(0, length));
    }

    protected String formatString(Object value, int length) {
        return value == null ? "" : ((String) value).trim().substring(0, Math.min(((String) value).trim().length(), length));
    }
    
    protected String upper(String value) {
        return value == null ? null : value.toUpperCase(); 
    }
}