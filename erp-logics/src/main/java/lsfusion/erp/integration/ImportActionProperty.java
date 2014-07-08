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
import lsfusion.server.logics.scripted.ScriptingActionProperty;
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

public class ImportActionProperty extends ScriptingActionProperty {
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
    
    public boolean skipExtraInvoiceParams;
    public boolean skipCertificateInvoiceParams;

    @Override
    protected void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
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

            this.context = context;

            Object countryBelarus = getLCP("countrySID").read(context.getSession(), new DataObject("112", StringClass.get(3)));
            getLCP("defaultCountry").change(countryBelarus, context.getSession());
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
        if (parentGroupsList != null) {

            ServerLoggers.systemLogger.info("importParentGroups");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(parentGroupsList.size());

            ImportField idItemGroupField = new ImportField(getLCP("idItemGroup"));
            ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) getClass("ItemGroup"),
                    getLCP("itemGroupId").getMapping(idItemGroupField));
            keys.add(itemGroupKey);
            fields.add(idItemGroupField);
            for (int i = 0; i < parentGroupsList.size(); i++)
                data.get(i).add(parentGroupsList.get(i).sid);

            ImportField idParentGroupField = new ImportField(getLCP("idItemGroup"));
            ImportKey<?> parentGroupKey = new ImportKey((ConcreteCustomClass) getClass("ItemGroup"),
                    getLCP("itemGroupId").getMapping(idParentGroupField));
            keys.add(parentGroupKey);
            props.add(new ImportProperty(idParentGroupField, getLCP("parentItemGroup").getMapping(itemGroupKey),
                    LM.object(getClass("ItemGroup")).getMapping(parentGroupKey)));
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

        if (itemGroupsList != null) {

            ServerLoggers.systemLogger.info("importItemGroups");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(itemGroupsList.size());

            ImportField idItemGroupField = new ImportField(getLCP("idItemGroup"));
            ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) getClass("ItemGroup"),
                    getLCP("itemGroupId").getMapping(idItemGroupField));
            keys.add(itemGroupKey);
            props.add(new ImportProperty(idItemGroupField, getLCP("idItemGroup").getMapping(itemGroupKey)));
            fields.add(idItemGroupField);
            for (int i = 0; i < itemGroupsList.size(); i++)
                data.get(i).add(itemGroupsList.get(i).sid);

            ImportField itemGroupNameField = new ImportField(getLCP("nameItemGroup"));
            props.add(new ImportProperty(itemGroupNameField, getLCP("nameItemGroup").getMapping(itemGroupKey)));
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

        if (warePurchaseInvoiceLM != null && waresList != null) {

            ServerLoggers.systemLogger.info("importWares");

            DataObject defaultDate = new DataObject(new java.sql.Date(2001 - 1900, 0, 01), DateClass.instance);

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(waresList.size());

            ImportField idWareField = new ImportField(warePurchaseInvoiceLM.findLCPByCompoundOldName("idWare"));
            ImportKey<?> wareKey = new ImportKey((ConcreteCustomClass) warePurchaseInvoiceLM.findClassByCompoundName("Ware"),
                    warePurchaseInvoiceLM.findLCPByCompoundOldName("wareId").getMapping(idWareField));
            keys.add(wareKey);
            props.add(new ImportProperty(idWareField, warePurchaseInvoiceLM.findLCPByCompoundOldName("idWare").getMapping(wareKey)));
            fields.add(idWareField);
            for (int i = 0; i < waresList.size(); i++)
                data.get(i).add(waresList.get(i).idWare);

            ImportField nameWareField = new ImportField(warePurchaseInvoiceLM.findLCPByCompoundOldName("nameWare"));
            props.add(new ImportProperty(nameWareField, warePurchaseInvoiceLM.findLCPByCompoundOldName("nameWare").getMapping(wareKey)));
            fields.add(nameWareField);
            for (int i = 0; i < waresList.size(); i++)
                data.get(i).add(waresList.get(i).nameWare);

            ImportField priceWareField = new ImportField(warePurchaseInvoiceLM.findLCPByCompoundOldName("warePrice"));
            props.add(new ImportProperty(priceWareField, warePurchaseInvoiceLM.findLCPByCompoundOldName("dataWarePriceDate").getMapping(wareKey, defaultDate)));
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

        ImportField idUOMField = new ImportField(getLCP("idUOM"));
        ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) getClass("UOM"),
                getLCP("UOMId").getMapping(idUOMField));
        keys.add(UOMKey);
        props.add(new ImportProperty(idUOMField, getLCP("idUOM").getMapping(UOMKey)));
        fields.add(idUOMField);
        for (int i = 0; i < uomsList.size(); i++)
            data.get(i).add(uomsList.get(i).idUOM);

        ImportField nameUOMField = new ImportField(getLCP("nameUOM"));
        props.add(new ImportProperty(nameUOMField, getLCP("nameUOM").getMapping(UOMKey)));
        fields.add(nameUOMField);
        for (int i = 0; i < uomsList.size(); i++)
            data.get(i).add(uomsList.get(i).nameUOM);

        ImportField shortNameUOMField = new ImportField(getLCP("shortNameUOM"));
        props.add(new ImportProperty(shortNameUOMField, getLCP("shortNameUOM").getMapping(UOMKey)));
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


    private void importPackOfItems(List<Item> itemsList, 
                                   boolean skipKeys)
            throws SQLException, IOException, xBaseJException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        if (itemsList.size() == 0) return;

        ServerLoggers.systemLogger.info("importItems " + itemsList.size());

        List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
        List<ImportField> fields = new ArrayList<ImportField>();
        List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

        List<List<Object>> data = initData(itemsList.size());

        ImportField idItemField = new ImportField(getLCP("idItem"));
        ImportKey<?> itemKey = new ImportKey((ConcreteCustomClass) getClass("Item"),
                getLCP("itemId").getMapping(idItemField));
        keys.add(itemKey);
        props.add(new ImportProperty(idItemField, getLCP("idItem").getMapping(itemKey)));
        fields.add(idItemField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).idItem);

        if (showItemField(itemsList, "captionItem")) {
            ImportField captionItemField = new ImportField(getLCP("captionItem"));
            props.add(new ImportProperty(captionItemField, getLCP("captionItem").getMapping(itemKey)));
            fields.add(captionItemField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).captionItem);
        }

        if (showItemField(itemsList, "idItemGroup")) {
            ImportField idItemGroupField = new ImportField(getLCP("idItemGroup"));
            ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) getClass("ItemGroup"),
                    getLCP("itemGroupId").getMapping(idItemGroupField));
            keys.add(itemGroupKey);
            props.add(new ImportProperty(idItemGroupField, getLCP("itemGroupItem").getMapping(itemKey),
                    LM.object(getClass("ItemGroup")).getMapping(itemGroupKey)));
            fields.add(idItemGroupField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).idItemGroup);
        }

        if (showItemField(itemsList, "idBrand")) {
            ImportField idBrandField = new ImportField(getLCP("idBrand"));
            ImportKey<?> brandKey = new ImportKey((ConcreteCustomClass) getClass("Brand"),
                    getLCP("brandId").getMapping(idBrandField));
            keys.add(brandKey);
            props.add(new ImportProperty(idBrandField, getLCP("idBrand").getMapping(brandKey)));
            props.add(new ImportProperty(idBrandField, getLCP("brandItem").getMapping(itemKey),
                    LM.object(getClass("Brand")).getMapping(brandKey)));
            fields.add(idBrandField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).idBrand);

            ImportField nameBrandField = new ImportField(getLCP("nameBrand"));
            props.add(new ImportProperty(nameBrandField, getLCP("nameBrand").getMapping(brandKey)));
            fields.add(nameBrandField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).nameBrand);
        }

        ImportField nameCountryField = new ImportField(getLCP("nameCountry"));
        ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) getClass("Country"),
                getLCP("countryName").getMapping(nameCountryField));
        keys.add(countryKey);
        props.add(new ImportProperty(nameCountryField, getLCP("nameCountry").getMapping(countryKey)));
        props.add(new ImportProperty(nameCountryField, getLCP("countryItem").getMapping(itemKey),
                LM.object(getClass("Country")).getMapping(countryKey)));
        fields.add(nameCountryField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).nameCountry);

        ImportField extIdBarcodeField = new ImportField(getLCP("extIdBarcode"));
        ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) getClass("Barcode"),
                getLCP(/*"barcodeIdDate"*/"extBarcodeId").getMapping(extIdBarcodeField));
        keys.add(barcodeKey);
        props.add(new ImportProperty(idItemField, getLCP("skuBarcode").getMapping(barcodeKey),
                LM.object(getClass("Item")).getMapping(itemKey)));
        props.add(new ImportProperty(extIdBarcodeField, getLCP("extIdBarcode").getMapping(barcodeKey)));
        fields.add(extIdBarcodeField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).extIdBarcode);

        ImportField idBarcodeField = new ImportField(getLCP("idBarcode"));
        props.add(new ImportProperty(idBarcodeField, getLCP("idBarcode").getMapping(barcodeKey)));
        fields.add(idBarcodeField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).idBarcode);

        ImportField idUOMField = new ImportField(getLCP("idUOM"));
        ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) getClass("UOM"),
                getLCP("UOMId").getMapping(idUOMField));
        UOMKey.skipKey = true;
        keys.add(UOMKey);
        props.add(new ImportProperty(idUOMField, getLCP("UOMItem").getMapping(itemKey),
                LM.object(getClass("UOM")).getMapping(UOMKey)));
        props.add(new ImportProperty(idUOMField, getLCP("UOMBarcode").getMapping(barcodeKey),
                LM.object(getClass("UOM")).getMapping(UOMKey)));
        fields.add(idUOMField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).idUOM);

        if (showItemField(itemsList, "isWeightItem")) {
            ImportField isWeightItemField = new ImportField(getLCP("isWeightItem"));
            props.add(new ImportProperty(isWeightItemField, getLCP("isWeightItem").getMapping(itemKey), true));
            fields.add(isWeightItemField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).isWeightItem);
        }

        if (showItemField(itemsList, "netWeightItem")) {
            ImportField netWeightItemField = new ImportField(getLCP("netWeightItem"));
            props.add(new ImportProperty(netWeightItemField, getLCP("netWeightItem").getMapping(itemKey)));
            fields.add(netWeightItemField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).netWeightItem);
        }

        if (showItemField(itemsList, "grossWeightItem")) {
            ImportField grossWeightItemField = new ImportField(getLCP("grossWeightItem"));
            props.add(new ImportProperty(grossWeightItemField, getLCP("grossWeightItem").getMapping(itemKey)));
            fields.add(grossWeightItemField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).grossWeightItem);
        }

        if (showItemField(itemsList, "compositionItem")) {
            ImportField compositionItemField = new ImportField(getLCP("compositionItem"));
            props.add(new ImportProperty(compositionItemField, getLCP("compositionItem").getMapping(itemKey)));
            fields.add(compositionItemField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).compositionItem);
        }

        ImportField dateField = new ImportField(DateClass.instance);
        props.add(new ImportProperty(dateField, getLCP("dataDateBarcode").getMapping(barcodeKey)));
        fields.add(dateField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).date);

        DataObject defaultCountryObject = (DataObject) getLCP("defaultCountry").readClasses(context.getSession());
        ImportField valueVATItemCountryDateField = new ImportField(getLCP("valueVATItemCountryDate"));
        ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) getClass("Range"),
                getLCP("valueCurrentVATDefaultValue").getMapping(valueVATItemCountryDateField));
        VATKey.skipKey = skipKeys;
        keys.add(VATKey);
        props.add(new ImportProperty(valueVATItemCountryDateField, getLCP("VATItemCountry").getMapping(itemKey, defaultCountryObject),
                LM.object(getClass("Range")).getMapping(VATKey)));
        fields.add(valueVATItemCountryDateField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).retailVAT);

        if (warePurchaseInvoiceLM != null && showItemField(itemsList, "idWare")) {

            ImportField idWareField = new ImportField(warePurchaseInvoiceLM.findLCPByCompoundOldName("idWare"));
            ImportKey<?> wareKey = new ImportKey((ConcreteCustomClass) warePurchaseInvoiceLM.findClassByCompoundName("Ware"),
                    warePurchaseInvoiceLM.findLCPByCompoundOldName("wareId").getMapping(idWareField));
            keys.add(wareKey);
            props.add(new ImportProperty(idWareField, warePurchaseInvoiceLM.findLCPByCompoundOldName("idWare").getMapping(wareKey)));
            props.add(new ImportProperty(idWareField, warePurchaseInvoiceLM.findLCPByCompoundOldName("wareItem").getMapping(itemKey),
                    warePurchaseInvoiceLM.object(warePurchaseInvoiceLM.findClassByCompoundName("Ware")).getMapping(wareKey)));
            fields.add(idWareField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).idWare);

            ImportField priceWareField = new ImportField(warePurchaseInvoiceLM.findLCPByCompoundOldName("dataWarePriceDate"));
            props.add(new ImportProperty(priceWareField, warePurchaseInvoiceLM.findLCPByCompoundOldName("dataWarePriceDate").getMapping(wareKey, dateField)));
            fields.add(priceWareField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).priceWare);

            ImportField vatWareField = new ImportField(warePurchaseInvoiceLM.findLCPByCompoundOldName("valueRate"));
            ImportKey<?> rangeKey = new ImportKey((ConcreteCustomClass) warePurchaseInvoiceLM.findClassByCompoundName("Range"),
                    warePurchaseInvoiceLM.findLCPByCompoundOldName("valueCurrentVATDefaultValue").getMapping(vatWareField));
            keys.add(rangeKey);
            props.add(new ImportProperty(vatWareField, warePurchaseInvoiceLM.findLCPByCompoundOldName("dataRangeWareDate").getMapping(wareKey, dateField, rangeKey),
                    warePurchaseInvoiceLM.object(warePurchaseInvoiceLM.findClassByCompoundName("Range")).getMapping(rangeKey)));
            fields.add(vatWareField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).vatWare);

        }

        if (writeOffItemLM != null && showItemField(itemsList, "idWriteOffRate")) {

            ImportField idWriteOffRateField = new ImportField(writeOffItemLM.findLCPByCompoundOldName("idWriteOffRate"));
            ImportKey<?> writeOffRateKey = new ImportKey((ConcreteCustomClass) writeOffItemLM.findClassByCompoundName("WriteOffRate"),
                    writeOffItemLM.findLCPByCompoundOldName("writeOffRateId").getMapping(idWriteOffRateField));
            keys.add(writeOffRateKey);
            props.add(new ImportProperty(idWriteOffRateField, writeOffItemLM.findLCPByCompoundOldName("writeOffRateCountryItem").getMapping(defaultCountryObject, itemKey),
                    LM.object(writeOffItemLM.findClassByCompoundName("WriteOffRate")).getMapping(writeOffRateKey)));
            fields.add(idWriteOffRateField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).idWriteOffRate);

        }

        if (showItemField(itemsList, "retailMarkup")) {

            ImportField idRetailCalcPriceListTypeField = new ImportField(getLCP("idCalcPriceListType"));
            ImportKey<?> retailCalcPriceListTypeKey = new ImportKey((ConcreteCustomClass) getClass("CalcPriceListType"),
                    getLCP("calcPriceListTypeId").getMapping(idRetailCalcPriceListTypeField));
            keys.add(retailCalcPriceListTypeKey);
            props.add(new ImportProperty(idRetailCalcPriceListTypeField, getLCP("idCalcPriceListType").getMapping(retailCalcPriceListTypeKey)));
            fields.add(idRetailCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add("retail");

            ImportField nameRetailCalcPriceListTypeField = new ImportField(getLCP("namePriceListType"));
            props.add(new ImportProperty(nameRetailCalcPriceListTypeField, getLCP("namePriceListType").getMapping(retailCalcPriceListTypeKey)));
            fields.add(nameRetailCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add("Розничная надбавка");

            ImportField retailMarkupCalcPriceListTypeField = new ImportField(getLCP("dataMarkupCalcPriceListTypeSku"));
            props.add(new ImportProperty(retailMarkupCalcPriceListTypeField, getLCP("dataMarkupCalcPriceListTypeSku").getMapping(retailCalcPriceListTypeKey, itemKey)));
            fields.add(retailMarkupCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).retailMarkup);

        }

        if (showItemField(itemsList, "baseMarkup")) {

            ImportField idBaseCalcPriceListTypeField = new ImportField(getLCP("idCalcPriceListType"));
            ImportKey<?> baseCalcPriceListTypeKey = new ImportKey((ConcreteCustomClass) getClass("CalcPriceListType"),
                    getLCP("calcPriceListTypeId").getMapping(idBaseCalcPriceListTypeField));
            keys.add(baseCalcPriceListTypeKey);
            props.add(new ImportProperty(idBaseCalcPriceListTypeField, getLCP("idCalcPriceListType").getMapping(baseCalcPriceListTypeKey)));
            fields.add(idBaseCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add("wholesale");

            ImportField nameBaseCalcPriceListTypeField = new ImportField(getLCP("namePriceListType"));
            props.add(new ImportProperty(nameBaseCalcPriceListTypeField, getLCP("namePriceListType").getMapping(baseCalcPriceListTypeKey)));
            fields.add(nameBaseCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add("Оптовая надбавка");

            ImportField baseMarkupCalcPriceListTypeField = new ImportField(getLCP("dataMarkupCalcPriceListTypeSku"));
            props.add(new ImportProperty(baseMarkupCalcPriceListTypeField, getLCP("dataMarkupCalcPriceListTypeSku").getMapping(baseCalcPriceListTypeKey, itemKey)));
            fields.add(baseMarkupCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).baseMarkup);
        }

        ImportField extIdBarcodePackField = new ImportField(getLCP("extIdBarcode"));
        ImportKey<?> barcodePackKey = new ImportKey((ConcreteCustomClass) getClass("Barcode"),
                getLCP(/*"barcodeIdDate"*/"extBarcodeId").getMapping(extIdBarcodePackField));
        keys.add(barcodePackKey);
        props.add(new ImportProperty(dateField, getLCP("dataDateBarcode").getMapping(barcodePackKey)));
        props.add(new ImportProperty(idItemField, getLCP("skuBarcode").getMapping(barcodePackKey),
                LM.object(getClass("Item")).getMapping(itemKey)));
        props.add(new ImportProperty(extIdBarcodePackField, getLCP("extIdBarcode").getMapping(barcodePackKey)));
        props.add(new ImportProperty(extIdBarcodePackField, getLCP("Purchase.packBarcodeSku").getMapping(itemKey),
                LM.object(getClass("Barcode")).getMapping(barcodePackKey)));
        fields.add(extIdBarcodePackField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(valueWithPrefix(itemsList.get(i).idBarcodePack, "P", null));

        ImportField idBarcodePackField = new ImportField(getLCP("idBarcode"));
        props.add(new ImportProperty(idBarcodePackField, getLCP("idBarcode").getMapping(barcodePackKey)));
        if(salePackLM != null) {
            props.add(new ImportProperty(extIdBarcodePackField, salePackLM.findLCPByCompoundOldName("Sale.packBarcodeSku").getMapping(itemKey),
                    salePackLM.object(salePackLM.findClassByCompoundName("Barcode")).getMapping(barcodePackKey)));
        }
        fields.add(idBarcodePackField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(null);

        if (showItemField(itemsList, "amountPack")) {
            ImportField amountBarcodePackField = new ImportField(getLCP("amountBarcode"));
            props.add(new ImportProperty(amountBarcodePackField, getLCP("amountBarcode").getMapping(barcodePackKey)));
            fields.add(amountBarcodePackField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).amountPack);
        }

        if (showItemField(itemsList, "idUOMPack")) {
            ImportField idUOMPackField = new ImportField(getLCP("idUOM"));
            props.add(new ImportProperty(idUOMPackField, getLCP("UOMBarcode").getMapping(barcodePackKey),
                    LM.object(getClass("UOM")).getMapping(UOMKey)));
            fields.add(idUOMPackField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).idUOMPack);
        }

        if (showItemField(itemsList, "idManufacturer")) {
            ImportField idManufacturerField = new ImportField(getLCP("idManufacturer"));
            ImportKey<?> manufacturerKey = new ImportKey((ConcreteCustomClass) getClass("Manufacturer"),
                    getLCP("manufacturerId").getMapping(idManufacturerField));
            keys.add(manufacturerKey);
            props.add(new ImportProperty(idManufacturerField, getLCP("idManufacturer").getMapping(manufacturerKey)));
            props.add(new ImportProperty(idManufacturerField, getLCP("manufacturerItem").getMapping(itemKey),
                    LM.object(getClass("Manufacturer")).getMapping(manufacturerKey)));
            fields.add(idManufacturerField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).idManufacturer);

            ImportField nameManufacturerField = new ImportField(getLCP("nameManufacturer"));
            props.add(new ImportProperty(nameManufacturerField, getLCP("nameManufacturer").getMapping(manufacturerKey)));
            fields.add(nameManufacturerField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).nameManufacturer);
        }

        if (showItemField(itemsList, "codeCustomsGroup")) {
            ImportField codeCustomsGroupField = new ImportField(getLCP("codeCustomsGroup"));
            ImportKey<?> customsGroupKey = new ImportKey((CustomClass) getClass("CustomsGroup"),
                    getLCP("customsGroupCode").getMapping(codeCustomsGroupField));
            keys.add(customsGroupKey);
            props.add(new ImportProperty(codeCustomsGroupField, getLCP("codeCustomsGroup").getMapping(customsGroupKey)));
            props.add(new ImportProperty(codeCustomsGroupField, getLCP("customsGroupCountryItem").getMapping(countryKey, itemKey),
                    LM.object(getClass("CustomsGroup")).getMapping(customsGroupKey)));
            fields.add(codeCustomsGroupField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).codeCustomsGroup);

            ImportField nameCustomsZoneField = new ImportField(getLCP("nameCustomsZone"));
            ImportKey<?> customsZoneKey = new ImportKey((CustomClass) getClass("CustomsZone"),
                    getLCP("customsZoneName").getMapping(nameCustomsZoneField));
            keys.add(customsZoneKey);
            props.add(new ImportProperty(nameCustomsZoneField, getLCP("nameCustomsZone").getMapping(customsZoneKey)));
            props.add(new ImportProperty(nameCustomsZoneField, getLCP("customsZoneCustomsGroup").getMapping(customsGroupKey),
                    LM.object(getClass("CustomsZone")).getMapping(customsZoneKey)));
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

        if (userInvoiceDetailsList != null) {

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

                ImportField idUserInvoiceField = new ImportField(getLCP("Purchase.idUserInvoice"));
                ImportKey<?> userInvoiceKey = new ImportKey((ConcreteCustomClass) getClass("Purchase.UserInvoice"),
                        getLCP("Purchase.userInvoiceId").getMapping(idUserInvoiceField));
                keys.add(userInvoiceKey);
                props.add(new ImportProperty(idUserInvoiceField, getLCP("Purchase.idUserInvoice").getMapping(userInvoiceKey)));
                fields.add(idUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idUserInvoice);

                ImportField idUserInvoiceDetailField = new ImportField(getLCP("Purchase.idUserInvoiceDetail"));
                ImportKey<?> userInvoiceDetailKey = new ImportKey((ConcreteCustomClass) getClass("Purchase.UserInvoiceDetail"),
                        getLCP("Purchase.userInvoiceDetailId").getMapping(idUserInvoiceDetailField));
                keys.add(userInvoiceDetailKey);
                props.add(new ImportProperty(idUserInvoiceDetailField, getLCP("Purchase.idUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                props.add(new ImportProperty(idUserInvoiceField, getLCP("Purchase.userInvoiceUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(getClass("Purchase.UserInvoice")).getMapping(userInvoiceKey)));
                fields.add(idUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idUserInvoiceDetail);


                ImportField idCustomerStockField = new ImportField(getLCP("idStock"));
                ImportKey<?> customerStockKey = new ImportKey((CustomClass) getClass("Stock"),
                        getLCP("stockId").getMapping(idCustomerStockField));
                customerStockKey.skipKey = skipKeys;
                keys.add(customerStockKey);
                props.add(new ImportProperty(idCustomerStockField, getLCP("idStock").getMapping(customerStockKey)));
                props.add(new ImportProperty(idCustomerStockField, getLCP("Purchase.customerUserInvoice").getMapping(userInvoiceKey),
                        getLCP("legalEntityStock").getMapping(customerStockKey)));
                props.add(new ImportProperty(idCustomerStockField, getLCP("Purchase.customerStockUserInvoice").getMapping(userInvoiceKey),
                        LM.object(getClass("Stock")).getMapping(customerStockKey)));
                fields.add(idCustomerStockField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idCustomerStock);


                ImportField idSupplierField = new ImportField(getLCP("idLegalEntity"));
                ImportKey<?> supplierKey = new ImportKey((ConcreteCustomClass) getClass("LegalEntity"),
                        getLCP("legalEntityId").getMapping(idSupplierField));
                supplierKey.skipKey = skipKeys;
                keys.add(supplierKey);
                props.add(new ImportProperty(idSupplierField, getLCP("Purchase.supplierUserInvoice").getMapping(userInvoiceKey),
                        LM.object(getClass("LegalEntity")).getMapping(supplierKey)));
                fields.add(idSupplierField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idSupplier);


                ImportField idSupplierStockField = new ImportField(getLCP("idStock"));
                ImportKey<?> supplierStockKey = new ImportKey((CustomClass) getClass("Stock"),
                        getLCP("stockId").getMapping(idSupplierStockField));
                supplierStockKey.skipKey = skipKeys;
                keys.add(supplierStockKey);
                props.add(new ImportProperty(idSupplierStockField, getLCP("idStock").getMapping(supplierStockKey)));
                props.add(new ImportProperty(idSupplierStockField, getLCP("Purchase.supplierStockUserInvoice").getMapping(userInvoiceKey),
                        LM.object(getClass("Stock")).getMapping(supplierStockKey)));
                fields.add(idSupplierStockField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idSupplierStock);


                ImportField numberUserInvoiceField = new ImportField(getLCP("Purchase.numberUserInvoice"));
                props.add(new ImportProperty(numberUserInvoiceField, getLCP("Purchase.numberUserInvoice").getMapping(userInvoiceKey)));
                fields.add(numberUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).number);

                if (showField(dataUserInvoiceDetail, "series")) {
                    ImportField seriesUserInvoiceField = new ImportField(getLCP("Purchase.seriesUserInvoice"));
                    props.add(new ImportProperty(seriesUserInvoiceField, getLCP("Purchase.seriesUserInvoice").getMapping(userInvoiceKey)));
                    fields.add(seriesUserInvoiceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).series);
                }

                if (pricingPurchaseLM != null) {

                    ImportField createPricingUserInvoiceField = new ImportField(pricingPurchaseLM.findLCPByCompoundOldName("createPricingUserInvoice"));
                    props.add(new ImportProperty(createPricingUserInvoiceField, pricingPurchaseLM.findLCPByCompoundOldName("createPricingUserInvoice").getMapping(userInvoiceKey), true));
                    fields.add(createPricingUserInvoiceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).createPricing);

                    ImportField retailPriceUserInvoiceDetailField = new ImportField(pricingPurchaseLM.findLCPByCompoundOldName("retailPriceUserInvoiceDetail"));
                    props.add(new ImportProperty(retailPriceUserInvoiceDetailField, pricingPurchaseLM.findLCPByCompoundOldName("retailPriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(retailPriceUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).retailPrice);

                    ImportField retailMarkupUserInvoiceDetailField = new ImportField(pricingPurchaseLM.findLCPByCompoundOldName("retailMarkupUserInvoiceDetail"));
                    props.add(new ImportProperty(retailMarkupUserInvoiceDetailField, pricingPurchaseLM.findLCPByCompoundOldName("retailMarkupUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(retailMarkupUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).retailMarkup);

                }

                ImportField createShipmentUserInvoiceField = new ImportField(getLCP("Purchase.createShipmentUserInvoice"));
                props.add(new ImportProperty(createShipmentUserInvoiceField, getLCP("Purchase.createShipmentUserInvoice").getMapping(userInvoiceKey), true));
                fields.add(createShipmentUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).createShipment);

                if (showField(dataUserInvoiceDetail, "manufacturingPrice")) {
                    ImportField showManufacturingPriceUserInvoiceField = new ImportField(getLCP("Purchase.showManufacturingPriceUserInvoice"));
                    props.add(new ImportProperty(showManufacturingPriceUserInvoiceField, getLCP("Purchase.showManufacturingPriceUserInvoice").getMapping(userInvoiceKey)));
                    fields.add(showManufacturingPriceUserInvoiceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                }

                if (purchaseInvoiceWholesalePriceLM != null) {

                    ImportField showWholesalePriceUserInvoiceField = new ImportField(purchaseInvoiceWholesalePriceLM.findLCPByCompoundOldName("Purchase.showWholesalePriceUserInvoice"));
                    props.add(new ImportProperty(showWholesalePriceUserInvoiceField, purchaseInvoiceWholesalePriceLM.findLCPByCompoundOldName("Purchase.showWholesalePriceUserInvoice").getMapping(userInvoiceKey), true));
                    fields.add(showWholesalePriceUserInvoiceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                    
                    ImportField wholesalePriceUserInvoiceDetailField = new ImportField(purchaseInvoiceWholesalePriceLM.findLCPByCompoundOldName("Purchase.wholesalePriceUserInvoiceDetail"));
                    props.add(new ImportProperty(wholesalePriceUserInvoiceDetailField, purchaseInvoiceWholesalePriceLM.findLCPByCompoundOldName("Purchase.wholesalePriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(wholesalePriceUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).wholesalePrice);

                    ImportField wholesaleMarkupUserInvoiceDetailField = new ImportField(purchaseInvoiceWholesalePriceLM.findLCPByCompoundOldName("Purchase.wholesaleMarkupUserInvoiceDetail"));
                    props.add(new ImportProperty(wholesaleMarkupUserInvoiceDetailField, purchaseInvoiceWholesalePriceLM.findLCPByCompoundOldName("Purchase.wholesaleMarkupUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(wholesaleMarkupUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).wholesaleMarkup);

                }

                ImportField dateUserInvoiceField = new ImportField(getLCP("Purchase.dateUserInvoice"));
                props.add(new ImportProperty(dateUserInvoiceField, getLCP("Purchase.dateUserInvoice").getMapping(userInvoiceKey)));
                fields.add(dateUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).date);


                ImportField timeUserInvoiceField = new ImportField(TimeClass.instance);
                props.add(new ImportProperty(timeUserInvoiceField, getLCP("Purchase.timeUserInvoice").getMapping(userInvoiceKey)));
                fields.add(timeUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(new Time(12, 0, 0));


                ImportField idItemField = new ImportField(getLCP("idItem"));
                ImportKey<?> itemKey = new ImportKey((CustomClass) getClass("Item"),
                        getLCP("itemId").getMapping(idItemField));
                itemKey.skipKey = skipKeys && !userInvoiceCreateNewItems;
                keys.add(itemKey);
                if (userInvoiceCreateNewItems)
                    props.add(new ImportProperty(idItemField, getLCP("idItem").getMapping(itemKey)));
                props.add(new ImportProperty(idItemField, getLCP("Purchase.skuUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(getClass("Sku")).getMapping(itemKey)));
                fields.add(idItemField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idItem);


                ImportField quantityUserInvoiceDetailField = new ImportField(getLCP("Purchase.quantityUserInvoiceDetail"));
                props.add(new ImportProperty(quantityUserInvoiceDetailField, getLCP("Purchase.quantityUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(quantityUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).quantity);


                ImportField priceUserInvoiceDetail = new ImportField(getLCP("Purchase.priceUserInvoiceDetail"));
                props.add(new ImportProperty(priceUserInvoiceDetail, getLCP("Purchase.priceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(priceUserInvoiceDetail);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).price);
                
                if (showField(dataUserInvoiceDetail, "shipmentPrice")) {
                    ImportField shipmentPriceInvoiceDetail = new ImportField(getLCP("Purchase.shipmentPriceUserInvoiceDetail"));
                    props.add(new ImportProperty(shipmentPriceInvoiceDetail, getLCP("Purchase.shipmentPriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(shipmentPriceInvoiceDetail);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).shipmentPrice);
                }

                if (showField(dataUserInvoiceDetail, "shipmentSum")) {
                    ImportField shipmentSumInvoiceDetail = new ImportField(getLCP("Purchase.shipmentSumUserInvoiceDetail"));
                    props.add(new ImportProperty(shipmentSumInvoiceDetail, getLCP("Purchase.shipmentSumUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(shipmentSumInvoiceDetail);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).shipmentSum);
                }
                    
                ImportField expiryDateUserInvoiceDetailField = new ImportField(getLCP("Purchase.expiryDateUserInvoiceDetail"));
                props.add(new ImportProperty(expiryDateUserInvoiceDetailField, getLCP("Purchase.expiryDateUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(expiryDateUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).expiryDate);

                if (!skipExtraInvoiceParams) {

                    ImportField dataRateExchangeUserInvoiceDetailField = new ImportField(getLCP("Purchase.dataRateExchangeUserInvoiceDetail"));
                    props.add(new ImportProperty(dataRateExchangeUserInvoiceDetailField, getLCP("Purchase.dataRateExchangeUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(dataRateExchangeUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).rateExchange);


                    ImportField homePriceUserInvoiceDetailField = new ImportField(getLCP("Purchase.homePriceUserInvoiceDetail"));
                    props.add(new ImportProperty(homePriceUserInvoiceDetailField, getLCP("Purchase.homePriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(homePriceUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).homePrice);


                    if (showField(dataUserInvoiceDetail, "priceDuty")) {
                        ImportField priceDutyUserInvoiceDetailField = new ImportField(getLCP("Purchase.dutyPriceUserInvoiceDetail"));
                        props.add(new ImportProperty(priceDutyUserInvoiceDetailField, getLCP("Purchase.dutyPriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                        fields.add(priceDutyUserInvoiceDetailField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).priceDuty);
                    }

                    if (showField(dataUserInvoiceDetail, "priceCompliance") && purchaseComplianceDetailLM != null) {
                        ImportField priceComplianceUserInvoiceDetailField = new ImportField(purchaseComplianceDetailLM.findLCPByCompoundOldName("Purchase.compliancePriceUserInvoiceDetail"));
                        props.add(new ImportProperty(priceComplianceUserInvoiceDetailField, purchaseComplianceDetailLM.findLCPByCompoundOldName("Purchase.compliancePriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                        fields.add(priceComplianceUserInvoiceDetailField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).priceCompliance);

                        ImportField showComplianceUserInvoiceDetailField = new ImportField(purchaseComplianceDetailLM.findLCPByCompoundOldName("showComplianceUserInvoice"));
                        props.add(new ImportProperty(showComplianceUserInvoiceDetailField, purchaseComplianceDetailLM.findLCPByCompoundOldName("showComplianceUserInvoice").getMapping(userInvoiceKey)));
                        fields.add(showComplianceUserInvoiceDetailField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(true);
                    }

                    if (showField(dataUserInvoiceDetail, "priceRegistration")) {
                        ImportField priceRegistrationUserInvoiceDetailField = new ImportField(getLCP("Purchase.registrationPriceUserInvoiceDetail"));
                        props.add(new ImportProperty(priceRegistrationUserInvoiceDetailField, getLCP("Purchase.registrationPriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                        fields.add(priceRegistrationUserInvoiceDetailField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).priceRegistration);
                    }

                    if (showField(dataUserInvoiceDetail, "chargePrice") || showField(dataUserInvoiceDetail, "chargeSum")) {

                        if (purchaseInvoiceChargeLM != null) {
                            ImportField chargePriceUserInvoiceDetailField = new ImportField(purchaseInvoiceChargeLM.findLCPByCompoundOldName("chargePriceUserInvoiceDetail"));
                            props.add(new ImportProperty(chargePriceUserInvoiceDetailField, purchaseInvoiceChargeLM.findLCPByCompoundOldName("chargePriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                            fields.add(chargePriceUserInvoiceDetailField);
                            for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                                data.get(i).add(dataUserInvoiceDetail.get(i).chargePrice);

                            ImportField chargeSumUserInvoiceDetailField = new ImportField(purchaseInvoiceChargeLM.findLCPByCompoundOldName("chargeSumUserInvoiceDetail"));
                            props.add(new ImportProperty(chargeSumUserInvoiceDetailField, purchaseInvoiceChargeLM.findLCPByCompoundOldName("chargeSumUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                            fields.add(chargeSumUserInvoiceDetailField);
                            for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                                data.get(i).add(dataUserInvoiceDetail.get(i).chargeSum);

                            ImportField showChargePriceUserInvoiceDetailField = new ImportField(purchaseInvoiceChargeLM.findLCPByCompoundOldName("showChargePriceUserInvoice"));
                            props.add(new ImportProperty(showChargePriceUserInvoiceDetailField, purchaseInvoiceChargeLM.findLCPByCompoundOldName("showChargePriceUserInvoice").getMapping(userInvoiceKey)));
                            fields.add(showChargePriceUserInvoiceDetailField);
                            for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                                data.get(i).add(true);
                        }
                    }
                }

                if (showField(dataUserInvoiceDetail, "idBin")) {
                    ImportField binUserInvoiceDetailField = new ImportField(getLCP("idBin"));
                    ImportKey<?> binKey = new ImportKey((ConcreteCustomClass) getClass("Bin"),
                            getLCP("binId").getMapping(binUserInvoiceDetailField));
                    keys.add(binKey);
                    props.add(new ImportProperty(binUserInvoiceDetailField, getLCP("idBin").getMapping(binKey)));
                    props.add(new ImportProperty(binUserInvoiceDetailField, getLCP("nameBin").getMapping(binKey)));
                    props.add(new ImportProperty(binUserInvoiceDetailField, getLCP("binUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                            LM.object(getClass("Bin")).getMapping(binKey)));
                    fields.add(binUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).idBin);

                    ImportField showBinUserInvoiceField = new ImportField(getLCP("showBinUserInvoice"));
                    props.add(new ImportProperty(showBinUserInvoiceField, getLCP("showBinUserInvoice").getMapping(userInvoiceKey)));
                    fields.add(showBinUserInvoiceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                }

                if (!skipExtraInvoiceParams) {
                    if (purchaseManufacturingPriceLM != null) {
                        ImportField manufacturingPriceUserInvoiceDetailField = new ImportField(purchaseManufacturingPriceLM.findLCPByCompoundOldName("manufacturingPriceUserInvoiceDetail"));
                        props.add(new ImportProperty(manufacturingPriceUserInvoiceDetailField, purchaseManufacturingPriceLM.findLCPByCompoundOldName("manufacturingPriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                        fields.add(manufacturingPriceUserInvoiceDetailField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).manufacturingPrice);

                        ImportField manufacturingMarkupUserInvoiceDetailField = new ImportField(purchaseManufacturingPriceLM.findLCPByCompoundOldName("manufacturingMarkupUserInvoiceDetail"));
                        props.add(new ImportProperty(manufacturingMarkupUserInvoiceDetailField, purchaseManufacturingPriceLM.findLCPByCompoundOldName("manufacturingMarkupUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                        fields.add(manufacturingMarkupUserInvoiceDetailField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).manufacturingMarkup);
                    }
                }
                
                if(purchaseCertificateLM != null) {
                    ImportField certificateTextUserInvoiceDetailField = new ImportField(purchaseCertificateLM.findLCPByCompoundOldName("certificateTextUserInvoiceDetail"));
                    props.add(new ImportProperty(certificateTextUserInvoiceDetailField, purchaseCertificateLM.findLCPByCompoundOldName("certificateTextUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(certificateTextUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).certificateText);
                }
                    
                if (warePurchaseInvoiceLM != null) {
                    ImportField skipCreateWareUserInvoiceDetailField = new ImportField(warePurchaseInvoiceLM.findLCPByCompoundOldName("skipCreateWareUserInvoiceDetail"));
                    props.add(new ImportProperty(skipCreateWareUserInvoiceDetailField, warePurchaseInvoiceLM.findLCPByCompoundOldName("skipCreateWareUserInvoiceDetail").getMapping(userInvoiceDetailKey), true));
                    fields.add(skipCreateWareUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                }


                if (showField(dataUserInvoiceDetail, "idContract")) {
                    ImportField userContractSkuField = new ImportField(getLCP("idUserContractSku"));
                    ImportKey<?> userContractSkuKey = new ImportKey((ConcreteCustomClass) getClass("UserContractSku"),
                            getLCP("userContractSkuId").getMapping(userContractSkuField));
                    userContractSkuKey.skipKey = skipKeys;
                    keys.add(userContractSkuKey);
                    props.add(new ImportProperty(userContractSkuField, getLCP("Purchase.contractSkuInvoice").getMapping(userInvoiceKey),
                            LM.object(getClass("Contract")).getMapping(userContractSkuKey)));
                    fields.add(userContractSkuField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).idContract);
                }
                
                if (!skipCertificateInvoiceParams) {
                    if (showField(dataUserInvoiceDetail, "numberDeclaration")) {
                        ImportField numberDeclarationField = new ImportField(getLCP("numberDeclaration"));
                        ImportKey<?> declarationKey = new ImportKey((ConcreteCustomClass) getClass("Declaration"),
                                getLCP("declarationId").getMapping(numberDeclarationField));
                        keys.add(declarationKey);
                        props.add(new ImportProperty(numberDeclarationField, getLCP("numberDeclaration").getMapping(declarationKey)));
                        props.add(new ImportProperty(numberDeclarationField, getLCP("idDeclaration").getMapping(declarationKey)));
                        props.add(new ImportProperty(numberDeclarationField, getLCP("Purchase.declarationUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                                LM.object(getClass("Declaration")).getMapping(declarationKey)));
                        fields.add(numberDeclarationField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).numberDeclaration);

                        ImportField dateDeclarationField = new ImportField(getLCP("dateDeclaration"));
                        props.add(new ImportProperty(dateDeclarationField, getLCP("dateDeclaration").getMapping(declarationKey)));
                        fields.add(dateDeclarationField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).dateDeclaration);
                    }

                    if (showField(dataUserInvoiceDetail, "numberCompliance")) {
                        ImportField numberComplianceField = new ImportField(getLCP("numberCompliance"));
                        ImportKey<?> complianceKey = new ImportKey((ConcreteCustomClass) getClass("Compliance"),
                                getLCP("complianceNumber").getMapping(numberComplianceField));
                        keys.add(complianceKey);
                        props.add(new ImportProperty(numberComplianceField, getLCP("numberCompliance").getMapping(complianceKey)));
                        props.add(new ImportProperty(numberComplianceField, getLCP("Purchase.complianceUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                                LM.object(getClass("Compliance")).getMapping(complianceKey)));
                        fields.add(numberComplianceField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).numberCompliance);

                        ImportField fromDateComplianceField = new ImportField(getLCP("fromDateCompliance"));
                        props.add(new ImportProperty(fromDateComplianceField, getLCP("dateCompliance").getMapping(complianceKey)));
                        props.add(new ImportProperty(fromDateComplianceField, getLCP("fromDateCompliance").getMapping(complianceKey)));
                        fields.add(fromDateComplianceField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).fromDateCompliance);

                        ImportField toDateComplianceField = new ImportField(getLCP("toDateCompliance"));
                        props.add(new ImportProperty(toDateComplianceField, getLCP("toDateCompliance").getMapping(complianceKey)));
                        fields.add(toDateComplianceField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).toDateCompliance);
                    }
                }
                
                if (!skipExtraInvoiceParams) {
                    if (showField(dataUserInvoiceDetail, "isHomeCurrency")) {
                        ImportField isHomeCurrencyUserInvoiceField = new ImportField(getLCP("Purchase.isHomeCurrencyUserInvoice"));
                        props.add(new ImportProperty(isHomeCurrencyUserInvoiceField, getLCP("Purchase.isHomeCurrencyUserInvoice").getMapping(userInvoiceKey), true));
                        fields.add(isHomeCurrencyUserInvoiceField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).isHomeCurrency);
                    }
                }

                if (showField(dataUserInvoiceDetail, "codeCustomsGroup") || showField(dataUserInvoiceDetail, "priceDuty") || showField(dataUserInvoiceDetail, "priceRegistration")) {
                    ImportField showDeclarationUserInvoiceField = new ImportField(getLCP("showDeclarationUserInvoice"));
                    props.add(new ImportProperty(showDeclarationUserInvoiceField, getLCP("showDeclarationUserInvoice").getMapping(userInvoiceKey), true));
                    fields.add(showDeclarationUserInvoiceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                }

                ImportField shortNameCurrencyField = new ImportField(getLCP("shortNameCurrency"));
                ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) getClass("Currency"),
                        getLCP("currencyShortName").getMapping(shortNameCurrencyField));
                keys.add(currencyKey);
                props.add(new ImportProperty(shortNameCurrencyField, getLCP("Purchase.currencyUserInvoice").getMapping(userInvoiceKey),
                        LM.object(getClass("Currency")).getMapping(currencyKey)));
                fields.add(shortNameCurrencyField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).shortNameCurrency);
                
                if (!skipCertificateInvoiceParams) {
                    if (purchaseDeclarationDetailLM != null) {
                        ObjectValue defaultCountryObject = getLCP("defaultCountry").readClasses(context.getSession());
                        ImportField codeCustomsGroupField = new ImportField(purchaseDeclarationDetailLM.findLCPByCompoundOldName("codeCustomsGroup"));
                        ImportKey<?> customsGroupKey = new ImportKey((CustomClass) purchaseDeclarationDetailLM.findClassByCompoundName("CustomsGroup"),
                                purchaseDeclarationDetailLM.findLCPByCompoundOldName("customsGroupCode").getMapping(codeCustomsGroupField));
                        keys.add(customsGroupKey);
                        props.add(new ImportProperty(codeCustomsGroupField, purchaseDeclarationDetailLM.findLCPByCompoundOldName("customsGroupUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                                purchaseDeclarationDetailLM.object(purchaseDeclarationDetailLM.findClassByCompoundName("CustomsGroup")).getMapping(customsGroupKey)));
                        props.add(new ImportProperty(codeCustomsGroupField, purchaseDeclarationDetailLM.findLCPByCompoundOldName("customsGroupCountryItem").getMapping(defaultCountryObject, itemKey),
                                purchaseDeclarationDetailLM.object(purchaseDeclarationDetailLM.findClassByCompoundName("CustomsGroup")).getMapping(customsGroupKey)));
                        fields.add(codeCustomsGroupField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).codeCustomsGroup);
                    }
                }
                    
                ImportField valueVATUserInvoiceDetailField = new ImportField(getLCP("Purchase.valueVATUserInvoiceDetail"));
                ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) getClass("Range"),
                        getLCP("valueCurrentVATDefaultValue").getMapping(valueVATUserInvoiceDetailField));
                VATKey.skipKey = skipKeys;
                keys.add(VATKey);
                props.add(new ImportProperty(valueVATUserInvoiceDetailField, getLCP("Purchase.VATUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(getClass("Range")).getMapping(VATKey)));
                fields.add(valueVATUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).retailVAT);

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

        if (priceListStoresList != null && importUserPriceListLM != null && storeLM != null) {

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

                ObjectValue dataPriceListTypeObject = getLCP("dataPriceListTypeId").readClasses(session, new DataObject("Coordinated", StringClass.get(100)));
                if (dataPriceListTypeObject instanceof NullValue) {
                    dataPriceListTypeObject = session.addObject((ConcreteCustomClass) getClass("DataPriceListType"));
                    Object defaultCurrency = getLCP("currencyShortName").read(session, new DataObject("BLR", StringClass.get(3)));
                    getLCP("namePriceListType").change("Поставщика (согласованная)", session, (DataObject) dataPriceListTypeObject);
                    getLCP("currencyDataPriceListType").change(defaultCurrency, session, (DataObject) dataPriceListTypeObject);
                    getLCP("idDataPriceListType").change("Coordinated", session, (DataObject) dataPriceListTypeObject);
                }

                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                List<List<Object>> data = initData(priceListStoresList.size());

                ImportField idItemField = new ImportField(getLCP("idItem"));
                ImportField idUserPriceListField = new ImportField(getLCP("idUserPriceList"));
                ImportKey<?> itemKey = new ImportKey((ConcreteCustomClass) getClass("Item"),
                        getLCP("itemId").getMapping(idItemField));
                keys.add(itemKey);
                ImportKey<?> userPriceListDetailKey = new ImportKey((ConcreteCustomClass) getClass("UserPriceListDetail"),
                        importUserPriceListLM.findLCPByCompoundOldName("userPriceListDetailIdSkuIdUserPriceList").getMapping(idItemField, idUserPriceListField));
                keys.add(userPriceListDetailKey);
                ImportKey<?> userPriceListKey = new ImportKey((ConcreteCustomClass) getClass("UserPriceList"),
                        getLCP("userPriceListId").getMapping(idUserPriceListField));
                keys.add(userPriceListKey);
                props.add(new ImportProperty(idItemField, getLCP("skuUserPriceListDetail").getMapping(userPriceListDetailKey),
                        LM.object(getClass("Item")).getMapping(itemKey)));
                props.add(new ImportProperty(idUserPriceListField, getLCP("idUserPriceList").getMapping(userPriceListKey)));
                props.add(new ImportProperty(idUserPriceListField, getLCP("userPriceListUserPriceListDetail").getMapping(userPriceListDetailKey),
                        LM.object(getClass("UserPriceList")).getMapping(userPriceListKey)));
                fields.add(idItemField);
                fields.add(idUserPriceListField);
                for (int i = 0; i < priceListStoresList.size(); i++) {
                    data.get(i).add(priceListStoresList.get(i).idItem);
                    data.get(i).add(priceListStoresList.get(i).idUserPriceList);
                }

                ImportField idLegalEntityField = new ImportField(getLCP("idLegalEntity"));
                ImportKey<?> legalEntityKey = new ImportKey((ConcreteCustomClass) getClass("LegalEntity"),
                        getLCP("legalEntityId").getMapping(idLegalEntityField));
                legalEntityKey.skipKey = skipKeys;
                keys.add(legalEntityKey);
                props.add(new ImportProperty(idLegalEntityField, getLCP("companyUserPriceList").getMapping(userPriceListKey),
                        LM.object(getClass("LegalEntity")).getMapping(legalEntityKey)));
                fields.add(idLegalEntityField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(priceListStoresList.get(i).idSupplier);

                ImportField idDepartmentStoreField = new ImportField(storeLM.findLCPByCompoundOldName("idDepartmentStore"));
                ImportKey<?> departmentStoreKey = new ImportKey((ConcreteCustomClass) storeLM.findClassByCompoundName("DepartmentStore"),
                        storeLM.findLCPByCompoundOldName("departmentStoreId").getMapping(idDepartmentStoreField));
                keys.add(departmentStoreKey);
                fields.add(idDepartmentStoreField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(priceListStoresList.get(i).idDepartmentStore);

                ImportField shortNameCurrencyField = new ImportField(getLCP("shortNameCurrency"));
                ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) getClass("Currency"),
                        getLCP("currencyShortName").getMapping(shortNameCurrencyField));
                keys.add(currencyKey);
                props.add(new ImportProperty(shortNameCurrencyField, getLCP("currencyUserPriceList").getMapping(userPriceListKey),
                        LM.object(getClass("Currency")).getMapping(currencyKey)));
                fields.add(shortNameCurrencyField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(priceListStoresList.get(i).shortNameCurrency);

                ImportField pricePriceListDetailField = new ImportField(getLCP("pricePriceListDetailDataPriceListType"));
                props.add(new ImportProperty(pricePriceListDetailField, getLCP("priceUserPriceListDetailDataPriceListType").getMapping(userPriceListDetailKey, dataPriceListTypeObject)));
                fields.add(pricePriceListDetailField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(priceListStoresList.get(i).pricePriceListDetail);

                ImportField inPriceListPriceListTypeField = new ImportField(getLCP("inPriceListDataPriceListType"));
                props.add(new ImportProperty(inPriceListPriceListTypeField, getLCP("inPriceListDataPriceListType").getMapping(userPriceListKey, dataPriceListTypeObject)));
                fields.add(inPriceListPriceListTypeField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(priceListStoresList.get(i).inPriceList);

                ImportField inPriceListStockField = new ImportField(getLCP("inPriceListStock"));
                props.add(new ImportProperty(inPriceListStockField, getLCP("inPriceListStock").getMapping(userPriceListKey, departmentStoreKey)));
                fields.add(inPriceListStockField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(priceListStoresList.get(i).inPriceListStock);

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

    private void importPriceListSuppliers(List<PriceListSupplier> priceListSuppliersList, Integer numberAtATime, boolean skipKeys) 
            throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (priceListSuppliersList != null) {

            if (numberAtATime == null)
                numberAtATime = priceListSuppliersList.size();

            for (int start = 0; true; start += numberAtATime) {

                int finish = (start + numberAtATime) < priceListSuppliersList.size() ? (start + numberAtATime) : priceListSuppliersList.size();
                List<PriceListSupplier> dataPriceListSuppliers = start < finish ? priceListSuppliersList.subList(start, finish) : new ArrayList<PriceListSupplier>();
                if (dataPriceListSuppliers.isEmpty())
                    return;

                ServerLoggers.systemLogger.info("importPriceListSuppliers " + dataPriceListSuppliers.size());

                DataSession session = context.createSession();
                session.pushVolatileStats("IA_PLSR");

                ObjectValue dataPriceListTypeObject = getLCP("dataPriceListTypeId").readClasses(session, new DataObject("Offered", StringClass.get(100)));
                if (dataPriceListTypeObject instanceof NullValue) {
                    dataPriceListTypeObject = session.addObject((ConcreteCustomClass) getClass("DataPriceListType"));
                    Object defaultCurrency = getLCP("currencyShortName").read(session, new DataObject("BLR", StringClass.get(3)));
                    getLCP("namePriceListType").change("Поставщика (предлагаемая)", session, (DataObject) dataPriceListTypeObject);
                    getLCP("currencyDataPriceListType").change(defaultCurrency, session, (DataObject) dataPriceListTypeObject);
                    getLCP("idDataPriceListType").change("Offered", session, (DataObject) dataPriceListTypeObject);
                }

                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                List<List<Object>> data = initData(priceListSuppliersList.size());

                ImportField idItemField = new ImportField(getLCP("idItem"));
                ImportField idUserPriceListField = new ImportField(getLCP("idUserPriceList"));
                ImportKey<?> itemKey = new ImportKey((ConcreteCustomClass) getClass("Item"),
                        getLCP("itemId").getMapping(idItemField));
                keys.add(itemKey);
                ImportKey<?> userPriceListDetailKey = new ImportKey((ConcreteCustomClass) getClass("UserPriceListDetail"),
                        importUserPriceListLM.findLCPByCompoundOldName("userPriceListDetailIdSkuIdUserPriceList").getMapping(idItemField, idUserPriceListField));
                keys.add(userPriceListDetailKey);
                ImportKey<?> userPriceListKey = new ImportKey((ConcreteCustomClass) getClass("UserPriceList"),
                        getLCP("userPriceListId").getMapping(idUserPriceListField));
                keys.add(userPriceListKey);
                props.add(new ImportProperty(idItemField, getLCP("skuUserPriceListDetail").getMapping(userPriceListDetailKey),
                        LM.object(getClass("Item")).getMapping(itemKey)));
                props.add(new ImportProperty(idUserPriceListField, getLCP("idUserPriceList").getMapping(userPriceListKey)));
                props.add(new ImportProperty(idUserPriceListField, getLCP("userPriceListUserPriceListDetail").getMapping(userPriceListDetailKey),
                        LM.object(getClass("UserPriceList")).getMapping(userPriceListKey)));
                fields.add(idItemField);
                fields.add(idUserPriceListField);
                for (int i = 0; i < priceListSuppliersList.size(); i++) {
                    data.get(i).add(priceListSuppliersList.get(i).idItem);
                    data.get(i).add(priceListSuppliersList.get(i).idUserPriceList);
                }

                ImportField idLegalEntityField = new ImportField(getLCP("idLegalEntity"));
                ImportKey<?> legalEntityKey = new ImportKey((ConcreteCustomClass) getClass("LegalEntity"),
                        getLCP("legalEntityId").getMapping(idLegalEntityField));
                legalEntityKey.skipKey = skipKeys;
                keys.add(legalEntityKey);
                props.add(new ImportProperty(idLegalEntityField, getLCP("companyUserPriceList").getMapping(userPriceListKey),
                        LM.object(getClass("LegalEntity")).getMapping(legalEntityKey)));
                fields.add(idLegalEntityField);
                for (int i = 0; i < priceListSuppliersList.size(); i++)
                    data.get(i).add(priceListSuppliersList.get(i).idSupplier);

                ImportField shortNameCurrencyField = new ImportField(getLCP("shortNameCurrency"));
                ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) getClass("Currency"),
                        getLCP("currencyShortName").getMapping(shortNameCurrencyField));
                keys.add(currencyKey);
                props.add(new ImportProperty(shortNameCurrencyField, getLCP("currencyUserPriceList").getMapping(userPriceListKey),
                        LM.object(getClass("Currency")).getMapping(currencyKey)));
                fields.add(shortNameCurrencyField);
                for (int i = 0; i < priceListSuppliersList.size(); i++)
                    data.get(i).add(priceListSuppliersList.get(i).shortNameCurrency);

                ImportField pricePriceListDetailField = new ImportField(getLCP("pricePriceListDetailDataPriceListType"));
                props.add(new ImportProperty(pricePriceListDetailField, getLCP("priceUserPriceListDetailDataPriceListType").getMapping(userPriceListDetailKey, dataPriceListTypeObject)));
                fields.add(pricePriceListDetailField);
                for (int i = 0; i < priceListSuppliersList.size(); i++)
                    data.get(i).add(priceListSuppliersList.get(i).pricePriceListDetail);

                ImportField inPriceListPriceListTypeField = new ImportField(getLCP("inPriceListDataPriceListType"));
                props.add(new ImportProperty(inPriceListPriceListTypeField, getLCP("inPriceListDataPriceListType").getMapping(userPriceListKey, dataPriceListTypeObject)));
                fields.add(inPriceListPriceListTypeField);
                for (int i = 0; i < priceListSuppliersList.size(); i++)
                    data.get(i).add(priceListSuppliersList.get(i).inPriceList);

                ImportField allStocksUserPriceListField = new ImportField(getLCP("allStocksUserPriceList"));
                props.add(new ImportProperty(allStocksUserPriceListField, getLCP("allStocksUserPriceList").getMapping(userPriceListKey)));
                fields.add(allStocksUserPriceListField);
                for (int i = 0; i < priceListSuppliersList.size(); i++)
                    data.get(i).add(priceListSuppliersList.get(i).inPriceList);

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

        if (legalEntitiesList != null) {

            ServerLoggers.systemLogger.info("importLegalEntities");

            DataObject defaultDate = new DataObject(new java.sql.Date(2001 - 1900, 0, 01), DateClass.instance);

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(legalEntitiesList.size());

            ImportField idLegalEntityField = new ImportField(getLCP("idLegalEntity"));
            ImportKey<?> legalEntityKey = new ImportKey((ConcreteCustomClass) getClass("LegalEntity"),
                    getLCP("legalEntityId").getMapping(idLegalEntityField));
            keys.add(legalEntityKey);
            props.add(new ImportProperty(idLegalEntityField, getLCP("idLegalEntity").getMapping(legalEntityKey)));
            fields.add(idLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).idLegalEntity);

            ImportField numberAccountField = new ImportField(getLCP("Bank.numberAccount"));
            ImportKey<?> accountKey = new ImportKey((ConcreteCustomClass) getClass("Bank.Account"),
                    getLCP("accountNumberLegalEntityID").getMapping(numberAccountField, idLegalEntityField));
            keys.add(accountKey);
            props.add(new ImportProperty(numberAccountField, getLCP("Bank.numberAccount").getMapping(accountKey)));
            props.add(new ImportProperty(idLegalEntityField, getLCP("Bank.legalEntityAccount").getMapping(accountKey),
                    LM.object(getClass("LegalEntity")).getMapping(legalEntityKey)));
            fields.add(numberAccountField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).numberAccount);

            ImportField nameLegalEntityField = new ImportField(getLCP("nameLegalEntity"));
            props.add(new ImportProperty(nameLegalEntityField, getLCP("nameLegalEntity").getMapping(legalEntityKey)));
            props.add(new ImportProperty(nameLegalEntityField, getLCP("fullNameLegalEntity").getMapping(legalEntityKey)));
            fields.add(nameLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).nameLegalEntity);

            ImportField addressLegalEntityField = new ImportField(getLCP("addressLegalEntity"));
            props.add(new ImportProperty(addressLegalEntityField, getLCP("dataAddressLegalEntityDate").getMapping(legalEntityKey, defaultDate)));
            fields.add(addressLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).addressLegalEntity);

            if(legalEntityByLM != null) {
                ImportField unpLegalEntityField = new ImportField(legalEntityByLM.findLCPByCompoundOldName("UNPLegalEntity"));
                props.add(new ImportProperty(unpLegalEntityField, legalEntityByLM.findLCPByCompoundOldName("UNPLegalEntity").getMapping(legalEntityKey)));
                fields.add(unpLegalEntityField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).unpLegalEntity);

                ImportField okpoLegalEntityField = new ImportField(legalEntityByLM.findLCPByCompoundOldName("OKPOLegalEntity"));
                props.add(new ImportProperty(okpoLegalEntityField, legalEntityByLM.findLCPByCompoundOldName("OKPOLegalEntity").getMapping(legalEntityKey)));
                fields.add(okpoLegalEntityField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).okpoLegalEntity);
            }
            
            ImportField phoneLegalEntityField = new ImportField(getLCP("dataPhoneLegalEntityDate"));
            props.add(new ImportProperty(phoneLegalEntityField, getLCP("dataPhoneLegalEntityDate").getMapping(legalEntityKey, defaultDate)));
            fields.add(phoneLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).phoneLegalEntity);

            ImportField emailLegalEntityField = new ImportField(getLCP("emailLegalEntity"));
            props.add(new ImportProperty(emailLegalEntityField, getLCP("emailLegalEntity").getMapping(legalEntityKey)));
            fields.add(emailLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).emailLegalEntity);

            ImportField isSupplierLegalEntityField = new ImportField(getLCP("isSupplierLegalEntity"));
            props.add(new ImportProperty(isSupplierLegalEntityField, getLCP("isSupplierLegalEntity").getMapping(legalEntityKey), true));
            fields.add(isSupplierLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).isSupplierLegalEntity);

            ImportField isCompanyLegalEntityField = new ImportField(getLCP("isCompanyLegalEntity"));
            props.add(new ImportProperty(isCompanyLegalEntityField, getLCP("isCompanyLegalEntity").getMapping(legalEntityKey), true));
            fields.add(isCompanyLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).isCompanyLegalEntity);

            ImportField isCustomerLegalEntityField = new ImportField(getLCP("isCustomerLegalEntity"));
            props.add(new ImportProperty(isCustomerLegalEntityField, getLCP("isCustomerLegalEntity").getMapping(legalEntityKey), true));
            fields.add(isCustomerLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).isCustomerLegalEntity);

            ImportField shortNameOwnershipField = new ImportField(getLCP("shortNameOwnership"));
            ImportKey<?> ownershipKey = new ImportKey((ConcreteCustomClass) getClass("Ownership"),
                    getLCP("ownershipShortName").getMapping(shortNameOwnershipField));
            keys.add(ownershipKey);
            props.add(new ImportProperty(shortNameOwnershipField, getLCP("shortNameOwnership").getMapping(ownershipKey)));
            props.add(new ImportProperty(shortNameOwnershipField, getLCP("ownershipLegalEntity").getMapping(legalEntityKey),
                    LM.object(getClass("Ownership")).getMapping(ownershipKey)));
            fields.add(shortNameOwnershipField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).shortNameOwnership);

            ImportField nameOwnershipField = new ImportField(getLCP("nameOwnership"));
            props.add(new ImportProperty(nameOwnershipField, getLCP("nameOwnership").getMapping(ownershipKey)));
            fields.add(nameOwnershipField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).nameOwnership);

            if (storeLM != null) {

                ImportField idChainStoresField = new ImportField(storeLM.findLCPByCompoundOldName("idChainStores"));
                ImportKey<?> chainStoresKey = new ImportKey((ConcreteCustomClass) storeLM.findClassByCompoundName("ChainStores"),
                        storeLM.findLCPByCompoundOldName("chainStoresId").getMapping(idChainStoresField));
                keys.add(chainStoresKey);
                props.add(new ImportProperty(idChainStoresField, storeLM.findLCPByCompoundOldName("idChainStores").getMapping(chainStoresKey)));
                fields.add(idChainStoresField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).idChainStores);

                ImportField nameChainStoresField = new ImportField(storeLM.findLCPByCompoundOldName("nameChainStores"));
                props.add(new ImportProperty(nameChainStoresField, storeLM.findLCPByCompoundOldName("nameChainStores").getMapping(chainStoresKey)));
                fields.add(nameChainStoresField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).nameChainStores);

            }

            ImportField idBankField = new ImportField(getLCP("idBank"));
            ImportKey<?> bankKey = new ImportKey((ConcreteCustomClass) getClass("Bank"),
                    getLCP("bankId").getMapping(idBankField));
            keys.add(bankKey);
            props.add(new ImportProperty(idBankField, getLCP("idBank").getMapping(bankKey)));
            props.add(new ImportProperty(idBankField, getLCP("nameBank").getMapping(bankKey)));
            props.add(new ImportProperty(idBankField, getLCP("Bank.bankAccount").getMapping(accountKey),
                    LM.object(getClass("Bank")).getMapping(bankKey)));
            fields.add(idBankField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).idBank);

            ImportField nameCountryField = new ImportField(getLCP("nameCountry"));
            ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) getClass("Country"),
                    getLCP("countryName").getMapping(nameCountryField));
            keys.add(countryKey);
            props.add(new ImportProperty(nameCountryField, getLCP("nameCountry").getMapping(countryKey)));
            props.add(new ImportProperty(nameCountryField, getLCP("countryLegalEntity").getMapping(legalEntityKey),
                    LM.object(getClass("Country")).getMapping(countryKey)));
            props.add(new ImportProperty(nameCountryField, getLCP("countryOwnership").getMapping(ownershipKey),
                    LM.object(getClass("Country")).getMapping(countryKey)));
            fields.add(nameCountryField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).nameCountry);

            ImportField shortNameCurrencyField = new ImportField(getLCP("shortNameCurrency"));
            ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) getClass("Currency"),
                    getLCP("currencyShortName").getMapping(shortNameCurrencyField));
            keys.add(currencyKey);
            props.add(new ImportProperty(shortNameCurrencyField, getLCP("Bank.currencyAccount").getMapping(accountKey),
                    LM.object(getClass("Currency")).getMapping(currencyKey)));
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

        if (employeesList != null) {

            ServerLoggers.systemLogger.info("importEmployees");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(employeesList.size());

            ImportField idEmployeeField = new ImportField(getLCP("idEmployee"));
            ImportKey<?> employeeKey = new ImportKey((ConcreteCustomClass) getClass("Employee"),
                    getLCP("employeeId").getMapping(idEmployeeField));
            keys.add(employeeKey);
            props.add(new ImportProperty(idEmployeeField, getLCP("idEmployee").getMapping(employeeKey)));
            fields.add(idEmployeeField);
            for (int i = 0; i < employeesList.size(); i++)
                data.get(i).add(employeesList.get(i).idEmployee);

            ImportField firstNameEmployeeField = new ImportField(getLCP("firstNameContact"));
            props.add(new ImportProperty(firstNameEmployeeField, getLCP("firstNameContact").getMapping(employeeKey)));
            fields.add(firstNameEmployeeField);
            for (int i = 0; i < employeesList.size(); i++)
                data.get(i).add(employeesList.get(i).firstNameEmployee);

            ImportField lastNameEmployeeField = new ImportField(getLCP("lastNameContact"));
            props.add(new ImportProperty(lastNameEmployeeField, getLCP("lastNameContact").getMapping(employeeKey)));
            fields.add(lastNameEmployeeField);
            for (int i = 0; i < employeesList.size(); i++)
                data.get(i).add(employeesList.get(i).lastNameEmployee);

            ImportField idPositionField = new ImportField(getLCP("idPosition"));
            ImportKey<?> positionKey = new ImportKey((ConcreteCustomClass) getClass("Position"),
                    getLCP("positionId").getMapping(idPositionField));
            keys.add(positionKey);
            props.add(new ImportProperty(idPositionField, getLCP("idPosition").getMapping(positionKey)));
            props.add(new ImportProperty(idPositionField, getLCP("positionEmployee").getMapping(employeeKey),
                    LM.object(getClass("Position")).getMapping(positionKey)));
            fields.add(idPositionField);
            for (int i = 0; i < employeesList.size(); i++)
                data.get(i).add(employeesList.get(i).idPosition);

            ImportField namePositionField = new ImportField(getLCP("namePosition"));
            props.add(new ImportProperty(namePositionField, getLCP("namePosition").getMapping(positionKey)));
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

        if (warehouseGroupsList != null) {

            ServerLoggers.systemLogger.info("importWarehouseGroups");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(warehouseGroupsList.size());

            ImportField idWarehouseGroupField = new ImportField(getLCP("idWarehouseGroup"));
            ImportKey<?> warehouseGroupKey = new ImportKey((ConcreteCustomClass) getClass("WarehouseGroup"),
                    getLCP("warehouseGroupId").getMapping(idWarehouseGroupField));
            keys.add(warehouseGroupKey);
            props.add(new ImportProperty(idWarehouseGroupField, getLCP("idWarehouseGroup").getMapping(warehouseGroupKey)));
            fields.add(idWarehouseGroupField);
            for (int i = 0; i < warehouseGroupsList.size(); i++)
                data.get(i).add(warehouseGroupsList.get(i).idWarehouseGroup);

            ImportField nameWarehouseGroupField = new ImportField(getLCP("nameWarehouseGroup"));
            props.add(new ImportProperty(nameWarehouseGroupField, getLCP("nameWarehouseGroup").getMapping(warehouseGroupKey)));
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

        if (warehousesList != null) {

            ServerLoggers.systemLogger.info("importWarehouses");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(warehousesList.size());

            ImportField idWarehouseField = new ImportField(getLCP("idWarehouse"));
            ImportKey<?> warehouseKey = new ImportKey((ConcreteCustomClass) getClass("Warehouse"),
                    getLCP("warehouseId").getMapping(idWarehouseField));
            keys.add(warehouseKey);
            props.add(new ImportProperty(idWarehouseField, getLCP("idWarehouse").getMapping(warehouseKey)));
            fields.add(idWarehouseField);
            for (int i = 0; i < warehousesList.size(); i++)
                data.get(i).add(warehousesList.get(i).idWarehouse);

            ImportField nameWarehouseField = new ImportField(getLCP("nameWarehouse"));
            props.add(new ImportProperty(nameWarehouseField, getLCP("nameWarehouse").getMapping(warehouseKey)));
            fields.add(nameWarehouseField);
            for (int i = 0; i < warehousesList.size(); i++)
                data.get(i).add(warehousesList.get(i).nameWarehouse);

            ImportField addressWarehouseField = new ImportField(getLCP("addressWarehouse"));
            props.add(new ImportProperty(addressWarehouseField, getLCP("addressWarehouse").getMapping(warehouseKey)));
            fields.add(addressWarehouseField);
            for (int i = 0; i < warehousesList.size(); i++)
                data.get(i).add(warehousesList.get(i).addressWarehouse);

            ImportField idLegalEntityField = new ImportField(getLCP("idLegalEntity"));
            ImportKey<?> legalEntityKey = new ImportKey((ConcreteCustomClass) getClass("LegalEntity"),
                    getLCP("legalEntityId").getMapping(idLegalEntityField));
            keys.add(legalEntityKey);
            props.add(new ImportProperty(idLegalEntityField, getLCP("idLegalEntity").getMapping(legalEntityKey)));
            props.add(new ImportProperty(idLegalEntityField, getLCP("legalEntityWarehouse").getMapping(warehouseKey),
                    LM.object(getClass("LegalEntity")).getMapping(legalEntityKey)));
            fields.add(idLegalEntityField);
            for (int i = 0; i < warehousesList.size(); i++)
                data.get(i).add(warehousesList.get(i).idLegalEntity);

            ImportField idWarehouseGroupField = new ImportField(getLCP("idWarehouseGroup"));
            ImportKey<?> warehouseGroupKey = new ImportKey((ConcreteCustomClass) getClass("WarehouseGroup"),
                    getLCP("warehouseGroupId").getMapping(idWarehouseGroupField));
            keys.add(warehouseGroupKey);
            props.add(new ImportProperty(idWarehouseGroupField, getLCP("warehouseGroupWarehouse").getMapping(warehouseKey),
                    LM.object(getClass("WarehouseGroup")).getMapping(warehouseGroupKey)));
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

        if (storeLM != null && storesList != null) {

            ServerLoggers.systemLogger.info("importStores");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(storesList.size());

            ImportField idStoreField = new ImportField(storeLM.findLCPByCompoundOldName("idStore"));
            ImportKey<?> storeKey = new ImportKey((ConcreteCustomClass) storeLM.findClassByCompoundName("Store"),
                    storeLM.findLCPByCompoundOldName("storeId").getMapping(idStoreField));
            keys.add(storeKey);
            props.add(new ImportProperty(idStoreField, storeLM.findLCPByCompoundOldName("idStore").getMapping(storeKey)));
            fields.add(idStoreField);
            for (int i = 0; i < storesList.size(); i++)
                data.get(i).add(((Store) storesList.get(i)).idStore);

            ImportField nameStoreField = new ImportField(storeLM.findLCPByCompoundOldName("nameStore"));
            props.add(new ImportProperty(nameStoreField, storeLM.findLCPByCompoundOldName("nameStore").getMapping(storeKey)));
            fields.add(nameStoreField);
            for (int i = 0; i < storesList.size(); i++)
                data.get(i).add(storesList.get(i).nameLegalEntity);

            ImportField addressStoreField = new ImportField(storeLM.findLCPByCompoundOldName("addressStore"));
            props.add(new ImportProperty(addressStoreField, storeLM.findLCPByCompoundOldName("addressStore").getMapping(storeKey)));
            fields.add(addressStoreField);
            for (int i = 0; i < storesList.size(); i++)
                data.get(i).add(storesList.get(i).addressLegalEntity);

            ImportField idLegalEntityField = new ImportField(getLCP("idLegalEntity"));
            ImportKey<?> legalEntityKey = new ImportKey((ConcreteCustomClass) getClass("LegalEntity"),
                    getLCP("legalEntityId").getMapping(idLegalEntityField));
            legalEntityKey.skipKey = skipKeys;
            keys.add(legalEntityKey);
            props.add(new ImportProperty(idLegalEntityField, storeLM.findLCPByCompoundOldName("legalEntityStore").getMapping(storeKey),
                    LM.object(getClass("LegalEntity")).getMapping(legalEntityKey)));
            fields.add(idLegalEntityField);
            for (int i = 0; i < storesList.size(); i++)
                data.get(i).add(storesList.get(i).idLegalEntity);

            ImportField idChainStoresField = new ImportField(storeLM.findLCPByCompoundOldName("idChainStores"));
            ImportKey<?> chainStoresKey = new ImportKey((ConcreteCustomClass) storeLM.findClassByCompoundName("ChainStores"),
                    storeLM.findLCPByCompoundOldName("chainStoresId").getMapping(idChainStoresField));
            keys.add(chainStoresKey);
            fields.add(idChainStoresField);
            for (int i = 0; i < storesList.size(); i++)
                data.get(i).add(storesList.get(i).idChainStores);

            ImportField storeTypeField = new ImportField(storeLM.findLCPByCompoundOldName("nameStoreType"));
            ImportKey<?> storeTypeKey = new ImportKey((ConcreteCustomClass) storeLM.findClassByCompoundName("StoreType"),
                    storeLM.findLCPByCompoundOldName("storeTypeNameChainStores").getMapping(storeTypeField, idChainStoresField));
            keys.add(storeTypeKey);
            props.add(new ImportProperty(idChainStoresField, storeLM.findLCPByCompoundOldName("chainStoresStoreType").getMapping(storeTypeKey),
                    storeLM.object(storeLM.findClassByCompoundName("ChainStores")).getMapping(chainStoresKey)));
            props.add(new ImportProperty(storeTypeField, storeLM.findLCPByCompoundOldName("nameStoreType").getMapping(storeTypeKey)));
            props.add(new ImportProperty(storeTypeField, storeLM.findLCPByCompoundOldName("storeTypeStore").getMapping(storeKey),
                    storeLM.object(storeLM.findClassByCompoundName("StoreType")).getMapping(storeTypeKey)));
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

        if (storeLM != null && departmentStoresList != null) {

            ServerLoggers.systemLogger.info("importDepartmentStores");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(departmentStoresList.size());

            ImportField idDepartmentStoreField = new ImportField(storeLM.findLCPByCompoundOldName("idDepartmentStore"));
            ImportKey<?> departmentStoreKey = new ImportKey((ConcreteCustomClass) storeLM.findClassByCompoundName("DepartmentStore"),
                    storeLM.findLCPByCompoundOldName("departmentStoreId").getMapping(idDepartmentStoreField));
            keys.add(departmentStoreKey);
            props.add(new ImportProperty(idDepartmentStoreField, storeLM.findLCPByCompoundOldName("idDepartmentStore").getMapping(departmentStoreKey)));
            fields.add(idDepartmentStoreField);
            for (int i = 0; i < departmentStoresList.size(); i++)
                data.get(i).add((departmentStoresList.get(i)).idDepartmentStore);

            ImportField nameDepartmentStoreField = new ImportField(storeLM.findLCPByCompoundOldName("nameDepartmentStore"));
            props.add(new ImportProperty(nameDepartmentStoreField, storeLM.findLCPByCompoundOldName("nameDepartmentStore").getMapping(departmentStoreKey)));
            fields.add(nameDepartmentStoreField);
            for (int i = 0; i < departmentStoresList.size(); i++)
                data.get(i).add((departmentStoresList.get(i)).nameDepartmentStore);

            ImportField idStoreField = new ImportField(storeLM.findLCPByCompoundOldName("idStore"));
            ImportKey<?> storeKey = new ImportKey((ConcreteCustomClass) storeLM.findClassByCompoundName("Store"),
                    storeLM.findLCPByCompoundOldName("storeId").getMapping(idStoreField));
            keys.add(storeKey);
            props.add(new ImportProperty(idStoreField, storeLM.findLCPByCompoundOldName("storeDepartmentStore").getMapping(departmentStoreKey),
                    storeLM.object(storeLM.findClassByCompoundName("Store")).getMapping(storeKey)));
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

        if (banksList != null) {

            ServerLoggers.systemLogger.info("importBanks");

            DataObject defaultDate = new DataObject(new java.sql.Date(2001 - 1900, 0, 01), DateClass.instance);

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(banksList.size());

            ImportField idBankField = new ImportField(getLCP("idBank"));
            ImportKey<?> bankKey = new ImportKey((ConcreteCustomClass) getClass("Bank"),
                    getLCP("bankId").getMapping(idBankField));
            keys.add(bankKey);
            props.add(new ImportProperty(idBankField, getLCP("idBank").getMapping(bankKey)));
            fields.add(idBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).idBank);

            ImportField nameBankField = new ImportField(getLCP("nameBank"));
            props.add(new ImportProperty(nameBankField, getLCP("nameBank").getMapping(bankKey)));
            fields.add(nameBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).nameBank);

            ImportField addressBankField = new ImportField(getLCP("dataAddressBankDate"));
            props.add(new ImportProperty(addressBankField, getLCP("dataAddressBankDate").getMapping(bankKey, defaultDate)));
            fields.add(addressBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).addressBank);

            ImportField departmentBankField = new ImportField(getLCP("departmentBank"));
            props.add(new ImportProperty(departmentBankField, getLCP("departmentBank").getMapping(bankKey)));
            fields.add(departmentBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).departmentBank);

            ImportField mfoBankField = new ImportField(getLCP("MFOBank"));
            props.add(new ImportProperty(mfoBankField, getLCP("MFOBank").getMapping(bankKey)));
            fields.add(mfoBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).mfoBank);

            ImportField cbuBankField = new ImportField(getLCP("CBUBank"));
            props.add(new ImportProperty(cbuBankField, getLCP("CBUBank").getMapping(bankKey)));
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

        if (writeOffItemLM != null && rateWastesList != null) {

            ServerLoggers.systemLogger.info("importRateWastes");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(rateWastesList.size());

            ImportField idWriteOffRateField = new ImportField(writeOffItemLM.findLCPByCompoundOldName("idWriteOffRate"));
            ImportKey<?> writeOffRateKey = new ImportKey((ConcreteCustomClass) writeOffItemLM.findClassByCompoundName("WriteOffRate"),
                    writeOffItemLM.findLCPByCompoundOldName("writeOffRateId").getMapping(idWriteOffRateField));
            keys.add(writeOffRateKey);
            props.add(new ImportProperty(idWriteOffRateField, writeOffItemLM.findLCPByCompoundOldName("idWriteOffRate").getMapping(writeOffRateKey)));
            fields.add(idWriteOffRateField);
            for (int i = 0; i < rateWastesList.size(); i++)
                data.get(i).add(rateWastesList.get(i).idRateWaste);

            ImportField nameWriteOffRateField = new ImportField(writeOffItemLM.findLCPByCompoundOldName("nameWriteOffRate"));
            props.add(new ImportProperty(nameWriteOffRateField, writeOffItemLM.findLCPByCompoundOldName("nameWriteOffRate").getMapping(writeOffRateKey)));
            fields.add(nameWriteOffRateField);
            for (int i = 0; i < rateWastesList.size(); i++)
                data.get(i).add(rateWastesList.get(i).nameRateWaste);

            ImportField percentWriteOffRateField = new ImportField(writeOffItemLM.findLCPByCompoundOldName("percentWriteOffRate"));
            props.add(new ImportProperty(percentWriteOffRateField, writeOffItemLM.findLCPByCompoundOldName("percentWriteOffRate").getMapping(writeOffRateKey)));
            fields.add(percentWriteOffRateField);
            for (int i = 0; i < rateWastesList.size(); i++)
                data.get(i).add(rateWastesList.get(i).percentWriteOffRate);

            ImportField nameCountryField = new ImportField(getLCP("nameCountry"));
            ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) getClass("Country"),
                    getLCP("countryName").getMapping(nameCountryField));
            keys.add(countryKey);
            props.add(new ImportProperty(nameCountryField, writeOffItemLM.findLCPByCompoundOldName("countryWriteOffRate").getMapping(writeOffRateKey),
                    LM.object(getClass("Country")).getMapping(countryKey)));
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

        if (contractsList != null) {

            ServerLoggers.systemLogger.info("importContacts");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(contractsList.size());

            ImportField idUserContractSkuField = new ImportField(getLCP("idUserContractSku"));
            ImportKey<?> userContractSkuKey = new ImportKey((ConcreteCustomClass) getClass("UserContractSku"),
                    getLCP("userContractSkuId").getMapping(idUserContractSkuField));
            keys.add(userContractSkuKey);
            props.add(new ImportProperty(idUserContractSkuField, getLCP("idUserContractSku").getMapping(userContractSkuKey)));
            fields.add(idUserContractSkuField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).idUserContractSku);

            ImportField numberContractField = new ImportField(getLCP("numberContract"));
            props.add(new ImportProperty(numberContractField, getLCP("numberContract").getMapping(userContractSkuKey)));
            fields.add(numberContractField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).numberContract);

            ImportField dateFromContractField = new ImportField(getLCP("dateFromContract"));
            props.add(new ImportProperty(dateFromContractField, getLCP("dateFromContract").getMapping(userContractSkuKey)));
            fields.add(dateFromContractField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).dateFromContract);

            ImportField dateToContractField = new ImportField(getLCP("dateToContract"));
            props.add(new ImportProperty(dateToContractField, getLCP("dateToContract").getMapping(userContractSkuKey)));
            fields.add(dateToContractField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).dateToContract);

            ImportField idSupplierField = new ImportField(getLCP("idLegalEntity"));
            ImportKey<?> supplierKey = new ImportKey((ConcreteCustomClass) getClass("LegalEntity"),
                    getLCP("legalEntityId").getMapping(idSupplierField));
            supplierKey.skipKey = skipKeys;
            keys.add(supplierKey);
            props.add(new ImportProperty(idSupplierField, getLCP("supplierContractSku").getMapping(userContractSkuKey),
                    LM.object(getClass("LegalEntity")).getMapping(supplierKey)));
            fields.add(idSupplierField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).idSupplier);

            ImportField idCustomerField = new ImportField(getLCP("idLegalEntity"));
            ImportKey<?> customerKey = new ImportKey((ConcreteCustomClass) getClass("LegalEntity"),
                    getLCP("legalEntityId").getMapping(idCustomerField));
            customerKey.skipKey = skipKeys;
            keys.add(customerKey);
            props.add(new ImportProperty(idCustomerField, getLCP("customerContractSku").getMapping(userContractSkuKey),
                    LM.object(getClass("LegalEntity")).getMapping(customerKey)));
            fields.add(idCustomerField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).idCustomer);

            ImportField shortNameCurrencyField = new ImportField(getLCP("shortNameCurrency"));
            ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) getClass("Currency"),
                    getLCP("currencyShortName").getMapping(shortNameCurrencyField));
            keys.add(currencyKey);
            props.add(new ImportProperty(shortNameCurrencyField, getLCP("currencyContract").getMapping(userContractSkuKey),
                    LM.object(getClass("Currency")).getMapping(currencyKey)));
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

    private List<List<Object>> initData(int size) {
        List<List<Object>> data = new ArrayList<List<Object>>();
        for (int i = 0; i < size; i++) {
            data.add(new ArrayList<Object>());
        }
        return data;
    }
}