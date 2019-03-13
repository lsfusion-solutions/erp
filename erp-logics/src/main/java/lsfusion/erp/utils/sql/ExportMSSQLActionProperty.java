package lsfusion.erp.utils.sql;

import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

public abstract class ExportMSSQLActionProperty extends ExportSQLActionProperty {

    public ExportMSSQLActionProperty(ScriptingLogicsModule LM, String idForm, String idGroupObject,
                                     List<String> keyColumns, String connectionStringProperty, boolean truncate, boolean noInsert) {
        super(LM, idForm, idGroupObject, keyColumns, connectionStringProperty, truncate, noInsert);
    }

    public ExportMSSQLActionProperty(ScriptingLogicsModule LM, String idForm, String idGroupObject,
                                     List<String> keyColumns, String connectionStringProperty, String table, boolean truncate, boolean noInsert) {
        super(LM, idForm, idGroupObject, keyColumns, connectionStringProperty, table, truncate, noInsert);
    }

    @Override
    public void init() throws ClassNotFoundException {
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
    }

    @Override
    public String getTruncateStatement() {
        return "TRUNCATE TABLE [" + table + "]";
    }

    @Override
    public String getInsertStatement(String columns, String params) {
        return String.format("INSERT INTO [%s](%s) VALUES (%s)", table, columns, params);
    }

    @Override
    public String getUpdateStatement(String set, String wheres, String columns, String params) {
        return noInsert ?
                String.format("UPDATE [%s] SET %s WHERE %s", table, set, wheres) :
                String.format("UPDATE [%s] SET %s WHERE %s IF @@ROWCOUNT=0 INSERT INTO %s(%s) VALUES (%s)",
                        table, set, wheres, table, columns, params);
    }

    public void setObject(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value == null)
            value = "";
        if (value instanceof Date)
            ps.setDate(index, (Date) value);
        else if (value instanceof Timestamp)
            ps.setTimestamp(index, ((Timestamp) value));
        else if (value instanceof String)
            ps.setString(index, ((String) value).trim());
        else
            ps.setObject(index, value);
    }
}

//example of implementation

//import lsfusion.server.language.ScriptingErrorLog;
//import lsfusion.server.language.ScriptingLogicsModule;
//import java.util.Arrays;
//public class TestExportSQLActionProperty extends ExportMSSQLActionProperty {
//
//    public TestExportSQLActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
//        super(LM, "testtable4", "i", Arrays.asList("dt"), "connectionString", true);
//    }
//}