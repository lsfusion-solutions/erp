package lsfusion.erp.region.by.integration.excel;

import com.google.common.base.Throwables;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.integration.Bank;
import lsfusion.erp.integration.ImportAction;
import lsfusion.erp.integration.ImportData;
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

public class ImportExcelBanksAction extends ImportExcelAction {

    public ImportExcelBanksAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException {
        try {

            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get( "Файлы таблиц", "xls");
            ObjectValue objectValue = context.requestUserData(valueClass, null);
            if (objectValue != null) {
                ImportData importData = new ImportData();

                importData.setBanksList(importBanks((RawFileData) objectValue.getValue()));

                new ImportAction(LM).makeImport(importData, context);
            }
        } catch (IOException | BiffException e) {
            throw Throwables.propagate(e);
        }
    }

    protected static List<Bank> importBanks(RawFileData file) throws IOException, BiffException {

        Sheet sheet = getSheet(file, 6);

        List<Bank> data = new ArrayList<>();

        for (int i = 1; i < sheet.getRows(); i++) {

            String idBank = parseString(sheet.getCell(0, i));
            String nameBank = parseString(sheet.getCell(1, i));
            String addressBank = parseString(sheet.getCell(2, i));
            String departmentBank = parseString(sheet.getCell(3, i));
            String mfoBank = parseString(sheet.getCell(4, i));
            String cbuBank = parseString(sheet.getCell(5, i));

            data.add(new Bank(idBank, nameBank, addressBank, departmentBank, mfoBank, cbuBank));
        }

        return data;
    }
}