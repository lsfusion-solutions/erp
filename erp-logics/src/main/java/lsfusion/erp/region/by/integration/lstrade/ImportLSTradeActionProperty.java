package lsfusion.erp.region.by.integration.lstrade;

import lsfusion.erp.integration.*;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.apache.commons.lang.time.DateUtils;
import org.xBaseJ.DBF;
import org.xBaseJ.xBaseJException;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;

public class ImportLSTradeActionProperty extends DefaultImportActionProperty {

    public ImportLSTradeActionProperty(ScriptingLogicsModule LM) {
        super(LM);

        try {
            Class.forName("sun.jdbc.odbc.JdbcOdbcDriver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {
        try {
            Integer numberOfItems = (Integer) getLCP("importNumberItems").read(context);
            Integer numberOfPriceLists = (Integer) getLCP("importNumberPriceLists").read(context);
            String prefixStore = (String) getLCP("prefixStore").read(context);
            prefixStore = prefixStore == null ? "МГ" : prefixStore.trim();

            Object pathObject = getLCP("importLSTDirectory").read(context);
            String path = pathObject == null ? "" : ((String) pathObject).trim();
            if (!path.isEmpty()) {

                ImportData importData = new ImportData();

                Boolean importInactive = getLCP("importInactive").read(context) != null;
                importData.setImportInactive(importInactive);
                importData.setSkipKeys(getLCP("skipKeysLSTrade").read(context) != null);
                importData.setWithoutRecalc(getLCP("withoutRecalcLSTrade").read(context) != null);

                importData.setNumberOfItemsAtATime((Integer) getLCP("importNumberItemsAtATime").read(context));
                importData.setNumberOfPriceListsAtATime((Integer) getLCP("importNumberPriceListsAtATime").read(context));

                importData.setItemGroupsList((getLCP("importGroupItems").read(context) != null) ?
                        importItemGroupsFromDBF(path + "//_sprgrt.dbf", false) : null);

                importData.setParentGroupsList((getLCP("importGroupItems").read(context) != null) ?
                        importItemGroupsFromDBF(path + "//_sprgrt.dbf", true) : null);

                importData.setBanksList((getLCP("importBanks").read(context) != null) ?
                        importBanksFromDBF(path + "//_sprbank.dbf") : null);

                importData.setLegalEntitiesList((getLCP("importLegalEntities").read(context) != null) ?
                        importLegalEntitiesFromDBF(path + "//_sprana.dbf", prefixStore, importInactive, false) : null);

                importData.setWarehousesList((getLCP("importWarehouses").read(context) != null) ?
                        importWarehousesFromDBF(path + "//_sprana.dbf", importInactive) : null);

                importData.setContractsList((getLCP("importContracts").read(context) != null) ?
                        importContractsFromDBF(path + "//_sprcont.dbf") : null);

                importData.setStoresList((getLCP("importStores").read(context) != null) ?
                        importLegalEntitiesFromDBF(path + "//_sprana.dbf", prefixStore, importInactive, true) : null);

                importData.setDepartmentStoresList((getLCP("importDepartmentStores").read(context) != null) ?
                        importDepartmentStoresFromDBF(path + "//_sprana.dbf", importInactive, path + "//_storestr.dbf",
                                prefixStore) : null);

                importData.setRateWastesList((getLCP("importRateWastes").read(context) != null) ?
                        importRateWastesFromDBF(path + "//_sprvgrt.dbf") : null);

                importData.setWaresList((getLCP("importWares").read(context) != null) ?
                        importWaresFromDBF(path + "//_sprgrm.dbf") : null);

                importData.setUOMsList((getLCP("importUOMs").read(context) != null) ?
                        importUOMsFromDBF(path + "//_sprgrm.dbf") : null);

                importData.setItemsList((getLCP("importItems").read(context) != null) ?
                        importItemsFromDBF(path + "//_sprgrm.dbf", path + "//_postvar.dbf", numberOfItems, importInactive) : null);

                importData.setPriceListStoresList((getLCP("importPriceListStores").read(context) != null) ?
                        importPriceListStoreFromDBF(path + "//_postvar.dbf", path + "//_strvar.dbf", prefixStore, numberOfPriceLists) : null);

                importData.setPriceListSuppliersList((getLCP("importPriceListSuppliers").read(context) != null) ?
                        importPriceListSuppliersFromDBF(path + "//_postvar.dbf", numberOfPriceLists) : null);

                importData.setUserInvoicesList((getLCP("importUserInvoices").read(context) != null) ?
                        importUserInvoicesFromDBF(path + "//_sprcont.dbf", path + "//_ostn.dbf") : null);

                new ImportActionProperty(LM, importData, context).makeImport();
            }
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        } catch (xBaseJException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private List<ItemGroup> data;
    private List<String> itemGroups;

    private List<ItemGroup> importItemGroupsFromDBF(String path, Boolean parents) throws IOException, xBaseJException {

        checkFileExistence(path);

        DBF importFile = new DBF(path);
        int recordCount = importFile.getRecordCount();

        data = new ArrayList<ItemGroup>();
        itemGroups = new ArrayList<String>();

        String groupTop = "ВСЕ";
        if (!parents)
            addIfNotContains(new ItemGroup(groupTop, groupTop, null));
        else
            addIfNotContains(new ItemGroup(groupTop, null, null));

        for (int i = 0; i < recordCount; i++) {

            importFile.read();

            String k_grtov = getFieldValue(importFile, "K_GRTOV", "Cp1251", "");
            String pol_naim = getFieldValue(importFile, "POL_NAIM", "Cp1251", "");
            String group1 = getFieldValue(importFile, "GROUP1", "Cp1251", "");
            String group2 = getFieldValue(importFile, "GROUP2", "Cp1251", "");
            String group3 = getFieldValue(importFile, "GROUP3", "Cp1251", "");

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
        List<Ware> data = new ArrayList<Ware>();

        for (int i = 0; i < recordCount; i++) {
            importFile.read();

            Boolean isWare = getBooleanFieldValue(importFile, "LGRMSEC", "Cp1251", false);
            String idWare = getFieldValue(importFile, "K_GRMAT", "Cp1251", null);
            String nameWare = getFieldValue(importFile, "POL_NAIM", "Cp1251", null);
            BigDecimal priceWare = getBigDecimalFieldValue(importFile, "CENUOSEC", "Cp1251", null);

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

        List<UOM> data = new ArrayList<UOM>();

        for (int i = 0; i < recordCount; i++) {
            itemsImportFile.read();

            String UOM = getFieldValue(itemsImportFile, "K_IZM", "Cp1251", null);
            Boolean isWare = getBooleanFieldValue(itemsImportFile, "LGRMSEC", "Cp1251", false);

            if (!isWare)
                data.add(new UOM(UOM, UOM, UOM));
        }
        return data;
    }

    private List<Item> importItemsFromDBF(String itemsPath, String quantityPath, Integer numberOfItems, Boolean importInactive) throws IOException, xBaseJException, ParseException {

        checkFileExistence(itemsPath);
        checkFileExistence(quantityPath);

        Set<String> barcodes = new HashSet<String>();

        DBF quantityImportFile = new DBF(quantityPath);
        int totalRecordCount = quantityImportFile.getRecordCount();

        Map<String, BigDecimal> quantities = new HashMap<String, BigDecimal>();

        for (int i = 0; i < totalRecordCount; i++) {
            quantityImportFile.read();

            String idItem = getFieldValue(quantityImportFile, "K_GRMAT", "Cp1251", null);
            BigDecimal quantityPackItem = getBigDecimalFieldValue(quantityImportFile, "PACKSIZE", "Cp1251", null);

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

        List<Item> data = new ArrayList<Item>();

        int recordCount = (numberOfItems != null && numberOfItems != 0 && numberOfItems < totalRecordCount) ? numberOfItems : totalRecordCount;
        for (int i = 0; i < recordCount; i++) {
            itemsImportFile.read();
            String barcode = getFieldValue(itemsImportFile, "K_GRUP", "Cp1251", null);
            int counter = 1;
            if (barcodes.contains(barcode)) {
                while (barcodes.contains(barcode + "_" + counter)) {
                    counter++;
                }
                barcode += "_" + counter;
            }
            barcodes.add(barcode);
            Boolean inactiveItem = getBooleanFieldValue(itemsImportFile, "LINACTIVE", "Cp1251", false);
            String isItem = getFieldValue(itemsImportFile, "K_GRMAT", "Cp1251", null);
            String captionItem = getFieldValue(itemsImportFile, "POL_NAIM", "Cp1251", null);
            String idItemGroup = getFieldValue(itemsImportFile, "K_GRTOV", "Cp1251", null);
            if (idItemGroup.endsWith("."))
                idItemGroup = idItemGroup.substring(0, idItemGroup.length() - 1);
            String UOM = getFieldValue(itemsImportFile, "K_IZM", "Cp1251", null);
            String brand = getFieldValue(itemsImportFile, "BRAND", "Cp1251", null);
            String nameCountry = getFieldValue(itemsImportFile, "MANFR", "Cp1251", null);
            if ("РБ".equals(nameCountry) || "Беларусь".equals(nameCountry))
                nameCountry = "БЕЛАРУСЬ";
            Date date = getDateFieldValue(itemsImportFile, "P_TIME", "Cp1251", null);
            Boolean isWeightItem = getBooleanFieldValue(itemsImportFile, "LWEIGHT", "Cp1251", false);
            String compositionItem = "";
            if (itemsImportFile.getField("ENERGVALUE").getBytes() != null) {
                compositionItem = getFieldValue(itemsImportFile, "ENERGVALUE", "Cp1251", "").replace("\n", "").replace("\r", "");
            }
            BigDecimal retailVAT = getBigDecimalFieldValue(itemsImportFile, "NDSR", "Cp1251", null);
            BigDecimal quantityPackItem = quantities.containsKey(isItem) ? quantities.get(isItem) : null;
            Boolean isWare = getBooleanFieldValue(itemsImportFile, "LGRMSEC", "Cp1251", false);
            String idWare = getFieldValue(itemsImportFile, "K_GRMSEC", "Cp1251", null);
            if (idWare.isEmpty())
                idWare = null;
            String idRateWaste = "RW_" + getFieldValue(itemsImportFile, "K_VGRTOV", "Cp1251", "");

            BigDecimal priceWare = getBigDecimalFieldValue(itemsImportFile, "CENUOSEC", "Cp1251", null);
            BigDecimal ndsWare = getBigDecimalFieldValue(itemsImportFile, "NDSSEC", "Cp1251", "20");

            if (!idItemGroup.isEmpty() && (!inactiveItem || importInactive) && !isWare)
                data.add(new Item(isItem, idItemGroup, captionItem, UOM, brand, brand, nameCountry, barcode, barcode,
                        date, isWeightItem ? isWeightItem : null, null, null, compositionItem.isEmpty() ? null : compositionItem,
                        VATifAllowed(retailVAT), idWare, priceWare, ndsWare, 
                        "RW_".equals(idRateWaste) ? null : idRateWaste, null, null, isItem, quantityPackItem, null, null,
                        null, null, null));
        }
        return data;
    }

    private List<UserInvoiceDetail> importUserInvoicesFromDBF(String sprcontPath, String ostnPath) throws
            IOException, xBaseJException, ParseException, ScriptingErrorLog.SemanticErrorException {

        Map<String, String> contractSupplierMap = new HashMap<String, String>();

        if (new File(sprcontPath).exists()) {

            DBF importFile = new DBF(sprcontPath);
            int totalRecordCount = importFile.getRecordCount();

            for (int i = 0; i < totalRecordCount; i++) {
                importFile.read();
                String idLegalEntity1 = getFieldValue(importFile, "K_ANA", "Cp1251", "");
                String idLegalEntity2 = getFieldValue(importFile, "DPRK", "Cp1251", "");
                String idContract = getFieldValue(importFile, "K_CONT", "Cp1251", null);
                contractSupplierMap.put(idContract, idLegalEntity1.startsWith("ПС") ? idLegalEntity1 : idLegalEntity2);
            }
        }

        checkFileExistence(ostnPath);

        DBF importFile = new DBF(ostnPath);
        int totalRecordCount = importFile.getRecordCount();

        List<UserInvoiceDetail> data = new ArrayList<UserInvoiceDetail>();
        Map<String, String> userInvoiceSupplierMap = new HashMap<String, String>();

        for (int i = 0; i < totalRecordCount; i++) {
            importFile.read();

            String post_dok = getFieldValue(importFile, "POST_DOK", "Cp1251", null);
            String[] seriesNumber = post_dok.split("-");
            String numberUserInvoice = seriesNumber[0];
            String seriesUserInvoice = seriesNumber.length == 1 ? null : seriesNumber[1];
            String idItem = getFieldValue(importFile, "K_GRMAT", "Cp1251", null);
            String idUserInvoiceDetail = numberUserInvoice + seriesUserInvoice + idItem;
            Date dateShipment = getDateFieldValue(importFile, "D_PRIH", "Cp1251", null);
            BigDecimal quantityShipmentDetail = getBigDecimalFieldValue(importFile, "N_MAT", "Cp1251", null);
            String idSupplier = getFieldValue(importFile, "K_POST", "Cp1251", null);
            if (userInvoiceSupplierMap.containsKey(post_dok))
                idSupplier = userInvoiceSupplierMap.get(post_dok);
            else
                userInvoiceSupplierMap.put(post_dok, idSupplier);

            String idCustomerStock = getFieldValue(importFile, "K_SKL", "Cp1251", null);
            String idSupplierStock = idSupplier + "WH";
            BigDecimal priceShipmentDetail = getBigDecimalFieldValue(importFile, "N_IZG", "Cp1251", null);
            BigDecimal retailPriceShipmentDetail = getBigDecimalFieldValue(importFile, "N_CENU", "Cp1251", null);
            BigDecimal retailMarkupShipmentDetail = getBigDecimalFieldValue(importFile, "N_TN", "Cp1251", null);
            String idContract = getFieldValue(importFile, "K_CONT", "Cp1251", null);
            idContract = (idContract != null && idSupplier.equals(contractSupplierMap.get(idContract))) ? idContract : null;

            if ((seriesNumber.length != 1) && (idSupplier.startsWith("ПС")) && (!quantityShipmentDetail.equals(new BigDecimal(0))))
                data.add(new UserInvoiceDetail(seriesUserInvoice + numberUserInvoice, seriesUserInvoice, numberUserInvoice,
                        true, true, idUserInvoiceDetail, dateShipment, idItem, false, quantityShipmentDetail, idSupplier,
                        idCustomerStock, idSupplierStock, priceShipmentDetail, null, null, null, null, null, null, null,
                        retailPriceShipmentDetail, retailMarkupShipmentDetail, null, null, idContract, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null));
        }
        return data;
    }


    private List<PriceListStore> importPriceListStoreFromDBF(String postvarPath, String strvarPath,
                                                             String prefixStore, Integer numberOfItems) throws
            IOException, xBaseJException, ParseException {

        checkFileExistence(postvarPath);
        checkFileExistence(strvarPath);

        Map<String, Object[]> postvarMap = new HashMap<String, Object[]>();

        DBF importPostvarFile = new DBF(postvarPath);
        int totalRecordCount = importPostvarFile.getRecordCount();

        for (int i = 0; i < totalRecordCount; i++) {
            importPostvarFile.read();

            String idSupplier = getFieldValue(importPostvarFile, "K_ANA", "Cp1251", null);
            String idItem = getFieldValue(importPostvarFile, "K_GRMAT", "Cp1251", null);
            BigDecimal price = getBigDecimalFieldValue(importPostvarFile, "N_CENU", "Cp1251", null);
            Date date = getDateFieldValue(importPostvarFile, "DBANNED", "Cp1251", null);

            postvarMap.put(idSupplier + idItem, new Object[]{price, date});
        }

        List<PriceListStore> data = new ArrayList<PriceListStore>();

        DBF importStrvarFile = new DBF(strvarPath);
        totalRecordCount = importStrvarFile.getRecordCount();

        for (int i = 0; i < totalRecordCount; i++) {

            if (numberOfItems != null && data.size() >= numberOfItems)
                break;

            importStrvarFile.read();

            String idSupplier = getFieldValue(importStrvarFile, "K_ANA", "Cp1251", null);
            String idDepartmentStore = getFieldValue(importStrvarFile, "K_SKL", "Cp1251", null);
            idDepartmentStore = idDepartmentStore.replace("МГ", prefixStore);
            String idItem = getFieldValue(importStrvarFile, "K_GRMAT", "Cp1251", null);
            String shortNameCurrency = "BLR";
            BigDecimal pricePriceListDetail = getBigDecimalFieldValue(importStrvarFile, "N_CENU", "Cp1251", null);

            Object[] priceDate = postvarMap.get(idSupplier + idItem);
            if (idDepartmentStore.length() >= 2 && idSupplier.startsWith("ПС")) {
                Date date = priceDate == null ? null : (Date) priceDate[1];
                pricePriceListDetail = pricePriceListDetail.equals(new BigDecimal(0)) ? (priceDate == null ? null : (BigDecimal) priceDate[0]) : pricePriceListDetail;
                if (pricePriceListDetail != null && (date == null || date.before(new Date(System.currentTimeMillis()))))
                    data.add(new PriceListStore(idSupplier + idDepartmentStore, idItem, idSupplier, idDepartmentStore,
                            shortNameCurrency, pricePriceListDetail, true, true));
            }
        }
        return data;
    }

    private List<PriceListSupplier> importPriceListSuppliersFromDBF(String postvarPath, Integer numberOfItems) throws
            IOException, xBaseJException, ScriptingErrorLog.SemanticErrorException, SQLException {

        checkFileExistence(postvarPath);

        List<PriceListSupplier> data = new ArrayList<PriceListSupplier>();

        DBF importPostvarFile = new DBF(postvarPath);
        int totalRecordCount = importPostvarFile.getRecordCount();

        for (int i = 0; i < totalRecordCount; i++) {

            if (numberOfItems != null && data.size() >= numberOfItems)
                break;

            importPostvarFile.read();

            String idSupplier = getFieldValue(importPostvarFile, "K_ANA", "Cp1251", null);
            String idItem = getFieldValue(importPostvarFile, "K_GRMAT", "Cp1251", null);
            String shortNameCurrency = "BLR";
            BigDecimal pricePriceListDetail = getBigDecimalFieldValue(importPostvarFile, "N_CENU", "Cp1251", null);

            data.add(new PriceListSupplier(idSupplier, idItem, idSupplier, shortNameCurrency, pricePriceListDetail, true));
        }
        return data;
    }

    private List<LegalEntity> importLegalEntitiesFromDBF(String path, String prefixStore, Boolean importInactive, Boolean isStore) throws
            IOException, xBaseJException {

        checkFileExistence(path);

        DBF importFile = new DBF(path);
        int recordCount = importFile.getRecordCount();

        List<LegalEntity> data = new ArrayList<LegalEntity>();

        for (int i = 0; i < recordCount; i++) {

            importFile.read();
            String idLegalEntity = getFieldValue(importFile, "K_ANA", "Cp1251", null);
            Boolean inactiveItem = getBooleanFieldValue(importFile, "LINACTIVE", "Cp1251", false);
            if (!inactiveItem || importInactive) {
                String nameLegalEntity = getFieldValue(importFile, "POL_NAIM", "Cp1251", null);
                String addressLegalEntity = getFieldValue(importFile, "ADDRESS", "Cp1251", null);
                String unpLegalEntity = getFieldValue(importFile, "UNN", "Cp1251", null);
                String okpoLegalEntity = getFieldValue(importFile, "OKPO", "Cp1251", null);
                String phoneLegalEntity = getFieldValue(importFile, "TEL", "Cp1251", null);
                String emailLegalEntity = getFieldValue(importFile, "EMAIL", "Cp1251", null);
                String numberAccount = getFieldValue(importFile, "ACCOUNT", "Cp1251", null);
                String companyStore = getFieldValue(importFile, "K_JUR", "Cp1251", null);
                String idBank = getFieldValue(importFile, "K_BANK", "Cp1251", null);
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
                            isCustomer ? true : null));
            }
        }
        return data;
    }

    private List<Warehouse> importWarehousesFromDBF(String path, Boolean importInactive) throws
            IOException, xBaseJException {

        checkFileExistence(path);

        DBF importFile = new DBF(path);
        int recordCount = importFile.getRecordCount();

        List<Warehouse> data = new ArrayList<Warehouse>();

        for (int i = 0; i < recordCount; i++) {

            importFile.read();
            String k_ana = getFieldValue(importFile, "K_ANA", "Cp1251", "");
            Boolean inactiveItem = getBooleanFieldValue(importFile, "LINACTIVE", "Cp1251", false);
            if (!inactiveItem || importInactive) {
                String nameWarehouse = getFieldValue(importFile, "POL_NAIM", "Cp1251", "");
                String addressWarehouse = getFieldValue(importFile, "ADDRESS", "Cp1251", "");
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
        Map<String, String> storeDepartmentStoreMap = new HashMap<String, String>();
        for (int i = 0; i < importStores.getRecordCount(); i++) {

            importStores.read();
            storeDepartmentStoreMap.put(new String(importStores.getField("K_SKL").getBytes(), "Cp1251").trim(),
                    new String(importStores.getField("K_SKLP").getBytes(), "Cp1251").trim());
        }

        DBF importFile = new DBF(path);
        int recordCount = importFile.getRecordCount();

        List<DepartmentStore> data = new ArrayList<DepartmentStore>();

        for (int i = 0; i < recordCount; i++) {

            importFile.read();
            String idDepartmentStore = getFieldValue(importFile, "K_ANA", "Cp1251", "");
            Boolean inactiveItem = getBooleanFieldValue(importFile, "LINACTIVE", "Cp1251", false);
            if ("СК".equals(idDepartmentStore.substring(0, 2)) && (!inactiveItem || importInactive)) {
                String name = getFieldValue(importFile, "POL_NAIM", "Cp1251", "");
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

        List<Bank> data = new ArrayList<Bank>();

        for (int i = 0; i < recordCount; i++) {

            importFile.read();
            String idBank = getFieldValue(importFile, "K_BANK", "Cp1251", "");
            String nameBank = getFieldValue(importFile, "POL_NAIM", "Cp1251", "");
            String addressBank = getFieldValue(importFile, "ADDRESS", "Cp1251", "");
            String departmentBank = getFieldValue(importFile, "DEPART", "Cp1251", "");
            String mfoBank = getFieldValue(importFile, "K_MFO", "Cp1251", "");
            String cbuBank = getFieldValue(importFile, "CBU", "Cp1251", "");
            data.add(new Bank(idBank, nameBank, addressBank, departmentBank, mfoBank, cbuBank));
        }
        return data;
    }

    private List<RateWaste> importRateWastesFromDBF(String path) throws IOException, xBaseJException {

        checkFileExistence(path);

        DBF importFile = new DBF(path);
        int recordCount = importFile.getRecordCount();

        List<RateWaste> data = new ArrayList<RateWaste>();

        for (int i = 0; i < recordCount; i++) {

            importFile.read();
            String idRateWaste = getFieldValue(importFile, "K_GRTOV", "Cp1251", "");
            String nameRateWaste = getFieldValue(importFile, "POL_NAIM", "Cp1251", null);
            BigDecimal percentWriteOffRate = getBigDecimalFieldValue(importFile, "KOEFF", "Cp1251", null);
            String nameCountry = "БЕЛАРУСЬ";
            data.add(new RateWaste(("RW_" + idRateWaste), nameRateWaste, percentWriteOffRate, nameCountry));
        }
        return data;
    }

    private List<Contract> importContractsFromDBF(String path) throws IOException, xBaseJException, ParseException {

        checkFileExistence(path);

        DBF importFile = new DBF(path);
        int recordCount = importFile.getRecordCount();
        String shortNameCurrency = "BLR";

        List<Contract> contractsList = new ArrayList<Contract>();
        List<String> idContracts = new ArrayList<String>();

        for (int i = 0; i < recordCount; i++) {

            importFile.read();

            String idLegalEntity1 = getFieldValue(importFile, "K_ANA", "Cp1251", "");
            String idLegalEntity2 = getFieldValue(importFile, "DPRK", "Cp1251", "");
            String idContract = getFieldValue(importFile, "K_CONT", "Cp1251", "");
            String numberContract = getFieldValue(importFile, "CFULLNAME", "Cp1251", "");

            java.sql.Date dateFromContract = getDateFieldValue(importFile, "D_VV", "Cp1251", null);
            java.sql.Date dateToContract = getDateFieldValue(importFile, "D_END", "Cp1251", null);

            if (!idContracts.contains(idContract)) {
                if (idLegalEntity1.startsWith("ПС"))
                    contractsList.add(new Contract(idContract, idLegalEntity1, idLegalEntity2, numberContract,
                            dateFromContract, dateToContract, shortNameCurrency));
                else
                    contractsList.add(new Contract(idContract, idLegalEntity2, idLegalEntity1, numberContract,
                            dateFromContract, dateToContract, shortNameCurrency));
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

    private BigDecimal getBigDecimalFieldValue(DBF importFile, String fieldName, String charset, String defaultValue) throws UnsupportedEncodingException {
        return BigDecimal.valueOf(Double.valueOf(getFieldValue(importFile, fieldName, charset, defaultValue)));
    }

    private java.sql.Date getDateFieldValue(DBF importFile, String fieldName, String charset, java.sql.Date defaultValue) throws UnsupportedEncodingException, ParseException {
        String dateString = getFieldValue(importFile, fieldName, charset, "");
        return dateString.isEmpty() ? defaultValue : new java.sql.Date(DateUtils.parseDate(dateString, new String[]{"yyyyMMdd"}).getTime());

    }

    private Boolean getBooleanFieldValue(DBF importFile, String fieldName, String charset, Boolean defaultValue) throws UnsupportedEncodingException {
        return "T".equals(getFieldValue(importFile, fieldName, charset, String.valueOf(defaultValue)));
    }

    private String getFieldValue(DBF importFile, String fieldName, String charset, String defaultValue) throws UnsupportedEncodingException {
        try {
            return new String(importFile.getField(fieldName).getBytes(), charset).trim();
        } catch (xBaseJException e) {
            return defaultValue;
        }
    }

}