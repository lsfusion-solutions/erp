package lsfusion.erp.region.by.integration.excel;

import com.google.common.base.Throwables;
import jxl.Sheet;
import jxl.read.biff.BiffException;
import lsfusion.erp.integration.ImportActionProperty;
import lsfusion.erp.integration.ImportData;
import lsfusion.erp.integration.UOM;
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

public class ImportExcelUOMsActionProperty extends ImportExcelActionProperty {

    public ImportExcelUOMsActionProperty(ScriptingLogicsModule LM) {
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

                    importData.setUOMsList(importUOMs(file));

                    new ImportActionProperty(LM).makeImport(importData, context);

                }
            }
        } catch (IOException | BiffException | ParseException e) {
            throw Throwables.propagate(e);
        }
    }

    protected static List<UOM> importUOMs(byte[] file) throws IOException, BiffException, ParseException {

        Sheet sheet = getSheet(file, 3);

        List<UOM> data = new ArrayList<>();

        for (int i = 1; i < sheet.getRows(); i++) {

            String idUOM = parseString(sheet.getCell(0, i));
            String nameUOM = parseString(sheet.getCell(1, i));
            String shortNameUOM = parseString(sheet.getCell(2, i));

            data.add(new UOM(idUOM, nameUOM, shortNameUOM));
        }

        return data;
    }
}