package lsfusion.erp.integration;

import lsfusion.base.ExceptionUtils;
import lsfusion.server.ServerLoggers;
import lsfusion.server.Settings;
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
import java.sql.SQLException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImportActionProperty {
    private ScriptingLogicsModule LM;
    private ImportData importData;
    private ExecutionContext<ClassPropertyInterface> context;

    // Опциональные модули
    ScriptingLogicsModule warePurchaseInvoiceLM;
    ScriptingLogicsModule storeLM;
    ScriptingLogicsModule writeOffItemLM;
    ScriptingLogicsModule pricingPurchaseLM;
    ScriptingLogicsModule purchaseInvoiceWholesalePriceLM;

    public ImportActionProperty(ScriptingLogicsModule LM, ImportData importData, ExecutionContext<ClassPropertyInterface> context) {
        this.LM = LM;
        this.importData = importData;
        this.context = context;
        this.warePurchaseInvoiceLM = (ScriptingLogicsModule) context.getBL().getModule("WarePurchaseInvoice");
        this.storeLM = (ScriptingLogicsModule) context.getBL().getModule("Store");
        this.writeOffItemLM = (ScriptingLogicsModule) context.getBL().getModule("WriteOffItem");
        this.pricingPurchaseLM = (ScriptingLogicsModule) context.getBL().getModule("PricingPurchase");
        this.purchaseInvoiceWholesalePriceLM = (ScriptingLogicsModule) context.getBL().getModule("PurchaseInvoiceWholesalePrice");
    }

    public void makeImport() throws SQLException {
        try {

            Object countryBelarus = LM.findLCPByCompoundOldName("countrySID").read(context.getSession(), new DataObject("112", StringClass.get(3)));
            LM.findLCPByCompoundOldName("defaultCountry").change(countryBelarus, context.getSession());
            context.getSession().apply(context);

            boolean disableVolatileStats = Settings.get().isDisableExplicitVolatileStats();
            
            importItemGroups(importData.getItemGroupsList(), disableVolatileStats);

            importParentGroups(importData.getParentGroupsList(), disableVolatileStats);

            importBanks(importData.getBanksList(), disableVolatileStats);

            importLegalEntities(importData.getLegalEntitiesList(), disableVolatileStats);

            importEmployees(importData.getEmployeesList(), disableVolatileStats);

            importWarehouseGroups(importData.getWarehouseGroupsList(), disableVolatileStats);

            importWarehouses(importData.getWarehousesList(), disableVolatileStats);

            importStores(importData.getStoresList(), importData.getSkipKeys(), disableVolatileStats);

            importDepartmentStores(importData.getDepartmentStoresList(), disableVolatileStats);

            importContracts(importData.getContractsList(), importData.getSkipKeys(), disableVolatileStats);

            importRateWastes(importData.getRateWastesList(), disableVolatileStats);

            importWares(importData.getWaresList(), disableVolatileStats);

            importUOMs(importData.getUOMsList(), disableVolatileStats);

            importItems(importData.getItemsList(), importData.getNumberOfItemsAtATime(), importData.getSkipKeys(), disableVolatileStats);

            importPriceListStores(importData.getPriceListStoresList(), importData.getNumberOfPriceListsAtATime(), importData.getSkipKeys(), disableVolatileStats);

            importPriceListSuppliers(importData.getPriceListSuppliersList(), importData.getNumberOfPriceListsAtATime(), importData.getSkipKeys(), disableVolatileStats);

            importUserInvoices(importData.getUserInvoicesList(), importData.getNumberOfUserInvoicesAtATime(), importData.getSkipKeys(), importData.getUserInvoiceCreateNewItems(), disableVolatileStats);

        } catch (Exception e) {
            throw ExceptionUtils.propagate(e, SQLException.class);
        }
    }

    private void importParentGroups(List<ItemGroup> parentGroupsList, boolean disableVolatileStats) throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        if (parentGroupsList != null) {

            ServerLoggers.systemLogger.info("importParentGroups");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(parentGroupsList.size());

            ImportField idItemGroupField = new ImportField(LM.findLCPByCompoundOldName("idItemGroup"));
            ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("ItemGroup"),
                    LM.findLCPByCompoundOldName("itemGroupId").getMapping(idItemGroupField));
            keys.add(itemGroupKey);
            fields.add(idItemGroupField);
            for (int i = 0; i < parentGroupsList.size(); i++)
                data.get(i).add(parentGroupsList.get(i).sid);

            ImportField idParentGroupField = new ImportField(LM.findLCPByCompoundOldName("idItemGroup"));
            ImportKey<?> parentGroupKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("ItemGroup"),
                    LM.findLCPByCompoundOldName("itemGroupId").getMapping(idParentGroupField));
            keys.add(parentGroupKey);
            props.add(new ImportProperty(idParentGroupField, LM.findLCPByCompoundOldName("parentItemGroup").getMapping(itemGroupKey),
                    LM.object(LM.findClassByCompoundName("ItemGroup")).getMapping(parentGroupKey)));
            fields.add(idParentGroupField);
            for (int i = 0; i < parentGroupsList.size(); i++)
                data.get(i).add(parentGroupsList.get(i).parent);

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.createSession();
            if(!disableVolatileStats)
                session.pushVolatileStats();
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            if(!disableVolatileStats)
                session.popVolatileStats();
            session.close();
        }
    }

    private void importItemGroups(List<ItemGroup> itemGroupsList, boolean disableVolatileStats) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (itemGroupsList != null) {

            ServerLoggers.systemLogger.info("importItemGroups");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(itemGroupsList.size());

            ImportField idItemGroupField = new ImportField(LM.findLCPByCompoundOldName("idItemGroup"));
            ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("ItemGroup"),
                    LM.findLCPByCompoundOldName("itemGroupId").getMapping(idItemGroupField));
            keys.add(itemGroupKey);
            props.add(new ImportProperty(idItemGroupField, LM.findLCPByCompoundOldName("idItemGroup").getMapping(itemGroupKey)));
            fields.add(idItemGroupField);
            for (int i = 0; i < itemGroupsList.size(); i++)
                data.get(i).add(itemGroupsList.get(i).sid);

            ImportField itemGroupNameField = new ImportField(LM.findLCPByCompoundOldName("nameItemGroup"));
            props.add(new ImportProperty(itemGroupNameField, LM.findLCPByCompoundOldName("nameItemGroup").getMapping(itemGroupKey)));
            fields.add(itemGroupNameField);
            for (int i = 0; i < itemGroupsList.size(); i++)
                data.get(i).add(itemGroupsList.get(i).name);

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.createSession();
            if(!disableVolatileStats)
                session.pushVolatileStats();
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            if(!disableVolatileStats)
                session.popVolatileStats();
            session.close();
        }
    }

    private void importWares(List<Ware> waresList, boolean disableVolatileStats) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

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
            if(!disableVolatileStats)
                session.pushVolatileStats();
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            if(!disableVolatileStats)
                session.popVolatileStats();
            session.close();
        }
    }

    private void importItems(List<Item> itemsList, Integer numberOfItemsAtATime, boolean skipKeys, boolean disableVolatileStats) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        try {
            Integer numAtATime = (numberOfItemsAtATime == null || numberOfItemsAtATime <= 0) ? 5000 : numberOfItemsAtATime;
            if (itemsList != null) {
                int amountOfImportIterations = (int) Math.ceil((double) itemsList.size() / numAtATime);
                Integer rest = itemsList.size();
                for (int i = 0; i < amountOfImportIterations; i++) {
                    importPackOfItems(warePurchaseInvoiceLM, writeOffItemLM, itemsList.subList(i * numAtATime, i * numAtATime + (rest > numAtATime ? numAtATime : rest)), skipKeys, disableVolatileStats);
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

    private void importUOMs(List<UOM> uomsList, boolean disableVolatileStats) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        if (uomsList == null)
            return;

        ServerLoggers.systemLogger.info("importUOMs");

        List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
        List<ImportField> fields = new ArrayList<ImportField>();
        List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

        List<List<Object>> data = initData(uomsList.size());

        ImportField idUOMField = new ImportField(LM.findLCPByCompoundOldName("idUOM"));
        ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("UOM"),
                LM.findLCPByCompoundOldName("UOMId").getMapping(idUOMField));
        keys.add(UOMKey);
        props.add(new ImportProperty(idUOMField, LM.findLCPByCompoundOldName("idUOM").getMapping(UOMKey)));
        fields.add(idUOMField);
        for (int i = 0; i < uomsList.size(); i++)
            data.get(i).add(uomsList.get(i).idUOM);

        ImportField nameUOMField = new ImportField(LM.findLCPByCompoundOldName("nameUOM"));
        props.add(new ImportProperty(nameUOMField, LM.findLCPByCompoundOldName("nameUOM").getMapping(UOMKey)));
        fields.add(nameUOMField);
        for (int i = 0; i < uomsList.size(); i++)
            data.get(i).add(uomsList.get(i).nameUOM);

        ImportField shortNameUOMField = new ImportField(LM.findLCPByCompoundOldName("shortNameUOM"));
        props.add(new ImportProperty(shortNameUOMField, LM.findLCPByCompoundOldName("shortNameUOM").getMapping(UOMKey)));
        fields.add(shortNameUOMField);
        for (int i = 0; i < uomsList.size(); i++)
            data.get(i).add(uomsList.get(i).shortNameUOM);

        ImportTable table = new ImportTable(fields, data);

        DataSession session = context.createSession();
        if(!disableVolatileStats)
            session.pushVolatileStats();
        IntegrationService service = new IntegrationService(session, table, keys, props);
        service.synchronize(true, false);
        session.apply(context);
        if(!disableVolatileStats)
            session.popVolatileStats();
        session.close();
    }


    private void importPackOfItems(ScriptingLogicsModule warePurchaseInvoiceLM, ScriptingLogicsModule writeOffItemLM, List<Item> itemsList, 
                                   boolean skipKeys, boolean disableVolatileStats)
            throws SQLException, IOException, xBaseJException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        if (itemsList.size() == 0) return;

        ServerLoggers.systemLogger.info("importItems " + itemsList.size());

        List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
        List<ImportField> fields = new ArrayList<ImportField>();
        List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

        List<List<Object>> data = initData(itemsList.size());

        ImportField idItemField = new ImportField(LM.findLCPByCompoundOldName("idItem"));
        ImportKey<?> itemKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Item"),
                LM.findLCPByCompoundOldName("itemId").getMapping(idItemField));
        keys.add(itemKey);
        props.add(new ImportProperty(idItemField, LM.findLCPByCompoundOldName("idItem").getMapping(itemKey)));
        fields.add(idItemField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).idItem);

        if (showItemField(itemsList, "captionItem")) {
            ImportField captionItemField = new ImportField(LM.findLCPByCompoundOldName("captionItem"));
            props.add(new ImportProperty(captionItemField, LM.findLCPByCompoundOldName("captionItem").getMapping(itemKey)));
            fields.add(captionItemField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).captionItem);
        }

        if (showItemField(itemsList, "idItemGroup")) {
            ImportField idItemGroupField = new ImportField(LM.findLCPByCompoundOldName("idItemGroup"));
            ImportKey<?> itemGroupKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("ItemGroup"),
                    LM.findLCPByCompoundOldName("itemGroupId").getMapping(idItemGroupField));
            keys.add(itemGroupKey);
            props.add(new ImportProperty(idItemGroupField, LM.findLCPByCompoundOldName("itemGroupItem").getMapping(itemKey),
                    LM.object(LM.findClassByCompoundName("ItemGroup")).getMapping(itemGroupKey)));
            fields.add(idItemGroupField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).idItemGroup);
        }

        if (showItemField(itemsList, "idBrand")) {
            ImportField idBrandField = new ImportField(LM.findLCPByCompoundOldName("idBrand"));
            ImportKey<?> brandKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Brand"),
                    LM.findLCPByCompoundOldName("brandId").getMapping(idBrandField));
            keys.add(brandKey);
            props.add(new ImportProperty(idBrandField, LM.findLCPByCompoundOldName("idBrand").getMapping(brandKey)));
            props.add(new ImportProperty(idBrandField, LM.findLCPByCompoundOldName("brandItem").getMapping(itemKey),
                    LM.object(LM.findClassByCompoundName("Brand")).getMapping(brandKey)));
            fields.add(idBrandField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).idBrand);

            ImportField nameBrandField = new ImportField(LM.findLCPByCompoundOldName("nameBrand"));
            props.add(new ImportProperty(nameBrandField, LM.findLCPByCompoundOldName("nameBrand").getMapping(brandKey)));
            fields.add(nameBrandField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).nameBrand);
        }

        ImportField nameCountryField = new ImportField(LM.findLCPByCompoundOldName("nameCountry"));
        ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Country"),
                LM.findLCPByCompoundOldName("countryName").getMapping(nameCountryField));
        keys.add(countryKey);
        props.add(new ImportProperty(nameCountryField, LM.findLCPByCompoundOldName("nameCountry").getMapping(countryKey)));
        props.add(new ImportProperty(nameCountryField, LM.findLCPByCompoundOldName("countryItem").getMapping(itemKey),
                LM.object(LM.findClassByCompoundName("Country")).getMapping(countryKey)));
        fields.add(nameCountryField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).nameCountry);

        ImportField extIdBarcodeField = new ImportField(LM.findLCPByCompoundOldName("extIdBarcode"));
        ImportKey<?> barcodeKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Barcode"),
                LM.findLCPByCompoundOldName(/*"barcodeIdDate"*/"extBarcodeId").getMapping(extIdBarcodeField));
        keys.add(barcodeKey);
        props.add(new ImportProperty(idItemField, LM.findLCPByCompoundOldName("skuBarcode").getMapping(barcodeKey),
                LM.object(LM.findClassByCompoundName("Item")).getMapping(itemKey)));
        props.add(new ImportProperty(extIdBarcodeField, LM.findLCPByCompoundOldName("extIdBarcode").getMapping(barcodeKey)));
        fields.add(extIdBarcodeField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).extIdBarcode);

        ImportField idBarcodeField = new ImportField(LM.findLCPByCompoundOldName("idBarcode"));
        props.add(new ImportProperty(idBarcodeField, LM.findLCPByCompoundOldName("idBarcode").getMapping(barcodeKey)));
        fields.add(idBarcodeField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).idBarcode);

        ImportField idUOMField = new ImportField(LM.findLCPByCompoundOldName("idUOM"));
        ImportKey<?> UOMKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("UOM"),
                LM.findLCPByCompoundOldName("UOMId").getMapping(idUOMField));
        UOMKey.skipKey = true;
        keys.add(UOMKey);
        props.add(new ImportProperty(idUOMField, LM.findLCPByCompoundOldName("UOMItem").getMapping(itemKey),
                LM.object(LM.findClassByCompoundName("UOM")).getMapping(UOMKey)));
        props.add(new ImportProperty(idUOMField, LM.findLCPByCompoundOldName("UOMBarcode").getMapping(barcodeKey),
                LM.object(LM.findClassByCompoundName("UOM")).getMapping(UOMKey)));
        fields.add(idUOMField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).idUOM);

        if (showItemField(itemsList, "isWeightItem")) {
            ImportField isWeightItemField = new ImportField(LM.findLCPByCompoundOldName("isWeightItem"));
            props.add(new ImportProperty(isWeightItemField, LM.findLCPByCompoundOldName("isWeightItem").getMapping(itemKey), true));
            fields.add(isWeightItemField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).isWeightItem);
        }

        if (showItemField(itemsList, "netWeightItem")) {
            ImportField netWeightItemField = new ImportField(LM.findLCPByCompoundOldName("netWeightItem"));
            props.add(new ImportProperty(netWeightItemField, LM.findLCPByCompoundOldName("netWeightItem").getMapping(itemKey)));
            fields.add(netWeightItemField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).netWeightItem);
        }

        if (showItemField(itemsList, "grossWeightItem")) {
            ImportField grossWeightItemField = new ImportField(LM.findLCPByCompoundOldName("grossWeightItem"));
            props.add(new ImportProperty(grossWeightItemField, LM.findLCPByCompoundOldName("grossWeightItem").getMapping(itemKey)));
            fields.add(grossWeightItemField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).grossWeightItem);
        }

        if (showItemField(itemsList, "compositionItem")) {
            ImportField compositionItemField = new ImportField(LM.findLCPByCompoundOldName("compositionItem"));
            props.add(new ImportProperty(compositionItemField, LM.findLCPByCompoundOldName("compositionItem").getMapping(itemKey)));
            fields.add(compositionItemField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).compositionItem);
        }

        ImportField dateField = new ImportField(DateClass.instance);
        props.add(new ImportProperty(dateField, LM.findLCPByCompoundOldName("dataDateBarcode").getMapping(barcodeKey)));
        fields.add(dateField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).date);

        DataObject defaultCountryObject = (DataObject) LM.findLCPByCompoundOldName("defaultCountry").readClasses(context.getSession());
        ImportField valueVATItemCountryDateField = new ImportField(LM.findLCPByCompoundOldName("valueVATItemCountryDate"));
        ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Range"),
                LM.findLCPByCompoundOldName("valueCurrentVATDefaultValue").getMapping(valueVATItemCountryDateField));
        VATKey.skipKey = skipKeys;
        keys.add(VATKey);
        props.add(new ImportProperty(valueVATItemCountryDateField, LM.findLCPByCompoundOldName("dataVATItemCountryDate").getMapping(itemKey, defaultCountryObject, dateField),
                LM.object(LM.findClassByCompoundName("Range")).getMapping(VATKey)));
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

            ImportField idRetailCalcPriceListTypeField = new ImportField(LM.findLCPByCompoundOldName("idCalcPriceListType"));
            ImportKey<?> retailCalcPriceListTypeKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("CalcPriceListType"),
                    LM.findLCPByCompoundOldName("calcPriceListTypeId").getMapping(idRetailCalcPriceListTypeField));
            keys.add(retailCalcPriceListTypeKey);
            props.add(new ImportProperty(idRetailCalcPriceListTypeField, LM.findLCPByCompoundOldName("idCalcPriceListType").getMapping(retailCalcPriceListTypeKey)));
            fields.add(idRetailCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add("retail");

            ImportField nameRetailCalcPriceListTypeField = new ImportField(LM.findLCPByCompoundOldName("namePriceListType"));
            props.add(new ImportProperty(nameRetailCalcPriceListTypeField, LM.findLCPByCompoundOldName("namePriceListType").getMapping(retailCalcPriceListTypeKey)));
            fields.add(nameRetailCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add("Розничная надбавка");

            ImportField retailMarkupCalcPriceListTypeField = new ImportField(LM.findLCPByCompoundOldName("dataMarkupCalcPriceListTypeSku"));
            props.add(new ImportProperty(retailMarkupCalcPriceListTypeField, LM.findLCPByCompoundOldName("dataMarkupCalcPriceListTypeSku").getMapping(retailCalcPriceListTypeKey, itemKey)));
            fields.add(retailMarkupCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).retailMarkup);

        }

        if (showItemField(itemsList, "baseMarkup")) {

            ImportField idBaseCalcPriceListTypeField = new ImportField(LM.findLCPByCompoundOldName("idCalcPriceListType"));
            ImportKey<?> baseCalcPriceListTypeKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("CalcPriceListType"),
                    LM.findLCPByCompoundOldName("calcPriceListTypeId").getMapping(idBaseCalcPriceListTypeField));
            keys.add(baseCalcPriceListTypeKey);
            props.add(new ImportProperty(idBaseCalcPriceListTypeField, LM.findLCPByCompoundOldName("idCalcPriceListType").getMapping(baseCalcPriceListTypeKey)));
            fields.add(idBaseCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add("wholesale");

            ImportField nameBaseCalcPriceListTypeField = new ImportField(LM.findLCPByCompoundOldName("namePriceListType"));
            props.add(new ImportProperty(nameBaseCalcPriceListTypeField, LM.findLCPByCompoundOldName("namePriceListType").getMapping(baseCalcPriceListTypeKey)));
            fields.add(nameBaseCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add("Оптовая надбавка");

            ImportField baseMarkupCalcPriceListTypeField = new ImportField(LM.findLCPByCompoundOldName("dataMarkupCalcPriceListTypeSku"));
            props.add(new ImportProperty(baseMarkupCalcPriceListTypeField, LM.findLCPByCompoundOldName("dataMarkupCalcPriceListTypeSku").getMapping(baseCalcPriceListTypeKey, itemKey)));
            fields.add(baseMarkupCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).baseMarkup);
        }

        ImportField extIdBarcodePackField = new ImportField(LM.findLCPByCompoundOldName("extIdBarcode"));
        ImportKey<?> barcodePackKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Barcode"),
                LM.findLCPByCompoundOldName(/*"barcodeIdDate"*/"extBarcodeId").getMapping(extIdBarcodePackField));
        keys.add(barcodePackKey);
        props.add(new ImportProperty(dateField, LM.findLCPByCompoundOldName("dataDateBarcode").getMapping(barcodePackKey)));
        props.add(new ImportProperty(idItemField, LM.findLCPByCompoundOldName("skuBarcode").getMapping(barcodePackKey),
                LM.object(LM.findClassByCompoundName("Item")).getMapping(itemKey)));
        props.add(new ImportProperty(extIdBarcodePackField, LM.findLCPByCompoundOldName("extIdBarcode").getMapping(barcodePackKey)));
        props.add(new ImportProperty(extIdBarcodePackField, LM.findLCPByCompoundOldName("Purchase.packBarcodeSku").getMapping(itemKey),
                LM.object(LM.findClassByCompoundName("Barcode")).getMapping(barcodePackKey)));
        fields.add(extIdBarcodePackField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(valueWithPrefix(itemsList.get(i).idBarcodePack, "P", null));

        ImportField idBarcodePackField = new ImportField(LM.findLCPByCompoundOldName("idBarcode"));
        props.add(new ImportProperty(idBarcodePackField, LM.findLCPByCompoundOldName("idBarcode").getMapping(barcodePackKey)));
        props.add(new ImportProperty(extIdBarcodePackField, LM.findLCPByCompoundOldName("Sale.packBarcodeSku").getMapping(itemKey),
                LM.object(LM.findClassByCompoundName("Barcode")).getMapping(barcodePackKey)));
        fields.add(idBarcodePackField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(null);

        if (showItemField(itemsList, "amountPack")) {
            ImportField amountBarcodePackField = new ImportField(LM.findLCPByCompoundOldName("amountBarcode"));
            props.add(new ImportProperty(amountBarcodePackField, LM.findLCPByCompoundOldName("amountBarcode").getMapping(barcodePackKey)));
            fields.add(amountBarcodePackField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).amountPack);
        }

        if (showItemField(itemsList, "idUOMPack")) {
            ImportField idUOMPackField = new ImportField(LM.findLCPByCompoundOldName("idUOM"));
            props.add(new ImportProperty(idUOMPackField, LM.findLCPByCompoundOldName("UOMBarcode").getMapping(barcodePackKey),
                    LM.object(LM.findClassByCompoundName("UOM")).getMapping(UOMKey)));
            fields.add(idUOMPackField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).idUOMPack);
        }

        if (showItemField(itemsList, "idManufacturer")) {
            ImportField idManufacturerField = new ImportField(LM.findLCPByCompoundOldName("idManufacturer"));
            ImportKey<?> manufacturerKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Manufacturer"),
                    LM.findLCPByCompoundOldName("manufacturerId").getMapping(idManufacturerField));
            keys.add(manufacturerKey);
            props.add(new ImportProperty(idManufacturerField, LM.findLCPByCompoundOldName("idManufacturer").getMapping(manufacturerKey)));
            props.add(new ImportProperty(idManufacturerField, LM.findLCPByCompoundOldName("manufacturerItem").getMapping(itemKey),
                    LM.object(LM.findClassByCompoundName("Manufacturer")).getMapping(manufacturerKey)));
            fields.add(idManufacturerField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).idManufacturer);

            ImportField nameManufacturerField = new ImportField(LM.findLCPByCompoundOldName("nameManufacturer"));
            props.add(new ImportProperty(nameManufacturerField, LM.findLCPByCompoundOldName("nameManufacturer").getMapping(manufacturerKey)));
            fields.add(nameManufacturerField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).nameManufacturer);
        }

        if (showItemField(itemsList, "codeCustomsGroup")) {
            ImportField codeCustomsGroupField = new ImportField(LM.findLCPByCompoundOldName("codeCustomsGroup"));
            ImportKey<?> customsGroupKey = new ImportKey((CustomClass) LM.findClassByCompoundName("CustomsGroup"),
                    LM.findLCPByCompoundOldName("customsGroupCode").getMapping(codeCustomsGroupField));
            keys.add(customsGroupKey);
            props.add(new ImportProperty(codeCustomsGroupField, LM.findLCPByCompoundOldName("codeCustomsGroup").getMapping(customsGroupKey)));
            props.add(new ImportProperty(codeCustomsGroupField, LM.findLCPByCompoundOldName("customsGroupCountryItem").getMapping(countryKey, itemKey),
                    LM.object(LM.findClassByCompoundName("CustomsGroup")).getMapping(customsGroupKey)));
            fields.add(codeCustomsGroupField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).codeCustomsGroup);

            ImportField nameCustomsZoneField = new ImportField(LM.findLCPByCompoundOldName("nameCustomsZone"));
            ImportKey<?> customsZoneKey = new ImportKey((CustomClass) LM.findClassByCompoundName("CustomsZone"),
                    LM.findLCPByCompoundOldName("customsZoneName").getMapping(nameCustomsZoneField));
            keys.add(customsZoneKey);
            props.add(new ImportProperty(nameCustomsZoneField, LM.findLCPByCompoundOldName("nameCustomsZone").getMapping(customsZoneKey)));
            props.add(new ImportProperty(nameCustomsZoneField, LM.findLCPByCompoundOldName("customsZoneCustomsGroup").getMapping(customsGroupKey),
                    LM.object(LM.findClassByCompoundName("CustomsZone")).getMapping(customsZoneKey)));
            fields.add(nameCustomsZoneField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).nameCustomsZone);
        }

        ImportTable table = new ImportTable(fields, data);

        DataSession session = context.createSession();
        if(!disableVolatileStats)
            session.pushVolatileStats();
        IntegrationService service = new IntegrationService(session, table, keys, props);
        service.synchronize(true, false);
        session.apply(context);
        if(!disableVolatileStats)
            session.popVolatileStats();
        session.close();
    }

    private void importUserInvoices(List<UserInvoiceDetail> userInvoiceDetailsList, Integer numberAtATime, boolean skipKeys,
                                    boolean userInvoiceCreateNewItems, boolean disableVolatileStats)
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

                ImportField idUserInvoiceField = new ImportField(LM.findLCPByCompoundOldName("Purchase.idUserInvoice"));
                ImportKey<?> userInvoiceKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Purchase.UserInvoice"),
                        LM.findLCPByCompoundOldName("Purchase.userInvoiceId").getMapping(idUserInvoiceField));
                keys.add(userInvoiceKey);
                props.add(new ImportProperty(idUserInvoiceField, LM.findLCPByCompoundOldName("Purchase.idUserInvoice").getMapping(userInvoiceKey)));
                fields.add(idUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idUserInvoice);

                ImportField idUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundOldName("Purchase.idUserInvoiceDetail"));
                ImportKey<?> userInvoiceDetailKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Purchase.UserInvoiceDetail"),
                        LM.findLCPByCompoundOldName("Purchase.userInvoiceDetailId").getMapping(idUserInvoiceDetailField));
                keys.add(userInvoiceDetailKey);
                props.add(new ImportProperty(idUserInvoiceDetailField, LM.findLCPByCompoundOldName("Purchase.idUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                props.add(new ImportProperty(idUserInvoiceField, LM.findLCPByCompoundOldName("Purchase.userInvoiceUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(LM.findClassByCompoundName("Purchase.UserInvoice")).getMapping(userInvoiceKey)));
                fields.add(idUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idUserInvoiceDetail);


                ImportField idCustomerStockField = new ImportField(LM.findLCPByCompoundOldName("idStock"));
                ImportKey<?> customerStockKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Stock"),
                        LM.findLCPByCompoundOldName("stockId").getMapping(idCustomerStockField));
                customerStockKey.skipKey = skipKeys;
                keys.add(customerStockKey);
                props.add(new ImportProperty(idCustomerStockField, LM.findLCPByCompoundOldName("idStock").getMapping(customerStockKey)));
                props.add(new ImportProperty(idCustomerStockField, LM.findLCPByCompoundOldName("Purchase.customerUserInvoice").getMapping(userInvoiceKey),
                        LM.findLCPByCompoundOldName("legalEntityStock").getMapping(customerStockKey)));
                props.add(new ImportProperty(idCustomerStockField, LM.findLCPByCompoundOldName("Purchase.customerStockUserInvoice").getMapping(userInvoiceKey),
                        LM.object(LM.findClassByCompoundName("Stock")).getMapping(customerStockKey)));
                fields.add(idCustomerStockField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idCustomerStock);


                ImportField idSupplierField = new ImportField(LM.findLCPByCompoundOldName("idLegalEntity"));
                ImportKey<?> supplierKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("LegalEntity"),
                        LM.findLCPByCompoundOldName("legalEntityId").getMapping(idSupplierField));
                supplierKey.skipKey = skipKeys;
                keys.add(supplierKey);
                props.add(new ImportProperty(idSupplierField, LM.findLCPByCompoundOldName("Purchase.supplierUserInvoice").getMapping(userInvoiceKey),
                        LM.object(LM.findClassByCompoundName("LegalEntity")).getMapping(supplierKey)));
                fields.add(idSupplierField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idSupplier);


                ImportField idSupplierStockField = new ImportField(LM.findLCPByCompoundOldName("idStock"));
                ImportKey<?> supplierStockKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Stock"),
                        LM.findLCPByCompoundOldName("stockId").getMapping(idSupplierStockField));
                supplierStockKey.skipKey = skipKeys;
                keys.add(supplierStockKey);
                props.add(new ImportProperty(idSupplierStockField, LM.findLCPByCompoundOldName("idStock").getMapping(supplierStockKey)));
                props.add(new ImportProperty(idSupplierStockField, LM.findLCPByCompoundOldName("Purchase.supplierStockUserInvoice").getMapping(userInvoiceKey),
                        LM.object(LM.findClassByCompoundName("Stock")).getMapping(supplierStockKey)));
                fields.add(idSupplierStockField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idSupplierStock);


                ImportField numberUserInvoiceField = new ImportField(LM.findLCPByCompoundOldName("Purchase.numberUserInvoice"));
                props.add(new ImportProperty(numberUserInvoiceField, LM.findLCPByCompoundOldName("Purchase.numberUserInvoice").getMapping(userInvoiceKey)));
                fields.add(numberUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).number);

                if (showField(dataUserInvoiceDetail, "series")) {
                    ImportField seriesUserInvoiceField = new ImportField(LM.findLCPByCompoundOldName("Purchase.seriesUserInvoice"));
                    props.add(new ImportProperty(seriesUserInvoiceField, LM.findLCPByCompoundOldName("Purchase.seriesUserInvoice").getMapping(userInvoiceKey)));
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

                ImportField createShipmentUserInvoiceField = new ImportField(LM.findLCPByCompoundOldName("Purchase.createShipmentUserInvoice"));
                props.add(new ImportProperty(createShipmentUserInvoiceField, LM.findLCPByCompoundOldName("Purchase.createShipmentUserInvoice").getMapping(userInvoiceKey), true));
                fields.add(createShipmentUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).createShipment);

                if (showField(dataUserInvoiceDetail, "manufacturingPrice")) {
                    ImportField showManufacturingPriceUserInvoiceField = new ImportField(LM.findLCPByCompoundOldName("Purchase.showManufacturingPriceUserInvoice"));
                    props.add(new ImportProperty(showManufacturingPriceUserInvoiceField, LM.findLCPByCompoundOldName("Purchase.showManufacturingPriceUserInvoice").getMapping(userInvoiceKey)));
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

                ImportField dateUserInvoiceField = new ImportField(LM.findLCPByCompoundOldName("Purchase.dateUserInvoice"));
                props.add(new ImportProperty(dateUserInvoiceField, LM.findLCPByCompoundOldName("Purchase.dateUserInvoice").getMapping(userInvoiceKey)));
                fields.add(dateUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).date);


                ImportField timeUserInvoiceField = new ImportField(TimeClass.instance);
                props.add(new ImportProperty(timeUserInvoiceField, LM.findLCPByCompoundOldName("Purchase.timeUserInvoice").getMapping(userInvoiceKey)));
                fields.add(timeUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(new Time(12, 0, 0));


                ImportField idItemField = new ImportField(LM.findLCPByCompoundOldName("idItem"));
                ImportKey<?> itemKey = new ImportKey((CustomClass) LM.findClassByCompoundName("Item"),
                        LM.findLCPByCompoundOldName("itemId").getMapping(idItemField));
                itemKey.skipKey = skipKeys && !userInvoiceCreateNewItems;
                keys.add(itemKey);
                if (userInvoiceCreateNewItems)
                    props.add(new ImportProperty(idItemField, LM.findLCPByCompoundOldName("idItem").getMapping(itemKey)));
                props.add(new ImportProperty(idItemField, LM.findLCPByCompoundOldName("Purchase.skuUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(LM.findClassByCompoundName("Sku")).getMapping(itemKey)));
                fields.add(idItemField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idItem);


                ImportField quantityUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundOldName("Purchase.quantityUserInvoiceDetail"));
                props.add(new ImportProperty(quantityUserInvoiceDetailField, LM.findLCPByCompoundOldName("Purchase.quantityUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(quantityUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).quantity);


                ImportField priceUserInvoiceDetail = new ImportField(LM.findLCPByCompoundOldName("Purchase.priceUserInvoiceDetail"));
                props.add(new ImportProperty(priceUserInvoiceDetail, LM.findLCPByCompoundOldName("Purchase.priceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(priceUserInvoiceDetail);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).price);

                if (showField(dataUserInvoiceDetail, "shipmentPrice")) {
                    ImportField shipmentPriceInvoiceDetail = new ImportField(LM.findLCPByCompoundOldName("Purchase.shipmentPriceUserInvoiceDetail"));
                    props.add(new ImportProperty(shipmentPriceInvoiceDetail, LM.findLCPByCompoundOldName("Purchase.shipmentPriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(shipmentPriceInvoiceDetail);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).shipmentPrice);
                }

                if (showField(dataUserInvoiceDetail, "shipmentSum")) {
                    ImportField shipmentSumInvoiceDetail = new ImportField(LM.findLCPByCompoundOldName("Purchase.shipmentSumUserInvoiceDetail"));
                    props.add(new ImportProperty(shipmentSumInvoiceDetail, LM.findLCPByCompoundOldName("Purchase.shipmentSumUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(shipmentSumInvoiceDetail);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).shipmentSum);
                }

                ImportField expiryDateUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundOldName("Purchase.expiryDateUserInvoiceDetail"));
                props.add(new ImportProperty(expiryDateUserInvoiceDetailField, LM.findLCPByCompoundOldName("Purchase.expiryDateUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(expiryDateUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).expiryDate);


                ImportField dataRateExchangeUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundOldName("Purchase.dataRateExchangeUserInvoiceDetail"));
                props.add(new ImportProperty(dataRateExchangeUserInvoiceDetailField, LM.findLCPByCompoundOldName("Purchase.dataRateExchangeUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(dataRateExchangeUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).rateExchange);


                ImportField homePriceUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundOldName("Purchase.homePriceUserInvoiceDetail"));
                props.add(new ImportProperty(homePriceUserInvoiceDetailField, LM.findLCPByCompoundOldName("Purchase.homePriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(homePriceUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).homePrice);


                if (showField(dataUserInvoiceDetail, "priceDuty")) {
                    ImportField priceDutyUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundOldName("Purchase.dutyPriceUserInvoiceDetail"));
                    props.add(new ImportProperty(priceDutyUserInvoiceDetailField, LM.findLCPByCompoundOldName("Purchase.dutyPriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(priceDutyUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).priceDuty);
                }

                if (showField(dataUserInvoiceDetail, "priceCompliance")) {
                    ImportField priceComplianceUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundOldName("Purchase.compliancePriceUserInvoiceDetail"));
                    props.add(new ImportProperty(priceComplianceUserInvoiceDetailField, LM.findLCPByCompoundOldName("Purchase.compliancePriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(priceComplianceUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).priceCompliance);

                    ImportField showComplianceUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundOldName("showComplianceUserInvoice"));
                    props.add(new ImportProperty(showComplianceUserInvoiceDetailField, LM.findLCPByCompoundOldName("showComplianceUserInvoice").getMapping(userInvoiceKey)));
                    fields.add(showComplianceUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                }

                if (showField(dataUserInvoiceDetail, "priceRegistration")) {
                    ImportField priceRegistrationUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundOldName("Purchase.registrationPriceUserInvoiceDetail"));
                    props.add(new ImportProperty(priceRegistrationUserInvoiceDetailField, LM.findLCPByCompoundOldName("Purchase.registrationPriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(priceRegistrationUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).priceRegistration);
                }

                if (showField(dataUserInvoiceDetail, "chargePrice") || showField(dataUserInvoiceDetail, "chargeSum")) {

                    ImportField chargePriceUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundOldName("Purchase.chargePriceUserInvoiceDetail"));
                    props.add(new ImportProperty(chargePriceUserInvoiceDetailField, LM.findLCPByCompoundOldName("Purchase.chargePriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(chargePriceUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).chargePrice);

                    ImportField chargeSumUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundOldName("Purchase.chargeSumUserInvoiceDetail"));
                    props.add(new ImportProperty(chargeSumUserInvoiceDetailField, LM.findLCPByCompoundOldName("Purchase.chargeSumUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                    fields.add(chargeSumUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).chargeSum);

                    ImportField showChargePriceUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundOldName("showChargePriceUserInvoice"));
                    props.add(new ImportProperty(showChargePriceUserInvoiceDetailField, LM.findLCPByCompoundOldName("showChargePriceUserInvoice").getMapping(userInvoiceKey)));
                    fields.add(showChargePriceUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                }


                if (showField(dataUserInvoiceDetail, "idBin")) {
                    ImportField binUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundOldName("idBin"));
                    ImportKey<?> binKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Bin"),
                            LM.findLCPByCompoundOldName("binId").getMapping(binUserInvoiceDetailField));
                    keys.add(binKey);
                    props.add(new ImportProperty(binUserInvoiceDetailField, LM.findLCPByCompoundOldName("idBin").getMapping(binKey)));
                    props.add(new ImportProperty(binUserInvoiceDetailField, LM.findLCPByCompoundOldName("nameBin").getMapping(binKey)));
                    props.add(new ImportProperty(binUserInvoiceDetailField, LM.findLCPByCompoundOldName("binUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                            LM.object(LM.findClassByCompoundName("Bin")).getMapping(binKey)));
                    fields.add(binUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).idBin);

                    ImportField showBinUserInvoiceField = new ImportField(LM.findLCPByCompoundOldName("showBinUserInvoice"));
                    props.add(new ImportProperty(showBinUserInvoiceField, LM.findLCPByCompoundOldName("showBinUserInvoice").getMapping(userInvoiceKey)));
                    fields.add(showBinUserInvoiceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                }

                ImportField manufacturingPriceUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundOldName("Purchase.manufacturingPriceUserInvoiceDetail"));
                props.add(new ImportProperty(manufacturingPriceUserInvoiceDetailField, LM.findLCPByCompoundOldName("Purchase.manufacturingPriceUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(manufacturingPriceUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).manufacturingPrice);

                ImportField manufacturingMarkupUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundOldName("Purchase.manufacturingMarkupUserInvoiceDetail"));
                props.add(new ImportProperty(manufacturingMarkupUserInvoiceDetailField, LM.findLCPByCompoundOldName("Purchase.manufacturingMarkupUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(manufacturingMarkupUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).manufacturingMarkup);

                ImportField certificateTextUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundOldName("Purchase.certificateTextUserInvoiceDetail"));
                props.add(new ImportProperty(certificateTextUserInvoiceDetailField, LM.findLCPByCompoundOldName("Purchase.certificateTextUserInvoiceDetail").getMapping(userInvoiceDetailKey)));
                fields.add(certificateTextUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).certificateText);

                if (warePurchaseInvoiceLM != null) {
                    ImportField skipCreateWareUserInvoiceDetailField = new ImportField(warePurchaseInvoiceLM.findLCPByCompoundOldName("skipCreateWareUserInvoiceDetail"));
                    props.add(new ImportProperty(skipCreateWareUserInvoiceDetailField, warePurchaseInvoiceLM.findLCPByCompoundOldName("skipCreateWareUserInvoiceDetail").getMapping(userInvoiceDetailKey), true));
                    fields.add(skipCreateWareUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                }


                if (showField(dataUserInvoiceDetail, "idContract")) {
                    ImportField userContractSkuField = new ImportField(LM.findLCPByCompoundOldName("idUserContractSku"));
                    ImportKey<?> userContractSkuKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("UserContractSku"),
                            LM.findLCPByCompoundOldName("userContractSkuId").getMapping(userContractSkuField));
                    userContractSkuKey.skipKey = skipKeys;
                    keys.add(userContractSkuKey);
                    props.add(new ImportProperty(userContractSkuField, LM.findLCPByCompoundOldName("Purchase.contractSkuInvoice").getMapping(userInvoiceKey),
                            LM.object(LM.findClassByCompoundName("Contract")).getMapping(userContractSkuKey)));
                    fields.add(userContractSkuField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).idContract);
                }

                if (showField(dataUserInvoiceDetail, "numberDeclaration")) {
                    ImportField numberDeclarationField = new ImportField(LM.findLCPByCompoundOldName("numberDeclaration"));
                    ImportKey<?> declarationKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Declaration"),
                            LM.findLCPByCompoundOldName("declarationId").getMapping(numberDeclarationField));
                    keys.add(declarationKey);
                    props.add(new ImportProperty(numberDeclarationField, LM.findLCPByCompoundOldName("numberDeclaration").getMapping(declarationKey)));
                    props.add(new ImportProperty(numberDeclarationField, LM.findLCPByCompoundOldName("idDeclaration").getMapping(declarationKey)));
                    props.add(new ImportProperty(numberDeclarationField, LM.findLCPByCompoundOldName("Purchase.declarationUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                            LM.object(LM.findClassByCompoundName("Declaration")).getMapping(declarationKey)));
                    fields.add(numberDeclarationField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).numberDeclaration);

                    ImportField dateDeclarationField = new ImportField(LM.findLCPByCompoundOldName("dateDeclaration"));
                    props.add(new ImportProperty(dateDeclarationField, LM.findLCPByCompoundOldName("dateDeclaration").getMapping(declarationKey)));
                    fields.add(dateDeclarationField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).dateDeclaration);
                }

                if (showField(dataUserInvoiceDetail, "numberCompliance")) {
                    ImportField numberComplianceField = new ImportField(LM.findLCPByCompoundOldName("numberCompliance"));
                    ImportKey<?> complianceKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Compliance"),
                            LM.findLCPByCompoundOldName("complianceId").getMapping(numberComplianceField));
                    keys.add(complianceKey);
                    props.add(new ImportProperty(numberComplianceField, LM.findLCPByCompoundOldName("numberCompliance").getMapping(complianceKey)));
                    props.add(new ImportProperty(numberComplianceField, LM.findLCPByCompoundOldName("idCompliance").getMapping(complianceKey)));
                    props.add(new ImportProperty(numberComplianceField, LM.findLCPByCompoundOldName("Purchase.complianceUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                            LM.object(LM.findClassByCompoundName("Compliance")).getMapping(complianceKey)));
                    fields.add(numberComplianceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).numberCompliance);

                    ImportField fromDateComplianceField = new ImportField(LM.findLCPByCompoundOldName("fromDateCompliance"));
                    props.add(new ImportProperty(fromDateComplianceField, LM.findLCPByCompoundOldName("dateCompliance").getMapping(complianceKey)));
                    props.add(new ImportProperty(fromDateComplianceField, LM.findLCPByCompoundOldName("fromDateCompliance").getMapping(complianceKey)));
                    fields.add(fromDateComplianceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).fromDateCompliance);

                    ImportField toDateComplianceField = new ImportField(LM.findLCPByCompoundOldName("toDateCompliance"));
                    props.add(new ImportProperty(toDateComplianceField, LM.findLCPByCompoundOldName("toDateCompliance").getMapping(complianceKey)));
                    fields.add(toDateComplianceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).toDateCompliance);
                }

                if (showField(dataUserInvoiceDetail, "isHomeCurrency")) {
                    ImportField isHomeCurrencyUserInvoiceField = new ImportField(LM.findLCPByCompoundOldName("Purchase.isHomeCurrencyUserInvoice"));
                    props.add(new ImportProperty(isHomeCurrencyUserInvoiceField, LM.findLCPByCompoundOldName("Purchase.isHomeCurrencyUserInvoice").getMapping(userInvoiceKey), true));
                    fields.add(isHomeCurrencyUserInvoiceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).isHomeCurrency);
                }

                if (showField(dataUserInvoiceDetail, "codeCustomsGroup") || showField(dataUserInvoiceDetail, "priceDuty") || showField(dataUserInvoiceDetail, "priceRegistration")) {
                    ImportField showDeclarationUserInvoiceField = new ImportField(LM.findLCPByCompoundOldName("showDeclarationUserInvoice"));
                    props.add(new ImportProperty(showDeclarationUserInvoiceField, LM.findLCPByCompoundOldName("showDeclarationUserInvoice").getMapping(userInvoiceKey), true));
                    fields.add(showDeclarationUserInvoiceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                }

                ImportField shortNameCurrencyField = new ImportField(LM.findLCPByCompoundOldName("shortNameCurrency"));
                ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Currency"),
                        LM.findLCPByCompoundOldName("currencyShortName").getMapping(shortNameCurrencyField));
                keys.add(currencyKey);
                props.add(new ImportProperty(shortNameCurrencyField, LM.findLCPByCompoundOldName("Purchase.currencyUserInvoice").getMapping(userInvoiceKey),
                        LM.object(LM.findClassByCompoundName("Currency")).getMapping(currencyKey)));
                fields.add(shortNameCurrencyField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).shortNameCurrency);

                ObjectValue defaultCountryObject = LM.findLCPByCompoundOldName("defaultCountry").readClasses(context.getSession());
                ImportField codeCustomsGroupField = new ImportField(LM.findLCPByCompoundOldName("codeCustomsGroup"));
                ImportKey<?> customsGroupKey = new ImportKey((CustomClass) LM.findClassByCompoundName("CustomsGroup"),
                        LM.findLCPByCompoundOldName("customsGroupCode").getMapping(codeCustomsGroupField));
                keys.add(customsGroupKey);
                props.add(new ImportProperty(codeCustomsGroupField, LM.findLCPByCompoundOldName("customsGroupUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(LM.findClassByCompoundName("CustomsGroup")).getMapping(customsGroupKey)));
                props.add(new ImportProperty(codeCustomsGroupField, LM.findLCPByCompoundOldName("customsGroupCountryItem").getMapping(defaultCountryObject, itemKey),
                        LM.object(LM.findClassByCompoundName("CustomsGroup")).getMapping(customsGroupKey)));
                fields.add(codeCustomsGroupField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).codeCustomsGroup);

                ImportField valueVATUserInvoiceDetailField = new ImportField(LM.findLCPByCompoundOldName("Purchase.valueVATUserInvoiceDetail"));
                ImportKey<?> VATKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Range"),
                        LM.findLCPByCompoundOldName("valueCurrentVATDefaultValue").getMapping(valueVATUserInvoiceDetailField));
                VATKey.skipKey = skipKeys;
                keys.add(VATKey);
                props.add(new ImportProperty(valueVATUserInvoiceDetailField, LM.findLCPByCompoundOldName("Purchase.VATUserInvoiceDetail").getMapping(userInvoiceDetailKey),
                        LM.object(LM.findClassByCompoundName("Range")).getMapping(VATKey)));
                fields.add(valueVATUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).retailVAT);

                ImportTable table = new ImportTable(fields, data);

                DataSession session = context.createSession();
                if(!disableVolatileStats)
                    session.pushVolatileStats();
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context);
                if(!disableVolatileStats)
                    session.popVolatileStats();
                session.close();
            }
        }
    }

    private void importPriceListStores(List<PriceListStore> priceListStoresList, Integer numberAtATime, boolean skipKeys, boolean disableVolatileStats) 
            throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (priceListStoresList != null) {

            if (numberAtATime == null)
                numberAtATime = priceListStoresList.size();

            for (int start = 0; true; start += numberAtATime) {

                int finish = (start + numberAtATime) < priceListStoresList.size() ? (start + numberAtATime) : priceListStoresList.size();
                List<PriceListStore> dataPriceListStores = start < finish ? priceListStoresList.subList(start, finish) : new ArrayList<PriceListStore>();
                if (dataPriceListStores.isEmpty())
                    return;

                ServerLoggers.systemLogger.info("importPriceListStores " + dataPriceListStores.size());

                DataSession session = context.createSession();
                if(!disableVolatileStats)
                    session.pushVolatileStats();

                ObjectValue dataPriceListTypeObject = LM.findLCPByCompoundOldName("dataPriceListTypeId").readClasses(session, new DataObject("Coordinated", StringClass.get(100)));
                if (dataPriceListTypeObject instanceof NullValue) {
                    dataPriceListTypeObject = session.addObject((ConcreteCustomClass) LM.findClassByCompoundName("DataPriceListType"));
                    Object defaultCurrency = LM.findLCPByCompoundOldName("currencyShortName").read(session, new DataObject("BLR", StringClass.get(3)));
                    LM.findLCPByCompoundOldName("namePriceListType").change("Поставщика (согласованная)", session, (DataObject) dataPriceListTypeObject);
                    LM.findLCPByCompoundOldName("currencyDataPriceListType").change(defaultCurrency, session, (DataObject) dataPriceListTypeObject);
                    LM.findLCPByCompoundOldName("idDataPriceListType").change("Coordinated", session, (DataObject) dataPriceListTypeObject);
                }

                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                List<List<Object>> data = initData(priceListStoresList.size());

                ImportField idItemField = new ImportField(LM.findLCPByCompoundOldName("idItem"));
                ImportField idUserPriceListField = new ImportField(LM.findLCPByCompoundOldName("idUserPriceList"));
                ImportKey<?> itemKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Item"),
                        LM.findLCPByCompoundOldName("itemId").getMapping(idItemField));
                keys.add(itemKey);
                ImportKey<?> userPriceListDetailKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("UserPriceListDetail"),
                        LM.findLCPByCompoundOldName("userPriceListDetailIdSkuIdUserPriceList").getMapping(idItemField, idUserPriceListField));
                keys.add(userPriceListDetailKey);
                ImportKey<?> userPriceListKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("UserPriceList"),
                        LM.findLCPByCompoundOldName("userPriceListId").getMapping(idUserPriceListField));
                keys.add(userPriceListKey);
                props.add(new ImportProperty(idItemField, LM.findLCPByCompoundOldName("skuUserPriceListDetail").getMapping(userPriceListDetailKey),
                        LM.object(LM.findClassByCompoundName("Item")).getMapping(itemKey)));
                props.add(new ImportProperty(idUserPriceListField, LM.findLCPByCompoundOldName("idUserPriceList").getMapping(userPriceListKey)));
                props.add(new ImportProperty(idUserPriceListField, LM.findLCPByCompoundOldName("userPriceListUserPriceListDetail").getMapping(userPriceListDetailKey),
                        LM.object(LM.findClassByCompoundName("UserPriceList")).getMapping(userPriceListKey)));
                fields.add(idItemField);
                fields.add(idUserPriceListField);
                for (int i = 0; i < priceListStoresList.size(); i++) {
                    data.get(i).add(priceListStoresList.get(i).idItem);
                    data.get(i).add(priceListStoresList.get(i).idUserPriceList);
                }

                ImportField idLegalEntityField = new ImportField(LM.findLCPByCompoundOldName("idLegalEntity"));
                ImportKey<?> legalEntityKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("LegalEntity"),
                        LM.findLCPByCompoundOldName("legalEntityId").getMapping(idLegalEntityField));
                legalEntityKey.skipKey = skipKeys;
                keys.add(legalEntityKey);
                props.add(new ImportProperty(idLegalEntityField, LM.findLCPByCompoundOldName("companyUserPriceList").getMapping(userPriceListKey),
                        LM.object(LM.findClassByCompoundName("LegalEntity")).getMapping(legalEntityKey)));
                fields.add(idLegalEntityField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(priceListStoresList.get(i).idSupplier);

                ImportField idDepartmentStoreField = new ImportField(LM.findLCPByCompoundOldName("idDepartmentStore"));
                ImportKey<?> departmentStoreKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("DepartmentStore"),
                        LM.findLCPByCompoundOldName("departmentStoreId").getMapping(idDepartmentStoreField));
                keys.add(departmentStoreKey);
                fields.add(idDepartmentStoreField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(priceListStoresList.get(i).idDepartmentStore);

                ImportField shortNameCurrencyField = new ImportField(LM.findLCPByCompoundOldName("shortNameCurrency"));
                ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Currency"),
                        LM.findLCPByCompoundOldName("currencyShortName").getMapping(shortNameCurrencyField));
                keys.add(currencyKey);
                props.add(new ImportProperty(shortNameCurrencyField, LM.findLCPByCompoundOldName("currencyUserPriceList").getMapping(userPriceListKey),
                        LM.object(LM.findClassByCompoundName("Currency")).getMapping(currencyKey)));
                fields.add(shortNameCurrencyField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(priceListStoresList.get(i).shortNameCurrency);

                ImportField pricePriceListDetailField = new ImportField(LM.findLCPByCompoundOldName("pricePriceListDetailDataPriceListType"));
                props.add(new ImportProperty(pricePriceListDetailField, LM.findLCPByCompoundOldName("priceUserPriceListDetailDataPriceListType").getMapping(userPriceListDetailKey, dataPriceListTypeObject)));
                fields.add(pricePriceListDetailField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(priceListStoresList.get(i).pricePriceListDetail);

                ImportField inPriceListPriceListTypeField = new ImportField(LM.findLCPByCompoundOldName("inPriceListDataPriceListType"));
                props.add(new ImportProperty(inPriceListPriceListTypeField, LM.findLCPByCompoundOldName("inPriceListDataPriceListType").getMapping(userPriceListKey, dataPriceListTypeObject)));
                fields.add(inPriceListPriceListTypeField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(priceListStoresList.get(i).inPriceList);

                ImportField inPriceListStockField = new ImportField(LM.findLCPByCompoundOldName("inPriceListStock"));
                props.add(new ImportProperty(inPriceListStockField, LM.findLCPByCompoundOldName("inPriceListStock").getMapping(userPriceListKey, departmentStoreKey)));
                fields.add(inPriceListStockField);
                for (int i = 0; i < priceListStoresList.size(); i++)
                    data.get(i).add(priceListStoresList.get(i).inPriceListStock);

                ImportTable table = new ImportTable(fields, data);

                IntegrationService service = new IntegrationService(session, table, Arrays.asList(userPriceListKey,
                        departmentStoreKey, userPriceListDetailKey, itemKey, legalEntityKey, currencyKey), props);
                service.synchronize(true, false);
                session.apply(context);
                if(!disableVolatileStats)
                    session.popVolatileStats();
                session.close();
            }
        }
    }

    private void importPriceListSuppliers(List<PriceListSupplier> priceListSuppliersList, Integer numberAtATime, boolean skipKeys, boolean disableVolatileStats) 
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
                if(!disableVolatileStats)
                    session.pushVolatileStats();

                ObjectValue dataPriceListTypeObject = LM.findLCPByCompoundOldName("dataPriceListTypeId").readClasses(session, new DataObject("Offered", StringClass.get(100)));
                if (dataPriceListTypeObject instanceof NullValue) {
                    dataPriceListTypeObject = session.addObject((ConcreteCustomClass) LM.findClassByCompoundName("DataPriceListType"));
                    Object defaultCurrency = LM.findLCPByCompoundOldName("currencyShortName").read(session, new DataObject("BLR", StringClass.get(3)));
                    LM.findLCPByCompoundOldName("namePriceListType").change("Поставщика (предлагаемая)", session, (DataObject) dataPriceListTypeObject);
                    LM.findLCPByCompoundOldName("currencyDataPriceListType").change(defaultCurrency, session, (DataObject) dataPriceListTypeObject);
                    LM.findLCPByCompoundOldName("idDataPriceListType").change("Offered", session, (DataObject) dataPriceListTypeObject);
                }

                List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
                List<ImportField> fields = new ArrayList<ImportField>();
                List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

                List<List<Object>> data = initData(priceListSuppliersList.size());

                ImportField idItemField = new ImportField(LM.findLCPByCompoundOldName("idItem"));
                ImportField idUserPriceListField = new ImportField(LM.findLCPByCompoundOldName("idUserPriceList"));
                ImportKey<?> itemKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Item"),
                        LM.findLCPByCompoundOldName("itemId").getMapping(idItemField));
                keys.add(itemKey);
                ImportKey<?> userPriceListDetailKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("UserPriceListDetail"),
                        LM.findLCPByCompoundOldName("userPriceListDetailIdSkuIdUserPriceList").getMapping(idItemField, idUserPriceListField));
                keys.add(userPriceListDetailKey);
                ImportKey<?> userPriceListKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("UserPriceList"),
                        LM.findLCPByCompoundOldName("userPriceListId").getMapping(idUserPriceListField));
                keys.add(userPriceListKey);
                props.add(new ImportProperty(idItemField, LM.findLCPByCompoundOldName("skuUserPriceListDetail").getMapping(userPriceListDetailKey),
                        LM.object(LM.findClassByCompoundName("Item")).getMapping(itemKey)));
                props.add(new ImportProperty(idUserPriceListField, LM.findLCPByCompoundOldName("idUserPriceList").getMapping(userPriceListKey)));
                props.add(new ImportProperty(idUserPriceListField, LM.findLCPByCompoundOldName("userPriceListUserPriceListDetail").getMapping(userPriceListDetailKey),
                        LM.object(LM.findClassByCompoundName("UserPriceList")).getMapping(userPriceListKey)));
                fields.add(idItemField);
                fields.add(idUserPriceListField);
                for (int i = 0; i < priceListSuppliersList.size(); i++) {
                    data.get(i).add(priceListSuppliersList.get(i).idItem);
                    data.get(i).add(priceListSuppliersList.get(i).idUserPriceList);
                }

                ImportField idLegalEntityField = new ImportField(LM.findLCPByCompoundOldName("idLegalEntity"));
                ImportKey<?> legalEntityKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("LegalEntity"),
                        LM.findLCPByCompoundOldName("legalEntityId").getMapping(idLegalEntityField));
                legalEntityKey.skipKey = skipKeys;
                keys.add(legalEntityKey);
                props.add(new ImportProperty(idLegalEntityField, LM.findLCPByCompoundOldName("companyUserPriceList").getMapping(userPriceListKey),
                        LM.object(LM.findClassByCompoundName("LegalEntity")).getMapping(legalEntityKey)));
                fields.add(idLegalEntityField);
                for (int i = 0; i < priceListSuppliersList.size(); i++)
                    data.get(i).add(priceListSuppliersList.get(i).idSupplier);

                ImportField shortNameCurrencyField = new ImportField(LM.findLCPByCompoundOldName("shortNameCurrency"));
                ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Currency"),
                        LM.findLCPByCompoundOldName("currencyShortName").getMapping(shortNameCurrencyField));
                keys.add(currencyKey);
                props.add(new ImportProperty(shortNameCurrencyField, LM.findLCPByCompoundOldName("currencyUserPriceList").getMapping(userPriceListKey),
                        LM.object(LM.findClassByCompoundName("Currency")).getMapping(currencyKey)));
                fields.add(shortNameCurrencyField);
                for (int i = 0; i < priceListSuppliersList.size(); i++)
                    data.get(i).add(priceListSuppliersList.get(i).shortNameCurrency);

                ImportField pricePriceListDetailField = new ImportField(LM.findLCPByCompoundOldName("pricePriceListDetailDataPriceListType"));
                props.add(new ImportProperty(pricePriceListDetailField, LM.findLCPByCompoundOldName("priceUserPriceListDetailDataPriceListType").getMapping(userPriceListDetailKey, dataPriceListTypeObject)));
                fields.add(pricePriceListDetailField);
                for (int i = 0; i < priceListSuppliersList.size(); i++)
                    data.get(i).add(priceListSuppliersList.get(i).pricePriceListDetail);

                ImportField inPriceListPriceListTypeField = new ImportField(LM.findLCPByCompoundOldName("inPriceListDataPriceListType"));
                props.add(new ImportProperty(inPriceListPriceListTypeField, LM.findLCPByCompoundOldName("inPriceListDataPriceListType").getMapping(userPriceListKey, dataPriceListTypeObject)));
                fields.add(inPriceListPriceListTypeField);
                for (int i = 0; i < priceListSuppliersList.size(); i++)
                    data.get(i).add(priceListSuppliersList.get(i).inPriceList);

                ImportField allStocksUserPriceListField = new ImportField(LM.findLCPByCompoundOldName("allStocksUserPriceList"));
                props.add(new ImportProperty(allStocksUserPriceListField, LM.findLCPByCompoundOldName("allStocksUserPriceList").getMapping(userPriceListKey)));
                fields.add(allStocksUserPriceListField);
                for (int i = 0; i < priceListSuppliersList.size(); i++)
                    data.get(i).add(priceListSuppliersList.get(i).inPriceList);

                ImportTable table = new ImportTable(fields, data);

                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context);
                if(!disableVolatileStats)
                    session.popVolatileStats();
                session.close();
            }
        }
    }

    private void importLegalEntities(List<LegalEntity> legalEntitiesList, boolean disableVolatileStats) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (legalEntitiesList != null) {

            ServerLoggers.systemLogger.info("importLegalEntities");

            DataObject defaultDate = new DataObject(new java.sql.Date(2001 - 1900, 0, 01), DateClass.instance);

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(legalEntitiesList.size());

            ImportField idLegalEntityField = new ImportField(LM.findLCPByCompoundOldName("idLegalEntity"));
            ImportKey<?> legalEntityKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("LegalEntity"),
                    LM.findLCPByCompoundOldName("legalEntityId").getMapping(idLegalEntityField));
            keys.add(legalEntityKey);
            props.add(new ImportProperty(idLegalEntityField, LM.findLCPByCompoundOldName("idLegalEntity").getMapping(legalEntityKey)));
            fields.add(idLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).idLegalEntity);

            ImportField numberAccountField = new ImportField(LM.findLCPByCompoundOldName("Bank.numberAccount"));
            ImportKey<?> accountKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Bank.Account"),
                    LM.findLCPByCompoundOldName("accountNumberLegalEntityID").getMapping(numberAccountField, idLegalEntityField));
            keys.add(accountKey);
            props.add(new ImportProperty(numberAccountField, LM.findLCPByCompoundOldName("Bank.numberAccount").getMapping(accountKey)));
            props.add(new ImportProperty(idLegalEntityField, LM.findLCPByCompoundOldName("Bank.legalEntityAccount").getMapping(accountKey),
                    LM.object(LM.findClassByCompoundName("LegalEntity")).getMapping(legalEntityKey)));
            fields.add(numberAccountField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).numberAccount);

            ImportField nameLegalEntityField = new ImportField(LM.findLCPByCompoundOldName("nameLegalEntity"));
            props.add(new ImportProperty(nameLegalEntityField, LM.findLCPByCompoundOldName("nameLegalEntity").getMapping(legalEntityKey)));
            props.add(new ImportProperty(nameLegalEntityField, LM.findLCPByCompoundOldName("fullNameLegalEntity").getMapping(legalEntityKey)));
            fields.add(nameLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).nameLegalEntity);

            ImportField addressLegalEntityField = new ImportField(LM.findLCPByCompoundOldName("addressLegalEntity"));
            props.add(new ImportProperty(addressLegalEntityField, LM.findLCPByCompoundOldName("dataAddressLegalEntityDate").getMapping(legalEntityKey, defaultDate)));
            fields.add(addressLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).addressLegalEntity);

            ImportField unpLegalEntityField = new ImportField(LM.findLCPByCompoundOldName("UNPLegalEntity"));
            props.add(new ImportProperty(unpLegalEntityField, LM.findLCPByCompoundOldName("UNPLegalEntity").getMapping(legalEntityKey)));
            fields.add(unpLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).unpLegalEntity);

            ImportField okpoLegalEntityField = new ImportField(LM.findLCPByCompoundOldName("OKPOLegalEntity"));
            props.add(new ImportProperty(okpoLegalEntityField, LM.findLCPByCompoundOldName("OKPOLegalEntity").getMapping(legalEntityKey)));
            fields.add(okpoLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).okpoLegalEntity);

            ImportField phoneLegalEntityField = new ImportField(LM.findLCPByCompoundOldName("dataPhoneLegalEntityDate"));
            props.add(new ImportProperty(phoneLegalEntityField, LM.findLCPByCompoundOldName("dataPhoneLegalEntityDate").getMapping(legalEntityKey, defaultDate)));
            fields.add(phoneLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).phoneLegalEntity);

            ImportField emailLegalEntityField = new ImportField(LM.findLCPByCompoundOldName("emailLegalEntity"));
            props.add(new ImportProperty(emailLegalEntityField, LM.findLCPByCompoundOldName("emailLegalEntity").getMapping(legalEntityKey)));
            fields.add(emailLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).emailLegalEntity);

            ImportField isSupplierLegalEntityField = new ImportField(LM.findLCPByCompoundOldName("isSupplierLegalEntity"));
            props.add(new ImportProperty(isSupplierLegalEntityField, LM.findLCPByCompoundOldName("isSupplierLegalEntity").getMapping(legalEntityKey), true));
            fields.add(isSupplierLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).isSupplierLegalEntity);

            ImportField isCompanyLegalEntityField = new ImportField(LM.findLCPByCompoundOldName("isCompanyLegalEntity"));
            props.add(new ImportProperty(isCompanyLegalEntityField, LM.findLCPByCompoundOldName("isCompanyLegalEntity").getMapping(legalEntityKey), true));
            fields.add(isCompanyLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).isCompanyLegalEntity);

            ImportField isCustomerLegalEntityField = new ImportField(LM.findLCPByCompoundOldName("isCustomerLegalEntity"));
            props.add(new ImportProperty(isCustomerLegalEntityField, LM.findLCPByCompoundOldName("isCustomerLegalEntity").getMapping(legalEntityKey), true));
            fields.add(isCustomerLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).isCustomerLegalEntity);

            ImportField shortNameOwnershipField = new ImportField(LM.findLCPByCompoundOldName("shortNameOwnership"));
            ImportKey<?> ownershipKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Ownership"),
                    LM.findLCPByCompoundOldName("ownershipShortName").getMapping(shortNameOwnershipField));
            keys.add(ownershipKey);
            props.add(new ImportProperty(shortNameOwnershipField, LM.findLCPByCompoundOldName("shortNameOwnership").getMapping(ownershipKey)));
            props.add(new ImportProperty(shortNameOwnershipField, LM.findLCPByCompoundOldName("ownershipLegalEntity").getMapping(legalEntityKey),
                    LM.object(LM.findClassByCompoundName("Ownership")).getMapping(ownershipKey)));
            fields.add(shortNameOwnershipField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).shortNameOwnership);

            ImportField nameOwnershipField = new ImportField(LM.findLCPByCompoundOldName("nameOwnership"));
            props.add(new ImportProperty(nameOwnershipField, LM.findLCPByCompoundOldName("nameOwnership").getMapping(ownershipKey)));
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

            ImportField idBankField = new ImportField(LM.findLCPByCompoundOldName("idBank"));
            ImportKey<?> bankKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Bank"),
                    LM.findLCPByCompoundOldName("bankId").getMapping(idBankField));
            keys.add(bankKey);
            props.add(new ImportProperty(idBankField, LM.findLCPByCompoundOldName("idBank").getMapping(bankKey)));
            props.add(new ImportProperty(idBankField, LM.findLCPByCompoundOldName("nameBank").getMapping(bankKey)));
            props.add(new ImportProperty(idBankField, LM.findLCPByCompoundOldName("Bank.bankAccount").getMapping(accountKey),
                    LM.object(LM.findClassByCompoundName("Bank")).getMapping(bankKey)));
            fields.add(idBankField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).idBank);

            ImportField nameCountryField = new ImportField(LM.findLCPByCompoundOldName("nameCountry"));
            ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Country"),
                    LM.findLCPByCompoundOldName("countryName").getMapping(nameCountryField));
            keys.add(countryKey);
            props.add(new ImportProperty(nameCountryField, LM.findLCPByCompoundOldName("nameCountry").getMapping(countryKey)));
            props.add(new ImportProperty(nameCountryField, LM.findLCPByCompoundOldName("countryLegalEntity").getMapping(legalEntityKey),
                    LM.object(LM.findClassByCompoundName("Country")).getMapping(countryKey)));
            props.add(new ImportProperty(nameCountryField, LM.findLCPByCompoundOldName("countryOwnership").getMapping(ownershipKey),
                    LM.object(LM.findClassByCompoundName("Country")).getMapping(countryKey)));
            fields.add(nameCountryField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).nameCountry);

            ImportField shortNameCurrencyField = new ImportField(LM.findLCPByCompoundOldName("shortNameCurrency"));
            ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Currency"),
                    LM.findLCPByCompoundOldName("currencyShortName").getMapping(shortNameCurrencyField));
            keys.add(currencyKey);
            props.add(new ImportProperty(shortNameCurrencyField, LM.findLCPByCompoundOldName("Bank.currencyAccount").getMapping(accountKey),
                    LM.object(LM.findClassByCompoundName("Currency")).getMapping(currencyKey)));
            fields.add(shortNameCurrencyField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add("BLR");

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.createSession();
            if(!disableVolatileStats)
                session.pushVolatileStats();
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            if(!disableVolatileStats)
                session.popVolatileStats();
            session.close();
        }
    }

    private void importEmployees(List<Employee> employeesList, boolean disableVolatileStats) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (employeesList != null) {

            ServerLoggers.systemLogger.info("importEmployees");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(employeesList.size());

            ImportField idEmployeeField = new ImportField(LM.findLCPByCompoundOldName("idEmployee"));
            ImportKey<?> employeeKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Employee"),
                    LM.findLCPByCompoundOldName("employeeId").getMapping(idEmployeeField));
            keys.add(employeeKey);
            props.add(new ImportProperty(idEmployeeField, LM.findLCPByCompoundOldName("idEmployee").getMapping(employeeKey)));
            fields.add(idEmployeeField);
            for (int i = 0; i < employeesList.size(); i++)
                data.get(i).add(employeesList.get(i).idEmployee);

            ImportField firstNameEmployeeField = new ImportField(LM.findLCPByCompoundOldName("firstNameContact"));
            props.add(new ImportProperty(firstNameEmployeeField, LM.findLCPByCompoundOldName("firstNameContact").getMapping(employeeKey)));
            fields.add(firstNameEmployeeField);
            for (int i = 0; i < employeesList.size(); i++)
                data.get(i).add(employeesList.get(i).firstNameEmployee);

            ImportField lastNameEmployeeField = new ImportField(LM.findLCPByCompoundOldName("lastNameContact"));
            props.add(new ImportProperty(lastNameEmployeeField, LM.findLCPByCompoundOldName("lastNameContact").getMapping(employeeKey)));
            fields.add(lastNameEmployeeField);
            for (int i = 0; i < employeesList.size(); i++)
                data.get(i).add(employeesList.get(i).lastNameEmployee);

            ImportField idPositionField = new ImportField(LM.findLCPByCompoundOldName("idPosition"));
            ImportKey<?> positionKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Position"),
                    LM.findLCPByCompoundOldName("positionId").getMapping(idPositionField));
            keys.add(positionKey);
            props.add(new ImportProperty(idPositionField, LM.findLCPByCompoundOldName("idPosition").getMapping(positionKey)));
            props.add(new ImportProperty(idPositionField, LM.findLCPByCompoundOldName("positionEmployee").getMapping(employeeKey),
                    LM.object(LM.findClassByCompoundName("Position")).getMapping(positionKey)));
            fields.add(idPositionField);
            for (int i = 0; i < employeesList.size(); i++)
                data.get(i).add(employeesList.get(i).idPosition);

            ImportField namePositionField = new ImportField(LM.findLCPByCompoundOldName("namePosition"));
            props.add(new ImportProperty(namePositionField, LM.findLCPByCompoundOldName("namePosition").getMapping(positionKey)));
            fields.add(namePositionField);
            for (int i = 0; i < employeesList.size(); i++)
                data.get(i).add(employeesList.get(i).idPosition);

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.createSession();
            if(!disableVolatileStats)
                session.pushVolatileStats();
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            if(!disableVolatileStats)
                session.popVolatileStats();
            session.close();
        }
    }

    private void importWarehouseGroups(List<WarehouseGroup> warehouseGroupsList, boolean disableVolatileStats) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (warehouseGroupsList != null) {

            ServerLoggers.systemLogger.info("importWarehouseGroups");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(warehouseGroupsList.size());

            ImportField idWarehouseGroupField = new ImportField(LM.findLCPByCompoundOldName("idWarehouseGroup"));
            ImportKey<?> warehouseGroupKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("WarehouseGroup"),
                    LM.findLCPByCompoundOldName("warehouseGroupId").getMapping(idWarehouseGroupField));
            keys.add(warehouseGroupKey);
            props.add(new ImportProperty(idWarehouseGroupField, LM.findLCPByCompoundOldName("idWarehouseGroup").getMapping(warehouseGroupKey)));
            fields.add(idWarehouseGroupField);
            for (int i = 0; i < warehouseGroupsList.size(); i++)
                data.get(i).add(warehouseGroupsList.get(i).idWarehouseGroup);

            ImportField nameWarehouseGroupField = new ImportField(LM.findLCPByCompoundOldName("nameWarehouseGroup"));
            props.add(new ImportProperty(nameWarehouseGroupField, LM.findLCPByCompoundOldName("nameWarehouseGroup").getMapping(warehouseGroupKey)));
            fields.add(nameWarehouseGroupField);
            for (int i = 0; i < warehouseGroupsList.size(); i++)
                data.get(i).add(warehouseGroupsList.get(i).nameWarehouseGroup);

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.createSession();
            if(!disableVolatileStats)
                session.pushVolatileStats();
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            if(!disableVolatileStats)
                session.popVolatileStats();
            session.close();
        }
    }

    private void importWarehouses(List<Warehouse> warehousesList, boolean disableVolatileStats) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (warehousesList != null) {

            ServerLoggers.systemLogger.info("importWarehouses");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(warehousesList.size());

            ImportField idWarehouseField = new ImportField(LM.findLCPByCompoundOldName("idWarehouse"));
            ImportKey<?> warehouseKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Warehouse"),
                    LM.findLCPByCompoundOldName("warehouseId").getMapping(idWarehouseField));
            keys.add(warehouseKey);
            props.add(new ImportProperty(idWarehouseField, LM.findLCPByCompoundOldName("idWarehouse").getMapping(warehouseKey)));
            fields.add(idWarehouseField);
            for (int i = 0; i < warehousesList.size(); i++)
                data.get(i).add(warehousesList.get(i).idWarehouse);

            ImportField nameWarehouseField = new ImportField(LM.findLCPByCompoundOldName("nameWarehouse"));
            props.add(new ImportProperty(nameWarehouseField, LM.findLCPByCompoundOldName("nameWarehouse").getMapping(warehouseKey)));
            fields.add(nameWarehouseField);
            for (int i = 0; i < warehousesList.size(); i++)
                data.get(i).add(warehousesList.get(i).nameWarehouse);

            ImportField addressWarehouseField = new ImportField(LM.findLCPByCompoundOldName("addressWarehouse"));
            props.add(new ImportProperty(addressWarehouseField, LM.findLCPByCompoundOldName("addressWarehouse").getMapping(warehouseKey)));
            fields.add(addressWarehouseField);
            for (int i = 0; i < warehousesList.size(); i++)
                data.get(i).add(warehousesList.get(i).addressWarehouse);

            ImportField idLegalEntityField = new ImportField(LM.findLCPByCompoundOldName("idLegalEntity"));
            ImportKey<?> legalEntityKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("LegalEntity"),
                    LM.findLCPByCompoundOldName("legalEntityId").getMapping(idLegalEntityField));
            keys.add(legalEntityKey);
            props.add(new ImportProperty(idLegalEntityField, LM.findLCPByCompoundOldName("idLegalEntity").getMapping(legalEntityKey)));
            props.add(new ImportProperty(idLegalEntityField, LM.findLCPByCompoundOldName("legalEntityWarehouse").getMapping(warehouseKey),
                    LM.object(LM.findClassByCompoundName("LegalEntity")).getMapping(legalEntityKey)));
            fields.add(idLegalEntityField);
            for (int i = 0; i < warehousesList.size(); i++)
                data.get(i).add(warehousesList.get(i).idLegalEntity);

            ImportField idWarehouseGroupField = new ImportField(LM.findLCPByCompoundOldName("idWarehouseGroup"));
            ImportKey<?> warehouseGroupKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("WarehouseGroup"),
                    LM.findLCPByCompoundOldName("warehouseGroupId").getMapping(idWarehouseGroupField));
            keys.add(warehouseGroupKey);
            props.add(new ImportProperty(idWarehouseGroupField, LM.findLCPByCompoundOldName("warehouseGroupWarehouse").getMapping(warehouseKey),
                    LM.object(LM.findClassByCompoundName("WarehouseGroup")).getMapping(warehouseGroupKey)));
            fields.add(idWarehouseGroupField);
            for (int i = 0; i < warehousesList.size(); i++)
                data.get(i).add(warehousesList.get(i).idWarehouseGroup);

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.createSession();
            if(!disableVolatileStats)
                session.pushVolatileStats();
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            if(!disableVolatileStats)
                session.popVolatileStats();
            session.close();
        }
    }

    private void importStores(List<LegalEntity> storesList, boolean skipKeys, boolean disableVolatileStats) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

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

            ImportField idLegalEntityField = new ImportField(LM.findLCPByCompoundOldName("idLegalEntity"));
            ImportKey<?> legalEntityKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("LegalEntity"),
                    LM.findLCPByCompoundOldName("legalEntityId").getMapping(idLegalEntityField));
            legalEntityKey.skipKey = skipKeys;
            keys.add(legalEntityKey);
            props.add(new ImportProperty(idLegalEntityField, storeLM.findLCPByCompoundOldName("legalEntityStore").getMapping(storeKey),
                    LM.object(LM.findClassByCompoundName("LegalEntity")).getMapping(legalEntityKey)));
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
            if(!disableVolatileStats)
                session.pushVolatileStats();
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            if(!disableVolatileStats)
                session.popVolatileStats();
            session.close();
        }
    }

    private void importDepartmentStores(List<DepartmentStore> departmentStoresList, boolean disableVolatileStats) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

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
            if(!disableVolatileStats)
                session.pushVolatileStats();
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            if(!disableVolatileStats)
                session.popVolatileStats();
            session.close();
        }
    }

    private void importBanks(List<Bank> banksList, boolean disableVolatileStats) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (banksList != null) {

            ServerLoggers.systemLogger.info("importBanks");

            DataObject defaultDate = new DataObject(new java.sql.Date(2001 - 1900, 0, 01), DateClass.instance);

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(banksList.size());

            ImportField idBankField = new ImportField(LM.findLCPByCompoundOldName("idBank"));
            ImportKey<?> bankKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Bank"),
                    LM.findLCPByCompoundOldName("bankId").getMapping(idBankField));
            keys.add(bankKey);
            props.add(new ImportProperty(idBankField, LM.findLCPByCompoundOldName("idBank").getMapping(bankKey)));
            fields.add(idBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).idBank);

            ImportField nameBankField = new ImportField(LM.findLCPByCompoundOldName("nameBank"));
            props.add(new ImportProperty(nameBankField, LM.findLCPByCompoundOldName("nameBank").getMapping(bankKey)));
            fields.add(nameBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).nameBank);

            ImportField addressBankField = new ImportField(LM.findLCPByCompoundOldName("dataAddressBankDate"));
            props.add(new ImportProperty(addressBankField, LM.findLCPByCompoundOldName("dataAddressBankDate").getMapping(bankKey, defaultDate)));
            fields.add(addressBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).addressBank);

            ImportField departmentBankField = new ImportField(LM.findLCPByCompoundOldName("departmentBank"));
            props.add(new ImportProperty(departmentBankField, LM.findLCPByCompoundOldName("departmentBank").getMapping(bankKey)));
            fields.add(departmentBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).departmentBank);

            ImportField mfoBankField = new ImportField(LM.findLCPByCompoundOldName("MFOBank"));
            props.add(new ImportProperty(mfoBankField, LM.findLCPByCompoundOldName("MFOBank").getMapping(bankKey)));
            fields.add(mfoBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).mfoBank);

            ImportField cbuBankField = new ImportField(LM.findLCPByCompoundOldName("CBUBank"));
            props.add(new ImportProperty(cbuBankField, LM.findLCPByCompoundOldName("CBUBank").getMapping(bankKey)));
            fields.add(cbuBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).cbuBank);

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.createSession();
            if(!disableVolatileStats)
                session.pushVolatileStats();
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            if(!disableVolatileStats)
                session.popVolatileStats();
            session.close();
        }
    }

    private void importRateWastes(List<RateWaste> rateWastesList, boolean disableVolatileStats) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

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

            ImportField nameCountryField = new ImportField(LM.findLCPByCompoundOldName("nameCountry"));
            ImportKey<?> countryKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Country"),
                    LM.findLCPByCompoundOldName("countryName").getMapping(nameCountryField));
            keys.add(countryKey);
            props.add(new ImportProperty(nameCountryField, writeOffItemLM.findLCPByCompoundOldName("countryWriteOffRate").getMapping(writeOffRateKey),
                    LM.object(LM.findClassByCompoundName("Country")).getMapping(countryKey)));
            fields.add(nameCountryField);
            for (int i = 0; i < rateWastesList.size(); i++)
                data.get(i).add(rateWastesList.get(i).nameCountry);

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.createSession();
            if(!disableVolatileStats)
                session.pushVolatileStats();
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            if(!disableVolatileStats)
                session.popVolatileStats();
            session.close();
        }
    }

    private void importContracts(List<Contract> contractsList, boolean skipKeys, boolean disableVolatileStats) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (contractsList != null) {

            ServerLoggers.systemLogger.info("importContacts");

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(contractsList.size());

            ImportField idUserContractSkuField = new ImportField(LM.findLCPByCompoundOldName("idUserContractSku"));
            ImportKey<?> userContractSkuKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("UserContractSku"),
                    LM.findLCPByCompoundOldName("userContractSkuId").getMapping(idUserContractSkuField));
            keys.add(userContractSkuKey);
            props.add(new ImportProperty(idUserContractSkuField, LM.findLCPByCompoundOldName("idUserContractSku").getMapping(userContractSkuKey)));
            fields.add(idUserContractSkuField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).idUserContractSku);

            ImportField numberContractField = new ImportField(LM.findLCPByCompoundOldName("numberContract"));
            props.add(new ImportProperty(numberContractField, LM.findLCPByCompoundOldName("numberContract").getMapping(userContractSkuKey)));
            fields.add(numberContractField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).numberContract);

            ImportField dateFromContractField = new ImportField(LM.findLCPByCompoundOldName("dateFromContract"));
            props.add(new ImportProperty(dateFromContractField, LM.findLCPByCompoundOldName("dateFromContract").getMapping(userContractSkuKey)));
            fields.add(dateFromContractField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).dateFromContract);

            ImportField dateToContractField = new ImportField(LM.findLCPByCompoundOldName("dateToContract"));
            props.add(new ImportProperty(dateToContractField, LM.findLCPByCompoundOldName("dateToContract").getMapping(userContractSkuKey)));
            fields.add(dateToContractField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).dateToContract);

            ImportField idSupplierField = new ImportField(LM.findLCPByCompoundOldName("idLegalEntity"));
            ImportKey<?> supplierKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("LegalEntity"),
                    LM.findLCPByCompoundOldName("legalEntityId").getMapping(idSupplierField));
            supplierKey.skipKey = skipKeys;
            keys.add(supplierKey);
            props.add(new ImportProperty(idSupplierField, LM.findLCPByCompoundOldName("supplierContractSku").getMapping(userContractSkuKey),
                    LM.object(LM.findClassByCompoundName("LegalEntity")).getMapping(supplierKey)));
            fields.add(idSupplierField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).idSupplier);

            ImportField idCustomerField = new ImportField(LM.findLCPByCompoundOldName("idLegalEntity"));
            ImportKey<?> customerKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("LegalEntity"),
                    LM.findLCPByCompoundOldName("legalEntityId").getMapping(idCustomerField));
            customerKey.skipKey = skipKeys;
            keys.add(customerKey);
            props.add(new ImportProperty(idCustomerField, LM.findLCPByCompoundOldName("customerContractSku").getMapping(userContractSkuKey),
                    LM.object(LM.findClassByCompoundName("LegalEntity")).getMapping(customerKey)));
            fields.add(idCustomerField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).idCustomer);

            ImportField shortNameCurrencyField = new ImportField(LM.findLCPByCompoundOldName("shortNameCurrency"));
            ImportKey<?> currencyKey = new ImportKey((ConcreteCustomClass) LM.findClassByCompoundName("Currency"),
                    LM.findLCPByCompoundOldName("currencyShortName").getMapping(shortNameCurrencyField));
            keys.add(currencyKey);
            props.add(new ImportProperty(shortNameCurrencyField, LM.findLCPByCompoundOldName("currencyContract").getMapping(userContractSkuKey),
                    LM.object(LM.findClassByCompoundName("Currency")).getMapping(currencyKey)));
            fields.add(shortNameCurrencyField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).shortNameCurrency);

            ImportTable table = new ImportTable(fields, data);

            DataSession session = context.createSession();
            if(!disableVolatileStats)
                session.pushVolatileStats();
            IntegrationService service = new IntegrationService(session, table, keys, props);
            service.synchronize(true, false);
            session.apply(context);
            if(!disableVolatileStats)
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