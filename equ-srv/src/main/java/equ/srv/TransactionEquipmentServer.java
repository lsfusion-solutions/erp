package equ.srv;

import com.google.common.base.Throwables;
import equ.api.ItemGroup;
import equ.api.PriceCheckerInfo;
import equ.api.PriceCheckerItem;
import equ.api.TransactionInfo;
import equ.api.TransactionPriceCheckerInfo;
import equ.api.cashregister.CashRegisterInfo;
import equ.api.cashregister.CashRegisterItem;
import equ.api.cashregister.CashRegisterItemBatch;
import equ.api.cashregister.TransactionCashRegisterInfo;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItem;
import equ.api.scales.TransactionScalesInfo;
import equ.api.terminal.*;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.file.RawFileData;
import lsfusion.interop.form.property.Compare;
import lsfusion.server.data.expr.key.KeyExpr;
import lsfusion.server.data.expr.value.ValueExpr;
import lsfusion.server.data.query.build.QueryBuilder;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.language.property.LP;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.action.session.DataSession;
import org.apache.log4j.Logger;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static lsfusion.erp.integration.DefaultIntegrationAction.localDateTimeToSqlTimestamp;
import static org.apache.commons.lang3.StringUtils.trim;

//reading of the transactions for the equipment server: the transactions of the main query, then the items and the machinery of each one
class TransactionEquipmentServer {
    private static final Logger logger = EquipmentLoggers.equipmentLogger;

    private static ScriptingLogicsModule equLM;
    private static ScriptingLogicsModule cashRegisterLM;
    private static ScriptingLogicsModule cashRegisterItemLM;
    private static ScriptingLogicsModule itemLM;
    private static ScriptingLogicsModule itemFashionLM;
    private static ScriptingLogicsModule machineryLM;
    private static ScriptingLogicsModule machineryPriceTransactionLM;
    private static ScriptingLogicsModule machineryPriceTransactionBatchLM;
    private static ScriptingLogicsModule machineryPriceTransactionSectionLM;
    private static ScriptingLogicsModule machineryPriceTransactionBalanceLM;
    private static ScriptingLogicsModule machineryPriceTransactionPromotionLM;
    private static ScriptingLogicsModule machineryPriceTransactionStockTaxLM;
    private static ScriptingLogicsModule priceCheckerLM;
    private static ScriptingLogicsModule scalesLM;
    private static ScriptingLogicsModule scalesItemLM;
    private static ScriptingLogicsModule terminalLM;

    private static final Map<String, Integer> skipTroubleCounter = new HashMap<>();

    static void init(BusinessLogics BL) {
        equLM = BL.getModule("Equipment");
        cashRegisterLM = BL.getModule("EquipmentCashRegister");
        cashRegisterItemLM = BL.getModule("CashRegisterItem");
        itemLM = BL.getModule("Item");
        itemFashionLM = BL.getModule("ItemFashion");
        machineryLM = BL.getModule("Machinery");
        machineryPriceTransactionLM = BL.getModule("MachineryPriceTransaction");
        machineryPriceTransactionBatchLM = BL.getModule("MachineryPriceTransactionBatch");
        machineryPriceTransactionSectionLM = BL.getModule("MachineryPriceTransactionSection");
        machineryPriceTransactionBalanceLM = BL.getModule("MachineryPriceTransactionBalance");
        machineryPriceTransactionPromotionLM = BL.getModule("MachineryPriceTransactionPromotion");
        machineryPriceTransactionStockTaxLM = BL.getModule("MachineryPriceTransactionStockTax");
        priceCheckerLM = BL.getModule("EquipmentPriceChecker");
        scalesLM = BL.getModule("EquipmentScales");
        scalesItemLM = BL.getModule("ScalesItem");
        terminalLM = BL.getModule("EquipmentTerminal");
    }

    //a transaction of the main query
    private static class Transaction {
        final DataObject groupMachineryObject;
        final Integer nppGroupMachinery;
        final String nameGroupMachinery;
        final String handlerModelGroupMachinery;
        final DataObject transactionObject;
        final String dateTimeCode;
        final LocalDate date;
        final boolean snapshotTransaction;
        final String descriptionTransaction;
        final LocalDateTime lastErrorDateTransaction;
        final String infoMPT;

        Transaction(DataObject groupMachineryObject, Integer nppGroupMachinery, String nameGroupMachinery, String handlerModelGroupMachinery, DataObject transactionObject,
                    String dateTimeCode, LocalDate date, boolean snapshotTransaction, String descriptionTransaction, LocalDateTime lastErrorDateTransaction, String infoMPT) {
            this.groupMachineryObject = groupMachineryObject;
            this.nppGroupMachinery = nppGroupMachinery;
            this.nameGroupMachinery = nameGroupMachinery;
            this.handlerModelGroupMachinery = handlerModelGroupMachinery;
            this.transactionObject = transactionObject;
            this.dateTimeCode = dateTimeCode;
            this.date = date;
            this.snapshotTransaction = snapshotTransaction;
            this.descriptionTransaction = descriptionTransaction;
            this.lastErrorDateTransaction = lastErrorDateTransaction;
            this.infoMPT = infoMPT;
        }
    }

    static List<TransactionInfo> readTransactionInfo(EquipmentServer server, String sidEquipmentServer) throws SQLException {
        try (DataSession session = server.createSession()) {
            List<TransactionInfo> transactionList = new ArrayList<>();

            ObjectValue equipmentServerObject = equLM.findProperty("sidTo[STRING[20]]").readClasses(session, new DataObject(sidEquipmentServer));
            Integer minutesTroubleMachineryGroups = (Integer) equLM.findProperty("minutesTroubleMachineryGroup[EquipmentServer]").read(session, equipmentServerObject);
            Integer skipTroubles = (Integer) equLM.findProperty("skipTroublesDelay[EquipmentServer]").read(session, equipmentServerObject);
            Integer selectTop = (Integer) equLM.findProperty("selectTop[EquipmentServer]").read(session, equipmentServerObject);
            if(selectTop == null)
                selectTop = 0;

            KeyExpr machineryPriceTransactionExpr = new KeyExpr("machineryPriceTransaction");
            ImRevMap<Object, KeyExpr> keys = MapFact.singletonRev("machineryPriceTransaction", machineryPriceTransactionExpr);
            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

            String[] mptNames = new String[]{"dateTimeMachineryPriceTransaction", "groupMachineryMachineryPriceTransaction",
                    "nppGroupMachineryMachineryPriceTransaction", "nameGroupMachineryMachineryPriceTransaction", "snapshotMachineryPriceTransaction",
                    "descriptionMachineryPriceTransaction", "lastDateMachineryPriceTransactionErrorMachineryPriceTransaction",
                    "priorityMPT", "filterMPT", "infoMPT"};
            LP[] mptProperties = equLM.findProperties("dateTime[MachineryPriceTransaction]", "groupMachinery[MachineryPriceTransaction]",
                    "nppGroupMachinery[MachineryPriceTransaction]", "nameGroupMachinery[MachineryPriceTransaction]", "snapshot[MachineryPriceTransaction]",
                    "description[MachineryPriceTransaction]", "lastDateMachineryPriceTransactionError[MachineryPriceTransaction]",
                    "priority[MachineryPriceTransaction]", "filter[MachineryPriceTransaction]", "info[MachineryPriceTransaction]");
            for (int i = 0; i < mptProperties.length; i++) {
                query.addProperty(mptNames[i], mptProperties[i].getExpr(machineryPriceTransactionExpr));
            }
            query.addProperty("handlerModelGroupMachineryMachineryPriceTransaction", equLM.findProperty("handlerModel[GroupMachinery]").getExpr(
                    equLM.findProperty("groupMachinery[MachineryPriceTransaction]").getExpr(machineryPriceTransactionExpr)));
            query.and(equLM.findProperty("sidEquipmentServer[MachineryPriceTransaction]").getExpr(machineryPriceTransactionExpr).compare(new DataObject(sidEquipmentServer), Compare.EQUALS));
            query.and(equLM.findProperty("process[MachineryPriceTransaction]").getExpr(machineryPriceTransactionExpr).getWhere());

            logger.info(String.format("Starting to read top %s transactions", selectTop));
            ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> result = query.executeClasses(session.sql, MapFact.toOrderMap("priorityMPT", true, "filterMPT", false), selectTop, session);

            List<Transaction> transactions = new ArrayList<>();
            for (int i = 0, size = result.size(); i < size; i++) {
                ImMap<Object, ObjectValue> value = result.getValue(i);
                ObjectValue dateTimeMPT = value.get("dateTimeMachineryPriceTransaction");
                if(dateTimeMPT instanceof DataObject) {
                    DataObject groupMachineryMPT = (DataObject) value.get("groupMachineryMachineryPriceTransaction");
                    Integer nppGroupMachineryMPT = (Integer) value.get("nppGroupMachineryMachineryPriceTransaction").getValue();
                    String nameGroupMachineryMPT = (String) value.get("nameGroupMachineryMachineryPriceTransaction").getValue();
                    String handlerModelGroupMachineryMPT = (String) value.get("handlerModelGroupMachineryMachineryPriceTransaction").getValue();
                    DataObject transactionObject = result.getKey(i).singleValue();
                    boolean snapshotMPT = value.get("snapshotMachineryPriceTransaction") instanceof DataObject;
                    String descriptionMPT = (String) value.get("descriptionMachineryPriceTransaction").getValue();
                    LocalDateTime lastErrorDate = (LocalDateTime) value.get("lastDateMachineryPriceTransactionErrorMachineryPriceTransaction").getValue();
                    String infoMPT = (String) value.get("infoMPT").getValue();
                    transactions.add(new Transaction(groupMachineryMPT, nppGroupMachineryMPT, nameGroupMachineryMPT, handlerModelGroupMachineryMPT, transactionObject,
                            dateTimeCode((LocalDateTime) dateTimeMPT.getValue()), ((LocalDateTime) dateTimeMPT.getValue()).toLocalDate(), snapshotMPT, descriptionMPT, lastErrorDate, infoMPT));
                }
            }

            Map<String, List<ItemGroup>> itemGroupMap = null; //is read only for cash registers: all the item groups with the hierarchy
            Map<DataObject, ImMap<Object, Object>> groupValuesMap = new HashMap<>(); //the properties of the groups of machinery, see readGroupValues
            List<Integer> troubleMachineryGroups = readTroubleMachineryGroups(session, minutesTroubleMachineryGroups);
            Integer skipCounter = skipTroubleCounter.get(sidEquipmentServer);
            if (skipCounter == null) skipCounter = 1;
            logger.info("skip problem transactions counter for " + sidEquipmentServer + " : " + skipCounter);
            boolean skipTroubleMachineryGroups = skipTroubles != null && skipCounter < skipTroubles;
            if (skipTroubleMachineryGroups)
                skipTroubleCounter.put(sidEquipmentServer, skipCounter + 1);
            else
                skipTroubleCounter.put(sidEquipmentServer, 1);

            logger.info("" + transactions.size() + " transactions read");
            int count = 0;
            for (Transaction transaction : transactions) {

                count++;
                if (troubleMachineryGroups.contains(transaction.nppGroupMachinery) && skipTroubleMachineryGroups) {
                    logger.info("Skipped transaction number " + count + " of " + transactions.size());
                    continue;
                } else {
                    logger.info("Reading transaction number " + count + " of " + transactions.size());
                }

                DataObject transactionObject = transaction.transactionObject;
                boolean isCashRegisterPriceTransaction = cashRegisterLM != null && transactionObject.objectClass.equals(cashRegisterLM.findClass("CashRegisterPriceTransaction"));
                boolean isScalesPriceTransaction = scalesLM != null && transactionObject.objectClass.equals(scalesLM.findClass("ScalesPriceTransaction"));
                boolean isPriceCheckerPriceTransaction = priceCheckerLM != null && transactionObject.objectClass.equals(priceCheckerLM.findClass("PriceCheckerPriceTransaction"));
                boolean isTerminalPriceTransaction = terminalLM != null && transactionObject.objectClass.equals(terminalLM.findClass("TerminalPriceTransaction"));

                ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> skuResult = readSkuResult(session, transactionObject, isCashRegisterPriceTransaction);

                if (isCashRegisterPriceTransaction) {
                    if (itemGroupMap == null)
                        itemGroupMap = readItemGroupMap(session);
                    transactionList.add(readCashRegisterTransaction(session, transaction, skuResult, itemGroupMap, groupValuesMap));
                } else if (isScalesPriceTransaction) {
                    transactionList.add(readScalesTransaction(session, transaction, skuResult, groupValuesMap));
                } else if (isPriceCheckerPriceTransaction) {
                    transactionList.add(readPriceCheckerTransaction(session, transaction, skuResult));
                } else if (isTerminalPriceTransaction) {
                    transactionList.add(readTerminalTransaction(server.getBusinessLogics(), session, transaction, skuResult, groupValuesMap));
                }
            }
            logger.info("Sending " + transactionList.size() + " transactions");
            return transactionList;
        } catch (ScriptingErrorLog.SemanticErrorException | SQLHandledException e) {
            throw Throwables.propagate(e);
        }
    }

    private static void addMachineryProperties(QueryBuilder<Object, Object> machineryQuery, KeyExpr machineryExpr) throws ScriptingErrorLog.SemanticErrorException {
        String[] machineryNames = new String[]{"nppMachinery", "portMachinery", "overDirectoryMachinery"};
        LP[] machineryProperties = machineryLM.findProperties("npp[Machinery]", "port[Machinery]", "overDirectory[Machinery]");
        for (int i = 0; i < machineryProperties.length; i++) {
            machineryQuery.addProperty(machineryNames[i], machineryProperties[i].getExpr(machineryExpr));
        }
    }

    //the properties of the group of machinery are read in one query, once per group for all the transactions of this group; the values are keyed by the property names
    private static ImMap<Object, Object> readGroupValues(DataSession session, Map<DataObject, ImMap<Object, Object>> groupValuesMap, DataObject groupMachineryObject,
                                                         ScriptingLogicsModule LM, String... propertyNames) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        ImMap<Object, Object> groupValues = groupValuesMap.get(groupMachineryObject);
        if (groupValues == null) {
            KeyExpr groupMachineryExpr = new KeyExpr("groupMachinery");
            ImRevMap<Object, KeyExpr> groupMachineryKeys = MapFact.singletonRev("groupMachinery", groupMachineryExpr);
            QueryBuilder<Object, Object> groupQuery = new QueryBuilder<>(groupMachineryKeys);
            LP[] properties = LM.findProperties(propertyNames);
            for (int i = 0; i < properties.length; i++) {
                groupQuery.addProperty(propertyNames[i], properties[i].getExpr(groupMachineryExpr));
            }
            groupQuery.and(groupMachineryExpr.compare(groupMachineryObject, Compare.EQUALS));

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> groupResult = groupQuery.execute(session);
            groupValues = groupResult.isEmpty() ? MapFact.EMPTY() : groupResult.getValue(0);
            groupValuesMap.put(groupMachineryObject, groupValues);
        }
        return groupValues;
    }

    private static ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> readSkuResult(DataSession session, DataObject transactionObject, boolean isCashRegisterPriceTransaction) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        ValueExpr transactionExpr = transactionObject.getExpr();
        KeyExpr barcodeExpr = new KeyExpr("barcode");
        ImRevMap<Object, KeyExpr> skuKeys = MapFact.singletonRev("barcode", barcodeExpr);

        QueryBuilder<Object, Object> skuQuery = new QueryBuilder<>(skuKeys);

        String[] skuNames = new String[]{"nameMachineryPriceTransactionBarcode", "priceMachineryPriceTransactionBarcode",
                "expiryDateMachineryPriceTransactionBarcode", "splitMachineryPriceTransactionBarcode", "passScalesMachineryPriceTransactionBarcode",
                "idUOMMachineryPriceTransactionBarcode", "shortNameUOMMachineryPriceTransactionBarcode", "infoMPTBarcode", "extraInfoMPTBarcode", "pluNumberMachineryPriceTransactionBarcode",
                "flagsMachineryPriceTransactionBarcode", "expiryDaysMachineryPriceTransactionBarcode", "minPriceMachineryPriceTransactionBarcode",
                "canonicalNameSkuGroupMachineryPriceTransactionBarcode", "retailPrice", "idSkuBarcode"};
        LP[] skuProperties = equLM.findProperties("name[MachineryPriceTransaction,Barcode]", "price[MachineryPriceTransaction,Barcode]",
                "expiryDate[MachineryPriceTransaction,Barcode]", "split[MachineryPriceTransaction,Barcode]", "passScales[MachineryPriceTransaction,Barcode]",
                "idUOM[MachineryPriceTransaction,Barcode]", "shortNameUOM[MachineryPriceTransaction,Barcode]", "info[MachineryPriceTransaction,Barcode]", "extraInfo[MachineryPriceTransaction,Barcode]",
                "pluNumber[MachineryPriceTransaction,Barcode]", "flags[MachineryPriceTransaction,Barcode]", "expiryDays[MachineryPriceTransaction,Barcode]",
                "minPrice[MachineryPriceTransaction,Barcode]", "canonicalNameSkuGroup[MachineryPriceTransaction,Barcode]", "retailPrice[MachineryPriceTransaction, Barcode]",
                "idSku[MachineryPriceTransaction, Barcode]");
        for (int i = 0; i < skuProperties.length; i++) {
            skuQuery.addProperty(skuNames[i], skuProperties[i].getExpr(transactionExpr, barcodeExpr));
        }

        String[] barcodeNames = new String[]{"valueBarcode", "idBarcode", "skuBarcode", "skuGroupBarcode"};
        LP[] barcodeProperties = equLM.findProperties("value[Barcode]", "id[Barcode]", "sku[Barcode]", "skuGroup[Barcode]");
        for (int i = 0; i < barcodeProperties.length; i++) {
            skuQuery.addProperty(barcodeNames[i], barcodeProperties[i].getExpr(barcodeExpr));
        }

        if (isCashRegisterPriceTransaction) {
            skuQuery.addProperty("amountBarcode", equLM.findProperty("amount[Barcode]").getExpr(barcodeExpr));
            skuQuery.addProperty("mainBarcode", equLM.findProperty("idMainBarcode[Barcode]").getExpr(barcodeExpr));
        }

        if(itemLM != null) {
            skuQuery.addProperty("idBrandBarcode", itemLM.findProperty("idBrand[Barcode]").getExpr(barcodeExpr));
            skuQuery.addProperty("nameBrandBarcode", itemLM.findProperty("nameBrand[Barcode]").getExpr(barcodeExpr));
        }

        if(itemFashionLM != null) {
            skuQuery.addProperty("idSeasonBarcode", itemFashionLM.findProperty("idSeason[Barcode]").getExpr(barcodeExpr));
            skuQuery.addProperty("nameSeasonBarcode", itemFashionLM.findProperty("nameSeason[Barcode]").getExpr(barcodeExpr));
        }

        if(cashRegisterItemLM != null) {
            skuQuery.addProperty("CashRegisterItem.idSkuGroupMachineryPriceTransactionBarcode",
                    cashRegisterItemLM.findProperty("idSkuGroup[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr));
            skuQuery.addProperty("CashRegisterItem.overIdSkuGroupMachineryPriceTransactionBarcode",
                    cashRegisterItemLM.findProperty("overIdSkuGroup[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr));
        }

        if(scalesItemLM != null) {
            skuQuery.addProperty("ScalesItem.idSkuGroupMachineryPriceTransactionBarcode",
                    scalesItemLM.findProperty("idSkuGroup[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr));
        }

        if (scalesItemLM != null) {
            String[] scalesSkuNames = new String[]{"hoursExpiryMachineryPriceTransactionBarcode",
                    "descriptionMachineryPriceTransactionBarcode", "descriptionNumberMachineryPriceTransactionBarcode",
                    "groupImageMPTBarcode", "itemImageMPTBarcode"};
            LP[] scalesSkuProperties = scalesItemLM.findProperties("hoursExpiry[MachineryPriceTransaction,Barcode]",
                    "description[MachineryPriceTransaction,Barcode]", "descriptionNumber[MachineryPriceTransaction,Barcode]",
                    "groupImage[MachineryPriceTransaction,Barcode]", "itemImage[MachineryPriceTransaction,Barcode]");
            for (int i = 0; i < scalesSkuProperties.length; i++) {
                skuQuery.addProperty(scalesSkuNames[i], scalesSkuProperties[i].getExpr(transactionExpr, barcodeExpr));
            }
            skuQuery.addProperty("extraPercent", scalesItemLM.findProperty("extraPercent[MachineryPriceTransaction, Barcode]").getExpr(transactionExpr, barcodeExpr));
            skuQuery.addProperty("imagesCount", scalesItemLM.findProperty("imagesCount[Barcode]").getExpr(barcodeExpr));
        }

        if (machineryPriceTransactionStockTaxLM != null) {
            String[] taxNames = new String[]{"VATMachineryPriceTransactionBarcode"};
            LP[] taxProperties = machineryPriceTransactionStockTaxLM.findProperties("VAT[MachineryPriceTransaction,Barcode]");
            for (int i = 0; i < taxProperties.length; i++) {
                skuQuery.addProperty(taxNames[i], taxProperties[i].getExpr(transactionExpr, barcodeExpr));
            }
        }

        if(machineryPriceTransactionSectionLM != null) {
            skuQuery.addProperty("sectionMachineryPriceTransactionBarcode",
                    machineryPriceTransactionSectionLM.findProperty("section[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr));
            skuQuery.addProperty("deleteSectionBarcode",
                    machineryPriceTransactionSectionLM.findProperty("deleteSectionBarcode[?]").getExpr(barcodeExpr));
        }

        if(machineryPriceTransactionBalanceLM != null) {
            skuQuery.addProperty("balanceMachineryPriceTransactionBarcode",
                    machineryPriceTransactionBalanceLM.findProperty("balance[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr));
            skuQuery.addProperty("balanceDateMachineryPriceTransactionBarcode",
                    machineryPriceTransactionBalanceLM.findProperty("balanceDate[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr));
        }

        if(machineryPriceTransactionPromotionLM != null) {
            skuQuery.addProperty("restrictionToDateTimeMachineryPriceTransactionBarcode",
                    machineryPriceTransactionPromotionLM.findProperty("restrictionToDateTime[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr));
        }

        if(machineryPriceTransactionLM != null) {
            skuQuery.addProperty("manufactureDays", machineryPriceTransactionLM.findProperty("manufactureDays[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr));
        }

        skuQuery.and(equLM.findProperty("in[MachineryPriceTransaction,Barcode]").getExpr(transactionExpr, barcodeExpr).getWhere());

        return skuQuery.execute(session);
    }

    private static TransactionCashRegisterInfo readCashRegisterTransaction(DataSession session, Transaction transaction, ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> skuResult,
                                                                           Map<String, List<ItemGroup>> itemGroupMap, Map<DataObject, ImMap<Object, Object>> groupValuesMap) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        DataObject transactionObject = transaction.transactionObject;
        DataObject groupMachineryObject = transaction.groupMachineryObject;
        Integer nppGroupMachinery = transaction.nppGroupMachinery;
        String handlerModelGroupMachinery = transaction.handlerModelGroupMachinery;
        ValueExpr transactionExpr = transactionObject.getExpr();

        Map<String, List<CashRegisterItemBatch>> barcodeBatchMap = readBarcodeBatchMap(session, transactionObject);

        ImMap<Object, Object> groupValues = readGroupValues(session, groupValuesMap, groupMachineryObject, cashRegisterLM,
                "startDate[GroupCashRegister]", "notDetailed[GroupCashRegister]", "overDepartmentNumberCashRegister[GroupMachinery]", "idStock[GroupCashRegister]",
                "pieceCode[GroupCashRegister]", "weightCode[GroupCashRegister]", "nameStock[GroupMachinery]", "section[GroupMachinery]");
        LocalDate startDateGroupCashRegister = (LocalDate) groupValues.get("startDate[GroupCashRegister]");
        boolean notDetailedGroupCashRegister = groupValues.get("notDetailed[GroupCashRegister]") != null;
        Integer overDepartmentNumberGroupCashRegister = (Integer) groupValues.get("overDepartmentNumberCashRegister[GroupMachinery]");
        String idDepartmentStoreGroupCashRegister = (String) groupValues.get("idStock[GroupCashRegister]");
        String pieceCodeGroupCashRegister = (String) groupValues.get("pieceCode[GroupCashRegister]");
        String weightCodeGroupCashRegister = (String) groupValues.get("weightCode[GroupCashRegister]");
        String nameStockGroupCashRegister = (String) groupValues.get("nameStock[GroupMachinery]");
        String sectionGroupCashRegister = (String) groupValues.get("section[GroupMachinery]");

        List<CashRegisterInfo> cashRegisterInfoList = new ArrayList<>();
        KeyExpr cashRegisterExpr = new KeyExpr("cashRegister");
        ImRevMap<Object, KeyExpr> cashRegisterKeys = MapFact.singletonRev("cashRegister", cashRegisterExpr);
        QueryBuilder<Object, Object> cashRegisterQuery = new QueryBuilder<>(cashRegisterKeys);

        addMachineryProperties(cashRegisterQuery, cashRegisterExpr);
        cashRegisterQuery.addProperty("disableSalesCashRegister", cashRegisterLM.findProperty("disableSales[CashRegister]").getExpr(cashRegisterExpr));
        cashRegisterQuery.addProperty("succeededMachineryMachineryPriceTransaction",
                cashRegisterLM.findProperty("succeeded[Machinery,MachineryPriceTransaction]").getExpr(cashRegisterExpr, transactionExpr));
        cashRegisterQuery.addProperty("clearedMachineryMachineryPriceTransaction",
                cashRegisterLM.findProperty("cleared[Machinery,MachineryPriceTransaction]").getExpr(cashRegisterExpr, transactionExpr));
        cashRegisterQuery.addProperty("inMachineryPriceTransactionMachinery",
                equLM.findProperty("in[MachineryPriceTransaction,Machinery]").getExpr(transactionExpr, cashRegisterExpr));
        cashRegisterQuery.and(cashRegisterLM.findProperty("groupCashRegister[CashRegister]").getExpr(cashRegisterExpr).compare(groupMachineryObject, Compare.EQUALS));
        cashRegisterQuery.and(cashRegisterLM.findProperty("active[CashRegister]").getExpr(cashRegisterExpr).getWhere());

        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> cashRegisterResult = cashRegisterQuery.execute(session);

        for (ImMap<Object, Object> row : cashRegisterResult.valueIt()) {
            Integer nppMachinery = (Integer) row.get("nppMachinery");
            String portMachinery = trim((String) row.get("portMachinery"));
            String directoryCashRegister = trim((String) row.get("overDirectoryMachinery"));
            boolean succeeded = row.get("succeededMachineryMachineryPriceTransaction") != null;
            boolean cleared = row.get("clearedMachineryMachineryPriceTransaction") != null;
            Boolean disableSalesCashRegister = row.get("disableSalesCashRegister") != null;
            boolean enabled = row.get("inMachineryPriceTransactionMachinery") != null;
            cashRegisterInfoList.add(new CashRegisterInfo(enabled, cleared, succeeded, nppGroupMachinery, nppMachinery,
                    handlerModelGroupMachinery, portMachinery, directoryCashRegister,
                    startDateGroupCashRegister, overDepartmentNumberGroupCashRegister, idDepartmentStoreGroupCashRegister, notDetailedGroupCashRegister,
                    disableSalesCashRegister, pieceCodeGroupCashRegister, weightCodeGroupCashRegister, sectionGroupCashRegister, null, null, false));
        }

        List<CashRegisterItem> cashRegisterItemList = new ArrayList<>();

        for (int i = 0, size = skuResult.size(); i < size; i++) {
            ImMap<Object, Object> row = skuResult.getValue(i);

            Long barcodeObject = (Long) row.get("valueBarcode");
            String barcode = getRowValue(row, "idBarcode");
            String mainBarcode = getRowValue(row, "mainBarcode");
            BigDecimal amountBarcode = (BigDecimal) row.get("amountBarcode");
            String name = getRowValue(row, "nameMachineryPriceTransactionBarcode");
            BigDecimal price = (BigDecimal) row.get("priceMachineryPriceTransactionBarcode");
            boolean split = row.get("splitMachineryPriceTransactionBarcode") != null;
            Integer daysExpiry = (Integer) row.get("expiryDaysMachineryPriceTransactionBarcode");
            Integer hoursExpiry = (Integer) row.get("hoursExpiryMachineryPriceTransactionBarcode");
            LocalDate expiryDate = (LocalDate) row.get("expiryDateMachineryPriceTransactionBarcode");
            Integer flags = (Integer) row.get("flagsMachineryPriceTransactionBarcode");
            boolean passScales = row.get("passScalesMachineryPriceTransactionBarcode") != null;
            String idUOM = (String) row.get("idUOMMachineryPriceTransactionBarcode");
            String shortNameUOM = (String) row.get("shortNameUOMMachineryPriceTransactionBarcode");
            String info = (String) row.get("infoMPTBarcode");
            String extraInfo = (String) row.get("extraInfoMPTBarcode");
            String idBrand = itemLM == null ? null : (String) row.get("idBrandBarcode");
            String nameBrand = itemLM == null ? null : (String) row.get("nameBrandBarcode");
            String idSeason = itemFashionLM == null ? null : (String) row.get("idSeasonBarcode");
            String nameSeason = itemFashionLM == null ? null : (String) row.get("nameSeasonBarcode");
            BigDecimal valueVAT = machineryPriceTransactionStockTaxLM == null ? null : (BigDecimal) row.get("VATMachineryPriceTransactionBarcode");
            String idItem = (String) row.get("idSkuBarcode");
            Long itemGroupObject = (Long) row.get("skuGroupBarcode");
            Integer pluNumber = (Integer) row.get("pluNumberMachineryPriceTransactionBarcode");
            String description = scalesItemLM == null ? null : (String) row.get("descriptionMachineryPriceTransactionBarcode");

            String idItemGroup = cashRegisterItemLM == null ? null : (String) row.get("CashRegisterItem.idSkuGroupMachineryPriceTransactionBarcode");
            String overIdItemGroup = cashRegisterItemLM == null ? null : (String) row.get("CashRegisterItem.overIdSkuGroupMachineryPriceTransactionBarcode");
            String canonicalNameSkuGroup = (String) row.get("canonicalNameSkuGroupMachineryPriceTransactionBarcode");
            String section = machineryPriceTransactionSectionLM == null ? null : (String) row.get("sectionMachineryPriceTransactionBarcode");
            String deleteSection = machineryPriceTransactionSectionLM == null ? null : (String) row.get("deleteSectionBarcode");
            BigDecimal balance = machineryPriceTransactionBalanceLM == null ? null : (BigDecimal) row.get("balanceMachineryPriceTransactionBarcode");
            LocalDateTime balanceDate = machineryPriceTransactionBalanceLM == null ? null : (LocalDateTime) row.get("balanceDateMachineryPriceTransactionBarcode");
            BigDecimal minPrice = (BigDecimal) row.get("minPriceMachineryPriceTransactionBarcode");
            LocalDateTime restrictionToDateTime = (LocalDateTime) row.get("restrictionToDateTimeMachineryPriceTransactionBarcode");

            Integer manufactureDays = (Integer) row.get("manufactureDays");

            CashRegisterItem c = new CashRegisterItem(idItem, barcode, name, price, split, daysExpiry, hoursExpiry, expiryDate, passScales, valueVAT,
                    pluNumber, flags, idItemGroup, canonicalNameSkuGroup, idUOM, shortNameUOM, info, extraInfo, itemGroupObject, description, idBrand, nameBrand,
                    idSeason, nameSeason, section, deleteSection, minPrice, overIdItemGroup, amountBarcode,
                    balance, balanceDate, restrictionToDateTime, barcodeObject, mainBarcode, manufactureDays, barcodeBatchMap.get(barcode));
            cashRegisterItemList.add(c);
        }

        return new TransactionCashRegisterInfo((Long) transactionObject.getValue(), transaction.dateTimeCode,
                transaction.date, handlerModelGroupMachinery, (Long) groupMachineryObject.object, nppGroupMachinery,
                transaction.nameGroupMachinery, transaction.descriptionTransaction, itemGroupMap, cashRegisterItemList,
                cashRegisterInfoList, transaction.snapshotTransaction, transaction.lastErrorDateTransaction, overDepartmentNumberGroupCashRegister,
                idDepartmentStoreGroupCashRegister, pieceCodeGroupCashRegister, weightCodeGroupCashRegister, nameStockGroupCashRegister, transaction.infoMPT);
    }

    private static TransactionScalesInfo readScalesTransaction(DataSession session, Transaction transaction, ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> skuResult,
                                                               Map<DataObject, ImMap<Object, Object>> groupValuesMap) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        DataObject transactionObject = transaction.transactionObject;
        DataObject groupMachineryObject = transaction.groupMachineryObject;
        Integer nppGroupMachinery = transaction.nppGroupMachinery;
        String handlerModelGroupMachinery = transaction.handlerModelGroupMachinery;
        ValueExpr transactionExpr = transactionObject.getExpr();

        List<ScalesInfo> scalesInfoList = new ArrayList<>();
        ImMap<Object, Object> groupValues = readGroupValues(session, groupValuesMap, groupMachineryObject, scalesLM,
                "directory[GroupScales]", "pieceCode[GroupScales]", "weightCode[GroupScales]");
        String directory = trim((String) groupValues.get("directory[GroupScales]"));
        String pieceCodeGroupScales = (String) groupValues.get("pieceCode[GroupScales]");
        String weightCodeGroupScales = (String) groupValues.get("weightCode[GroupScales]");

        KeyExpr scalesExpr = new KeyExpr("scales");
        ImRevMap<Object, KeyExpr> scalesKeys = MapFact.singletonRev("scales", scalesExpr);
        QueryBuilder<Object, Object> scalesQuery = new QueryBuilder<>(scalesKeys);

        addMachineryProperties(scalesQuery, scalesExpr);
        scalesQuery.addProperty("inMachineryPriceTransactionMachinery",
                scalesLM.findProperty("in[MachineryPriceTransaction,Machinery]").getExpr(transactionExpr, scalesExpr));
        scalesQuery.addProperty("succeededMachineryMachineryPriceTransaction",
                    scalesLM.findProperty("succeeded[Machinery,MachineryPriceTransaction]").getExpr(scalesExpr, transactionExpr));
        scalesQuery.addProperty("clearedMachineryMachineryPriceTransaction",
                scalesLM.findProperty("cleared[Machinery,MachineryPriceTransaction]").getExpr(scalesExpr, transactionExpr));
        scalesQuery.addProperty("activeScales", scalesLM.findProperty("active[Scales]").getExpr(scalesExpr));
        scalesQuery.and(scalesLM.findProperty("groupScales[Scales]").getExpr(scalesExpr).compare(groupMachineryObject, Compare.EQUALS));
        //scalesQuery.and(scalesLM.findProperty("active[Scales]").getExpr(scalesExpr).getWhere());

        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> scalesResult = scalesQuery.execute(session);

        int enabledCount = 0;
        int enabledInactiveCount = 0;
        for (ImMap<Object, Object> values : scalesResult.valueIt()) {
            String portMachinery = trim((String) values.get("portMachinery"));
            Integer nppMachinery = (Integer) values.get("nppMachinery");
            boolean enabled = values.get("inMachineryPriceTransactionMachinery") != null;
            boolean succeeded = values.get("succeededMachineryMachineryPriceTransaction") != null;
            boolean cleared = values.get("clearedMachineryMachineryPriceTransaction") != null;
            boolean active = values.get("activeScales") != null;
            if(enabled) {
                enabledCount++;
                if(!active)
                    enabledInactiveCount++;
            }
            if(active)
                scalesInfoList.add(new ScalesInfo(enabled, cleared, succeeded, nppGroupMachinery, nppMachinery,
                    handlerModelGroupMachinery, portMachinery, directory,
                    pieceCodeGroupScales, weightCodeGroupScales));
        }
        //if all the marked scales are inactive, the scales aren't sent at all
        if(enabledCount > 0 && enabledCount==enabledInactiveCount)
            scalesInfoList = new ArrayList<>();

        List<ScalesItem> scalesItemList = new ArrayList<>();

        for (ImMap<Object, Object> row : skuResult.valueIt()) {
            String idItem = getRowValue(row, "idSkuBarcode");
            String barcode = getRowValue(row, "idBarcode");
            String name = getRowValue(row, "nameMachineryPriceTransactionBarcode");
            BigDecimal price = (BigDecimal) row.get("priceMachineryPriceTransactionBarcode");
            Integer pluNumber = (Integer) row.get("pluNumberMachineryPriceTransactionBarcode");
            Integer flags = (Integer) row.get("flagsMachineryPriceTransactionBarcode");
            LocalDate expiryDate = (LocalDate) row.get("expiryDateMachineryPriceTransactionBarcode");
            boolean split = row.get("splitMachineryPriceTransactionBarcode") != null;
            Integer daysExpiry = (Integer) row.get("expiryDaysMachineryPriceTransactionBarcode");
            Integer hoursExpiry = (Integer) row.get("hoursExpiryMachineryPriceTransactionBarcode");
            String description = (String) row.get("descriptionMachineryPriceTransactionBarcode");
            Integer descriptionNumberCellScales = (Integer) row.get("descriptionNumberMachineryPriceTransactionBarcode");
            boolean passScales = row.get("passScalesMachineryPriceTransactionBarcode") != null;
            BigDecimal valueVAT = machineryPriceTransactionStockTaxLM == null ? null : (BigDecimal) row.get("VATMachineryPriceTransactionBarcode");
            String idUOM = (String) row.get("idUOMMachineryPriceTransactionBarcode");
            String shortNameUOM = (String) row.get("shortNameUOMMachineryPriceTransactionBarcode");
            String info = (String) row.get("infoMPTBarcode");
            String extraInfo = (String) row.get("extraInfoMPTBarcode");

            String idItemGroup = scalesItemLM == null ? null : (String) row.get("ScalesItem.idSkuGroupMachineryPriceTransactionBarcode");
            String canonicalNameSkuGroup = (String) row.get("canonicalNameSkuGroupMachineryPriceTransactionBarcode");
            BigDecimal extraPercent = scalesItemLM == null ? null : (BigDecimal) row.get("extraPercent");

            BigDecimal retailPrice = (BigDecimal) row.get("retailPrice");
            Integer imagesCount = (Integer) row.get("imagesCount");

            RawFileData groupImage = (RawFileData) row.get("groupImageMPTBarcode");
            RawFileData itemImage = (RawFileData) row.get("itemImageMPTBarcode");

            scalesItemList.add(new ScalesItem(idItem, barcode, name, price, split, daysExpiry, hoursExpiry,
                    expiryDate, passScales, valueVAT, pluNumber, flags, idItemGroup, canonicalNameSkuGroup,
                    null, description, descriptionNumberCellScales, idUOM, shortNameUOM, info, extraInfo, extraPercent,
                    retailPrice, imagesCount, groupImage, itemImage));
        }

        return new TransactionScalesInfo((Long) transactionObject.getValue(), transaction.dateTimeCode,
                transaction.date, handlerModelGroupMachinery, (Long) groupMachineryObject.object, nppGroupMachinery,
                transaction.nameGroupMachinery, transaction.descriptionTransaction, scalesItemList, scalesInfoList, transaction.snapshotTransaction,
                transaction.lastErrorDateTransaction, transaction.infoMPT);
    }

    private static TransactionPriceCheckerInfo readPriceCheckerTransaction(DataSession session, Transaction transaction, ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> skuResult) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        DataObject transactionObject = transaction.transactionObject;
        DataObject groupMachineryObject = transaction.groupMachineryObject;
        Integer nppGroupMachinery = transaction.nppGroupMachinery;
        String handlerModelGroupMachinery = transaction.handlerModelGroupMachinery;
        ValueExpr transactionExpr = transactionObject.getExpr();

        List<PriceCheckerInfo> priceCheckerInfoList = new ArrayList<>();
        KeyExpr priceCheckerExpr = new KeyExpr("priceChecker");
        ImRevMap<Object, KeyExpr> priceCheckerKeys = MapFact.singletonRev("priceChecker", priceCheckerExpr);
        QueryBuilder<Object, Object> priceCheckerQuery = new QueryBuilder<>(priceCheckerKeys);

        addMachineryProperties(priceCheckerQuery, priceCheckerExpr);
        priceCheckerQuery.addProperty("inMachineryPriceTransactionMachinery",
                priceCheckerLM.findProperty("in[MachineryPriceTransaction,Machinery]").getExpr(transactionExpr, priceCheckerExpr));
        priceCheckerQuery.addProperty("succeededMachineryMachineryPriceTransaction",
                priceCheckerLM.findProperty("succeeded[Machinery,MachineryPriceTransaction]").getExpr(priceCheckerExpr, transactionExpr));
        priceCheckerQuery.addProperty("clearedMachineryMachineryPriceTransaction",
                priceCheckerLM.findProperty("cleared[Machinery,MachineryPriceTransaction]").getExpr(priceCheckerExpr, transactionExpr));
        priceCheckerQuery.and(priceCheckerLM.findProperty("groupPriceChecker[PriceChecker]").getExpr(priceCheckerExpr).compare(groupMachineryObject, Compare.EQUALS));

        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> priceCheckerResult = priceCheckerQuery.execute(session);

        for (ImMap<Object, Object> row : priceCheckerResult.valueIt()) {
            boolean enabled = row.get("inMachineryPriceTransactionMachinery") != null;
            Boolean succeeded = row.get("succeededMachineryMachineryPriceTransaction") != null;
            Boolean cleared = row.get("clearedMachineryMachineryPriceTransaction") != null;
            priceCheckerInfoList.add(new PriceCheckerInfo(enabled, cleared, succeeded, nppGroupMachinery, (Integer) row.get("nppMachinery"),
                    handlerModelGroupMachinery, trim((String) row.get("portMachinery"))));
        }

        List<PriceCheckerItem> priceCheckerItemList = new ArrayList<>();
        for (ImMap<Object, Object> row : skuResult.valueIt()) {
            String idItem = getRowValue(row, "idSkuBarcode");
            String barcode = getRowValue(row, "idBarcode");
            String name = getRowValue(row, "nameMachineryPriceTransactionBarcode");
            BigDecimal price = (BigDecimal) row.get("priceMachineryPriceTransactionBarcode");
            boolean split = row.get("splitMachineryPriceTransactionBarcode") != null;
            Integer daysExpiry = (Integer) row.get("expiryDaysMachineryPriceTransactionBarcode");
            Integer hoursExpiry = (Integer) row.get("hoursExpiryMachineryPriceTransactionBarcode");
            LocalDate expiryDate = (LocalDate) row.get("expiryDateMachineryPriceTransactionBarcode");
            boolean passScales = row.get("passScalesMachineryPriceTransactionBarcode") != null;
            BigDecimal valueVAT = machineryPriceTransactionStockTaxLM == null ? null : (BigDecimal) row.get("VATMachineryPriceTransactionBarcode");
            Integer pluNumber = (Integer) row.get("pluNumberMachineryPriceTransactionBarcode");
            Integer flags = (Integer) row.get("flagsMachineryPriceTransactionBarcode");

            priceCheckerItemList.add(new PriceCheckerItem(idItem, barcode, name, price, split,
                    daysExpiry, hoursExpiry, expiryDate, passScales, valueVAT, pluNumber, flags, null, null, null, null));
        }

        return new TransactionPriceCheckerInfo((Long) transactionObject.getValue(), transaction.dateTimeCode,
                transaction.date, handlerModelGroupMachinery, (Long) groupMachineryObject.object, nppGroupMachinery,
                transaction.nameGroupMachinery, transaction.descriptionTransaction, priceCheckerItemList, priceCheckerInfoList,
                transaction.snapshotTransaction, transaction.lastErrorDateTransaction, transaction.infoMPT);
    }

    private static TransactionTerminalInfo readTerminalTransaction(BusinessLogics BL, DataSession session, Transaction transaction, ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> skuResult,
                                                                   Map<DataObject, ImMap<Object, Object>> groupValuesMap) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        DataObject transactionObject = transaction.transactionObject;
        DataObject groupMachineryObject = transaction.groupMachineryObject;
        Integer nppGroupMachinery = transaction.nppGroupMachinery;
        String handlerModelGroupMachinery = transaction.handlerModelGroupMachinery;
        ValueExpr transactionExpr = transactionObject.getExpr();

        List<TerminalInfo> terminalInfoList = new ArrayList<>();

        ImMap<Object, Object> groupValues = readGroupValues(session, groupValuesMap, groupMachineryObject, terminalLM,
                "directory[GroupTerminal]", "idPriceListType[GroupMachinery]");
        String directoryGroupTerminal = trim((String) groupValues.get("directory[GroupTerminal]"));
        String idPriceListType = (String) groupValues.get("idPriceListType[GroupMachinery]");

        KeyExpr terminalExpr = new KeyExpr("terminal");
        ImRevMap<Object, KeyExpr> terminalKeys = MapFact.singletonRev("terminal", terminalExpr);
        QueryBuilder<Object, Object> terminalQuery = new QueryBuilder<>(terminalKeys);

        addMachineryProperties(terminalQuery, terminalExpr);
        terminalQuery.addProperty("inMachineryPriceTransactionMachinery",
                terminalLM.findProperty("in[MachineryPriceTransaction,Machinery]").getExpr(transactionExpr, terminalExpr));
        terminalQuery.addProperty("succeededMachineryMachineryPriceTransaction",
                terminalLM.findProperty("succeeded[Machinery,MachineryPriceTransaction]").getExpr(terminalExpr, transactionExpr));
        terminalQuery.addProperty("clearedMachineryMachineryPriceTransaction",
                terminalLM.findProperty("cleared[Machinery,MachineryPriceTransaction]").getExpr(terminalExpr, transactionExpr));
        terminalQuery.and(terminalLM.findProperty("groupTerminal[Terminal]").getExpr(terminalExpr).compare(groupMachineryObject, Compare.EQUALS));
        terminalQuery.and(terminalLM.findProperty("active[Terminal]").getExpr(terminalExpr).getWhere());

        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> terminalResult = terminalQuery.execute(session);

        for (ImMap<Object, Object> row : terminalResult.valueIt()) {
            boolean enabled = row.get("inMachineryPriceTransactionMachinery") != null;
            Boolean succeeded = row.get("succeededMachineryMachineryPriceTransaction") != null;
            Boolean cleared = row.get("clearedMachineryMachineryPriceTransaction") != null;
            terminalInfoList.add(new TerminalInfo(enabled, cleared, succeeded, nppGroupMachinery, (Integer) row.get("nppMachinery"),
                    handlerModelGroupMachinery, getRowValue(row, "portMachinery"),
                    directoryGroupTerminal, idPriceListType));
        }

        List<TerminalItem> terminalItemList = new ArrayList<>();
        for (ImMap<Object, Object> row : skuResult.valueIt()) {
            String idItem = getRowValue(row, "idSkuBarcode");
            String barcode = getRowValue(row, "idBarcode");
            String name = getRowValue(row, "nameMachineryPriceTransactionBarcode");
            BigDecimal price = (BigDecimal) row.get("priceMachineryPriceTransactionBarcode");
            boolean split = row.get("splitMachineryPriceTransactionBarcode") != null;
            Integer daysExpiry = (Integer) row.get("expiryDaysMachineryPriceTransactionBarcode");
            Integer hoursExpiry = (Integer) row.get("hoursExpiryMachineryPriceTransactionBarcode");
            LocalDate expiryDate = (LocalDate) row.get("expiryDateMachineryPriceTransactionBarcode");
            Integer pluNumber = (Integer) row.get("pluNumberMachineryPriceTransactionBarcode");
            Integer flags = (Integer) row.get("flagsMachineryPriceTransactionBarcode");
            boolean passScales = row.get("passScalesMachineryPriceTransactionBarcode") != null;
            BigDecimal valueVAT = machineryPriceTransactionStockTaxLM == null ? null : (BigDecimal) row.get("VATMachineryPriceTransactionBarcode");
            String canonicalNameSkuGroup = (String) row.get("canonicalNameSkuGroupMachineryPriceTransactionBarcode");
            String extraInfo = (String) row.get("extraInfoMPTBarcode");

            terminalItemList.add(new TerminalItem(idItem, barcode, name, price, split, daysExpiry, hoursExpiry,
                    expiryDate, passScales, valueVAT, pluNumber, flags, null, canonicalNameSkuGroup, null, extraInfo, null, null));
        }

        //commented out to reduce the amount of the transferred data (priceListType[GroupMachinery] and stock[GroupTerminal] of the group are not read anymore)
        List<TerminalAssortment> terminalAssortmentList = new ArrayList<>();//TerminalEquipmentServer.readTerminalAssortmentList(session, BL, priceListTypeGroupMachinery, stockGroupTerminal);
        List<TerminalHandbookType> terminalHandbookTypeList = TerminalEquipmentServer.readTerminalHandbookTypeList(session, BL);
        List<TerminalDocumentType> terminalDocumentTypeList = TerminalEquipmentServer.readTerminalDocumentTypeListEquipment(session, BL, groupMachineryObject);
        List<TerminalLegalEntity> terminalLegalEntityList = TerminalEquipmentServer.readTerminalLegalEntityList(session, BL);

        return new TransactionTerminalInfo((Long) transactionObject.getValue(), transaction.dateTimeCode,
                transaction.date, handlerModelGroupMachinery, (Long) groupMachineryObject.object, nppGroupMachinery, transaction.nameGroupMachinery,
                transaction.descriptionTransaction, terminalItemList, terminalInfoList, transaction.snapshotTransaction, transaction.lastErrorDateTransaction,
                terminalHandbookTypeList, terminalDocumentTypeList, terminalLegalEntityList, terminalAssortmentList,
                nppGroupMachinery, directoryGroupTerminal, transaction.infoMPT);
    }

    private static List<Integer> readTroubleMachineryGroups(DataSession session, Integer minutes) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        List<Integer> result = new ArrayList<>();
        if(minutes != null) {
            KeyExpr groupMachineryExpr = new KeyExpr("GroupMachinery");
            ImRevMap<Object, KeyExpr> groupMachineryKeys = MapFact.singletonRev("groupMachinery", groupMachineryExpr);
            QueryBuilder<Object, Object> groupMachineryQuery = new QueryBuilder<>(groupMachineryKeys);

            String[] groupMachineryNames = new String[]{"npp", "lastErrorTime"};
            LP[] groupMachineryProperties = machineryPriceTransactionLM.findProperties("npp[GroupMachinery]", "lastErrorTime[GroupMachinery]");
            for (int i = 0; i < groupMachineryProperties.length; i++) {
                groupMachineryQuery.addProperty(groupMachineryNames[i], groupMachineryProperties[i].getExpr(groupMachineryExpr));
            }
            groupMachineryQuery.and(machineryPriceTransactionLM.findProperty("lastErrorTime[GroupMachinery]").getExpr(groupMachineryExpr).getWhere());

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> groupMachineryResult = groupMachineryQuery.execute(session);

            LocalDateTime minTime = LocalDateTime.now().minusMinutes(minutes);
            for (ImMap<Object, Object> row : groupMachineryResult.valueIt()) {
                Integer npp = (Integer) row.get("npp");
                LocalDateTime lastErrorTime = (LocalDateTime) row.get("lastErrorTime");
                if (lastErrorTime != null && lastErrorTime.compareTo(minTime) <= 0)
                    result.add(npp);
            }
        }
        return result;
    }

    private static Map<String, List<ItemGroup>> readItemGroupMap(DataSession session) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, List<ItemGroup>> result = new HashMap<>();
        Map<String, ItemGroup> itemGroupMap = new HashMap<>();
        
        KeyExpr itemGroupExpr = new KeyExpr("ItemGroup");        
        ImRevMap<Object, KeyExpr> itemGroupKeys = MapFact.singletonRev("itemGroup", itemGroupExpr);
        QueryBuilder<Object, Object> itemGroupQuery = new QueryBuilder<>(itemGroupKeys);

        String[] itemGroupNames = new String[] {"idItemGroup", "overIdItemGroup", "nameItemGroup", "idParentItemGroup"};
        LP[] itemGroupProperties = itemLM.findProperties("id[ItemGroup]", "overId[ItemGroup]", "name[ItemGroup]", "idParent[ItemGroup]");
        for (int i = 0; i < itemGroupProperties.length; i++) {
            itemGroupQuery.addProperty(itemGroupNames[i], itemGroupProperties[i].getExpr(itemGroupExpr));
        }
        
        itemGroupQuery.and(itemLM.findProperty("id[ItemGroup]").getExpr(itemGroupExpr).getWhere());
        
        ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> itemGroupResult = itemGroupQuery.execute(session);

        for (ImMap<Object, Object> row : itemGroupResult.valueIt()) {
            String idItemGroup = (String) row.get("idItemGroup");
            String overIdItemGroup = (String) row.get("overIdItemGroup");
            String nameItemGroup = (String) row.get("nameItemGroup");
            String idParentItemGroup = (String) row.get("idParentItemGroup");
            itemGroupMap.put(overIdItemGroup, new ItemGroup(idItemGroup, overIdItemGroup, nameItemGroup, idParentItemGroup));
        }

        for (Map.Entry<String, ItemGroup> entry : itemGroupMap.entrySet()) {
            List<ItemGroup> hierarchy = new ArrayList<>(Collections.singletonList(entry.getValue()));
            String idParent = entry.getValue().idParentItemGroup;
            Set<String> usedIds = new HashSet<>(Collections.singletonList(idParent));
            while (itemGroupMap.containsKey(idParent)) {
                ItemGroup parentItemGroup = itemGroupMap.get(idParent);
                hierarchy.add(parentItemGroup);
                idParent = parentItemGroup.idParentItemGroup;
                if (!usedIds.add(idParent)) {
                    throw new RuntimeException(String.format("ReadItemGroupMap failed. Cycle parent found for %s: %s", entry.getKey(), idParent));
                }
            }
            result.put(entry.getKey(), hierarchy);
        }
        return result;
    }

    private static Map<String, List<CashRegisterItemBatch>> readBarcodeBatchMap(DataSession session, DataObject transactionObject) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<String, List<CashRegisterItemBatch>> result = new HashMap<>();
        if(machineryPriceTransactionBatchLM != null) {

            ValueExpr transactionExpr = transactionObject.getExpr();
            KeyExpr barcodeExpr = new KeyExpr("barcode");
            KeyExpr batchExpr = new KeyExpr("batch");
            ImRevMap<Object, KeyExpr> keys = MapFact.toRevMap("barcode", barcodeExpr, "batch", batchExpr);

            QueryBuilder<Object, Object> query = new QueryBuilder<>(keys);

            query.addProperty("idBarcode", machineryPriceTransactionBatchLM.findProperty("id[Barcode]").getExpr(barcodeExpr));

            String[] names = new String[]{"idBatch", "dateBatch", "expiryDate", "seriesPharmacy", "nameManufacturer", "price",
                    "nameATCGroup", "balance", "balanceBlister", "balanceDate", "sidCountry", "nameCountry", "blisterAmount",
                    "idDosage", "desctiptionDosage", "flag", "info"};
            LP[] properties = machineryPriceTransactionBatchLM.findProperties("id[MachineryPriceTransaction,Barcode,Batch]", "date[MachineryPriceTransaction,Barcode,Batch]",
                    "expiryDate[MachineryPriceTransaction,Barcode,Batch]", "seriesPharmacy[MachineryPriceTransaction,Barcode,Batch]",
                    "nameManufacturer[MachineryPriceTransaction,Barcode,Batch]", "price[MachineryPriceTransaction,Barcode,Batch]",
                    "nameATCGroup[MachineryPriceTransaction,Barcode,Batch]", "balance[MachineryPriceTransaction,Barcode,Batch]", "balanceBlister[MachineryPriceTransaction,Barcode,Batch]",
                    "balanceDate[MachineryPriceTransaction,Barcode,Batch]", "sidCountry[MachineryPriceTransaction,Barcode,Batch]",
                    "nameCountry[MachineryPriceTransaction,Barcode,Batch]", "blisterAmount[MachineryPriceTransaction,Barcode,Batch]",
                    "idDosage[MachineryPriceTransaction,Barcode,Batch]", "descriptionDosage[MachineryPriceTransaction,Barcode,Batch]",
                    "flag[MachineryPriceTransaction,Barcode,Batch]", "info[MachineryPriceTransaction,Barcode,Batch]");
            for (int i = 0; i < properties.length; i++) {
                query.addProperty(names[i], properties[i].getExpr(transactionExpr, barcodeExpr, batchExpr));
            }

            query.and(machineryPriceTransactionBatchLM.findProperty("in[MachineryPriceTransaction,Barcode,Batch]").getExpr(transactionExpr, barcodeExpr, batchExpr).getWhere());

            ImOrderMap<ImMap<Object, Object>, ImMap<Object, Object>> queryResult = query.execute(session);

            for (int i = 0, size = queryResult.size(); i < size; i++) {
                ImMap<Object, Object> row = queryResult.getValue(i);

                String barcode = getRowValue(row, "idBarcode");

                String idBatch = getRowValue(row, "idBatch");
                LocalDate dateBatch = (LocalDate) row.get("dateBatch");
                LocalDate expiryDate = (LocalDate) row.get("expiryDate");
                String seriesPharmacy = getRowValue(row, "seriesPharmacy");
                String nameManufacturer = getRowValue(row, "nameManufacturer");
                BigDecimal price = (BigDecimal) row.get("price");
                String nameSubstance = getRowValue(row, "nameATCGroup");
                BigDecimal balance = (BigDecimal) row.get("balance");
                BigDecimal balanceBlister = (BigDecimal) row.get("balanceBlister");
                LocalDateTime balanceDate = (LocalDateTime) row.get("balanceDate");
                String countryCode = getRowValue(row, "sidCountry");
                String countryName = getRowValue(row, "nameCountry");
                Integer blisterAmount = (Integer) row.get("blisterAmount");
                String idDosage = (String) row.get("idDosage");
                String desctiptionDosage = (String) row.get("desctiptionDosage");
                Integer flag = (Integer) row.get("flag");
                String info = (String) row.get("info");

                List<CashRegisterItemBatch> batchList = result.getOrDefault(barcode, new ArrayList<>());
                batchList.add(new CashRegisterItemBatch(idBatch, dateBatch, expiryDate, seriesPharmacy, nameManufacturer,
                        price, nameSubstance, balance, balanceBlister, balanceDate, countryCode, countryName, blisterAmount,
                        idDosage, desctiptionDosage, flag, info));
                result.put(barcode, batchList);
            }
        }
        return result;
    }

    private static String dateTimeCode(LocalDateTime timeStamp) {
        String result = "";
        long time = localDateTimeToSqlTimestamp(timeStamp).getTime() / 1000;
        while (time > 26) {
            result = (char) (time % 26 + 97) + result;
            time = time / 26;
        }
        result = (char) (time + 97) + result;
        return result;
    }

    private static String getRowValue(ImMap<Object, Object> row, String key) {
        return trim((String) row.get(key));
    }
}
