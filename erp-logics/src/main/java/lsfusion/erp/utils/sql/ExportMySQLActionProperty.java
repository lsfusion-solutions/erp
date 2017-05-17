package lsfusion.erp.utils.sql;

import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.util.List;

public abstract class ExportMySQLActionProperty extends ExportSQLActionProperty {

    public ExportMySQLActionProperty(ScriptingLogicsModule LM, String idForm, String idGroupObject,
                                     List<String> keyColumns, String connectionStringProperty, boolean truncate, boolean noInsert) {
        super(LM, idForm, idGroupObject, keyColumns, connectionStringProperty, idForm, truncate, noInsert);
    }

    public ExportMySQLActionProperty(ScriptingLogicsModule LM, String idForm, String idGroupObject,
                                     List<String> keyColumns, String connectionStringProperty, String table, boolean truncate, boolean noInsert) {
        super(LM, idForm, idGroupObject, keyColumns, connectionStringProperty, table, truncate, noInsert);
    }

    @Override
    public void init() throws ClassNotFoundException {
        Class.forName("com.mysql.jdbc.Driver");
    }

    @Override
    public String getUpdateStatement(String set, String wheres, String columns, String params) {
        throw new UnsupportedOperationException("Update for mySQL is not supported yet");
    }
}

//example of implementation

//import lsfusion.server.logics.scripted.ScriptingErrorLog;
//import lsfusion.server.logics.scripted.ScriptingLogicsModule;
//import java.util.Arrays;
//public class TestExportSQLActionProperty extends ExportMSSQLActionProperty {
//
//    public TestExportSQLActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
//        super(LM, "testtable4", "i", Arrays.asList("dt"), "connectionString", true);
//    }
//}