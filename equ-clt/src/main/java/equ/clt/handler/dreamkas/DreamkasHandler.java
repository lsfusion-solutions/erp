package equ.clt.handler.dreamkas;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
import equ.clt.handler.DefaultCashRegisterHandler;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.util.*;

public class DreamkasHandler extends DefaultCashRegisterHandler<DreamkasSalesBatch> {

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected final static Logger sendSalesLogger = Logger.getLogger("SendSalesLogger");

    private static String logPrefix = "Dreamkas: ";

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
            server.salesDays = dreamkasSettings.getSalesDays();
            server.salesLimitReceipt = dreamkasSettings.getSalesLimitReceipt();
            server.stepSend = dreamkasSettings.getStepSend();
            if (server.baseURL.isEmpty()) eMsg = "В настройках не определен BaseURL";
            if (server.token.isEmpty()) eMsg = "В настройках не определен token";
            if (server.salesLimitReceipt < 200) server.salesLimitReceipt = 200;
//            if ((server.stepSend < 1) || (server.stepSend > 100)) server.stepSend = 100;
        }
        if (eMsg.length() > 0) throw new RuntimeException(eMsg);
    }

    @Override
// Вызывается при импорте данных в кассовый сервер
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) throws IOException {
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

    int readSalesInfoCount = 0;
    // Вызывается при чтении реализации
    @Override
    public SalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) {
        List<SalesInfo> salesInfoList = new ArrayList<>();

        DreamkasSettings dreamkasSettings = springContext.containsBean("dreamkasSettings") ? (DreamkasSettings) springContext.getBean("dreamkasSettings") : null;
        Integer runReadSalesInterval = dreamkasSettings != null ? dreamkasSettings.getRunReadSalesInterval() : null;
        if(runReadSalesInterval == null || readSalesInfoCount >= runReadSalesInterval) {
            readSalesInfoCount = 0;
            DreamkasServer server = new DreamkasServer();
            setProp(server);
            server.cashRegisterInfoList = cashRegisterInfoList;
            if (!server.getSales()) {
                if (server.logMessage.length() > 0) {
                    sendSalesLogger.error(logPrefix + server.logMessage);
                }
                throw Throwables.propagate(new RuntimeException(server.eMessage));
            } else {
                sendSalesLogger.info(logPrefix + "found " + server.salesInfoList.size() + " sale records");
                salesInfoList = server.salesInfoList;
            }
        } else {
            readSalesInfoCount++;
        }
        return new DreamkasSalesBatch(salesInfoList);
    }

    // Обратная связь: после записи реализации, отправка на сервер, что данные были приняты
    @Override
    public void finishReadingSalesInfo(DreamkasSalesBatch salesBatch) {
    }

    // Чтение кассовых документов (внесения/изъятия) throws ClassNotFoundException - убрал
    @Override
    public CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList, Set<String> cashDocumentSet) {
        DreamkasServer server = new DreamkasServer();
        setProp(server);

        server.cashRegisterInfoList = cashRegisterInfoList;
        if (!server.getDocInfo()) {
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
}


