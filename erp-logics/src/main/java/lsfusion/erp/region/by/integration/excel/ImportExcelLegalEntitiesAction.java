package lsfusion.erp.region.by.integration.excel;

import com.google.common.base.Throwables;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.integration.ImportAction;
import lsfusion.erp.integration.ImportData;
import lsfusion.erp.integration.LegalEntity;
import jxl.Sheet;
import jxl.read.biff.BiffException;
import lsfusion.server.logics.classes.data.file.CustomStaticFormatFileClass;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingLogicsModule;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ImportExcelLegalEntitiesAction extends ImportExcelAction {

    public ImportExcelLegalEntitiesAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException {
        try {

            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get("Файлы таблиц", "xls");
            ObjectValue objectValue = context.requestUserData(valueClass, null);
            if (objectValue != null) {
                ImportData importData = new ImportData();

                importData.setLegalEntitiesList(importLegalEntities((RawFileData) objectValue.getValue()));

                new ImportAction(LM).makeImport(importData, context);
            }
        } catch (IOException | BiffException e) {
            throw Throwables.propagate(e);
        }
    }

    protected static List<LegalEntity> importLegalEntities(RawFileData file) throws IOException, BiffException {

        Sheet sheet = getSheet(file, 14);

        List<LegalEntity> data = new ArrayList<>();

        for (int i = 1; i < sheet.getRows(); i++) {

            String idLegalEntity = parseString(sheet.getCell(0, i));
            String idLegalEntityGroup = parseString(sheet.getCell(1, i));
            String nameLegalEntity = parseString(sheet.getCell(2, i));
            String addressLegalEntity = parseString(sheet.getCell(3, i));
            String phoneLegalEntity = parseString(sheet.getCell(4, i));
            String emailLegalEntity = parseString(sheet.getCell(5, i));
            String numberAccount = parseString(sheet.getCell(6, i));
            String idBank = parseString(sheet.getCell(7, i));
            String nameCountry = parseString(sheet.getCell(8, i));
            nameCountry = nameCountry == null ? null : nameCountry.toUpperCase();
            Boolean isSupplier = parseBoolean(sheet.getCell(9, i));
            Boolean isCompany = parseBoolean(sheet.getCell(10, i));
            Boolean isCustomer = parseBoolean(sheet.getCell(11, i));
            String unpLegalEntity = parseString(sheet.getCell(12, i));
            String okpoLegalEntity = parseString(sheet.getCell(13, i));
            String[] ownership = getAndTrimOwnershipFromName(nameLegalEntity);

            data.add(new LegalEntity(idLegalEntity, nameLegalEntity, addressLegalEntity, unpLegalEntity, okpoLegalEntity,
                    phoneLegalEntity, emailLegalEntity, ownership[1], ownership[0], numberAccount, null, null, idBank,
                    nameCountry, isSupplier, isCompany, isCustomer, idLegalEntityGroup));
        }

        return data;
    }  
}