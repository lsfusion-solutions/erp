package equ.srv.actions;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
import equ.api.terminal.TransactionTerminalInfo;
import lsfusion.base.Pair;
import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.erp.integration.DefaultIntegrationActionProperty;
import lsfusion.interop.Compare;
import lsfusion.interop.remote.RMIUtils;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.query.QueryBuilder;
import lsfusion.server.integration.*;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.util.*;

public class TransactionExchangeActionProperty extends DefaultIntegrationActionProperty {
    
    ScriptingLogicsModule retailCRMLM;
    ScriptingLogicsModule HTCPromotionLM;
    ScriptingLogicsModule itemFashionLM;
    ScriptingLogicsModule machineryPriceTransactionStockTaxLM;
    ScriptingLogicsModule zReportDiscountCardLM;
    
    public TransactionExchangeActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            retailCRMLM = context.getBL().getModule("RetailCRM");
            HTCPromotionLM = context.getBL().getModule("HTCPromotion");
            itemFashionLM = context.getBL().getModule("ItemFashion");
            machineryPriceTransactionStockTaxLM = context.getBL().getModule("MachineryPriceTransactionStockTax");
            zReportDiscountCardLM = context.getBL().getModule("ZReportDiscountCard");
            
            String sidEquipmentServer = (String) findProperty("sidEquipmentServerTransactionExchange").read(context);
            String serverHost = (String) findProperty("hostTransactionExchange").read(context);
            Integer connectPort = (Integer) findProperty("portTransactionExchange").read(context);
            String serverDB = (String) findProperty("serverDBTransactionExchange").read(context);
            EquipmentServerInterface remote = RMIUtils.rmiLookup(serverHost, connectPort, serverDB, "EquipmentServer");
            
            readTransactionInfo(context, remote, sidEquipmentServer);

            sendReceiptInfo(context, remote, sidEquipmentServer);
            
            readPromotionInfo(context, remote);
            
            readDiscountCard(context, remote);
            
        } catch (RemoteException e) {
            throw Throwables.propagate(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } catch (NotBoundException e) {
            throw Throwables.propagate(e);
        }
    }
    
    private void readTransactionInfo(ExecutionContext context, EquipmentServerInterface remote, String sidEquipmentServer) 
            throws RemoteException, SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        List<TransactionInfo> transactionList = remote.readTransactionInfo(sidEquipmentServer);

        List<List<List<Object>>> transactionData = getTransactionData(transactionList);
        importItemGroups(context, transactionData.get(0));
        importParentGroups(context, transactionData.get(1));
        importCashRegisterTransactionList(context, transactionData.get(2));
        importScalesTransactionList(context,  transactionData.get(3));
        importTerminalTransactionList(context, transactionData.get(4));
        importPriceCheckerTransactionList(context, transactionData.get(5));
    }

    private void sendReceiptInfo(ExecutionContext context, EquipmentServerInterface remote, String sidEquipmentServer)
            throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException, IOException {

        List<SalesInfo> salesInfoList = readSalesInfo(context);

        String result = remote.sendSalesInfo(salesInfoList, sidEquipmentServer, null);
        if (result == null) {
            List<String> succeededReceiptList = new ArrayList<String>();
            for (SalesInfo sale : salesInfoList) {
                succeededReceiptList.add(sale.getIdReceipt(null));
            }
            if (!succeededReceiptList.isEmpty())
                finishSendSalesInfo(context, succeededReceiptList);
        }
    }
    
    private void readPromotionInfo(ExecutionContext context, EquipmentServerInterface remote) 
            throws RemoteException, SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        
        if(HTCPromotionLM != null) {
            PromotionInfo promotionInfo = remote.readPromotionInfo();
            importPromotionQuantityList(context, promotionInfo.promotionQuantityList);
            importPromotionSumList(context, promotionInfo.promotionSumList);
            importPromotionTimeList(context, promotionInfo.promotionTimeList);
        }
        
    }
    
    private void readDiscountCard(ExecutionContext context, EquipmentServerInterface remote) throws RemoteException, SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {
        
        if(retailCRMLM != null) {

            List<DiscountCard> discountCardList = remote.readDiscountCardList();

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(discountCardList.size());

            ImportField idDiscountCardField = new ImportField(retailCRMLM.findProperty("idDiscountCard"));
            ImportKey<?> discountCardKey = new ImportKey((CustomClass) retailCRMLM.findClass("DiscountCard"),
                    retailCRMLM.findProperty("discountCardId").getMapping(idDiscountCardField));
            props.add(new ImportProperty(idDiscountCardField, retailCRMLM.findProperty("idDiscountCard").getMapping(discountCardKey)));
            keys.add(discountCardKey);
            fields.add(idDiscountCardField);
            for (int i = 0; i < discountCardList.size(); i++)
                data.get(i).add(discountCardList.get(i).idDiscountCard);

            ImportField numberDiscountCardField = new ImportField(retailCRMLM.findProperty("numberDiscountCard"));
            props.add(new ImportProperty(numberDiscountCardField, retailCRMLM.findProperty("numberDiscountCard").getMapping(discountCardKey)));
            fields.add(numberDiscountCardField);
            for (int i = 0; i < discountCardList.size(); i++)
                data.get(i).add(discountCardList.get(i).numberDiscountCard);

            ImportField nameDiscountCardField = new ImportField(retailCRMLM.findProperty("nameDiscountCard"));
            props.add(new ImportProperty(nameDiscountCardField, retailCRMLM.findProperty("nameDiscountCard").getMapping(discountCardKey)));
            fields.add(nameDiscountCardField);
            for (int i = 0; i < discountCardList.size(); i++)
                data.get(i).add(discountCardList.get(i).nameDiscountCard);

            ImportField percentDiscountCardField = new ImportField(retailCRMLM.findProperty("percentDiscountCard"));
            props.add(new ImportProperty(percentDiscountCardField, retailCRMLM.findProperty("percentDiscountCard").getMapping(discountCardKey)));
            fields.add(percentDiscountCardField);
            for (int i = 0; i < discountCardList.size(); i++)
                data.get(i).add(discountCardList.get(i).percentDiscountCard);

            ImportField dateDiscountCardField = new ImportField(retailCRMLM.findProperty("dateDiscountCard"));
            props.add(new ImportProperty(dateDiscountCardField, retailCRMLM.findProperty("dateDiscountCard").getMapping(discountCardKey)));
            fields.add(dateDiscountCardField);
            for (int i = 0; i < discountCardList.size(); i++)
                data.get(i).add(discountCardList.get(i).dateFromDiscountCard);

            ImportField dateToDiscountCardField = new ImportField(retailCRMLM.findProperty("dateToDiscountCard"));
            props.add(new ImportProperty(dateToDiscountCardField, retailCRMLM.findProperty("dateToDiscountCard").getMapping(discountCardKey)));
            fields.add(dateToDiscountCardField);
            for (int i = 0; i < discountCardList.size(); i++)
                data.get(i).add(discountCardList.get(i).dateToDiscountCard);

            ImportTable table = new ImportTable(fields, data);

            try (DataSession session = context.createSession()) {
                session.pushVolatileStats("TE_DC");
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context);
                session.popVolatileStats();
            }
        }
    }
    
    private void importPromotionQuantityList(ExecutionContext context, List<PromotionQuantity> promotionQuantityList) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        if (notNullNorEmpty(promotionQuantityList)) {

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(promotionQuantityList.size());
            
            ImportField idHTCPromotionQuantityField = new ImportField(HTCPromotionLM.findProperty("idHTCPromotionQuantity"));
            ImportKey<?> htcPromotionQuantityKey = new ImportKey((CustomClass) HTCPromotionLM.findClass("HTCPromotionQuantity"),
                    HTCPromotionLM.findProperty("HTCPromotionQuantityId").getMapping(idHTCPromotionQuantityField));
            keys.add(htcPromotionQuantityKey);
            props.add(new ImportProperty(idHTCPromotionQuantityField, HTCPromotionLM.findProperty("idHTCPromotionQuantity").getMapping(htcPromotionQuantityKey)));
            fields.add(idHTCPromotionQuantityField);
            for (int i = 0; i < promotionQuantityList.size(); i++)
                data.get(i).add(promotionQuantityList.get(i).idPromotionQuantity);

            ImportField isStopHTCPromotionQuantityField = new ImportField(HTCPromotionLM.findProperty("isStopHTCPromotionQuantity"));
            props.add(new ImportProperty(isStopHTCPromotionQuantityField, HTCPromotionLM.findProperty("isStopHTCPromotionQuantity").getMapping(htcPromotionQuantityKey)));
            fields.add(isStopHTCPromotionQuantityField);
            for (int i = 0; i < promotionQuantityList.size(); i++)
                data.get(i).add(promotionQuantityList.get(i).isStop ? true : null);

            ImportField quantityHTCPromotionQuantityField = new ImportField(HTCPromotionLM.findProperty("quantityHTCPromotionQuantity"));
            props.add(new ImportProperty(quantityHTCPromotionQuantityField, HTCPromotionLM.findProperty("quantityHTCPromotionQuantity").getMapping(htcPromotionQuantityKey)));
            fields.add(quantityHTCPromotionQuantityField);
            for (int i = 0; i < promotionQuantityList.size(); i++)
                data.get(i).add(promotionQuantityList.get(i).quantity);

            ImportField percentHTCPromotionQuantityField = new ImportField(HTCPromotionLM.findProperty("percentHTCPromotionQuantity"));
            props.add(new ImportProperty(percentHTCPromotionQuantityField, HTCPromotionLM.findProperty("percentHTCPromotionQuantity").getMapping(htcPromotionQuantityKey)));
            fields.add(percentHTCPromotionQuantityField);
            for (int i = 0; i < promotionQuantityList.size(); i++)
                data.get(i).add(promotionQuantityList.get(i).percent);

            ImportField idItemHTCPromotionQuantityField = new ImportField(HTCPromotionLM.findProperty("idItem"));
            ImportKey<?> itemKey = new ImportKey((CustomClass) HTCPromotionLM.findClass("Item"),
                    HTCPromotionLM.findProperty("itemId").getMapping(idItemHTCPromotionQuantityField));
            itemKey.skipKey = true;
            keys.add(itemKey);
            props.add(new ImportProperty(idItemHTCPromotionQuantityField, HTCPromotionLM.findProperty("itemHTCPromotionQuantity").getMapping(htcPromotionQuantityKey),
                    object(HTCPromotionLM.findClass("Item")).getMapping(itemKey)));
            fields.add(idItemHTCPromotionQuantityField);
            for (int i = 0; i < promotionQuantityList.size(); i++)
                data.get(i).add(promotionQuantityList.get(i).idItem);
            
            ImportTable table = new ImportTable(fields, data);

            try (DataSession session = context.createSession()) {
                session.pushVolatileStats("TE_PQ");
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context);
                session.popVolatileStats();
            }
        }
    }

    private void importPromotionSumList(ExecutionContext context, List<PromotionSum> promotionSumList) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        if (notNullNorEmpty(promotionSumList)) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(promotionSumList.size());
            
            ImportField idHTCPromotionSumField = new ImportField(HTCPromotionLM.findProperty("idHTCPromotionSum"));
            ImportKey<?> htcPromotionSumKey = new ImportKey((CustomClass) HTCPromotionLM.findClass("HTCPromotionSum"),
                    HTCPromotionLM.findProperty("HTCPromotionSumId").getMapping(idHTCPromotionSumField));
            keys.add(htcPromotionSumKey);
            props.add(new ImportProperty(idHTCPromotionSumField, HTCPromotionLM.findProperty("idHTCPromotionSum").getMapping(htcPromotionSumKey)));
            fields.add(idHTCPromotionSumField);
            for (int i = 0; i < promotionSumList.size(); i++)
                data.get(i).add(promotionSumList.get(i).idPromotionSum);

            ImportField isStopHTCPromotionSumField = new ImportField(HTCPromotionLM.findProperty("isStopHTCPromotionSum"));
            props.add(new ImportProperty(isStopHTCPromotionSumField, HTCPromotionLM.findProperty("isStopHTCPromotionSum").getMapping(htcPromotionSumKey)));
            fields.add(isStopHTCPromotionSumField);
            for (int i = 0; i < promotionSumList.size(); i++)
                data.get(i).add(promotionSumList.get(i).isStop ? true : null);

            ImportField sumHTCPromotionSumField = new ImportField(HTCPromotionLM.findProperty("sumHTCPromotionSum"));
            props.add(new ImportProperty(sumHTCPromotionSumField, HTCPromotionLM.findProperty("sumHTCPromotionSum").getMapping(htcPromotionSumKey)));
            fields.add(sumHTCPromotionSumField);
            for (int i = 0; i < promotionSumList.size(); i++)
                data.get(i).add(promotionSumList.get(i).sum);

            ImportField percentHTCPromotionSumField = new ImportField(HTCPromotionLM.findProperty("percentHTCPromotionSum"));
            props.add(new ImportProperty(percentHTCPromotionSumField, HTCPromotionLM.findProperty("percentHTCPromotionSum").getMapping(htcPromotionSumKey)));
            fields.add(percentHTCPromotionSumField);
            for (int i = 0; i < promotionSumList.size(); i++)
                data.get(i).add(promotionSumList.get(i).percent);

            ImportTable table = new ImportTable(fields, data);

            try (DataSession session = context.createSession()) {
                session.pushVolatileStats("TE_PS");
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context);
                session.popVolatileStats();
            }
        }
    }

    private void importPromotionTimeList(ExecutionContext context, List<PromotionTime> promotionTimeList) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        if (notNullNorEmpty(promotionTimeList)) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            List<List<Object>> data = initData(promotionTimeList.size());
            
            ImportField idHTCPromotionTimeField = new ImportField(HTCPromotionLM.findProperty("idHTCPromotionTime"));
            ImportKey<?> htcPromotionTimeKey = new ImportKey((CustomClass) HTCPromotionLM.findClass("HTCPromotionTime"),
                    HTCPromotionLM.findProperty("HTCPromotionTimeId").getMapping(idHTCPromotionTimeField));
            keys.add(htcPromotionTimeKey);
            props.add(new ImportProperty(idHTCPromotionTimeField, HTCPromotionLM.findProperty("idHTCPromotionTime").getMapping(htcPromotionTimeKey)));
            fields.add(idHTCPromotionTimeField);
            for (int i = 0; i < promotionTimeList.size(); i++)
                data.get(i).add(promotionTimeList.get(i).idPromotionTime);

            ImportField isStopHTCPromotionTimeField = new ImportField(HTCPromotionLM.findProperty("isStopHTCPromotionTime"));
            props.add(new ImportProperty(isStopHTCPromotionTimeField, HTCPromotionLM.findProperty("isStopHTCPromotionTime").getMapping(htcPromotionTimeKey)));
            fields.add(isStopHTCPromotionTimeField);
            for (int i = 0; i < promotionTimeList.size(); i++)
                data.get(i).add(promotionTimeList.get(i).isStop ? true : null);

            ImportField beginTimeHTCPromotionTimeField = new ImportField(HTCPromotionLM.findProperty("beginTimeHTCPromotionTime"));
            props.add(new ImportProperty(beginTimeHTCPromotionTimeField, HTCPromotionLM.findProperty("beginTimeHTCPromotionTime").getMapping(htcPromotionTimeKey)));
            fields.add(beginTimeHTCPromotionTimeField);
            for (int i = 0; i < promotionTimeList.size(); i++)
                data.get(i).add(promotionTimeList.get(i).beginTime);

            ImportField endTimeHTCPromotionTimeField = new ImportField(HTCPromotionLM.findProperty("endTimeHTCPromotionTime"));
            props.add(new ImportProperty(endTimeHTCPromotionTimeField, HTCPromotionLM.findProperty("endTimeHTCPromotionTime").getMapping(htcPromotionTimeKey)));
            fields.add(endTimeHTCPromotionTimeField);
            for (int i = 0; i < promotionTimeList.size(); i++)
                data.get(i).add(promotionTimeList.get(i).endTime);

            ImportField percentHTCPromotionTimeField = new ImportField(HTCPromotionLM.findProperty("percentHTCPromotionTime"));
            props.add(new ImportProperty(percentHTCPromotionTimeField, HTCPromotionLM.findProperty("percentHTCPromotionTime").getMapping(htcPromotionTimeKey)));
            fields.add(percentHTCPromotionTimeField);
            for (int i = 0; i < promotionTimeList.size(); i++)
                data.get(i).add(promotionTimeList.get(i).percent);

            ImportField numberDayHTCPromotionQuantityField = new ImportField(HTCPromotionLM.findProperty("numberDOW"));
            ImportKey<?> dowKey = new ImportKey((CustomClass) HTCPromotionLM.findClass("DOW"),
                    HTCPromotionLM.findProperty("DOWNumber").getMapping(numberDayHTCPromotionQuantityField));
            keys.add(dowKey);
            props.add(new ImportProperty(numberDayHTCPromotionQuantityField, HTCPromotionLM.findProperty("dayHTCPromotionTime").getMapping(htcPromotionTimeKey),
                    object(HTCPromotionLM.findClass("DOW")).getMapping(dowKey)));
            fields.add(numberDayHTCPromotionQuantityField);
            for (int i = 0; i < promotionTimeList.size(); i++)
                data.get(i).add(promotionTimeList.get(i).numberDay);

            ImportTable table = new ImportTable(fields, data);

            try (DataSession session = context.createSession()) {
                session.pushVolatileStats("TE_PT");
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context);
                session.popVolatileStats();
            }
        }
    }
    
    private List<SalesInfo> readSalesInfo(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        Map<Object, List<Pair<String, BigDecimal>>> paymentMap = readPaymentMap(context);

        KeyExpr receiptExpr = new KeyExpr("receipt");
        KeyExpr receiptDetailExpr = new KeyExpr("receiptDetail");
        ImRevMap<Object, KeyExpr> receiptKeys = MapFact.toRevMap((Object) "receipt", receiptExpr, "receiptDetail", receiptDetailExpr);

        QueryBuilder<Object, Object> receiptQuery = new QueryBuilder<Object, Object>(receiptKeys);

        String[] receiptNames = new String[]{"numberZReportReceipt", "numberReceipt", "dateReceipt", "timeReceipt", "discountSumReceipt", "idEmployeeReceipt",
                "firstNameEmployeeReceipt", "lastNameEmployeeReceipt", "numberGroupCashRegisterReceipt", "numberCashRegisterReceipt"};
        LCP<?>[] receiptProperties = findProperties("numberZReportReceipt", "numberReceipt", "dateReceipt", "timeReceipt", "discountSumReceipt", "idEmployeeReceipt",
                "firstNameEmployeeReceipt", "lastNameEmployeeReceipt", "numberGroupCashRegisterReceipt", "numberCashRegisterReceipt");
        for (int j = 0; j < receiptProperties.length; j++) {
            receiptQuery.addProperty(receiptNames[j], receiptProperties[j].getExpr(receiptExpr));
        }
        if(zReportDiscountCardLM != null) {
            receiptQuery.addProperty("seriesNumberDiscountCardReceipt", zReportDiscountCardLM.findProperty("seriesNumberDiscountCardReceipt").getExpr(receiptExpr));
        }

        String[] receiptDetailNames = new String[]{"idBarcodeReceiptDetail", "quantityReceiptDetail", "priceReceiptDetail", "sumReceiptDetail", 
                "discountSumReceiptDetail", "typeReceiptDetail", "numberReceiptDetail", "idSkuReceiptDetail"};
        LCP<?>[] receiptDetailProperties = findProperties("idBarcodeReceiptDetail", "quantityReceiptDetail", "priceReceiptDetail", "sumReceiptDetail", 
                "discountSumReceiptDetail", "typeReceiptDetail", "numberReceiptDetail", "idSkuReceiptDetail");
        for (int j = 0; j < receiptDetailProperties.length; j++) {
            receiptQuery.addProperty(receiptDetailNames[j], receiptDetailProperties[j].getExpr(receiptDetailExpr));
        }

        receiptQuery.and(findProperty("notExportedIncrementReceipt").getExpr(receiptExpr).getWhere());
        receiptQuery.and(findProperty("receiptReceiptDetail").getExpr(receiptDetailExpr).compare(receiptExpr, Compare.EQUALS));

        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> receiptResult = receiptQuery.executeClasses(context);

        List<SalesInfo> salesInfoList = new ArrayList<SalesInfo>();
        for (int i = 0, size = receiptResult.size(); i < size; i++) {
            Object receiptObject = receiptResult.getKey(i).get("receipt").object;
            ImMap<Object, ObjectValue> entry = receiptResult.getValue(i);

            BigDecimal sumCard = null;
            BigDecimal sumCash = null;
            BigDecimal sumGiftCard = null;
            List<Pair<String, BigDecimal>> paymentList = paymentMap.get(receiptObject);
            if(paymentList != null) {
                for (Pair<String, BigDecimal> payment : paymentList) {
                    String sidPayment = payment.first;
                    BigDecimal sumPayment = payment.second;
                    if (sidPayment.equals("card"))
                        sumCard = safeAdd(sumCard, sumPayment);
                    else if (sidPayment.equals("cash"))
                        sumCash = safeAdd(sumCash, sumPayment);
                    else if (sidPayment.equals("giftcard"))
                        sumGiftCard = safeAdd(sumGiftCard, sumPayment);
                    else sumCash = safeAdd(sumCash, sumPayment);
                }
            }

            String numberZReport = trim((String) entry.get("numberZReportReceipt").getValue());
            Integer numberReceipt = (Integer) entry.get("numberReceipt").getValue();
            Date dateReceipt = (Date) entry.get("dateReceipt").getValue();
            Time timeReceipt = (Time) entry.get("timeReceipt").getValue();
            BigDecimal discountSumReceipt = (BigDecimal) entry.get("discountSumReceipt").getValue();
            String idEmployee = trim((String) entry.get("idEmployeeReceipt").getValue());
            String firstNameContact = trim((String) entry.get("firstNameEmployeeReceipt").getValue());
            String lastNameContact = trim((String) entry.get("lastNameEmployeeReceipt").getValue());
            Integer nppGroupMachinery = (Integer) entry.get("numberGroupCashRegisterReceipt").getValue();
            Integer nppMachinery = (Integer) entry.get("numberCashRegisterReceipt").getValue();
            String seriesNumberDiscountCard = zReportDiscountCardLM == null ? null : trim((String) entry.get("seriesNumberDiscountCardReceipt").getValue());

            String barcodeReceiptDetail = trim((String) entry.get("idBarcodeReceiptDetail").getValue());
            BigDecimal quantityReceiptDetail = (BigDecimal) entry.get("quantityReceiptDetail").getValue();
            BigDecimal priceReceiptDetail = (BigDecimal) entry.get("priceReceiptDetail").getValue();
            BigDecimal sumReceiptDetail = (BigDecimal) entry.get("sumReceiptDetail").getValue();
            BigDecimal discountSumReceiptDetail = (BigDecimal) entry.get("discountSumReceiptDetail").getValue();
            Integer numberReceiptDetail = (Integer) entry.get("numberReceiptDetail").getValue();
            String typeReceiptDetail = trim((String) entry.get("typeReceiptDetail").getValue());
            boolean isGiftCard = typeReceiptDetail != null && typeReceiptDetail.equals("Сертификат");

            salesInfoList.add(new SalesInfo(isGiftCard, nppGroupMachinery, nppMachinery, numberZReport, numberReceipt,
                    dateReceipt, timeReceipt, idEmployee, firstNameContact, lastNameContact, sumCard, sumCash, sumGiftCard,
                    barcodeReceiptDetail, null, quantityReceiptDetail, priceReceiptDetail, sumReceiptDetail, discountSumReceiptDetail,
                    discountSumReceipt, seriesNumberDiscountCard, numberReceiptDetail, null));
        }
        return salesInfoList;
    }
    
    private Map<Object, List<Pair<String, BigDecimal>>> readPaymentMap(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        KeyExpr paymentExpr = new KeyExpr("payment");
        KeyExpr receiptExpr = new KeyExpr("receipt");
        ImRevMap<Object, KeyExpr> paymentKeys = MapFact.toRevMap((Object) "payment", paymentExpr, "receipt", receiptExpr);

        QueryBuilder<Object, Object> paymentQuery = new QueryBuilder<Object, Object>(paymentKeys);

        String[] paymentNames = new String[]{"sidPaymentTypePayment", "sumPayment"};
        LCP<?>[] paymentProperties = findProperties("sidPaymentTypePayment", "sumPayment");
        for (int j = 0; j < paymentProperties.length; j++) {
            paymentQuery.addProperty(paymentNames[j], paymentProperties[j].getExpr(paymentExpr));
        }
        paymentQuery.and(findProperty("sumPayment").getExpr(paymentExpr).getWhere());
        paymentQuery.and(findProperty("receiptPayment").getExpr(paymentExpr).compare(receiptExpr, Compare.EQUALS));

        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> paymentResult = paymentQuery.executeClasses(context);

        Map<Object, List<Pair<String, BigDecimal>>> paymentMap = new HashMap<Object, List<Pair<String, BigDecimal>>>();
        for (int i = 0, size = paymentResult.size(); i < size; i++) {
            Object receiptObject = paymentResult.getKey(i).get("receipt").object;
            ImMap<Object, ObjectValue> entryValue = paymentResult.getValue(i);
            String sidPayment = trim((String) entryValue.get("sidPaymentTypePayment").getValue());
            BigDecimal sumPayment = (BigDecimal) entryValue.get("sumPayment").getValue();
            
            List<Pair<String, BigDecimal>> paymentEntry = paymentMap.containsKey(receiptObject) ? paymentMap.get(receiptObject) : new ArrayList<Pair<String, BigDecimal>>();
            paymentEntry.add(Pair.create(sidPayment, sumPayment));
            paymentMap.put(receiptObject, paymentEntry);
        }
        return paymentMap;
    }

    public void finishSendSalesInfo(ExecutionContext context, List<String> succeededReceiptList) throws IOException, SQLException {
        try {
            DataSession session = context.createSession();
            for (String idReceipt : succeededReceiptList) {
                ObjectValue receiptObject = findProperty("receiptId").readClasses(session, new DataObject(idReceipt));
                if (receiptObject instanceof DataObject)
                    findProperty("exportedIncrementReceipt").change(true, session, (DataObject) receiptObject);
            }
            session.apply(context.getBL());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
    
    private List<List<List<Object>>> getTransactionData(List<TransactionInfo> transactionList) {
        List<List<Object>> itemGroupData = new ArrayList<List<Object>>();
        List<List<Object>> parentGroupData = new ArrayList<List<Object>>();
        List<List<Object>> cashRegisterData = new ArrayList<List<Object>>();
        List<List<Object>> scalesData = new ArrayList<List<Object>>();
        List<List<Object>> terminalData = new ArrayList<List<Object>>();
        List<List<Object>> priceCheckerData = new ArrayList<List<Object>>();

        for(TransactionInfo transaction : transactionList) {

            String idTransaction = String.valueOf(transaction.id);
            Date dateTransaction = transaction.date == null ? null : new Date(transaction.date.getTime());
            Time timeTransaction = dateTransaction == null ? null : new Time(dateTransaction.getTime());
            List<ItemInfo> itemsListTransaction = transaction.itemsList;
            Boolean snapshotTransaction = format(transaction.snapshot);

            if(transaction.itemGroupMap != null) {
                for (Object itemGroupListEntry : transaction.itemGroupMap.values()) {
                    List<ItemGroup> itemGroupList = (List<ItemGroup>) itemGroupListEntry;
                    for (ItemGroup itemGroup : itemGroupList) {
                        itemGroupData.add(Arrays.asList((Object) itemGroup.idItemGroup, itemGroup.nameItemGroup));
                        parentGroupData.add(Arrays.asList((Object) itemGroup.idItemGroup, itemGroup.idParentItemGroup));
                    }
                }
            }
            
            if(transaction instanceof TransactionCashRegisterInfo) {
                for(ItemInfo itemInfo : itemsListTransaction) {
                    CashRegisterItemInfo item = (CashRegisterItemInfo) itemInfo;
                    if (itemFashionLM == null)
                        cashRegisterData.add(Arrays.asList((Object) idTransaction, transaction.nppGroupMachinery, dateTransaction, timeTransaction, snapshotTransaction,
                                transaction.description, item.idItem, item.idBarcode, item.name, item.idBrand, item.nameBrand,
                                item.price, format(item.splitItem), item.daysExpiry, item.expiryDate, item.description, item.pluNumber, item.flags, item.idUOM, 
                                item.shortNameUOM, format(item.passScalesItem), item.vat, item.idItemGroup, true));
                    else
                        cashRegisterData.add(Arrays.asList((Object) idTransaction, transaction.nppGroupMachinery, dateTransaction, timeTransaction, snapshotTransaction,
                                transaction.description, item.idItem, item.idBarcode, item.name, item.idBrand, item.nameBrand, item.idSeason, item.nameSeason,
                                item.price, format(item.splitItem), item.daysExpiry, item.expiryDate, item.description, item.pluNumber, item.flags, item.idUOM, 
                                item.shortNameUOM, format(item.passScalesItem), item.vat, item.idItemGroup, true));
                }
            } else if(transaction instanceof TransactionScalesInfo) {               
                for(ItemInfo itemInfo : itemsListTransaction) {
                    ScalesItemInfo item = (ScalesItemInfo) itemInfo;
                    scalesData.add(Arrays.asList((Object) idTransaction, transaction.nppGroupMachinery, dateTransaction, timeTransaction, snapshotTransaction,
                            transaction.description, item.idItem, item.idBarcode, item.name, item.price, format(item.splitItem), item.daysExpiry, item.hoursExpiry, item.expiryDate,
                            item.description, item.pluNumber, item.flags, format(item.passScalesItem), item.vat, item.idUOM, item.shortNameUOM, item.labelFormat, item.idItemGroup, true));
                }
            } else if(transaction instanceof TransactionTerminalInfo) {               
                for(ItemInfo item : itemsListTransaction) {
                    terminalData.add(Arrays.asList((Object) idTransaction, transaction.nppGroupMachinery, dateTransaction, timeTransaction, snapshotTransaction,
                            transaction.description, item.idItem, item.idBarcode, item.name, format(item.splitItem), item.daysExpiry,
                            item.pluNumber, item.flags, item.price, true));
                }
            } else if(transaction instanceof TransactionPriceCheckerInfo) {
                for(ItemInfo item : itemsListTransaction) {
                    priceCheckerData.add(Arrays.asList((Object) idTransaction, transaction.nppGroupMachinery, dateTransaction, timeTransaction, snapshotTransaction,
                            transaction.description, item.idItem, item.idBarcode, item.name, format(item.splitItem), item.daysExpiry, 
                            item.pluNumber, item.flags, item.price, true));
                }
            }
        }
        return Arrays.asList(itemGroupData, parentGroupData, cashRegisterData, scalesData, terminalData, priceCheckerData);
    }

    private void importItemGroups(ExecutionContext context, List<List<Object>> data) throws SQLException, ScriptingErrorLog.SemanticErrorException, SQLHandledException {

        if (notNullNorEmpty(data)) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            ImportField idItemGroupField = new ImportField(findProperty("idItemGroup"));
            ImportKey<?> itemGroupKey = new ImportKey((CustomClass) findClass("ItemGroup"),
                    findProperty("itemGroupId").getMapping(idItemGroupField));
            keys.add(itemGroupKey);
            props.add(new ImportProperty(idItemGroupField, findProperty("idItemGroup").getMapping(itemGroupKey)));
            fields.add(idItemGroupField);

            ImportField itemGroupNameField = new ImportField(findProperty("nameItemGroup"));
            props.add(new ImportProperty(itemGroupNameField, findProperty("nameItemGroup").getMapping(itemGroupKey)));
            fields.add(itemGroupNameField);

            ImportTable table = new ImportTable(fields, data);

            try (DataSession session = context.createSession()) {
                session.pushVolatileStats("TE_IG");
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context);
                session.popVolatileStats();
            }
        }
    }

    private void importParentGroups(ExecutionContext context, List<List<Object>> data) throws ScriptingErrorLog.SemanticErrorException, SQLHandledException, SQLException {
        if (notNullNorEmpty(data)) {
            
            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();
            
            ImportField idItemGroupField = new ImportField(findProperty("idItemGroup"));
            ImportKey<?> itemGroupKey = new ImportKey((CustomClass) findClass("ItemGroup"),
                    findProperty("itemGroupId").getMapping(idItemGroupField));
            keys.add(itemGroupKey);
            fields.add(idItemGroupField);

            ImportField idParentGroupField = new ImportField(findProperty("idItemGroup"));
            ImportKey<?> parentGroupKey = new ImportKey((CustomClass) findClass("ItemGroup"),
                    findProperty("itemGroupId").getMapping(idParentGroupField));
            keys.add(parentGroupKey);
            props.add(new ImportProperty(idParentGroupField, findProperty("parentItemGroup").getMapping(itemGroupKey),
                    LM.object(findClass("ItemGroup")).getMapping(parentGroupKey)));
            fields.add(idParentGroupField);

            ImportTable table = new ImportTable(fields, data);

            try (DataSession session = context.createSession()) {
                session.pushVolatileStats("TE_PG");
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context);
                session.popVolatileStats();
            }
        }
    }

    

    private void importCashRegisterTransactionList(ExecutionContext context, List<List<Object>> cashRegisterData) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        if (notNullNorEmpty(cashRegisterData)) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            ImportField idMachineryPriceTransactionField = new ImportField(findProperty("idMachineryPriceTransaction"));
            ImportKey<?> machineryPriceTransactionKey = new ImportKey((CustomClass) findClass("CashRegisterPriceTransaction"),
                    findProperty("machineryPriceTransactionId").getMapping(idMachineryPriceTransactionField));
            keys.add(machineryPriceTransactionKey);
            props.add(new ImportProperty(idMachineryPriceTransactionField, findProperty("idMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(idMachineryPriceTransactionField);

            ImportField nppGroupMachineryField = new ImportField(findProperty("nppGroupMachinery"));
            ImportKey<?> groupCashRegisterKey = new ImportKey((CustomClass) findClass("GroupCashRegister"),
                    findProperty("groupCashRegisterNpp").getMapping(nppGroupMachineryField));
            groupCashRegisterKey.skipKey = true;
            keys.add(groupCashRegisterKey);
            props.add(new ImportProperty(nppGroupMachineryField, findProperty("groupMachineryMachineryPriceTransaction").getMapping(machineryPriceTransactionKey),
                    object(findClass("GroupCashRegister")).getMapping(groupCashRegisterKey)));
            fields.add(nppGroupMachineryField);

            ImportField dateMachineryPriceTransactionField = new ImportField(findProperty("dateMachineryPriceTransaction"));
            props.add(new ImportProperty(dateMachineryPriceTransactionField, findProperty("dateMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(dateMachineryPriceTransactionField);

            ImportField timeMachineryPriceTransactionField = new ImportField(findProperty("timeMachineryPriceTransaction"));
            props.add(new ImportProperty(timeMachineryPriceTransactionField, findProperty("timeMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(timeMachineryPriceTransactionField);

            ImportField snapshotMachineryPriceTransactionField = new ImportField(findProperty("snapshotMachineryPriceTransaction"));
            props.add(new ImportProperty(snapshotMachineryPriceTransactionField, findProperty("snapshotMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(snapshotMachineryPriceTransactionField);

            ImportField commentMachineryPriceTransactionField = new ImportField(findProperty("commentMachineryPriceTransaction"));
            props.add(new ImportProperty(commentMachineryPriceTransactionField, findProperty("commentMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(commentMachineryPriceTransactionField);

            ImportField idItemField = new ImportField(findProperty("idItem"));
            ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                    findProperty("itemId").getMapping(idItemField));
            keys.add(itemKey);
            props.add(new ImportProperty(idItemField, findProperty("idItem").getMapping(itemKey)));
            fields.add(idItemField);
            
            ImportField extIdBarcodeField = new ImportField(findProperty("extIdBarcode"));
            ImportKey<?> barcodeKey = new ImportKey((CustomClass) findClass("Barcode"),
                    findProperty("extBarcodeId").getMapping(extIdBarcodeField));
            keys.add(barcodeKey);
            props.add(new ImportProperty(idItemField, findProperty("skuBarcode").getMapping(barcodeKey),
                    object(findClass("Item")).getMapping(itemKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("extIdBarcode").getMapping(barcodeKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("idBarcode").getMapping(barcodeKey)));
            fields.add(extIdBarcodeField);

            ImportField captionItemField = new ImportField(findProperty("captionItem"));
            props.add(new ImportProperty(captionItemField, findProperty("captionItem").getMapping(itemKey), true));
            props.add(new ImportProperty(captionItemField, findProperty("nameMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(captionItemField);

            ImportField idBrandField = new ImportField(findProperty("idBrand"));
            ImportKey<?> brandKey = new ImportKey((CustomClass) findClass("Brand"),
                    findProperty("brandId").getMapping(idBrandField));
            keys.add(brandKey);
            props.add(new ImportProperty(idBrandField, findProperty("idBrand").getMapping(brandKey)));
            props.add(new ImportProperty(idBrandField, findProperty("brandItem").getMapping(itemKey),
                    object(findClass("Brand")).getMapping(brandKey), true));
            fields.add(idBrandField);

            ImportField nameBrandField = new ImportField(findProperty("nameBrand"));
            props.add(new ImportProperty(nameBrandField, findProperty("nameBrand").getMapping(brandKey), true));
            fields.add(nameBrandField);

            if(itemFashionLM != null) {
                ImportField idSeasonField = new ImportField(itemFashionLM.findProperty("idSeason"));
                ImportKey<?> seasonKey = new ImportKey((CustomClass) itemFashionLM.findClass("Season"),
                        itemFashionLM.findProperty("seasonId").getMapping(idSeasonField));
                props.add(new ImportProperty(idSeasonField, itemFashionLM.findProperty("idSeason").getMapping(seasonKey)));
                keys.add(seasonKey);
                props.add(new ImportProperty(idSeasonField, itemFashionLM.findProperty("seasonItem").getMapping(itemKey),
                        object(itemFashionLM.findClass("Season")).getMapping(seasonKey), true));
                fields.add(idSeasonField);

                ImportField nameSeasonField = new ImportField(itemFashionLM.findProperty("nameSeason"));
                props.add(new ImportProperty(nameSeasonField, itemFashionLM.findProperty("nameSeason").getMapping(seasonKey), true));
                fields.add(nameSeasonField);
            }
            
            ImportField priceMachineryPriceTransactionBarcodeField = new ImportField(findProperty("priceMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(priceMachineryPriceTransactionBarcodeField, findProperty("priceMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(priceMachineryPriceTransactionBarcodeField);

            ImportField splitMachineryPriceTransactionBarcodeField = new ImportField(findProperty("splitMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(splitMachineryPriceTransactionBarcodeField, findProperty("splitMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(splitMachineryPriceTransactionBarcodeField);

            ImportField expiryDaysMachineryPriceTransactionBarcodeField = new ImportField(findProperty("expiryDaysMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(expiryDaysMachineryPriceTransactionBarcodeField, findProperty("expiryDaysMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(expiryDaysMachineryPriceTransactionBarcodeField);
            
            ImportField expiryDateMachineryPriceTransactionBarcodeField = new ImportField(findProperty("expiryDateMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(expiryDateMachineryPriceTransactionBarcodeField, findProperty("expiryDateMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(expiryDateMachineryPriceTransactionBarcodeField);

            ImportField descriptionMachineryPriceTransactionBarcodeField = new ImportField(findProperty("descriptionMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(descriptionMachineryPriceTransactionBarcodeField, findProperty("descriptionMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(descriptionMachineryPriceTransactionBarcodeField);

            ImportField pluNumberMachineryPriceTransactionBarcodeField = new ImportField(findProperty("pluNumberMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(pluNumberMachineryPriceTransactionBarcodeField, findProperty("pluNumberMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(pluNumberMachineryPriceTransactionBarcodeField);

            ImportField flagsMachineryPriceTransactionBarcodeField = new ImportField(findProperty("flagsMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(flagsMachineryPriceTransactionBarcodeField, findProperty("flagsMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(flagsMachineryPriceTransactionBarcodeField);

            ImportField idUOMField = new ImportField(findProperty("idUOM"));
            ImportKey<?> UOMKey = new ImportKey((CustomClass) findClass("UOM"),
                    findProperty("UOMId").getMapping(idUOMField));
            UOMKey.skipKey = true;
            keys.add(UOMKey);
            props.add(new ImportProperty(idUOMField, findProperty("idUOM").getMapping(UOMKey)));
            props.add(new ImportProperty(idUOMField, findProperty("idUOMMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(idUOMField);

            ImportField shortNameUOMField = new ImportField(findProperty("shortNameUOM"));
            props.add(new ImportProperty(shortNameUOMField, findProperty("shortNameUOM").getMapping(UOMKey), true));
            props.add(new ImportProperty(shortNameUOMField, findProperty("shortNameUOMMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(shortNameUOMField);

            ImportField passScalesMachineryPriceTransactionBarcodeField = new ImportField(findProperty("passScalesMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(passScalesMachineryPriceTransactionBarcodeField, findProperty("passScalesMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(passScalesMachineryPriceTransactionBarcodeField);

            DataObject defaultCountryObject = (DataObject) findProperty("defaultCountry").readClasses(context);
            ImportField valueVATItemCountryDateField = new ImportField(findProperty("valueVATItemCountryDate"));
            ImportKey<?> VATKey = new ImportKey((CustomClass) findClass("Range"),
                    findProperty("valueCurrentVATDefaultValue").getMapping(valueVATItemCountryDateField));
            VATKey.skipKey = true;
            keys.add(VATKey);
            props.add(new ImportProperty(valueVATItemCountryDateField, findProperty("VATItemCountry").getMapping(itemKey, defaultCountryObject),
                    object(findClass("Range")).getMapping(VATKey)));
            if(machineryPriceTransactionStockTaxLM != null)
                props.add(new ImportProperty(valueVATItemCountryDateField, machineryPriceTransactionStockTaxLM.findProperty("VATMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(valueVATItemCountryDateField);

            ImportField idItemGroupField = new ImportField(findProperty("idItemGroup"));
            ImportKey<?> itemGroupKey = new ImportKey((CustomClass) findClass("ItemGroup"),
                    findProperty("itemGroupId").getMapping(idItemGroupField));
            keys.add(itemGroupKey);
            props.add(new ImportProperty(idItemGroupField, findProperty("skuGroupMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey),
                    object(findClass("ItemGroup")).getMapping(itemGroupKey)));
            props.add(new ImportProperty(idItemGroupField, findProperty("itemGroupItem").getMapping(itemKey),
                    object(findClass("ItemGroup")).getMapping(itemGroupKey)));
            fields.add(idItemGroupField);
                        
            ImportField inMachineryPriceTransactionBarcodeField = new ImportField(findProperty("inMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(inMachineryPriceTransactionBarcodeField, findProperty("inMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(inMachineryPriceTransactionBarcodeField);
            
            ImportTable table = new ImportTable(fields, cashRegisterData);

            try (DataSession session = context.createSession()) {
                session.pushVolatileStats("TE_CT");
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context);
                session.popVolatileStats();
            }
        }
    }

    private void importScalesTransactionList(ExecutionContext context, List<List<Object>> scalesData) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        if (notNullNorEmpty(scalesData)) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            
            ImportField idMachineryPriceTransactionField = new ImportField(findProperty("idMachineryPriceTransaction"));
            ImportKey<?> machineryPriceTransactionKey = new ImportKey((CustomClass) findClass("ScalesPriceTransaction"),
                    findProperty("machineryPriceTransactionId").getMapping(idMachineryPriceTransactionField));
            keys.add(machineryPriceTransactionKey);
            props.add(new ImportProperty(idMachineryPriceTransactionField, findProperty("idMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(idMachineryPriceTransactionField);

            ImportField nppGroupMachineryField = new ImportField(findProperty("nppGroupMachinery"));
            ImportKey<?> groupScalesKey = new ImportKey((CustomClass) findClass("GroupScales"),
                    findProperty("groupScalesNpp").getMapping(nppGroupMachineryField));
            groupScalesKey.skipKey = true;
            keys.add(groupScalesKey);
            props.add(new ImportProperty(nppGroupMachineryField, findProperty("groupMachineryMachineryPriceTransaction").getMapping(machineryPriceTransactionKey),
                    object(findClass("GroupScales")).getMapping(groupScalesKey)));
            fields.add(nppGroupMachineryField);

            ImportField dateMachineryPriceTransactionField = new ImportField(findProperty("dateMachineryPriceTransaction"));
            props.add(new ImportProperty(dateMachineryPriceTransactionField, findProperty("dateMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(dateMachineryPriceTransactionField);

            ImportField timeMachineryPriceTransactionField = new ImportField(findProperty("timeMachineryPriceTransaction"));
            props.add(new ImportProperty(timeMachineryPriceTransactionField, findProperty("timeMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(timeMachineryPriceTransactionField);

            ImportField snapshotMachineryPriceTransactionField = new ImportField(findProperty("snapshotMachineryPriceTransaction"));
            props.add(new ImportProperty(snapshotMachineryPriceTransactionField, findProperty("snapshotMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(snapshotMachineryPriceTransactionField);

            ImportField commentMachineryPriceTransactionField = new ImportField(findProperty("commentMachineryPriceTransaction"));
            props.add(new ImportProperty(commentMachineryPriceTransactionField, findProperty("commentMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(commentMachineryPriceTransactionField);

            ImportField idItemField = new ImportField(findProperty("idItem"));
            ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                    findProperty("itemId").getMapping(idItemField));
            keys.add(itemKey);
            props.add(new ImportProperty(idItemField, findProperty("idItem").getMapping(itemKey)));
            fields.add(idItemField);

            ImportField extIdBarcodeField = new ImportField(findProperty("extIdBarcode"));
            ImportKey<?> barcodeKey = new ImportKey((CustomClass) findClass("Barcode"),
                    findProperty("extBarcodeId").getMapping(extIdBarcodeField));
            keys.add(barcodeKey);
            props.add(new ImportProperty(idItemField, findProperty("skuBarcode").getMapping(barcodeKey),
                    object(findClass("Item")).getMapping(itemKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("extIdBarcode").getMapping(barcodeKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("idBarcode").getMapping(barcodeKey)));
            fields.add(extIdBarcodeField);
            
            ImportField captionItemField = new ImportField(findProperty("captionItem"));
            props.add(new ImportProperty(captionItemField, findProperty("captionItem").getMapping(itemKey), true));
            props.add(new ImportProperty(captionItemField, findProperty("nameMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(captionItemField);

            ImportField priceMachineryPriceTransactionBarcodeField = new ImportField(findProperty("priceMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(priceMachineryPriceTransactionBarcodeField, findProperty("priceMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(priceMachineryPriceTransactionBarcodeField);
            
            ImportField splitMachineryPriceTransactionBarcodeField = new ImportField(findProperty("splitMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(splitMachineryPriceTransactionBarcodeField, findProperty("splitMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(splitMachineryPriceTransactionBarcodeField);

            ImportField expiryDaysMachineryPriceTransactionBarcodeField = new ImportField(findProperty("expiryDaysMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(expiryDaysMachineryPriceTransactionBarcodeField, findProperty("expiryDaysMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(expiryDaysMachineryPriceTransactionBarcodeField);

            ImportField hoursExpiryMachineryPriceTransactionBarcodeField = new ImportField(findProperty("hoursExpiryMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(hoursExpiryMachineryPriceTransactionBarcodeField, findProperty("hoursExpiryMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(hoursExpiryMachineryPriceTransactionBarcodeField);

            ImportField expiryDateMachineryPriceTransactionBarcodeField = new ImportField(findProperty("expiryDateMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(expiryDateMachineryPriceTransactionBarcodeField, findProperty("expiryDateMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(expiryDateMachineryPriceTransactionBarcodeField);
            
            ImportField descriptionMachineryPriceTransactionBarcodeField = new ImportField(findProperty("descriptionMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(descriptionMachineryPriceTransactionBarcodeField, findProperty("descriptionMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(descriptionMachineryPriceTransactionBarcodeField);

            ImportField pluNumberMachineryPriceTransactionBarcodeField = new ImportField(findProperty("pluNumberMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(pluNumberMachineryPriceTransactionBarcodeField, findProperty("pluNumberMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(pluNumberMachineryPriceTransactionBarcodeField);

            ImportField flagsMachineryPriceTransactionBarcodeField = new ImportField(findProperty("flagsMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(flagsMachineryPriceTransactionBarcodeField, findProperty("flagsMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(flagsMachineryPriceTransactionBarcodeField);
            
            ImportField passScalesMachineryPriceTransactionBarcodeField = new ImportField(findProperty("passScalesMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(passScalesMachineryPriceTransactionBarcodeField, findProperty("passScalesMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(passScalesMachineryPriceTransactionBarcodeField);

            DataObject defaultCountryObject = (DataObject) findProperty("defaultCountry").readClasses(context);
            ImportField valueVATItemCountryDateField = new ImportField(findProperty("valueVATItemCountryDate"));
            ImportKey<?> VATKey = new ImportKey((CustomClass) findClass("Range"),
                    findProperty("valueCurrentVATDefaultValue").getMapping(valueVATItemCountryDateField));
            VATKey.skipKey = true;
            keys.add(VATKey);
            props.add(new ImportProperty(valueVATItemCountryDateField, findProperty("VATItemCountry").getMapping(itemKey, defaultCountryObject),
                    object(findClass("Range")).getMapping(VATKey)));
            if(machineryPriceTransactionStockTaxLM != null)
                props.add(new ImportProperty(valueVATItemCountryDateField, machineryPriceTransactionStockTaxLM.findProperty("VATMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(valueVATItemCountryDateField);
            
            ImportField idUOMField = new ImportField(findProperty("idUOM"));
            ImportKey<?> UOMKey = new ImportKey((CustomClass) findClass("UOM"),
                    findProperty("UOMId").getMapping(idUOMField));
            UOMKey.skipKey = true;
            keys.add(UOMKey);
            props.add(new ImportProperty(idUOMField, findProperty("idUOM").getMapping(UOMKey)));
            props.add(new ImportProperty(idUOMField, findProperty("idUOMMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(idUOMField);

            ImportField shortNameUOMField = new ImportField(findProperty("shortNameUOM"));
            props.add(new ImportProperty(shortNameUOMField, findProperty("shortNameUOM").getMapping(UOMKey), true));
            props.add(new ImportProperty(shortNameUOMField, findProperty("shortNameUOMMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(shortNameUOMField);
            
            ImportField labelFormatMachineryPriceTransactionBarcodeField = new ImportField(findProperty("labelFormatMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(labelFormatMachineryPriceTransactionBarcodeField, findProperty("labelFormatMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(labelFormatMachineryPriceTransactionBarcodeField);

            ImportField idItemGroupField = new ImportField(findProperty("idItemGroup"));
            ImportKey<?> itemGroupKey = new ImportKey((CustomClass) findClass("ItemGroup"),
                    findProperty("itemGroupId").getMapping(idItemGroupField));
            keys.add(itemGroupKey);
            props.add(new ImportProperty(idItemGroupField, findProperty("skuGroupMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey),
                    object(findClass("ItemGroup")).getMapping(itemGroupKey)));
            props.add(new ImportProperty(idItemGroupField, findProperty("itemGroupItem").getMapping(itemKey),
                    object(findClass("ItemGroup")).getMapping(itemGroupKey)));
            fields.add(idItemGroupField);
            
            ImportField inMachineryPriceTransactionBarcodeField = new ImportField(findProperty("inMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(inMachineryPriceTransactionBarcodeField, findProperty("inMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(inMachineryPriceTransactionBarcodeField);

            ImportTable table = new ImportTable(fields, scalesData);

            try (DataSession session = context.createSession()) {
                session.pushVolatileStats("TE_ST");
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context);
                session.popVolatileStats();
            }
        }
    }

    private void importTerminalTransactionList(ExecutionContext context, List<List<Object>> terminalData) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        if (notNullNorEmpty(terminalData)) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            ImportField idMachineryPriceTransactionField = new ImportField(findProperty("idMachineryPriceTransaction"));
            ImportKey<?> machineryPriceTransactionKey = new ImportKey((CustomClass) findClass("TerminalPriceTransaction"),
                    findProperty("machineryPriceTransactionId").getMapping(idMachineryPriceTransactionField));
            keys.add(machineryPriceTransactionKey);
            props.add(new ImportProperty(idMachineryPriceTransactionField, findProperty("idMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(idMachineryPriceTransactionField);

            ImportField nppGroupMachineryField = new ImportField(findProperty("nppGroupMachinery"));
            ImportKey<?> groupTerminalKey = new ImportKey((CustomClass) findClass("GroupTerminal"),
                    findProperty("groupTerminalNpp").getMapping(nppGroupMachineryField));
            groupTerminalKey.skipKey = true;
            keys.add(groupTerminalKey);
            props.add(new ImportProperty(nppGroupMachineryField, findProperty("groupMachineryMachineryPriceTransaction").getMapping(machineryPriceTransactionKey),
                    object(findClass("GroupTerminal")).getMapping(groupTerminalKey)));
            fields.add(nppGroupMachineryField);

            ImportField dateMachineryPriceTransactionField = new ImportField(findProperty("dateMachineryPriceTransaction"));
            props.add(new ImportProperty(dateMachineryPriceTransactionField, findProperty("dateMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(dateMachineryPriceTransactionField);

            ImportField timeMachineryPriceTransactionField = new ImportField(findProperty("timeMachineryPriceTransaction"));
            props.add(new ImportProperty(timeMachineryPriceTransactionField, findProperty("timeMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(timeMachineryPriceTransactionField);

            ImportField snapshotMachineryPriceTransactionField = new ImportField(findProperty("snapshotMachineryPriceTransaction"));
            props.add(new ImportProperty(snapshotMachineryPriceTransactionField, findProperty("snapshotMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(snapshotMachineryPriceTransactionField);

            ImportField commentMachineryPriceTransactionField = new ImportField(findProperty("commentMachineryPriceTransaction"));
            props.add(new ImportProperty(commentMachineryPriceTransactionField, findProperty("commentMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(commentMachineryPriceTransactionField);

            ImportField idItemField = new ImportField(findProperty("idItem"));
            ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                    findProperty("itemId").getMapping(idItemField));
            keys.add(itemKey);
            props.add(new ImportProperty(idItemField, findProperty("idItem").getMapping(itemKey)));
            fields.add(idItemField);
            
            ImportField extIdBarcodeField = new ImportField(findProperty("extIdBarcode"));
            ImportKey<?> barcodeKey = new ImportKey((CustomClass) findClass("Barcode"),
                    findProperty("extBarcodeId").getMapping(extIdBarcodeField));
            keys.add(barcodeKey);
            props.add(new ImportProperty(idItemField, findProperty("skuBarcode").getMapping(barcodeKey),
                    object(findClass("Item")).getMapping(itemKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("extIdBarcode").getMapping(barcodeKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("idBarcode").getMapping(barcodeKey)));
            fields.add(extIdBarcodeField);

            ImportField captionItemField = new ImportField(findProperty("captionItem"));
            props.add(new ImportProperty(captionItemField, findProperty("captionItem").getMapping(itemKey), true));
            props.add(new ImportProperty(captionItemField, findProperty("nameMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(captionItemField);

            ImportField splitMachineryPriceTransactionBarcodeField = new ImportField(findProperty("splitMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(splitMachineryPriceTransactionBarcodeField, findProperty("splitMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(splitMachineryPriceTransactionBarcodeField);

            ImportField expiryDaysMachineryPriceTransactionBarcodeField = new ImportField(findProperty("expiryDaysMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(expiryDaysMachineryPriceTransactionBarcodeField, findProperty("expiryDaysMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(expiryDaysMachineryPriceTransactionBarcodeField);

            ImportField pluNumberMachineryPriceTransactionBarcodeField = new ImportField(findProperty("pluNumberMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(pluNumberMachineryPriceTransactionBarcodeField, findProperty("pluNumberMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(pluNumberMachineryPriceTransactionBarcodeField);

            ImportField flagsMachineryPriceTransactionBarcodeField = new ImportField(findProperty("flagsMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(flagsMachineryPriceTransactionBarcodeField, findProperty("flagsMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(flagsMachineryPriceTransactionBarcodeField);
            
            ImportField priceMachineryPriceTransactionBarcodeField = new ImportField(findProperty("priceMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(priceMachineryPriceTransactionBarcodeField, findProperty("priceMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(priceMachineryPriceTransactionBarcodeField);
            
            ImportField inMachineryPriceTransactionBarcodeField = new ImportField(findProperty("inMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(inMachineryPriceTransactionBarcodeField, findProperty("inMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(inMachineryPriceTransactionBarcodeField);

            ImportTable table = new ImportTable(fields, terminalData);

            try (DataSession session = context.createSession()) {
                session.pushVolatileStats("TE_TT");
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context);
                session.popVolatileStats();
            }
        }
    }

    private void importPriceCheckerTransactionList(ExecutionContext context, List<List<Object>> priceCheckerData) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {

        if (notNullNorEmpty(priceCheckerData)) {

            List<ImportProperty<?>> props = new ArrayList<ImportProperty<?>>();
            List<ImportField> fields = new ArrayList<ImportField>();
            List<ImportKey<?>> keys = new ArrayList<ImportKey<?>>();

            ImportField idMachineryPriceTransactionField = new ImportField(findProperty("idMachineryPriceTransaction"));
            ImportKey<?> machineryPriceTransactionKey = new ImportKey((CustomClass) findClass("ScalesPriceTransaction"),
                    findProperty("machineryPriceTransactionId").getMapping(idMachineryPriceTransactionField));
            keys.add(machineryPriceTransactionKey);
            props.add(new ImportProperty(idMachineryPriceTransactionField, findProperty("idMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(idMachineryPriceTransactionField);

            ImportField nppGroupMachineryField = new ImportField(findProperty("nppGroupMachinery"));
            ImportKey<?> groupPriceCheckerKey = new ImportKey((CustomClass) findClass("GroupPriceChecker"),
                    findProperty("groupPriceCheckerNpp").getMapping(nppGroupMachineryField));
            groupPriceCheckerKey.skipKey = true;
            keys.add(groupPriceCheckerKey);
            props.add(new ImportProperty(nppGroupMachineryField, findProperty("groupMachineryMachineryPriceTransaction").getMapping(machineryPriceTransactionKey),
                    object(findClass("GroupPriceChecker")).getMapping(groupPriceCheckerKey)));
            fields.add(nppGroupMachineryField);

            ImportField dateMachineryPriceTransactionField = new ImportField(findProperty("dateMachineryPriceTransaction"));
            props.add(new ImportProperty(dateMachineryPriceTransactionField, findProperty("dateMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(dateMachineryPriceTransactionField);

            ImportField timeMachineryPriceTransactionField = new ImportField(findProperty("timeMachineryPriceTransaction"));
            props.add(new ImportProperty(timeMachineryPriceTransactionField, findProperty("timeMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(timeMachineryPriceTransactionField);

            ImportField snapshotMachineryPriceTransactionField = new ImportField(findProperty("snapshotMachineryPriceTransaction"));
            props.add(new ImportProperty(snapshotMachineryPriceTransactionField, findProperty("snapshotMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(snapshotMachineryPriceTransactionField);

            ImportField commentMachineryPriceTransactionField = new ImportField(findProperty("commentMachineryPriceTransaction"));
            props.add(new ImportProperty(commentMachineryPriceTransactionField, findProperty("commentMachineryPriceTransaction").getMapping(machineryPriceTransactionKey)));
            fields.add(commentMachineryPriceTransactionField);

            ImportField idItemField = new ImportField(findProperty("idItem"));
            ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                    findProperty("itemId").getMapping(idItemField));
            keys.add(itemKey);
            props.add(new ImportProperty(idItemField, findProperty("idItem").getMapping(itemKey)));
            fields.add(idItemField);
            
            ImportField extIdBarcodeField = new ImportField(findProperty("extIdBarcode"));
            ImportKey<?> barcodeKey = new ImportKey((CustomClass) findClass("Barcode"),
                    findProperty("extBarcodeId").getMapping(extIdBarcodeField));
            keys.add(barcodeKey);
            props.add(new ImportProperty(idItemField, findProperty("skuBarcode").getMapping(barcodeKey),
                    object(findClass("Item")).getMapping(itemKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("extIdBarcode").getMapping(barcodeKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("idBarcode").getMapping(barcodeKey)));
            fields.add(extIdBarcodeField);

            ImportField captionItemField = new ImportField(findProperty("captionItem"));
            props.add(new ImportProperty(captionItemField, findProperty("captionItem").getMapping(itemKey), true));
            props.add(new ImportProperty(captionItemField, findProperty("nameMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(captionItemField);

            ImportField splitMachineryPriceTransactionBarcodeField = new ImportField(findProperty("splitMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(splitMachineryPriceTransactionBarcodeField, findProperty("splitMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(splitMachineryPriceTransactionBarcodeField);

            ImportField expiryDaysMachineryPriceTransactionBarcodeField = new ImportField(findProperty("expiryDaysMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(expiryDaysMachineryPriceTransactionBarcodeField, findProperty("expiryDaysMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(expiryDaysMachineryPriceTransactionBarcodeField);

            ImportField pluNumberMachineryPriceTransactionBarcodeField = new ImportField(findProperty("pluNumberMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(pluNumberMachineryPriceTransactionBarcodeField, findProperty("pluNumberMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(pluNumberMachineryPriceTransactionBarcodeField);

            ImportField flagsMachineryPriceTransactionBarcodeField = new ImportField(findProperty("flagsMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(flagsMachineryPriceTransactionBarcodeField, findProperty("flagsMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(flagsMachineryPriceTransactionBarcodeField);
            
            ImportField priceMachineryPriceTransactionBarcodeField = new ImportField(findProperty("priceMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(priceMachineryPriceTransactionBarcodeField, findProperty("priceMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(priceMachineryPriceTransactionBarcodeField);
            
            ImportField inMachineryPriceTransactionBarcodeField = new ImportField(findProperty("inMachineryPriceTransactionBarcode"));
            props.add(new ImportProperty(inMachineryPriceTransactionBarcodeField, findProperty("inMachineryPriceTransactionBarcode").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(inMachineryPriceTransactionBarcodeField);
            
            ImportTable table = new ImportTable(fields, priceCheckerData);

            try (DataSession session = context.createSession()) {
                session.pushVolatileStats("TE_PT");
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context);
                session.popVolatileStats();
            }
        }
    }
    
    private Boolean format(Boolean value) {
        return value != null && value ? true : null;
    }

    protected List<List<Object>> initData(int size) {
        List<List<Object>> data = new ArrayList<List<Object>>();
        for (int i = 0; i < size; i++) {
            data.add(new ArrayList<Object>());
        }
        return data;
    }


}

