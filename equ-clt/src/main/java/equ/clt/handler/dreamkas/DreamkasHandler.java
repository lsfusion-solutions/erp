package equ.clt.handler.dreamkas;

import com.google.common.base.Throwables;
import equ.api.RequestExchange;
import equ.api.SendTransactionBatch;
import equ.api.cashregister.CashDocumentBatch;
import equ.api.cashregister.CashRegisterInfo;
import equ.api.cashregister.TransactionCashRegisterInfo;
import equ.clt.handler.DefaultCashRegisterHandler;
import lsfusion.base.Pair;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;

public class DreamkasHandler extends DefaultCashRegisterHandler<DreamkasSalesBatch> {

    private static String logPrefix = "Dreamkas: ";

    private static List<String> pendingQueryList = new ArrayList<>();

    private FileSystemXmlApplicationContext springContext;

    public DreamkasHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        String groupId = null;
        for (CashRegisterInfo cashRegister : transactionInfo.machineryInfoList) {
            if (cashRegister.directory != null) {
                groupId = cashRegister.directory;
            }
        }
        return "dreamkas" + groupId;
    }

    private void setProp(DreamkasServer server) {
        String eMsg = "";
        DreamkasSettings dreamkasSettings = springContext.containsBean("dreamkasSettings") ?
                (DreamkasSettings) springContext.getBean("dreamkasSettings") : null;
        if (dreamkasSettings == null) {
            eMsg = "Отсутствует блок базовых настроек в Settings.xml";
        } else {
            server.baseURL = dreamkasSettings.getBaseURL();
            server.token = dreamkasSettings.getToken();
            server.uuidSuffix = dreamkasSettings.getUuidSuffix();
            server.salesLimitReceipt = dreamkasSettings.getSalesLimitReceipt();
            server.stepSend = dreamkasSettings.getStepSend();
            if (server.baseURL.isEmpty()) eMsg = "В настройках не определен BaseURL";
            if (server.token.isEmpty()) eMsg = "В настройках не определен token";
            if (server.uuidSuffix == null) eMsg = "В настройках не определен uuidSuffix";
            if (server.salesLimitReceipt < 200) server.salesLimitReceipt = 200;
//            if ((server.stepSend < 1) || (server.stepSend > 100)) server.stepSend = 100;
        }
        if (eMsg.length() > 0) throw new RuntimeException(eMsg);
    }

    @Override
// Вызывается при импорте данных в кассовый сервер
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) {
        Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        if (transactionList == null) return null;

        DreamkasServer server = new DreamkasServer();
        setProp(server);

        for (TransactionCashRegisterInfo transaction : transactionList) {
            if (!server.sendPriceList(transaction)) {
                if (server.logMessage.length() > 0) processTransactionLogger.error(logPrefix + server.logMessage);
                try {
                    throw new RuntimeException(server.eMessage);
                } catch (Exception e) {
                    sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(e));
                }
            }
        }
        return sendTransactionBatchMap;
    }

    int readSalesInfoCount = -1;
    // Вызывается при чтении реализации
    @Override
    public DreamkasSalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) throws UnsupportedEncodingException {
        DreamkasSettings dreamkasSettings = springContext.containsBean("dreamkasSettings") ? (DreamkasSettings) springContext.getBean("dreamkasSettings") : null;
        Integer runReadSalesInterval = dreamkasSettings != null ? dreamkasSettings.getRunReadSalesInterval() : null;
        Integer salesHours = dreamkasSettings != null ? dreamkasSettings.getSalesHours() : null;

        DreamkasServer server = new DreamkasServer(cashRegisterInfoList);
        setProp(server);

        if((runReadSalesInterval == null || readSalesInfoCount >= runReadSalesInterval || readSalesInfoCount == -1)) {
            readSalesInfoCount = 0;
            Pair<Date, Date> rangeDates = getRangeDates(salesHours);
            if (!server.getSales(getReceiptsQuery(rangeDates.first, rangeDates.second, null))) {
                if (server.logMessage.length() > 0) {
                    sendSalesLogger.error(logPrefix + server.logMessage);
                }
                throw Throwables.propagate(new RuntimeException(server.eMessage));
            }
        } else {
            readSalesInfoCount++;

            if (!pendingQueryList.isEmpty()) {
                String pendingQuery = pendingQueryList.remove(0);
                machineryExchangeLogger.info(logPrefix + "processing pending query: " + pendingQuery);
                for(String p: pendingQueryList) {
                    machineryExchangeLogger.info(logPrefix + "waiting pending query: " + p);
                }
                if (!server.getSales(pendingQuery)) {
                    if (server.logMessage.length() > 0) {
                        sendSalesLogger.error(logPrefix + server.logMessage);
                    }
                    throw Throwables.propagate(new RuntimeException(server.eMessage));
                }
            }
        }

        if(!server.salesInfoList.isEmpty()) {
            sendSalesLogger.info(logPrefix + "found " + server.salesInfoList.size() + " sale records");
        }
        return new DreamkasSalesBatch(server.salesInfoList);
    }

    // Обратная связь: после записи реализации, отправка на сервер, что данные были приняты
    @Override
    public void finishReadingSalesInfo(DreamkasSalesBatch salesBatch) {
    }

    // Чтение кассовых документов (внесения/изъятия) throws ClassNotFoundException - убрал
    @Override
    public CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList, Set<String> cashDocumentSet) {
        DreamkasSettings dreamkasSettings = springContext.containsBean("dreamkasSettings") ? (DreamkasSettings) springContext.getBean("dreamkasSettings") : null;
        Integer salesHours = dreamkasSettings != null ? dreamkasSettings.getSalesHours() : null;

        DreamkasServer server = new DreamkasServer();
        setProp(server);

        server.cashRegisterInfoList = cashRegisterInfoList;
        if (!server.getDocInfo(salesHours)) {
            if (server.logMessage.length() > 0) sendSalesLogger.error(logPrefix + server.logMessage);
            try {
                throw new RuntimeException(server.eMessage);
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
        return new CashDocumentBatch(server.cashDocList, null);
    }

    // Обратная связь: после чтения кассовых документов, отправка на сервер, что данные были приняты
    @Override
    public void finishReadingCashDocumentInfo(CashDocumentBatch cashDocumentBatch) {

    }

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList, Set<Long> succeededRequests, Map<Long, Throwable> failedRequests, Map<Long, Throwable> ignoredRequests) throws UnsupportedEncodingException {
        for (RequestExchange entry : requestExchangeList) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(entry.dateTo);
            cal.set(Calendar.HOUR, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            machineryExchangeLogger.info(logPrefix + String.format("creating request for dates: from %s to %s", entry.dateFrom, cal.getTime()));
            pendingQueryList.add(getReceiptsQuery(entry.dateFrom, entry.dateTo, getCashRegisterSet(entry, true)));
            succeededRequests.add(entry.requestExchange);
        }
    }

    public String getReceiptsQuery(Date dateFrom, Date dateTo, Set<CashRegisterInfo> cashRegisterSet) throws UnsupportedEncodingException {
        Set<String> deviceSet = new HashSet<>();
        if(cashRegisterSet != null) {
            for (CashRegisterInfo cashRegister : cashRegisterSet) {
                deviceSet.add("\"" + cashRegister.number + "\"");
            }
        }
        String devicesSubQuery = deviceSet.isEmpty() ? "" : ("&" + URLEncoder.encode("devices=[" + StringUtils.join(deviceSet, ",") + "]", "UTF-8"));
        return "receipts?" + getRangeDatesSubQuery(dateFrom, dateTo) + devicesSubQuery;
    }

    public static String getRangeDatesSubQuery(Integer salesHours) {
        Pair<Date, Date> rangeDates = getRangeDates(salesHours);
        return getRangeDatesSubQuery(rangeDates.first, rangeDates.second);
    }

    public static String getRangeDatesSubQuery(Date dateFrom, Date dateTo) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        return "from=" + dateFormat.format(dateFrom) + "&to=" + dateFormat.format(dateTo);
    }

    public static Pair<Date, Date> getRangeDates(Integer salesHours) {
        Calendar cal = Calendar.getInstance();
        if (salesHours != null && salesHours != 0) {
            cal.add(Calendar.HOUR, -salesHours);
        } else {
            cal.set(Calendar.HOUR, 0);
        }
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        Date dateFrom = cal.getTime();
        Date dateTo = Calendar.getInstance().getTime();
        return Pair.create(dateFrom, dateTo);
    }
}


