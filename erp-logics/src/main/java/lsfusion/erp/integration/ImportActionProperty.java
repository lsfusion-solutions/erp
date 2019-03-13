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
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import org.xBaseJ.xBaseJException;

import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.ArrayList;
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

    DataObject defaultDate = new DataObject(defaultDateFrom, DateClass.instance);

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

            ObjectValue countryBelarus = findProperty("country[STRING[3]]").readClasses(context.getSession(), new DataObject("112", StringClass.get(3)));
            findProperty("defaultCountry[]").change(countryBelarus, context.getSession());
            context.apply();

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

            importItems(importData.getItemsList(), importData.getNumberOfItemsAtATime());

            importPriceListStores(importData.getPriceListStoresList(), importData.getNumberOfPriceListsAtATime(), importData.getSkipKeys());

            importPriceListSuppliers(importData.getPriceListSuppliersList(), importData.getNumberOfPriceListsAtATime(), importData.getSkipKeys());

            importUserInvoices(importData.getUserInvoicesList(), importData.getNumberOfUserInvoicesAtATime(), importData.getSkipKeys(), importData.getUserInvoiceCreateNewItems());

        } catch (Exception e) {
            throw ExceptionUtils.propagate(e, SQLException.class);
        }
    }

    private void importParentGroups(List<ItemGroup> parentGroupsList) throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        if (notNullNorEmpty(parentGroupsList)) {

            ServerLoggers.importLogger.info("importParentGroups");

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(parentGroupsList.size());

            ImportField idItemGroupField = new ImportField(findProperty("id[ItemGroup]"));
            ImportKey<?> itemGroupKey = new ImportKey((CustomClass) findClass("ItemGroup"),
                    findProperty("itemGroup[VARSTRING[100]]").getMapping(idItemGroupField));
            keys.add(itemGroupKey);
            fields.add(idItemGroupField);
            for (int i = 0; i < parentGroupsList.size(); i++)
                data.get(i).add(parentGroupsList.get(i).sid);

            ImportField idParentGroupField = new ImportField(findProperty("id[ItemGroup]"));
            ImportKey<?> parentGroupKey = new ImportKey((CustomClass) findClass("ItemGroup"),
                    findProperty("itemGroup[VARSTRING[100]]").getMapping(idParentGroupField));
            keys.add(parentGroupKey);
            props.add(new ImportProperty(idParentGroupField, findProperty("parent[ItemGroup]").getMapping(itemGroupKey),
                    LM.object(findClass("ItemGroup")).getMapping(parentGroupKey)));
            fields.add(idParentGroupField);
            for (int i = 0; i < parentGroupsList.size(); i++)
                data.get(i).add(parentGroupsList.get(i).parent);

            ImportTable table = new ImportTable(fields, data);

            try(ExecutionContext.NewSession newContext = context.newSession()) {
                IntegrationService service = new IntegrationService(newContext, table, keys, props);
                service.synchronize(true, false);
                newContext.apply();
            }
        }
    }

    private void importItemGroups(List<ItemGroup> itemGroupsList) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (notNullNorEmpty(itemGroupsList)) {

            ServerLoggers.importLogger.info("importItemGroups");

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(itemGroupsList.size());

            ImportField idItemGroupField = new ImportField(findProperty("id[ItemGroup]"));
            ImportKey<?> itemGroupKey = new ImportKey((CustomClass) findClass("ItemGroup"),
                    findProperty("itemGroup[VARSTRING[100]]").getMapping(idItemGroupField));
            keys.add(itemGroupKey);
            props.add(new ImportProperty(idItemGroupField, findProperty("id[ItemGroup]").getMapping(itemGroupKey)));
            fields.add(idItemGroupField);
            for (int i = 0; i < itemGroupsList.size(); i++)
                data.get(i).add(itemGroupsList.get(i).sid);

            ImportField itemGroupNameField = new ImportField(findProperty("name[ItemGroup]"));
            props.add(new ImportProperty(itemGroupNameField, findProperty("name[ItemGroup]").getMapping(itemGroupKey)));
            fields.add(itemGroupNameField);
            for (int i = 0; i < itemGroupsList.size(); i++)
                data.get(i).add(itemGroupsList.get(i).name);

            ImportTable table = new ImportTable(fields, data);

            try(ExecutionContext.NewSession newContext = context.newSession()) {
                IntegrationService service = new IntegrationService(newContext, table, keys, props);
                service.synchronize(true, false);
                newContext.apply();
            }
        }
    }

    private void importWares(List<Ware> waresList) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (warePurchaseInvoiceLM != null && notNullNorEmpty(waresList)) {

            ServerLoggers.importLogger.info("importWares");

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(waresList.size());

            ImportField idWareField = new ImportField(warePurchaseInvoiceLM.findProperty("id[Ware]"));
            ImportKey<?> wareKey = new ImportKey((CustomClass) warePurchaseInvoiceLM.findClass("Ware"),
                    warePurchaseInvoiceLM.findProperty("ware[VARSTRING[100]]").getMapping(idWareField));
            keys.add(wareKey);
            props.add(new ImportProperty(idWareField, warePurchaseInvoiceLM.findProperty("id[Ware]").getMapping(wareKey)));
            fields.add(idWareField);
            for (int i = 0; i < waresList.size(); i++)
                data.get(i).add(waresList.get(i).idWare);

            ImportField nameWareField = new ImportField(warePurchaseInvoiceLM.findProperty("name[Ware]"));
            props.add(new ImportProperty(nameWareField, warePurchaseInvoiceLM.findProperty("name[Ware]").getMapping(wareKey)));
            fields.add(nameWareField);
            for (int i = 0; i < waresList.size(); i++)
                data.get(i).add(waresList.get(i).nameWare);

            ImportField priceWareField = new ImportField(warePurchaseInvoiceLM.findProperty("price[Ware]"));
            props.add(new ImportProperty(priceWareField, warePurchaseInvoiceLM.findProperty("dataPrice[Ware,DATE]").getMapping(wareKey, defaultDate)));
            fields.add(priceWareField);
            for (int i = 0; i < waresList.size(); i++)
                data.get(i).add(waresList.get(i).priceWare);

            ImportTable table = new ImportTable(fields, data);

            try(ExecutionContext.NewSession newContext = context.newSession()) {
                IntegrationService service = new IntegrationService(newContext, table, keys, props);
                service.synchronize(true, false);
                newContext.apply();
            }
        }
    }

    private void importItems(List<Item> itemsList, Integer numberOfItemsAtATime) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        try {
            Integer numAtATime = (numberOfItemsAtATime == null || numberOfItemsAtATime <= 0) ? 5000 : numberOfItemsAtATime;
            if (itemsList != null) {
                int amountOfImportIterations = (int) Math.ceil((double) itemsList.size() / numAtATime);
                Integer rest = itemsList.size();
                for (int i = 0; i < amountOfImportIterations; i++) {
                    importPackOfItems(itemsList.subList(i * numAtATime, i * numAtATime + (rest > numAtATime ? numAtATime : rest)));
                    rest -= numAtATime;
                }
            }
        } catch (xBaseJException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void importUOMs(List<UOM> uomsList) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        if (uomsList == null)
            return;

        ServerLoggers.importLogger.info("importUOMs");

        List<ImportProperty<?>> props = new ArrayList<>();
        List<ImportField> fields = new ArrayList<>();
        List<ImportKey<?>> keys = new ArrayList<>();

        List<List<Object>> data = initData(uomsList.size());

        ImportField idUOMField = new ImportField(findProperty("id[UOM]"));
        ImportKey<?> UOMKey = new ImportKey((CustomClass) findClass("UOM"),
                findProperty("UOM[VARSTRING[100]]").getMapping(idUOMField));
        keys.add(UOMKey);
        props.add(new ImportProperty(idUOMField, findProperty("id[UOM]").getMapping(UOMKey)));
        fields.add(idUOMField);
        for (int i = 0; i < uomsList.size(); i++)
            data.get(i).add(uomsList.get(i).idUOM);

        ImportField nameUOMField = new ImportField(findProperty("name[UOM]"));
        props.add(new ImportProperty(nameUOMField, findProperty("name[UOM]").getMapping(UOMKey)));
        fields.add(nameUOMField);
        for (int i = 0; i < uomsList.size(); i++)
            data.get(i).add(uomsList.get(i).nameUOM);

        ImportField shortNameUOMField = new ImportField(findProperty("shortName[UOM]"));
        props.add(new ImportProperty(shortNameUOMField, findProperty("shortName[UOM]").getMapping(UOMKey)));
        fields.add(shortNameUOMField);
        for (int i = 0; i < uomsList.size(); i++)
            data.get(i).add(uomsList.get(i).shortNameUOM);

        ImportTable table = new ImportTable(fields, data);

        try(ExecutionContext.NewSession newContext = context.newSession()) {
            IntegrationService service = new IntegrationService(newContext, table, keys, props);
            service.synchronize(true, false);
            newContext.apply();
        }
    }


    private void importPackOfItems(List<Item> itemsList) throws SQLException, IOException, xBaseJException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        if (!notNullNorEmpty(itemsList)) return;

        ServerLoggers.importLogger.info("importItems " + itemsList.size());

        List<ImportProperty<?>> props = new ArrayList<>();
        List<ImportField> fields = new ArrayList<>();
        List<ImportKey<?>> keys = new ArrayList<>();

        List<List<Object>> data = initData(itemsList.size());

        ImportField idItemField = new ImportField(findProperty("id[Item]"));
        ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                findProperty("item[VARSTRING[100]]").getMapping(idItemField));
        keys.add(itemKey);
        props.add(new ImportProperty(idItemField, findProperty("id[Item]").getMapping(itemKey)));
        fields.add(idItemField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).idItem);

        if (showItemField(itemsList, "captionItem")) {
            ImportField captionItemField = new ImportField(findProperty("caption[Item]"));
            props.add(new ImportProperty(captionItemField, findProperty("caption[Item]").getMapping(itemKey)));
            fields.add(captionItemField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).captionItem);
        }

        if (showItemField(itemsList, "idItemGroup")) {
            ImportField idItemGroupField = new ImportField(findProperty("id[ItemGroup]"));
            ImportKey<?> itemGroupKey = new ImportKey((CustomClass) findClass("ItemGroup"),
                    findProperty("itemGroup[VARSTRING[100]]").getMapping(idItemGroupField));
            keys.add(itemGroupKey);
            props.add(new ImportProperty(idItemGroupField, findProperty("itemGroup[Item]").getMapping(itemKey),
                    LM.object(findClass("ItemGroup")).getMapping(itemGroupKey)));
            fields.add(idItemGroupField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).idItemGroup);
        }

        if (showItemField(itemsList, "idBrand")) {
            ImportField idBrandField = new ImportField(findProperty("id[Brand]"));
            ImportKey<?> brandKey = new ImportKey((CustomClass) findClass("Brand"),
                    findProperty("brand[VARSTRING[100]]").getMapping(idBrandField));
            keys.add(brandKey);
            props.add(new ImportProperty(idBrandField, findProperty("id[Brand]").getMapping(brandKey)));
            props.add(new ImportProperty(idBrandField, findProperty("brand[Item]").getMapping(itemKey),
                    LM.object(findClass("Brand")).getMapping(brandKey)));
            fields.add(idBrandField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).idBrand);

            ImportField nameBrandField = new ImportField(findProperty("name[Brand]"));
            props.add(new ImportProperty(nameBrandField, findProperty("name[Brand]").getMapping(brandKey)));
            fields.add(nameBrandField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).nameBrand);
        }

        ImportField nameCountryField = new ImportField(findProperty("name[Country]"));
        ImportKey<?> countryKey = new ImportKey((CustomClass) findClass("Country"),
                findProperty("countryName[VARISTRING[50]]").getMapping(nameCountryField));
        keys.add(countryKey);
        props.add(new ImportProperty(nameCountryField, findProperty("name[Country]").getMapping(countryKey)));
        props.add(new ImportProperty(nameCountryField, findProperty("country[Item]").getMapping(itemKey),
                LM.object(findClass("Country")).getMapping(countryKey)));
        fields.add(nameCountryField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).nameCountry);

        ImportField extIdBarcodeField = new ImportField(findProperty("extId[Barcode]"));
        ImportKey<?> barcodeKey = new ImportKey((CustomClass) findClass("Barcode"),
                findProperty(/*"barcodeIdDate"*/"extBarcode[VARSTRING[100]]").getMapping(extIdBarcodeField));
        keys.add(barcodeKey);
        props.add(new ImportProperty(idItemField, findProperty("sku[Barcode]").getMapping(barcodeKey),
                LM.object(findClass("Item")).getMapping(itemKey)));
        props.add(new ImportProperty(extIdBarcodeField, findProperty("extId[Barcode]").getMapping(barcodeKey)));
        fields.add(extIdBarcodeField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).extIdBarcode);

        ImportField idBarcodeField = new ImportField(findProperty("id[Barcode]"));
        props.add(new ImportProperty(idBarcodeField, findProperty("id[Barcode]").getMapping(barcodeKey)));
        fields.add(idBarcodeField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).idBarcode);

        ImportField idUOMField = new ImportField(findProperty("id[UOM]"));
        ImportKey<?> UOMKey = new ImportKey((CustomClass) findClass("UOM"),
                findProperty("UOM[VARSTRING[100]]").getMapping(idUOMField));
        UOMKey.skipKey = true;
        keys.add(UOMKey);
        props.add(new ImportProperty(idUOMField, findProperty("UOM[Item]").getMapping(itemKey),
                LM.object(findClass("UOM")).getMapping(UOMKey)));
        props.add(new ImportProperty(idUOMField, findProperty("UOM[Barcode]").getMapping(barcodeKey),
                LM.object(findClass("UOM")).getMapping(UOMKey)));
        fields.add(idUOMField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).idUOM);

        if (showItemField(itemsList, "splitItem")) {
            ImportField splitItemField = new ImportField(findProperty("split[Item]"));
            props.add(new ImportProperty(splitItemField, findProperty("split[Item]").getMapping(itemKey), true));
            fields.add(splitItemField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).splitItem);
        }

        if (showItemField(itemsList, "netWeightItem")) {
            ImportField netWeightItemField = new ImportField(findProperty("netWeight[Item]"));
            props.add(new ImportProperty(netWeightItemField, findProperty("netWeight[Item]").getMapping(itemKey)));
            fields.add(netWeightItemField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).netWeightItem);
        }

        if (showItemField(itemsList, "grossWeightItem")) {
            ImportField grossWeightItemField = new ImportField(findProperty("grossWeight[Item]"));
            props.add(new ImportProperty(grossWeightItemField, findProperty("grossWeight[Item]").getMapping(itemKey)));
            fields.add(grossWeightItemField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).grossWeightItem);
        }

        if (showItemField(itemsList, "compositionItem")) {
            ImportField compositionItemField = new ImportField(findProperty("composition[Item]"));
            props.add(new ImportProperty(compositionItemField, findProperty("composition[Item]").getMapping(itemKey)));
            fields.add(compositionItemField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).compositionItem);
        }

        ImportField dateField = new ImportField(DateClass.instance);
        props.add(new ImportProperty(dateField, findProperty("dataDate[Barcode]").getMapping(barcodeKey)));
        fields.add(dateField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(itemsList.get(i).date);

        ObjectValue defaultCountryObject = findProperty("defaultCountry[]").readClasses(context.getSession());
        if(defaultCountryObject instanceof DataObject) {
            ImportField valueVATItemCountryDateField = new ImportField(findProperty("valueVAT[Item,Country,DATE]"));
            ImportKey<?> VATKey = new ImportKey((CustomClass) findClass("Range"),
                    findProperty("valueCurrentVATDefault[NUMERIC[10,5]]").getMapping(valueVATItemCountryDateField));
            VATKey.skipKey = true;
            keys.add(VATKey);
            props.add(new ImportProperty(valueVATItemCountryDateField, findProperty("VAT[Item,Country]").getMapping(itemKey, defaultCountryObject),
                    LM.object(findClass("Range")).getMapping(VATKey)));
            fields.add(valueVATItemCountryDateField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).retailVAT);
        }

        if (warePurchaseInvoiceLM != null && showItemField(itemsList, "idWare")) {

            ImportField idWareField = new ImportField(warePurchaseInvoiceLM.findProperty("id[Ware]"));
            ImportKey<?> wareKey = new ImportKey((CustomClass) warePurchaseInvoiceLM.findClass("Ware"),
                    warePurchaseInvoiceLM.findProperty("ware[VARSTRING[100]]").getMapping(idWareField));
            keys.add(wareKey);
            props.add(new ImportProperty(idWareField, warePurchaseInvoiceLM.findProperty("id[Ware]").getMapping(wareKey)));
            props.add(new ImportProperty(idWareField, warePurchaseInvoiceLM.findProperty("ware[Item]").getMapping(itemKey),
                    warePurchaseInvoiceLM.object(warePurchaseInvoiceLM.findClass("Ware")).getMapping(wareKey)));
            fields.add(idWareField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).idWare);

            ImportField priceWareField = new ImportField(warePurchaseInvoiceLM.findProperty("dataPrice[Ware,DATE]"));
            props.add(new ImportProperty(priceWareField, warePurchaseInvoiceLM.findProperty("dataPrice[Ware,DATE]").getMapping(wareKey, dateField)));
            fields.add(priceWareField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).priceWare);

            ImportField vatWareField = new ImportField(warePurchaseInvoiceLM.findProperty("value[Rate]"));
            ImportKey<?> rangeKey = new ImportKey((CustomClass) warePurchaseInvoiceLM.findClass("Range"),
                    warePurchaseInvoiceLM.findProperty("valueCurrentVATDefault[NUMERIC[10,5]]").getMapping(vatWareField));
            keys.add(rangeKey);
            props.add(new ImportProperty(vatWareField, warePurchaseInvoiceLM.findProperty("VAT[Ware,Country]").getMapping(wareKey, countryKey),
                    warePurchaseInvoiceLM.object(warePurchaseInvoiceLM.findClass("Range")).getMapping(rangeKey)));
            fields.add(vatWareField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).vatWare);

        }

        if (writeOffItemLM != null && showItemField(itemsList, "idWriteOffRate")) {

            ImportField idWriteOffRateField = new ImportField(writeOffItemLM.findProperty("id[WriteOffRate]"));
            ImportKey<?> writeOffRateKey = new ImportKey((CustomClass) writeOffItemLM.findClass("WriteOffRate"),
                    writeOffItemLM.findProperty("writeOffRate[VARSTRING[100]]").getMapping(idWriteOffRateField));
            keys.add(writeOffRateKey);
            props.add(new ImportProperty(idWriteOffRateField, writeOffItemLM.findProperty("writeOffRate[Country,Item]").getMapping(defaultCountryObject, itemKey),
                    LM.object(writeOffItemLM.findClass("WriteOffRate")).getMapping(writeOffRateKey)));
            fields.add(idWriteOffRateField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).idWriteOffRate);

        }

        if (showItemField(itemsList, "retailMarkup")) {

            ImportField idRetailCalcPriceListTypeField = new ImportField(findProperty("id[CalcPriceListType]"));
            ImportKey<?> retailCalcPriceListTypeKey = new ImportKey((CustomClass) findClass("CalcPriceListType"),
                    findProperty("calcPriceListType[VARSTRING[100]]").getMapping(idRetailCalcPriceListTypeField));
            keys.add(retailCalcPriceListTypeKey);
            props.add(new ImportProperty(idRetailCalcPriceListTypeField, findProperty("id[CalcPriceListType]").getMapping(retailCalcPriceListTypeKey)));
            fields.add(idRetailCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add("retail");

            ImportField nameRetailCalcPriceListTypeField = new ImportField(findProperty("name[PriceListType]"));
            props.add(new ImportProperty(nameRetailCalcPriceListTypeField, findProperty("name[PriceListType]").getMapping(retailCalcPriceListTypeKey)));
            fields.add(nameRetailCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add("Розничная надбавка");

            ImportField retailMarkupCalcPriceListTypeField = new ImportField(findProperty("dataMarkup[CalcPriceListType,Sku]"));
            props.add(new ImportProperty(retailMarkupCalcPriceListTypeField, findProperty("dataMarkup[CalcPriceListType,Sku]").getMapping(retailCalcPriceListTypeKey, itemKey)));
            fields.add(retailMarkupCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).retailMarkup);

        }

        if (showItemField(itemsList, "baseMarkup")) {

            ImportField idBaseCalcPriceListTypeField = new ImportField(findProperty("id[CalcPriceListType]"));
            ImportKey<?> baseCalcPriceListTypeKey = new ImportKey((CustomClass) findClass("CalcPriceListType"),
                    findProperty("calcPriceListType[VARSTRING[100]]").getMapping(idBaseCalcPriceListTypeField));
            keys.add(baseCalcPriceListTypeKey);
            props.add(new ImportProperty(idBaseCalcPriceListTypeField, findProperty("id[CalcPriceListType]").getMapping(baseCalcPriceListTypeKey)));
            fields.add(idBaseCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add("wholesale");

            ImportField nameBaseCalcPriceListTypeField = new ImportField(findProperty("name[PriceListType]"));
            props.add(new ImportProperty(nameBaseCalcPriceListTypeField, findProperty("name[PriceListType]").getMapping(baseCalcPriceListTypeKey)));
            fields.add(nameBaseCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add("Оптовая надбавка");

            ImportField baseMarkupCalcPriceListTypeField = new ImportField(findProperty("dataMarkup[CalcPriceListType,Sku]"));
            props.add(new ImportProperty(baseMarkupCalcPriceListTypeField, findProperty("dataMarkup[CalcPriceListType,Sku]").getMapping(baseCalcPriceListTypeKey, itemKey)));
            fields.add(baseMarkupCalcPriceListTypeField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).baseMarkup);
        }

        ImportField extIdBarcodePackField = new ImportField(findProperty("extId[Barcode]"));
        ImportKey<?> barcodePackKey = new ImportKey((CustomClass) findClass("Barcode"),
                findProperty(/*"barcodeIdDate"*/"extBarcode[VARSTRING[100]]").getMapping(extIdBarcodePackField));
        keys.add(barcodePackKey);
        props.add(new ImportProperty(dateField, findProperty("dataDate[Barcode]").getMapping(barcodePackKey)));
        props.add(new ImportProperty(idItemField, findProperty("sku[Barcode]").getMapping(barcodePackKey),
                LM.object(findClass("Item")).getMapping(itemKey)));
        props.add(new ImportProperty(extIdBarcodePackField, findProperty("extId[Barcode]").getMapping(barcodePackKey)));
        props.add(new ImportProperty(extIdBarcodePackField, findProperty("Purchase.packBarcode[Sku]").getMapping(itemKey),
                LM.object(findClass("Barcode")).getMapping(barcodePackKey)));
        fields.add(extIdBarcodePackField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(valueWithPrefix(itemsList.get(i).idBarcodePack, "P", null));

        ImportField idBarcodePackField = new ImportField(findProperty("id[Barcode]"));
        props.add(new ImportProperty(idBarcodePackField, findProperty("id[Barcode]").getMapping(barcodePackKey)));
        if(salePackLM != null) {
            props.add(new ImportProperty(extIdBarcodePackField, salePackLM.findProperty("packBarcode[Sku]").getMapping(itemKey),
                    salePackLM.object(salePackLM.findClass("Barcode")).getMapping(barcodePackKey)));
        }
        fields.add(idBarcodePackField);
        for (int i = 0; i < itemsList.size(); i++)
            data.get(i).add(null);

        if (showItemField(itemsList, "amountPack")) {
            ImportField amountBarcodePackField = new ImportField(findProperty("amount[Barcode]"));
            props.add(new ImportProperty(amountBarcodePackField, findProperty("amount[Barcode]").getMapping(barcodePackKey)));
            fields.add(amountBarcodePackField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).amountPack);
        }

        if (showItemField(itemsList, "idUOMPack")) {
            ImportField idUOMPackField = new ImportField(findProperty("id[UOM]"));
            props.add(new ImportProperty(idUOMPackField, findProperty("UOM[Barcode]").getMapping(barcodePackKey),
                    LM.object(findClass("UOM")).getMapping(UOMKey)));
            fields.add(idUOMPackField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).idUOMPack);
        }

        if (showItemField(itemsList, "idManufacturer")) {
            ImportField idManufacturerField = new ImportField(findProperty("id[Manufacturer]"));
            ImportKey<?> manufacturerKey = new ImportKey((CustomClass) findClass("Manufacturer"),
                    findProperty("manufacturer[VARSTRING[100]]").getMapping(idManufacturerField));
            keys.add(manufacturerKey);
            props.add(new ImportProperty(idManufacturerField, findProperty("id[Manufacturer]").getMapping(manufacturerKey)));
            props.add(new ImportProperty(idManufacturerField, findProperty("manufacturer[Item]").getMapping(itemKey),
                    LM.object(findClass("Manufacturer")).getMapping(manufacturerKey)));
            fields.add(idManufacturerField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).idManufacturer);

            ImportField nameManufacturerField = new ImportField(findProperty("name[Manufacturer]"));
            props.add(new ImportProperty(nameManufacturerField, findProperty("name[Manufacturer]").getMapping(manufacturerKey)));
            fields.add(nameManufacturerField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).nameManufacturer);
        }

        if (showItemField(itemsList, "codeCustomsGroup")) {
            ImportField codeCustomsGroupField = new ImportField(findProperty("code[CustomsGroup]"));
            ImportKey<?> customsGroupKey = new ImportKey((CustomClass) findClass("CustomsGroup"),
                    findProperty("customsGroup[STRING[10]]").getMapping(codeCustomsGroupField));
            keys.add(customsGroupKey);
            props.add(new ImportProperty(codeCustomsGroupField, findProperty("code[CustomsGroup]").getMapping(customsGroupKey)));
            props.add(new ImportProperty(codeCustomsGroupField, findProperty("customsGroup[Country,Item]").getMapping(countryKey, itemKey),
                    LM.object(findClass("CustomsGroup")).getMapping(customsGroupKey)));
            fields.add(codeCustomsGroupField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).codeCustomsGroup);

            ImportField nameCustomsZoneField = new ImportField(findProperty("name[CustomsZone]"));
            ImportKey<?> customsZoneKey = new ImportKey((CustomClass) findClass("CustomsZone"),
                    findProperty("customsZone[VARISTRING[50]]").getMapping(nameCustomsZoneField));
            keys.add(customsZoneKey);
            props.add(new ImportProperty(nameCustomsZoneField, findProperty("name[CustomsZone]").getMapping(customsZoneKey)));
            props.add(new ImportProperty(nameCustomsZoneField, findProperty("customsZone[CustomsGroup]").getMapping(customsGroupKey),
                    LM.object(findClass("CustomsZone")).getMapping(customsZoneKey)));
            fields.add(nameCustomsZoneField);
            for (int i = 0; i < itemsList.size(); i++)
                data.get(i).add(itemsList.get(i).nameCustomsZone);
        }

        ImportTable table = new ImportTable(fields, data);

        try(ExecutionContext.NewSession newContext = context.newSession()) {
            IntegrationService service = new IntegrationService(newContext, table, keys, props);
            service.synchronize(true, false);
            newContext.apply();
        }
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

                ServerLoggers.importLogger.info("importUserInvoices " + dataUserInvoiceDetail.size());
                
                List<ImportProperty<?>> props = new ArrayList<>();
                List<ImportField> fields = new ArrayList<>();
                List<ImportKey<?>> keys = new ArrayList<>();

                List<List<Object>> data = initData(dataUserInvoiceDetail.size());

                ImportField idUserInvoiceField = new ImportField(findProperty("id[UserInvoice]"));
                ImportKey<?> userInvoiceKey = new ImportKey((CustomClass) findClass("Purchase.UserInvoice"),
                        findProperty("userInvoice[VARSTRING[100]]").getMapping(idUserInvoiceField));
                keys.add(userInvoiceKey);
                props.add(new ImportProperty(idUserInvoiceField, findProperty("id[UserInvoice]").getMapping(userInvoiceKey)));
                fields.add(idUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idUserInvoice);

                ImportField idUserInvoiceDetailField = new ImportField(findProperty("id[UserInvoiceDetail]"));
                ImportKey<?> userInvoiceDetailKey = new ImportKey((CustomClass) findClass("Purchase.UserInvoiceDetail"),
                        findProperty("userInvoiceDetail[VARSTRING[100]]").getMapping(idUserInvoiceDetailField));
                keys.add(userInvoiceDetailKey);
                props.add(new ImportProperty(idUserInvoiceDetailField, findProperty("id[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                props.add(new ImportProperty(idUserInvoiceField, findProperty("userInvoice[UserInvoiceDetail]").getMapping(userInvoiceDetailKey),
                        LM.object(findClass("Purchase.UserInvoice")).getMapping(userInvoiceKey)));
                fields.add(idUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idUserInvoiceDetail);


                ImportField idCustomerStockField = new ImportField(findProperty("id[Stock]"));
                ImportKey<?> customerStockKey = new ImportKey((CustomClass) findClass("Stock"),
                        findProperty("stock[VARSTRING[100]]").getMapping(idCustomerStockField));
                customerStockKey.skipKey = skipKeys;
                keys.add(customerStockKey);
                props.add(new ImportProperty(idCustomerStockField, findProperty("id[Stock]").getMapping(customerStockKey)));
                props.add(new ImportProperty(idCustomerStockField, findProperty("customer[UserInvoice]").getMapping(userInvoiceKey),
                        findProperty("legalEntity[Stock]").getMapping(customerStockKey)));
                props.add(new ImportProperty(idCustomerStockField, findProperty("customerStock[UserInvoice]").getMapping(userInvoiceKey),
                        LM.object(findClass("Stock")).getMapping(customerStockKey)));
                fields.add(idCustomerStockField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idCustomerStock);


                ImportField idSupplierField = new ImportField(findProperty("id[LegalEntity]"));
                ImportKey<?> supplierKey = new ImportKey((CustomClass) findClass("LegalEntity"),
                        findProperty("legalEntity[VARSTRING[100]]").getMapping(idSupplierField));
                supplierKey.skipKey = skipKeys;
                keys.add(supplierKey);
                props.add(new ImportProperty(idSupplierField, findProperty("supplier[UserInvoice]").getMapping(userInvoiceKey),
                        LM.object(findClass("LegalEntity")).getMapping(supplierKey)));
                fields.add(idSupplierField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idSupplier);


                ImportField idSupplierStockField = new ImportField(findProperty("id[Stock]"));
                ImportKey<?> supplierStockKey = new ImportKey((CustomClass) findClass("Stock"),
                        findProperty("stock[VARSTRING[100]]").getMapping(idSupplierStockField));
                supplierStockKey.skipKey = skipKeys;
                keys.add(supplierStockKey);
                props.add(new ImportProperty(idSupplierStockField, findProperty("id[Stock]").getMapping(supplierStockKey)));
                props.add(new ImportProperty(idSupplierStockField, findProperty("supplierStock[UserInvoice]").getMapping(userInvoiceKey),
                        LM.object(findClass("Stock")).getMapping(supplierStockKey)));
                fields.add(idSupplierStockField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idSupplierStock);


                ImportField numberUserInvoiceField = new ImportField(findProperty("number[UserInvoice]"));
                props.add(new ImportProperty(numberUserInvoiceField, findProperty("number[UserInvoice]").getMapping(userInvoiceKey)));
                fields.add(numberUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).number);

                if (showField(dataUserInvoiceDetail, "series")) {
                    ImportField seriesUserInvoiceField = new ImportField(findProperty("series[UserInvoice]"));
                    props.add(new ImportProperty(seriesUserInvoiceField, findProperty("series[UserInvoice]").getMapping(userInvoiceKey)));
                    fields.add(seriesUserInvoiceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).series);
                }

                if (pricingPurchaseLM != null) {

                    ImportField createPricingUserInvoiceField = new ImportField(pricingPurchaseLM.findProperty("createPricing[UserInvoice]"));
                    props.add(new ImportProperty(createPricingUserInvoiceField, pricingPurchaseLM.findProperty("createPricing[UserInvoice]").getMapping(userInvoiceKey), true));
                    fields.add(createPricingUserInvoiceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).createPricing);

                    ImportField retailPriceUserInvoiceDetailField = new ImportField(pricingPurchaseLM.findProperty("retailPrice[UserInvoiceDetail]"));
                    props.add(new ImportProperty(retailPriceUserInvoiceDetailField, pricingPurchaseLM.findProperty("retailPrice[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                    fields.add(retailPriceUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).retailPrice);

                    ImportField retailMarkupUserInvoiceDetailField = new ImportField(pricingPurchaseLM.findProperty("retailMarkup[UserInvoiceDetail]"));
                    props.add(new ImportProperty(retailMarkupUserInvoiceDetailField, pricingPurchaseLM.findProperty("retailMarkup[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                    fields.add(retailMarkupUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).retailMarkup);

                }

                ImportField createShipmentUserInvoiceField = new ImportField(findProperty("createShipment[UserInvoice]"));
                props.add(new ImportProperty(createShipmentUserInvoiceField, findProperty("createShipment[UserInvoice]").getMapping(userInvoiceKey), true));
                fields.add(createShipmentUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).createShipment);

                if (showField(dataUserInvoiceDetail, "manufacturingPrice")) {
                    ImportField showManufacturingPriceUserInvoiceField = new ImportField(findProperty("showManufacturingPrice[UserInvoice]"));
                    props.add(new ImportProperty(showManufacturingPriceUserInvoiceField, findProperty("showManufacturingPrice[UserInvoice]").getMapping(userInvoiceKey)));
                    fields.add(showManufacturingPriceUserInvoiceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                }

                if (purchaseInvoiceWholesalePriceLM != null) {

                    ImportField showWholesalePriceUserInvoiceField = new ImportField(purchaseInvoiceWholesalePriceLM.findProperty("showWholesalePrice[UserInvoice]"));
                    props.add(new ImportProperty(showWholesalePriceUserInvoiceField, purchaseInvoiceWholesalePriceLM.findProperty("showWholesalePrice[UserInvoice]").getMapping(userInvoiceKey), true));
                    fields.add(showWholesalePriceUserInvoiceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                    
                    ImportField wholesalePriceUserInvoiceDetailField = new ImportField(purchaseInvoiceWholesalePriceLM.findProperty("wholesalePrice[UserInvoiceDetail]"));
                    props.add(new ImportProperty(wholesalePriceUserInvoiceDetailField, purchaseInvoiceWholesalePriceLM.findProperty("wholesalePrice[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                    fields.add(wholesalePriceUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).wholesalePrice);

                    ImportField wholesaleMarkupUserInvoiceDetailField = new ImportField(purchaseInvoiceWholesalePriceLM.findProperty("wholesaleMarkup[UserInvoiceDetail]"));
                    props.add(new ImportProperty(wholesaleMarkupUserInvoiceDetailField, purchaseInvoiceWholesalePriceLM.findProperty("wholesaleMarkup[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                    fields.add(wholesaleMarkupUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).wholesaleMarkup);

                }

                ImportField dateUserInvoiceField = new ImportField(findProperty("date[UserInvoice]"));
                props.add(new ImportProperty(dateUserInvoiceField, findProperty("date[UserInvoice]").getMapping(userInvoiceKey)));
                fields.add(dateUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).date);


                ImportField timeUserInvoiceField = new ImportField(TimeClass.instance);
                props.add(new ImportProperty(timeUserInvoiceField, findProperty("time[UserInvoice]").getMapping(userInvoiceKey)));
                fields.add(timeUserInvoiceField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(noonTime);


                ImportField idItemField = new ImportField(findProperty("id[Item]"));
                ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                        findProperty("item[VARSTRING[100]]").getMapping(idItemField));
                itemKey.skipKey = skipKeys && !userInvoiceCreateNewItems;
                keys.add(itemKey);
                if (userInvoiceCreateNewItems)
                    props.add(new ImportProperty(idItemField, findProperty("id[Item]").getMapping(itemKey)));
                props.add(new ImportProperty(idItemField, findProperty("sku[UserInvoiceDetail]").getMapping(userInvoiceDetailKey),
                        LM.object(findClass("Sku")).getMapping(itemKey)));
                fields.add(idItemField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).idItem);


                ImportField quantityUserInvoiceDetailField = new ImportField(findProperty("quantity[UserInvoiceDetail]"));
                props.add(new ImportProperty(quantityUserInvoiceDetailField, findProperty("quantity[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                fields.add(quantityUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).quantity);


                ImportField priceUserInvoiceDetail = new ImportField(findProperty("price[UserInvoiceDetail]"));
                props.add(new ImportProperty(priceUserInvoiceDetail, findProperty("price[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                fields.add(priceUserInvoiceDetail);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).price);
                
                if (showField(dataUserInvoiceDetail, "shipmentPrice")) {
                    ImportField shipmentPriceInvoiceDetail = new ImportField(findProperty("shipmentPrice[UserInvoiceDetail]"));
                    props.add(new ImportProperty(shipmentPriceInvoiceDetail, findProperty("shipmentPrice[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                    fields.add(shipmentPriceInvoiceDetail);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).shipmentPrice);
                }

                if (showField(dataUserInvoiceDetail, "shipmentSum")) {
                    ImportField shipmentSumInvoiceDetail = new ImportField(findProperty("shipmentSum[UserInvoiceDetail]"));
                    props.add(new ImportProperty(shipmentSumInvoiceDetail, findProperty("shipmentSum[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                    fields.add(shipmentSumInvoiceDetail);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).shipmentSum);
                }
                    
                ImportField expiryDateUserInvoiceDetailField = new ImportField(findProperty("expiryDate[UserInvoiceDetail]"));
                props.add(new ImportProperty(expiryDateUserInvoiceDetailField, findProperty("expiryDate[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                fields.add(expiryDateUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).expiryDate);

                if (!skipExtraInvoiceParams) {

                    ImportField dataRateExchangeUserInvoiceDetailField = new ImportField(findProperty("dataRateExchange[UserInvoiceDetail]"));
                    props.add(new ImportProperty(dataRateExchangeUserInvoiceDetailField, findProperty("dataRateExchange[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                    fields.add(dataRateExchangeUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).rateExchange);


                    ImportField homePriceUserInvoiceDetailField = new ImportField(findProperty("homePrice[UserInvoiceDetail]"));
                    props.add(new ImportProperty(homePriceUserInvoiceDetailField, findProperty("homePrice[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                    fields.add(homePriceUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).homePrice);


                    if (showField(dataUserInvoiceDetail, "priceDuty")) {
                        ImportField priceDutyUserInvoiceDetailField = new ImportField(findProperty("dutyPrice[UserInvoiceDetail]"));
                        props.add(new ImportProperty(priceDutyUserInvoiceDetailField, findProperty("dutyPrice[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                        fields.add(priceDutyUserInvoiceDetailField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).priceDuty);
                    }

                    if (showField(dataUserInvoiceDetail, "priceCompliance") && purchaseComplianceDetailLM != null) {
                        ImportField priceComplianceUserInvoiceDetailField = new ImportField(purchaseComplianceDetailLM.findProperty("compliancePrice[UserInvoiceDetail]"));
                        props.add(new ImportProperty(priceComplianceUserInvoiceDetailField, purchaseComplianceDetailLM.findProperty("compliancePrice[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                        fields.add(priceComplianceUserInvoiceDetailField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).priceCompliance);

                        ImportField showComplianceUserInvoiceDetailField = new ImportField(purchaseComplianceDetailLM.findProperty("showCompliance[UserInvoice]"));
                        props.add(new ImportProperty(showComplianceUserInvoiceDetailField, purchaseComplianceDetailLM.findProperty("showCompliance[UserInvoice]").getMapping(userInvoiceKey)));
                        fields.add(showComplianceUserInvoiceDetailField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(true);
                    }

                    if (showField(dataUserInvoiceDetail, "priceRegistration")) {
                        ImportField priceRegistrationUserInvoiceDetailField = new ImportField(findProperty("registrationPrice[UserInvoiceDetail]"));
                        props.add(new ImportProperty(priceRegistrationUserInvoiceDetailField, findProperty("registrationPrice[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                        fields.add(priceRegistrationUserInvoiceDetailField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).priceRegistration);
                    }

                    if (showField(dataUserInvoiceDetail, "chargePrice") || showField(dataUserInvoiceDetail, "chargeSum")) {

                        if (purchaseInvoiceChargeLM != null) {
                            ImportField chargePriceUserInvoiceDetailField = new ImportField(purchaseInvoiceChargeLM.findProperty("chargePrice[UserInvoiceDetail]"));
                            props.add(new ImportProperty(chargePriceUserInvoiceDetailField, purchaseInvoiceChargeLM.findProperty("chargePrice[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                            fields.add(chargePriceUserInvoiceDetailField);
                            for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                                data.get(i).add(dataUserInvoiceDetail.get(i).chargePrice);

                            ImportField chargeSumUserInvoiceDetailField = new ImportField(purchaseInvoiceChargeLM.findProperty("chargeSum[UserInvoiceDetail]"));
                            props.add(new ImportProperty(chargeSumUserInvoiceDetailField, purchaseInvoiceChargeLM.findProperty("chargeSum[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                            fields.add(chargeSumUserInvoiceDetailField);
                            for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                                data.get(i).add(dataUserInvoiceDetail.get(i).chargeSum);

                            ImportField showChargePriceUserInvoiceDetailField = new ImportField(purchaseInvoiceChargeLM.findProperty("showChargePrice[UserInvoice]"));
                            props.add(new ImportProperty(showChargePriceUserInvoiceDetailField, purchaseInvoiceChargeLM.findProperty("showChargePrice[UserInvoice]").getMapping(userInvoiceKey)));
                            fields.add(showChargePriceUserInvoiceDetailField);
                            for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                                data.get(i).add(true);
                        }
                    }
                }

                if (showField(dataUserInvoiceDetail, "idBin")) {
                    ImportField binUserInvoiceDetailField = new ImportField(findProperty("id[Bin]"));
                    ImportKey<?> binKey = new ImportKey((CustomClass) findClass("Bin"),
                            findProperty("bin[VARSTRING[100]]").getMapping(binUserInvoiceDetailField));
                    keys.add(binKey);
                    props.add(new ImportProperty(binUserInvoiceDetailField, findProperty("id[Bin]").getMapping(binKey)));
                    props.add(new ImportProperty(binUserInvoiceDetailField, findProperty("name[Bin]").getMapping(binKey)));
                    props.add(new ImportProperty(binUserInvoiceDetailField, findProperty("bin[UserInvoiceDetail]").getMapping(userInvoiceDetailKey),
                            LM.object(findClass("Bin")).getMapping(binKey)));
                    fields.add(binUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).idBin);

                    ImportField showBinUserInvoiceField = new ImportField(findProperty("showBin[UserInvoice]"));
                    props.add(new ImportProperty(showBinUserInvoiceField, findProperty("showBin[UserInvoice]").getMapping(userInvoiceKey)));
                    fields.add(showBinUserInvoiceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                }

                if (!skipExtraInvoiceParams) {
                    if (purchaseManufacturingPriceLM != null) {
                        ImportField manufacturingPriceUserInvoiceDetailField = new ImportField(purchaseManufacturingPriceLM.findProperty("manufacturingPrice[UserInvoiceDetail]"));
                        props.add(new ImportProperty(manufacturingPriceUserInvoiceDetailField, purchaseManufacturingPriceLM.findProperty("manufacturingPrice[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                        fields.add(manufacturingPriceUserInvoiceDetailField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).manufacturingPrice);

                        ImportField manufacturingMarkupUserInvoiceDetailField = new ImportField(purchaseManufacturingPriceLM.findProperty("manufacturingMarkup[UserInvoiceDetail]"));
                        props.add(new ImportProperty(manufacturingMarkupUserInvoiceDetailField, purchaseManufacturingPriceLM.findProperty("manufacturingMarkup[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                        fields.add(manufacturingMarkupUserInvoiceDetailField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).manufacturingMarkup);
                    }
                }
                
                if(purchaseCertificateLM != null) {
                    ImportField certificateTextUserInvoiceDetailField = new ImportField(purchaseCertificateLM.findProperty("certificateText[UserInvoiceDetail]"));
                    props.add(new ImportProperty(certificateTextUserInvoiceDetailField, purchaseCertificateLM.findProperty("certificateText[UserInvoiceDetail]").getMapping(userInvoiceDetailKey)));
                    fields.add(certificateTextUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).certificateText);
                }
                    
                if (warePurchaseInvoiceLM != null) {
                    ImportField skipCreateWareUserInvoiceDetailField = new ImportField(warePurchaseInvoiceLM.findProperty("skipCreateWare[UserInvoiceDetail]"));
                    props.add(new ImportProperty(skipCreateWareUserInvoiceDetailField, warePurchaseInvoiceLM.findProperty("skipCreateWare[UserInvoiceDetail]").getMapping(userInvoiceDetailKey), true));
                    fields.add(skipCreateWareUserInvoiceDetailField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                }


                if (showField(dataUserInvoiceDetail, "idContract")) {
                    ImportField userContractSkuField = new ImportField(findProperty("id[UserContractSku]"));
                    ImportKey<?> userContractSkuKey = new ImportKey((CustomClass) findClass("UserContractSku"),
                            findProperty("userContractSku[VARSTRING[100]]").getMapping(userContractSkuField));
                    userContractSkuKey.skipKey = skipKeys;
                    keys.add(userContractSkuKey);
                    props.add(new ImportProperty(userContractSkuField, findProperty("contractSku[Purchase.Invoice]").getMapping(userInvoiceKey),
                            LM.object(findClass("Contract")).getMapping(userContractSkuKey)));
                    fields.add(userContractSkuField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).idContract);
                }
                
                if (!skipCertificateInvoiceParams) {
                    if (showField(dataUserInvoiceDetail, "numberDeclaration")) {
                        ImportField numberDeclarationField = new ImportField(findProperty("number[Declaration]"));
                        ImportKey<?> declarationKey = new ImportKey((CustomClass) findClass("Declaration"),
                                findProperty("declaration[VARSTRING[100]]").getMapping(numberDeclarationField));
                        keys.add(declarationKey);
                        props.add(new ImportProperty(numberDeclarationField, findProperty("number[Declaration]").getMapping(declarationKey)));
                        props.add(new ImportProperty(numberDeclarationField, findProperty("id[Declaration]").getMapping(declarationKey)));
                        props.add(new ImportProperty(numberDeclarationField, findProperty("declaration[UserInvoiceDetail]").getMapping(userInvoiceDetailKey),
                                LM.object(findClass("Declaration")).getMapping(declarationKey)));
                        fields.add(numberDeclarationField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).numberDeclaration);

                        ImportField dateDeclarationField = new ImportField(findProperty("date[Declaration]"));
                        props.add(new ImportProperty(dateDeclarationField, findProperty("date[Declaration]").getMapping(declarationKey)));
                        fields.add(dateDeclarationField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).dateDeclaration);
                    }

                    if (showField(dataUserInvoiceDetail, "numberCompliance")) {
                        ImportField numberComplianceField = new ImportField(findProperty("number[Compliance]"));
                        ImportKey<?> complianceKey = new ImportKey((CustomClass) findClass("Compliance"),
                                findProperty("compliance[STRING[100]]").getMapping(numberComplianceField));
                        keys.add(complianceKey);
                        props.add(new ImportProperty(numberComplianceField, findProperty("number[Compliance]").getMapping(complianceKey)));
                        props.add(new ImportProperty(numberComplianceField, findProperty("compliance[UserInvoiceDetail]").getMapping(userInvoiceDetailKey),
                                LM.object(findClass("Compliance")).getMapping(complianceKey)));
                        fields.add(numberComplianceField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).numberCompliance);

                        ImportField fromDateComplianceField = new ImportField(findProperty("fromDate[Compliance]"));
                        props.add(new ImportProperty(fromDateComplianceField, findProperty("date[Compliance]").getMapping(complianceKey)));
                        props.add(new ImportProperty(fromDateComplianceField, findProperty("fromDate[Compliance]").getMapping(complianceKey)));
                        fields.add(fromDateComplianceField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).fromDateCompliance);

                        ImportField toDateComplianceField = new ImportField(findProperty("toDate[Compliance]"));
                        props.add(new ImportProperty(toDateComplianceField, findProperty("toDate[Compliance]").getMapping(complianceKey)));
                        fields.add(toDateComplianceField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).toDateCompliance);
                    }
                }
                
                if (!skipExtraInvoiceParams) {
                    if (showField(dataUserInvoiceDetail, "isHomeCurrency")) {
                        ImportField isHomeCurrencyUserInvoiceField = new ImportField(findProperty("isHomeCurrency[UserInvoice]"));
                        props.add(new ImportProperty(isHomeCurrencyUserInvoiceField, findProperty("isHomeCurrency[UserInvoice]").getMapping(userInvoiceKey), true));
                        fields.add(isHomeCurrencyUserInvoiceField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).isHomeCurrency);
                    }
                }

                if (showField(dataUserInvoiceDetail, "codeCustomsGroup") || showField(dataUserInvoiceDetail, "priceDuty") || showField(dataUserInvoiceDetail, "priceRegistration")) {
                    ImportField showDeclarationUserInvoiceField = new ImportField(findProperty("showDeclaration[UserInvoice]"));
                    props.add(new ImportProperty(showDeclarationUserInvoiceField, findProperty("showDeclaration[UserInvoice]").getMapping(userInvoiceKey), true));
                    fields.add(showDeclarationUserInvoiceField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(true);
                }

                ImportField shortNameCurrencyField = new ImportField(findProperty("shortName[Currency]"));
                ImportKey<?> currencyKey = new ImportKey((CustomClass) findClass("Currency"),
                        findProperty("currencyShortName[STRING[3]]").getMapping(shortNameCurrencyField));
                keys.add(currencyKey);
                props.add(new ImportProperty(shortNameCurrencyField, findProperty("currency[UserInvoice]").getMapping(userInvoiceKey),
                        LM.object(findClass("Currency")).getMapping(currencyKey)));
                fields.add(shortNameCurrencyField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).shortNameCurrency);
                
                if (!skipCertificateInvoiceParams) {
                    if (purchaseDeclarationDetailLM != null) {
                        ObjectValue defaultCountryObject = findProperty("defaultCountry[]").readClasses(context.getSession());
                        ImportField codeCustomsGroupField = new ImportField(purchaseDeclarationDetailLM.findProperty("code[CustomsGroup]"));
                        ImportKey<?> customsGroupKey = new ImportKey((CustomClass) purchaseDeclarationDetailLM.findClass("CustomsGroup"),
                                purchaseDeclarationDetailLM.findProperty("customsGroup[STRING[10]]").getMapping(codeCustomsGroupField));
                        keys.add(customsGroupKey);
                        props.add(new ImportProperty(codeCustomsGroupField, purchaseDeclarationDetailLM.findProperty("customsGroup[UserInvoiceDetail]").getMapping(userInvoiceDetailKey),
                                purchaseDeclarationDetailLM.object(purchaseDeclarationDetailLM.findClass("CustomsGroup")).getMapping(customsGroupKey)));
                        props.add(new ImportProperty(codeCustomsGroupField, purchaseDeclarationDetailLM.findProperty("customsGroup[Country,Item]").getMapping(defaultCountryObject, itemKey),
                                purchaseDeclarationDetailLM.object(purchaseDeclarationDetailLM.findClass("CustomsGroup")).getMapping(customsGroupKey)));
                        fields.add(codeCustomsGroupField);
                        for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                            data.get(i).add(dataUserInvoiceDetail.get(i).codeCustomsGroup);
                    }
                }
                    
                ImportField valueVATUserInvoiceDetailField = new ImportField(findProperty("valueVAT[UserInvoiceDetail]"));
                ImportKey<?> VATKey = new ImportKey((CustomClass) findClass("Range"),
                        findProperty("valueCurrentVATDefault[NUMERIC[10,5]]").getMapping(valueVATUserInvoiceDetailField));
                VATKey.skipKey = true;
                keys.add(VATKey);
                props.add(new ImportProperty(valueVATUserInvoiceDetailField, findProperty("VAT[UserInvoiceDetail]").getMapping(userInvoiceDetailKey),
                        LM.object(findClass("Range")).getMapping(VATKey)));
                fields.add(valueVATUserInvoiceDetailField);
                for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                    data.get(i).add(dataUserInvoiceDetail.get(i).retailVAT);

                if (tripInvoiceLM != null && showField(dataUserInvoiceDetail, "numberTrip")) {
                    ImportField numberTripField = new ImportField(tripInvoiceLM.findProperty("number[Trip]"));
                    ImportKey<?> tripKey = new ImportKey((CustomClass) tripInvoiceLM.findClass("Trip"),
                            tripInvoiceLM.findProperty("trip[STRING[18]]").getMapping(numberTripField));
                    keys.add(tripKey);
                    props.add(new ImportProperty(numberTripField, tripInvoiceLM.findProperty("number[Trip]").getMapping(tripKey)));
                    props.add(new ImportProperty(numberTripField, tripInvoiceLM.findProperty("trip[Invoice]").getMapping(userInvoiceKey),
                            object(tripInvoiceLM.findClass("Trip")).getMapping(tripKey)));
                    fields.add(numberTripField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).numberTrip);

                    ImportField dateTripField = new ImportField(tripInvoiceLM.findProperty("date[Trip]"));
                    props.add(new ImportProperty(dateTripField, tripInvoiceLM.findProperty("date[Trip]").getMapping(tripKey)));
                    fields.add(dateTripField);
                    for (int i = 0; i < dataUserInvoiceDetail.size(); i++)
                        data.get(i).add(dataUserInvoiceDetail.get(i).dateTrip);
                }

                ImportTable table = new ImportTable(fields, data);

                try(ExecutionContext.NewSession newContext = context.newSession()) {
                    IntegrationService service = new IntegrationService(newContext, table, keys, props);
                    service.synchronize(true, false);
                    newContext.apply();
                }
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

                ServerLoggers.importLogger.info("importPriceListStores " + dataPriceListStores.size());

                try(ExecutionContext.NewSession newContext = context.newSession()) {

                    ObjectValue dataPriceListTypeObject = findProperty("dataPriceListType[VARSTRING[100]]").readClasses(newContext, new DataObject("Coordinated", StringClass.get(100)));
                    if (dataPriceListTypeObject instanceof NullValue) {
                        dataPriceListTypeObject = newContext.addObject((ConcreteCustomClass) findClass("DataPriceListType"));
                        ObjectValue defaultCurrency = findProperty("currencyShortName[STRING[3]]").readClasses(newContext, new DataObject("BYN", StringClass.get(3)));
                        findProperty("name[PriceListType]").change("Поставщика (согласованная)", newContext, (DataObject) dataPriceListTypeObject);
                        findProperty("currency[DataPriceListType]").change(defaultCurrency, newContext, (DataObject) dataPriceListTypeObject);
                        findProperty("id[DataPriceListType]").change("Coordinated", newContext, (DataObject) dataPriceListTypeObject);
                    }

                    List<ImportProperty<?>> props = new ArrayList<>();
                    List<ImportField> fields = new ArrayList<>();
                    List<ImportKey<?>> keys = new ArrayList<>();

                    List<List<Object>> data = initData(priceListStoresList.size());

                    ImportField idItemField = new ImportField(findProperty("id[Item]"));
                    ImportField idUserPriceListField = new ImportField(findProperty("id[UserPriceList]"));
                    ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                            findProperty("item[VARSTRING[100]]").getMapping(idItemField));
                    itemKey.skipKey = true;
                    keys.add(itemKey);
                    ImportKey<?> userPriceListDetailKey = new ImportKey((CustomClass) findClass("UserPriceListDetail"),
                            importUserPriceListLM.findProperty("userPriceListDetailIdId[VARSTRING[100],VARSTRING[100]]").getMapping(idItemField, idUserPriceListField));
                    keys.add(userPriceListDetailKey);
                    ImportKey<?> userPriceListKey = new ImportKey((CustomClass) findClass("UserPriceList"),
                            findProperty("userPriceList[VARSTRING[100]]").getMapping(idUserPriceListField));
                    keys.add(userPriceListKey);
                    props.add(new ImportProperty(idItemField, findProperty("sku[UserPriceListDetail]").getMapping(userPriceListDetailKey),
                            LM.object(findClass("Item")).getMapping(itemKey)));
                    props.add(new ImportProperty(idUserPriceListField, findProperty("id[UserPriceList]").getMapping(userPriceListKey)));
                    props.add(new ImportProperty(idUserPriceListField, findProperty("userPriceList[UserPriceListDetail]").getMapping(userPriceListDetailKey),
                            LM.object(findClass("UserPriceList")).getMapping(userPriceListKey)));
                    fields.add(idItemField);
                    fields.add(idUserPriceListField);
                    for (int i = 0; i < priceListStoresList.size(); i++) {
                        data.get(i).add(priceListStoresList.get(i).idItem);
                        data.get(i).add(priceListStoresList.get(i).idPriceList);
                    }

                    ImportField idLegalEntityField = new ImportField(findProperty("id[LegalEntity]"));
                    ImportKey<?> legalEntityKey = new ImportKey((CustomClass) findClass("LegalEntity"),
                            findProperty("legalEntity[VARSTRING[100]]").getMapping(idLegalEntityField));
                    legalEntityKey.skipKey = skipKeys;
                    keys.add(legalEntityKey);
                    props.add(new ImportProperty(idLegalEntityField, findProperty("company[UserPriceList]").getMapping(userPriceListKey),
                            LM.object(findClass("LegalEntity")).getMapping(legalEntityKey)));
                    fields.add(idLegalEntityField);
                    for (int i = 0; i < priceListStoresList.size(); i++)
                        data.get(i).add(priceListStoresList.get(i).idSupplier);

                    ImportField idDepartmentStoreField = new ImportField(storeLM.findProperty("id[DepartmentStore]"));
                    ImportKey<?> departmentStoreKey = new ImportKey((CustomClass) storeLM.findClass("DepartmentStore"),
                            storeLM.findProperty("departmentStore[VARSTRING[100]]").getMapping(idDepartmentStoreField));
                    keys.add(departmentStoreKey);
                    fields.add(idDepartmentStoreField);
                    for (int i = 0; i < priceListStoresList.size(); i++)
                        data.get(i).add(priceListStoresList.get(i).idDepartmentStore);

                    ImportField shortNameCurrencyField = new ImportField(findProperty("shortName[Currency]"));
                    ImportKey<?> currencyKey = new ImportKey((CustomClass) findClass("Currency"),
                            findProperty("currencyShortName[STRING[3]]").getMapping(shortNameCurrencyField));
                    keys.add(currencyKey);
                    props.add(new ImportProperty(shortNameCurrencyField, findProperty("currency[UserPriceList]").getMapping(userPriceListKey),
                            LM.object(findClass("Currency")).getMapping(currencyKey)));
                    fields.add(shortNameCurrencyField);
                    for (int i = 0; i < priceListStoresList.size(); i++)
                        data.get(i).add(priceListStoresList.get(i).shortNameCurrency);

                    ImportField pricePriceListDetailField = new ImportField(findProperty("price[PriceListDetail,DataPriceListType]"));
                    props.add(new ImportProperty(pricePriceListDetailField, findProperty("price[UserPriceListDetail,DataPriceListType]").getMapping(userPriceListDetailKey, dataPriceListTypeObject)));
                    fields.add(pricePriceListDetailField);
                    for (int i = 0; i < priceListStoresList.size(); i++)
                        data.get(i).add(priceListStoresList.get(i).pricePriceListDetail);

                    ImportField inPriceListPriceListTypeField = new ImportField(findProperty("in[UserPriceList,DataPriceListType]"));
                    props.add(new ImportProperty(inPriceListPriceListTypeField, findProperty("in[UserPriceList,DataPriceListType]").getMapping(userPriceListKey, dataPriceListTypeObject)));
                    fields.add(inPriceListPriceListTypeField);
                    for (int i = 0; i < priceListStoresList.size(); i++)
                        data.get(i).add(true);

                    ImportField inPriceListStockField = new ImportField(findProperty("in[PriceList,Stock]"));
                    props.add(new ImportProperty(inPriceListStockField, findProperty("in[PriceList,Stock]").getMapping(userPriceListKey, departmentStoreKey)));
                    fields.add(inPriceListStockField);
                    for (int i = 0; i < priceListStoresList.size(); i++)
                        data.get(i).add(true);

                    ImportTable table = new ImportTable(fields, data);

                    IntegrationService service = new IntegrationService(newContext, table, keys, props);
                    service.synchronize(true, false);
                    newContext.apply();
                }
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

                ServerLoggers.importLogger.info("importPriceListSuppliers " + dataPriceListSuppliers.size());

                try (ExecutionContext.NewSession<ClassPropertyInterface> newContext = context.newSession()) {
                    ObjectValue dataPriceListTypeObject = findProperty("dataPriceListType[VARSTRING[100]]").readClasses(newContext, new DataObject("Offered", StringClass.get(100)));
                    if (dataPriceListTypeObject instanceof NullValue) {
                        dataPriceListTypeObject = newContext.addObject((ConcreteCustomClass) findClass("DataPriceListType"));
                        ObjectValue defaultCurrency = findProperty("currencyShortName[STRING[3]]").readClasses(newContext, new DataObject("BYN", StringClass.get(3)));
                        findProperty("name[PriceListType]").change("Поставщика (предлагаемая)", newContext, (DataObject) dataPriceListTypeObject);
                        findProperty("currency[DataPriceListType]").change(defaultCurrency, newContext, (DataObject) dataPriceListTypeObject);
                        findProperty("id[DataPriceListType]").change("Offered", newContext, (DataObject) dataPriceListTypeObject);
                    }

                    List<ImportProperty<?>> props = new ArrayList<>();
                    List<ImportField> fields = new ArrayList<>();
                    List<ImportKey<?>> keys = new ArrayList<>();

                    List<List<Object>> data = initData(priceListSuppliersList.size());

                    ImportField idItemField = new ImportField(findProperty("id[Item]"));
                    ImportField idUserPriceListField = new ImportField(findProperty("id[UserPriceList]"));
                    ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                            findProperty("item[VARSTRING[100]]").getMapping(idItemField));
                    itemKey.skipKey = true;
                    keys.add(itemKey);
                    ImportKey<?> userPriceListDetailKey = new ImportKey((CustomClass) findClass("UserPriceListDetail"),
                            importUserPriceListLM.findProperty("userPriceListDetailIdId[VARSTRING[100],VARSTRING[100]]").getMapping(idItemField, idUserPriceListField));
                    keys.add(userPriceListDetailKey);
                    ImportKey<?> userPriceListKey = new ImportKey((CustomClass) findClass("UserPriceList"),
                            findProperty("userPriceList[VARSTRING[100]]").getMapping(idUserPriceListField));
                    keys.add(userPriceListKey);
                    props.add(new ImportProperty(idItemField, findProperty("sku[UserPriceListDetail]").getMapping(userPriceListDetailKey),
                            LM.object(findClass("Item")).getMapping(itemKey)));
                    props.add(new ImportProperty(idUserPriceListField, findProperty("id[UserPriceList]").getMapping(userPriceListKey)));
                    props.add(new ImportProperty(idUserPriceListField, findProperty("userPriceList[UserPriceListDetail]").getMapping(userPriceListDetailKey),
                            LM.object(findClass("UserPriceList")).getMapping(userPriceListKey)));
                    fields.add(idItemField);
                    fields.add(idUserPriceListField);
                    for (int i = 0; i < priceListSuppliersList.size(); i++) {
                        data.get(i).add(priceListSuppliersList.get(i).idItem);
                        data.get(i).add(priceListSuppliersList.get(i).idPriceList);
                    }

                    ImportField idLegalEntityField = new ImportField(findProperty("id[LegalEntity]"));
                    ImportKey<?> legalEntityKey = new ImportKey((CustomClass) findClass("LegalEntity"),
                            findProperty("legalEntity[VARSTRING[100]]").getMapping(idLegalEntityField));
                    legalEntityKey.skipKey = skipKeys;
                    keys.add(legalEntityKey);
                    props.add(new ImportProperty(idLegalEntityField, findProperty("company[UserPriceList]").getMapping(userPriceListKey),
                            LM.object(findClass("LegalEntity")).getMapping(legalEntityKey)));
                    fields.add(idLegalEntityField);
                    for (int i = 0; i < priceListSuppliersList.size(); i++)
                        data.get(i).add(priceListSuppliersList.get(i).idSupplier);

                    ImportField shortNameCurrencyField = new ImportField(findProperty("shortName[Currency]"));
                    ImportKey<?> currencyKey = new ImportKey((CustomClass) findClass("Currency"),
                            findProperty("currencyShortName[STRING[3]]").getMapping(shortNameCurrencyField));
                    keys.add(currencyKey);
                    props.add(new ImportProperty(shortNameCurrencyField, findProperty("currency[UserPriceList]").getMapping(userPriceListKey),
                            LM.object(findClass("Currency")).getMapping(currencyKey)));
                    fields.add(shortNameCurrencyField);
                    for (int i = 0; i < priceListSuppliersList.size(); i++)
                        data.get(i).add(priceListSuppliersList.get(i).shortNameCurrency);

                    ImportField pricePriceListDetailField = new ImportField(findProperty("price[PriceListDetail,DataPriceListType]"));
                    props.add(new ImportProperty(pricePriceListDetailField, findProperty("price[UserPriceListDetail,DataPriceListType]").getMapping(userPriceListDetailKey, dataPriceListTypeObject)));
                    fields.add(pricePriceListDetailField);
                    for (int i = 0; i < priceListSuppliersList.size(); i++)
                        data.get(i).add(priceListSuppliersList.get(i).pricePriceListDetail);

                    ImportField inPriceListPriceListTypeField = new ImportField(findProperty("in[UserPriceList,DataPriceListType]"));
                    props.add(new ImportProperty(inPriceListPriceListTypeField, findProperty("in[UserPriceList,DataPriceListType]").getMapping(userPriceListKey, dataPriceListTypeObject)));
                    fields.add(inPriceListPriceListTypeField);
                    for (int i = 0; i < priceListSuppliersList.size(); i++)
                        data.get(i).add(true);

                    ImportField allStocksUserPriceListField = new ImportField(findProperty("allStocks[UserPriceList]"));
                    props.add(new ImportProperty(allStocksUserPriceListField, findProperty("allStocks[UserPriceList]").getMapping(userPriceListKey)));
                    fields.add(allStocksUserPriceListField);
                    for (int i = 0; i < priceListSuppliersList.size(); i++)
                        data.get(i).add(true);

                    ImportTable table = new ImportTable(fields, data);

                    IntegrationService service = new IntegrationService(newContext, table, keys, props);
                    service.synchronize(true, false);
                    newContext.apply();
                }
            }
        }
    }

    private void importLegalEntities(List<LegalEntity> legalEntitiesList) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (notNullNorEmpty(legalEntitiesList)) {

            ServerLoggers.importLogger.info("importLegalEntities");

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(legalEntitiesList.size());

            ImportField idLegalEntityField = new ImportField(findProperty("id[LegalEntity]"));
            ImportKey<?> legalEntityKey = new ImportKey((CustomClass) findClass("LegalEntity"),
                    findProperty("legalEntity[VARSTRING[100]]").getMapping(idLegalEntityField));
            keys.add(legalEntityKey);
            props.add(new ImportProperty(idLegalEntityField, findProperty("id[LegalEntity]").getMapping(legalEntityKey)));
            fields.add(idLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).idLegalEntity);

            ImportField numberAccountField = new ImportField(findProperty("number[Bank.Account]"));
            ImportKey<?> accountKey = new ImportKey((CustomClass) findClass("Bank.Account"),
                    findProperty("accountID[VARSTRING[20],VARSTRING[100]]").getMapping(numberAccountField, idLegalEntityField));
            keys.add(accountKey);
            props.add(new ImportProperty(numberAccountField, findProperty("number[Bank.Account]").getMapping(accountKey)));
            props.add(new ImportProperty(idLegalEntityField, findProperty("legalEntity[Bank.Account]").getMapping(accountKey),
                    LM.object(findClass("LegalEntity")).getMapping(legalEntityKey)));
            fields.add(numberAccountField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).numberAccount);

            ImportField nameLegalEntityField = new ImportField(findProperty("name[LegalEntity]"));
            props.add(new ImportProperty(nameLegalEntityField, findProperty("name[LegalEntity]").getMapping(legalEntityKey)));
            props.add(new ImportProperty(nameLegalEntityField, findProperty("fullName[LegalEntity]").getMapping(legalEntityKey)));
            fields.add(nameLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).nameLegalEntity);

            ImportField addressLegalEntityField = new ImportField(findProperty("address[LegalEntity]"));
            props.add(new ImportProperty(addressLegalEntityField, findProperty("dataAddress[LegalEntity,DATE]").getMapping(legalEntityKey, defaultDate)));
            fields.add(addressLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).addressLegalEntity);

            if(legalEntityByLM != null) {
                ImportField unpLegalEntityField = new ImportField(legalEntityByLM.findProperty("UNP[LegalEntity]"));
                props.add(new ImportProperty(unpLegalEntityField, legalEntityByLM.findProperty("UNP[LegalEntity]").getMapping(legalEntityKey)));
                fields.add(unpLegalEntityField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).unpLegalEntity);

                ImportField okpoLegalEntityField = new ImportField(legalEntityByLM.findProperty("OKPO[LegalEntity]"));
                props.add(new ImportProperty(okpoLegalEntityField, legalEntityByLM.findProperty("OKPO[LegalEntity]").getMapping(legalEntityKey)));
                fields.add(okpoLegalEntityField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).okpoLegalEntity);
            }
            
            ImportField phoneLegalEntityField = new ImportField(findProperty("dataPhone[LegalEntity,DATE]"));
            props.add(new ImportProperty(phoneLegalEntityField, findProperty("dataPhone[LegalEntity,DATE]").getMapping(legalEntityKey, defaultDate)));
            fields.add(phoneLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).phoneLegalEntity);

            ImportField emailLegalEntityField = new ImportField(findProperty("email[LegalEntity]"));
            props.add(new ImportProperty(emailLegalEntityField, findProperty("email[LegalEntity]").getMapping(legalEntityKey)));
            fields.add(emailLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).emailLegalEntity);

            ImportField isSupplierLegalEntityField = new ImportField(findProperty("isSupplier[LegalEntity]"));
            props.add(new ImportProperty(isSupplierLegalEntityField, findProperty("isSupplier[LegalEntity]").getMapping(legalEntityKey), true));
            fields.add(isSupplierLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).isSupplierLegalEntity);

            ImportField isCompanyLegalEntityField = new ImportField(findProperty("isCompany[LegalEntity]"));
            props.add(new ImportProperty(isCompanyLegalEntityField, findProperty("isCompany[LegalEntity]").getMapping(legalEntityKey), true));
            fields.add(isCompanyLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).isCompanyLegalEntity);

            ImportField isCustomerLegalEntityField = new ImportField(findProperty("isCustomer[LegalEntity]"));
            props.add(new ImportProperty(isCustomerLegalEntityField, findProperty("isCustomer[LegalEntity]").getMapping(legalEntityKey), true));
            fields.add(isCustomerLegalEntityField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).isCustomerLegalEntity);

            ImportField shortNameOwnershipField = new ImportField(findProperty("shortName[Ownership]"));
            ImportKey<?> ownershipKey = new ImportKey((CustomClass) findClass("Ownership"),
                    findProperty("ownershipShortName[STRING[10]]").getMapping(shortNameOwnershipField));
            keys.add(ownershipKey);
            props.add(new ImportProperty(shortNameOwnershipField, findProperty("shortName[Ownership]").getMapping(ownershipKey)));
            props.add(new ImportProperty(shortNameOwnershipField, findProperty("ownership[LegalEntity]").getMapping(legalEntityKey),
                    LM.object(findClass("Ownership")).getMapping(ownershipKey)));
            fields.add(shortNameOwnershipField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).shortNameOwnership);

            ImportField nameOwnershipField = new ImportField(findProperty("name[Ownership]"));
            props.add(new ImportProperty(nameOwnershipField, findProperty("name[Ownership]").getMapping(ownershipKey)));
            fields.add(nameOwnershipField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).nameOwnership);

            if (storeLM != null) {

                ImportField idChainStoresField = new ImportField(storeLM.findProperty("id[ChainStores]"));
                ImportKey<?> chainStoresKey = new ImportKey((CustomClass) storeLM.findClass("ChainStores"),
                        storeLM.findProperty("chainStores[VARSTRING[100]]").getMapping(idChainStoresField));
                keys.add(chainStoresKey);
                props.add(new ImportProperty(idChainStoresField, storeLM.findProperty("id[ChainStores]").getMapping(chainStoresKey)));
                fields.add(idChainStoresField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).idChainStores);

                ImportField nameChainStoresField = new ImportField(storeLM.findProperty("name[ChainStores]"));
                props.add(new ImportProperty(nameChainStoresField, storeLM.findProperty("name[ChainStores]").getMapping(chainStoresKey)));
                fields.add(nameChainStoresField);
                for (int i = 0; i < legalEntitiesList.size(); i++)
                    data.get(i).add(legalEntitiesList.get(i).nameChainStores);

            }

            ImportField idBankField = new ImportField(findProperty("id[Bank]"));
            ImportKey<?> bankKey = new ImportKey((CustomClass) findClass("Bank"),
                    findProperty("bank[VARSTRING[100]]").getMapping(idBankField));
            keys.add(bankKey);
            props.add(new ImportProperty(idBankField, findProperty("id[Bank]").getMapping(bankKey)));
            props.add(new ImportProperty(idBankField, findProperty("name[Bank]").getMapping(bankKey), true));
            props.add(new ImportProperty(idBankField, findProperty("bank[Bank.Account]").getMapping(accountKey),
                    LM.object(findClass("Bank")).getMapping(bankKey)));
            fields.add(idBankField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).idBank);

            ImportField nameCountryField = new ImportField(findProperty("name[Country]"));
            ImportKey<?> countryKey = new ImportKey((CustomClass) findClass("Country"),
                    findProperty("countryName[VARISTRING[50]]").getMapping(nameCountryField));
            keys.add(countryKey);
            props.add(new ImportProperty(nameCountryField, findProperty("name[Country]").getMapping(countryKey)));
            props.add(new ImportProperty(nameCountryField, findProperty("country[LegalEntity]").getMapping(legalEntityKey),
                    LM.object(findClass("Country")).getMapping(countryKey)));
            props.add(new ImportProperty(nameCountryField, findProperty("country[Ownership]").getMapping(ownershipKey),
                    LM.object(findClass("Country")).getMapping(countryKey)));
            fields.add(nameCountryField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).nameCountry);

            ImportField shortNameCurrencyField = new ImportField(findProperty("shortName[Currency]"));
            ImportKey<?> currencyKey = new ImportKey((CustomClass) findClass("Currency"),
                    findProperty("currencyShortName[STRING[3]]").getMapping(shortNameCurrencyField));
            keys.add(currencyKey);
            props.add(new ImportProperty(shortNameCurrencyField, findProperty("currency[Bank.Account]").getMapping(accountKey),
                    LM.object(findClass("Currency")).getMapping(currencyKey)));
            fields.add(shortNameCurrencyField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add("BYN");

            ImportField idLegalEntityGroupField = new ImportField(findProperty("id[LegalEntityGroup]"));
            ImportKey<?> legalEntityGroupKey = new ImportKey((CustomClass) findClass("LegalEntityGroup"),
                    findProperty("legalEntityGroup[VARSTRING[100]]").getMapping(idLegalEntityGroupField));
            keys.add(legalEntityGroupKey);
            props.add(new ImportProperty(idLegalEntityGroupField, findProperty("id[LegalEntityGroup]").getMapping(legalEntityGroupKey)));
            props.add(new ImportProperty(idLegalEntityGroupField, findProperty("name[LegalEntityGroup]").getMapping(legalEntityGroupKey)));
            props.add(new ImportProperty(idLegalEntityGroupField, findProperty("legalEntityGroup[LegalEntity]").getMapping(legalEntityKey),
                    object(findClass("LegalEntityGroup")).getMapping(legalEntityGroupKey), true));
            fields.add(idLegalEntityGroupField);
            for (int i = 0; i < legalEntitiesList.size(); i++)
                data.get(i).add(legalEntitiesList.get(i).idLegalEntityGroup);
            
            ImportTable table = new ImportTable(fields, data);

            try(ExecutionContext.NewSession newContext = context.newSession()) {
                IntegrationService service = new IntegrationService(newContext, table, keys, props);
                service.synchronize(true, false);
                newContext.apply();
            }
        }
    }

    private void importEmployees(List<Employee> employeesList) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (notNullNorEmpty(employeesList)) {

            ServerLoggers.importLogger.info("importEmployees");

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(employeesList.size());

            ImportField idEmployeeField = new ImportField(findProperty("id[Employee]"));
            ImportKey<?> employeeKey = new ImportKey((CustomClass) findClass("Employee"),
                    findProperty("employee[VARSTRING[100]]").getMapping(idEmployeeField));
            keys.add(employeeKey);
            props.add(new ImportProperty(idEmployeeField, findProperty("id[Employee]").getMapping(employeeKey)));
            fields.add(idEmployeeField);
            for (int i = 0; i < employeesList.size(); i++)
                data.get(i).add(employeesList.get(i).idEmployee);

            ImportField firstNameEmployeeField = new ImportField(findProperty("firstName[Contact]"));
            props.add(new ImportProperty(firstNameEmployeeField, findProperty("firstName[Contact]").getMapping(employeeKey)));
            fields.add(firstNameEmployeeField);
            for (int i = 0; i < employeesList.size(); i++)
                data.get(i).add(employeesList.get(i).firstNameEmployee);

            ImportField lastNameEmployeeField = new ImportField(findProperty("lastName[Contact]"));
            props.add(new ImportProperty(lastNameEmployeeField, findProperty("lastName[Contact]").getMapping(employeeKey)));
            fields.add(lastNameEmployeeField);
            for (int i = 0; i < employeesList.size(); i++)
                data.get(i).add(employeesList.get(i).lastNameEmployee);

            ImportField idPositionField = new ImportField(findProperty("id[Position]"));
            ImportKey<?> positionKey = new ImportKey((CustomClass) findClass("Position"),
                    findProperty("position[VARSTRING[100]]").getMapping(idPositionField));
            keys.add(positionKey);
            props.add(new ImportProperty(idPositionField, findProperty("id[Position]").getMapping(positionKey)));
            props.add(new ImportProperty(idPositionField, findProperty("position[Employee]").getMapping(employeeKey),
                    LM.object(findClass("Position")).getMapping(positionKey)));
            fields.add(idPositionField);
            for (int i = 0; i < employeesList.size(); i++)
                data.get(i).add(employeesList.get(i).idPosition);

            ImportField namePositionField = new ImportField(findProperty("name[Position]"));
            props.add(new ImportProperty(namePositionField, findProperty("name[Position]").getMapping(positionKey)));
            fields.add(namePositionField);
            for (int i = 0; i < employeesList.size(); i++)
                data.get(i).add(employeesList.get(i).idPosition);

            ImportTable table = new ImportTable(fields, data);

            try(ExecutionContext.NewSession newContext = context.newSession()) {
                IntegrationService service = new IntegrationService(newContext, table, keys, props);
                service.synchronize(true, false);
                newContext.apply();
            }
        }
    }

    private void importWarehouseGroups(List<WarehouseGroup> warehouseGroupsList) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (notNullNorEmpty(warehouseGroupsList)) {

            ServerLoggers.importLogger.info("importWarehouseGroups");

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(warehouseGroupsList.size());

            ImportField idWarehouseGroupField = new ImportField(findProperty("id[WarehouseGroup]"));
            ImportKey<?> warehouseGroupKey = new ImportKey((CustomClass) findClass("WarehouseGroup"),
                    findProperty("warehouseGroup[VARSTRING[100]]").getMapping(idWarehouseGroupField));
            keys.add(warehouseGroupKey);
            props.add(new ImportProperty(idWarehouseGroupField, findProperty("id[WarehouseGroup]").getMapping(warehouseGroupKey)));
            fields.add(idWarehouseGroupField);
            for (int i = 0; i < warehouseGroupsList.size(); i++)
                data.get(i).add(warehouseGroupsList.get(i).idWarehouseGroup);

            ImportField nameWarehouseGroupField = new ImportField(findProperty("name[WarehouseGroup]"));
            props.add(new ImportProperty(nameWarehouseGroupField, findProperty("name[WarehouseGroup]").getMapping(warehouseGroupKey)));
            fields.add(nameWarehouseGroupField);
            for (int i = 0; i < warehouseGroupsList.size(); i++)
                data.get(i).add(warehouseGroupsList.get(i).nameWarehouseGroup);

            ImportTable table = new ImportTable(fields, data);

            try(ExecutionContext.NewSession newContext = context.newSession()) {
                IntegrationService service = new IntegrationService(newContext, table, keys, props);
                service.synchronize(true, false);
                newContext.apply();
            }
        }
    }

    private void importWarehouses(List<Warehouse> warehousesList) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (notNullNorEmpty(warehousesList)) {

            ServerLoggers.importLogger.info("importWarehouses");

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(warehousesList.size());

            ImportField idWarehouseField = new ImportField(findProperty("id[Warehouse]"));
            ImportKey<?> warehouseKey = new ImportKey((CustomClass) findClass("Warehouse"),
                    findProperty("warehouse[VARSTRING[100]]").getMapping(idWarehouseField));
            keys.add(warehouseKey);
            props.add(new ImportProperty(idWarehouseField, findProperty("id[Warehouse]").getMapping(warehouseKey)));
            fields.add(idWarehouseField);
            for (int i = 0; i < warehousesList.size(); i++)
                data.get(i).add(warehousesList.get(i).idWarehouse);

            ImportField nameWarehouseField = new ImportField(findProperty("name[Warehouse]"));
            props.add(new ImportProperty(nameWarehouseField, findProperty("name[Warehouse]").getMapping(warehouseKey)));
            fields.add(nameWarehouseField);
            for (int i = 0; i < warehousesList.size(); i++)
                data.get(i).add(warehousesList.get(i).nameWarehouse);

            ImportField addressWarehouseField = new ImportField(findProperty("address[Warehouse]"));
            props.add(new ImportProperty(addressWarehouseField, findProperty("address[Warehouse]").getMapping(warehouseKey)));
            fields.add(addressWarehouseField);
            for (int i = 0; i < warehousesList.size(); i++)
                data.get(i).add(warehousesList.get(i).addressWarehouse);

            ImportField idLegalEntityField = new ImportField(findProperty("id[LegalEntity]"));
            ImportKey<?> legalEntityKey = new ImportKey((CustomClass) findClass("LegalEntity"),
                    findProperty("legalEntity[VARSTRING[100]]").getMapping(idLegalEntityField));
            keys.add(legalEntityKey);
            props.add(new ImportProperty(idLegalEntityField, findProperty("id[LegalEntity]").getMapping(legalEntityKey)));
            props.add(new ImportProperty(idLegalEntityField, findProperty("legalEntity[Warehouse]").getMapping(warehouseKey),
                    LM.object(findClass("LegalEntity")).getMapping(legalEntityKey)));
            fields.add(idLegalEntityField);
            for (int i = 0; i < warehousesList.size(); i++)
                data.get(i).add(warehousesList.get(i).idLegalEntity);

            ImportField idWarehouseGroupField = new ImportField(findProperty("id[WarehouseGroup]"));
            ImportKey<?> warehouseGroupKey = new ImportKey((CustomClass) findClass("WarehouseGroup"),
                    findProperty("warehouseGroup[VARSTRING[100]]").getMapping(idWarehouseGroupField));
            keys.add(warehouseGroupKey);
            props.add(new ImportProperty(idWarehouseGroupField, findProperty("warehouseGroup[Warehouse]").getMapping(warehouseKey),
                    LM.object(findClass("WarehouseGroup")).getMapping(warehouseGroupKey)));
            fields.add(idWarehouseGroupField);
            for (int i = 0; i < warehousesList.size(); i++)
                data.get(i).add(warehousesList.get(i).idWarehouseGroup);

            ImportTable table = new ImportTable(fields, data);

            try(ExecutionContext.NewSession newContext = context.newSession()) {
                IntegrationService service = new IntegrationService(newContext, table, keys, props);
                service.synchronize(true, false);
                newContext.apply();
            }
        }
    }

    private void importStores(List<LegalEntity> storesList, boolean skipKeys) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (storeLM != null && notNullNorEmpty(storesList)) {

            ServerLoggers.importLogger.info("importStores");

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(storesList.size());

            ImportField idStoreField = new ImportField(storeLM.findProperty("id[Store]"));
            ImportKey<?> storeKey = new ImportKey((CustomClass) storeLM.findClass("Store"),
                    storeLM.findProperty("store[VARSTRING[100]]").getMapping(idStoreField));
            keys.add(storeKey);
            props.add(new ImportProperty(idStoreField, storeLM.findProperty("id[Store]").getMapping(storeKey)));
            fields.add(idStoreField);
            for (int i = 0; i < storesList.size(); i++)
                data.get(i).add(((Store) storesList.get(i)).idStore);

            ImportField nameStoreField = new ImportField(storeLM.findProperty("name[Store]"));
            props.add(new ImportProperty(nameStoreField, storeLM.findProperty("name[Store]").getMapping(storeKey)));
            fields.add(nameStoreField);
            for (int i = 0; i < storesList.size(); i++)
                data.get(i).add(storesList.get(i).nameLegalEntity);

            ImportField addressStoreField = new ImportField(storeLM.findProperty("address[Store]"));
            props.add(new ImportProperty(addressStoreField, storeLM.findProperty("address[Store]").getMapping(storeKey)));
            fields.add(addressStoreField);
            for (int i = 0; i < storesList.size(); i++)
                data.get(i).add(storesList.get(i).addressLegalEntity);

            ImportField idLegalEntityField = new ImportField(findProperty("id[LegalEntity]"));
            ImportKey<?> legalEntityKey = new ImportKey((CustomClass) findClass("LegalEntity"),
                    findProperty("legalEntity[VARSTRING[100]]").getMapping(idLegalEntityField));
            legalEntityKey.skipKey = skipKeys;
            keys.add(legalEntityKey);
            props.add(new ImportProperty(idLegalEntityField, storeLM.findProperty("legalEntity[Store]").getMapping(storeKey),
                    LM.object(findClass("LegalEntity")).getMapping(legalEntityKey)));
            fields.add(idLegalEntityField);
            for (int i = 0; i < storesList.size(); i++)
                data.get(i).add(storesList.get(i).idLegalEntity);

            ImportField idChainStoresField = new ImportField(storeLM.findProperty("id[ChainStores]"));
            ImportKey<?> chainStoresKey = new ImportKey((CustomClass) storeLM.findClass("ChainStores"),
                    storeLM.findProperty("chainStores[VARSTRING[100]]").getMapping(idChainStoresField));
            keys.add(chainStoresKey);
            fields.add(idChainStoresField);
            for (int i = 0; i < storesList.size(); i++)
                data.get(i).add(storesList.get(i).idChainStores);

            ImportField storeTypeField = new ImportField(storeLM.findProperty("name[StoreType]"));
            ImportKey<?> storeTypeKey = new ImportKey((CustomClass) storeLM.findClass("StoreType"),
                    storeLM.findProperty("storeType[VARISTRING[100],VARSTRING[100]]").getMapping(storeTypeField, idChainStoresField));
            keys.add(storeTypeKey);
            props.add(new ImportProperty(idChainStoresField, storeLM.findProperty("chainStores[StoreType]").getMapping(storeTypeKey),
                    storeLM.object(storeLM.findClass("ChainStores")).getMapping(chainStoresKey)));
            props.add(new ImportProperty(storeTypeField, storeLM.findProperty("name[StoreType]").getMapping(storeTypeKey)));
            props.add(new ImportProperty(storeTypeField, storeLM.findProperty("storeType[Store]").getMapping(storeKey),
                    storeLM.object(storeLM.findClass("StoreType")).getMapping(storeTypeKey)));
            fields.add(storeTypeField);
            for (int i = 0; i < storesList.size(); i++)
                data.get(i).add(((Store) storesList.get(i)).storeType);

            ImportTable table = new ImportTable(fields, data);

            try(ExecutionContext.NewSession newContext = context.newSession()) {
                IntegrationService service = new IntegrationService(newContext, table, keys, props);
                service.synchronize(true, false);
                newContext.apply();
            }
        }
    }

    private void importDepartmentStores(List<DepartmentStore> departmentStoresList) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (storeLM != null && notNullNorEmpty(departmentStoresList)) {

            ServerLoggers.importLogger.info("importDepartmentStores");

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(departmentStoresList.size());

            ImportField idDepartmentStoreField = new ImportField(storeLM.findProperty("id[DepartmentStore]"));
            ImportKey<?> departmentStoreKey = new ImportKey((CustomClass) storeLM.findClass("DepartmentStore"),
                    storeLM.findProperty("departmentStore[VARSTRING[100]]").getMapping(idDepartmentStoreField));
            keys.add(departmentStoreKey);
            props.add(new ImportProperty(idDepartmentStoreField, storeLM.findProperty("id[DepartmentStore]").getMapping(departmentStoreKey)));
            fields.add(idDepartmentStoreField);
            for (int i = 0; i < departmentStoresList.size(); i++)
                data.get(i).add((departmentStoresList.get(i)).idDepartmentStore);

            ImportField nameDepartmentStoreField = new ImportField(storeLM.findProperty("name[DepartmentStore]"));
            props.add(new ImportProperty(nameDepartmentStoreField, storeLM.findProperty("name[DepartmentStore]").getMapping(departmentStoreKey)));
            fields.add(nameDepartmentStoreField);
            for (int i = 0; i < departmentStoresList.size(); i++)
                data.get(i).add((departmentStoresList.get(i)).nameDepartmentStore);

            ImportField idStoreField = new ImportField(storeLM.findProperty("id[Store]"));
            ImportKey<?> storeKey = new ImportKey((CustomClass) storeLM.findClass("Store"),
                    storeLM.findProperty("store[VARSTRING[100]]").getMapping(idStoreField));
            keys.add(storeKey);
            props.add(new ImportProperty(idStoreField, storeLM.findProperty("store[DepartmentStore]").getMapping(departmentStoreKey),
                    storeLM.object(storeLM.findClass("Store")).getMapping(storeKey)));
            fields.add(idStoreField);
            for (int i = 0; i < departmentStoresList.size(); i++)
                data.get(i).add((departmentStoresList.get(i)).idStore);

            ImportTable table = new ImportTable(fields, data);

            try(ExecutionContext.NewSession newContext = context.newSession()) {
                IntegrationService service = new IntegrationService(newContext, table, keys, props);
                service.synchronize(true, false);
                newContext.apply();
            }
        }
    }

    private void importBanks(List<Bank> banksList) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (notNullNorEmpty(banksList)) {

            ServerLoggers.importLogger.info("importBanks");

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(banksList.size());

            ImportField idBankField = new ImportField(findProperty("id[Bank]"));
            ImportKey<?> bankKey = new ImportKey((CustomClass) findClass("Bank"),
                    findProperty("bank[VARSTRING[100]]").getMapping(idBankField));
            keys.add(bankKey);
            props.add(new ImportProperty(idBankField, findProperty("id[Bank]").getMapping(bankKey)));
            fields.add(idBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).idBank);

            ImportField nameBankField = new ImportField(findProperty("name[Bank]"));
            props.add(new ImportProperty(nameBankField, findProperty("name[Bank]").getMapping(bankKey)));
            fields.add(nameBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).nameBank);

            ImportField addressBankField = new ImportField(findProperty("dataAddress[Bank,DATE]"));
            props.add(new ImportProperty(addressBankField, findProperty("dataAddress[Bank,DATE]").getMapping(bankKey, defaultDate)));
            fields.add(addressBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).addressBank);

            ImportField departmentBankField = new ImportField(findProperty("department[Bank]"));
            props.add(new ImportProperty(departmentBankField, findProperty("department[Bank]").getMapping(bankKey)));
            fields.add(departmentBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).departmentBank);

            ImportField mfoBankField = new ImportField(findProperty("MFO[Bank]"));
            props.add(new ImportProperty(mfoBankField, findProperty("MFO[Bank]").getMapping(bankKey)));
            fields.add(mfoBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).mfoBank);

            ImportField cbuBankField = new ImportField(findProperty("CBU[Bank]"));
            props.add(new ImportProperty(cbuBankField, findProperty("CBU[Bank]").getMapping(bankKey)));
            fields.add(cbuBankField);
            for (int i = 0; i < banksList.size(); i++)
                data.get(i).add(banksList.get(i).cbuBank);

            ImportTable table = new ImportTable(fields, data);

            try(ExecutionContext.NewSession newContext = context.newSession()) {
                IntegrationService service = new IntegrationService(newContext, table, keys, props);
                service.synchronize(true, false);
                newContext.apply();
            }
        }
    }

    private void importRateWastes(List<RateWaste> rateWastesList) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (writeOffItemLM != null && notNullNorEmpty(rateWastesList)) {

            ServerLoggers.importLogger.info("importRateWastes");

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(rateWastesList.size());

            ImportField idWriteOffRateField = new ImportField(writeOffItemLM.findProperty("id[WriteOffRate]"));
            ImportKey<?> writeOffRateKey = new ImportKey((CustomClass) writeOffItemLM.findClass("WriteOffRate"),
                    writeOffItemLM.findProperty("writeOffRate[VARSTRING[100]]").getMapping(idWriteOffRateField));
            keys.add(writeOffRateKey);
            props.add(new ImportProperty(idWriteOffRateField, writeOffItemLM.findProperty("id[WriteOffRate]").getMapping(writeOffRateKey)));
            fields.add(idWriteOffRateField);
            for (int i = 0; i < rateWastesList.size(); i++)
                data.get(i).add(rateWastesList.get(i).idRateWaste);

            ImportField nameWriteOffRateField = new ImportField(writeOffItemLM.findProperty("name[WriteOffRate]"));
            props.add(new ImportProperty(nameWriteOffRateField, writeOffItemLM.findProperty("name[WriteOffRate]").getMapping(writeOffRateKey)));
            fields.add(nameWriteOffRateField);
            for (int i = 0; i < rateWastesList.size(); i++)
                data.get(i).add(rateWastesList.get(i).nameRateWaste);

            ImportField percentWriteOffRateField = new ImportField(writeOffItemLM.findProperty("percent[WriteOffRate]"));
            props.add(new ImportProperty(percentWriteOffRateField, writeOffItemLM.findProperty("percent[WriteOffRate]").getMapping(writeOffRateKey)));
            fields.add(percentWriteOffRateField);
            for (int i = 0; i < rateWastesList.size(); i++)
                data.get(i).add(rateWastesList.get(i).percentWriteOffRate);

            ImportField nameCountryField = new ImportField(findProperty("name[Country]"));
            ImportKey<?> countryKey = new ImportKey((CustomClass) findClass("Country"),
                    findProperty("countryName[VARISTRING[50]]").getMapping(nameCountryField));
            keys.add(countryKey);
            props.add(new ImportProperty(nameCountryField, writeOffItemLM.findProperty("country[WriteOffRate]").getMapping(writeOffRateKey),
                    LM.object(findClass("Country")).getMapping(countryKey)));
            fields.add(nameCountryField);
            for (int i = 0; i < rateWastesList.size(); i++)
                data.get(i).add(rateWastesList.get(i).nameCountry);

            ImportTable table = new ImportTable(fields, data);

            try(ExecutionContext.NewSession newContext = context.newSession()) {
                IntegrationService service = new IntegrationService(newContext, table, keys, props);
                service.synchronize(true, false);
                newContext.apply();
            }
        }
    }

    private void importContracts(List<Contract> contractsList, boolean skipKeys) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (notNullNorEmpty(contractsList)) {

            ServerLoggers.importLogger.info("importContacts");

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(contractsList.size());

            ImportField idUserContractSkuField = new ImportField(findProperty("id[UserContractSku]"));
            ImportKey<?> userContractSkuKey = new ImportKey((CustomClass) findClass("UserContractSku"),
                    findProperty("userContractSku[VARSTRING[100]]").getMapping(idUserContractSkuField));
            keys.add(userContractSkuKey);
            props.add(new ImportProperty(idUserContractSkuField, findProperty("id[UserContractSku]").getMapping(userContractSkuKey)));
            fields.add(idUserContractSkuField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).idUserContractSku);

            ImportField numberContractField = new ImportField(findProperty("number[Contract]"));
            props.add(new ImportProperty(numberContractField, findProperty("number[Contract]").getMapping(userContractSkuKey)));
            fields.add(numberContractField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).numberContract);

            ImportField dateFromContractField = new ImportField(findProperty("dateFrom[Contract]"));
            props.add(new ImportProperty(dateFromContractField, findProperty("dateFrom[Contract]").getMapping(userContractSkuKey)));
            fields.add(dateFromContractField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).dateFromContract);

            ImportField dateToContractField = new ImportField(findProperty("dateTo[Contract]"));
            props.add(new ImportProperty(dateToContractField, findProperty("dateTo[Contract]").getMapping(userContractSkuKey)));
            fields.add(dateToContractField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).dateToContract);

            ImportField idSupplierField = new ImportField(findProperty("id[LegalEntity]"));
            ImportKey<?> supplierKey = new ImportKey((CustomClass) findClass("LegalEntity"),
                    findProperty("legalEntity[VARSTRING[100]]").getMapping(idSupplierField));
            supplierKey.skipKey = skipKeys;
            keys.add(supplierKey);
            props.add(new ImportProperty(idSupplierField, findProperty("supplier[ContractSku]").getMapping(userContractSkuKey),
                    LM.object(findClass("LegalEntity")).getMapping(supplierKey)));
            fields.add(idSupplierField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).idSupplier);

            ImportField idCustomerField = new ImportField(findProperty("id[LegalEntity]"));
            ImportKey<?> customerKey = new ImportKey((CustomClass) findClass("LegalEntity"),
                    findProperty("legalEntity[VARSTRING[100]]").getMapping(idCustomerField));
            customerKey.skipKey = skipKeys;
            keys.add(customerKey);
            props.add(new ImportProperty(idCustomerField, findProperty("customer[ContractSku]").getMapping(userContractSkuKey),
                    LM.object(findClass("LegalEntity")).getMapping(customerKey)));
            fields.add(idCustomerField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).idCustomer);

            ImportField shortNameCurrencyField = new ImportField(findProperty("shortName[Currency]"));
            ImportKey<?> currencyKey = new ImportKey((CustomClass) findClass("Currency"),
                    findProperty("currencyShortName[STRING[3]]").getMapping(shortNameCurrencyField));
            keys.add(currencyKey);
            props.add(new ImportProperty(shortNameCurrencyField, findProperty("currency[Contract]").getMapping(userContractSkuKey),
                    LM.object(findClass("Currency")).getMapping(currencyKey)));
            fields.add(shortNameCurrencyField);
            for (int i = 0; i < contractsList.size(); i++)
                data.get(i).add(contractsList.get(i).shortNameCurrency);

            ImportTable table = new ImportTable(fields, data);

            try(ExecutionContext.NewSession newContext = context.newSession()) {
                IntegrationService service = new IntegrationService(newContext, table, keys, props);
                service.synchronize(true, false);
                newContext.apply();
            }
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
        } catch (NoSuchFieldException | IllegalAccessException e) {
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
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return true;
        }
        return false;
    }
}