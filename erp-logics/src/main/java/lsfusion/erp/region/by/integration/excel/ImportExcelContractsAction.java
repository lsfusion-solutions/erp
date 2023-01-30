package lsfusion.erp.region.by.integration.excel;

import com.google.common.base.Throwables;
import jxl.Sheet;
import jxl.read.biff.BiffException;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.integration.Contract;
import lsfusion.erp.integration.ImportAction;
import lsfusion.erp.integration.ImportData;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.data.file.CustomStaticFormatFileClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ImportExcelContractsAction extends ImportExcelAction {

    public ImportExcelContractsAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException {
        try {

            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get("Файлы таблиц", "xls");
            ObjectValue objectValue = context.requestUserData(valueClass, null);
            if (objectValue != null) {
                ImportData importData = new ImportData();

                importData.setContractsList(importContracts((RawFileData) objectValue.getValue()));

                new ImportAction(LM).makeImport(importData, context);
            }
        } catch (IOException | BiffException | ParseException e) {
            throw Throwables.propagate(e);
        }
    }

    protected static List<Contract> importContracts(RawFileData file) throws IOException, BiffException, ParseException {

        Sheet sheet = getSheet(file, 6);

        List<Contract> data = new ArrayList<>();

        for (int i = 1; i < sheet.getRows(); i++) {

            String numberContract = parseString(sheet.getCell(0, i));
            String idSupplier = parseString(sheet.getCell(1, i));
            String idCustomer = parseString(sheet.getCell(2, i));
            String idContract = idSupplier + "/" + idCustomer;
            LocalDate dateFromContract = parseDateValue(sheet.getCell(3, i));
            LocalDate dateToContract = parseDateValue(sheet.getCell(4, i));
            String shortNameCurrency = parseString(sheet.getCell(5, i));
            data.add(new Contract(idContract, idSupplier, idCustomer, numberContract, dateFromContract,
                    dateToContract, shortNameCurrency));
        }

        return data;
    }
}