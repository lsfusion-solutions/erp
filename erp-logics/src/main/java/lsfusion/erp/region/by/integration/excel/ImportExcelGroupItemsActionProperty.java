package lsfusion.erp.region.by.integration.excel;

import com.google.common.base.Throwables;
import lsfusion.erp.integration.ImportActionProperty;
import lsfusion.erp.integration.ImportData;
import lsfusion.erp.integration.ItemGroup;
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

public class ImportExcelGroupItemsActionProperty extends ImportExcelActionProperty {

    public ImportExcelGroupItemsActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {

            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, "Файлы таблиц", "xls");
            ObjectValue objectValue = context.requestUserData(valueClass, null);
            if (objectValue != null) {
                List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                for (byte[] file : fileList) {

                    ImportData importData = new ImportData();

                    importData.setParentGroupsList(importGroupItems(file, true));
                    importData.setItemGroupsList(importGroupItems(file, false));

                    new ImportActionProperty(LM).makeImport(importData, context);

                }
            }
        } catch (IOException | BiffException | ParseException e) {
            throw Throwables.propagate(e);
        }
    }

    public static List<ItemGroup> importGroupItems(byte[] file, Boolean parents) throws IOException, BiffException, ParseException {

        Sheet sheet = getSheet(file, 3);

        List<ItemGroup> data = new ArrayList<>();

        for (int i = 1; i < sheet.getRows(); i++) {

            String idGroup = parseString(sheet.getCell(0, i));
            String nameGroup = parseString(sheet.getCell(1, i));
            String idParentGroup = parseString(sheet.getCell(2, i));
            if (parents)
                data.add(new ItemGroup(idGroup, null, idParentGroup));
            else
                data.add(new ItemGroup(idGroup, nameGroup, null));
        }

        return data;
    }
}