package lsfusion.erp.region.by.integration.excel;

import jxl.write.WriteException;
import lsfusion.base.Pair;
import lsfusion.base.file.RawFileData;
import lsfusion.base.file.WriteClientAction;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingActionProperty;
import lsfusion.server.language.ScriptingLogicsModule;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Iterator;

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

            Pair<String, RawFileData> generalLedgerEntry = new ExportExcelGeneralLedgerActionProperty(LM, dateFromInterface, dateToInterface).createFile(context);
            context.delayUserInteraction(new WriteClientAction(generalLedgerEntry.second, generalLedgerEntry.first, "xls", false, true));

            Pair<String, RawFileData> legalEntityEntry = new ExportExcelLegalEntitiesActionProperty(LM).createFile(context);
            context.delayUserInteraction(new WriteClientAction(legalEntityEntry.second, legalEntityEntry.first, "xls", false, true));

            Pair<String, RawFileData> itemEntry = new ExportExcelItemsActionProperty(LM).createFile(context);
            context.delayUserInteraction(new WriteClientAction(itemEntry.second, itemEntry.first, "xls", false, true));

            Pair<String, RawFileData> userInvoiceEntry = new ExportExcelUserInvoicesActionProperty(LM, dateFromInterface, dateToInterface).createFile(context);
            context.delayUserInteraction(new WriteClientAction(userInvoiceEntry.second, userInvoiceEntry.first, "xls", false, true));

        } catch (IOException | WriteException e) {
            throw new RuntimeException(e);
        }
    }
}