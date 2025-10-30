package lsfusion.erp.region.by.integration.excel;

import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.integration.ImportAction;
import lsfusion.erp.integration.ImportData;
import lsfusion.erp.integration.Warehouse;
import lsfusion.erp.integration.WarehouseGroup;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ImportExcelWarehousesAction extends ImportExcelAction {

    public ImportExcelWarehousesAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException {
        try {
            ObjectValue objectValue = requestUserData(context, "Файлы таблиц", "xls");
            if (objectValue != null) {
                ImportData importData = new ImportData();

                importData.setWarehouseGroupsList(importWarehouseGroups((RawFileData) objectValue.getValue()));
                importData.setWarehousesList(importWarehouses((RawFileData) objectValue.getValue()));

                new ImportAction(LM).makeImport(importData, context);
            }
        } catch (IOException | BiffException e) {
            throw new RuntimeException(e);
        }
    }

    protected static List<Warehouse> importWarehouses(RawFileData file) throws IOException, BiffException {

        Sheet sheet = getSheet(file, 6);

        List<Warehouse> data = new ArrayList<>();

        for (int i = 1; i < sheet.getRows(); i++) {

            String idWarehouse = parseString(sheet.getCell(0, i));
            String nameWarehouse = parseString(sheet.getCell(1, i));
            String idWarehouseGroup = parseString(sheet.getCell(2, i));
            String idLegalEntity = parseString(sheet.getCell(4, i));
            String addressWarehouse = parseString(sheet.getCell(5, i));

            data.add(new Warehouse(idLegalEntity, idWarehouseGroup, idWarehouse, nameWarehouse, addressWarehouse));
        }

        return data;
    }

    protected static List<WarehouseGroup> importWarehouseGroups(RawFileData file) throws IOException, BiffException {

        WorkbookSettings ws = new WorkbookSettings();
        ws.setGCDisabled(true);
        Workbook Wb = Workbook.getWorkbook(file.getInputStream(), ws);
        Sheet sheet = Wb.getSheet(0);

        List<WarehouseGroup> data = new ArrayList<>();

        for (int i = 1; i < sheet.getRows(); i++) {

            String idWarehouseGroup = parseString(sheet.getCell(2, i));
            String nameWarehouseGroup = parseString(sheet.getCell(3, i));

            data.add(new WarehouseGroup(idWarehouseGroup, nameWarehouseGroup));
        }

        return data;
    }
}