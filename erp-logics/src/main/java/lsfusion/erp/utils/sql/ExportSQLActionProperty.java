package lsfusion.erp.utils.sql;

import com.google.common.base.Throwables;
import lsfusion.base.col.interfaces.immutable.ImList;
import lsfusion.server.ServerLoggers;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.form.entity.PropertyDrawEntity;
import lsfusion.server.form.instance.FormData;
import lsfusion.server.form.instance.FormInstance;
import lsfusion.server.form.instance.FormRow;
import lsfusion.server.form.instance.PropertyDrawInstance;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingActionProperty;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract class ExportSQLActionProperty extends ScriptingActionProperty {
    String idForm; //idForm = table
    String table;
    String idGroupObject;
    List<String> keyColumns;
    String connectionStringProperty;
    boolean truncate;
    boolean noInsert;

    public ExportSQLActionProperty(ScriptingLogicsModule LM, String idForm, String idGroupObject,
                                   List<String> keyColumns, String connectionStringProperty, boolean truncate, boolean noInsert) {
        this(LM, idForm, idGroupObject, keyColumns, connectionStringProperty, idForm, truncate, noInsert);
    }

    public ExportSQLActionProperty(ScriptingLogicsModule LM, String idForm, String idGroupObject,
                                   List<String> keyColumns, String connectionStringProperty, String table, boolean truncate, boolean noInsert) {
        super(LM);
        this.idForm = idForm;
        this.idGroupObject = idGroupObject;
        this.keyColumns = keyColumns;
        this.connectionStringProperty = connectionStringProperty;
        this.table = table;
        this.truncate = truncate;
        this.noInsert = noInsert;
    }

    public abstract void init() throws ClassNotFoundException;

    public abstract String getTruncateStatement();

    public abstract String getInsertStatement(String columns, String params);

    public abstract String getUpdateStatement(String set, String wheres, String columns, String params);

    public abstract void setObject(PreparedStatement ps, int index, Object value) throws SQLException;

    @Override
    public void executeCustom(ExecutionContext context) throws SQLException, SQLHandledException {

        Connection conn = null;
        PreparedStatement ps = null;
        try {

            if (idForm != null && idGroupObject != null && connectionStringProperty != null) {

                String url = (String) findProperty(connectionStringProperty).read(context);

                FormEntity formEntity = findForm(idForm);
                FormInstance formInstance = context.createFormInstance(formEntity);
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
                    init();
                    conn = DriverManager.getConnection(url);
                    conn.setAutoCommit(false);

                    ServerLoggers.importLogger.info("ExportSQL: started");

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
                            String truncateStatement = getTruncateStatement();
                            ServerLoggers.importLogger.info("ExportSQL: " + truncateStatement);
                            statement.execute(truncateStatement);
                            conn.commit();
                        } finally {
                            if (statement != null)
                                statement.close();
                        }
                    }
                    ServerLoggers.importLogger.info(String.format("ExportSQL: prepare statement (%s rows, %s columns, %s keys)", rows.size(), columnNames.size(), keyColumns.size()));
                    if (wheres.isEmpty() || truncate) {
                        ps = conn.prepareStatement(getInsertStatement(columns, params));
                        for (List<Object> row : rows) {
                            for (int i = 0; i < paramLength; i++) {
                                setObject(ps, i + 1, row.get(i));
                            }
                            ps.addBatch();
                        }
                    } else {
                        ps = conn.prepareStatement(getUpdateStatement(set, wheres, columns, params));

                        for (int k = 0; k < rows.size(); k++) {
                            List<Object> row = rows.get(k);
                            Map<String, Object> keysRow = keysRows.get(k);
                            int i;
                            for (i = 0; i < paramLength; i++) {
                                Object value = row.get(i);
                                setObject(ps, i + 1, value);
                                if (!noInsert)
                                    setObject(ps, i + paramLength + keyColumns.size() + 1, value);
                            }
                            for (int j = 0; j < keyColumns.size(); j++) {
                                setObject(ps, i + j + 1, keysRow.get(keyColumns.get(j)));
                            }
                            ps.addBatch();
                        }
                    }
                    ServerLoggers.importLogger.info("ExportSQL: execute batch");
                    ps.executeBatch();
                    ServerLoggers.importLogger.info("ExportSQL: commit");
                    conn.commit();
                    ServerLoggers.importLogger.info("ExportSQL: finished");
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
}