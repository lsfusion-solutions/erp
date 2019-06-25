package lsfusion.erp.region.by.integration.excel;

import jxl.write.WriteException;
import lsfusion.base.Pair;
import lsfusion.base.file.RawFileData;
import lsfusion.base.file.WriteClientAction;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingLogicsModule;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Iterator;

public class ExportExcelAllAction extends InternalAction {
    private final ClassPropertyInterface dateFromInterface;
    private final ClassPropertyInterface dateToInterface;

    public ExportExcelAllAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        dateFromInterface = i.next();
        dateToInterface = i.next();
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            Pair<String, RawFileData> generalLedgerEntry = new ExportExcelGeneralLedgerAction(LM, dateFromInterface, dateToInterface).createFile(context);
            context.delayUserInteraction(new WriteClientAction(generalLedgerEntry.second, generalLedgerEntry.first, "xls", false, true));

            Pair<String, RawFileData> legalEntityEntry = new ExportExcelLegalEntitiesAction(LM).createFile(context);
            context.delayUserInteraction(new WriteClientAction(legalEntityEntry.second, legalEntityEntry.first, "xls", false, true));

            Pair<String, RawFileData> itemEntry = new ExportExcelItemsAction(LM).createFile(context);
            context.delayUserInteraction(new WriteClientAction(itemEntry.second, itemEntry.first, "xls", false, true));

            Pair<String, RawFileData> userInvoiceEntry = new ExportExcelUserInvoicesAction(LM, dateFromInterface, dateToInterface).createFile(context);
            context.delayUserInteraction(new WriteClientAction(userInvoiceEntry.second, userInvoiceEntry.first, "xls", false, true));

        } catch (IOException | WriteException e) {
            throw new RuntimeException(e);
        }
    }
}