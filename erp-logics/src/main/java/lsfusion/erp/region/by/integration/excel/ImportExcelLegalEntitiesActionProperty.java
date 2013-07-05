package lsfusion.erp.region.by.integration.excel;

import lsfusion.erp.integration.ImportActionProperty;
import lsfusion.erp.integration.ImportData;
import lsfusion.erp.integration.LegalEntity;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;
import lsfusion.server.classes.CustomStaticFormatFileClass;
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

public class ImportExcelLegalEntitiesActionProperty extends ImportExcelActionProperty {

    public ImportExcelLegalEntitiesActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {
        try {

            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, "Файлы таблиц", "xls");
            ObjectValue objectValue = context.requestUserData(valueClass, null);
            if (objectValue != null) {
                List<byte[]> fileList = valueClass.getFiles(objectValue.getValue());

                for (byte[] file : fileList) {

                    ImportData importData = new ImportData();

                    importData.setLegalEntitiesList(importLegalEntities(file));

                    new ImportActionProperty(LM, importData, context).makeImport();

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

    protected static List<LegalEntity> importLegalEntities(byte[] file) throws IOException, BiffException, ParseException {

        Workbook Wb = Workbook.getWorkbook(new ByteArrayInputStream(file));
        Sheet sheet = Wb.getSheet(0);

        List<LegalEntity> data = new ArrayList<LegalEntity>();

        for (int i = 1; i < sheet.getRows(); i++) {

            String idLegalEntity = parseString(sheet.getCell(0, i).getContents());
            String nameLegalEntity = parseString(sheet.getCell(1, i).getContents());
            String addressLegalEntity = parseString(sheet.getCell(2, i).getContents());
            String phoneLegalEntity = parseString(sheet.getCell(3, i).getContents());
            String emailLegalEntity = parseString(sheet.getCell(4, i).getContents());
            String numberAccount = parseString(sheet.getCell(5, i).getContents());
            String idBank = parseString(sheet.getCell(6, i).getContents());
            String nameCountry = parseString(sheet.getCell(7, i).getContents());
            Boolean isSupplier = parseBoolean(sheet.getCell(8, i).getContents());
            Boolean isCompany = parseBoolean(sheet.getCell(9, i).getContents());
            Boolean isCustomer = parseBoolean(sheet.getCell(10, i).getContents());
            String unpLegalEntity = parseString(sheet.getCell(11, i).getContents());
            String okpoLegalEntity = parseString(sheet.getCell(12, i).getContents());
            String[] ownership = getAndTrimOwnershipFromName(nameLegalEntity);

            data.add(new LegalEntity(idLegalEntity, nameLegalEntity, addressLegalEntity, unpLegalEntity, okpoLegalEntity,
                    phoneLegalEntity, emailLegalEntity, ownership[1], ownership[0],
                    numberAccount, null, null, idBank, nameCountry, isSupplier, isCompany, isCustomer));
        }

        return data;
    }

    private static String[] getAndTrimOwnershipFromName(String name) {
        String nameOwnership = null;
        String shortNameOwnership = null;
        for (String[] ownership : ownershipsList) {
            if (name.contains(ownership[0] + " ") || name.contains(" " + ownership[0])) {
                nameOwnership = ownership[1];
                shortNameOwnership = ownership[0];
                name = name.replace(ownership[0], "");
            }
        }
        return new String[]{shortNameOwnership, nameOwnership, name};
    }

    static String[][] ownershipsList = new String[][]{
            {"ОАОТ", "Открытое акционерное общество торговое"},
            {"ОАО", "Открытое акционерное общество"},
            {"СООО", "Совместное общество с ограниченной ответственностью"},
            {"ООО", "Общество с ограниченной ответственностью"},
            {"ОДО", "Общество с дополнительной ответственностью"},
            {"ЗАО", "Закрытое акционерное общество"},
            {"ЧТУП", "Частное торговое унитарное предприятие"},
            {"ЧУТП", "Частное унитарное торговое предприятие"},
            {"ТЧУП", "Торговое частное унитарное предприятие"},
            {"ЧУП", "Частное унитарное предприятие"},
            {"РУП", "Республиканское унитарное предприятие"},
            {"РДУП", "Республиканское дочернее унитарное предприятие"},
            {"УП", "Унитарное предприятие"},
            {"ИП", "Индивидуальный предприниматель"},
            {"СПК", "Сельскохозяйственный производственный кооператив"},
            {"СП", "Совместное предприятие"}};
}