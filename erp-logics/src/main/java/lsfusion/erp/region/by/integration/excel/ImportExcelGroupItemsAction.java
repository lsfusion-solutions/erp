package lsfusion.erp.region.by.integration.excel;

import com.google.common.base.Throwables;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.integration.ImportAction;
import lsfusion.erp.integration.ImportData;
import lsfusion.erp.integration.ItemGroup;
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

public class ImportExcelGroupItemsAction extends ImportExcelAction {

    public ImportExcelGroupItemsAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException {
        try {
            
            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get( "Файлы таблиц", "xls");
            ObjectValue objectValue = context.requestUserData(valueClass, null);
            if (objectValue != null) {
                ImportData importData = new ImportData();

                importData.setParentGroupsList(importGroupItems((RawFileData) objectValue.getValue(), true));
                importData.setItemGroupsList(importGroupItems((RawFileData) objectValue.getValue(), false));

                new ImportAction(LM).makeImport(importData, context);
            }
        } catch (IOException | BiffException | ParseException e) {
            throw Throwables.propagate(e);
        }
    }

    public static List<ItemGroup> importGroupItems(RawFileData file, Boolean parents) throws IOException, BiffException, ParseException {

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