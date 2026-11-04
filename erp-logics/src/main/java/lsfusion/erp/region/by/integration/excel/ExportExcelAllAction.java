package lsfusion.erp.region.by.integration.excel;

import jxl.write.WriteException;
import lsfusion.base.Pair;
import lsfusion.base.file.FileData;
import lsfusion.base.file.NamedFileData;
import lsfusion.base.file.RawFileData;
import lsfusion.base.file.WriteClientAction;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingLogicsModule;

import java.io.IOException;
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
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) {
        try {

            Pair<String, RawFileData> generalLedgerEntry = new ExportExcelGeneralLedgerAction(LM, dateFromInterface, dateToInterface).createFile(context);
            context.delayUserInteraction(new WriteClientAction(new NamedFileData(new FileData(generalLedgerEntry.second, "xls")), generalLedgerEntry.first, false, true));

            Pair<String, RawFileData> legalEntityEntry = new ExportExcelLegalEntitiesAction(LM).createFile(context);
            context.delayUserInteraction(new WriteClientAction(new NamedFileData(new FileData(legalEntityEntry.second, "xls")), legalEntityEntry.first, false, true));

            Pair<String, RawFileData> itemEntry = new ExportExcelItemsAction(LM).createFile(context);
            context.delayUserInteraction(new WriteClientAction(new NamedFileData(new FileData(itemEntry.second, "xls")), itemEntry.first, false, true));

            Pair<String, RawFileData> userInvoiceEntry = new ExportExcelUserInvoicesAction(LM, dateFromInterface, dateToInterface).createFile(context);
            context.delayUserInteraction(new WriteClientAction(new NamedFileData(new FileData(userInvoiceEntry.second, "xls")), userInvoiceEntry.first, false, true));

        } catch (IOException | WriteException e) {
            throw new RuntimeException(e);
        }
    }
}