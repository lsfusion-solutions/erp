package lsfusion.erp.integration;

import lsfusion.server.classes.*;
import lsfusion.server.integration.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.NullValue;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;
import org.xBaseJ.xBaseJException;

import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImportActionProperty {
    private ScriptingLogicsModule LM;
    private ImportData importData;
    private ExecutionContext<ClassPropertyInterface> context;

    public ImportActionProperty(ScriptingLogicsModule LM, ImportData importData, ExecutionContext<ClassPropertyInterface> context) {
        this.LM = LM;
        this.importData = importData;
        this.context = context;
    }

    public void makeImport() throws SQLException {
        try {

            Object countryBelarus = LM.findLCPByCompoundName("countrySID").read(context.getSession(), new DataObject("112", StringClass.get(3)));
            LM.findLCPByCompoundName("defaultCountry").change(countryBelarus, context.getSession());
            context.getSession().apply(context.getBL());

            importItemGroups(importData.getItemGroupsList());

            importParentGroups(importData.getParentGroupsList());

            importBanks(importData.getBanksList());

            importLegalEntities(importData.getLegalEntitiesList());

            importEmployees(importData.getEmployeesList());

            importWarehouseGroups(importData.getWarehouseGroupsList());

            importWarehouses(importData.getWarehousesList());

            importStores(importData.getStoresList());

            importDepartmentStores(importData.getDepartmentStoresList());

            importContracts(importData.getContractsList());

            importRateWastes(importData.getRateWastesList());

            importWares(importData.getWaresList());

            importItems(importData.getItemsList(), importData.getNumberOfItemsAtATime(), importData.getSkipKeys());

            importPriceListStores(importData.getPriceListStoresList(), importData.getNumberOfPriceListsAtATime());

            importPriceListSuppliers(importData.getPriceListSuppliersList(), importData.getNumberOfPriceListsAtATime());

            importUserInvoices(importData.getUserInvoicesList(), importData.getImportUserInvoicesPosted(), importData.getNumberOfUserInvoicesAtATime(), importData.getSkipKeys());

        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }

    private void importParentGroups(List<ItemGroup> parentGroupsList) throws ScriptingErrorLog.SemanticErrorException {
        try {
            if (parentGroupsList != null) {

                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                List<List<Object>> data = initData(parentGroupsList.size());

                ImportField idItemGroupField = new ImportField(LM.findLCPByCompoundName("idItemGroup"));
                ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("ItemGroup"),
                        LM.findLCPByCompoundName("itemGroupId").getMapping(idItemGroupField));
                keys.add(itemGroupKey);
                fields.add(idItemGroupField);
                for (int i = 0; i < parentGroupsList.size(); i++)
                    data.get(i).add(parentGroupsList.get(i).sid);

                ImportField idParentGroupField = new ImportField(LM.findLCPByCompoundName("idItemGroup"));
                ImportKey<?> parentGroupKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("ItemGroup"),
                        LM.findLCPByCompoundName("itemGroupId").getMapping(idParentGroupField));
                keys.add(parentGroupKey);
                props.add(new ImportProperty(idParentGroupField, LM.findLCPByCompoundName("parentItemGroup").getMapping(itemGroupKey),
                        LM.object(LM.findClassByCompoundName("ItemGroup")).getMapping(parentGroupKey)));
                fields.add(idParentGroupField);
                for (int i = 0; i < parentGroupsList.size(); i++)
                    data.get(i).add(parentGroupsList.get(i).parent);

                ImportTable table = new ImportTable(fields, data);

                DataSession session = context.createSession();
                session.sql.pushVolatileStats(null);
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context.getBL());
                session.sql.popVolatileStats(null);
                session.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void importItemGroups(List<ItemGroup> itemGroupsList) throws SQLException, ScriptingErrorLog.SemanticErrorException {

        try {
            if (itemGroupsList != null) {

                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                List<List<Object>> data = initData(itemGroupsList.size());

                ImportField idItemGroupField = new ImportField(LM.findLCPByCompoundName("idItemGroup"));
                ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("ItemGroup"),
                        LM.findLCPByCompoundName("itemGroupId").getMapping(idItemGroupField));
                keys.add(itemGroupKey);
                props.add(new ImportProperty(idItemGroupField, LM.findLCPByCompoundName("idItemGroup").getMapping(itemGroupKey)));
                fields.add(idItemGroupField);
                for (int i = 0; i < itemGroupsList.size(); i++)
                    data.get(i).add(itemGroupsList.get(i).sid);

                ImportField itemGroupNameField = new ImportField(LM.findLCPByCompoundName("nameItemGroup"));
                props.add(new ImportProperty(itemGroupNameField, LM.findLCPByCompoundName("nameItemGroup").getMapping(itemGroupKey)));
                fields.add(itemGroupNameField);
                for (int i = 0; i < itemGroupsList.size(); i++)
                    data.get(i).add(itemGroupsList.get(i).name);

                ImportTable table = new ImportTable(fields, data);

                DataSession session = context.createSession();
                session.sql.pushVolatileStats(null);
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context.getBL());
                session.sql.popVolatileStats(null);
                session.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void importWares(List<Ware> waresList) throws SQLException, ScriptingErrorLog.SemanticErrorException {

        try {
            if (waresList != null) {

                DataObject defaultDate = new DataObject(new java.sql.Date(2001 - 1900, 0, 01), DateClass.instance);

                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                List<List<Object>> data = initData(waresList.size());

                ImportField idWareField = new ImportField(LM.findLCPByCompoundName("idWare"));
                ImportKey<?> wareKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Ware"),
                        LM.findLCPByCompoundName("wareId").getMapping(idWareField));
                keys.add(wareKey);
                props.add(new ImportProperty(idWareField, LM.findLCPByCompoundName("idWare").getMapping(wareKey)));
                fields.add(idWareField);
                for (int i = 0; i < waresList.size(); i++)
                    data.get(i).add(waresList.get(i).idWare);

                ImportField nameWareField = new ImportField(LM.findLCPByCompoundName("nameWare"));
                props.add(new ImportProperty(nameWareField, LM.findLCPByCompoundName("nameWare").getMapping(wareKey)));
                fields.add(nameWareField);
                for (int i = 0; i < waresList.size(); i++)
                    data.get(i).add(waresList.get(i).nameWare);

                ImportField priceWareField = new ImportField(LM.findLCPByCompoundName("warePrice"));
                props.add(new ImportProperty(priceWareField, LM.findLCPByCompoundName("dataWarePriceDate").getMapping(wareKey, defaultDate)));
                fields.add(priceWareField);
                for (int i = 0; i < waresList.size(); i++)
                    data.get(i).add(waresList.get(i).priceWare);

                ImportTable table = new ImportTable(fields, data);

                DataSession session = context.createSession();
                session.sql.pushVolatileStats(null);
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context.getBL());
                session.sql.popVolatileStats(null);
                session.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void importItems(List<Item> itemsList, Integer numberOfItemsAtATime, boolean skipKeys) throws SQLException, ScriptingErrorLog.SemanticErrorException {

        try {
            Integer numAtATime = (numberOfItemsAtATime == null || numberOfItemsAtATime <= 0) ? 5000 : numberOfItemsAtATime;
            if (itemsList != null) {
                int amountOfImportIterations = (int) Math.ceil((double) itemsList.size() / numAtATime);
                Integer rest = itemsList.size();
                for (int i = 0; i < amountOfImportIterations; i++) {
                    importPackOfItems(itemsList.subList(i * numAtATime, i * numAtATime +  (rest > numAtATime ? numAtATime : rest)), skipKeys);
                    rest -= numAtATime;
                    System.gc();
                }
            }
        } catch (xBaseJException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void importPackOfItems(List<Item> itemsList, boolean skipKeys) throws SQLException, IOException, xBaseJException, ScriptingErrorLog.SemanticErrorException {
        if (itemsList.size() == 0) return;

        List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
        List<ImportField> fields = new ArrayList<ImportField>();
        List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

        List<List<Object>> data = initData(itemsList.size());

        ImportField idItemField = new ImportField(LM.findLCPByCompoundName("idItem"));
        ImportKey<?> itemKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Item"),
                LM.findLCPByCompoundName("itemId").getMapping(idItemField));
        keys.add(itemKey);
        props.add(new ImportProperty(idItemField, LM.findLCPByCompoundName("idItem").getMapping(itemKey)));
        fields.add(idItemField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).idItem);

        ImportField captionItemField = new ImportField(LM.findLCPByCompoundName("captionItem"));
        props.add(new ImportProperty(captionItemField, LM.findLCPByCompoundName("captionItem").getMapping(itemKey)));
        fields.add(captionItemField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).captionItem);

        ImportField idItemGroupField = new ImportField(LM.findLCPByCompoundName("idItemGroup"));
        ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("ItemGroup"),
                LM.findLCPByCompoundName("itemGroupId").getMapping(idItemGroupField));
        keys.add(itemGroupKey);
        props.add(new ImportProperty(idItemGroupField, LM.findLCPByCompoundName("itemGroupItem").getMapping(itemKey),
                LM.object(LM.findClassByCompoundName("ItemGroup")).getMapping(itemGroupKey)));
        fields.add(idItemGroupField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).idItemGroup);

        ImportField idUOMField = new ImportField(LM.findLCPByCompoundName("idUOM"));
        ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("UOM"),
                LM.findLCPByCompoundName("UOMId").getMapping(idUOMField));
        keys.add(UOMKey);
        props.add(new ImportProperty(idUOMField, LM.findLCPByCompoundName("idUOM").getMapping(UOMKey)));
        props.add(new ImportProperty(idUOMField, LM.findLCPByCompoundName("UOMItem").getMapping(itemKey),
                LM.object(LM.findClassByCompoundName("UOM")).getMapping(UOMKey)));
        fields.add(idUOMField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).idUOM);

        ImportField nameUOMField = new ImportField(LM.findLCPByCompoundName("nameUOM"));
        props.add(new ImportProperty(nameUOMField, LM.findLCPByCompoundName("nameUOM").getMapping(UOMKey)));
        fields.add(nameUOMField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).nameUOM);

        ImportField shortNameUOMField = new ImportField(LM.findLCPByCompoundName("shortNameUOM"));
        props.add(new ImportProperty(shortNameUOMField, LM.findLCPByCompoundName("shortNameUOM").getMapping(UOMKey)));
        fields.add(shortNameUOMField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).shortNameUOM);

        ImportField idBrandField = new ImportField(LM.findLCPByCompoundName("idBrand"));
        ImportKey<?> brandKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Brand"),
                LM.findLCPByCompoundName("brandId").getMapping(idBrandField));
        keys.add(brandKey);
        props.add(new ImportProperty(idBrandField, LM.findLCPByCompoundName("idBrand").getMapping(brandKey)));
        props.add(new ImportProperty(idBrandField, LM.findLCPByCompoundName("brandItem").getMapping(itemKey),
                LM.object(LM.findClassByCompoundName("Brand")).getMapping(brandKey)));
        fields.add(idBrandField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).idBrand);

        ImportField nameBrandField = new ImportField(LM.findLCPByCompoundName("nameBrand"));
        props.add(new ImportProperty(nameBrandField, LM.findLCPByCompoundName("nameBrand").getMapping(brandKey)));
        fields.add(nameBrandField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).nameBrand);

        ImportField nameCountryField = new ImportField(LM.findLCPByCompoundName("nameCountry"));
        ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Country"),
                LM.findLCPByCompoundName("countryName").getMapping(nameCountryField));
        keys.add(countryKey);
        props.add(new ImportProperty(nameCountryField, LM.findLCPByCompoundName("nameCountry").getMapping(countryKey)));
        props.add(new ImportProperty(nameCountryField, LM.findLCPByCompoundName("countryItem").getMapping(itemKey),
                LM.object(LM.findClassByCompoundName("Country")).getMapping(countryKey)));
        fields.add(nameCountryField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).nameCountry);

        ImportField extIdBarcodeField = new ImportField(LM.findLCPByCompoundName("extIdBarcode"));
        ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Barcode"),
                LM.findLCPByCompoundName(/*"barcodeIdDate"*/"extBarcodeId").getMapping(extIdBarcodeField));
        keys.add(barcodeKey);
        props.add(new ImportProperty(idItemField, LM.findLCPByCompoundName("skuBarcode").getMapping(barcodeKey),
                LM.object(LM.findClassByCompoundName("Item")).getMapping(itemKey)));
        props.add(new ImportProperty(extIdBarcodeField, LM.findLCPByCompoundName("extIdBarcode").getMapping(barcodeKey)));
        fields.add(extIdBarcodeField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).extIdBarcode);

        ImportField idBarcodeField = new ImportField(LM.findLCPByCompoundName("idBarcode"));
        props.add(new ImportProperty(idBarcodeField, LM.findLCPByCompoundName("idBarcode").getMapping(barcodeKey)));
        fields.add(idBarcodeField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).idBarcode);

        ImportField isWeightItemField = new ImportField(LM.findLCPByCompoundName("isWeightItem"));
        props.add(new ImportProperty(isWeightItemField, LM.findLCPByCompoundName("isWeightItem").getMapping(itemKey)));
        fields.add(isWeightItemField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).isWeightItem);

        ImportField netWeightItemField = new ImportField(LM.findLCPByCompoundName("netWeightItem"));
        props.add(new ImportProperty(netWeightItemField, LM.findLCPByCompoundName("netWeightItem").getMapping(itemKey)));
        fields.add(netWeightItemField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).netWeightItem);

        ImportField grossWeightItemField = new ImportField(LM.findLCPByCompoundName("grossWeightItem"));
        props.add(new ImportProperty(grossWeightItemField, LM.findLCPByCompoundName("grossWeightItem").getMapping(itemKey)));
        fields.add(grossWeightItemField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).grossWeightItem);

        ImportField compositionItemField = new ImportField(LM.findLCPByCompoundName("compositionItem"));
        props.add(new ImportProperty(compositionItemField, LM.findLCPByCompoundName("compositionItem").getMapping(itemKey)));
        fields.add(compositionItemField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).compositionItem);

        ImportField dateField = new ImportField(DateClass.instance);
        props.add(new ImportProperty(dateField, LM.findLCPByCompoundName("dataDateBarcode").getMapping(barcodeKey)));
        fields.add(dateField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).date);

        DataObject defaultCountryObject = (DataObject) LM.findLCPByCompoundName("defaultCountry").readClasses(context.getSession());
        ImportField valueVATItemCountryDateField = new ImportField(LM.findLCPByCompoundName("valueVATItemCountryDate"));
        ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Range"),
                LM.findLCPByCompoundName("valueCurrentVATDefaultValue").getMapping(valueVATItemCountryDateField));
        VATKey.skipKey = skipKeys;
        keys.add(VATKey);
        props.add(new ImportProperty(valueVATItemCountryDateField, LM.findLCPByCompoundName("dataVATItemCountryDate").getMapping(itemKey, defaultCountryObject, dateField),
                LM.object(LM.findClassByCompoundName("Range")).getMapping(VATKey)));
        fields.add(valueVATItemCountryDateField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).retailVAT);

        ImportField idWareField = new ImportField(LM.findLCPByCompoundName("idWare"));
        ImportKey<?> wareKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Ware"),
                LM.findLCPByCompoundName("wareId").getMapping(idWareField));
        keys.add(wareKey);
        props.add(new ImportProperty(idWareField, LM.findLCPByCompoundName("idWare").getMapping(wareKey)));
        props.add(new ImportProperty(idWareField, LM.findLCPByCompoundName("wareItem").getMapping(itemKey),
                LM.object(LM.findClassByCompoundName("Ware")).getMapping(wareKey)));
        fields.add(idWareField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).idWare);

        ImportField priceWareField = new ImportField(LM.findLCPByCompoundName("dataWarePriceDate"));
        props.add(new ImportProperty(priceWareField, LM.findLCPByCompoundName("dataWarePriceDate").getMapping(wareKey, dateField)));
        fields.add(priceWareField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).priceWare);

        ImportField vatWareField = new ImportField(LM.findLCPByCompoundName("valueRate"));
        ImportKey<?> rangeKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Range"),
                LM.findLCPByCompoundName("valueCurrentVATDefaultValue").getMapping(vatWareField));
        keys.add(rangeKey);
        props.add(new ImportProperty(vatWareField, LM.findLCPByCompoundName("dataRangeWareDate").getMapping(wareKey, dateField, rangeKey),
                LM.object(LM.findClassByCompoundName("Range")).getMapping(rangeKey)));
        fields.add(vatWareField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).vatWare);

        ImportField idWriteOffRateField = new ImportField(LM.findLCPByCompoundName("idWriteOffRate"));
        ImportKey<?> writeOffRateKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("WriteOffRate"),
                LM.findLCPByCompoundName("writeOffRateId").getMapping(idWriteOffRateField));
        keys.add(writeOffRateKey);
        props.add(new ImportProperty(idWriteOffRateField, LM.findLCPByCompoundName("writeOffRateCountryItem").getMapping(defaultCountryObject, itemKey),
                LM.object(LM.findClassByCompoundName("WriteOffRate")).getMapping(writeOffRateKey)));
        fields.add(idWriteOffRateField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).idWriteOffRate);

        ImportField idRetailCalcPriceListTypeField = new ImportField(LM.findLCPByCompoundName("idCalcPriceListType"));
        ImportKey<?> retailCalcPriceListTypeKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("CalcPriceListType"),
                LM.findLCPByCompoundName("calcPriceListTypeId").getMapping(idRetailCalcPriceListTypeField));
        keys.add(retailCalcPriceListTypeKey);
        props.add(new ImportProperty(idRetailCalcPriceListTypeField, LM.findLCPByCompoundName("idCalcPriceListType").getMapping(retailCalcPriceListTypeKey)));
        fields.add(idRetailCalcPriceListTypeField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add("retail");

        ImportField nameRetailCalcPriceListTypeField = new ImportField(LM.findLCPByCompoundName("namePriceListType"));
        props.add(new ImportProperty(nameRetailCalcPriceListTypeField, LM.findLCPByCompoundName("namePriceListType").getMapping(retailCalcPriceListTypeKey)));
        fields.add(nameRetailCalcPriceListTypeField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add("Розничная надбавка");

        ImportField retailMarkupCalcPriceListTypeField = new ImportField(LM.findLCPByCompoundName("dataMarkupCalcPriceListTypeSku"));
        props.add(new ImportProperty(retailMarkupCalcPriceListTypeField, LM.findLCPByCompoundName("dataMarkupCalcPriceListTypeSku").getMapping(retailCalcPriceListTypeKey, itemKey)));
        fields.add(retailMarkupCalcPriceListTypeField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).retailMarkup);

        ImportField idBaseCalcPriceListTypeField = new ImportField(LM.findLCPByCompoundName("idCalcPriceListType"));
        ImportKey<?> baseCalcPriceListTypeKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("CalcPriceListType"),
                LM.findLCPByCompoundName("calcPriceListTypeId").getMapping(idBaseCalcPriceListTypeField));
        keys.add(baseCalcPriceListTypeKey);
        props.add(new ImportProperty(idBaseCalcPriceListTypeField, LM.findLCPByCompoundName("idCalcPriceListType").getMapping(baseCalcPriceListTypeKey)));
        fields.add(idBaseCalcPriceListTypeField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add("wholesale");

        ImportField nameBaseCalcPriceListTypeField = new ImportField(LM.findLCPByCompoundName("namePriceListType"));
        props.add(new ImportProperty(nameBaseCalcPriceListTypeField, LM.findLCPByCompoundName("namePriceListType").getMapping(baseCalcPriceListTypeKey)));
        fields.add(nameBaseCalcPriceListTypeField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add("Оптовая надбавка");

        ImportField baseMarkupCalcPriceListTypeField = new ImportField(LM.findLCPByCompoundName("dataMarkupCalcPriceListTypeSku"));
        props.add(new ImportProperty(baseMarkupCalcPriceListTypeField, LM.findLCPByCompoundName("dataMarkupCalcPriceListTypeSku").getMapping(baseCalcPriceListTypeKey, itemKey)));
        fields.add(baseMarkupCalcPriceListTypeField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).baseMarkup);

        ImportField extIdBarcodePackField = new ImportField(LM.findLCPByCompoundName("extIdBarcode"));
        ImportKey<?> barcodePackKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Barcode"),
                LM.findLCPByCompoundName(/*"barcodeIdDate"*/"extBarcodeId").getMapping(extIdBarcodePackField));
        keys.add(barcodePackKey);
        props.add(new ImportProperty(dateField, LM.findLCPByCompoundName("dataDateBarcode").getMapping(barcodePackKey)));
        props.add(new ImportProperty(idItemField, LM.findLCPByCompoundName("skuBarcode").getMapping(barcodePackKey),
                LM.object(LM.findClassByCompoundName("Item")).getMapping(itemKey)));
        props.add(new ImportProperty(extIdBarcodePackField, LM.findLCPByCompoundName("extIdBarcode").getMapping(barcodePackKey)));
        props.add(new ImportProperty(extIdBarcodePackField, LM.findLCPByCompoundName("Purchase.packBarcodeSku").getMapping(itemKey),
                LM.object(LM.findClassByCompoundName("Barcode")).getMapping(barcodePackKey)));
        fields.add(extIdBarcodePackField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(valueWithPrefix(itemsList.get(i).idBarcodePack, "P", null));

        ImportField idBarcodePackField = new ImportField(LM.findLCPByCompoundName("idBarcode"));
        props.add(new ImportProperty(idBarcodePackField, LM.findLCPByCompoundName("idBarcode").getMapping(barcodePackKey)));
        props.add(new ImportProperty(extIdBarcodePackField, LM.findLCPByCompoundName("Sale.packBarcodeSku").getMapping(itemKey),
                LM.object(LM.findClassByCompoundName("Barcode")).getMapping(barcodePackKey)));
        fields.add(idBarcodePackField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(null);

        ImportField amountBarcodePackField = new ImportField(LM.findLCPByCompoundName("amountBarcode"));
        props.add(new ImportProperty(amountBarcodePackField, LM.findLCPByCompoundName("amountBarcode").getMapping(barcodePackKey)));
        fields.add(amountBarcodePackField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).amountPack);

        ImportField idManufacturerField = new ImportField(LM.findLCPByCompoundName("idManufacturer"));
        ImportKey<?> manufacturerKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Manufacturer"),
                LM.findLCPByCompoundName("manufacturerId").getMapping(idManufacturerField));
        keys.add(manufacturerKey);
        props.add(new ImportProperty(idManufacturerField, LM.findLCPByCompoundName("idManufacturer").getMapping(manufacturerKey)));
        props.add(new ImportProperty(idManufacturerField, LM.findLCPByCompoundName("manufacturerItem").getMapping(itemKey),
                LM.object(LM.findClassByCompoundName("Manufacturer")).getMapping(manufacturerKey)));
        fields.add(idManufacturerField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).idManufacturer);

        ImportField nameManufacturerField = new ImportField(LM.findLCPByCompoundName("nameManufacturer"));
        props.add(new ImportProperty(nameManufacturerField, LM.findLCPByCompoundName("nameManufacturer").getMapping(manufacturerKey)));
        fields.add(nameManufacturerField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).nameManufacturer);

        ImportField codeCustomsGroupField = new ImportField(LM.findLCPByCompoundName("codeCustomsGroup"));
        ImportKey<?> customsGroupKey = new ImportKey((CustomClass) LM.findClassByCompoundName("CustomsGroup"),
                LM.findLCPByCompoundName("customsGroupCode").getMapping(codeCustomsGroupField));
        keys.add(customsGroupKey);
        props.add(new ImportProperty(codeCustomsGroupField, LM.findLCPByCompoundName("codeCustomsGroup").getMapping(customsGroupKey)));
        props.add(new ImportProperty(codeCustomsGroupField, LM.findLCPByCompoundName("customsGroupCountryItem").getMapping(countryKey, itemKey),
                LM.object(LM.findClassByCompoundName("CustomsGroup")).getMapping(customsGroupKey)));
        fields.add(codeCustomsGroupField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).codeCustomsGroup);

        ImportField nameCustomsZoneField = new ImportField(LM.findLCPByCompoundName("nameCustomsZone"));
        ImportKey<?> customsZoneKey = new ImportKey((CustomClass) LM.findClassByCompoundName("CustomsZone"),
                LM.findLCPByCompoundName("customsZoneName").getMapping(nameCustomsZoneField));
        keys.add(customsZoneKey);
        props.add(new ImportProperty(nameCustomsZoneField, LM.findLCPByCompoundName("nameCustomsZone").getMapping(customsZoneKey)));
        props.add(new ImportProperty(nameCustomsZoneField, LM.findLCPByCompoundName("customsZoneCustomsGroup").getMapping(customsGroupKey),
                LM.object(LM.findClassByCompoundName("CustomsZone")).getMapping(customsZoneKey)));
        fields.add(nameCustomsZoneField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).nameCustomsZone);

        ImportTable table = new ImportTable(fields, data);

        DataSession session = context.createSession();
        session.sql.pushVolatileStats(null);
        IntegrationService service = new IntegrationService(session, table, keys, props);
        service.synchronize(true, false);
        session.apply(context.getBL());
        session.sql.popVolatileStats(null);
        session.close();
    }

    public Boolean showWholesalePrice;

    private void importUserInvoices(List<UserInvoiceDetail> userInvoiceDetailsList, Boolean posted, Integer numberAtATime, boolean skipKeys) throws SQLException, ScriptingErrorLog.SemanticErrorException {

        if (userInvoiceDetailsList != null) {

            if (numberAtATime == null)
                numberAtATime = userInvoiceDetailsList.size();

            for (int start = 0; true; start += numberAtATime) {

                int finish = (start + numberAtATime) < userInvoiceDetailsList.size() ? (start + numberAtATime) : userInvoiceDetailsList.size();
                List<UserInvoiceDetail> dataUserInvoiceDetail = start < finish ? userInvoiceDetailsList.subList(start, finish) : new ArrayList<UserInvoiceDetail>();
                if (dataUserInvoiceDetail.isEmpty())
                    return;

                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                List<List<Object>> data = initData(userInvoiceDetailsList.size());

                ImportField idUserInvoiceField = new ImportField(LM.findLCPByCompoundName("idUserInvoice"));
                ImportKey<?> userInvoiceKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName(posted ? "Purchase.UserInvoicePosted" : "Purchase.UserInvoice"),
                        LM.findLCPByCompoundName("userInvoiceId").getMapping(idUserInvoiceField));
                keys.add(userInvoiceKey);
                props.add(new ImportProperty(idUserInvoiceField, LM.findLCPByCompoundName("idUserInvoice").getMapping(userInvoiceKey)));
                fields.add(idUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idUserInvoice);

                ImportField idUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("idUserInvoiceDetail"));
                ImportKey<?> userInvoiceDetailKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Purchase.UserInvoiceDetail"),
                        LM.findLCPByCompoundName("userInvoiceDetailId").getMapping(idUserInvoiceDetailField));
                keys.add(userInvoiceDetailKey);
                props.add(new ImportProperty(idUserInvoiceDetailField, LM.findLCPByCompoundName("idUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                props.add(new ImportProperty(idUserInvoiceField, LM.findLCPByCompoundName("Purchase.userInvoiceUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(LM.findClassByCompoundName(posted ? "Purchase.UserInvoicePosted" : "Purchase.UserInvoice")).getMapping(userInvoiceKey)));
                fields.add(idUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idUserInvoiceDetail);


                ImportField idCustomerStockField = new ImportField(LM.findLCPByCompoundName("idStock"));
                ImportKey<?> customerStockKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Stock"),
                        LM.findLCPByCompoundName("stockId").getMapping(idCustomerStockField));
                keys.add(customerStockKey);
                props.add(new ImportProperty(idCustomerStockField, LM.findLCPByCompoundName("Purchase.customerUserInvoice").getMapping(userInvoiceKey),
                        LM.findLCPByCompoundName("legalEntityStock").getMapping(customerStockKey)));
                props.add(new ImportProperty(idCustomerStockField, LM.findLCPByCompoundName("Purchase.customerStockUserInvoice").getMapping(userInvoiceKey),
                        LM.object(LM.findClassByCompoundName("Stock")).getMapping(customerStockKey)));
                fields.add(idCustomerStockField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idCustomerStock);


                ImportField idSupplierField = new ImportField(LM.findLCPByCompoundName("idLegalEntity"));
                ImportKey<?> supplierKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("LegalEntity"),
                        LM.findLCPByCompoundName("legalEntityId").getMapping(idSupplierField));
                supplierKey.skipKey = skipKeys;
                keys.add(supplierKey);
                props.add(new ImportProperty(idSupplierField, LM.findLCPByCompoundName("Purchase.supplierUserInvoice").getMapping(userInvoiceKey),
                        LM.object(LM.findClassByCompoundName("LegalEntity")).getMapping(supplierKey)));
                fields.add(idSupplierField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idSupplier);


                ImportField idSupplierStockField = new ImportField(LM.findLCPByCompoundName("idWarehouse"));
                ImportKey<?> supplierWarehouseKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Warehouse"),
                        LM.findLCPByCompoundName("warehouseId").getMapping(idSupplierStockField));
                supplierWarehouseKey.skipKey = skipKeys;
                keys.add(supplierWarehouseKey);
                props.add(new ImportProperty(idSupplierStockField, LM.findLCPByCompoundName("Purchase.supplierStockUserInvoice").getMapping(userInvoiceKey),
                        LM.object(LM.findClassByCompoundName("Stock")).getMapping(supplierWarehouseKey)));
                fields.add(idSupplierStockField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idSupplierStock);


                ImportField numberUserInvoiceField = new ImportField(LM.findLCPByCompoundName("numberObject"));
                props.add(new ImportProperty(numberUserInvoiceField, LM.findLCPByCompoundName("numberObject").getMapping(userInvoiceKey)));
                fields.add(numberUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).number);


                ImportField seriesUserInvoiceField = new ImportField(LM.findLCPByCompoundName("seriesObject"));
                props.add(new ImportProperty(seriesUserInvoiceField, LM.findLCPByCompoundName("seriesObject").getMapping(userInvoiceKey)));
                fields.add(seriesUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).series);


                ImportField createPricingUserInvoiceField = new ImportField(LM.findLCPByCompoundName("Purchase.createPricingUserInvoice"));
                props.add(new ImportProperty(createPricingUserInvoiceField, LM.findLCPByCompoundName("Purchase.createPricingUserInvoice").getMapping(userInvoiceKey)));
                fields.add(createPricingUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).createPricing);


                ImportField createShipmentUserInvoiceField = new ImportField(LM.findLCPByCompoundName("Purchase.createShipmentUserInvoice"));
                props.add(new ImportProperty(createShipmentUserInvoiceField, LM.findLCPByCompoundName("Purchase.createShipmentUserInvoice").getMapping(userInvoiceKey)));
                fields.add(createShipmentUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).createShipment);


                ImportField showManufacturingPriceUserInvoiceField = new ImportField(LM.findLCPByCompoundName("Purchase.showManufacturingPriceUserInvoice"));
                props.add(new ImportProperty(showManufacturingPriceUserInvoiceField, LM.findLCPByCompoundName("Purchase.showManufacturingPriceUserInvoice").getMapping(userInvoiceKey)));
                fields.add(showManufacturingPriceUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).showManufacturingPrice);


                ImportField showWholesalePriceUserInvoiceField = new ImportField(LM.findLCPByCompoundName("Purchase.showWholesalePriceUserInvoice"));
                props.add(new ImportProperty(showWholesalePriceUserInvoiceField, LM.findLCPByCompoundName("Purchase.showWholesalePriceUserInvoice").getMapping(userInvoiceKey)));
                fields.add(showWholesalePriceUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(showWholesalePrice);


                ImportField dateUserInvoiceField = new ImportField(LM.findLCPByCompoundName("Purchase.dateUserInvoice"));
                props.add(new ImportProperty(dateUserInvoiceField, LM.findLCPByCompoundName("Purchase.dateUserInvoice").getMapping(userInvoiceKey)));
                fields.add(dateUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).date);


                ImportField timeUserInvoiceField = new ImportField(TimeClass.instance);
                props.add(new ImportProperty(timeUserInvoiceField, LM.findLCPByCompoundName("Purchase.timeUserInvoice").getMapping(userInvoiceKey)));
                fields.add(timeUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(new Time(12, 0, 0));


                ImportField idItemField = new ImportField(LM.findLCPByCompoundName("idItem"));
                ImportKey<?> itemKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Sku"),
                        LM.findLCPByCompoundName("itemId").getMapping(idItemField));
                keys.add(itemKey);
                props.add(new ImportProperty(idItemField, LM.findLCPByCompoundName("Purchase.skuInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(LM.findClassByCompoundName("Sku")).getMapping(itemKey)));
                fields.add(idItemField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idItem);


                ImportField quantityUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("Purchase.quantityUserInvoiceDetail"));
                props.add(new ImportProperty(quantityUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.quantityUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(quantityUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).quantity);


                ImportField priceUserInvoiceDetail = new ImportField(LM.findLCPByCompoundName("Purchase.priceUserInvoiceDetail"));
                props.add(new ImportProperty(priceUserInvoiceDetail, LM.findLCPByCompoundName("Purchase.priceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(priceUserInvoiceDetail);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).price);

                boolean flag = false;
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    if (dataUserInvoiceDetail.get(i).shipmentSum != null) {
                        flag = true;
                        break;
                    }
                if (flag) {
                    ImportField shipmentSumInvoiceDetail = new ImportField(LM.findLCPByCompoundName("Purchase.shipmentSumInvoiceDetail"));
                    props.add(new ImportProperty(shipmentSumInvoiceDetail, LM.findLCPByCompoundName("Purchase.shipmentSumInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(shipmentSumInvoiceDetail);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).shipmentSum);
                }

                ImportField expiryDateUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("expiryDateUserInvoiceDetail"));
                props.add(new ImportProperty(expiryDateUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.expiryDateUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(expiryDateUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).expiryDate);


                ImportField dataRateExchangeUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("dataRateExchangeUserInvoiceDetail"));
                props.add(new ImportProperty(dataRateExchangeUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.dataRateExchangeUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(dataRateExchangeUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).rateExchange);


                ImportField homePriceUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("homePriceUserInvoiceDetail"));
                props.add(new ImportProperty(homePriceUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.homePriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(homePriceUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).homePrice);


                if (showField(dataUserInvoiceDetail, "priceDuty")) {
                    ImportField priceDutyUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("priceDutyUserInvoiceDetail"));
                    props.add(new ImportProperty(priceDutyUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.priceDutyUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(priceDutyUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).priceDuty);
                }

                if (showField(dataUserInvoiceDetail, "priceCompliance")) {
                    ImportField priceComplianceUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("priceComplianceUserInvoiceDetail"));
                    props.add(new ImportProperty(priceComplianceUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.priceComplianceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(priceComplianceUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).priceCompliance);

                    ImportField showComplianceUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("showComplianceUserInvoice"));
                    props.add(new ImportProperty(showComplianceUserInvoiceDetailField, LM.findLCPByCompoundName("showComplianceUserInvoice").getMapping(userInvoiceKey)));
                    fields.add(showComplianceUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                }

                if (showField(dataUserInvoiceDetail, "priceRegistration")) {
                    ImportField priceRegistrationUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("priceRegistrationUserInvoiceDetail"));
                    props.add(new ImportProperty(priceRegistrationUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.priceRegistrationUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(priceRegistrationUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).priceRegistration);
                }

                if (showField(dataUserInvoiceDetail, "chargeSum")) {
                    ImportField chargeSumUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("chargeSumUserInvoiceDetail"));
                    props.add(new ImportProperty(chargeSumUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.chargeSumUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(chargeSumUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).chargeSum);

                    ImportField showChargePriceUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("showChargePriceUserInvoice"));
                    props.add(new ImportProperty(showChargePriceUserInvoiceDetailField, LM.findLCPByCompoundName("showChargePriceUserInvoice").getMapping(userInvoiceKey)));
                    fields.add(showChargePriceUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                }


                if (showField(dataUserInvoiceDetail, "idBin")) {
                    ImportField binUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("idBin"));
                    ImportKey<?> binKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Bin"),
                            LM.findLCPByCompoundName("binId").getMapping(binUserInvoiceDetailField));
                    keys.add(binKey);
                    props.add(new ImportProperty(binUserInvoiceDetailField, LM.findLCPByCompoundName("idBin").getMapping(binKey)));
                    props.add(new ImportProperty(binUserInvoiceDetailField, LM.findLCPByCompoundName("nameBin").getMapping(binKey)));
                    props.add(new ImportProperty(binUserInvoiceDetailField, LM.findLCPByCompoundName("binUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                            LM.object(LM.findClassByCompoundName("Bin")).getMapping(binKey)));
                    fields.add(binUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).idBin);

                    ImportField showBinUserInvoiceField = new ImportField(LM.findLCPByCompoundName("showBinUserInvoice"));
                    props.add(new ImportProperty(showBinUserInvoiceField, LM.findLCPByCompoundName("showBinUserInvoice").getMapping(userInvoiceKey)));
                    fields.add(showBinUserInvoiceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                }


                ImportField chargePriceUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("Purchase.chargePriceUserInvoiceDetail"));
                props.add(new ImportProperty(chargePriceUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.chargePriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(chargePriceUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).chargePrice);


                ImportField manufacturingPriceUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("Purchase.manufacturingPriceUserInvoiceDetail"));
                props.add(new ImportProperty(manufacturingPriceUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.manufacturingPriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(manufacturingPriceUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).manufacturingPrice);


                ImportField wholesalePriceUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("Purchase.wholesalePriceUserInvoiceDetail"));
                props.add(new ImportProperty(wholesalePriceUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.wholesalePriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(wholesalePriceUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).wholesalePrice);


                ImportField wholesaleMarkupUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("Purchase.wholesaleMarkupUserInvoiceDetail"));
                props.add(new ImportProperty(wholesaleMarkupUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.wholesaleMarkupUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(wholesaleMarkupUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).wholesaleMarkup);


                ImportField retailPriceUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("Purchase.retailPriceUserInvoiceDetail"));
                props.add(new ImportProperty(retailPriceUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.retailPriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(retailPriceUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).retailPrice);


                ImportField retailMarkupUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("Purchase.retailMarkupUserInvoiceDetail"));
                props.add(new ImportProperty(retailMarkupUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.retailMarkupUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(retailMarkupUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).retailMarkup);


                ImportField certificateTextUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("certificateTextUserInvoiceDetail"));
                props.add(new ImportProperty(certificateTextUserInvoiceDetailField, LM.findLCPByCompoundName("certificateTextUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(certificateTextUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).certificateText);


                ImportField skipCreateWareUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("skipCreateWareUserInvoiceDetail"));
                props.add(new ImportProperty(skipCreateWareUserInvoiceDetailField, LM.findLCPByCompoundName("skipCreateWareUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(skipCreateWareUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(true);


                ImportField userContractSkuField = new ImportField(LM.findLCPByCompoundName("idUserContractSku"));
                ImportKey<?> userContractSkuKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("UserContractSku"),
                        LM.findLCPByCompoundName("userContractSkuId").getMapping(userContractSkuField));
                keys.add(userContractSkuKey);
                props.add(new ImportProperty(userContractSkuField, LM.findLCPByCompoundName("Purchase.contractSkuInvoice").getMapping(userInvoiceKey),
                        LM.object(LM.findClassByCompoundName("Contract")).getMapping(userContractSkuKey)));
                fields.add(userContractSkuField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idContract);


                ImportField numberDeclarationField = new ImportField(LM.findLCPByCompoundName("numberObject"));
                ImportKey<?> declarationKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Declaration"),
                        LM.findLCPByCompoundName("declarationId").getMapping(numberDeclarationField));
                keys.add(declarationKey);
                props.add(new ImportProperty(numberDeclarationField, LM.findLCPByCompoundName("numberObject").getMapping(declarationKey)));
                props.add(new ImportProperty(numberDeclarationField, LM.findLCPByCompoundName("idDeclaration").getMapping(declarationKey)));
                props.add(new ImportProperty(numberDeclarationField, LM.findLCPByCompoundName("declarationUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(LM.findClassByCompoundName("Declaration")).getMapping(declarationKey)));
                fields.add(numberDeclarationField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).numberDeclaration);


                ImportField dateDeclarationField = new ImportField(LM.findLCPByCompoundName("dateDeclaration"));
                props.add(new ImportProperty(dateDeclarationField, LM.findLCPByCompoundName("dateDeclaration").getMapping(declarationKey)));
                fields.add(dateDeclarationField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).dateDeclaration);


                ImportField numberComplianceField = new ImportField(LM.findLCPByCompoundName("numberObject"));
                ImportKey<?> complianceKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Compliance"),
                        LM.findLCPByCompoundName("complianceId").getMapping(numberComplianceField));
                keys.add(complianceKey);
                props.add(new ImportProperty(numberComplianceField, LM.findLCPByCompoundName("numberObject").getMapping(complianceKey)));
                props.add(new ImportProperty(numberComplianceField, LM.findLCPByCompoundName("idCompliance").getMapping(complianceKey)));
                props.add(new ImportProperty(numberComplianceField, LM.findLCPByCompoundName("complianceUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(LM.findClassByCompoundName("Compliance")).getMapping(complianceKey)));
                fields.add(numberComplianceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).numberCompliance);


                ImportField fromDateComplianceField = new ImportField(LM.findLCPByCompoundName("fromDateCompliance"));
                props.add(new ImportProperty(fromDateComplianceField, LM.findLCPByCompoundName("dateCompliance").getMapping(complianceKey)));
                props.add(new ImportProperty(fromDateComplianceField, LM.findLCPByCompoundName("fromDateCompliance").getMapping(complianceKey)));
                fields.add(fromDateComplianceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).fromDateCompliance);


                ImportField toDateComplianceField = new ImportField(LM.findLCPByCompoundName("toDateCompliance"));
                props.add(new ImportProperty(toDateComplianceField, LM.findLCPByCompoundName("toDateCompliance").getMapping(complianceKey)));
                fields.add(toDateComplianceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).toDateCompliance);


                ImportField isHomeCurrencyUserInvoiceField = new ImportField(LM.findLCPByCompoundName("isHomeCurrencyUserInvoice"));
                props.add(new ImportProperty(isHomeCurrencyUserInvoiceField, LM.findLCPByCompoundName("isHomeCurrencyUserInvoice").getMapping(userInvoiceKey)));
                fields.add(isHomeCurrencyUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).isHomeCurrency);


                ImportField showDeclarationUserInvoiceField = new ImportField(LM.findLCPByCompoundName("showDeclarationUserInvoice"));
                props.add(new ImportProperty(showDeclarationUserInvoiceField, LM.findLCPByCompoundName("showDeclarationUserInvoice").getMapping(userInvoiceKey)));
                fields.add(showDeclarationUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).showDeclaration);


                ImportField shortNameCurrencyField = new ImportField(LM.findLCPByCompoundName("shortNameCurrency"));
                ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Currency"),
                        LM.findLCPByCompoundName("currencyShortName").getMapping(shortNameCurrencyField));
                keys.add(currencyKey);
                props.add(new ImportProperty(shortNameCurrencyField, LM.findLCPByCompoundName("Purchase.currencyUserInvoice").getMapping(userInvoiceKey),
                        LM.object(LM.findClassByCompoundName("Currency")).getMapping(currencyKey)));
                fields.add(shortNameCurrencyField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).shortNameCurrency);


                ImportField codeCustomsGroupField = new ImportField(LM.findLCPByCompoundName("codeCustomsGroup"));
                ImportKey<?> customsGroupKey = new ImportKey((CustomClass) LM.findClassByCompoundName("CustomsGroup"),
                        LM.findLCPByCompoundName("customsGroupCode").getMapping(codeCustomsGroupField));
                keys.add(customsGroupKey);
                props.add(new ImportProperty(codeCustomsGroupField, LM.findLCPByCompoundName("customsGroupUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(LM.findClassByCompoundName("CustomsGroup")).getMapping(customsGroupKey)));
                fields.add(codeCustomsGroupField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).codeCustomsGroup);


                ImportField valueVATUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundName("Purchase.valueVATUserInvoiceDetail"));
                ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Range"),
                        LM.findLCPByCompoundName("valueCurrentVATDefaultValue").getMapping(valueVATUserInvoiceDetailField));
                VATKey.skipKey = skipKeys;
                keys.add(VATKey);
                props.add(new ImportProperty(valueVATUserInvoiceDetailField, LM.findLCPByCompoundName("Purchase.VATUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(LM.findClassByCompoundName("Range")).getMapping(VATKey)));
                fields.add(valueVATUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).retailVAT);


                ImportTable table = new ImportTable(fields, data);

                DataSession session = context.createSession();
                session.sql.pushVolatileStats(null);
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context.getBL());
                session.sql.popVolatileStats(null);
                session.close();
            }
        }
    }

    private void importPriceListStores(List<PriceListStore> priceListStoresList, Integer numberAtATime) throws SQLException, ScriptingErrorLog.SemanticErrorException {

        if (priceListStoresList != null) {

            if (numberAtATime == null)
                numberAtATime = priceListStoresList.size();

            for (int start = 0; true; start += numberAtATime) {

                int finish = (start + numberAtATime) < priceListStoresList.size() ? (start + numberAtATime) : priceListStoresList.size();
                List<PriceListStore> dataPriceListStores = start < finish ? priceListStoresList.subList(start, finish) : new ArrayList<PriceListStore>();
                if (dataPriceListStores.isEmpty())
                    return;

                DataSession session = context.createSession();
                session.sql.pushVolatileStats(null);

                ObjectValue dataPriceListTypeObject = LM.findLCPByCompoundName("dataPriceListTypeId").readClasses(session, new DataObject("Coordinated", StringClass.get(100)));
                if (dataPriceListTypeObject instanceof NullValue) {
                    dataPriceListTypeObject = session.addObject((ConcreteCustomClass) LM.findClassByCompoundName("DataPriceListType"));
                    Object defaultCurrency = LM.findLCPByCompoundName("currencyShortName").read(session, new DataObject("BLR", StringClass.get(3)));
                    LM.findLCPByCompoundName("namePriceListType").change("Поставщика (согласованная)", session, (DataObject) dataPriceListTypeObject);
                    LM.findLCPByCompoundName("currencyDataPriceListType").change(defaultCurrency, session, (DataObject) dataPriceListTypeObject);
                    LM.findLCPByCompoundName("idDataPriceListType").change("Coordinated", session, (DataObject) dataPriceListTypeObject);
                }

                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                List<List<Object>> data = initData(priceListStoresList.size());

                ImportField idItemField = new ImportField(LM.findLCPByCompoundName("idItem"));
                ImportField idUserPriceListField = new ImportField(LM.findLCPByCompoundName("idUserPriceList"));
                ImportKey<?> itemKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Item"),
                        LM.findLCPByCompoundName("itemId").getMapping(idItemField));
                keys.add(itemKey);
                ImportKey<?> userPriceListDetailKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("UserPriceListDetail"),
                        LM.findLCPByCompoundName("userPriceListDetailIdSkuIdUserPriceList").getMapping(idItemField, idUserPriceListField));
                keys.add(userPriceListDetailKey);
                ImportKey<?> userPriceListKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("UserPriceList"),
                        LM.findLCPByCompoundName("userPriceListId").getMapping(idUserPriceListField));
                keys.add(userPriceListKey);
                props.add(new ImportProperty(idItemField, LM.findLCPByCompoundName("skuUserPriceListDetail").getMapping(userPriceListDetailKey),
                        LM.object(LM.findClassByCompoundName("Item")).getMapping(itemKey)));
                props.add(new ImportProperty(idUserPriceListField, LM.findLCPByCompoundName("idUserPriceList").getMapping(userPriceListKey)));
                props.add(new ImportProperty(idUserPriceListField, LM.findLCPByCompoundName("userPriceListUserPriceListDetail").getMapping(userPriceListDetailKey),
                        LM.object(LM.findClassByCompoundName("UserPriceList")).getMapping(userPriceListKey)));
                fields.add(idItemField);
                fields.add(idUserPriceListField);
                for (int i = 0; i < priceListStoresList.size(); i++) {
                    data.get(i).add(priceListStoresList.get(i).idItem);
                    data.get(i).add(priceListStoresList.get(i).idUserPriceList);
                }

                ImportField idLegalEntityField = new ImportField(LM.findLCPByCompoundName("idLegalEntity"));
                ImportKey<?> legalEntityKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("LegalEntity"),
                        LM.findLCPByCompoundName("legalEntityId").getMapping(idLegalEntityField));
                keys.add(legalEntityKey);
                props.add(new ImportProperty(idLegalEntityField, LM.findLCPByCompoundName("companyUserPriceList").getMapping(userPriceListKey),
                        LM.object(LM.findClassByCompoundName("LegalEntity")).getMapping(legalEntityKey)));
                fields.add(idLegalEntityField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(priceListStoresList.get(i).idSupplier);

                ImportField idDepartmentStoreField = new ImportField(LM.findLCPByCompoundName("idDepartmentStore"));
                ImportKey<?> departmentStoreKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("DepartmentStore"),
                        LM.findLCPByCompoundName("departmentStoreId").getMapping(idDepartmentStoreField));
                keys.add(departmentStoreKey);
                fields.add(idDepartmentStoreField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(priceListStoresList.get(i).idDepartmentStore);

                ImportField shortNameCurrencyField = new ImportField(LM.findLCPByCompoundName("shortNameCurrency"));
                ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Currency"),
                        LM.findLCPByCompoundName("currencyShortName").getMapping(shortNameCurrencyField));
                keys.add(currencyKey);
                props.add(new ImportProperty(shortNameCurrencyField, LM.findLCPByCompoundName("currencyUserPriceList").getMapping(userPriceListKey),
                        LM.object(LM.findClassByCompoundName("Currency")).getMapping(currencyKey)));
                fields.add(shortNameCurrencyField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(priceListStoresList.get(i).shortNameCurrency);

                ImportField pricePriceListDetailField = new ImportField(LM.findLCPByCompoundName("pricePriceListDetailDataPriceListType"));
                props.add(new ImportProperty(pricePriceListDetailField, LM.findLCPByCompoundName("priceUserPriceListDetailDataPriceListType").getMapping(userPriceListDetailKey, dataPriceListTypeObject)));
                fields.add(pricePriceListDetailField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(priceListStoresList.get(i).pricePriceListDetail);

                ImportField inPriceListPriceListTypeField = new ImportField(LM.findLCPByCompoundName("inPriceListDataPriceListType"));
                props.add(new ImportProperty(inPriceListPriceListTypeField, LM.findLCPByCompoundName("inPriceListDataPriceListType").getMapping(userPriceListKey, dataPriceListTypeObject)));
                fields.add(inPriceListPriceListTypeField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(priceListStoresList.get(i).inPriceList);

                ImportField inPriceListStockField = new ImportField(LM.findLCPByCompoundName("inPriceListStock"));
                props.add(new ImportProperty(inPriceListStockField, LM.findLCPByCompoundName("inPriceListStock").getMapping(userPriceListKey, departmentStoreKey)));
                fields.add(inPriceListStockField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(priceListStoresList.get(i).inPriceListStock);

                ImportTable table = new ImportTable(fields, data);

                IntegrationService service = new IntegrationService(session, table, Arrays.asList(userPriceListKey,
                        departmentStoreKey, userPriceListDetailKey, itemKey, legalEntityKey, currencyKey), props);
                service.synchronize(true, false);
                session.apply(context.getBL());
                session.sql.popVolatileStats(null);
                session.close();
            }
        }
    }

    private void importPriceListSuppliers(List<PriceListSupplier> priceListSuppliersList, Integer numberAtATime) throws SQLException, ScriptingErrorLog.SemanticErrorException {

        if (priceListSuppliersList != null) {

            if (numberAtATime == null)
                numberAtATime = priceListSuppliersList.size();

            for (int start = 0; true; start += numberAtATime) {

                int finish = (start + numberAtATime) < priceListSuppliersList.size() ? (start + numberAtATime) : priceListSuppliersList.size();
                List<PriceListSupplier> dataPriceListSuppliers = start < finish ? priceListSuppliersList.subList(start, finish) : new ArrayList<PriceListSupplier>();
                if (dataPriceListSuppliers.isEmpty())
                    return;

                DataSession session = context.createSession();
                session.sql.pushVolatileStats(null);

                ObjectValue dataPriceListTypeObject = LM.findLCPByCompoundName("dataPriceListTypeId").readClasses(session, new DataObject("Offered", StringClass.get(100)));
                if (dataPriceListTypeObject instanceof NullValue) {
                    dataPriceListTypeObject = session.addObject((ConcreteCustomClass) LM.findClassByCompoundName("DataPriceListType"));
                    Object defaultCurrency = LM.findLCPByCompoundName("currencyShortName").read(session, new DataObject("BLR", StringClass.get(3)));
                    LM.findLCPByCompoundName("namePriceListType").change("Поставщика (предлагаемая)", session, (DataObject) dataPriceListTypeObject);
                    LM.findLCPByCompoundName("currencyDataPriceListType").change(defaultCurrency, session, (DataObject) dataPriceListTypeObject);
                    LM.findLCPByCompoundName("idDataPriceListType").change("Offered", session, (DataObject) dataPriceListTypeObject);
                }

                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                List<List<Object>> data = initData(priceListSuppliersList.size());

                ImportField idItemField = new ImportField(LM.findLCPByCompoundName("idItem"));
                ImportField idUserPriceListField = new ImportField(LM.findLCPByCompoundName("idUserPriceList"));
                ImportKey<?> itemKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Item"),
                        LM.findLCPByCompoundName("itemId").getMapping(idItemField));
                keys.add(itemKey);
                ImportKey<?> userPriceListDetailKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("UserPriceListDetail"),
                        LM.findLCPByCompoundName("userPriceListDetailIdSkuIdUserPriceList").getMapping(idItemField, idUserPriceListField));
                keys.add(userPriceListDetailKey);
                ImportKey<?> userPriceListKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("UserPriceList"),
                        LM.findLCPByCompoundName("userPriceListId").getMapping(idUserPriceListField));
                keys.add(userPriceListKey);
                props.add(new ImportProperty(idItemField, LM.findLCPByCompoundName("skuUserPriceListDetail").getMapping(userPriceListDetailKey),
                        LM.object(LM.findClassByCompoundName("Item")).getMapping(itemKey)));
                props.add(new ImportProperty(idUserPriceListField, LM.findLCPByCompoundName("idUserPriceList").getMapping(userPriceListKey)));
                props.add(new ImportProperty(idUserPriceListField, LM.findLCPByCompoundName("userPriceListUserPriceListDetail").getMapping(userPriceListDetailKey),
                        LM.object(LM.findClassByCompoundName("UserPriceList")).getMapping(userPriceListKey)));
                fields.add(idItemField);
                fields.add(idUserPriceListField);
                for (int i = 0; i < priceListSuppliersList.size(); i++) {
                    data.get(i).add(priceListSuppliersList.get(i).idItem);
                    data.get(i).add(priceListSuppliersList.get(i).idUserPriceList);
                }

                ImportField idLegalEntityField = new ImportField(LM.findLCPByCompoundName("idLegalEntity"));
                ImportKey<?> legalEntityKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("LegalEntity"),
                        LM.findLCPByCompoundName("legalEntityId").getMapping(idLegalEntityField));
                keys.add(legalEntityKey);
                props.add(new ImportProperty(idLegalEntityField, LM.findLCPByCompoundName("companyUserPriceList").getMapping(userPriceListKey),
                        LM.object(LM.findClassByCompoundName("LegalEntity")).getMapping(legalEntityKey)));
                fields.add(idLegalEntityField);
                for (int i = 0; i < priceListSuppliersList.size(); i++)
                    data.get(i).add(priceListSuppliersList.get(i).idSupplier);

                ImportField shortNameCurrencyField = new ImportField(LM.findLCPByCompoundName("shortNameCurrency"));
                ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Currency"),
                        LM.findLCPByCompoundName("currencyShortName").getMapping(shortNameCurrencyField));
                keys.add(currencyKey);
                props.add(new ImportProperty(shortNameCurrencyField, LM.findLCPByCompoundName("currencyUserPriceList").getMapping(userPriceListKey),
                        LM.object(LM.findClassByCompoundName("Currency")).getMapping(currencyKey)));
                fields.add(shortNameCurrencyField);
                for (int i = 0; i < priceListSuppliersList.size(); i++)
                    data.get(i).add(priceListSuppliersList.get(i).shortNameCurrency);

                ImportField pricePriceListDetailField = new ImportField(LM.findLCPByCompoundName("pricePriceListDetailDataPriceListType"));
                props.add(new ImportProperty(pricePriceListDetailField, LM.findLCPByCompoundName("priceUserPriceListDetailDataPriceListType").getMapping(userPriceListDetailKey, dataPriceListTypeObject)));
                fields.add(pricePriceListDetailField);
                for (int i = 0; i < priceListSuppliersList.size(); i++)
                    data.get(i).add(priceListSuppliersList.get(i).pricePriceListDetail);

                ImportField inPriceListPriceListTypeField = new ImportField(LM.findLCPByCompoundName("inPriceListDataPriceListType"));
                props.add(new ImportProperty(inPriceListPriceListTypeField, LM.findLCPByCompoundName("inPriceListDataPriceListType").getMapping(userPriceListKey, dataPriceListTypeObject)));
                fields.add(inPriceListPriceListTypeField);
                for (int i = 0; i < priceListSuppliersList.size(); i++)
                    data.get(i).add(priceListSuppliersList.get(i).inPriceList);

                ImportField allStocksUserPriceListField = new ImportField(LM.findLCPByCompoundName("allStocksUserPriceList"));
                props.add(new ImportProperty(allStocksUserPriceListField, LM.findLCPByCompoundName("allStocksUserPriceList").getMapping(userPriceListKey)));
                fields.add(allStocksUserPriceListField);
                for (int i = 0; i < priceListSuppliersList.size(); i++)
                    data.get(i).add(priceListSuppliersList.get(i).inPriceList);

                ImportTable table = new ImportTable(fields, data);

                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context.getBL());
                session.sql.popVolatileStats(null);
                session.close();
            }
        }
    }

    private void importLegalEntities(List<LegalEntity> legalEntitiesList) throws SQLException, ScriptingErrorLog.SemanticErrorException {

        try {
            if (legalEntitiesList != null) {

                DataObject defaultDate = new DataObject(new java.sql.Date(2001 - 1900, 0, 01), DateClass.instance);

                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                List<List<Object>> data = initData(legalEntitiesList.size());

                ImportField numberAccountField = new ImportField(LM.findLCPByCompoundName("Bank.numberAccount"));
                ImportKey<?> accountKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Bank.Account"),
                        LM.findLCPByCompoundName("Bank.accountNumber").getMapping(numberAccountField));
                keys.add(accountKey);
                props.add(new ImportProperty(numberAccountField, LM.findLCPByCompoundName("Bank.numberAccount").getMapping(accountKey)));
                fields.add(numberAccountField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).numberAccount);

                ImportField idLegalEntityField = new ImportField(LM.findLCPByCompoundName("idLegalEntity"));
                ImportKey<?> legalEntityKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("LegalEntity"),
                        LM.findLCPByCompoundName("legalEntityId").getMapping(idLegalEntityField));
                keys.add(legalEntityKey);
                props.add(new ImportProperty(idLegalEntityField, LM.findLCPByCompoundName("idLegalEntity").getMapping(legalEntityKey)));
                props.add(new ImportProperty(idLegalEntityField, LM.findLCPByCompoundName("Bank.legalEntityAccount").getMapping(accountKey),
                        LM.object(LM.findClassByCompoundName("LegalEntity")).getMapping(legalEntityKey)));
                fields.add(idLegalEntityField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).idLegalEntity);

                ImportField nameLegalEntityField = new ImportField(LM.findLCPByCompoundName("nameLegalEntity"));
                props.add(new ImportProperty(nameLegalEntityField, LM.findLCPByCompoundName("nameLegalEntity").getMapping(legalEntityKey)));
                props.add(new ImportProperty(nameLegalEntityField, LM.findLCPByCompoundName("fullNameLegalEntity").getMapping(legalEntityKey)));
                fields.add(nameLegalEntityField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).nameLegalEntity);

                ImportField addressLegalEntityField = new ImportField(LM.findLCPByCompoundName("addressLegalEntity"));
                props.add(new ImportProperty(addressLegalEntityField, LM.findLCPByCompoundName("dataAddressLegalEntityDate").getMapping(legalEntityKey, defaultDate)));
                fields.add(addressLegalEntityField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).addressLegalEntity);

                ImportField unpLegalEntityField = new ImportField(LM.findLCPByCompoundName("UNPLegalEntity"));
                props.add(new ImportProperty(unpLegalEntityField, LM.findLCPByCompoundName("UNPLegalEntity").getMapping(legalEntityKey)));
                fields.add(unpLegalEntityField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).unpLegalEntity);

                ImportField okpoLegalEntityField = new ImportField(LM.findLCPByCompoundName("OKPOLegalEntity"));
                props.add(new ImportProperty(okpoLegalEntityField, LM.findLCPByCompoundName("OKPOLegalEntity").getMapping(legalEntityKey)));
                fields.add(okpoLegalEntityField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).okpoLegalEntity);

                ImportField phoneLegalEntityField = new ImportField(LM.findLCPByCompoundName("dataPhoneLegalEntityDate"));
                props.add(new ImportProperty(phoneLegalEntityField, LM.findLCPByCompoundName("dataPhoneLegalEntityDate").getMapping(legalEntityKey, defaultDate)));
                fields.add(phoneLegalEntityField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).phoneLegalEntity);

                ImportField emailLegalEntityField = new ImportField(LM.findLCPByCompoundName("emailLegalEntity"));
                props.add(new ImportProperty(emailLegalEntityField, LM.findLCPByCompoundName("emailLegalEntity").getMapping(legalEntityKey)));
                fields.add(emailLegalEntityField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).emailLegalEntity);

                ImportField isSupplierLegalEntityField = new ImportField(LM.findLCPByCompoundName("isSupplierLegalEntity"));
                props.add(new ImportProperty(isSupplierLegalEntityField, LM.findLCPByCompoundName("isSupplierLegalEntity").getMapping(legalEntityKey)));
                fields.add(isSupplierLegalEntityField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).isSupplierLegalEntity);

                ImportField isCompanyLegalEntityField = new ImportField(LM.findLCPByCompoundName("isCompanyLegalEntity"));
                props.add(new ImportProperty(isCompanyLegalEntityField, LM.findLCPByCompoundName("isCompanyLegalEntity").getMapping(legalEntityKey)));
                fields.add(isCompanyLegalEntityField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).isCompanyLegalEntity);

                ImportField isCustomerLegalEntityField = new ImportField(LM.findLCPByCompoundName("isCustomerLegalEntity"));
                props.add(new ImportProperty(isCustomerLegalEntityField, LM.findLCPByCompoundName("isCustomerLegalEntity").getMapping(legalEntityKey)));
                fields.add(isCustomerLegalEntityField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).isCustomerLegalEntity);

                ImportField shortNameOwnershipField = new ImportField(LM.findLCPByCompoundName("shortNameOwnership"));
                ImportKey<?> ownershipKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Ownership"),
                        LM.findLCPByCompoundName("shortNameToOwnership").getMapping(shortNameOwnershipField));
                keys.add(ownershipKey);
                props.add(new ImportProperty(shortNameOwnershipField, LM.findLCPByCompoundName("shortNameOwnership").getMapping(ownershipKey)));
                props.add(new ImportProperty(shortNameOwnershipField, LM.findLCPByCompoundName("ownershipLegalEntity").getMapping(legalEntityKey),
                        LM.object(LM.findClassByCompoundName("Ownership")).getMapping(ownershipKey)));
                fields.add(shortNameOwnershipField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).shortNameOwnership);

                ImportField nameOwnershipField = new ImportField(LM.findLCPByCompoundName("nameOwnership"));
                props.add(new ImportProperty(nameOwnershipField, LM.findLCPByCompoundName("nameOwnership").getMapping(ownershipKey)));
                fields.add(nameOwnershipField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).nameOwnership);

                ImportField idChainStoresField = new ImportField(LM.findLCPByCompoundName("idChainStores"));
                ImportKey<?> chainStoresKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("ChainStores"),
                        LM.findLCPByCompoundName("chainStoresId").getMapping(idChainStoresField));
                keys.add(chainStoresKey);
                props.add(new ImportProperty(idChainStoresField, LM.findLCPByCompoundName("idChainStores").getMapping(chainStoresKey)));
                fields.add(idChainStoresField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).idChainStores);

                ImportField nameChainStoresField = new ImportField(LM.findLCPByCompoundName("nameChainStores"));
                props.add(new ImportProperty(nameChainStoresField, LM.findLCPByCompoundName("nameChainStores").getMapping(chainStoresKey)));
                fields.add(nameChainStoresField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).nameChainStores);

                ImportField idBankField = new ImportField(LM.findLCPByCompoundName("idBank"));
                ImportKey<?> bankKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Bank"),
                        LM.findLCPByCompoundName("bankId").getMapping(idBankField));
                keys.add(bankKey);
                props.add(new ImportProperty(idBankField, LM.findLCPByCompoundName("Bank.bankAccount").getMapping(accountKey),
                        LM.object(LM.findClassByCompoundName("Bank")).getMapping(bankKey)));
                fields.add(idBankField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).idBank);

                ImportField nameCountryField = new ImportField(LM.findLCPByCompoundName("nameCountry"));
                ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Country"),
                        LM.findLCPByCompoundName("countryName").getMapping(nameCountryField));
                keys.add(countryKey);
                props.add(new ImportProperty(nameCountryField, LM.findLCPByCompoundName("nameCountry").getMapping(countryKey)));
                props.add(new ImportProperty(nameCountryField, LM.findLCPByCompoundName("countryLegalEntity").getMapping(legalEntityKey),
                        LM.object(LM.findClassByCompoundName("Country")).getMapping(countryKey)));
                fields.add(nameCountryField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).nameCountry);

                ImportField shortNameCurrencyField = new ImportField(LM.findLCPByCompoundName("shortNameCurrency"));
                ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Currency"),
                        LM.findLCPByCompoundName("currencyShortName").getMapping(shortNameCurrencyField));
                keys.add(currencyKey);
                props.add(new ImportProperty(shortNameCurrencyField, LM.findLCPByCompoundName("Bank.currencyAccount").getMapping(accountKey),
                        LM.object(LM.findClassByCompoundName("Currency")).getMapping(currencyKey)));
                fields.add(shortNameCurrencyField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add("BLR");

                ImportTable table = new ImportTable(fields, data);

                DataSession session = context.createSession();
                session.sql.pushVolatileStats(null);
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context.getBL());
                session.sql.popVolatileStats(null);
                session.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void importEmployees(List<Employee> employeesList) throws SQLException, ScriptingErrorLog.SemanticErrorException {

        try {
            if (employeesList != null) {

                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                List<List<Object>> data = initData(employeesList.size());

                ImportField idEmployeeField = new ImportField(LM.findLCPByCompoundName("idEmployee"));
                ImportKey<?> employeeKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Employee"),
                        LM.findLCPByCompoundName("employeeId").getMapping(idEmployeeField));
                keys.add(employeeKey);
                props.add(new ImportProperty(idEmployeeField, LM.findLCPByCompoundName("idEmployee").getMapping(employeeKey)));
                fields.add(idEmployeeField);
                for (int i = 0; i < employeesList.size(); i++)
                    data.get(i).add(employeesList.get(i).idEmployee);

                ImportField firstNameEmployeeField = new ImportField(LM.findLCPByCompoundName("firstNameContact"));
                props.add(new ImportProperty(firstNameEmployeeField, LM.findLCPByCompoundName("firstNameContact").getMapping(employeeKey)));
                fields.add(firstNameEmployeeField);
                for (int i = 0; i < employeesList.size(); i++)
                    data.get(i).add(employeesList.get(i).firstNameEmployee);

                ImportField lastNameEmployeeField = new ImportField(LM.findLCPByCompoundName("lastNameContact"));
                props.add(new ImportProperty(lastNameEmployeeField, LM.findLCPByCompoundName("lastNameContact").getMapping(employeeKey)));
                fields.add(lastNameEmployeeField);
                for (int i = 0; i < employeesList.size(); i++)
                    data.get(i).add(employeesList.get(i).lastNameEmployee);

                ImportField idPositionField = new ImportField(LM.findLCPByCompoundName("idPosition"));
                ImportKey<?> positionKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Position"),
                        LM.findLCPByCompoundName("positionId").getMapping(idPositionField));
                keys.add(positionKey);
                props.add(new ImportProperty(idPositionField, LM.findLCPByCompoundName("idPosition").getMapping(positionKey)));
                props.add(new ImportProperty(idPositionField, LM.findLCPByCompoundName("positionEmployee").getMapping(employeeKey),
                        LM.object(LM.findClassByCompoundName("Position")).getMapping(positionKey)));
                fields.add(idPositionField);
                for (int i = 0; i < employeesList.size(); i++)
                    data.get(i).add(employeesList.get(i).idPosition);

                ImportField namePositionField = new ImportField(LM.findLCPByCompoundName("namePosition"));
                props.add(new ImportProperty(namePositionField, LM.findLCPByCompoundName("namePosition").getMapping(positionKey)));
                fields.add(namePositionField);
                for (int i = 0; i < employeesList.size(); i++)
                    data.get(i).add(employeesList.get(i).idPosition);

                ImportTable table = new ImportTable(fields, data);

                DataSession session = context.createSession();
                session.sql.pushVolatileStats(null);
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context.getBL());
                session.sql.popVolatileStats(null);
                session.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void importWarehouseGroups(List<WarehouseGroup> warehouseGroupsList) throws SQLException, ScriptingErrorLog.SemanticErrorException {

        try {
            if (warehouseGroupsList != null) {

                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                List<List<Object>> data = initData(warehouseGroupsList.size());

                ImportField idWarehouseGroupField = new ImportField(LM.findLCPByCompoundName("idWarehouseGroup"));
                ImportKey<?> warehouseGroupKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("WarehouseGroup"),
                        LM.findLCPByCompoundName("warehouseGroupId").getMapping(idWarehouseGroupField));
                keys.add(warehouseGroupKey);
                props.add(new ImportProperty(idWarehouseGroupField, LM.findLCPByCompoundName("idWarehouseGroup").getMapping(warehouseGroupKey)));
                fields.add(idWarehouseGroupField);
                for (int i = 0; i < warehouseGroupsList.size(); i++)
                    data.get(i).add(warehouseGroupsList.get(i).idWarehouseGroup);

                ImportField nameWarehouseGroupField = new ImportField(LM.findLCPByCompoundName("nameWarehouseGroup"));
                props.add(new ImportProperty(nameWarehouseGroupField, LM.findLCPByCompoundName("nameWarehouseGroup").getMapping(warehouseGroupKey)));
                fields.add(nameWarehouseGroupField);
                for (int i = 0; i < warehouseGroupsList.size(); i++)
                    data.get(i).add(warehouseGroupsList.get(i).nameWarehouseGroup);

                ImportTable table = new ImportTable(fields, data);

                DataSession session = context.createSession();
                session.sql.pushVolatileStats(null);
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context.getBL());
                session.sql.popVolatileStats(null);
                session.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void importWarehouses(List<Warehouse> warehousesList) throws SQLException, ScriptingErrorLog.SemanticErrorException {

        try {
            if (warehousesList != null) {

                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                List<List<Object>> data = initData(warehousesList.size());

                ImportField idWarehouseField = new ImportField(LM.findLCPByCompoundName("idWarehouse"));
                ImportKey<?> warehouseKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Warehouse"),
                        LM.findLCPByCompoundName("warehouseId").getMapping(idWarehouseField));
                keys.add(warehouseKey);
                props.add(new ImportProperty(idWarehouseField, LM.findLCPByCompoundName("idWarehouse").getMapping(warehouseKey)));
                fields.add(idWarehouseField);
                for (int i = 0; i < warehousesList.size(); i++)
                    data.get(i).add(warehousesList.get(i).idWarehouse);

                ImportField nameWarehouseField = new ImportField(LM.findLCPByCompoundName("nameWarehouse"));
                props.add(new ImportProperty(nameWarehouseField, LM.findLCPByCompoundName("nameWarehouse").getMapping(warehouseKey)));
                fields.add(nameWarehouseField);
                for (int i = 0; i < warehousesList.size(); i++)
                    data.get(i).add(warehousesList.get(i).nameWarehouse);

                ImportField addressWarehouseField = new ImportField(LM.findLCPByCompoundName("addressWarehouse"));
                props.add(new ImportProperty(addressWarehouseField, LM.findLCPByCompoundName("addressWarehouse").getMapping(warehouseKey)));
                fields.add(addressWarehouseField);
                for (int i = 0; i < warehousesList.size(); i++)
                    data.get(i).add(warehousesList.get(i).addressWarehouse);

                ImportField idLegalEntityField = new ImportField(LM.findLCPByCompoundName("idLegalEntity"));
                ImportKey<?> legalEntityKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("LegalEntity"),
                        LM.findLCPByCompoundName("legalEntityId").getMapping(idLegalEntityField));
                keys.add(legalEntityKey);
                props.add(new ImportProperty(idLegalEntityField, LM.findLCPByCompoundName("idLegalEntity").getMapping(legalEntityKey)));
                props.add(new ImportProperty(idLegalEntityField, LM.findLCPByCompoundName("legalEntityWarehouse").getMapping(warehouseKey),
                        LM.object(LM.findClassByCompoundName("LegalEntity")).getMapping(legalEntityKey)));
                fields.add(idLegalEntityField);
                for (int i = 0; i < warehousesList.size(); i++)
                    data.get(i).add(warehousesList.get(i).idLegalEntity);

                ImportField idWarehouseGroupField = new ImportField(LM.findLCPByCompoundName("idWarehouseGroup"));
                ImportKey<?> warehouseGroupKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("WarehouseGroup"),
                        LM.findLCPByCompoundName("warehouseGroupId").getMapping(idWarehouseGroupField));
                keys.add(warehouseGroupKey);
                props.add(new ImportProperty(idWarehouseGroupField, LM.findLCPByCompoundName("warehouseGroupWarehouse").getMapping(warehouseKey),
                        LM.object(LM.findClassByCompoundName("WarehouseGroup")).getMapping(warehouseGroupKey)));
                fields.add(idWarehouseGroupField);
                for (int i = 0; i < warehousesList.size(); i++)
                    data.get(i).add(warehousesList.get(i).idWarehouseGroup);

                ImportTable table = new ImportTable(fields, data);

                DataSession session = context.createSession();
                session.sql.pushVolatileStats(null);
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context.getBL());
                session.sql.popVolatileStats(null);
                session.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void importStores(List<LegalEntity> storesList) throws SQLException, ScriptingErrorLog.SemanticErrorException {

        try {
            if (storesList != null) {

                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                List<List<Object>> data = initData(storesList.size());

                ImportField idStoreField = new ImportField(LM.findLCPByCompoundName("idStore"));
                ImportKey<?> storeKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Store"),
                        LM.findLCPByCompoundName("storeId").getMapping(idStoreField));
                keys.add(storeKey);
                props.add(new ImportProperty(idStoreField, LM.findLCPByCompoundName("idStore").getMapping(storeKey)));
                fields.add(idStoreField);
                for (int i = 0; i < storesList.size(); i++)
                    data.get(i).add(((Store) storesList.get(i)).idStore);

                ImportField nameStoreField = new ImportField(LM.findLCPByCompoundName("nameStore"));
                props.add(new ImportProperty(nameStoreField, LM.findLCPByCompoundName("nameStore").getMapping(storeKey)));
                fields.add(nameStoreField);
                for (int i = 0; i < storesList.size(); i++)
                    data.get(i).add(storesList.get(i).nameLegalEntity);

                ImportField addressStoreField = new ImportField(LM.findLCPByCompoundName("addressStore"));
                props.add(new ImportProperty(addressStoreField, LM.findLCPByCompoundName("addressStore").getMapping(storeKey)));
                fields.add(addressStoreField);
                for (int i = 0; i < storesList.size(); i++)
                    data.get(i).add(storesList.get(i).addressLegalEntity);

                ImportField idLegalEntityField = new ImportField(LM.findLCPByCompoundName("idLegalEntity"));
                ImportKey<?> legalEntityKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("LegalEntity"),
                        LM.findLCPByCompoundName("legalEntityId").getMapping(idLegalEntityField));
                keys.add(legalEntityKey);
                props.add(new ImportProperty(idLegalEntityField, LM.findLCPByCompoundName("legalEntityStore").getMapping(storeKey),
                        LM.object(LM.findClassByCompoundName("LegalEntity")).getMapping(legalEntityKey)));
                fields.add(idLegalEntityField);
                for (int i = 0; i < storesList.size(); i++)
                    data.get(i).add(storesList.get(i).idLegalEntity);

                ImportField idChainStoresField = new ImportField(LM.findLCPByCompoundName("idChainStores"));
                ImportKey<?> chainStoresKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("ChainStores"),
                        LM.findLCPByCompoundName("chainStoresId").getMapping(idChainStoresField));
                keys.add(chainStoresKey);
                fields.add(idChainStoresField);
                for (int i = 0; i < storesList.size(); i++)
                    data.get(i).add(storesList.get(i).idChainStores);

                ImportField storeTypeField = new ImportField(LM.findLCPByCompoundName("nameStoreType"));
                ImportKey<?> storeTypeKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("StoreType"),
                        LM.findLCPByCompoundName("storeTypeNameChainStores").getMapping(storeTypeField, idChainStoresField));
                keys.add(storeTypeKey);
                props.add(new ImportProperty(idChainStoresField, LM.findLCPByCompoundName("chainStoresStoreType").getMapping(storeTypeKey),
                        LM.object(LM.findClassByCompoundName("ChainStores")).getMapping(chainStoresKey)));
                props.add(new ImportProperty(storeTypeField, LM.findLCPByCompoundName("nameStoreType").getMapping(storeTypeKey)));
                props.add(new ImportProperty(storeTypeField, LM.findLCPByCompoundName("storeTypeStore").getMapping(storeKey),
                        LM.object(LM.findClassByCompoundName("StoreType")).getMapping(storeTypeKey)));
                fields.add(storeTypeField);
                for (int i = 0; i < storesList.size(); i++)
                    data.get(i).add(((Store) storesList.get(i)).storeType);

                ImportTable table = new ImportTable(fields, data);

                DataSession session = context.createSession();
                session.sql.pushVolatileStats(null);
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context.getBL());
                session.sql.popVolatileStats(null);
                session.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void importDepartmentStores(List<DepartmentStore> departmentStoresList) throws SQLException, ScriptingErrorLog.SemanticErrorException {

        try {
            if (departmentStoresList != null) {

                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                List<List<Object>> data = initData(departmentStoresList.size());

                ImportField idDepartmentStoreField = new ImportField(LM.findLCPByCompoundName("idDepartmentStore"));
                ImportKey<?> departmentStoreKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("DepartmentStore"),
                        LM.findLCPByCompoundName("departmentStoreId").getMapping(idDepartmentStoreField));
                keys.add(departmentStoreKey);
                props.add(new ImportProperty(idDepartmentStoreField, LM.findLCPByCompoundName("idDepartmentStore").getMapping(departmentStoreKey)));
                fields.add(idDepartmentStoreField);
                for (int i = 0; i < departmentStoresList.size(); i++)
                    data.get(i).add((departmentStoresList.get(i)).idDepartmentStore);

                ImportField nameDepartmentStoreField = new ImportField(LM.findLCPByCompoundName("nameDepartmentStore"));
                props.add(new ImportProperty(nameDepartmentStoreField, LM.findLCPByCompoundName("nameDepartmentStore").getMapping(departmentStoreKey)));
                fields.add(nameDepartmentStoreField);
                for (int i = 0; i < departmentStoresList.size(); i++)
                    data.get(i).add((departmentStoresList.get(i)).nameDepartmentStore);

                ImportField idStoreField = new ImportField(LM.findLCPByCompoundName("idStore"));
                ImportKey<?> storeKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Store"),
                        LM.findLCPByCompoundName("storeId").getMapping(idStoreField));
                keys.add(storeKey);
                props.add(new ImportProperty(idStoreField, LM.findLCPByCompoundName("storeDepartmentStore").getMapping(departmentStoreKey),
                        LM.object(LM.findClassByCompoundName("Store")).getMapping(storeKey)));
                fields.add(idStoreField);
                for (int i = 0; i < departmentStoresList.size(); i++)
                    data.get(i).add((departmentStoresList.get(i)).idStore);

                ImportTable table = new ImportTable(fields, data);

                DataSession session = context.createSession();
                session.sql.pushVolatileStats(null);
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context.getBL());
                session.sql.popVolatileStats(null);
                session.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void importBanks(List<Bank> banksList) throws SQLException, ScriptingErrorLog.SemanticErrorException {

        try {
            if (banksList != null) {

                DataObject defaultDate = new DataObject(new java.sql.Date(2001 - 1900, 0, 01), DateClass.instance);

                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                List<List<Object>> data = initData(banksList.size());

                ImportField idBankField = new ImportField(LM.findLCPByCompoundName("idBank"));
                ImportKey<?> bankKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Bank"),
                        LM.findLCPByCompoundName("bankId").getMapping(idBankField));
                keys.add(bankKey);
                props.add(new ImportProperty(idBankField, LM.findLCPByCompoundName("idBank").getMapping(bankKey)));
                fields.add(idBankField);
                for (int i = 0; i < banksList.size(); i++)
                    data.get(i).add(banksList.get(i).idBank);

                ImportField nameBankField = new ImportField(LM.findLCPByCompoundName("nameBank"));
                props.add(new ImportProperty(nameBankField, LM.findLCPByCompoundName("nameBank").getMapping(bankKey)));
                fields.add(nameBankField);
                for (int i = 0; i < banksList.size(); i++)
                    data.get(i).add(banksList.get(i).nameBank);

                ImportField addressBankField = new ImportField(LM.findLCPByCompoundName("dataAddressBankDate"));
                props.add(new ImportProperty(addressBankField, LM.findLCPByCompoundName("dataAddressBankDate").getMapping(bankKey, defaultDate)));
                fields.add(addressBankField);
                for (int i = 0; i < banksList.size(); i++)
                    data.get(i).add(banksList.get(i).addressBank);

                ImportField departmentBankField = new ImportField(LM.findLCPByCompoundName("departmentBank"));
                props.add(new ImportProperty(departmentBankField, LM.findLCPByCompoundName("departmentBank").getMapping(bankKey)));
                fields.add(departmentBankField);
                for (int i = 0; i < banksList.size(); i++)
                    data.get(i).add(banksList.get(i).departmentBank);

                ImportField mfoBankField = new ImportField(LM.findLCPByCompoundName("MFOBank"));
                props.add(new ImportProperty(mfoBankField, LM.findLCPByCompoundName("MFOBank").getMapping(bankKey)));
                fields.add(mfoBankField);
                for (int i = 0; i < banksList.size(); i++)
                    data.get(i).add(banksList.get(i).mfoBank);

                ImportField cbuBankField = new ImportField(LM.findLCPByCompoundName("CBUBank"));
                props.add(new ImportProperty(cbuBankField, LM.findLCPByCompoundName("CBUBank").getMapping(bankKey)));
                fields.add(cbuBankField);
                for (int i = 0; i < banksList.size(); i++)
                    data.get(i).add(banksList.get(i).cbuBank);

                ImportTable table = new ImportTable(fields, data);

                DataSession session = context.createSession();
                session.sql.pushVolatileStats(null);
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context.getBL());
                session.sql.popVolatileStats(null);
                session.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void importRateWastes(List<RateWaste> rateWastesList) throws SQLException, ScriptingErrorLog.SemanticErrorException {

        try {
            if (rateWastesList != null) {

                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                List<List<Object>> data = initData(rateWastesList.size());

                ImportField idWriteOffRateField = new ImportField(LM.findLCPByCompoundName("idWriteOffRate"));
                ImportKey<?> writeOffRateKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("WriteOffRate"),
                        LM.findLCPByCompoundName("writeOffRateId").getMapping(idWriteOffRateField));
                keys.add(writeOffRateKey);
                props.add(new ImportProperty(idWriteOffRateField, LM.findLCPByCompoundName("idWriteOffRate").getMapping(writeOffRateKey)));
                fields.add(idWriteOffRateField);
                for (int i = 0; i < rateWastesList.size(); i++)
                    data.get(i).add(rateWastesList.get(i).idRateWaste);

                ImportField nameWriteOffRateField = new ImportField(LM.findLCPByCompoundName("nameWriteOffRate"));
                props.add(new ImportProperty(nameWriteOffRateField, LM.findLCPByCompoundName("nameWriteOffRate").getMapping(writeOffRateKey)));
                fields.add(nameWriteOffRateField);
                for (int i = 0; i < rateWastesList.size(); i++)
                    data.get(i).add(rateWastesList.get(i).nameRateWaste);

                ImportField percentWriteOffRateField = new ImportField(LM.findLCPByCompoundName("percentWriteOffRate"));
                props.add(new ImportProperty(percentWriteOffRateField, LM.findLCPByCompoundName("percentWriteOffRate").getMapping(writeOffRateKey)));
                fields.add(percentWriteOffRateField);
                for (int i = 0; i < rateWastesList.size(); i++)
                    data.get(i).add(rateWastesList.get(i).percentWriteOffRate);

                ImportField nameCountryField = new ImportField(LM.findLCPByCompoundName("nameCountry"));
                ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Country"),
                        LM.findLCPByCompoundName("countryName").getMapping(nameCountryField));
                keys.add(countryKey);
                props.add(new ImportProperty(nameCountryField, LM.findLCPByCompoundName("countryWriteOffRate").getMapping(writeOffRateKey),
                        LM.object(LM.findClassByCompoundName("Country")).getMapping(countryKey)));
                fields.add(nameCountryField);
                for (int i = 0; i < rateWastesList.size(); i++)
                    data.get(i).add(rateWastesList.get(i).nameCountry);

                ImportTable table = new ImportTable(fields, data);

                DataSession session = context.createSession();
                session.sql.pushVolatileStats(null);
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context.getBL());
                session.sql.popVolatileStats(null);
                session.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void importContracts(List<Contract> contractsList) throws SQLException, ScriptingErrorLog.SemanticErrorException {

        try {
            if (contractsList != null) {

                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                List<List<Object>> data = initData(contractsList.size());

                ImportField idUserContractSkuField = new ImportField(LM.findLCPByCompoundName("idUserContractSku"));
                ImportKey<?> userContractSkuKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("UserContractSku"),
                        LM.findLCPByCompoundName("userContractSkuId").getMapping(idUserContractSkuField));
                keys.add(userContractSkuKey);
                props.add(new ImportProperty(idUserContractSkuField, LM.findLCPByCompoundName("idUserContractSku").getMapping(userContractSkuKey)));
                fields.add(idUserContractSkuField);
                for (int i = 0; i < contractsList.size(); i++)
                    data.get(i).add(contractsList.get(i).idUserContractSku);

                ImportField numberContractField = new ImportField(LM.findLCPByCompoundName("numberContract"));
                props.add(new ImportProperty(numberContractField, LM.findLCPByCompoundName("numberContract").getMapping(userContractSkuKey)));
                fields.add(numberContractField);
                for (int i = 0; i < contractsList.size(); i++)
                    data.get(i).add(contractsList.get(i).numberContract);

                ImportField dateFromContractField = new ImportField(LM.findLCPByCompoundName("dateFromContract"));
                props.add(new ImportProperty(dateFromContractField, LM.findLCPByCompoundName("dateFromContract").getMapping(userContractSkuKey)));
                fields.add(dateFromContractField);
                for (int i = 0; i < contractsList.size(); i++)
                    data.get(i).add(contractsList.get(i).dateFromContract);

                ImportField dateToContractField = new ImportField(LM.findLCPByCompoundName("dateToContract"));
                props.add(new ImportProperty(dateToContractField, LM.findLCPByCompoundName("dateToContract").getMapping(userContractSkuKey)));
                fields.add(dateToContractField);
                for (int i = 0; i < contractsList.size(); i++)
                    data.get(i).add(contractsList.get(i).dateToContract);

                ImportField idSupplierField = new ImportField(LM.findLCPByCompoundName("idLegalEntity"));
                ImportKey<?> supplierKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("LegalEntity"),
                        LM.findLCPByCompoundName("legalEntityId").getMapping(idSupplierField));
                keys.add(supplierKey);
                props.add(new ImportProperty(idSupplierField, LM.findLCPByCompoundName("supplierContractSku").getMapping(userContractSkuKey),
                        LM.object(LM.findClassByCompoundName("LegalEntity")).getMapping(supplierKey)));
                fields.add(idSupplierField);
                for (int i = 0; i < contractsList.size(); i++)
                    data.get(i).add(contractsList.get(i).idSupplier);

                ImportField idCustomerField = new ImportField(LM.findLCPByCompoundName("idLegalEntity"));
                ImportKey<?> customerKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("LegalEntity"),
                        LM.findLCPByCompoundName("legalEntityId").getMapping(idCustomerField));
                keys.add(customerKey);
                props.add(new ImportProperty(idCustomerField, LM.findLCPByCompoundName("customerContractSku").getMapping(userContractSkuKey),
                        LM.object(LM.findClassByCompoundName("LegalEntity")).getMapping(customerKey)));
                fields.add(idCustomerField);
                for (int i = 0; i < contractsList.size(); i++)
                    data.get(i).add(contractsList.get(i).idCustomer);

                ImportField shortNameCurrencyField = new ImportField(LM.findLCPByCompoundName("shortNameCurrency"));
                ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Currency"),
                        LM.findLCPByCompoundName("currencyShortName").getMapping(shortNameCurrencyField));
                keys.add(currencyKey);
                props.add(new ImportProperty(shortNameCurrencyField, LM.findLCPByCompoundName("currencyContract").getMapping(userContractSkuKey),
                        LM.object(LM.findClassByCompoundName("Currency")).getMapping(currencyKey)));
                fields.add(shortNameCurrencyField);
                for (int i = 0; i < contractsList.size(); i++)
                    data.get(i).add(contractsList.get(i).shortNameCurrency);

                ImportTable table = new ImportTable(fields, data);

                DataSession session = context.createSession();
                session.sql.pushVolatileStats(null);
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context.getBL());
                session.sql.popVolatileStats(null);
                session.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String valueWithPrefix(String value, String prefix, String defaultValue) {
        if (value == null)
            return defaultValue;
        else return prefix + value;
    }

    private Boolean showField(List<UserInvoiceDetail> data, String fieldName) {
        try {
            Field field = UserInvoiceDetail.class.getField(fieldName);

            for (int i = 0; i < data.size(); i++) {
                if (field.get(data.get(i)) != null)
                    return true;
            }
        } catch (NoSuchFieldException e) {
            return true;
        } catch (IllegalAccessException e) {
            return true;
        }
        return false;
    }

    private List<List<Object>> initData(int size) {
        List<List<Object>> data = new ArrayList<List<Object>>();
        for (int i = 0; i < size; i++) {
            data.add(new ArrayList<Object>());
        }
        return data;
    }
}