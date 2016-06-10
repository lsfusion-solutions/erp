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
            
            String sidEquipmentServer = (String) findProperty("sidEquipmentServerTransactionExchange[]").read(context);
            String serverHost = (String) findProperty("hostTransactionExchange[]").read(context);
            Integer connectPort = (Integer) findProperty("portTransactionExchange[]").read(context);
            String serverDB = (String) findProperty("serverDBTransactionExchange[]").read(context);
            EquipmentServerInterface remote = RMIUtils.rmiLookup(serverHost, connectPort, serverDB, "EquipmentServer");
            
            readTransactionInfo(context, remote, sidEquipmentServer);

            sendReceiptInfo(context, remote, sidEquipmentServer);
            
            readPromotionInfo(context, remote);
            
            readDiscountCard(context, remote);
            
        } catch (ScriptingErrorLog.SemanticErrorException | NotBoundException | IOException e) {
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
            List<String> succeededReceiptList = new ArrayList<>();
            for (SalesInfo sale : salesInfoList) {
                succeededReceiptList.add(sale.getIdReceipt(null, null));
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

            List<DiscountCard> discountCardList = remote.readDiscountCardList(null, null);

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(discountCardList.size());

            ImportField idDiscountCardField = new ImportField(retailCRMLM.findProperty("id[DiscountCard]"));
            ImportKey<?> discountCardKey = new ImportKey((CustomClass) retailCRMLM.findClass("DiscountCard"),
                    retailCRMLM.findProperty("discountCard[VARSTRING[100]]").getMapping(idDiscountCardField));
            props.add(new ImportProperty(idDiscountCardField, retailCRMLM.findProperty("id[DiscountCard]").getMapping(discountCardKey)));
            keys.add(discountCardKey);
            fields.add(idDiscountCardField);
            for (int i = 0; i < discountCardList.size(); i++)
                data.get(i).add(discountCardList.get(i).idDiscountCard);

            ImportField numberDiscountCardField = new ImportField(retailCRMLM.findProperty("number[DiscountCard]"));
            props.add(new ImportProperty(numberDiscountCardField, retailCRMLM.findProperty("number[DiscountCard]").getMapping(discountCardKey)));
            fields.add(numberDiscountCardField);
            for (int i = 0; i < discountCardList.size(); i++)
                data.get(i).add(discountCardList.get(i).numberDiscountCard);

            ImportField nameDiscountCardField = new ImportField(retailCRMLM.findProperty("name[DiscountCard]"));
            props.add(new ImportProperty(nameDiscountCardField, retailCRMLM.findProperty("name[DiscountCard]").getMapping(discountCardKey)));
            fields.add(nameDiscountCardField);
            for (int i = 0; i < discountCardList.size(); i++)
                data.get(i).add(discountCardList.get(i).nameDiscountCard);

            ImportField percentDiscountCardField = new ImportField(retailCRMLM.findProperty("percent[DiscountCard]"));
            props.add(new ImportProperty(percentDiscountCardField, retailCRMLM.findProperty("percent[DiscountCard]").getMapping(discountCardKey)));
            fields.add(percentDiscountCardField);
            for (int i = 0; i < discountCardList.size(); i++)
                data.get(i).add(discountCardList.get(i).percentDiscountCard);

            ImportField dateDiscountCardField = new ImportField(retailCRMLM.findProperty("date[DiscountCard]"));
            props.add(new ImportProperty(dateDiscountCardField, retailCRMLM.findProperty("date[DiscountCard]").getMapping(discountCardKey)));
            fields.add(dateDiscountCardField);
            for (int i = 0; i < discountCardList.size(); i++)
                data.get(i).add(discountCardList.get(i).dateFromDiscountCard);

            ImportField dateToDiscountCardField = new ImportField(retailCRMLM.findProperty("dateTo[DiscountCard]"));
            props.add(new ImportProperty(dateToDiscountCardField, retailCRMLM.findProperty("dateTo[DiscountCard]").getMapping(discountCardKey)));
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
            
            ImportField idHTCPromotionQuantityField = new ImportField(HTCPromotionLM.findProperty("id[HTCPromotionQuantity]"));
            ImportKey<?> htcPromotionQuantityKey = new ImportKey((CustomClass) HTCPromotionLM.findClass("HTCPromotionQuantity"),
                    HTCPromotionLM.findProperty("HTCPromotionQuantity[VARSTRING[100]]").getMapping(idHTCPromotionQuantityField));
            keys.add(htcPromotionQuantityKey);
            props.add(new ImportProperty(idHTCPromotionQuantityField, HTCPromotionLM.findProperty("id[HTCPromotionQuantity]").getMapping(htcPromotionQuantityKey)));
            fields.add(idHTCPromotionQuantityField);
            for (int i = 0; i < promotionQuantityList.size(); i++)
                data.get(i).add(promotionQuantityList.get(i).idPromotionQuantity);

            ImportField isStopHTCPromotionQuantityField = new ImportField(HTCPromotionLM.findProperty("isStop[HTCPromotionQuantity]"));
            props.add(new ImportProperty(isStopHTCPromotionQuantityField, HTCPromotionLM.findProperty("isStop[HTCPromotionQuantity]").getMapping(htcPromotionQuantityKey)));
            fields.add(isStopHTCPromotionQuantityField);
            for (int i = 0; i < promotionQuantityList.size(); i++)
                data.get(i).add(promotionQuantityList.get(i).isStop ? true : null);

            ImportField quantityHTCPromotionQuantityField = new ImportField(HTCPromotionLM.findProperty("quantity[HTCPromotionQuantity]"));
            props.add(new ImportProperty(quantityHTCPromotionQuantityField, HTCPromotionLM.findProperty("quantity[HTCPromotionQuantity]").getMapping(htcPromotionQuantityKey)));
            fields.add(quantityHTCPromotionQuantityField);
            for (int i = 0; i < promotionQuantityList.size(); i++)
                data.get(i).add(promotionQuantityList.get(i).quantity);

            ImportField percentHTCPromotionQuantityField = new ImportField(HTCPromotionLM.findProperty("percent[HTCPromotionQuantity]"));
            props.add(new ImportProperty(percentHTCPromotionQuantityField, HTCPromotionLM.findProperty("percent[HTCPromotionQuantity]").getMapping(htcPromotionQuantityKey)));
            fields.add(percentHTCPromotionQuantityField);
            for (int i = 0; i < promotionQuantityList.size(); i++)
                data.get(i).add(promotionQuantityList.get(i).percent);

            ImportField idItemHTCPromotionQuantityField = new ImportField(HTCPromotionLM.findProperty("id[Item]"));
            ImportKey<?> itemKey = new ImportKey((CustomClass) HTCPromotionLM.findClass("Item"),
                    HTCPromotionLM.findProperty("item[VARSTRING[100]]").getMapping(idItemHTCPromotionQuantityField));
            itemKey.skipKey = true;
            keys.add(itemKey);
            props.add(new ImportProperty(idItemHTCPromotionQuantityField, HTCPromotionLM.findProperty("item[HTCPromotionQuantity]").getMapping(htcPromotionQuantityKey),
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

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(promotionSumList.size());
            
            ImportField idHTCPromotionSumField = new ImportField(HTCPromotionLM.findProperty("id[HTCPromotionSum]"));
            ImportKey<?> htcPromotionSumKey = new ImportKey((CustomClass) HTCPromotionLM.findClass("HTCPromotionSum"),
                    HTCPromotionLM.findProperty("HTCPromotionSum[VARSTRING[100]]").getMapping(idHTCPromotionSumField));
            keys.add(htcPromotionSumKey);
            props.add(new ImportProperty(idHTCPromotionSumField, HTCPromotionLM.findProperty("id[HTCPromotionSum]").getMapping(htcPromotionSumKey)));
            fields.add(idHTCPromotionSumField);
            for (int i = 0; i < promotionSumList.size(); i++)
                data.get(i).add(promotionSumList.get(i).idPromotionSum);

            ImportField isStopHTCPromotionSumField = new ImportField(HTCPromotionLM.findProperty("isStop[HTCPromotionSum]"));
            props.add(new ImportProperty(isStopHTCPromotionSumField, HTCPromotionLM.findProperty("isStop[HTCPromotionSum]").getMapping(htcPromotionSumKey)));
            fields.add(isStopHTCPromotionSumField);
            for (int i = 0; i < promotionSumList.size(); i++)
                data.get(i).add(promotionSumList.get(i).isStop ? true : null);

            ImportField sumHTCPromotionSumField = new ImportField(HTCPromotionLM.findProperty("sum[HTCPromotionSum]"));
            props.add(new ImportProperty(sumHTCPromotionSumField, HTCPromotionLM.findProperty("sum[HTCPromotionSum]").getMapping(htcPromotionSumKey)));
            fields.add(sumHTCPromotionSumField);
            for (int i = 0; i < promotionSumList.size(); i++)
                data.get(i).add(promotionSumList.get(i).sum);

            ImportField percentHTCPromotionSumField = new ImportField(HTCPromotionLM.findProperty("percent[HTCPromotionSum]"));
            props.add(new ImportProperty(percentHTCPromotionSumField, HTCPromotionLM.findProperty("percent[HTCPromotionSum]").getMapping(htcPromotionSumKey)));
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

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            List<List<Object>> data = initData(promotionTimeList.size());
            
            ImportField idHTCPromotionTimeField = new ImportField(HTCPromotionLM.findProperty("id[HTCPromotionTime]"));
            ImportKey<?> htcPromotionTimeKey = new ImportKey((CustomClass) HTCPromotionLM.findClass("HTCPromotionTime"),
                    HTCPromotionLM.findProperty("HTCPromotionTime[VARSTRING[100]]").getMapping(idHTCPromotionTimeField));
            keys.add(htcPromotionTimeKey);
            props.add(new ImportProperty(idHTCPromotionTimeField, HTCPromotionLM.findProperty("id[HTCPromotionTime]").getMapping(htcPromotionTimeKey)));
            fields.add(idHTCPromotionTimeField);
            for (int i = 0; i < promotionTimeList.size(); i++)
                data.get(i).add(promotionTimeList.get(i).idPromotionTime);

            ImportField isStopHTCPromotionTimeField = new ImportField(HTCPromotionLM.findProperty("isStop[HTCPromotionTime]"));
            props.add(new ImportProperty(isStopHTCPromotionTimeField, HTCPromotionLM.findProperty("isStop[HTCPromotionTime]").getMapping(htcPromotionTimeKey)));
            fields.add(isStopHTCPromotionTimeField);
            for (int i = 0; i < promotionTimeList.size(); i++)
                data.get(i).add(promotionTimeList.get(i).isStop ? true : null);

            ImportField beginTimeHTCPromotionTimeField = new ImportField(HTCPromotionLM.findProperty("beginTime[HTCPromotionTime]"));
            props.add(new ImportProperty(beginTimeHTCPromotionTimeField, HTCPromotionLM.findProperty("beginTime[HTCPromotionTime]").getMapping(htcPromotionTimeKey)));
            fields.add(beginTimeHTCPromotionTimeField);
            for (int i = 0; i < promotionTimeList.size(); i++)
                data.get(i).add(promotionTimeList.get(i).beginTime);

            ImportField endTimeHTCPromotionTimeField = new ImportField(HTCPromotionLM.findProperty("endTime[HTCPromotionTime]"));
            props.add(new ImportProperty(endTimeHTCPromotionTimeField, HTCPromotionLM.findProperty("endTime[HTCPromotionTime]").getMapping(htcPromotionTimeKey)));
            fields.add(endTimeHTCPromotionTimeField);
            for (int i = 0; i < promotionTimeList.size(); i++)
                data.get(i).add(promotionTimeList.get(i).endTime);

            ImportField percentHTCPromotionTimeField = new ImportField(HTCPromotionLM.findProperty("percent[HTCPromotionTime]"));
            props.add(new ImportProperty(percentHTCPromotionTimeField, HTCPromotionLM.findProperty("percent[HTCPromotionTime]").getMapping(htcPromotionTimeKey)));
            fields.add(percentHTCPromotionTimeField);
            for (int i = 0; i < promotionTimeList.size(); i++)
                data.get(i).add(promotionTimeList.get(i).percent);

            ImportField numberDayHTCPromotionQuantityField = new ImportField(HTCPromotionLM.findProperty("number[DOW]"));
            ImportKey<?> dowKey = new ImportKey((CustomClass) HTCPromotionLM.findClass("DOW"),
                    HTCPromotionLM.findProperty("DOW[INTEGER]").getMapping(numberDayHTCPromotionQuantityField));
            keys.add(dowKey);
            props.add(new ImportProperty(numberDayHTCPromotionQuantityField, HTCPromotionLM.findProperty("day[HTCPromotionTime]").getMapping(htcPromotionTimeKey),
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

        QueryBuilder<Object, Object> receiptQuery = new QueryBuilder<>(receiptKeys);

        String[] receiptNames = new String[]{"numberZReportReceipt", "numberReceipt", "dateReceipt", "timeReceipt", "discountSumReceipt", "idEmployeeReceipt",
                "firstNameEmployeeReceipt", "lastNameEmployeeReceipt", "numberGroupCashRegisterReceipt", "numberCashRegisterReceipt"};
        LCP<?>[] receiptProperties = findProperties("numberZReport[Receipt]", "number[Receipt]", "date[Receipt]", "time[Receipt]", "discountSum[Receipt]", "idEmployee[Receipt]",
                "firstNameEmployee[Receipt]", "lastNameEmployee[Receipt]", "numberGroupCashRegister[Receipt]", "numberCashRegister[Receipt]");
        for (int j = 0; j < receiptProperties.length; j++) {
            receiptQuery.addProperty(receiptNames[j], receiptProperties[j].getExpr(receiptExpr));
        }
        if(zReportDiscountCardLM != null) {
            receiptQuery.addProperty("seriesNumberDiscountCardReceipt", zReportDiscountCardLM.findProperty("seriesNumberDiscountCard[Receipt]").getExpr(receiptExpr));
        }

        String[] receiptDetailNames = new String[]{"idBarcodeReceiptDetail", "quantityReceiptDetail", "priceReceiptDetail", "sumReceiptDetail", 
                "discountSumReceiptDetail", "typeReceiptDetail", "numberReceiptDetail", "idSkuReceiptDetail"};
        LCP<?>[] receiptDetailProperties = findProperties("idBarcode[ReceiptDetail]", "quantity[ReceiptDetail]", "price[ReceiptDetail]", "sum[ReceiptDetail]",
                "discountSum[ReceiptDetail]", "type[ReceiptDetail]", "number[ReceiptDetail]", "idSku[ReceiptDetail]");
        for (int j = 0; j < receiptDetailProperties.length; j++) {
            receiptQuery.addProperty(receiptDetailNames[j], receiptDetailProperties[j].getExpr(receiptDetailExpr));
        }

        receiptQuery.and(findProperty("notExportedIncrement[Receipt]").getExpr(receiptExpr).getWhere());
        receiptQuery.and(findProperty("receipt[ReceiptDetail]").getExpr(receiptDetailExpr).compare(receiptExpr, Compare.EQUALS));

        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> receiptResult = receiptQuery.executeClasses(context);

        List<SalesInfo> salesInfoList = new ArrayList<>();
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

            salesInfoList.add(new SalesInfo(isGiftCard, nppGroupMachinery, nppMachinery, numberZReport, dateReceipt, timeReceipt, numberReceipt,
                    dateReceipt, timeReceipt, idEmployee, firstNameContact, lastNameContact, sumCard, sumCash, sumGiftCard,
                    barcodeReceiptDetail, null, null, null, quantityReceiptDetail, priceReceiptDetail, sumReceiptDetail, discountSumReceiptDetail,
                    discountSumReceipt, seriesNumberDiscountCard, numberReceiptDetail, null, null));
        }
        return salesInfoList;
    }
    
    private Map<Object, List<Pair<String, BigDecimal>>> readPaymentMap(ExecutionContext context) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        KeyExpr paymentExpr = new KeyExpr("payment");
        KeyExpr receiptExpr = new KeyExpr("receipt");
        ImRevMap<Object, KeyExpr> paymentKeys = MapFact.toRevMap((Object) "payment", paymentExpr, "receipt", receiptExpr);

        QueryBuilder<Object, Object> paymentQuery = new QueryBuilder<>(paymentKeys);

        String[] paymentNames = new String[]{"sidPaymentTypePayment", "sumPayment"};
        LCP<?>[] paymentProperties = findProperties("sidPaymentType[Payment]", "sum[Payment]");
        for (int j = 0; j < paymentProperties.length; j++) {
            paymentQuery.addProperty(paymentNames[j], paymentProperties[j].getExpr(paymentExpr));
        }
        paymentQuery.and(findProperty("sum[Payment]").getExpr(paymentExpr).getWhere());
        paymentQuery.and(findProperty("receipt[Payment]").getExpr(paymentExpr).compare(receiptExpr, Compare.EQUALS));

        ImOrderMap<ImMap<Object, DataObject>, ImMap<Object, ObjectValue>> paymentResult = paymentQuery.executeClasses(context);

        Map<Object, List<Pair<String, BigDecimal>>> paymentMap = new HashMap<>();
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
        try (DataSession session = context.createSession()) {
            for (String idReceipt : succeededReceiptList) {
                ObjectValue receiptObject = findProperty("receipt[VARSTRING[100]]").readClasses(session, new DataObject(idReceipt));
                if (receiptObject instanceof DataObject)
                    findProperty("exportedIncrement[Receipt]").change(true, session, (DataObject) receiptObject);
            }
            session.apply(context.getBL());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
    
    private List<List<List<Object>>> getTransactionData(List<TransactionInfo> transactionList) {
        List<List<Object>> itemGroupData = new ArrayList<>();
        List<List<Object>> parentGroupData = new ArrayList<>();
        List<List<Object>> cashRegisterData = new ArrayList<>();
        List<List<Object>> scalesData = new ArrayList<>();
        List<List<Object>> terminalData = new ArrayList<>();
        List<List<Object>> priceCheckerData = new ArrayList<>();

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

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            ImportField idItemGroupField = new ImportField(findProperty("id[ItemGroup]"));
            ImportKey<?> itemGroupKey = new ImportKey((CustomClass) findClass("ItemGroup"),
                    findProperty("itemGroup[VARSTRING[100]]").getMapping(idItemGroupField));
            keys.add(itemGroupKey);
            props.add(new ImportProperty(idItemGroupField, findProperty("id[ItemGroup]").getMapping(itemGroupKey)));
            fields.add(idItemGroupField);

            ImportField itemGroupNameField = new ImportField(findProperty("name[ItemGroup]"));
            props.add(new ImportProperty(itemGroupNameField, findProperty("name[ItemGroup]").getMapping(itemGroupKey)));
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
            
            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();
            
            ImportField idItemGroupField = new ImportField(findProperty("id[ItemGroup]"));
            ImportKey<?> itemGroupKey = new ImportKey((CustomClass) findClass("ItemGroup"),
                    findProperty("itemGroup[VARSTRING[100]]").getMapping(idItemGroupField));
            keys.add(itemGroupKey);
            fields.add(idItemGroupField);

            ImportField idParentGroupField = new ImportField(findProperty("id[ItemGroup]"));
            ImportKey<?> parentGroupKey = new ImportKey((CustomClass) findClass("ItemGroup"),
                    findProperty("itemGroup[VARSTRING[100]]").getMapping(idParentGroupField));
            keys.add(parentGroupKey);
            props.add(new ImportProperty(idParentGroupField, findProperty("parent[ItemGroup]").getMapping(itemGroupKey),
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

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            ImportField idMachineryPriceTransactionField = new ImportField(findProperty("id[MachineryPriceTransaction]"));
            ImportKey<?> machineryPriceTransactionKey = new ImportKey((CustomClass) findClass("CashRegisterPriceTransaction"),
                    findProperty("machineryPriceTransaction[VARSTRING[100]]").getMapping(idMachineryPriceTransactionField));
            keys.add(machineryPriceTransactionKey);
            props.add(new ImportProperty(idMachineryPriceTransactionField, findProperty("id[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey)));
            fields.add(idMachineryPriceTransactionField);

            ImportField nppGroupMachineryField = new ImportField(findProperty("npp[GroupMachinery]"));
            ImportKey<?> groupCashRegisterKey = new ImportKey((CustomClass) findClass("GroupCashRegister"),
                    findProperty("groupCashRegister[INTEGER]").getMapping(nppGroupMachineryField));
            groupCashRegisterKey.skipKey = true;
            keys.add(groupCashRegisterKey);
            props.add(new ImportProperty(nppGroupMachineryField, findProperty("groupMachinery[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey),
                    object(findClass("GroupCashRegister")).getMapping(groupCashRegisterKey)));
            fields.add(nppGroupMachineryField);

            ImportField dateMachineryPriceTransactionField = new ImportField(findProperty("date[MachineryPriceTransaction]"));
            props.add(new ImportProperty(dateMachineryPriceTransactionField, findProperty("date[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey)));
            fields.add(dateMachineryPriceTransactionField);

            ImportField timeMachineryPriceTransactionField = new ImportField(findProperty("time[MachineryPriceTransaction]"));
            props.add(new ImportProperty(timeMachineryPriceTransactionField, findProperty("time[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey)));
            fields.add(timeMachineryPriceTransactionField);

            ImportField snapshotMachineryPriceTransactionField = new ImportField(findProperty("snapshot[MachineryPriceTransaction]"));
            props.add(new ImportProperty(snapshotMachineryPriceTransactionField, findProperty("snapshot[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey)));
            fields.add(snapshotMachineryPriceTransactionField);

            ImportField commentMachineryPriceTransactionField = new ImportField(findProperty("comment[MachineryPriceTransaction]"));
            props.add(new ImportProperty(commentMachineryPriceTransactionField, findProperty("comment[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey)));
            fields.add(commentMachineryPriceTransactionField);

            ImportField idItemField = new ImportField(findProperty("id[Item]"));
            ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                    findProperty("item[VARSTRING[100]]").getMapping(idItemField));
            keys.add(itemKey);
            props.add(new ImportProperty(idItemField, findProperty("id[Item]").getMapping(itemKey)));
            fields.add(idItemField);
            
            ImportField extIdBarcodeField = new ImportField(findProperty("extId[Barcode]"));
            ImportKey<?> barcodeKey = new ImportKey((CustomClass) findClass("Barcode"),
                    findProperty("extBarcode[VARSTRING[100]]").getMapping(extIdBarcodeField));
            keys.add(barcodeKey);
            props.add(new ImportProperty(idItemField, findProperty("sku[Barcode]").getMapping(barcodeKey),
                    object(findClass("Item")).getMapping(itemKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("extId[Barcode]").getMapping(barcodeKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("id[Barcode]").getMapping(barcodeKey)));
            fields.add(extIdBarcodeField);

            ImportField captionItemField = new ImportField(findProperty("caption[Item]"));
            props.add(new ImportProperty(captionItemField, findProperty("caption[Item]").getMapping(itemKey), true));
            props.add(new ImportProperty(captionItemField, findProperty("name[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(captionItemField);

            ImportField idBrandField = new ImportField(findProperty("id[Brand]"));
            ImportKey<?> brandKey = new ImportKey((CustomClass) findClass("Brand"),
                    findProperty("brand[VARSTRING[100]]").getMapping(idBrandField));
            keys.add(brandKey);
            props.add(new ImportProperty(idBrandField, findProperty("id[Brand]").getMapping(brandKey)));
            props.add(new ImportProperty(idBrandField, findProperty("brand[Item]").getMapping(itemKey),
                    object(findClass("Brand")).getMapping(brandKey), true));
            fields.add(idBrandField);

            ImportField nameBrandField = new ImportField(findProperty("name[Brand]"));
            props.add(new ImportProperty(nameBrandField, findProperty("name[Brand]").getMapping(brandKey), true));
            fields.add(nameBrandField);

            if(itemFashionLM != null) {
                ImportField idSeasonField = new ImportField(itemFashionLM.findProperty("id[Season]"));
                ImportKey<?> seasonKey = new ImportKey((CustomClass) itemFashionLM.findClass("Season"),
                        itemFashionLM.findProperty("season[VARSTRING[100]]").getMapping(idSeasonField));
                props.add(new ImportProperty(idSeasonField, itemFashionLM.findProperty("id[Season]").getMapping(seasonKey)));
                keys.add(seasonKey);
                props.add(new ImportProperty(idSeasonField, itemFashionLM.findProperty("season[Item]").getMapping(itemKey),
                        object(itemFashionLM.findClass("Season")).getMapping(seasonKey), true));
                fields.add(idSeasonField);

                ImportField nameSeasonField = new ImportField(itemFashionLM.findProperty("name[Season]"));
                props.add(new ImportProperty(nameSeasonField, itemFashionLM.findProperty("name[Season]").getMapping(seasonKey), true));
                fields.add(nameSeasonField);
            }
            
            ImportField priceMachineryPriceTransactionBarcodeField = new ImportField(findProperty("price[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(priceMachineryPriceTransactionBarcodeField, findProperty("price[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(priceMachineryPriceTransactionBarcodeField);

            ImportField splitMachineryPriceTransactionBarcodeField = new ImportField(findProperty("split[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(splitMachineryPriceTransactionBarcodeField, findProperty("split[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(splitMachineryPriceTransactionBarcodeField);

            ImportField expiryDaysMachineryPriceTransactionBarcodeField = new ImportField(findProperty("expiryDays[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(expiryDaysMachineryPriceTransactionBarcodeField, findProperty("expiryDays[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(expiryDaysMachineryPriceTransactionBarcodeField);
            
            ImportField expiryDateMachineryPriceTransactionBarcodeField = new ImportField(findProperty("expiryDate[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(expiryDateMachineryPriceTransactionBarcodeField, findProperty("expiryDate[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(expiryDateMachineryPriceTransactionBarcodeField);

            ImportField descriptionMachineryPriceTransactionBarcodeField = new ImportField(findProperty("description[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(descriptionMachineryPriceTransactionBarcodeField, findProperty("description[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(descriptionMachineryPriceTransactionBarcodeField);

            ImportField pluNumberMachineryPriceTransactionBarcodeField = new ImportField(findProperty("pluNumber[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(pluNumberMachineryPriceTransactionBarcodeField, findProperty("pluNumber[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(pluNumberMachineryPriceTransactionBarcodeField);

            ImportField flagsMachineryPriceTransactionBarcodeField = new ImportField(findProperty("flags[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(flagsMachineryPriceTransactionBarcodeField, findProperty("flags[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(flagsMachineryPriceTransactionBarcodeField);

            ImportField idUOMField = new ImportField(findProperty("id[UOM]"));
            ImportKey<?> UOMKey = new ImportKey((CustomClass) findClass("UOM"),
                    findProperty("UOM[VARSTRING[100]]").getMapping(idUOMField));
            UOMKey.skipKey = true;
            keys.add(UOMKey);
            props.add(new ImportProperty(idUOMField, findProperty("id[UOM]").getMapping(UOMKey)));
            props.add(new ImportProperty(idUOMField, findProperty("idUOM[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(idUOMField);

            ImportField shortNameUOMField = new ImportField(findProperty("shortName[UOM]"));
            props.add(new ImportProperty(shortNameUOMField, findProperty("shortName[UOM]").getMapping(UOMKey), true));
            props.add(new ImportProperty(shortNameUOMField, findProperty("shortNameUOM[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(shortNameUOMField);

            ImportField passScalesMachineryPriceTransactionBarcodeField = new ImportField(findProperty("passScales[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(passScalesMachineryPriceTransactionBarcodeField, findProperty("passScales[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(passScalesMachineryPriceTransactionBarcodeField);

            DataObject defaultCountryObject = (DataObject) findProperty("defaultCountry[]").readClasses(context);
            ImportField valueVATItemCountryDateField = new ImportField(findProperty("valueVAT[Item,Country,DATE]"));
            ImportKey<?> VATKey = new ImportKey((CustomClass) findClass("Range"),
                    findProperty("valueCurrentVATDefault[NUMERIC[10,5]]").getMapping(valueVATItemCountryDateField));
            VATKey.skipKey = true;
            keys.add(VATKey);
            props.add(new ImportProperty(valueVATItemCountryDateField, findProperty("VAT[Item,Country]").getMapping(itemKey, defaultCountryObject),
                    object(findClass("Range")).getMapping(VATKey)));
            if(machineryPriceTransactionStockTaxLM != null)
                props.add(new ImportProperty(valueVATItemCountryDateField, machineryPriceTransactionStockTaxLM.findProperty("VAT[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(valueVATItemCountryDateField);

            ImportField idItemGroupField = new ImportField(findProperty("id[ItemGroup]"));
            ImportKey<?> itemGroupKey = new ImportKey((CustomClass) findClass("ItemGroup"),
                    findProperty("itemGroup[VARSTRING[100]]").getMapping(idItemGroupField));
            keys.add(itemGroupKey);
            props.add(new ImportProperty(idItemGroupField, findProperty("skuGroup[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey),
                    object(findClass("ItemGroup")).getMapping(itemGroupKey)));
            props.add(new ImportProperty(idItemGroupField, findProperty("itemGroup[Item]").getMapping(itemKey),
                    object(findClass("ItemGroup")).getMapping(itemGroupKey)));
            fields.add(idItemGroupField);
                        
            ImportField inMachineryPriceTransactionBarcodeField = new ImportField(findProperty("in[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(inMachineryPriceTransactionBarcodeField, findProperty("in[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
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

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            
            ImportField idMachineryPriceTransactionField = new ImportField(findProperty("id[MachineryPriceTransaction]"));
            ImportKey<?> machineryPriceTransactionKey = new ImportKey((CustomClass) findClass("ScalesPriceTransaction"),
                    findProperty("machineryPriceTransaction[VARSTRING[100]]").getMapping(idMachineryPriceTransactionField));
            keys.add(machineryPriceTransactionKey);
            props.add(new ImportProperty(idMachineryPriceTransactionField, findProperty("id[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey)));
            fields.add(idMachineryPriceTransactionField);

            ImportField nppGroupMachineryField = new ImportField(findProperty("npp[GroupMachinery]"));
            ImportKey<?> groupScalesKey = new ImportKey((CustomClass) findClass("GroupScales"),
                    findProperty("groupScales[INTEGER]").getMapping(nppGroupMachineryField));
            groupScalesKey.skipKey = true;
            keys.add(groupScalesKey);
            props.add(new ImportProperty(nppGroupMachineryField, findProperty("groupMachinery[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey),
                    object(findClass("GroupScales")).getMapping(groupScalesKey)));
            fields.add(nppGroupMachineryField);

            ImportField dateMachineryPriceTransactionField = new ImportField(findProperty("date[MachineryPriceTransaction]"));
            props.add(new ImportProperty(dateMachineryPriceTransactionField, findProperty("date[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey)));
            fields.add(dateMachineryPriceTransactionField);

            ImportField timeMachineryPriceTransactionField = new ImportField(findProperty("time[MachineryPriceTransaction]"));
            props.add(new ImportProperty(timeMachineryPriceTransactionField, findProperty("time[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey)));
            fields.add(timeMachineryPriceTransactionField);

            ImportField snapshotMachineryPriceTransactionField = new ImportField(findProperty("snapshot[MachineryPriceTransaction]"));
            props.add(new ImportProperty(snapshotMachineryPriceTransactionField, findProperty("snapshot[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey)));
            fields.add(snapshotMachineryPriceTransactionField);

            ImportField commentMachineryPriceTransactionField = new ImportField(findProperty("comment[MachineryPriceTransaction]"));
            props.add(new ImportProperty(commentMachineryPriceTransactionField, findProperty("comment[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey)));
            fields.add(commentMachineryPriceTransactionField);

            ImportField idItemField = new ImportField(findProperty("id[Item]"));
            ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                    findProperty("item[VARSTRING[100]]").getMapping(idItemField));
            keys.add(itemKey);
            props.add(new ImportProperty(idItemField, findProperty("id[Item]").getMapping(itemKey)));
            fields.add(idItemField);

            ImportField extIdBarcodeField = new ImportField(findProperty("extId[Barcode]"));
            ImportKey<?> barcodeKey = new ImportKey((CustomClass) findClass("Barcode"),
                    findProperty("extBarcode[VARSTRING[100]]").getMapping(extIdBarcodeField));
            keys.add(barcodeKey);
            props.add(new ImportProperty(idItemField, findProperty("sku[Barcode]").getMapping(barcodeKey),
                    object(findClass("Item")).getMapping(itemKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("extId[Barcode]").getMapping(barcodeKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("id[Barcode]").getMapping(barcodeKey)));
            fields.add(extIdBarcodeField);
            
            ImportField captionItemField = new ImportField(findProperty("caption[Item]"));
            props.add(new ImportProperty(captionItemField, findProperty("caption[Item]").getMapping(itemKey), true));
            props.add(new ImportProperty(captionItemField, findProperty("name[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(captionItemField);

            ImportField priceMachineryPriceTransactionBarcodeField = new ImportField(findProperty("price[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(priceMachineryPriceTransactionBarcodeField, findProperty("price[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(priceMachineryPriceTransactionBarcodeField);
            
            ImportField splitMachineryPriceTransactionBarcodeField = new ImportField(findProperty("split[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(splitMachineryPriceTransactionBarcodeField, findProperty("split[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(splitMachineryPriceTransactionBarcodeField);

            ImportField expiryDaysMachineryPriceTransactionBarcodeField = new ImportField(findProperty("expiryDays[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(expiryDaysMachineryPriceTransactionBarcodeField, findProperty("expiryDays[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(expiryDaysMachineryPriceTransactionBarcodeField);

            ImportField hoursExpiryMachineryPriceTransactionBarcodeField = new ImportField(findProperty("hoursExpiry[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(hoursExpiryMachineryPriceTransactionBarcodeField, findProperty("hoursExpiry[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(hoursExpiryMachineryPriceTransactionBarcodeField);

            ImportField expiryDateMachineryPriceTransactionBarcodeField = new ImportField(findProperty("expiryDate[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(expiryDateMachineryPriceTransactionBarcodeField, findProperty("expiryDate[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(expiryDateMachineryPriceTransactionBarcodeField);
            
            ImportField descriptionMachineryPriceTransactionBarcodeField = new ImportField(findProperty("description[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(descriptionMachineryPriceTransactionBarcodeField, findProperty("description[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(descriptionMachineryPriceTransactionBarcodeField);

            ImportField pluNumberMachineryPriceTransactionBarcodeField = new ImportField(findProperty("pluNumber[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(pluNumberMachineryPriceTransactionBarcodeField, findProperty("pluNumber[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(pluNumberMachineryPriceTransactionBarcodeField);

            ImportField flagsMachineryPriceTransactionBarcodeField = new ImportField(findProperty("flags[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(flagsMachineryPriceTransactionBarcodeField, findProperty("flags[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(flagsMachineryPriceTransactionBarcodeField);
            
            ImportField passScalesMachineryPriceTransactionBarcodeField = new ImportField(findProperty("passScales[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(passScalesMachineryPriceTransactionBarcodeField, findProperty("passScales[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(passScalesMachineryPriceTransactionBarcodeField);

            DataObject defaultCountryObject = (DataObject) findProperty("defaultCountry[]").readClasses(context);
            ImportField valueVATItemCountryDateField = new ImportField(findProperty("valueVAT[Item,Country,DATE]"));
            ImportKey<?> VATKey = new ImportKey((CustomClass) findClass("Range"),
                    findProperty("valueCurrentVATDefault[NUMERIC[10,5]]").getMapping(valueVATItemCountryDateField));
            VATKey.skipKey = true;
            keys.add(VATKey);
            props.add(new ImportProperty(valueVATItemCountryDateField, findProperty("VAT[Item,Country]").getMapping(itemKey, defaultCountryObject),
                    object(findClass("Range")).getMapping(VATKey)));
            if(machineryPriceTransactionStockTaxLM != null)
                props.add(new ImportProperty(valueVATItemCountryDateField, machineryPriceTransactionStockTaxLM.findProperty("VAT[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(valueVATItemCountryDateField);
            
            ImportField idUOMField = new ImportField(findProperty("id[UOM]"));
            ImportKey<?> UOMKey = new ImportKey((CustomClass) findClass("UOM"),
                    findProperty("UOM[VARSTRING[100]]").getMapping(idUOMField));
            UOMKey.skipKey = true;
            keys.add(UOMKey);
            props.add(new ImportProperty(idUOMField, findProperty("id[UOM]").getMapping(UOMKey)));
            props.add(new ImportProperty(idUOMField, findProperty("idUOM[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(idUOMField);

            ImportField shortNameUOMField = new ImportField(findProperty("shortName[UOM]"));
            props.add(new ImportProperty(shortNameUOMField, findProperty("shortName[UOM]").getMapping(UOMKey), true));
            props.add(new ImportProperty(shortNameUOMField, findProperty("shortNameUOM[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(shortNameUOMField);
            
            ImportField labelFormatMachineryPriceTransactionBarcodeField = new ImportField(findProperty("labelFormat[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(labelFormatMachineryPriceTransactionBarcodeField, findProperty("labelFormat[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(labelFormatMachineryPriceTransactionBarcodeField);

            ImportField idItemGroupField = new ImportField(findProperty("id[ItemGroup]"));
            ImportKey<?> itemGroupKey = new ImportKey((CustomClass) findClass("ItemGroup"),
                    findProperty("itemGroup[VARSTRING[100]]").getMapping(idItemGroupField));
            keys.add(itemGroupKey);
            props.add(new ImportProperty(idItemGroupField, findProperty("skuGroup[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey),
                    object(findClass("ItemGroup")).getMapping(itemGroupKey)));
            props.add(new ImportProperty(idItemGroupField, findProperty("itemGroup[Item]").getMapping(itemKey),
                    object(findClass("ItemGroup")).getMapping(itemGroupKey)));
            fields.add(idItemGroupField);
            
            ImportField inMachineryPriceTransactionBarcodeField = new ImportField(findProperty("in[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(inMachineryPriceTransactionBarcodeField, findProperty("in[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
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

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            ImportField idMachineryPriceTransactionField = new ImportField(findProperty("id[MachineryPriceTransaction]"));
            ImportKey<?> machineryPriceTransactionKey = new ImportKey((CustomClass) findClass("TerminalPriceTransaction"),
                    findProperty("machineryPriceTransaction[VARSTRING[100]]").getMapping(idMachineryPriceTransactionField));
            keys.add(machineryPriceTransactionKey);
            props.add(new ImportProperty(idMachineryPriceTransactionField, findProperty("id[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey)));
            fields.add(idMachineryPriceTransactionField);

            ImportField nppGroupMachineryField = new ImportField(findProperty("npp[GroupMachinery]"));
            ImportKey<?> groupTerminalKey = new ImportKey((CustomClass) findClass("GroupTerminal"),
                    findProperty("groupTerminal[INTEGER]").getMapping(nppGroupMachineryField));
            groupTerminalKey.skipKey = true;
            keys.add(groupTerminalKey);
            props.add(new ImportProperty(nppGroupMachineryField, findProperty("groupMachinery[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey),
                    object(findClass("GroupTerminal")).getMapping(groupTerminalKey)));
            fields.add(nppGroupMachineryField);

            ImportField dateMachineryPriceTransactionField = new ImportField(findProperty("date[MachineryPriceTransaction]"));
            props.add(new ImportProperty(dateMachineryPriceTransactionField, findProperty("date[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey)));
            fields.add(dateMachineryPriceTransactionField);

            ImportField timeMachineryPriceTransactionField = new ImportField(findProperty("time[MachineryPriceTransaction]"));
            props.add(new ImportProperty(timeMachineryPriceTransactionField, findProperty("time[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey)));
            fields.add(timeMachineryPriceTransactionField);

            ImportField snapshotMachineryPriceTransactionField = new ImportField(findProperty("snapshot[MachineryPriceTransaction]"));
            props.add(new ImportProperty(snapshotMachineryPriceTransactionField, findProperty("snapshot[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey)));
            fields.add(snapshotMachineryPriceTransactionField);

            ImportField commentMachineryPriceTransactionField = new ImportField(findProperty("comment[MachineryPriceTransaction]"));
            props.add(new ImportProperty(commentMachineryPriceTransactionField, findProperty("comment[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey)));
            fields.add(commentMachineryPriceTransactionField);

            ImportField idItemField = new ImportField(findProperty("id[Item]"));
            ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                    findProperty("item[VARSTRING[100]]").getMapping(idItemField));
            keys.add(itemKey);
            props.add(new ImportProperty(idItemField, findProperty("id[Item]").getMapping(itemKey)));
            fields.add(idItemField);
            
            ImportField extIdBarcodeField = new ImportField(findProperty("extId[Barcode]"));
            ImportKey<?> barcodeKey = new ImportKey((CustomClass) findClass("Barcode"),
                    findProperty("extBarcode[VARSTRING[100]]").getMapping(extIdBarcodeField));
            keys.add(barcodeKey);
            props.add(new ImportProperty(idItemField, findProperty("sku[Barcode]").getMapping(barcodeKey),
                    object(findClass("Item")).getMapping(itemKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("extId[Barcode]").getMapping(barcodeKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("id[Barcode]").getMapping(barcodeKey)));
            fields.add(extIdBarcodeField);

            ImportField captionItemField = new ImportField(findProperty("caption[Item]"));
            props.add(new ImportProperty(captionItemField, findProperty("caption[Item]").getMapping(itemKey), true));
            props.add(new ImportProperty(captionItemField, findProperty("name[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(captionItemField);

            ImportField splitMachineryPriceTransactionBarcodeField = new ImportField(findProperty("split[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(splitMachineryPriceTransactionBarcodeField, findProperty("split[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(splitMachineryPriceTransactionBarcodeField);

            ImportField expiryDaysMachineryPriceTransactionBarcodeField = new ImportField(findProperty("expiryDays[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(expiryDaysMachineryPriceTransactionBarcodeField, findProperty("expiryDays[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(expiryDaysMachineryPriceTransactionBarcodeField);

            ImportField pluNumberMachineryPriceTransactionBarcodeField = new ImportField(findProperty("pluNumber[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(pluNumberMachineryPriceTransactionBarcodeField, findProperty("pluNumber[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(pluNumberMachineryPriceTransactionBarcodeField);

            ImportField flagsMachineryPriceTransactionBarcodeField = new ImportField(findProperty("flags[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(flagsMachineryPriceTransactionBarcodeField, findProperty("flags[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(flagsMachineryPriceTransactionBarcodeField);
            
            ImportField priceMachineryPriceTransactionBarcodeField = new ImportField(findProperty("price[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(priceMachineryPriceTransactionBarcodeField, findProperty("price[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(priceMachineryPriceTransactionBarcodeField);
            
            ImportField inMachineryPriceTransactionBarcodeField = new ImportField(findProperty("in[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(inMachineryPriceTransactionBarcodeField, findProperty("in[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
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

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            ImportField idMachineryPriceTransactionField = new ImportField(findProperty("id[MachineryPriceTransaction]"));
            ImportKey<?> machineryPriceTransactionKey = new ImportKey((CustomClass) findClass("ScalesPriceTransaction"),
                    findProperty("machineryPriceTransaction[VARSTRING[100]]").getMapping(idMachineryPriceTransactionField));
            keys.add(machineryPriceTransactionKey);
            props.add(new ImportProperty(idMachineryPriceTransactionField, findProperty("id[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey)));
            fields.add(idMachineryPriceTransactionField);

            ImportField nppGroupMachineryField = new ImportField(findProperty("npp[GroupMachinery]"));
            ImportKey<?> groupPriceCheckerKey = new ImportKey((CustomClass) findClass("GroupPriceChecker"),
                    findProperty("groupPriceChecker[INTEGER]").getMapping(nppGroupMachineryField));
            groupPriceCheckerKey.skipKey = true;
            keys.add(groupPriceCheckerKey);
            props.add(new ImportProperty(nppGroupMachineryField, findProperty("groupMachinery[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey),
                    object(findClass("GroupPriceChecker")).getMapping(groupPriceCheckerKey)));
            fields.add(nppGroupMachineryField);

            ImportField dateMachineryPriceTransactionField = new ImportField(findProperty("date[MachineryPriceTransaction]"));
            props.add(new ImportProperty(dateMachineryPriceTransactionField, findProperty("date[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey)));
            fields.add(dateMachineryPriceTransactionField);

            ImportField timeMachineryPriceTransactionField = new ImportField(findProperty("time[MachineryPriceTransaction]"));
            props.add(new ImportProperty(timeMachineryPriceTransactionField, findProperty("time[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey)));
            fields.add(timeMachineryPriceTransactionField);

            ImportField snapshotMachineryPriceTransactionField = new ImportField(findProperty("snapshot[MachineryPriceTransaction]"));
            props.add(new ImportProperty(snapshotMachineryPriceTransactionField, findProperty("snapshot[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey)));
            fields.add(snapshotMachineryPriceTransactionField);

            ImportField commentMachineryPriceTransactionField = new ImportField(findProperty("comment[MachineryPriceTransaction]"));
            props.add(new ImportProperty(commentMachineryPriceTransactionField, findProperty("comment[MachineryPriceTransaction]").getMapping(machineryPriceTransactionKey)));
            fields.add(commentMachineryPriceTransactionField);

            ImportField idItemField = new ImportField(findProperty("id[Item]"));
            ImportKey<?> itemKey = new ImportKey((CustomClass) findClass("Item"),
                    findProperty("item[VARSTRING[100]]").getMapping(idItemField));
            keys.add(itemKey);
            props.add(new ImportProperty(idItemField, findProperty("id[Item]").getMapping(itemKey)));
            fields.add(idItemField);
            
            ImportField extIdBarcodeField = new ImportField(findProperty("extId[Barcode]"));
            ImportKey<?> barcodeKey = new ImportKey((CustomClass) findClass("Barcode"),
                    findProperty("extBarcode[VARSTRING[100]]").getMapping(extIdBarcodeField));
            keys.add(barcodeKey);
            props.add(new ImportProperty(idItemField, findProperty("sku[Barcode]").getMapping(barcodeKey),
                    object(findClass("Item")).getMapping(itemKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("extId[Barcode]").getMapping(barcodeKey)));
            props.add(new ImportProperty(extIdBarcodeField, findProperty("id[Barcode]").getMapping(barcodeKey)));
            fields.add(extIdBarcodeField);

            ImportField captionItemField = new ImportField(findProperty("caption[Item]"));
            props.add(new ImportProperty(captionItemField, findProperty("caption[Item]").getMapping(itemKey), true));
            props.add(new ImportProperty(captionItemField, findProperty("name[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(captionItemField);

            ImportField splitMachineryPriceTransactionBarcodeField = new ImportField(findProperty("split[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(splitMachineryPriceTransactionBarcodeField, findProperty("split[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(splitMachineryPriceTransactionBarcodeField);

            ImportField expiryDaysMachineryPriceTransactionBarcodeField = new ImportField(findProperty("expiryDays[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(expiryDaysMachineryPriceTransactionBarcodeField, findProperty("expiryDays[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(expiryDaysMachineryPriceTransactionBarcodeField);

            ImportField pluNumberMachineryPriceTransactionBarcodeField = new ImportField(findProperty("pluNumber[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(pluNumberMachineryPriceTransactionBarcodeField, findProperty("pluNumber[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(pluNumberMachineryPriceTransactionBarcodeField);

            ImportField flagsMachineryPriceTransactionBarcodeField = new ImportField(findProperty("flags[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(flagsMachineryPriceTransactionBarcodeField, findProperty("flags[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(flagsMachineryPriceTransactionBarcodeField);
            
            ImportField priceMachineryPriceTransactionBarcodeField = new ImportField(findProperty("price[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(priceMachineryPriceTransactionBarcodeField, findProperty("price[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
            fields.add(priceMachineryPriceTransactionBarcodeField);
            
            ImportField inMachineryPriceTransactionBarcodeField = new ImportField(findProperty("in[MachineryPriceTransaction,Barcode]"));
            props.add(new ImportProperty(inMachineryPriceTransactionBarcodeField, findProperty("in[MachineryPriceTransaction,Barcode]").getMapping(machineryPriceTransactionKey, barcodeKey)));
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
        List<List<Object>> data = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            data.add(new ArrayList<>());
        }
        return data;
    }


}

