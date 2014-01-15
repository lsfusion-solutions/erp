package lsfusion.erp.utils;

import com.google.common.base.Throwables;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.sql.ResultSet;
import java.sql.SQLException;

public class GetActiveTasksPostgreSQLActionProperty extends ScriptingActionProperty {

    public GetActiveTasksPostgreSQLActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    protected void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {

        try {

            getActiveTasksFromDatabase(context);

        } catch (SQLHandledException e) {
            throw Throwables.propagate(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }

    }


    private void getActiveTasksFromDatabase(ExecutionContext context) throws SQLException, SQLHandledException, ScriptingErrorLog.SemanticErrorException {

        DataSession session = context.getSession();

        Integer previousCount = (Integer) getLCP("previousCountActiveTask").read(session);
        previousCount = previousCount == null ? 0 : previousCount;
        
        for (int i = 0; i < previousCount; i++) {
            DataObject currentObject = new DataObject(i);
            getLCP("idActiveTask").change((Object) null, session, currentObject);
            getLCP("queryActiveTask").change((Object) null, session, currentObject);
            getLCP("userActiveTask").change((Object) null, session, currentObject);
            getLCP("addressUserActiveTask").change((Object) null, session, currentObject);
            getLCP("dateTimeActiveTask").change((Object) null, session, currentObject);
        }

        String originalQuery = String.format("SELECT * FROM pg_stat_activity WHERE datname='%s' AND state!='idle'", context.getBL().getDataBaseName());
        ResultSet rs = context.getSession().sql.getConnection().createStatement().executeQuery(originalQuery);

        int i = 0;
        while (rs.next()) {

            DataObject currentObject = new DataObject(i);

            String query = (String) rs.getObject("query");
            if (!query.equals(originalQuery)) {

                getLCP("idActiveTask").change(rs.getObject("pid"), session, currentObject);
                getLCP("queryActiveTask").change(query, session, currentObject);
                getLCP("userActiveTask").change(rs.getObject("usename"), session, currentObject);
                getLCP("addressUserActiveTask").change(String.valueOf(rs.getObject("client_addr")), session, currentObject);
                getLCP("dateTimeActiveTask").change(rs.getObject("query_start"), session, currentObject);
                i++;
            }
        }
        getLCP("previousCountActiveTask").change(i, session);
    }
}