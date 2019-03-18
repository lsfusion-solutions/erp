package lsfusion.erp.region.by.integration.lstrade;

import lsfusion.erp.integration.*;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;

public class ImportLSTradeActionProperty extends DefaultImportDBFActionProperty {

    String charset = "Cp1251";
    
    public ImportLSTradeActionProperty(ScriptingLogicsModule LM) {
        super(LM);

        try {
            Class.forName("sun.jdbc.odbc.JdbcOdbcDriver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            Integer numberOfItems = (Integer) findProperty("importNumberItems[]").read(context);
            Integer numberOfPriceLists = (Integer) findProperty("importNumberPriceLists[]").read(context);
            String prefixStore = trim((String) findProperty("prefixStore[]").read(context), "МГ");

            String path = trim((String) findProperty("importLSTDirectory[]").read(context));
            if (notNullNorEmpty(path)) {

                ImportData importData = new ImportData();

                Boolean importInactive = findProperty("importInactive[]").read(context) != null;
                importData.setImportInactive(importInactive);
                importData.setSkipKeys(findProperty("skipKeysLSTrade[]").read(context) != null);

                importData.setNumberOfItemsAtATime((Integer) findProperty("importNumberItemsAtATime[]").read(context));
                importData.setNumberOfPriceListsAtATime((Integer) findProperty("importNumberPriceListsAtATime[]").read(context));

                importData.setItemGroupsList((findProperty("importGroupItems[]").read(context) != null) ?
                        importItemGroupsFromDBF(path + "//_sprgrt.dbf", false) : null);

                importData.setParentGroupsList((findProperty("importGroupItems[]").read(context) != null) ?
                        importItemGroupsFromDBF(path + "//_sprgrt.dbf", true) : null);

                importData.setBanksList((findProperty("importBanks[]").read(context) != null) ?
                        importBanksFromDBF(path + "//_sprbank.dbf") : null);

                importData.setLegalEntitiesList((findProperty("importLegalEntities[]").read(context) != null) ?
                        importLegalEntitiesFromDBF(path + "//_sprana.dbf", prefixStore, importInactive, false) : null);

                importData.setWarehousesList((findProperty("importWarehouses[]").read(context) != null) ?
                        importWarehousesFromDBF(path + "//_sprana.dbf", importInactive) : null);

                importData.setContractsList((findProperty("importContracts[]").read(context) != null) ?
                        importContractsFromDBF(path + "//_sprcont.dbf") : null);

                importData.setStoresList((findProperty("importStores[]").read(context) != null) ?
                        importLegalEntitiesFromDBF(path + "//_sprana.dbf", prefixStore, importInactive, true) : null);

                importData.setDepartmentStoresList((findProperty("importDepartmentStores[]").read(context) != null) ?
                        importDepartmentStoresFromDBF(path + "//_sprana.dbf", importInactive, path + "//_storestr.dbf",
                                prefixStore) : null);

                importData.setRateWastesList((findProperty("importRateWastes[]").read(context) != null) ?
                        importRateWastesFromDBF(path + "//_sprvgrt.dbf") : null);

                importData.setWaresList((findProperty("importWares[]").read(context) != null) ?
                        importWaresFromDBF(path + "//_sprgrm.dbf") : null);

                importData.setUOMsList((findProperty("importUOMs[]").read(context) != null) ?
                        importUOMsFromDBF(path + "//_sprgrm.dbf") : null);

                importData.setItemsList((findProperty("importItems[]").read(context) != null) ?
                        importItemsFromDBF(path + "//_sprgrm.dbf", path + "//_postvar.dbf", numberOfItems, importInactive) : null);

                importData.setPriceListStoresList((findProperty("importPriceListStores[]").read(context) != null) ?
                        importPriceListStoreFromDBF(path + "//_postvar.dbf", path + "//_strvar.dbf", prefixStore, numberOfPriceLists) : null);

                importData.setPriceListSuppliersList((findProperty("importPriceListSuppliers[]").read(context) != null) ?
                        importPriceListSuppliersFromDBF(path + "//_postvar.dbf", numberOfPriceLists) : null);

                importData.setUserInvoicesList((findProperty("importUserInvoices[]").read(context) != null) ?
                        importUserInvoicesFromDBF(path + "//_sprcont.dbf", path + "//_ostn.dbf") : null);

                new ImportActionProperty(LM).makeImport(importData, context);
            }
        } catch (ScriptingErrorLog.SemanticErrorException | ParseException | IOException | xBaseJException e) {
            throw new RuntimeException(e);
        }
    }

    private List<ItemGroup> data;
    private List<String> itemGroups;

    private List<ItemGroup> importItemGroupsFromDBF(String path, Boolean parents) throws IOException, xBaseJException {

        checkFileExistence(path);

        DBF importFile = new DBF(path);
        int recordCount = importFile.getRecordCount();

        data = new ArrayList<>();
        itemGroups = new ArrayList<>();

        String groupTop = "ВСЕ";
        if (!parents)
            addIfNotContains(new ItemGroup(groupTop, groupTop, null));
        else
            addIfNotContains(new ItemGroup(groupTop, null, null));

        for (int i = 0; i < recordCount; i++) {

            importFile.read();

            String k_grtov = getDBFFieldValue(importFile, "K_GRTOV", charset, "");
            String pol_naim = getDBFFieldValue(importFile, "POL_NAIM", charset, "");
            String group1 = getDBFFieldValue(importFile, "GROUP1", charset, "");
            String group2 = getDBFFieldValue(importFile, "GROUP2", charset, "");
            String group3 = getDBFFieldValue(importFile, "GROUP3", charset, "");

            if (!pol_naim.isEmpty()) {

                if (!group2.isEmpty() && (!group3.isEmpty())) {

                    if (!parents) {
                        //id - name - idParent(null)
                        addIfNotContains(new ItemGroup((group3.substring(0, 3) + "/" + groupTop), group3, null));
                        addIfNotContains(new ItemGroup((group2 + "/" + group3.substring(0, 3) + "/" + groupTop), group2, null));
                        addIfNotContains(new ItemGroup((group1 + "/" + group2.substring(0, 3) + "/" + group3.substring(0, 3) + "/" + groupTop), group1, null));
                        addIfNotContains(new ItemGroup(k_grtov, pol_naim, null));
                    } else {
                        //id - name(null) - idParent
                        addIfNotContains(new ItemGroup((group3.substring(0, 3) + "/" + groupTop), null, groupTop));
                        addIfNotContains(new ItemGroup((group2 + "/" + group3.substring(0, 3) + "/" + groupTop), null, group3.substring(0, 3) + "/" + groupTop));
                        addIfNotContains(new ItemGroup((group1 + "/" + group2.substring(0, 3) + "/" + group3.substring(0, 3) + "/" + groupTop), null, group2 + "/" + group3.substring(0, 3) + "/" + groupTop));
                        addIfNotContains(new ItemGroup(k_grtov, null, group1 + "/" + group2.substring(0, 3) + "/" + group3.substring(0, 3) + "/" + groupTop));
                    }

                } else {
                    if (k_grtov.endsWith("."))
                        k_grtov = k_grtov.substring(0, k_grtov.length() - 1);

                    int dotCount = 0;
                    for (char c : k_grtov.toCharArray())
                        if (c == '.')
                            dotCount++;

                    if (!parents) {
                        //id - name - idParent(null)
                        addIfNotContains(new ItemGroup(k_grtov, pol_naim, null));
                        if (dotCount == 1)
                            addIfNotContains(new ItemGroup(group1, group1, null));

                    } else {
                        //id - name(null) - idParent
                        addIfNotContains(new ItemGroup(k_grtov, null, group1));
                        if (dotCount == 1)
                            addIfNotContains(new ItemGroup(group1, null, groupTop));
                    }
                }
            }
        }
        return data;
    }

    private List<Ware> importWaresFromDBF(String path) throws IOException, xBaseJException {

        checkFileExistence(path);

        DBF importFile = new DBF(path);
        int recordCount = importFile.getRecordCount();
        List<Ware> data = new ArrayList<>();

        for (int i = 0; i < recordCount; i++) {
            importFile.read();

            Boolean isWare = getDBFBooleanFieldValue(importFile, "LGRMSEC", charset, false);
            String idWare = getDBFFieldValue(importFile, "K_GRMAT", charset);
            String nameWare = getDBFFieldValue(importFile, "POL_NAIM", charset);
            BigDecimal priceWare = getDBFBigDecimalFieldValue(importFile, "CENUOSEC", charset);

            if (!idWare.isEmpty() && isWare)
                data.add(new Ware(idWare, nameWare, priceWare));
        }
        return data;
    }

    private List<UOM> importUOMsFromDBF(String itemsPath) throws IOException, xBaseJException, ParseException {

        checkFileExistence(itemsPath);

        DBF itemsImportFile = new DBF(itemsPath);
        int recordCount = itemsImportFile.getRecordCount();
        if (recordCount <= 0) {
            return null;
        }

        List<UOM> data = new ArrayList<>();

        for (int i = 0; i < recordCount; i++) {
            itemsImportFile.read();

            String UOM = getDBFFieldValue(itemsImportFile, "K_IZM", charset);
            Boolean isWare = getDBFBooleanFieldValue(itemsImportFile, "LGRMSEC", charset, false);

            if (!isWare)
                data.add(new UOM(UOM, UOM, UOM));
        }
        return data;
    }

    private List<Item> importItemsFromDBF(String itemsPath, String quantityPath, Integer numberOfItems, Boolean importInactive) throws IOException, xBaseJException, ParseException {

        checkFileExistence(itemsPath);
        checkFileExistence(quantityPath);

        Set<String> barcodes = new HashSet<>();

        DBF quantityImportFile = new DBF(quantityPath);
        int totalRecordCount = quantityImportFile.getRecordCount();

        Map<String, BigDecimal> quantities = new HashMap<>();

        for (int i = 0; i < totalRecordCount; i++) {
            quantityImportFile.read();

            String idItem = getDBFFieldValue(quantityImportFile, "K_GRMAT", charset);
            BigDecimal quantityPackItem = getDBFBigDecimalFieldValue(quantityImportFile, "PACKSIZE", charset);

            if (quantityPackItem.equals(BigDecimal.ZERO))
                quantityPackItem = BigDecimal.ONE;
            if (!quantities.containsKey(idItem)) {
                quantities.put(idItem, quantityPackItem);
            }
        }

        DBF itemsImportFile = new DBF(itemsPath);
        totalRecordCount = itemsImportFile.getRecordCount();
        if (totalRecordCount <= 0) {
            return null;
        }

        List<Item> data = new ArrayList<>();

        int recordCount = (numberOfItems != null && numberOfItems != 0 && numberOfItems < totalRecordCount) ? numberOfItems : totalRecordCount;
        for (int i = 0; i < recordCount; i++) {
            itemsImportFile.read();
            String barcode = getDBFFieldValue(itemsImportFile, "K_GRUP", charset);
            int counter = 1;
            if (barcodes.contains(barcode)) {
                while (barcodes.contains(barcode + "_" + counter)) {
                    counter++;
                }
                barcode += "_" + counter;
            }
            barcodes.add(barcode);
            Boolean inactiveItem = getDBFBooleanFieldValue(itemsImportFile, "LINACTIVE", charset, false);
            String isItem = getDBFFieldValue(itemsImportFile, "K_GRMAT", charset);
            String captionItem = getDBFFieldValue(itemsImportFile, "POL_NAIM", charset);
            String idItemGroup = getDBFFieldValue(itemsImportFile, "K_GRTOV", charset, "");
            if (idItemGroup.endsWith("."))
                idItemGroup = idItemGroup.substring(0, idItemGroup.length() - 1);
            String UOM = getDBFFieldValue(itemsImportFile, "K_IZM", charset);
            String brand = getDBFFieldValue(itemsImportFile, "BRAND", charset);
            String nameCountry = getDBFFieldValue(itemsImportFile, "MANFR", charset);
            if ("РБ".equals(nameCountry) || "Беларусь".equals(nameCountry))
                nameCountry = "БЕЛАРУСЬ";
            Date date = getDBFDateFieldValue(itemsImportFile, "P_TIME", charset);
            Boolean isWeightItem = getDBFBooleanFieldValue(itemsImportFile, "LWEIGHT", charset, false);
            String compositionItem = "";
            if (itemsImportFile.getField("ENERGVALUE").getBytes() != null) {
                compositionItem = getDBFFieldValue(itemsImportFile, "ENERGVALUE", charset, "").replace("\n", "").replace("\r", "");
            }
            BigDecimal retailVAT = getDBFBigDecimalFieldValue(itemsImportFile, "NDSR", charset);
            BigDecimal quantityPackItem = quantities.containsKey(isItem) ? quantities.get(isItem) : null;
            Boolean isWare = getDBFBooleanFieldValue(itemsImportFile, "LGRMSEC", charset, false);
            String idWare = getDBFFieldValue(itemsImportFile, "K_GRMSEC", charset);
            String idRateWaste = "RW_" + getDBFFieldValue(itemsImportFile, "K_VGRTOV", charset, "");

            BigDecimal priceWare = getDBFBigDecimalFieldValue(itemsImportFile, "CENUOSEC", charset);
            BigDecimal ndsWare = getDBFBigDecimalFieldValue(itemsImportFile, "NDSSEC", charset, "20");

            if (!idItemGroup.isEmpty() && (!inactiveItem || importInactive) && !isWare)
                data.add(new Item(isItem, idItemGroup, captionItem, UOM, brand, brand, nameCountry, barcode, barcode,
                        date, isWeightItem ? isWeightItem : null, null, null, nullIfEmpty(compositionItem),
                        VATifAllowed(retailVAT), idWare, priceWare, ndsWare, "RW_".equals(idRateWaste) ? null : idRateWaste,
                        null, null, isItem, quantityPackItem, null, null, null, null, null));
        }
        return data;
    }

    private List<UserInvoiceDetail> importUserInvoicesFromDBF(String sprcontPath, String ostnPath) throws
            IOException, xBaseJException, ParseException, ScriptingErrorLog.SemanticErrorException {

        Map<String, String> contractSupplierMap = new HashMap<>();

        if (new File(sprcontPath).exists()) {

            DBF importFile = new DBF(sprcontPath);
            int totalRecordCount = importFile.getRecordCount();

            for (int i = 0; i < totalRecordCount; i++) {
                importFile.read();
                String idLegalEntity1 = getDBFFieldValue(importFile, "K_ANA", charset, "");
                String idLegalEntity2 = getDBFFieldValue(importFile, "DPRK", charset, "");
                String idContract = getDBFFieldValue(importFile, "K_CONT", charset);
                contractSupplierMap.put(idContract, idLegalEntity1.startsWith("ПС") ? idLegalEntity1 : idLegalEntity2);
            }
        }

        checkFileExistence(ostnPath);

        DBF importFile = new DBF(ostnPath);
        int totalRecordCount = importFile.getRecordCount();

        List<UserInvoiceDetail> data = new ArrayList<>();
        Map<String, String> userInvoiceSupplierMap = new HashMap<>();

        for (int i = 0; i < totalRecordCount; i++) {
            importFile.read();

            String post_dok = getDBFFieldValue(importFile, "POST_DOK", charset);
            String[] seriesNumber = post_dok.split("-");
            String numberUserInvoice = seriesNumber[0];
            String seriesUserInvoice = seriesNumber.length == 1 ? null : seriesNumber[1];
            String idItem = getDBFFieldValue(importFile, "K_GRMAT", charset);
            String idUserInvoiceDetail = numberUserInvoice + seriesUserInvoice + idItem;
            Date dateShipment = getDBFDateFieldValue(importFile, "D_PRIH", charset);
            BigDecimal quantityShipmentDetail = getDBFBigDecimalFieldValue(importFile, "N_MAT", charset);
            String idSupplier = getDBFFieldValue(importFile, "K_POST", charset);
            if (userInvoiceSupplierMap.containsKey(post_dok))
                idSupplier = userInvoiceSupplierMap.get(post_dok);
            else
                userInvoiceSupplierMap.put(post_dok, idSupplier);

            String idCustomerStock = getDBFFieldValue(importFile, "K_SKL", charset);
            String idSupplierStock = idSupplier + "WH";
            BigDecimal priceShipmentDetail = getDBFBigDecimalFieldValue(importFile, "N_IZG", charset);
            BigDecimal retailPriceShipmentDetail = getDBFBigDecimalFieldValue(importFile, "N_CENU", charset);
            BigDecimal retailMarkupShipmentDetail = getDBFBigDecimalFieldValue(importFile, "N_TN", charset);
            String idContract = getDBFFieldValue(importFile, "K_CONT", charset);
            idContract = (idContract != null && idSupplier.equals(contractSupplierMap.get(idContract))) ? idContract : null;

            if ((seriesNumber.length != 1) && (idSupplier.startsWith("ПС")) && (!quantityShipmentDetail.equals(BigDecimal.ZERO)))
                data.add(new UserInvoiceDetail(seriesUserInvoice + numberUserInvoice, seriesUserInvoice, numberUserInvoice,
                        true, true, idUserInvoiceDetail, dateShipment, idItem, false, quantityShipmentDetail, idSupplier,
                        idCustomerStock, idSupplierStock, priceShipmentDetail, null, null, null, null, null, null, null,
                        retailPriceShipmentDetail, retailMarkupShipmentDetail, null, null, idContract, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
        }
        return data;
    }


    private List<PriceListStore> importPriceListStoreFromDBF(String postvarPath, String strvarPath,
                                                             String prefixStore, Integer numberOfItems) throws
            IOException, xBaseJException, ParseException {

        checkFileExistence(postvarPath);
        checkFileExistence(strvarPath);

        Map<String, Object[]> postvarMap = new HashMap<>();

        DBF importPostvarFile = new DBF(postvarPath);
        int totalRecordCount = importPostvarFile.getRecordCount();

        for (int i = 0; i < totalRecordCount; i++) {
            importPostvarFile.read();

            String idSupplier = getDBFFieldValue(importPostvarFile, "K_ANA", charset);
            String idItem = getDBFFieldValue(importPostvarFile, "K_GRMAT", charset);
            BigDecimal price = getDBFBigDecimalFieldValue(importPostvarFile, "N_CENU", charset);
            Date date = getDBFDateFieldValue(importPostvarFile, "DBANNED", charset);

            postvarMap.put(idSupplier + idItem, new Object[]{price, date});
        }

        List<PriceListStore> data = new ArrayList<>();

        DBF importStrvarFile = new DBF(strvarPath);
        totalRecordCount = importStrvarFile.getRecordCount();

        for (int i = 0; i < totalRecordCount; i++) {

            if (numberOfItems != null && data.size() >= numberOfItems)
                break;

            importStrvarFile.read();

            String idSupplier = getDBFFieldValue(importStrvarFile, "K_ANA", charset);
            String idDepartmentStore = getDBFFieldValue(importStrvarFile, "K_SKL", charset);
            idDepartmentStore = idDepartmentStore.replace("МГ", prefixStore);
            String idItem = getDBFFieldValue(importStrvarFile, "K_GRMAT", charset);
            String shortNameCurrency = "BYN";
            BigDecimal pricePriceListDetail = getDBFBigDecimalFieldValue(importStrvarFile, "N_CENU", charset);

            Object[] priceDate = postvarMap.get(idSupplier + idItem);
            if (idDepartmentStore.length() >= 2 && idSupplier.startsWith("ПС")) {
                Date date = priceDate == null ? null : (Date) priceDate[1];
                pricePriceListDetail = pricePriceListDetail.equals(BigDecimal.ZERO) ? (priceDate == null ? null : (BigDecimal) priceDate[0]) : pricePriceListDetail;
                if (pricePriceListDetail != null && (date == null || date.before(new Date(System.currentTimeMillis()))))
                    data.add(new PriceListStore(idSupplier + idDepartmentStore, idItem, idSupplier, shortNameCurrency, 
                            pricePriceListDetail, idDepartmentStore));
            }
        }
        return data;
    }

    private List<PriceList> importPriceListSuppliersFromDBF(String postvarPath, Integer numberOfItems) throws
            IOException, xBaseJException, ScriptingErrorLog.SemanticErrorException, SQLException {

        checkFileExistence(postvarPath);

        List<PriceList> data = new ArrayList<>();

        DBF importPostvarFile = new DBF(postvarPath);
        int totalRecordCount = importPostvarFile.getRecordCount();

        for (int i = 0; i < totalRecordCount; i++) {

            if (numberOfItems != null && data.size() >= numberOfItems)
                break;

            importPostvarFile.read();

            String idSupplier = getDBFFieldValue(importPostvarFile, "K_ANA", charset);
            String idItem = getDBFFieldValue(importPostvarFile, "K_GRMAT", charset);
            String shortNameCurrency = "BYN";
            BigDecimal pricePriceListDetail = getDBFBigDecimalFieldValue(importPostvarFile, "N_CENU", charset);

            data.add(new PriceList(idSupplier, idItem, idSupplier, shortNameCurrency, pricePriceListDetail));
        }
        return data;
    }

    private List<LegalEntity> importLegalEntitiesFromDBF(String path, String prefixStore, Boolean importInactive, Boolean isStore) throws
            IOException, xBaseJException {

        checkFileExistence(path);

        DBF importFile = new DBF(path);
        int recordCount = importFile.getRecordCount();

        List<LegalEntity> data = new ArrayList<>();

        for (int i = 0; i < recordCount; i++) {

            importFile.read();
            String idLegalEntity = getDBFFieldValue(importFile, "K_ANA", charset);
            Boolean inactiveItem = getDBFBooleanFieldValue(importFile, "LINACTIVE", charset, false);
            if (!inactiveItem || importInactive) {
                String nameLegalEntity = getDBFFieldValue(importFile, "POL_NAIM", charset);
                String addressLegalEntity = getDBFFieldValue(importFile, "ADDRESS", charset);
                String unpLegalEntity = getDBFFieldValue(importFile, "UNN", charset);
                String okpoLegalEntity = getDBFFieldValue(importFile, "OKPO", charset);
                String phoneLegalEntity = getDBFFieldValue(importFile, "TEL", charset);
                String emailLegalEntity = getDBFFieldValue(importFile, "EMAIL", charset);
                String numberAccount = getDBFFieldValue(importFile, "ACCOUNT", charset);
                String companyStore = getDBFFieldValue(importFile, "K_JUR", charset);
                String idBank = getDBFFieldValue(importFile, "K_BANK", charset);
                String[] ownership = getAndTrimOwnershipFromName(nameLegalEntity);
                String nameCountry = "БЕЛАРУСЬ";
                String type = idLegalEntity.substring(0, 2);
                Boolean isCompany = "ЮР".equals(type);
                Boolean isSupplier = "ПС".equals(type);
                Boolean isCustomer = "ПК".equals(type);
                if (isStore) {
                    if (prefixStore.equals(type))
                        data.add(new Store(idLegalEntity, ownership[2], addressLegalEntity, companyStore, "Магазин", companyStore + "ТС"));
                } else if (isCompany || isSupplier || isCustomer)
                    data.add(new LegalEntity(idLegalEntity, ownership[2], addressLegalEntity, unpLegalEntity,
                            okpoLegalEntity, phoneLegalEntity, emailLegalEntity, ownership[1], ownership[0],
                            numberAccount, isCompany ? (idLegalEntity + "ТС") : null, isCompany ? ownership[2] : null,
                            idBank, nameCountry, isSupplier ? true : null, isCompany ? true : null,
                            isCustomer ? true : null, null));
            }
        }
        return data;
    }

    private List<Warehouse> importWarehousesFromDBF(String path, Boolean importInactive) throws
            IOException, xBaseJException {

        checkFileExistence(path);

        DBF importFile = new DBF(path);
        int recordCount = importFile.getRecordCount();

        List<Warehouse> data = new ArrayList<>();

        for (int i = 0; i < recordCount; i++) {

            importFile.read();
            String k_ana = getDBFFieldValue(importFile, "K_ANA", charset, "");
            Boolean inactiveItem = getDBFBooleanFieldValue(importFile, "LINACTIVE", charset, false);
            if (!inactiveItem || importInactive) {
                String nameWarehouse = getDBFFieldValue(importFile, "POL_NAIM", charset, "");
                String addressWarehouse = getDBFFieldValue(importFile, "ADDRESS", charset);
                String type = k_ana.substring(0, 2);
                Boolean isSupplier = "ПС".equals(type);
                Boolean isCustomer = "ПК".equals(type);
                if (isSupplier || isCustomer)
                    data.add(new Warehouse(k_ana, null, k_ana + "WH", "Склад " + nameWarehouse, addressWarehouse));
            }
        }
        return data;
    }


    private List<DepartmentStore> importDepartmentStoresFromDBF(String path, Boolean importInactive, String
            pathStores, String prefixStore) throws IOException, xBaseJException {

        checkFileExistence(path);

        DBF importStores = new DBF(pathStores);
        Map<String, String> storeDepartmentStoreMap = new HashMap<>();
        for (int i = 0; i < importStores.getRecordCount(); i++) {

            importStores.read();
            storeDepartmentStoreMap.put(new String(importStores.getField("K_SKL").getBytes(), charset).trim(),
                    new String(importStores.getField("K_SKLP").getBytes(), charset).trim());
        }

        DBF importFile = new DBF(path);
        int recordCount = importFile.getRecordCount();

        List<DepartmentStore> data = new ArrayList<>();

        for (int i = 0; i < recordCount; i++) {

            importFile.read();
            String idDepartmentStore = getDBFFieldValue(importFile, "K_ANA", charset, "");
            Boolean inactiveItem = getDBFBooleanFieldValue(importFile, "LINACTIVE", charset, false);
            if ("СК".equals(idDepartmentStore.substring(0, 2)) && (!inactiveItem || importInactive)) {
                String name = getDBFFieldValue(importFile, "POL_NAIM", charset);
                String idStore = storeDepartmentStoreMap.get(idDepartmentStore);
                idStore = idStore == null ? null : idStore.replace("МГ", prefixStore);
                String[] ownership = getAndTrimOwnershipFromName(name);
                if (idStore != null)
                    data.add(new DepartmentStore(idDepartmentStore, ownership[2], idStore));
            }
        }
        return data;
    }

    private List<Bank> importBanksFromDBF(String path) throws IOException, xBaseJException {

        checkFileExistence(path);

        DBF importFile = new DBF(path);
        int recordCount = importFile.getRecordCount();

        List<Bank> data = new ArrayList<>();

        for (int i = 0; i < recordCount; i++) {

            importFile.read();
            String idBank = getDBFFieldValue(importFile, "K_BANK", charset);
            String nameBank = getDBFFieldValue(importFile, "POL_NAIM", charset);
            String addressBank = getDBFFieldValue(importFile, "ADDRESS", charset);
            String departmentBank = getDBFFieldValue(importFile, "DEPART", charset);
            String mfoBank = getDBFFieldValue(importFile, "K_MFO", charset);
            String cbuBank = getDBFFieldValue(importFile, "CBU", charset);
            data.add(new Bank(idBank, nameBank, addressBank, departmentBank, mfoBank, cbuBank));
        }
        return data;
    }

    private List<RateWaste> importRateWastesFromDBF(String path) throws IOException, xBaseJException {

        checkFileExistence(path);

        DBF importFile = new DBF(path);
        int recordCount = importFile.getRecordCount();

        List<RateWaste> data = new ArrayList<>();

        for (int i = 0; i < recordCount; i++) {

            importFile.read();
            String idRateWaste = getDBFFieldValue(importFile, "K_GRTOV", charset, "");
            String nameRateWaste = getDBFFieldValue(importFile, "POL_NAIM", charset);
            BigDecimal percentWriteOffRate = getDBFBigDecimalFieldValue(importFile, "KOEFF", charset);
            String nameCountry = "БЕЛАРУСЬ";
            data.add(new RateWaste(("RW_" + idRateWaste), nameRateWaste, percentWriteOffRate, nameCountry));
        }
        return data;
    }

    private List<Contract> importContractsFromDBF(String path) throws IOException, xBaseJException, ParseException {

        checkFileExistence(path);

        DBF importFile = new DBF(path);
        int recordCount = importFile.getRecordCount();
        String shortNameCurrency = "BYN";

        List<Contract> contractsList = new ArrayList<>();
        List<String> idContracts = new ArrayList<>();

        for (int i = 0; i < recordCount; i++) {

            importFile.read();

            String idLegalEntity1 = getDBFFieldValue(importFile, "K_ANA", charset, "");
            String idLegalEntity2 = getDBFFieldValue(importFile, "DPRK", charset, "");
            String idContract = getDBFFieldValue(importFile, "K_CONT", charset);
            String numberContract = getDBFFieldValue(importFile, "CFULLNAME", charset);

            java.sql.Date dateFromContract = getDBFDateFieldValue(importFile, "D_VV", charset);
            java.sql.Date dateToContract = getDBFDateFieldValue(importFile, "D_END", charset);

            if (!idContracts.contains(idContract)) {
                if (idLegalEntity1.startsWith("ПС"))
                    contractsList.add(new Contract(idContract, idLegalEntity1, idLegalEntity2, numberContract,
                            dateFromContract, dateToContract, shortNameCurrency, null, null, null));
                else
                    contractsList.add(new Contract(idContract, idLegalEntity2, idLegalEntity1, numberContract,
                            dateFromContract, dateToContract, shortNameCurrency, null, null, null));
                idContracts.add(idContract);
            }
        }
        return contractsList;
    }

    private void addIfNotContains(ItemGroup element) {
        String itemGroup = element.sid.trim() + (element.name == null ? "" : element.name.trim()) + (element.parent == null ? "" : element.parent.trim());
        if (!itemGroups.contains(itemGroup)) {
            data.add(element);
            itemGroups.add(itemGroup);
        }
    }
}