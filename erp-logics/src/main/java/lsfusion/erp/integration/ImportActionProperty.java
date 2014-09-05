package lsfusion.erp.integration;

import lsfusion.base.ExceptionUtils;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.*;
import lsfusion.server.data.SQLHandledException;
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
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImportActionProperty extends DefaultImportActionProperty {
    private ExecutionContext<ClassPropertyInterface> context;

    // Опциональные модули
    ScriptingLogicsModule legalEntityByLM;
    ScriptingLogicsModule pricingPurchaseLM;
    ScriptingLogicsModule purchaseCertificateLM;
    ScriptingLogicsModule purchaseComplianceDetailLM;
    ScriptingLogicsModule importUserPriceListLM;
    ScriptingLogicsModule purchaseDeclarationDetailLM;
    ScriptingLogicsModule purchaseInvoiceChargeLM;
    ScriptingLogicsModule purchaseInvoiceWholesalePriceLM;
    ScriptingLogicsModule purchaseManufacturingPriceLM;
    ScriptingLogicsModule salePackLM;
    ScriptingLogicsModule storeLM;
    ScriptingLogicsModule warePurchaseInvoiceLM;
    ScriptingLogicsModule writeOffItemLM;
    ScriptingLogicsModule tripInvoiceLM;

    public boolean skipExtraInvoiceParams;
    public boolean skipCertificateInvoiceParams;

    DataObject defaultDate = new DataObject(new Date(2001 - 1900, 0, 01), DateClass.instance);

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        makeImport(new ImportData(), context);
    }

    public ImportActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public void makeImport(ImportData importData, ExecutionContext<ClassPropertyInterface> context) throws SQLException {
        try {
            this.legalEntityByLM = context.getBL().getModule("LegalEntityBy");
            this.pricingPurchaseLM = context.getBL().getModule("PricingPurchase");
            this.purchaseCertificateLM = context.getBL().getModule("PurchaseCertificate");
            this.purchaseComplianceDetailLM = context.getBL().getModule("PurchaseComplianceDetail");
            this.importUserPriceListLM = context.getBL().getModule("ImportUserPriceList");
            this.purchaseDeclarationDetailLM = context.getBL().getModule("PurchaseDeclarationDetail");
            this.purchaseInvoiceChargeLM = context.getBL().getModule("PurchaseInvoiceCharge");
            this.purchaseInvoiceWholesalePriceLM = context.getBL().getModule("PurchaseInvoiceWholesalePrice");
            this.purchaseManufacturingPriceLM = context.getBL().getModule("PurchaseManufacturingPrice");
            this.salePackLM = context.getBL().getModule("SalePack");
            this.storeLM = context.getBL().getModule("Store");
            this.warePurchaseInvoiceLM = context.getBL().getModule("WarePurchaseInvoice");
            this.writeOffItemLM = context.getBL().getModule("WriteOffPurchaseItem");
            this.tripInvoiceLM = context.getBL().getModule("TripInvoice");

            this.context = context;

            Object countryBelarus = findProperty("countrySID").read(context.getSession(), new DataObject("112", StringClass.get(3)));
            findProperty("defaultCountry").change(countryBelarus, context.getSession());
            context.getSession().apply(context);

            importItemGroups(importData.getItemGroupsList());

            importParentGroups(importData.getParentGroupsList());

            importBanks(importData.getBanksList());

            importLegalEntities(importData.getLegalEntitiesList());

            importEmployees(importData.getEmployeesList());

            importWarehouseGroups(importData.getWarehouseGroupsList());

            importWarehouses(importData.getWarehousesList());

            importStores(importData.getStoresList(), importData.getSkipKeys());

            importDepartmentStores(importData.getDepartmentStoresList());

            importContracts(importData.getContractsList(), importData.getSkipKeys());

            importRateWastes(importData.getRateWastesList());

            importWares(importData.getWaresList());

            importUOMs(importData.getUOMsList());

            importItems(importData.getItemsList(), importData.getNumberOfItemsAtATime(), importData.getSkipKeys());

            importPriceListStores(importData.getPriceListStoresList(), importData.getNumberOfPriceListsAtATime(), importData.getSkipKeys());

            importPriceListSuppliers(importData.getPriceListSuppliersList(), importData.getNumberOfPriceListsAtATime(), importData.getSkipKeys());

            importUserInvoices(importData.getUserInvoicesList(), importData.getNumberOfUserInvoicesAtATime(), importData.getSkipKeys(), importData.getUserInvoiceCreateNewItems());

        } catch (Exception e) {
            throw ExceptionUtils.propagate(e, SQLException.class);
        }
    }

    private void importParentGroups(List<ItemGroup> parentGroupsList) throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        if (notNullNorEmpty(parentGroupsList)) {

            ServerLoggers.systemLogger.info("importParentGroups");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(parentGroupsList.size());

            ImportField idItemGroupField = new ImportField(findProperty("idItemGroup"));
            ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) findClass("ItemGroup"),
                    findProperty("itemGroupId").getMapping(idItemGroupField));
            keys.add(itemGroupKey);
            fields.add(idItemGroupField);
            for (int i = 0; i < parentGroupsList.size(); i++)
                data.get(i).add(parentGroupsList.get(i).sid);

            ImportField idParentGroupField = new ImportField(findProperty("idItemGroup"));
            ImportKey<?> parentGroupKey = new ImportKey((ConcreteCustomClass) findClass("ItemGroup"),
                    findProperty("itemGroupId").getMapping(idParentGroupField));
            keys.add(parentGroupKey);
            props.add(new ImportProperty(idParentGroupField, findProperty("parentItemGroup").getMapping(itemGroupKey),
                    LM.object(findClass("ItemGroup")).getMapping(parentGroupKey)));
            fields.add(idParentGroupField);
            for (int i = 0; i < parentGroupsList.size(); i++)
                data.get(i).add(parentGroupsList.get(i).parent);

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.createSession();
            session.pushVolatileStats("IA_PG");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            session.popVolatileStats();
            session.close();
        }
    }

    private void importItemGroups(List<ItemGroup> itemGroupsList) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (notNullNorEmpty(itemGroupsList)) {

            ServerLoggers.systemLogger.info("importItemGroups");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(itemGroupsList.size());

            ImportField idItemGroupField = new ImportField(findProperty("idItemGroup"));
            ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) findClass("ItemGroup"),
                    findProperty("itemGroupId").getMapping(idItemGroupField));
            keys.add(itemGroupKey);
            props.add(new ImportProperty(idItemGroupField, findProperty("idItemGroup").getMapping(itemGroupKey)));
            fields.add(idItemGroupField);
            for (int i = 0; i < itemGroupsList.size(); i++)
                data.get(i).add(itemGroupsList.get(i).sid);

            ImportField itemGroupNameField = new ImportField(findProperty("nameItemGroup"));
            props.add(new ImportProperty(itemGroupNameField, findProperty("nameItemGroup").getMapping(itemGroupKey)));
            fields.add(itemGroupNameField);
            for (int i = 0; i < itemGroupsList.size(); i++)
                data.get(i).add(itemGroupsList.get(i).name);

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.createSession();
            session.pushVolatileStats("IA_IG");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            session.popVolatileStats();
            session.close();
        }
    }

    private void importWares(List<Ware> waresList) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (warePurchaseInvoiceLM != null && notNullNorEmpty(waresList)) {

            ServerLoggers.systemLogger.info("importWares");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(waresList.size());

            ImportField idWareField = new ImportField(warePurchaseInvoiceLM.findProperty("idWare"));
            ImportKey<?> wareKey = new ImportKey((ConcreteCustomClass) warePurchaseInvoiceLM.findClass("Ware"),
                    warePurchaseInvoiceLM.findProperty("wareId").getMapping(idWareField));
            keys.add(wareKey);
            props.add(new ImportProperty(idWareField, warePurchaseInvoiceLM.findProperty("idWare").getMapping(wareKey)));
            fields.add(idWareField);
            for (int i = 0; i < waresList.size(); i++)
                data.get(i).add(waresList.get(i).idWare);

            ImportField nameWareField = new ImportField(warePurchaseInvoiceLM.findProperty("nameWare"));
            props.add(new ImportProperty(nameWareField, warePurchaseInvoiceLM.findProperty("nameWare").getMapping(wareKey)));
            fields.add(nameWareField);
            for (int i = 0; i < waresList.size(); i++)
                data.get(i).add(waresList.get(i).nameWare);

            ImportField priceWareField = new ImportField(warePurchaseInvoiceLM.findProperty("warePrice"));
            props.add(new ImportProperty(priceWareField, warePurchaseInvoiceLM.findProperty("dataWarePriceDate").getMapping(wareKey, defaultDate)));
            fields.add(priceWareField);
            for (int i = 0; i < waresList.size(); i++)
                data.get(i).add(waresList.get(i).priceWare);

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.createSession();
            session.pushVolatileStats("IA_WRE");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            session.popVolatileStats();
            session.close();
        }
    }

    private void importItems(List<Item> itemsList, Integer numberOfItemsAtATime, boolean skipKeys) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        try {
            Integer numAtATime = (numberOfItemsAtATime == null || numberOfItemsAtATime <= 0) ? 5000 : numberOfItemsAtATime;
            if (itemsList != null) {
                int amountOfImportIterations = (int) Math.ceil((double) itemsList.size() / numAtATime);
                Integer rest = itemsList.size();
                for (int i = 0; i < amountOfImportIterations; i++) {
                    importPackOfItems(itemsList.subList(i * numAtATime, i * numAtATime + (rest > numAtATime ? numAtATime : rest)), skipKeys);
                    rest -= numAtATime;
                    System.gc();
                }
            }
        } catch (xBaseJException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void importUOMs(List<UOM> uomsList) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        if (uomsList == null)
            return;

        ServerLoggers.systemLogger.info("importUOMs");

        List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
        List<ImportField> fields = new ArrayList<ImportField>();
        List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

        List<List<Object>> data = initData(uomsList.size());

        ImportField idUOMField = new ImportField(findProperty("idUOM"));
        ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) findClass("UOM"),
                findProperty("UOMId").getMapping(idUOMField));
        keys.add(UOMKey);
        props.add(new ImportProperty(idUOMField, findProperty("idUOM").getMapping(UOMKey)));
        fields.add(idUOMField);
        for (int i = 0; i < uomsList.size(); i++)
            data.get(i).add(uomsList.get(i).idUOM);

        ImportField nameUOMField = new ImportField(findProperty("nameUOM"));
        props.add(new ImportProperty(nameUOMField, findProperty("nameUOM").getMapping(UOMKey)));
        fields.add(nameUOMField);
        for (int i = 0; i < uomsList.size(); i++)
            data.get(i).add(uomsList.get(i).nameUOM);

        ImportField shortNameUOMField = new ImportField(findProperty("shortNameUOM"));
        props.add(new ImportProperty(shortNameUOMField, findProperty("shortNameUOM").getMapping(UOMKey)));
        fields.add(shortNameUOMField);
        for (int i = 0; i < uomsList.size(); i++)
            data.get(i).add(uomsList.get(i).shortNameUOM);

        ImportTable table = new ImportTable(fields, data);

        DataSession session = context.createSession();
        session.pushVolatileStats("IA_UOM");
        IntegrationService service = new IntegrationService(session, table, keys, props);
        service.synchronize(true, false);
        session.apply(context);
        session.popVolatileStats();
        session.close();
    }


    private void importPackOfItems(List<Item> itemsList, boolean skipKeys) throws SQLException, IOException, xBaseJException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        if (!notNullNorEmpty(itemsList)) return;

        ServerLoggers.systemLogger.info("importItems " + itemsList.size());

        List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
        List<ImportField> fields = new ArrayList<ImportField>();
        List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

        List<List<Object>> data = initData(itemsList.size());

        ImportField idItemField = new ImportField(findProperty("idItem"));
        ImportKey<?> itemKey = new ImportKey((ConcreteCustomClass) findClass("Item"),
                findProperty("itemId").getMapping(idItemField));
        keys.add(itemKey);
        props.add(new ImportProperty(idItemField, findProperty("idItem").getMapping(itemKey)));
        fields.add(idItemField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).idItem);

        if (showItemField(itemsList, "captionItem")) {
            ImportField captionItemField = new ImportField(findProperty("captionItem"));
            props.add(new ImportProperty(captionItemField, findProperty("captionItem").getMapping(itemKey)));
            fields.add(captionItemField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).captionItem);
        }

        if (showItemField(itemsList, "idItemGroup")) {
            ImportField idItemGroupField = new ImportField(findProperty("idItemGroup"));
            ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) findClass("ItemGroup"),
                    findProperty("itemGroupId").getMapping(idItemGroupField));
            keys.add(itemGroupKey);
            props.add(new ImportProperty(idItemGroupField, findProperty("itemGroupItem").getMapping(itemKey),
                    LM.object(findClass("ItemGroup")).getMapping(itemGroupKey)));
            fields.add(idItemGroupField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).idItemGroup);
        }

        if (showItemField(itemsList, "idBrand")) {
            ImportField idBrandField = new ImportField(findProperty("idBrand"));
            ImportKey<?> brandKey = new ImportKey((ConcreteCustomClass) findClass("Brand"),
                    findProperty("brandId").getMapping(idBrandField));
            keys.add(brandKey);
            props.add(new ImportProperty(idBrandField, findProperty("idBrand").getMapping(brandKey)));
            props.add(new ImportProperty(idBrandField, findProperty("brandItem").getMapping(itemKey),
                    LM.object(findClass("Brand")).getMapping(brandKey)));
            fields.add(idBrandField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).idBrand);

            ImportField nameBrandField = new ImportField(findProperty("nameBrand"));
            props.add(new ImportProperty(nameBrandField, findProperty("nameBrand").getMapping(brandKey)));
            fields.add(nameBrandField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).nameBrand);
        }

        ImportField nameCountryField = new ImportField(findProperty("nameCountry"));
        ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) findClass("Country"),
                findProperty("countryName").getMapping(nameCountryField));
        keys.add(countryKey);
        props.add(new ImportProperty(nameCountryField, findProperty("nameCountry").getMapping(countryKey)));
        props.add(new ImportProperty(nameCountryField, findProperty("countryItem").getMapping(itemKey),
                LM.object(findClass("Country")).getMapping(countryKey)));
        fields.add(nameCountryField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).nameCountry);

        ImportField extIdBarcodeField = new ImportField(findProperty("extIdBarcode"));
        ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) findClass("Barcode"),
                findProperty(/*"barcodeIdDate"*/"extBarcodeId").getMapping(extIdBarcodeField));
        keys.add(barcodeKey);
        props.add(new ImportProperty(idItemField, findProperty("skuBarcode").getMapping(barcodeKey),
                LM.object(findClass("Item")).getMapping(itemKey)));
        props.add(new ImportProperty(extIdBarcodeField, findProperty("extIdBarcode").getMapping(barcodeKey)));
        fields.add(extIdBarcodeField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).extIdBarcode);

        ImportField idBarcodeField = new ImportField(findProperty("idBarcode"));
        props.add(new ImportProperty(idBarcodeField, findProperty("idBarcode").getMapping(barcodeKey)));
        fields.add(idBarcodeField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).idBarcode);

        ImportField idUOMField = new ImportField(findProperty("idUOM"));
        ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) findClass("UOM"),
                findProperty("UOMId").getMapping(idUOMField));
        UOMKey.skipKey = true;
        keys.add(UOMKey);
        props.add(new ImportProperty(idUOMField, findProperty("UOMItem").getMapping(itemKey),
                LM.object(findClass("UOM")).getMapping(UOMKey)));
        props.add(new ImportProperty(idUOMField, findProperty("UOMBarcode").getMapping(barcodeKey),
                LM.object(findClass("UOM")).getMapping(UOMKey)));
        fields.add(idUOMField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).idUOM);

        if (showItemField(itemsList, "splitItem")) {
            ImportField splitItemField = new ImportField(findProperty("splitItem"));
            props.add(new ImportProperty(splitItemField, findProperty("splitItem").getMapping(itemKey), true));
            fields.add(splitItemField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).splitItem);
        }

        if (showItemField(itemsList, "netWeightItem")) {
            ImportField netWeightItemField = new ImportField(findProperty("netWeightItem"));
            props.add(new ImportProperty(netWeightItemField, findProperty("netWeightItem").getMapping(itemKey)));
            fields.add(netWeightItemField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).netWeightItem);
        }

        if (showItemField(itemsList, "grossWeightItem")) {
            ImportField grossWeightItemField = new ImportField(findProperty("grossWeightItem"));
            props.add(new ImportProperty(grossWeightItemField, findProperty("grossWeightItem").getMapping(itemKey)));
            fields.add(grossWeightItemField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).grossWeightItem);
        }

        if (showItemField(itemsList, "compositionItem")) {
            ImportField compositionItemField = new ImportField(findProperty("compositionItem"));
            props.add(new ImportProperty(compositionItemField, findProperty("compositionItem").getMapping(itemKey)));
            fields.add(compositionItemField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).compositionItem);
        }

        ImportField dateField = new ImportField(DateClass.instance);
        props.add(new ImportProperty(dateField, findProperty("dataDateBarcode").getMapping(barcodeKey)));
        fields.add(dateField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).date);

        DataObject defaultCountryObject = (DataObject) findProperty("defaultCountry").readClasses(context.getSession());
        ImportField valueVATItemCountryDateField = new ImportField(findProperty("valueVATItemCountryDate"));
        ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) findClass("Range"),
                findProperty("valueCurrentVATDefaultValue").getMapping(valueVATItemCountryDateField));
        VATKey.skipKey = true;
        keys.add(VATKey);
        props.add(new ImportProperty(valueVATItemCountryDateField, findProperty("VATItemCountry").getMapping(itemKey, defaultCountryObject),
                LM.object(findClass("Range")).getMapping(VATKey)));
        fields.add(valueVATItemCountryDateField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).retailVAT);

        if (warePurchaseInvoiceLM != null && showItemField(itemsList, "idWare")) {

            ImportField idWareField = new ImportField(warePurchaseInvoiceLM.findProperty("idWare"));
            ImportKey<?> wareKey = new ImportKey((ConcreteCustomClass) warePurchaseInvoiceLM.findClass("Ware"),
                    warePurchaseInvoiceLM.findProperty("wareId").getMapping(idWareField));
            keys.add(wareKey);
            props.add(new ImportProperty(idWareField, warePurchaseInvoiceLM.findProperty("idWare").getMapping(wareKey)));
            props.add(new ImportProperty(idWareField, warePurchaseInvoiceLM.findProperty("wareItem").getMapping(itemKey),
                    warePurchaseInvoiceLM.object(warePurchaseInvoiceLM.findClass("Ware")).getMapping(wareKey)));
            fields.add(idWareField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).idWare);

            ImportField priceWareField = new ImportField(warePurchaseInvoiceLM.findProperty("dataWarePriceDate"));
            props.add(new ImportProperty(priceWareField, warePurchaseInvoiceLM.findProperty("dataWarePriceDate").getMapping(wareKey, dateField)));
            fields.add(priceWareField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).priceWare);

            ImportField vatWareField = new ImportField(warePurchaseInvoiceLM.findProperty("valueRate"));
            ImportKey<?> rangeKey = new ImportKey((ConcreteCustomClass) warePurchaseInvoiceLM.findClass("Range"),
                    warePurchaseInvoiceLM.findProperty("valueCurrentVATDefaultValue").getMapping(vatWareField));
            keys.add(rangeKey);
            props.add(new ImportProperty(vatWareField, warePurchaseInvoiceLM.findProperty("dataRangeWareDate").getMapping(wareKey, dateField, rangeKey),
                    warePurchaseInvoiceLM.object(warePurchaseInvoiceLM.findClass("Range")).getMapping(rangeKey)));
            fields.add(vatWareField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).vatWare);

        }

        if (writeOffItemLM != null && showItemField(itemsList, "idWriteOffRate")) {

            ImportField idWriteOffRateField = new ImportField(writeOffItemLM.findProperty("idWriteOffRate"));
            ImportKey<?> writeOffRateKey = new ImportKey((ConcreteCustomClass) writeOffItemLM.findClass("WriteOffRate"),
                    writeOffItemLM.findProperty("writeOffRateId").getMapping(idWriteOffRateField));
            keys.add(writeOffRateKey);
            props.add(new ImportProperty(idWriteOffRateField, writeOffItemLM.findProperty("writeOffRateCountryItem").getMapping(defaultCountryObject, itemKey),
                    LM.object(writeOffItemLM.findClass("WriteOffRate")).getMapping(writeOffRateKey)));
            fields.add(idWriteOffRateField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).idWriteOffRate);

        }

        if (showItemField(itemsList, "retailMarkup")) {

            ImportField idRetailCalcPriceListTypeField = new ImportField(findProperty("idCalcPriceListType"));
            ImportKey<?> retailCalcPriceListTypeKey = new ImportKey((ConcreteCustomClass) findClass("CalcPriceListType"),
                    findProperty("calcPriceListTypeId").getMapping(idRetailCalcPriceListTypeField));
            keys.add(retailCalcPriceListTypeKey);
            props.add(new ImportProperty(idRetailCalcPriceListTypeField, findProperty("idCalcPriceListType").getMapping(retailCalcPriceListTypeKey)));
            fields.add(idRetailCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add("retail");

            ImportField nameRetailCalcPriceListTypeField = new ImportField(findProperty("namePriceListType"));
            props.add(new ImportProperty(nameRetailCalcPriceListTypeField, findProperty("namePriceListType").getMapping(retailCalcPriceListTypeKey)));
            fields.add(nameRetailCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add("Розничная надбавка");

            ImportField retailMarkupCalcPriceListTypeField = new ImportField(findProperty("dataMarkupCalcPriceListTypeSku"));
            props.add(new ImportProperty(retailMarkupCalcPriceListTypeField, findProperty("dataMarkupCalcPriceListTypeSku").getMapping(retailCalcPriceListTypeKey, itemKey)));
            fields.add(retailMarkupCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).retailMarkup);

        }

        if (showItemField(itemsList, "baseMarkup")) {

            ImportField idBaseCalcPriceListTypeField = new ImportField(findProperty("idCalcPriceListType"));
            ImportKey<?> baseCalcPriceListTypeKey = new ImportKey((ConcreteCustomClass) findClass("CalcPriceListType"),
                    findProperty("calcPriceListTypeId").getMapping(idBaseCalcPriceListTypeField));
            keys.add(baseCalcPriceListTypeKey);
            props.add(new ImportProperty(idBaseCalcPriceListTypeField, findProperty("idCalcPriceListType").getMapping(baseCalcPriceListTypeKey)));
            fields.add(idBaseCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add("wholesale");

            ImportField nameBaseCalcPriceListTypeField = new ImportField(findProperty("namePriceListType"));
            props.add(new ImportProperty(nameBaseCalcPriceListTypeField, findProperty("namePriceListType").getMapping(baseCalcPriceListTypeKey)));
            fields.add(nameBaseCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add("Оптовая надбавка");

            ImportField baseMarkupCalcPriceListTypeField = new ImportField(findProperty("dataMarkupCalcPriceListTypeSku"));
            props.add(new ImportProperty(baseMarkupCalcPriceListTypeField, findProperty("dataMarkupCalcPriceListTypeSku").getMapping(baseCalcPriceListTypeKey, itemKey)));
            fields.add(baseMarkupCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).baseMarkup);
        }

        ImportField extIdBarcodePackField = new ImportField(findProperty("extIdBarcode"));
        ImportKey<?> barcodePackKey = new ImportKey((ConcreteCustomClass) findClass("Barcode"),
                findProperty(/*"barcodeIdDate"*/"extBarcodeId").getMapping(extIdBarcodePackField));
        keys.add(barcodePackKey);
        props.add(new ImportProperty(dateField, findProperty("dataDateBarcode").getMapping(barcodePackKey)));
        props.add(new ImportProperty(idItemField, findProperty("skuBarcode").getMapping(barcodePackKey),
                LM.object(findClass("Item")).getMapping(itemKey)));
        props.add(new ImportProperty(extIdBarcodePackField, findProperty("extIdBarcode").getMapping(barcodePackKey)));
        props.add(new ImportProperty(extIdBarcodePackField, findProperty("Purchase.packBarcodeSku").getMapping(itemKey),
                LM.object(findClass("Barcode")).getMapping(barcodePackKey)));
        fields.add(extIdBarcodePackField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(valueWithPrefix(itemsList.get(i).idBarcodePack, "P", null));

        ImportField idBarcodePackField = new ImportField(findProperty("idBarcode"));
        props.add(new ImportProperty(idBarcodePackField, findProperty("idBarcode").getMapping(barcodePackKey)));
        if(salePackLM != null) {
            props.add(new ImportProperty(extIdBarcodePackField, salePackLM.findProperty("Sale.packBarcodeSku").getMapping(itemKey),
                    salePackLM.object(salePackLM.findClass("Barcode")).getMapping(barcodePackKey)));
        }
        fields.add(idBarcodePackField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(null);

        if (showItemField(itemsList, "amountPack")) {
            ImportField amountBarcodePackField = new ImportField(findProperty("amountBarcode"));
            props.add(new ImportProperty(amountBarcodePackField, findProperty("amountBarcode").getMapping(barcodePackKey)));
            fields.add(amountBarcodePackField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).amountPack);
        }

        if (showItemField(itemsList, "idUOMPack")) {
            ImportField idUOMPackField = new ImportField(findProperty("idUOM"));
            props.add(new ImportProperty(idUOMPackField, findProperty("UOMBarcode").getMapping(barcodePackKey),
                    LM.object(findClass("UOM")).getMapping(UOMKey)));
            fields.add(idUOMPackField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).idUOMPack);
        }

        if (showItemField(itemsList, "idManufacturer")) {
            ImportField idManufacturerField = new ImportField(findProperty("idManufacturer"));
            ImportKey<?> manufacturerKey = new ImportKey((ConcreteCustomClass) findClass("Manufacturer"),
                    findProperty("manufacturerId").getMapping(idManufacturerField));
            keys.add(manufacturerKey);
            props.add(new ImportProperty(idManufacturerField, findProperty("idManufacturer").getMapping(manufacturerKey)));
            props.add(new ImportProperty(idManufacturerField, findProperty("manufacturerItem").getMapping(itemKey),
                    LM.object(findClass("Manufacturer")).getMapping(manufacturerKey)));
            fields.add(idManufacturerField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).idManufacturer);

            ImportField nameManufacturerField = new ImportField(findProperty("nameManufacturer"));
            props.add(new ImportProperty(nameManufacturerField, findProperty("nameManufacturer").getMapping(manufacturerKey)));
            fields.add(nameManufacturerField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).nameManufacturer);
        }

        if (showItemField(itemsList, "codeCustomsGroup")) {
            ImportField codeCustomsGroupField = new ImportField(findProperty("codeCustomsGroup"));
            ImportKey<?> customsGroupKey = new ImportKey((CustomClass) findClass("CustomsGroup"),
                    findProperty("customsGroupCode").getMapping(codeCustomsGroupField));
            keys.add(customsGroupKey);
            props.add(new ImportProperty(codeCustomsGroupField, findProperty("codeCustomsGroup").getMapping(customsGroupKey)));
            props.add(new ImportProperty(codeCustomsGroupField, findProperty("customsGroupCountryItem").getMapping(countryKey, itemKey),
                    LM.object(findClass("CustomsGroup")).getMapping(customsGroupKey)));
            fields.add(codeCustomsGroupField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).codeCustomsGroup);

            ImportField nameCustomsZoneField = new ImportField(findProperty("nameCustomsZone"));
            ImportKey<?> customsZoneKey = new ImportKey((CustomClass) findClass("CustomsZone"),
                    findProperty("customsZoneName").getMapping(nameCustomsZoneField));
            keys.add(customsZoneKey);
            props.add(new ImportProperty(nameCustomsZoneField, findProperty("nameCustomsZone").getMapping(customsZoneKey)));
            props.add(new ImportProperty(nameCustomsZoneField, findProperty("customsZoneCustomsGroup").getMapping(customsGroupKey),
                    LM.object(findClass("CustomsZone")).getMapping(customsZoneKey)));
            fields.add(nameCustomsZoneField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).nameCustomsZone);
        }

        ImportTable table = new ImportTable(fields, data);

        DataSession session = context.createSession();
        session.pushVolatileStats("IA_POI");
        IntegrationService service = new IntegrationService(session, table, keys, props);
        service.synchronize(true, false);
        session.apply(context);
        session.popVolatileStats();
        session.close();
    }

    private void importUserInvoices(List<UserInvoiceDetail> userInvoiceDetailsList, Integer numberAtATime, boolean skipKeys,
                                    boolean userInvoiceCreateNewItems)
            throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (notNullNorEmpty(userInvoiceDetailsList)) {

            if (numberAtATime == null)
                numberAtATime = userInvoiceDetailsList.size();

            for (int start = 0; true; start += numberAtATime) {

                int finish = (start + numberAtATime) < userInvoiceDetailsList.size() ? (start + numberAtATime) : userInvoiceDetailsList.size();
                List<UserInvoiceDetail> dataUserInvoiceDetail = start < finish ? userInvoiceDetailsList.subList(start, finish) : new ArrayList<UserInvoiceDetail>();
                if (dataUserInvoiceDetail.isEmpty())
                    return;

                ServerLoggers.systemLogger.info("importUserInvoices " + dataUserInvoiceDetail.size());
                
                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                List<List<Object>> data = initData(dataUserInvoiceDetail.size());

                ImportField idUserInvoiceField = new ImportField(findProperty("Purchase.idUserInvoice"));
                ImportKey<?> userInvoiceKey = new ImportKey((ConcreteCustomClass) findClass("Purchase.UserInvoice"),
                        findProperty("Purchase.userInvoiceId").getMapping(idUserInvoiceField));
                keys.add(userInvoiceKey);
                props.add(new ImportProperty(idUserInvoiceField, findProperty("Purchase.idUserInvoice").getMapping(userInvoiceKey)));
                fields.add(idUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idUserInvoice);

                ImportField idUserInvoiceDetailField = new ImportField(findProperty("Purchase.idUserInvoiceDetail"));
                ImportKey<?> userInvoiceDetailKey = new ImportKey((ConcreteCustomClass) findClass("Purchase.UserInvoiceDetail"),
                        findProperty("Purchase.userInvoiceDetailId").getMapping(idUserInvoiceDetailField));
                keys.add(userInvoiceDetailKey);
                props.add(new ImportProperty(idUserInvoiceDetailField, findProperty("Purchase.idUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                props.add(new ImportProperty(idUserInvoiceField, findProperty("Purchase.userInvoiceUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(findClass("Purchase.UserInvoice")).getMapping(userInvoiceKey)));
                fields.add(idUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idUserInvoiceDetail);


                ImportField idCustomerStockField = new ImportField(findProperty("idStock"));
                ImportKey<?> customerStockKey = new ImportKey((CustomClass) findClass("Stock"),
                        findProperty("stockId").getMapping(idCustomerStockField));
                customerStockKey.skipKey = skipKeys;
                keys.add(customerStockKey);
                props.add(new ImportProperty(idCustomerStockField, findProperty("idStock").getMapping(customerStockKey)));
                props.add(new ImportProperty(idCustomerStockField, findProperty("Purchase.customerUserInvoice").getMapping(userInvoiceKey),
                        findProperty("legalEntityStock").getMapping(customerStockKey)));
                props.add(new ImportProperty(idCustomerStockField, findProperty("Purchase.customerStockUserInvoice").getMapping(userInvoiceKey),
                        LM.object(findClass("Stock")).getMapping(customerStockKey)));
                fields.add(idCustomerStockField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idCustomerStock);


                ImportField idSupplierField = new ImportField(findProperty("idLegalEntity"));
                ImportKey<?> supplierKey = new ImportKey((ConcreteCustomClass) findClass("LegalEntity"),
                        findProperty("legalEntityId").getMapping(idSupplierField));
                supplierKey.skipKey = skipKeys;
                keys.add(supplierKey);
                props.add(new ImportProperty(idSupplierField, findProperty("Purchase.supplierUserInvoice").getMapping(userInvoiceKey),
                        LM.object(findClass("LegalEntity")).getMapping(supplierKey)));
                fields.add(idSupplierField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idSupplier);


                ImportField idSupplierStockField = new ImportField(findProperty("idStock"));
                ImportKey<?> supplierStockKey = new ImportKey((CustomClass) findClass("Stock"),
                        findProperty("stockId").getMapping(idSupplierStockField));
                supplierStockKey.skipKey = skipKeys;
                keys.add(supplierStockKey);
                props.add(new ImportProperty(idSupplierStockField, findProperty("idStock").getMapping(supplierStockKey)));
                props.add(new ImportProperty(idSupplierStockField, findProperty("Purchase.supplierStockUserInvoice").getMapping(userInvoiceKey),
                        LM.object(findClass("Stock")).getMapping(supplierStockKey)));
                fields.add(idSupplierStockField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idSupplierStock);


                ImportField numberUserInvoiceField = new ImportField(findProperty("Purchase.numberUserInvoice"));
                props.add(new ImportProperty(numberUserInvoiceField, findProperty("Purchase.numberUserInvoice").getMapping(userInvoiceKey)));
                fields.add(numberUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).number);

                if (showField(dataUserInvoiceDetail, "series")) {
                    ImportField seriesUserInvoiceField = new ImportField(findProperty("Purchase.seriesUserInvoice"));
                    props.add(new ImportProperty(seriesUserInvoiceField, findProperty("Purchase.seriesUserInvoice").getMapping(userInvoiceKey)));
                    fields.add(seriesUserInvoiceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).series);
                }

                if (pricingPurchaseLM != null) {

                    ImportField createPricingUserInvoiceField = new ImportField(pricingPurchaseLM.findProperty("createPricingUserInvoice"));
                    props.add(new ImportProperty(createPricingUserInvoiceField, pricingPurchaseLM.findProperty("createPricingUserInvoice").getMapping(userInvoiceKey), true));
                    fields.add(createPricingUserInvoiceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).createPricing);

                    ImportField retailPriceUserInvoiceDetailField = new ImportField(pricingPurchaseLM.findProperty("retailPriceUserInvoiceDetail"));
                    props.add(new ImportProperty(retailPriceUserInvoiceDetailField, pricingPurchaseLM.findProperty("retailPriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(retailPriceUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).retailPrice);

                    ImportField retailMarkupUserInvoiceDetailField = new ImportField(pricingPurchaseLM.findProperty("retailMarkupUserInvoiceDetail"));
                    props.add(new ImportProperty(retailMarkupUserInvoiceDetailField, pricingPurchaseLM.findProperty("retailMarkupUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(retailMarkupUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).retailMarkup);

                }

                ImportField createShipmentUserInvoiceField = new ImportField(findProperty("Purchase.createShipmentUserInvoice"));
                props.add(new ImportProperty(createShipmentUserInvoiceField, findProperty("Purchase.createShipmentUserInvoice").getMapping(userInvoiceKey), true));
                fields.add(createShipmentUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).createShipment);

                if (showField(dataUserInvoiceDetail, "manufacturingPrice")) {
                    ImportField showManufacturingPriceUserInvoiceField = new ImportField(findProperty("Purchase.showManufacturingPriceUserInvoice"));
                    props.add(new ImportProperty(showManufacturingPriceUserInvoiceField, findProperty("Purchase.showManufacturingPriceUserInvoice").getMapping(userInvoiceKey)));
                    fields.add(showManufacturingPriceUserInvoiceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                }

                if (purchaseInvoiceWholesalePriceLM != null) {

                    ImportField showWholesalePriceUserInvoiceField = new ImportField(purchaseInvoiceWholesalePriceLM.findProperty("Purchase.showWholesalePriceUserInvoice"));
                    props.add(new ImportProperty(showWholesalePriceUserInvoiceField, purchaseInvoiceWholesalePriceLM.findProperty("Purchase.showWholesalePriceUserInvoice").getMapping(userInvoiceKey), true));
                    fields.add(showWholesalePriceUserInvoiceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                    
                    ImportField wholesalePriceUserInvoiceDetailField = new ImportField(purchaseInvoiceWholesalePriceLM.findProperty("Purchase.wholesalePriceUserInvoiceDetail"));
                    props.add(new ImportProperty(wholesalePriceUserInvoiceDetailField, purchaseInvoiceWholesalePriceLM.findProperty("Purchase.wholesalePriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(wholesalePriceUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).wholesalePrice);

                    ImportField wholesaleMarkupUserInvoiceDetailField = new ImportField(purchaseInvoiceWholesalePriceLM.findProperty("Purchase.wholesaleMarkupUserInvoiceDetail"));
                    props.add(new ImportProperty(wholesaleMarkupUserInvoiceDetailField, purchaseInvoiceWholesalePriceLM.findProperty("Purchase.wholesaleMarkupUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(wholesaleMarkupUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).wholesaleMarkup);

                }

                ImportField dateUserInvoiceField = new ImportField(findProperty("Purchase.dateUserInvoice"));
                props.add(new ImportProperty(dateUserInvoiceField, findProperty("Purchase.dateUserInvoice").getMapping(userInvoiceKey)));
                fields.add(dateUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).date);


                ImportField timeUserInvoiceField = new ImportField(TimeClass.instance);
                props.add(new ImportProperty(timeUserInvoiceField, findProperty("Purchase.timeUserInvoice").getMapping(userInvoiceKey)));
                fields.add(timeUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(new Time(12, 0, 0));


                ImportField idItemField = new ImportField(findProperty("idItem"));
                ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                        findProperty("itemId").getMapping(idItemField));
                itemKey.skipKey = skipKeys && !userInvoiceCreateNewItems;
                keys.add(itemKey);
                if (userInvoiceCreateNewItems)
                    props.add(new ImportProperty(idItemField, findProperty("idItem").getMapping(itemKey)));
                props.add(new ImportProperty(idItemField, findProperty("Purchase.skuUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(findClass("Sku")).getMapping(itemKey)));
                fields.add(idItemField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idItem);


                ImportField quantityUserInvoiceDetailField = new ImportField(findProperty("Purchase.quantityUserInvoiceDetail"));
                props.add(new ImportProperty(quantityUserInvoiceDetailField, findProperty("Purchase.quantityUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(quantityUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).quantity);


                ImportField priceUserInvoiceDetail = new ImportField(findProperty("Purchase.priceUserInvoiceDetail"));
                props.add(new ImportProperty(priceUserInvoiceDetail, findProperty("Purchase.priceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(priceUserInvoiceDetail);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).price);
                
                if (showField(dataUserInvoiceDetail, "shipmentPrice")) {
                    ImportField shipmentPriceInvoiceDetail = new ImportField(findProperty("Purchase.shipmentPriceUserInvoiceDetail"));
                    props.add(new ImportProperty(shipmentPriceInvoiceDetail, findProperty("Purchase.shipmentPriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(shipmentPriceInvoiceDetail);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).shipmentPrice);
                }

                if (showField(dataUserInvoiceDetail, "shipmentSum")) {
                    ImportField shipmentSumInvoiceDetail = new ImportField(findProperty("Purchase.shipmentSumUserInvoiceDetail"));
                    props.add(new ImportProperty(shipmentSumInvoiceDetail, findProperty("Purchase.shipmentSumUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(shipmentSumInvoiceDetail);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).shipmentSum);
                }
                    
                ImportField expiryDateUserInvoiceDetailField = new ImportField(findProperty("Purchase.expiryDateUserInvoiceDetail"));
                props.add(new ImportProperty(expiryDateUserInvoiceDetailField, findProperty("Purchase.expiryDateUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(expiryDateUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).expiryDate);

                if (!skipExtraInvoiceParams) {

                    ImportField dataRateExchangeUserInvoiceDetailField = new ImportField(findProperty("Purchase.dataRateExchangeUserInvoiceDetail"));
                    props.add(new ImportProperty(dataRateExchangeUserInvoiceDetailField, findProperty("Purchase.dataRateExchangeUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(dataRateExchangeUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).rateExchange);


                    ImportField homePriceUserInvoiceDetailField = new ImportField(findProperty("Purchase.homePriceUserInvoiceDetail"));
                    props.add(new ImportProperty(homePriceUserInvoiceDetailField, findProperty("Purchase.homePriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(homePriceUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).homePrice);


                    if (showField(dataUserInvoiceDetail, "priceDuty")) {
                        ImportField priceDutyUserInvoiceDetailField = new ImportField(findProperty("Purchase.dutyPriceUserInvoiceDetail"));
                        props.add(new ImportProperty(priceDutyUserInvoiceDetailField, findProperty("Purchase.dutyPriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                        fields.add(priceDutyUserInvoiceDetailField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).priceDuty);
                    }

                    if (showField(dataUserInvoiceDetail, "priceCompliance") && purchaseComplianceDetailLM != null) {
                        ImportField priceComplianceUserInvoiceDetailField = new ImportField(purchaseComplianceDetailLM.findProperty("Purchase.compliancePriceUserInvoiceDetail"));
                        props.add(new ImportProperty(priceComplianceUserInvoiceDetailField, purchaseComplianceDetailLM.findProperty("Purchase.compliancePriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                        fields.add(priceComplianceUserInvoiceDetailField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).priceCompliance);

                        ImportField showComplianceUserInvoiceDetailField = new ImportField(purchaseComplianceDetailLM.findProperty("showComplianceUserInvoice"));
                        props.add(new ImportProperty(showComplianceUserInvoiceDetailField, purchaseComplianceDetailLM.findProperty("showComplianceUserInvoice").getMapping(userInvoiceKey)));
                        fields.add(showComplianceUserInvoiceDetailField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(true);
                    }

                    if (showField(dataUserInvoiceDetail, "priceRegistration")) {
                        ImportField priceRegistrationUserInvoiceDetailField = new ImportField(findProperty("Purchase.registrationPriceUserInvoiceDetail"));
                        props.add(new ImportProperty(priceRegistrationUserInvoiceDetailField, findProperty("Purchase.registrationPriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                        fields.add(priceRegistrationUserInvoiceDetailField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).priceRegistration);
                    }

                    if (showField(dataUserInvoiceDetail, "chargePrice") || showField(dataUserInvoiceDetail, "chargeSum")) {

                        if (purchaseInvoiceChargeLM != null) {
                            ImportField chargePriceUserInvoiceDetailField = new ImportField(purchaseInvoiceChargeLM.findProperty("chargePriceUserInvoiceDetail"));
                            props.add(new ImportProperty(chargePriceUserInvoiceDetailField, purchaseInvoiceChargeLM.findProperty("chargePriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                            fields.add(chargePriceUserInvoiceDetailField);
                            for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                                data.get(i).add(dataUserInvoiceDetail.get(i).chargePrice);

                            ImportField chargeSumUserInvoiceDetailField = new ImportField(purchaseInvoiceChargeLM.findProperty("chargeSumUserInvoiceDetail"));
                            props.add(new ImportProperty(chargeSumUserInvoiceDetailField, purchaseInvoiceChargeLM.findProperty("chargeSumUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                            fields.add(chargeSumUserInvoiceDetailField);
                            for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                                data.get(i).add(dataUserInvoiceDetail.get(i).chargeSum);

                            ImportField showChargePriceUserInvoiceDetailField = new ImportField(purchaseInvoiceChargeLM.findProperty("showChargePriceUserInvoice"));
                            props.add(new ImportProperty(showChargePriceUserInvoiceDetailField, purchaseInvoiceChargeLM.findProperty("showChargePriceUserInvoice").getMapping(userInvoiceKey)));
                            fields.add(showChargePriceUserInvoiceDetailField);
                            for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                                data.get(i).add(true);
                        }
                    }
                }

                if (showField(dataUserInvoiceDetail, "idBin")) {
                    ImportField binUserInvoiceDetailField = new ImportField(findProperty("idBin"));
                    ImportKey<?> binKey = new ImportKey((ConcreteCustomClass) findClass("Bin"),
                            findProperty("binId").getMapping(binUserInvoiceDetailField));
                    keys.add(binKey);
                    props.add(new ImportProperty(binUserInvoiceDetailField, findProperty("idBin").getMapping(binKey)));
                    props.add(new ImportProperty(binUserInvoiceDetailField, findProperty("nameBin").getMapping(binKey)));
                    props.add(new ImportProperty(binUserInvoiceDetailField, findProperty("binUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                            LM.object(findClass("Bin")).getMapping(binKey)));
                    fields.add(binUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).idBin);

                    ImportField showBinUserInvoiceField = new ImportField(findProperty("showBinUserInvoice"));
                    props.add(new ImportProperty(showBinUserInvoiceField, findProperty("showBinUserInvoice").getMapping(userInvoiceKey)));
                    fields.add(showBinUserInvoiceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                }

                if (!skipExtraInvoiceParams) {
                    if (purchaseManufacturingPriceLM != null) {
                        ImportField manufacturingPriceUserInvoiceDetailField = new ImportField(purchaseManufacturingPriceLM.findProperty("manufacturingPriceUserInvoiceDetail"));
                        props.add(new ImportProperty(manufacturingPriceUserInvoiceDetailField, purchaseManufacturingPriceLM.findProperty("manufacturingPriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                        fields.add(manufacturingPriceUserInvoiceDetailField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).manufacturingPrice);

                        ImportField manufacturingMarkupUserInvoiceDetailField = new ImportField(purchaseManufacturingPriceLM.findProperty("manufacturingMarkupUserInvoiceDetail"));
                        props.add(new ImportProperty(manufacturingMarkupUserInvoiceDetailField, purchaseManufacturingPriceLM.findProperty("manufacturingMarkupUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                        fields.add(manufacturingMarkupUserInvoiceDetailField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).manufacturingMarkup);
                    }
                }
                
                if(purchaseCertificateLM != null) {
                    ImportField certificateTextUserInvoiceDetailField = new ImportField(purchaseCertificateLM.findProperty("certificateTextUserInvoiceDetail"));
                    props.add(new ImportProperty(certificateTextUserInvoiceDetailField, purchaseCertificateLM.findProperty("certificateTextUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(certificateTextUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).certificateText);
                }
                    
                if (warePurchaseInvoiceLM != null) {
                    ImportField skipCreateWareUserInvoiceDetailField = new ImportField(warePurchaseInvoiceLM.findProperty("skipCreateWareUserInvoiceDetail"));
                    props.add(new ImportProperty(skipCreateWareUserInvoiceDetailField, warePurchaseInvoiceLM.findProperty("skipCreateWareUserInvoiceDetail").getMapping(userInvoiceDetailKey), true));
                    fields.add(skipCreateWareUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                }


                if (showField(dataUserInvoiceDetail, "idContract")) {
                    ImportField userContractSkuField = new ImportField(findProperty("idUserContractSku"));
                    ImportKey<?> userContractSkuKey = new ImportKey((ConcreteCustomClass) findClass("UserContractSku"),
                            findProperty("userContractSkuId").getMapping(userContractSkuField));
                    userContractSkuKey.skipKey = skipKeys;
                    keys.add(userContractSkuKey);
                    props.add(new ImportProperty(userContractSkuField, findProperty("Purchase.contractSkuInvoice").getMapping(userInvoiceKey),
                            LM.object(findClass("Contract")).getMapping(userContractSkuKey)));
                    fields.add(userContractSkuField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).idContract);
                }
                
                if (!skipCertificateInvoiceParams) {
                    if (showField(dataUserInvoiceDetail, "numberDeclaration")) {
                        ImportField numberDeclarationField = new ImportField(findProperty("numberDeclaration"));
                        ImportKey<?> declarationKey = new ImportKey((ConcreteCustomClass) findClass("Declaration"),
                                findProperty("declarationId").getMapping(numberDeclarationField));
                        keys.add(declarationKey);
                        props.add(new ImportProperty(numberDeclarationField, findProperty("numberDeclaration").getMapping(declarationKey)));
                        props.add(new ImportProperty(numberDeclarationField, findProperty("idDeclaration").getMapping(declarationKey)));
                        props.add(new ImportProperty(numberDeclarationField, findProperty("Purchase.declarationUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                                LM.object(findClass("Declaration")).getMapping(declarationKey)));
                        fields.add(numberDeclarationField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).numberDeclaration);

                        ImportField dateDeclarationField = new ImportField(findProperty("dateDeclaration"));
                        props.add(new ImportProperty(dateDeclarationField, findProperty("dateDeclaration").getMapping(declarationKey)));
                        fields.add(dateDeclarationField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).dateDeclaration);
                    }

                    if (showField(dataUserInvoiceDetail, "numberCompliance")) {
                        ImportField numberComplianceField = new ImportField(findProperty("numberCompliance"));
                        ImportKey<?> complianceKey = new ImportKey((ConcreteCustomClass) findClass("Compliance"),
                                findProperty("complianceNumber").getMapping(numberComplianceField));
                        keys.add(complianceKey);
                        props.add(new ImportProperty(numberComplianceField, findProperty("numberCompliance").getMapping(complianceKey)));
                        props.add(new ImportProperty(numberComplianceField, findProperty("Purchase.complianceUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                                LM.object(findClass("Compliance")).getMapping(complianceKey)));
                        fields.add(numberComplianceField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).numberCompliance);

                        ImportField fromDateComplianceField = new ImportField(findProperty("fromDateCompliance"));
                        props.add(new ImportProperty(fromDateComplianceField, findProperty("dateCompliance").getMapping(complianceKey)));
                        props.add(new ImportProperty(fromDateComplianceField, findProperty("fromDateCompliance").getMapping(complianceKey)));
                        fields.add(fromDateComplianceField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).fromDateCompliance);

                        ImportField toDateComplianceField = new ImportField(findProperty("toDateCompliance"));
                        props.add(new ImportProperty(toDateComplianceField, findProperty("toDateCompliance").getMapping(complianceKey)));
                        fields.add(toDateComplianceField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).toDateCompliance);
                    }
                }
                
                if (!skipExtraInvoiceParams) {
                    if (showField(dataUserInvoiceDetail, "isHomeCurrency")) {
                        ImportField isHomeCurrencyUserInvoiceField = new ImportField(findProperty("Purchase.isHomeCurrencyUserInvoice"));
                        props.add(new ImportProperty(isHomeCurrencyUserInvoiceField, findProperty("Purchase.isHomeCurrencyUserInvoice").getMapping(userInvoiceKey), true));
                        fields.add(isHomeCurrencyUserInvoiceField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).isHomeCurrency);
                    }
                }

                if (showField(dataUserInvoiceDetail, "codeCustomsGroup") || showField(dataUserInvoiceDetail, "priceDuty") || showField(dataUserInvoiceDetail, "priceRegistration")) {
                    ImportField showDeclarationUserInvoiceField = new ImportField(findProperty("showDeclarationUserInvoice"));
                    props.add(new ImportProperty(showDeclarationUserInvoiceField, findProperty("showDeclarationUserInvoice").getMapping(userInvoiceKey), true));
                    fields.add(showDeclarationUserInvoiceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                }

                ImportField shortNameCurrencyField = new ImportField(findProperty("shortNameCurrency"));
                ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) findClass("Currency"),
                        findProperty("currencyShortName").getMapping(shortNameCurrencyField));
                keys.add(currencyKey);
                props.add(new ImportProperty(shortNameCurrencyField, findProperty("Purchase.currencyUserInvoice").getMapping(userInvoiceKey),
                        LM.object(findClass("Currency")).getMapping(currencyKey)));
                fields.add(shortNameCurrencyField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).shortNameCurrency);
                
                if (!skipCertificateInvoiceParams) {
                    if (purchaseDeclarationDetailLM != null) {
                        ObjectValue defaultCountryObject = findProperty("defaultCountry").readClasses(context.getSession());
                        ImportField codeCustomsGroupField = new ImportField(purchaseDeclarationDetailLM.findProperty("codeCustomsGroup"));
                        ImportKey<?> customsGroupKey = new ImportKey((CustomClass) purchaseDeclarationDetailLM.findClass("CustomsGroup"),
                                purchaseDeclarationDetailLM.findProperty("customsGroupCode").getMapping(codeCustomsGroupField));
                        keys.add(customsGroupKey);
                        props.add(new ImportProperty(codeCustomsGroupField, purchaseDeclarationDetailLM.findProperty("customsGroupUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                                purchaseDeclarationDetailLM.object(purchaseDeclarationDetailLM.findClass("CustomsGroup")).getMapping(customsGroupKey)));
                        props.add(new ImportProperty(codeCustomsGroupField, purchaseDeclarationDetailLM.findProperty("customsGroupCountryItem").getMapping(defaultCountryObject, itemKey),
                                purchaseDeclarationDetailLM.object(purchaseDeclarationDetailLM.findClass("CustomsGroup")).getMapping(customsGroupKey)));
                        fields.add(codeCustomsGroupField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).codeCustomsGroup);
                    }
                }
                    
                ImportField valueVATUserInvoiceDetailField = new ImportField(findProperty("Purchase.valueVATUserInvoiceDetail"));
                ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) findClass("Range"),
                        findProperty("valueCurrentVATDefaultValue").getMapping(valueVATUserInvoiceDetailField));
                VATKey.skipKey = true;
                keys.add(VATKey);
                props.add(new ImportProperty(valueVATUserInvoiceDetailField, findProperty("Purchase.VATUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(findClass("Range")).getMapping(VATKey)));
                fields.add(valueVATUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).retailVAT);

                if (tripInvoiceLM != null && showField(dataUserInvoiceDetail, "numberTrip")) {
                    ImportField numberTripField = new ImportField(tripInvoiceLM.findProperty("numberTrip"));
                    ImportKey<?> tripKey = new ImportKey((ConcreteCustomClass) tripInvoiceLM.findClass("Trip"),
                            tripInvoiceLM.findProperty("tripNumber").getMapping(numberTripField));
                    keys.add(tripKey);
                    props.add(new ImportProperty(numberTripField, tripInvoiceLM.findProperty("numberTrip").getMapping(tripKey)));
                    props.add(new ImportProperty(numberTripField, tripInvoiceLM.findProperty("tripInvoice").getMapping(userInvoiceKey),
                            object(tripInvoiceLM.findClass("Trip")).getMapping(tripKey)));
                    fields.add(numberTripField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).numberTrip);

                    ImportField dateTripField = new ImportField(tripInvoiceLM.findProperty("dateTrip"));
                    props.add(new ImportProperty(dateTripField, tripInvoiceLM.findProperty("dateTrip").getMapping(tripKey)));
                    fields.add(dateTripField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).dateTrip);
                }

                ImportTable table = new ImportTable(fields, data);

                DataSession session = context.createSession();
                session.pushVolatileStats("IA_UI");
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context);
                session.popVolatileStats();
                session.close();
            }
        }
    }

    private void importPriceListStores(List<PriceListStore> priceListStoresList, Integer numberAtATime, boolean skipKeys) 
            throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (notNullNorEmpty(priceListStoresList) && importUserPriceListLM != null && storeLM != null) {

            if (numberAtATime == null)
                numberAtATime = priceListStoresList.size();

            for (int start = 0; true; start += numberAtATime) {

                int finish = (start + numberAtATime) < priceListStoresList.size() ? (start + numberAtATime) : priceListStoresList.size();
                List<PriceListStore> dataPriceListStores = start < finish ? priceListStoresList.subList(start, finish) : new ArrayList<PriceListStore>();
                if (dataPriceListStores.isEmpty())
                    return;

                ServerLoggers.systemLogger.info("importPriceListStores " + dataPriceListStores.size());

                DataSession session = context.createSession();
                session.pushVolatileStats("IA_PLSE");

                ObjectValue dataPriceListTypeObject = findProperty("dataPriceListTypeId").readClasses(session, new DataObject("Coordinated", StringClass.get(100)));
                if (dataPriceListTypeObject instanceof NullValue) {
                    dataPriceListTypeObject = session.addObject((ConcreteCustomClass) findClass("DataPriceListType"));
                    Object defaultCurrency = findProperty("currencyShortName").read(session, new DataObject("BLR", StringClass.get(3)));
                    findProperty("namePriceListType").change("Поставщика (согласованная)", session, (DataObject) dataPriceListTypeObject);
                    findProperty("currencyDataPriceListType").change(defaultCurrency, session, (DataObject) dataPriceListTypeObject);
                    findProperty("idDataPriceListType").change("Coordinated", session, (DataObject) dataPriceListTypeObject);
                }

                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                List<List<Object>> data = initData(priceListStoresList.size());

                ImportField idItemField = new ImportField(findProperty("idItem"));
                ImportField idUserPriceListField = new ImportField(findProperty("idUserPriceList"));
                ImportKey<?> itemKey = new ImportKey((ConcreteCustomClass) findClass("Item"),
                        findProperty("itemId").getMapping(idItemField));
                keys.add(itemKey);
                ImportKey<?> userPriceListDetailKey = new ImportKey((ConcreteCustomClass) findClass("UserPriceListDetail"),
                        importUserPriceListLM.findProperty("userPriceListDetailIdSkuIdUserPriceList").getMapping(idItemField, idUserPriceListField));
                keys.add(userPriceListDetailKey);
                ImportKey<?> userPriceListKey = new ImportKey((ConcreteCustomClass) findClass("UserPriceList"),
                        findProperty("userPriceListId").getMapping(idUserPriceListField));
                keys.add(userPriceListKey);
                props.add(new ImportProperty(idItemField, findProperty("skuUserPriceListDetail").getMapping(userPriceListDetailKey),
                        LM.object(findClass("Item")).getMapping(itemKey)));
                props.add(new ImportProperty(idUserPriceListField, findProperty("idUserPriceList").getMapping(userPriceListKey)));
                props.add(new ImportProperty(idUserPriceListField, findProperty("userPriceListUserPriceListDetail").getMapping(userPriceListDetailKey),
                        LM.object(findClass("UserPriceList")).getMapping(userPriceListKey)));
                fields.add(idItemField);
                fields.add(idUserPriceListField);
                for (int i = 0; i < priceListStoresList.size(); i++) {
                    data.get(i).add(priceListStoresList.get(i).idItem);
                    data.get(i).add(priceListStoresList.get(i).idPriceList);
                }

                ImportField idLegalEntityField = new ImportField(findProperty("idLegalEntity"));
                ImportKey<?> legalEntityKey = new ImportKey((ConcreteCustomClass) findClass("LegalEntity"),
                        findProperty("legalEntityId").getMapping(idLegalEntityField));
                legalEntityKey.skipKey = skipKeys;
                keys.add(legalEntityKey);
                props.add(new ImportProperty(idLegalEntityField, findProperty("companyUserPriceList").getMapping(userPriceListKey),
                        LM.object(findClass("LegalEntity")).getMapping(legalEntityKey)));
                fields.add(idLegalEntityField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(priceListStoresList.get(i).idSupplier);

                ImportField idDepartmentStoreField = new ImportField(storeLM.findProperty("idDepartmentStore"));
                ImportKey<?> departmentStoreKey = new ImportKey((ConcreteCustomClass) storeLM.findClass("DepartmentStore"),
                        storeLM.findProperty("departmentStoreId").getMapping(idDepartmentStoreField));
                keys.add(departmentStoreKey);
                fields.add(idDepartmentStoreField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(priceListStoresList.get(i).idDepartmentStore);

                ImportField shortNameCurrencyField = new ImportField(findProperty("shortNameCurrency"));
                ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) findClass("Currency"),
                        findProperty("currencyShortName").getMapping(shortNameCurrencyField));
                keys.add(currencyKey);
                props.add(new ImportProperty(shortNameCurrencyField, findProperty("currencyUserPriceList").getMapping(userPriceListKey),
                        LM.object(findClass("Currency")).getMapping(currencyKey)));
                fields.add(shortNameCurrencyField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(priceListStoresList.get(i).shortNameCurrency);

                ImportField pricePriceListDetailField = new ImportField(findProperty("pricePriceListDetailDataPriceListType"));
                props.add(new ImportProperty(pricePriceListDetailField, findProperty("priceUserPriceListDetailDataPriceListType").getMapping(userPriceListDetailKey, dataPriceListTypeObject)));
                fields.add(pricePriceListDetailField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(priceListStoresList.get(i).pricePriceListDetail);

                ImportField inPriceListPriceListTypeField = new ImportField(findProperty("inUserPriceListDataPriceListType"));
                props.add(new ImportProperty(inPriceListPriceListTypeField, findProperty("inUserPriceListDataPriceListType").getMapping(userPriceListKey, dataPriceListTypeObject)));
                fields.add(inPriceListPriceListTypeField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(true);

                ImportField inPriceListStockField = new ImportField(findProperty("inPriceListStock"));
                props.add(new ImportProperty(inPriceListStockField, findProperty("inPriceListStock").getMapping(userPriceListKey, departmentStoreKey)));
                fields.add(inPriceListStockField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(true);

                ImportTable table = new ImportTable(fields, data);

                IntegrationService service = new IntegrationService(session, table, Arrays.asList(userPriceListKey,
                        departmentStoreKey, userPriceListDetailKey, itemKey, legalEntityKey, currencyKey), props);
                service.synchronize(true, false);
                session.apply(context);
                session.popVolatileStats();
                session.close();
            }
        }
    }

    private void importPriceListSuppliers(List<PriceList> priceListSuppliersList, Integer numberAtATime, boolean skipKeys) 
            throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (notNullNorEmpty(priceListSuppliersList)) {

            if (numberAtATime == null)
                numberAtATime = priceListSuppliersList.size();

            for (int start = 0; true; start += numberAtATime) {

                int finish = (start + numberAtATime) < priceListSuppliersList.size() ? (start + numberAtATime) : priceListSuppliersList.size();
                List<PriceList> dataPriceListSuppliers = start < finish ? priceListSuppliersList.subList(start, finish) : new ArrayList<PriceList>();
                if (dataPriceListSuppliers.isEmpty())
                    return;

                ServerLoggers.systemLogger.info("importPriceListSuppliers " + dataPriceListSuppliers.size());

                DataSession session = context.createSession();
                session.pushVolatileStats("IA_PLSR");

                ObjectValue dataPriceListTypeObject = findProperty("dataPriceListTypeId").readClasses(session, new DataObject("Offered", StringClass.get(100)));
                if (dataPriceListTypeObject instanceof NullValue) {
                    dataPriceListTypeObject = session.addObject((ConcreteCustomClass) findClass("DataPriceListType"));
                    Object defaultCurrency = findProperty("currencyShortName").read(session, new DataObject("BLR", StringClass.get(3)));
                    findProperty("namePriceListType").change("Поставщика (предлагаемая)", session, (DataObject) dataPriceListTypeObject);
                    findProperty("currencyDataPriceListType").change(defaultCurrency, session, (DataObject) dataPriceListTypeObject);
                    findProperty("idDataPriceListType").change("Offered", session, (DataObject) dataPriceListTypeObject);
                }

                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                List<List<Object>> data = initData(priceListSuppliersList.size());

                ImportField idItemField = new ImportField(findProperty("idItem"));
                ImportField idUserPriceListField = new ImportField(findProperty("idUserPriceList"));
                ImportKey<?> itemKey = new ImportKey((ConcreteCustomClass) findClass("Item"),
                        findProperty("itemId").getMapping(idItemField));
                keys.add(itemKey);
                ImportKey<?> userPriceListDetailKey = new ImportKey((ConcreteCustomClass) findClass("UserPriceListDetail"),
                        importUserPriceListLM.findProperty("userPriceListDetailIdSkuIdUserPriceList").getMapping(idItemField, idUserPriceListField));
                keys.add(userPriceListDetailKey);
                ImportKey<?> userPriceListKey = new ImportKey((ConcreteCustomClass) findClass("UserPriceList"),
                        findProperty("userPriceListId").getMapping(idUserPriceListField));
                keys.add(userPriceListKey);
                props.add(new ImportProperty(idItemField, findProperty("skuUserPriceListDetail").getMapping(userPriceListDetailKey),
                        LM.object(findClass("Item")).getMapping(itemKey)));
                props.add(new ImportProperty(idUserPriceListField, findProperty("idUserPriceList").getMapping(userPriceListKey)));
                props.add(new ImportProperty(idUserPriceListField, findProperty("userPriceListUserPriceListDetail").getMapping(userPriceListDetailKey),
                        LM.object(findClass("UserPriceList")).getMapping(userPriceListKey)));
                fields.add(idItemField);
                fields.add(idUserPriceListField);
                for (int i = 0; i < priceListSuppliersList.size(); i++) {
                    data.get(i).add(priceListSuppliersList.get(i).idItem);
                    data.get(i).add(priceListSuppliersList.get(i).idPriceList);
                }

                ImportField idLegalEntityField = new ImportField(findProperty("idLegalEntity"));
                ImportKey<?> legalEntityKey = new ImportKey((ConcreteCustomClass) findClass("LegalEntity"),
                        findProperty("legalEntityId").getMapping(idLegalEntityField));
                legalEntityKey.skipKey = skipKeys;
                keys.add(legalEntityKey);
                props.add(new ImportProperty(idLegalEntityField, findProperty("companyUserPriceList").getMapping(userPriceListKey),
                        LM.object(findClass("LegalEntity")).getMapping(legalEntityKey)));
                fields.add(idLegalEntityField);
                for (int i = 0; i < priceListSuppliersList.size(); i++)
                    data.get(i).add(priceListSuppliersList.get(i).idSupplier);

                ImportField shortNameCurrencyField = new ImportField(findProperty("shortNameCurrency"));
                ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) findClass("Currency"),
                        findProperty("currencyShortName").getMapping(shortNameCurrencyField));
                keys.add(currencyKey);
                props.add(new ImportProperty(shortNameCurrencyField, findProperty("currencyUserPriceList").getMapping(userPriceListKey),
                        LM.object(findClass("Currency")).getMapping(currencyKey)));
                fields.add(shortNameCurrencyField);
                for (int i = 0; i < priceListSuppliersList.size(); i++)
                    data.get(i).add(priceListSuppliersList.get(i).shortNameCurrency);

                ImportField pricePriceListDetailField = new ImportField(findProperty("pricePriceListDetailDataPriceListType"));
                props.add(new ImportProperty(pricePriceListDetailField, findProperty("priceUserPriceListDetailDataPriceListType").getMapping(userPriceListDetailKey, dataPriceListTypeObject)));
                fields.add(pricePriceListDetailField);
                for (int i = 0; i < priceListSuppliersList.size(); i++)
                    data.get(i).add(priceListSuppliersList.get(i).pricePriceListDetail);

                ImportField inPriceListPriceListTypeField = new ImportField(findProperty("inUserPriceListDataPriceListType[PriceList,DataPriceListType]"));
                props.add(new ImportProperty(inPriceListPriceListTypeField, findProperty("inUserPriceListDataPriceListType[PriceList,DataPriceListType]").getMapping(userPriceListKey, dataPriceListTypeObject)));
                fields.add(inPriceListPriceListTypeField);
                for (int i = 0; i < priceListSuppliersList.size(); i++)
                    data.get(i).add(true);

                ImportField allStocksUserPriceListField = new ImportField(findProperty("allStocksUserPriceList"));
                props.add(new ImportProperty(allStocksUserPriceListField, findProperty("allStocksUserPriceList").getMapping(userPriceListKey)));
                fields.add(allStocksUserPriceListField);
                for (int i = 0; i < priceListSuppliersList.size(); i++)
                    data.get(i).add(true);

                ImportTable table = new ImportTable(fields, data);

                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context);
                session.popVolatileStats();
                session.close();
            }
        }
    }

    private void importLegalEntities(List<LegalEntity> legalEntitiesList) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (notNullNorEmpty(legalEntitiesList)) {

            ServerLoggers.systemLogger.info("importLegalEntities");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(legalEntitiesList.size());

            ImportField idLegalEntityField = new ImportField(findProperty("idLegalEntity"));
            ImportKey<?> legalEntityKey = new ImportKey((ConcreteCustomClass) findClass("LegalEntity"),
                    findProperty("legalEntityId").getMapping(idLegalEntityField));
            keys.add(legalEntityKey);
            props.add(new ImportProperty(idLegalEntityField, findProperty("idLegalEntity").getMapping(legalEntityKey)));
            fields.add(idLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).idLegalEntity);

            ImportField numberAccountField = new ImportField(findProperty("Bank.numberAccount"));
            ImportKey<?> accountKey = new ImportKey((ConcreteCustomClass) findClass("Bank.Account"),
                    findProperty("accountNumberLegalEntityID").getMapping(numberAccountField, idLegalEntityField));
            keys.add(accountKey);
            props.add(new ImportProperty(numberAccountField, findProperty("Bank.numberAccount").getMapping(accountKey)));
            props.add(new ImportProperty(idLegalEntityField, findProperty("Bank.legalEntityAccount").getMapping(accountKey),
                    LM.object(findClass("LegalEntity")).getMapping(legalEntityKey)));
            fields.add(numberAccountField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).numberAccount);

            ImportField nameLegalEntityField = new ImportField(findProperty("nameLegalEntity"));
            props.add(new ImportProperty(nameLegalEntityField, findProperty("nameLegalEntity").getMapping(legalEntityKey)));
            props.add(new ImportProperty(nameLegalEntityField, findProperty("fullNameLegalEntity").getMapping(legalEntityKey)));
            fields.add(nameLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).nameLegalEntity);

            ImportField addressLegalEntityField = new ImportField(findProperty("addressLegalEntity"));
            props.add(new ImportProperty(addressLegalEntityField, findProperty("dataAddressLegalEntityDate").getMapping(legalEntityKey, defaultDate)));
            fields.add(addressLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).addressLegalEntity);

            if(legalEntityByLM != null) {
                ImportField unpLegalEntityField = new ImportField(legalEntityByLM.findProperty("UNPLegalEntity"));
                props.add(new ImportProperty(unpLegalEntityField, legalEntityByLM.findProperty("UNPLegalEntity").getMapping(legalEntityKey)));
                fields.add(unpLegalEntityField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).unpLegalEntity);

                ImportField okpoLegalEntityField = new ImportField(legalEntityByLM.findProperty("OKPOLegalEntity"));
                props.add(new ImportProperty(okpoLegalEntityField, legalEntityByLM.findProperty("OKPOLegalEntity").getMapping(legalEntityKey)));
                fields.add(okpoLegalEntityField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).okpoLegalEntity);
            }
            
            ImportField phoneLegalEntityField = new ImportField(findProperty("dataPhoneLegalEntityDate"));
            props.add(new ImportProperty(phoneLegalEntityField, findProperty("dataPhoneLegalEntityDate").getMapping(legalEntityKey, defaultDate)));
            fields.add(phoneLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).phoneLegalEntity);

            ImportField emailLegalEntityField = new ImportField(findProperty("emailLegalEntity"));
            props.add(new ImportProperty(emailLegalEntityField, findProperty("emailLegalEntity").getMapping(legalEntityKey)));
            fields.add(emailLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).emailLegalEntity);

            ImportField isSupplierLegalEntityField = new ImportField(findProperty("isSupplierLegalEntity"));
            props.add(new ImportProperty(isSupplierLegalEntityField, findProperty("isSupplierLegalEntity").getMapping(legalEntityKey), true));
            fields.add(isSupplierLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).isSupplierLegalEntity);

            ImportField isCompanyLegalEntityField = new ImportField(findProperty("isCompanyLegalEntity"));
            props.add(new ImportProperty(isCompanyLegalEntityField, findProperty("isCompanyLegalEntity").getMapping(legalEntityKey), true));
            fields.add(isCompanyLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).isCompanyLegalEntity);

            ImportField isCustomerLegalEntityField = new ImportField(findProperty("isCustomerLegalEntity"));
            props.add(new ImportProperty(isCustomerLegalEntityField, findProperty("isCustomerLegalEntity").getMapping(legalEntityKey), true));
            fields.add(isCustomerLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).isCustomerLegalEntity);

            ImportField shortNameOwnershipField = new ImportField(findProperty("shortNameOwnership"));
            ImportKey<?> ownershipKey = new ImportKey((ConcreteCustomClass) findClass("Ownership"),
                    findProperty("ownershipShortName").getMapping(shortNameOwnershipField));
            keys.add(ownershipKey);
            props.add(new ImportProperty(shortNameOwnershipField, findProperty("shortNameOwnership").getMapping(ownershipKey)));
            props.add(new ImportProperty(shortNameOwnershipField, findProperty("ownershipLegalEntity").getMapping(legalEntityKey),
                    LM.object(findClass("Ownership")).getMapping(ownershipKey)));
            fields.add(shortNameOwnershipField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).shortNameOwnership);

            ImportField nameOwnershipField = new ImportField(findProperty("nameOwnership"));
            props.add(new ImportProperty(nameOwnershipField, findProperty("nameOwnership").getMapping(ownershipKey)));
            fields.add(nameOwnershipField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).nameOwnership);

            if (storeLM != null) {

                ImportField idChainStoresField = new ImportField(storeLM.findProperty("idChainStores"));
                ImportKey<?> chainStoresKey = new ImportKey((ConcreteCustomClass) storeLM.findClass("ChainStores"),
                        storeLM.findProperty("chainStoresId").getMapping(idChainStoresField));
                keys.add(chainStoresKey);
                props.add(new ImportProperty(idChainStoresField, storeLM.findProperty("idChainStores").getMapping(chainStoresKey)));
                fields.add(idChainStoresField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).idChainStores);

                ImportField nameChainStoresField = new ImportField(storeLM.findProperty("nameChainStores"));
                props.add(new ImportProperty(nameChainStoresField, storeLM.findProperty("nameChainStores").getMapping(chainStoresKey)));
                fields.add(nameChainStoresField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).nameChainStores);

            }

            ImportField idBankField = new ImportField(findProperty("idBank"));
            ImportKey<?> bankKey = new ImportKey((ConcreteCustomClass) findClass("Bank"),
                    findProperty("bankId").getMapping(idBankField));
            keys.add(bankKey);
            props.add(new ImportProperty(idBankField, findProperty("idBank").getMapping(bankKey)));
            props.add(new ImportProperty(idBankField, findProperty("nameBank").getMapping(bankKey)));
            props.add(new ImportProperty(idBankField, findProperty("Bank.bankAccount").getMapping(accountKey),
                    LM.object(findClass("Bank")).getMapping(bankKey)));
            fields.add(idBankField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).idBank);

            ImportField nameCountryField = new ImportField(findProperty("nameCountry"));
            ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) findClass("Country"),
                    findProperty("countryName").getMapping(nameCountryField));
            keys.add(countryKey);
            props.add(new ImportProperty(nameCountryField, findProperty("nameCountry").getMapping(countryKey)));
            props.add(new ImportProperty(nameCountryField, findProperty("countryLegalEntity").getMapping(legalEntityKey),
                    LM.object(findClass("Country")).getMapping(countryKey)));
            props.add(new ImportProperty(nameCountryField, findProperty("countryOwnership").getMapping(ownershipKey),
                    LM.object(findClass("Country")).getMapping(countryKey)));
            fields.add(nameCountryField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).nameCountry);

            ImportField shortNameCurrencyField = new ImportField(findProperty("shortNameCurrency"));
            ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) findClass("Currency"),
                    findProperty("currencyShortName").getMapping(shortNameCurrencyField));
            keys.add(currencyKey);
            props.add(new ImportProperty(shortNameCurrencyField, findProperty("Bank.currencyAccount").getMapping(accountKey),
                    LM.object(findClass("Currency")).getMapping(currencyKey)));
            fields.add(shortNameCurrencyField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add("BLR");

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.createSession();
            session.pushVolatileStats("IA_LE");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            session.popVolatileStats();
            session.close();
        }
    }

    private void importEmployees(List<Employee> employeesList) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (notNullNorEmpty(employeesList)) {

            ServerLoggers.systemLogger.info("importEmployees");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(employeesList.size());

            ImportField idEmployeeField = new ImportField(findProperty("idEmployee"));
            ImportKey<?> employeeKey = new ImportKey((ConcreteCustomClass) findClass("Employee"),
                    findProperty("employeeId").getMapping(idEmployeeField));
            keys.add(employeeKey);
            props.add(new ImportProperty(idEmployeeField, findProperty("idEmployee").getMapping(employeeKey)));
            fields.add(idEmployeeField);
            for (int i = 0; i < employeesList.size(); i++)
                data.get(i).add(employeesList.get(i).idEmployee);

            ImportField firstNameEmployeeField = new ImportField(findProperty("firstNameContact"));
            props.add(new ImportProperty(firstNameEmployeeField, findProperty("firstNameContact").getMapping(employeeKey)));
            fields.add(firstNameEmployeeField);
            for (int i = 0; i < employeesList.size(); i++)
                data.get(i).add(employeesList.get(i).firstNameEmployee);

            ImportField lastNameEmployeeField = new ImportField(findProperty("lastNameContact"));
            props.add(new ImportProperty(lastNameEmployeeField, findProperty("lastNameContact").getMapping(employeeKey)));
            fields.add(lastNameEmployeeField);
            for (int i = 0; i < employeesList.size(); i++)
                data.get(i).add(employeesList.get(i).lastNameEmployee);

            ImportField idPositionField = new ImportField(findProperty("idPosition"));
            ImportKey<?> positionKey = new ImportKey((ConcreteCustomClass) findClass("Position"),
                    findProperty("positionId").getMapping(idPositionField));
            keys.add(positionKey);
            props.add(new ImportProperty(idPositionField, findProperty("idPosition").getMapping(positionKey)));
            props.add(new ImportProperty(idPositionField, findProperty("positionEmployee").getMapping(employeeKey),
                    LM.object(findClass("Position")).getMapping(positionKey)));
            fields.add(idPositionField);
            for (int i = 0; i < employeesList.size(); i++)
                data.get(i).add(employeesList.get(i).idPosition);

            ImportField namePositionField = new ImportField(findProperty("namePosition"));
            props.add(new ImportProperty(namePositionField, findProperty("namePosition").getMapping(positionKey)));
            fields.add(namePositionField);
            for (int i = 0; i < employeesList.size(); i++)
                data.get(i).add(employeesList.get(i).idPosition);

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.createSession();
            session.pushVolatileStats("IA_EE");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            session.popVolatileStats();
            session.close();
        }
    }

    private void importWarehouseGroups(List<WarehouseGroup> warehouseGroupsList) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (notNullNorEmpty(warehouseGroupsList)) {

            ServerLoggers.systemLogger.info("importWarehouseGroups");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(warehouseGroupsList.size());

            ImportField idWarehouseGroupField = new ImportField(findProperty("idWarehouseGroup"));
            ImportKey<?> warehouseGroupKey = new ImportKey((ConcreteCustomClass) findClass("WarehouseGroup"),
                    findProperty("warehouseGroupId").getMapping(idWarehouseGroupField));
            keys.add(warehouseGroupKey);
            props.add(new ImportProperty(idWarehouseGroupField, findProperty("idWarehouseGroup").getMapping(warehouseGroupKey)));
            fields.add(idWarehouseGroupField);
            for (int i = 0; i < warehouseGroupsList.size(); i++)
                data.get(i).add(warehouseGroupsList.get(i).idWarehouseGroup);

            ImportField nameWarehouseGroupField = new ImportField(findProperty("nameWarehouseGroup"));
            props.add(new ImportProperty(nameWarehouseGroupField, findProperty("nameWarehouseGroup").getMapping(warehouseGroupKey)));
            fields.add(nameWarehouseGroupField);
            for (int i = 0; i < warehouseGroupsList.size(); i++)
                data.get(i).add(warehouseGroupsList.get(i).nameWarehouseGroup);

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.createSession();
            session.pushVolatileStats("IA_WG");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            session.popVolatileStats();
            session.close();
        }
    }

    private void importWarehouses(List<Warehouse> warehousesList) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (notNullNorEmpty(warehousesList)) {

            ServerLoggers.systemLogger.info("importWarehouses");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(warehousesList.size());

            ImportField idWarehouseField = new ImportField(findProperty("idWarehouse"));
            ImportKey<?> warehouseKey = new ImportKey((ConcreteCustomClass) findClass("Warehouse"),
                    findProperty("warehouseId").getMapping(idWarehouseField));
            keys.add(warehouseKey);
            props.add(new ImportProperty(idWarehouseField, findProperty("idWarehouse").getMapping(warehouseKey)));
            fields.add(idWarehouseField);
            for (int i = 0; i < warehousesList.size(); i++)
                data.get(i).add(warehousesList.get(i).idWarehouse);

            ImportField nameWarehouseField = new ImportField(findProperty("nameWarehouse"));
            props.add(new ImportProperty(nameWarehouseField, findProperty("nameWarehouse").getMapping(warehouseKey)));
            fields.add(nameWarehouseField);
            for (int i = 0; i < warehousesList.size(); i++)
                data.get(i).add(warehousesList.get(i).nameWarehouse);

            ImportField addressWarehouseField = new ImportField(findProperty("addressWarehouse"));
            props.add(new ImportProperty(addressWarehouseField, findProperty("addressWarehouse").getMapping(warehouseKey)));
            fields.add(addressWarehouseField);
            for (int i = 0; i < warehousesList.size(); i++)
                data.get(i).add(warehousesList.get(i).addressWarehouse);

            ImportField idLegalEntityField = new ImportField(findProperty("idLegalEntity"));
            ImportKey<?> legalEntityKey = new ImportKey((ConcreteCustomClass) findClass("LegalEntity"),
                    findProperty("legalEntityId").getMapping(idLegalEntityField));
            keys.add(legalEntityKey);
            props.add(new ImportProperty(idLegalEntityField, findProperty("idLegalEntity").getMapping(legalEntityKey)));
            props.add(new ImportProperty(idLegalEntityField, findProperty("legalEntityWarehouse").getMapping(warehouseKey),
                    LM.object(findClass("LegalEntity")).getMapping(legalEntityKey)));
            fields.add(idLegalEntityField);
            for (int i = 0; i < warehousesList.size(); i++)
                data.get(i).add(warehousesList.get(i).idLegalEntity);

            ImportField idWarehouseGroupField = new ImportField(findProperty("idWarehouseGroup"));
            ImportKey<?> warehouseGroupKey = new ImportKey((ConcreteCustomClass) findClass("WarehouseGroup"),
                    findProperty("warehouseGroupId").getMapping(idWarehouseGroupField));
            keys.add(warehouseGroupKey);
            props.add(new ImportProperty(idWarehouseGroupField, findProperty("warehouseGroupWarehouse").getMapping(warehouseKey),
                    LM.object(findClass("WarehouseGroup")).getMapping(warehouseGroupKey)));
            fields.add(idWarehouseGroupField);
            for (int i = 0; i < warehousesList.size(); i++)
                data.get(i).add(warehousesList.get(i).idWarehouseGroup);

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.createSession();
            session.pushVolatileStats("IA_WE");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            session.popVolatileStats();
            session.close();
        }
    }

    private void importStores(List<LegalEntity> storesList, boolean skipKeys) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (storeLM != null && notNullNorEmpty(storesList)) {

            ServerLoggers.systemLogger.info("importStores");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(storesList.size());

            ImportField idStoreField = new ImportField(storeLM.findProperty("idStore"));
            ImportKey<?> storeKey = new ImportKey((ConcreteCustomClass) storeLM.findClass("Store"),
                    storeLM.findProperty("storeId").getMapping(idStoreField));
            keys.add(storeKey);
            props.add(new ImportProperty(idStoreField, storeLM.findProperty("idStore").getMapping(storeKey)));
            fields.add(idStoreField);
            for (int i = 0; i < storesList.size(); i++)
                data.get(i).add(((Store) storesList.get(i)).idStore);

            ImportField nameStoreField = new ImportField(storeLM.findProperty("nameStore"));
            props.add(new ImportProperty(nameStoreField, storeLM.findProperty("nameStore").getMapping(storeKey)));
            fields.add(nameStoreField);
            for (int i = 0; i < storesList.size(); i++)
                data.get(i).add(storesList.get(i).nameLegalEntity);

            ImportField addressStoreField = new ImportField(storeLM.findProperty("addressStore"));
            props.add(new ImportProperty(addressStoreField, storeLM.findProperty("addressStore").getMapping(storeKey)));
            fields.add(addressStoreField);
            for (int i = 0; i < storesList.size(); i++)
                data.get(i).add(storesList.get(i).addressLegalEntity);

            ImportField idLegalEntityField = new ImportField(findProperty("idLegalEntity"));
            ImportKey<?> legalEntityKey = new ImportKey((ConcreteCustomClass) findClass("LegalEntity"),
                    findProperty("legalEntityId").getMapping(idLegalEntityField));
            legalEntityKey.skipKey = skipKeys;
            keys.add(legalEntityKey);
            props.add(new ImportProperty(idLegalEntityField, storeLM.findProperty("legalEntityStore").getMapping(storeKey),
                    LM.object(findClass("LegalEntity")).getMapping(legalEntityKey)));
            fields.add(idLegalEntityField);
            for (int i = 0; i < storesList.size(); i++)
                data.get(i).add(storesList.get(i).idLegalEntity);

            ImportField idChainStoresField = new ImportField(storeLM.findProperty("idChainStores"));
            ImportKey<?> chainStoresKey = new ImportKey((ConcreteCustomClass) storeLM.findClass("ChainStores"),
                    storeLM.findProperty("chainStoresId").getMapping(idChainStoresField));
            keys.add(chainStoresKey);
            fields.add(idChainStoresField);
            for (int i = 0; i < storesList.size(); i++)
                data.get(i).add(storesList.get(i).idChainStores);

            ImportField storeTypeField = new ImportField(storeLM.findProperty("nameStoreType"));
            ImportKey<?> storeTypeKey = new ImportKey((ConcreteCustomClass) storeLM.findClass("StoreType"),
                    storeLM.findProperty("storeTypeNameChainStores").getMapping(storeTypeField, idChainStoresField));
            keys.add(storeTypeKey);
            props.add(new ImportProperty(idChainStoresField, storeLM.findProperty("chainStoresStoreType").getMapping(storeTypeKey),
                    storeLM.object(storeLM.findClass("ChainStores")).getMapping(chainStoresKey)));
            props.add(new ImportProperty(storeTypeField, storeLM.findProperty("nameStoreType").getMapping(storeTypeKey)));
            props.add(new ImportProperty(storeTypeField, storeLM.findProperty("storeTypeStore").getMapping(storeKey),
                    storeLM.object(storeLM.findClass("StoreType")).getMapping(storeTypeKey)));
            fields.add(storeTypeField);
            for (int i = 0; i < storesList.size(); i++)
                data.get(i).add(((Store) storesList.get(i)).storeType);

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.createSession();
            session.pushVolatileStats("IA_SE");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            session.popVolatileStats();
            session.close();
        }
    }

    private void importDepartmentStores(List<DepartmentStore> departmentStoresList) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (storeLM != null && notNullNorEmpty(departmentStoresList)) {

            ServerLoggers.systemLogger.info("importDepartmentStores");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(departmentStoresList.size());

            ImportField idDepartmentStoreField = new ImportField(storeLM.findProperty("idDepartmentStore"));
            ImportKey<?> departmentStoreKey = new ImportKey((ConcreteCustomClass) storeLM.findClass("DepartmentStore"),
                    storeLM.findProperty("departmentStoreId").getMapping(idDepartmentStoreField));
            keys.add(departmentStoreKey);
            props.add(new ImportProperty(idDepartmentStoreField, storeLM.findProperty("idDepartmentStore").getMapping(departmentStoreKey)));
            fields.add(idDepartmentStoreField);
            for (int i = 0; i < departmentStoresList.size(); i++)
                data.get(i).add((departmentStoresList.get(i)).idDepartmentStore);

            ImportField nameDepartmentStoreField = new ImportField(storeLM.findProperty("nameDepartmentStore"));
            props.add(new ImportProperty(nameDepartmentStoreField, storeLM.findProperty("nameDepartmentStore").getMapping(departmentStoreKey)));
            fields.add(nameDepartmentStoreField);
            for (int i = 0; i < departmentStoresList.size(); i++)
                data.get(i).add((departmentStoresList.get(i)).nameDepartmentStore);

            ImportField idStoreField = new ImportField(storeLM.findProperty("idStore"));
            ImportKey<?> storeKey = new ImportKey((ConcreteCustomClass) storeLM.findClass("Store"),
                    storeLM.findProperty("storeId").getMapping(idStoreField));
            keys.add(storeKey);
            props.add(new ImportProperty(idStoreField, storeLM.findProperty("storeDepartmentStore").getMapping(departmentStoreKey),
                    storeLM.object(storeLM.findClass("Store")).getMapping(storeKey)));
            fields.add(idStoreField);
            for (int i = 0; i < departmentStoresList.size(); i++)
                data.get(i).add((departmentStoresList.get(i)).idStore);

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.createSession();
            session.pushVolatileStats("IA_DS");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            session.popVolatileStats();
            session.close();
        }
    }

    private void importBanks(List<Bank> banksList) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (notNullNorEmpty(banksList)) {

            ServerLoggers.systemLogger.info("importBanks");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(banksList.size());

            ImportField idBankField = new ImportField(findProperty("idBank"));
            ImportKey<?> bankKey = new ImportKey((ConcreteCustomClass) findClass("Bank"),
                    findProperty("bankId").getMapping(idBankField));
            keys.add(bankKey);
            props.add(new ImportProperty(idBankField, findProperty("idBank").getMapping(bankKey)));
            fields.add(idBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).idBank);

            ImportField nameBankField = new ImportField(findProperty("nameBank"));
            props.add(new ImportProperty(nameBankField, findProperty("nameBank").getMapping(bankKey)));
            fields.add(nameBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).nameBank);

            ImportField addressBankField = new ImportField(findProperty("dataAddressBankDate"));
            props.add(new ImportProperty(addressBankField, findProperty("dataAddressBankDate").getMapping(bankKey, defaultDate)));
            fields.add(addressBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).addressBank);

            ImportField departmentBankField = new ImportField(findProperty("departmentBank"));
            props.add(new ImportProperty(departmentBankField, findProperty("departmentBank").getMapping(bankKey)));
            fields.add(departmentBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).departmentBank);

            ImportField mfoBankField = new ImportField(findProperty("MFOBank"));
            props.add(new ImportProperty(mfoBankField, findProperty("MFOBank").getMapping(bankKey)));
            fields.add(mfoBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).mfoBank);

            ImportField cbuBankField = new ImportField(findProperty("CBUBank"));
            props.add(new ImportProperty(cbuBankField, findProperty("CBUBank").getMapping(bankKey)));
            fields.add(cbuBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).cbuBank);

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.createSession();
            session.pushVolatileStats("IA_BK");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            session.popVolatileStats();
            session.close();
        }
    }

    private void importRateWastes(List<RateWaste> rateWastesList) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (writeOffItemLM != null && notNullNorEmpty(rateWastesList)) {

            ServerLoggers.systemLogger.info("importRateWastes");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(rateWastesList.size());

            ImportField idWriteOffRateField = new ImportField(writeOffItemLM.findProperty("idWriteOffRate"));
            ImportKey<?> writeOffRateKey = new ImportKey((ConcreteCustomClass) writeOffItemLM.findClass("WriteOffRate"),
                    writeOffItemLM.findProperty("writeOffRateId").getMapping(idWriteOffRateField));
            keys.add(writeOffRateKey);
            props.add(new ImportProperty(idWriteOffRateField, writeOffItemLM.findProperty("idWriteOffRate").getMapping(writeOffRateKey)));
            fields.add(idWriteOffRateField);
            for (int i = 0; i < rateWastesList.size(); i++)
                data.get(i).add(rateWastesList.get(i).idRateWaste);

            ImportField nameWriteOffRateField = new ImportField(writeOffItemLM.findProperty("nameWriteOffRate"));
            props.add(new ImportProperty(nameWriteOffRateField, writeOffItemLM.findProperty("nameWriteOffRate").getMapping(writeOffRateKey)));
            fields.add(nameWriteOffRateField);
            for (int i = 0; i < rateWastesList.size(); i++)
                data.get(i).add(rateWastesList.get(i).nameRateWaste);

            ImportField percentWriteOffRateField = new ImportField(writeOffItemLM.findProperty("percentWriteOffRate"));
            props.add(new ImportProperty(percentWriteOffRateField, writeOffItemLM.findProperty("percentWriteOffRate").getMapping(writeOffRateKey)));
            fields.add(percentWriteOffRateField);
            for (int i = 0; i < rateWastesList.size(); i++)
                data.get(i).add(rateWastesList.get(i).percentWriteOffRate);

            ImportField nameCountryField = new ImportField(findProperty("nameCountry"));
            ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) findClass("Country"),
                    findProperty("countryName").getMapping(nameCountryField));
            keys.add(countryKey);
            props.add(new ImportProperty(nameCountryField, writeOffItemLM.findProperty("countryWriteOffRate").getMapping(writeOffRateKey),
                    LM.object(findClass("Country")).getMapping(countryKey)));
            fields.add(nameCountryField);
            for (int i = 0; i < rateWastesList.size(); i++)
                data.get(i).add(rateWastesList.get(i).nameCountry);

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.createSession();
            session.pushVolatileStats("IA_RW");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            session.popVolatileStats();
            session.close();
        }
    }

    private void importContracts(List<Contract> contractsList, boolean skipKeys) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (notNullNorEmpty(contractsList)) {

            ServerLoggers.systemLogger.info("importContacts");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(contractsList.size());

            ImportField idUserContractSkuField = new ImportField(findProperty("idUserContractSku"));
            ImportKey<?> userContractSkuKey = new ImportKey((ConcreteCustomClass) findClass("UserContractSku"),
                    findProperty("userContractSkuId").getMapping(idUserContractSkuField));
            keys.add(userContractSkuKey);
            props.add(new ImportProperty(idUserContractSkuField, findProperty("idUserContractSku").getMapping(userContractSkuKey)));
            fields.add(idUserContractSkuField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).idUserContractSku);

            ImportField numberContractField = new ImportField(findProperty("numberContract"));
            props.add(new ImportProperty(numberContractField, findProperty("numberContract").getMapping(userContractSkuKey)));
            fields.add(numberContractField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).numberContract);

            ImportField dateFromContractField = new ImportField(findProperty("dateFromContract"));
            props.add(new ImportProperty(dateFromContractField, findProperty("dateFromContract").getMapping(userContractSkuKey)));
            fields.add(dateFromContractField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).dateFromContract);

            ImportField dateToContractField = new ImportField(findProperty("dateToContract"));
            props.add(new ImportProperty(dateToContractField, findProperty("dateToContract").getMapping(userContractSkuKey)));
            fields.add(dateToContractField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).dateToContract);

            ImportField idSupplierField = new ImportField(findProperty("idLegalEntity"));
            ImportKey<?> supplierKey = new ImportKey((ConcreteCustomClass) findClass("LegalEntity"),
                    findProperty("legalEntityId").getMapping(idSupplierField));
            supplierKey.skipKey = skipKeys;
            keys.add(supplierKey);
            props.add(new ImportProperty(idSupplierField, findProperty("supplierContractSku").getMapping(userContractSkuKey),
                    LM.object(findClass("LegalEntity")).getMapping(supplierKey)));
            fields.add(idSupplierField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).idSupplier);

            ImportField idCustomerField = new ImportField(findProperty("idLegalEntity"));
            ImportKey<?> customerKey = new ImportKey((ConcreteCustomClass) findClass("LegalEntity"),
                    findProperty("legalEntityId").getMapping(idCustomerField));
            customerKey.skipKey = skipKeys;
            keys.add(customerKey);
            props.add(new ImportProperty(idCustomerField, findProperty("customerContractSku").getMapping(userContractSkuKey),
                    LM.object(findClass("LegalEntity")).getMapping(customerKey)));
            fields.add(idCustomerField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).idCustomer);

            ImportField shortNameCurrencyField = new ImportField(findProperty("shortNameCurrency"));
            ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) findClass("Currency"),
                    findProperty("currencyShortName").getMapping(shortNameCurrencyField));
            keys.add(currencyKey);
            props.add(new ImportProperty(shortNameCurrencyField, findProperty("currencyContract").getMapping(userContractSkuKey),
                    LM.object(findClass("Currency")).getMapping(currencyKey)));
            fields.add(shortNameCurrencyField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).shortNameCurrency);

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.createSession();
            session.pushVolatileStats("IA_CT");
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            session.popVolatileStats();
            session.close();
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

            for (UserInvoiceDetail aData : data) {
                if (field.get(aData) != null)
                    return true;
            }
        } catch (NoSuchFieldException e) {
            return true;
        } catch (IllegalAccessException e) {
            return true;
        }
        return false;
    }


    private Boolean showItemField(List<Item> data, String fieldName) {
        try {
            Field field = Item.class.getField(fieldName);

            for (Item aData : data) {
                if (field.get(aData) != null)
                    return true;
            }
        } catch (NoSuchFieldException e) {
            return true;
        } catch (IllegalAccessException e) {
            return true;
        }
        return false;
    }
}