package lsfusion.erp.region.by.integration.excel;

import com.google.common.base.Throwables;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.integration.*;
import jxl.Sheet;
import jxl.read.biff.BiffException;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingLogicsModule;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ImportExcelStoresAction extends ImportExcelAction {

    public ImportExcelStoresAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException {
        try {
            ObjectValue objectValue = requestUserData(context, "Файлы таблиц", "xls");
            if (objectValue != null) {
                ImportData importData = new ImportData();

                importData.setStoresList(importStores((RawFileData) objectValue.getValue()));

                new ImportAction(LM).makeImport(importData, context);
            }
        } catch (IOException | BiffException e) {
            throw Throwables.propagate(e);
        }
    }

    protected static List<LegalEntity> importStores(RawFileData file) throws IOException, BiffException {

        Sheet sheet = getSheet(file, 4);

        List<LegalEntity> data = new ArrayList<>();

        for (int i = 1; i < sheet.getRows(); i++) {
            String idStore = parseString(sheet.getCell(0, i));
            String nameStore = parseString(sheet.getCell(1, i));
            String addressStore = parseString(sheet.getCell(2, i));
            String idLegalEntity = parseString(sheet.getCell(3, i));

            data.add(new Store(idStore, nameStore, addressStore, idLegalEntity, null, null));
        }

        return data;
    }
}