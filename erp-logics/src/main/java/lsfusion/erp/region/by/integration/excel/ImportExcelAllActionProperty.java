package lsfusion.erp.region.by.integration.excel;

import lsfusion.erp.integration.ImportActionProperty;
import lsfusion.erp.integration.ImportData;
import jxl.read.biff.BiffException;
import lsfusion.server.classes.CustomStaticFormatFileClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Map;

public class ImportExcelAllActionProperty extends ScriptingActionProperty {

    public ImportExcelAllActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(true, true, "Файлы таблиц", "xls");
            ObjectValue objectValue = context.requestUserData(valueClass, null);
            if (objectValue != null) {
                Map<String, byte[]> fileList = valueClass.getNamedFiles(objectValue.getValue());

                for (Map.Entry<String, byte[]> file : fileList.entrySet()) {

                    ImportData importData = new ImportData();

                    importData.setSkipKeys(findProperty("skipKeysExcel[]").read(context) != null);

                    if (file.getKey().contains("importUOMs")) {
                        importData.setUOMsList(ImportExcelUOMsActionProperty.importUOMs(file.getValue()));
                    }
                    if (file.getKey().contains("importItems")) {
                        boolean onlyEAN = findProperty("importItemsOnlyEAN[]").read(context) != null;
                        importData.setItemsList(ImportExcelItemsActionProperty.importItems(file.getValue(), onlyEAN));
                    }
                    if (file.getKey().contains("importGroupItems")) {
                        importData.setParentGroupsList(ImportExcelGroupItemsActionProperty.importGroupItems(file.getValue(), true));
                        importData.setItemGroupsList(ImportExcelGroupItemsActionProperty.importGroupItems(file.getValue(), false));
                    }
                    if (file.getKey().contains("importBanks")) {
                        importData.setBanksList(ImportExcelBanksActionProperty.importBanks(file.getValue()));
                    }
                    if (file.getKey().contains("importLegalEntities")) {
                        importData.setLegalEntitiesList(ImportExcelLegalEntitiesActionProperty.importLegalEntities(file.getValue()));
                    }
                    if (file.getKey().contains("importStores")) {
                        importData.setStoresList(ImportExcelStoresActionProperty.importStores(file.getValue()));
                    }
                    if (file.getKey().contains("importDepartmentStores")) {
                        importData.setDepartmentStoresList(ImportExcelDepartmentStoresActionProperty.importDepartmentStores(file.getValue()));
                    }
                    if (file.getKey().contains("importWarehouses")) {
                        importData.setWarehouseGroupsList(ImportExcelWarehousesActionProperty.importWarehouseGroups(file.getValue()));
                        importData.setWarehousesList(ImportExcelWarehousesActionProperty.importWarehouses(file.getValue()));
                    }
                    if (file.getKey().contains("importContracts")) {
                        importData.setContractsList(ImportExcelContractsActionProperty.importContracts(file.getValue()));
                    }
                    if (file.getKey().contains("importUserInvoices")) {
                        importData.setUserInvoicesList(ImportExcelUserInvoicesActionProperty.importUserInvoices(file.getValue()));
                    }

                    new ImportActionProperty(LM).makeImport(importData, context);
                }
            }
        } catch (BiffException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (ParseException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
}