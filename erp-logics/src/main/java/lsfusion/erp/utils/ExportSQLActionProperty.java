package lsfusion.erp.utils;

import com.google.common.base.Throwables;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImList;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.form.entity.ObjectEntity;
import lsfusion.server.form.entity.PropertyDrawEntity;
import lsfusion.server.form.instance.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.sql.*;
import java.sql.Date;
import java.util.*;

public abstract class ExportSQLActionProperty extends ScriptingActionProperty {
    String idForm; //idForm = table
    String idGroupObject;
    List<String> keyColumns;
    String connectionStringProperty;
    boolean truncate;
    boolean noInsert;

    public ExportSQLActionProperty(ScriptingLogicsModule LM, String idForm, String idGroupObject,
                                   List<String> keyColumns, String connectionStringProperty, boolean truncate, boolean noInsert) {
        super(LM);
        this.idForm = idForm;
        this.idGroupObject = idGroupObject;
        this.keyColumns = keyColumns;
        this.connectionStringProperty = connectionStringProperty;
        this.truncate = truncate;
        this.noInsert = noInsert;
    }

    @Override
    public void executeCustom(ExecutionContext context) throws SQLException, SQLHandledException {

        Connection conn = null;
        PreparedStatement ps = null;
        try {

            if (idForm != null && idGroupObject != null && connectionStringProperty != null) {

                String url = (String) findProperty(connectionStringProperty).read(context);

                FormEntity formEntity = findForm(idForm);
                FormInstance formInstance = context.createFormInstance(formEntity, MapFact.<ObjectEntity, DataObject>EMPTY(),
                        context.getSession(), true, FormSessionScope.OLDSESSION, false, false, false, null);
                FormData formData = formInstance.getFormData(0);

                List<List<Object>> rows = new ArrayList<>();
                List<Map<String, Object>> keysRows = new ArrayList<>();
                List<String> columnNames = new ArrayList<>();
                boolean first = true;
                for (FormRow formRow : formData.rows) {
                    List<Object> row = new ArrayList<>();
                    Map<String, Object> keysRow = new HashMap<>();
                    ImList propertyDrawsList = formEntity.getPropertyDrawsList();
                    for (int i = 0; i < propertyDrawsList.size(); i++) {
                        PropertyDrawInstance instance = ((PropertyDrawEntity) propertyDrawsList.get(i)).getInstance(formInstance.instanceFactory);
                        if (instance.toDraw != null && instance.toDraw.getSID() != null && instance.toDraw.getSID().equals(idGroupObject)) {
                            Object value = formRow.values.get(instance);
                            row.add(value);
                            if (first)
                                columnNames.add(instance.getsID());
                            if (keyColumns.contains(instance.getsID()))
                                keysRow.put(instance.getsID(), value);
                        }
                    }
                    rows.add(row);
                    keysRows.add(keysRow);
                    first = false;
                }

                if (!rows.isEmpty()) {
                    Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
                    conn = DriverManager.getConnection(url);
                    conn.setAutoCommit(false);

                    int paramLength = columnNames.size();
                    String set = "";
                    String columns = "";
                    String params = "";
                    for (String columnName : columnNames) {
                        columns += (columns.isEmpty() ? "" : ",") + columnName;
                        set += (set.isEmpty() ? "" : ",") + columnName + "=?";
                        params += (params.isEmpty() ? "" : ",") + "?";
                    }
                    String wheres = "";
                    for (String key : keyColumns)
                        wheres += (wheres.isEmpty() ? "" : " AND ") + key + "=?";

                    if (truncate) {
                        Statement statement = null;
                        try {
                            statement = conn.createStatement();
                            statement.execute("TRUNCATE TABLE " + idForm);
                            conn.commit();
                        } finally {
                            if (statement != null)
                                statement.close();
                        }
                    }
                    if (wheres.isEmpty()) {
                        ps = conn.prepareStatement(String.format("INSERT INTO %s(%s) VALUES (%s)", idForm, columns, params));
                        for (List<Object> row : rows) {
                            for (int i = 0; i < paramLength; i++) {
                                setObject(ps, i + 1, row.get(i));
                            }
                            ps.addBatch();
                        }
                    } else {
                        ps = conn.prepareStatement(
                                noInsert ?
                                        String.format("UPDATE %s SET %s WHERE %s", idForm, set, wheres) :
                                        String.format("UPDATE %s SET %s WHERE %s IF @@ROWCOUNT=0 INSERT INTO %s(%s) VALUES (%s)",
                                                idForm, set, wheres, idForm, columns, params));

                        for (int k = 0; k < rows.size(); k++) {
                            List<Object> row = rows.get(k);
                            Map<String, Object> keysRow = keysRows.get(k);
                            int i;
                            for (i = 0; i < paramLength; i++) {
                                Object value = row.get(i);
                                setObject(ps, i + 1, value);
                                setObject(ps, i + paramLength + keyColumns.size() + 1, value);
                            }
                            for (int j = 0; j < keyColumns.size(); j++) {
                                setObject(ps, i + j + 1, keysRow.get(keyColumns.get(j)));
                            }
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch();
                    conn.commit();
                }
            }
        } catch (ClassNotFoundException | ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        } finally {
            if (ps != null)
                ps.close();
            if (conn != null)
                conn.close();
        }
    }

    private void setObject(PreparedStatement ps, int index, Object value) throws SQLException {
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

//import lsfusion.server.logics.scripted.ScriptingErrorLog;
//import lsfusion.server.logics.scripted.ScriptingLogicsModule;
//import java.util.Arrays;
//public class TestExportSQLActionProperty extends ExportSQLActionProperty {
//
//    public TestExportSQLActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
//        super(LM, "testtable4", "i", Arrays.asList("dt"), "connectionString", true);
//    }
//}