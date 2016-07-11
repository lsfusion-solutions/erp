package lsfusion.erp.region.by.integration.excel;

import jxl.write.WriteException;
import jxl.write.biff.RowsExceededException;
import lsfusion.interop.action.ExportFileClientAction;
import lsfusion.server.classes.DateClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ExportExcelAllActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface dateFromInterface;
    private final ClassPropertyInterface dateToInterface;

    public ExportExcelAllActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        dateFromInterface = i.next();
        dateToInterface = i.next();
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            Map<String, byte[]> files = new HashMap<>();
            files.putAll(new ExportExcelGeneralLedgerActionProperty(LM, dateFromInterface, dateToInterface).createFile(context));
            files.putAll(new ExportExcelLegalEntitiesActionProperty(LM).createFile(context));
            files.putAll(new ExportExcelItemsActionProperty(LM).createFile(context));
            files.putAll(new ExportExcelUserInvoicesActionProperty(LM, dateFromInterface, dateToInterface).createFile(context));
            context.delayUserInterfaction(new ExportFileClientAction(files));

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (WriteException e) {
            throw new RuntimeException(e);
        }
    }
}