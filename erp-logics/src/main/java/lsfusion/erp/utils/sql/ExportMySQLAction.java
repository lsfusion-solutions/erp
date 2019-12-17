package lsfusion.erp.utils.sql;

import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

public abstract class ExportMySQLAction extends ExportSQLAction {

    public ExportMySQLAction(ScriptingLogicsModule LM, String idForm, String idGroupObject,
                             List<String> keyColumns, String connectionStringProperty, boolean truncate,
                             boolean noInsert, Integer batchSize) {
        super(LM, idForm, idGroupObject, keyColumns, connectionStringProperty, idForm, truncate, noInsert, batchSize);
    }

    public ExportMySQLAction(ScriptingLogicsModule LM, String idForm, String idGroupObject,
                             List<String> keyColumns, String connectionStringProperty, String table, boolean truncate,
                             boolean noInsert, Integer batchSize) {
        super(LM, idForm, idGroupObject, keyColumns, connectionStringProperty, table, truncate, noInsert, batchSize);
    }

    @Override
    public void init() throws ClassNotFoundException {
        Class.forName("com.mysql.jdbc.Driver");
    }

    @Override
    public String getTruncateStatement() {
        return "TRUNCATE TABLE `" + table + "`";
    }

    @Override
    public String getInsertStatement(String columns, String params) {
        return String.format("INSERT INTO `%s`(%s) VALUES (%s)", table, columns, params);
    }

    @Override
    public String getUpdateStatement(String set, String wheres, String columns, String params) {
        throw new UnsupportedOperationException("Update for mySQL is not supported yet");
    }

    public void setObject(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value == null)
            ps.setObject(index, null);
        else if (value instanceof Date)
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
//public class TestExportSQLAction extends ExportMSSQLAction {
//
//    public TestExportSQLAction(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
//        super(LM, "testtable4", "i", Arrays.asList("dt"), "connectionString", true);
//    }
//}