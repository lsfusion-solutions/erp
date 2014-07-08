package lsfusion.erp.region.by.integration.excel;

import lsfusion.erp.integration.Bank;
import lsfusion.erp.integration.ImportActionProperty;
import lsfusion.erp.integration.ImportData;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class ImportExcelBanksActionProperty extends ImportExcelActionProperty {

    public ImportExcelBanksActionProperty(ScriptingLogicsModule LM) {
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

                    importData.setBanksList(importBanks(file));

                    new ImportActionProperty(LM).makeImport(importData, context);

                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (BiffException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    protected static List<Bank> importBanks(byte[] file) throws IOException, BiffException, ParseException {

        Workbook Wb = Workbook.getWorkbook(new ByteArrayInputStream(file));
        Sheet sheet = Wb.getSheet(0);

        List<Bank> data = new ArrayList<Bank>();

        for (int i = 1; i < sheet.getRows(); i++) {

            String idBank = parseString(sheet.getCell(0, i).getContents());
            String nameBank = parseString(sheet.getCell(1, i).getContents());
            String addressBank = parseString(sheet.getCell(2, i).getContents());
            String departmentBank = parseString(sheet.getCell(3, i).getContents());
            String mfoBank = parseString(sheet.getCell(4, i).getContents());
            String cbuBank = parseString(sheet.getCell(5, i).getContents());

            data.add(new Bank(idBank, nameBank, addressBank, departmentBank, mfoBank, cbuBank));
        }

        return data;
    }
}