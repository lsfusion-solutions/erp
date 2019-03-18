package lsfusion.erp.region.by.integration.formular;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.logics.classes.user.ConcreteCustomClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.physics.dev.integration.service.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class ImportFormularActionProperty extends InternalAction {
    public ImportFormularActionProperty(ScriptingLogicsModule LM) {
        super(LM);

        try {
            Class.forName("sun.jdbc.odbc.JdbcOdbcDriver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        Connection conn = null;

        try {
            // Get a connection to the database
            conn = DriverManager.getConnection(((String) findProperty("importUrl[]").read(context)).trim(),
                    ((String) findProperty("importLogin[]").read(context)).trim(),
                    ((String) findProperty("importPassword[]").read(context)).trim());

            importItemGroup(context, conn);

            // Close the result set, statement and the connection
        } catch (SQLException e) {
            context.delayUserInterfaction(new MessageClientAction("Ошибка при подключении к базе данных : " + e.getLocalizedMessage(), "Импорт данных"));
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        } finally {
            if (conn != null)
                conn.close();
        }
    }

    private void importItemGroup(ExecutionContext context, Connection conn) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        ResultSet rs = conn.createStatement().executeQuery(
                "SELECT num_class AS ext_id, name_u AS name, par AS par_id FROM klass");

        ImportField idItemGroup = new ImportField(findProperty("id[ItemGroup]"));
        ImportField itemGroupName = new ImportField(findProperty("name[ItemGroup]"));
        ImportField idParentGroup = new ImportField(findProperty("id[ItemGroup]"));

        ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) findClass("ItemGroup"),
                findProperty("itemGroup[VARSTRING[100]]").getMapping(idItemGroup));
        ImportProperty<?> itemGroupIDProperty = new ImportProperty(idItemGroup, findProperty("id[ItemGroup]").getMapping(itemGroupKey));
        ImportProperty<?> itemGroupNameProperty = new ImportProperty(itemGroupName, findProperty("name[ItemGroup]").getMapping(itemGroupKey));

        ImportKey<?> parentGroupKey = new ImportKey((ConcreteCustomClass) findClass("ItemGroup"),
                findProperty("itemGroup[VARSTRING[100]]").getMapping(idParentGroup));

        ImportProperty<?> parentGroupProperty = new ImportProperty(idParentGroup, findProperty("parent[ItemGroup]").getMapping(itemGroupKey),
                object(findClass("ItemGroup")).getMapping(parentGroupKey));

        Collection<? extends ImportKey<?>> keys = Arrays.asList(itemGroupKey, parentGroupKey);
        Collection<ImportProperty<?>> properties = Arrays.asList(itemGroupIDProperty, itemGroupNameProperty, parentGroupProperty);

        new IntegrationService(context,
                new ImportTable(Arrays.asList(idItemGroup, itemGroupName, idParentGroup), createData(rs)),
                keys,
                properties).synchronize();
    }

    private List<List<Object>> createData(ResultSet rs) throws SQLException {

        ResultSetMetaData rsmd = rs.getMetaData();

        int columnCount = rsmd.getColumnCount();

        List<List<Object>> data = new ArrayList<>();
        while (rs.next()) {
            List<Object> row = new ArrayList<>();
            for (int i = 0; i < columnCount; i++)
                row.add(rs.getObject(i));
            data.add(row);
        }

        return data;
    }
}
