package lsfusion.erp.region.by.integration.excel;

import com.google.common.base.Throwables;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.integration.*;
import jxl.Sheet;
import jxl.read.biff.BiffException;
import lsfusion.server.logics.classes.data.file.CustomStaticFormatFileClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingLogicsModule;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class ImportExcelDepartmentStoresAction extends ImportExcelAction {

    public ImportExcelDepartmentStoresAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get("Файлы таблиц", "xls");
            ObjectValue objectValue = context.requestUserData(valueClass, null);
            if (objectValue != null) {
                ImportData importData = new ImportData();

                importData.setDepartmentStoresList(importDepartmentStores((RawFileData) objectValue.getValue()));

                new ImportAction(LM).makeImport(importData, context);
            }
        } catch (IOException | BiffException | ParseException e) {
            throw Throwables.propagate(e);
        }
    }

    protected static List<DepartmentStore> importDepartmentStores(RawFileData file) throws IOException, BiffException, ParseException {

        Sheet sheet = getSheet(file, 3);

        List<DepartmentStore> data = new ArrayList<>();

        for (int i = 1; i < sheet.getRows(); i++) {

            String idDepartmentStore = parseString(sheet.getCell(0, i));
            String nameDepartmentStore = parseString(sheet.getCell(1, i));
            String idStore = parseString(sheet.getCell(2, i));

            data.add(new DepartmentStore(idDepartmentStore, nameDepartmentStore, idStore));
        }

        return data;
    }
}