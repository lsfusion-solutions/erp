package lsfusion.erp.region.by.integration.excel;

import com.google.common.base.Throwables;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.integration.*;
import jxl.Sheet;
import jxl.read.biff.BiffException;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class ImportExcelDepartmentStoresActionProperty extends ImportExcelActionProperty {

    public ImportExcelDepartmentStoresActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get("Файлы таблиц", "xls");
            ObjectValue objectValue = context.requestUserData(valueClass, null);
            if (objectValue != null) {
                ImportData importData = new ImportData();

                importData.setDepartmentStoresList(importDepartmentStores((RawFileData) objectValue.getValue()));

                new ImportActionProperty(LM).makeImport(importData, context);
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