package lsfusion.erp.utils;

import lsfusion.server.data.sql.SQLSession;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

public class EnsureJumpWorkDaysAction extends InternalAction {

    public EnsureJumpWorkDaysAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            SQLSession sql = context.getSession().sql;
            LP<?> lp = findProperty("isDayOff[Country,DATE]");
            Properties props = new Properties();
            props.put("dayoff.tablename", lp.property.mapTable.table.getName(sql.syntax));
            props.put("dayoff.fieldname", lp.property.field.getName(sql.syntax));
            context.getDbManager().getAdapter().ensureScript("jumpWorkdays.tsql", props);
        } catch (ScriptingErrorLog.SemanticErrorException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}