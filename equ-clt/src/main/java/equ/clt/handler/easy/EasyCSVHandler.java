package equ.clt.handler.easy;

import equ.api.*;
import equ.api.cashregister.*;
import equ.api.scales.ScalesHandler;
import equ.api.scales.ScalesInfo;
import equ.api.scales.TransactionScalesInfo;

import java.io.*;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.*;

public class EasyCSVHandler {

    public EasyCSVHandler() {

    }

    public class EasyCashRegisterCSVHandler extends CashRegisterHandler<SalesBatch> {

        public EasyCashRegisterCSVHandler() {
        }

        public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
            return "easycr";
        }
        
        @Override
        public Map<Integer, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionInfoList) throws IOException {

            Map<Integer, SendTransactionBatch> sendTransactionBatchMap = new HashMap<Integer, SendTransactionBatch>();

            for(TransactionCashRegisterInfo transactionInfo : transactionInfoList) {

                Exception exception = null;
                try {

                    List<String> directoriesList = new ArrayList<String>();
                    for (CashRegisterInfo cashRegisterInfo : transactionInfo.machineryInfoList) {
                        if ((cashRegisterInfo.port != null) && (!directoriesList.contains(cashRegisterInfo.port.trim())))
                            directoriesList.add(cashRegisterInfo.port.trim());
                        if ((cashRegisterInfo.directory != null) && (!directoriesList.contains(cashRegisterInfo.directory.trim())))
                            directoriesList.add(cashRegisterInfo.directory.trim());
                    }

                    for (String directory : directoriesList) {
                        File folder = new File(directory.trim());
                        folder.mkdir();
                        File f = new File(directory.trim() + "/" + transactionInfo.dateTimeCode + ".csv");
                        PrintWriter writer = new PrintWriter(
                                new OutputStreamWriter(
                                        new FileOutputStream(f), "windows-1251"));
                        for (ItemInfo item : transactionInfo.itemsList) {
                            String row = item.idBarcode + ";" + item.name + ";" + item.price;
                            writer.println(row);
                        }
                        writer.close();
                    }
                } catch (Exception e) {
                    exception = e;
                }
                sendTransactionBatchMap.put(transactionInfo.id, new SendTransactionBatch(exception));
            }
            return sendTransactionBatchMap;
        }

        @Override
        public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {           
        }

        @Override
        public void sendStopListInfo(StopListInfo stopListInfo, Set<String> directorySet) throws IOException {
        }

        @Override
        public void sendDiscountCardList(List<DiscountCard> discountCardList, RequestExchange requestExchange) throws IOException {
        }

        @Override
        public void sendPromotionInfo(PromotionInfo promotionInfo, RequestExchange requestExchange) throws IOException {
        }

        @Override
        public void sendCashierInfoList(List<CashierInfo> cashierInfoList, Map<String, Set<String>> directoryStockMap) throws IOException {
        }

        @Override
        public List<CashierTime> requestCashierTime(List<MachineryInfo> cashRegisterInfoList) throws IOException, ClassNotFoundException, SQLException {
            return null;
        }

        @Override
        public SalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) {
            return null;
        }

        @Override
        public void requestSalesInfo(List<RequestExchange> requestExchangeList, Set<String> directorySet,
                                     Set<Integer> succeededRequests, Map<Integer, String> failedRequests, Map<Integer, String> ignoredRequests) throws IOException, ParseException {
        }

        @Override
        public CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList, Set<String> cashDocumentSet) throws ClassNotFoundException {
            return null;
        }

        @Override
        public void finishReadingCashDocumentInfo(CashDocumentBatch cashDocumentBatch) {            
        }

        @Override
        public void finishReadingSalesInfo(SalesBatch salesBatch) {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public Map<String, Timestamp> requestSucceededSoftCheckInfo(Set<String> directorySet) {
            return null;
        }

        @Override
        public List<List<Object>> checkZReportSum(Map<String, List<Object>> zReportSumMap, List<List<Object>> cashRegisterList) throws ClassNotFoundException, SQLException {
            return null;
        }

        @Override
        public Map<String, List<Object>> readExtraCheckZReport(List<CashRegisterInfo> cashRegisterInfoList) throws ClassNotFoundException, SQLException {
            return null;
        }

        @Override
        public ExtraCheckZReportBatch compareExtraCheckZReport(Map<String, List<Object>> handlerZReportSumMap, Map<String, BigDecimal> baseZReportSumMap) throws ClassNotFoundException, SQLException {
            return null;
        }
    }

    public class EasyPriceCheckerCSVHandler extends PriceCheckerHandler {

        public EasyPriceCheckerCSVHandler() {
        }

        public String getGroupId(TransactionPriceCheckerInfo transactionInfo) {
            return "easypc";
        }

        @Override
        public Map<Integer, SendTransactionBatch> sendTransaction(List<TransactionPriceCheckerInfo> transactionInfoList) throws IOException {

            Map<Integer, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

            for(TransactionPriceCheckerInfo transactionInfo : transactionInfoList) {

                Exception exception = null;
                try {

                    List<String> directoriesList = new ArrayList<>();
                    for (PriceCheckerInfo priceCheckerInfo : transactionInfo.machineryInfoList) {
                        if ((priceCheckerInfo.port != null) && (!directoriesList.contains(priceCheckerInfo.port.trim())))
                            directoriesList.add(priceCheckerInfo.port.trim());
                    }

                    for (String directory : directoriesList) {
                        File folder = new File(directory.trim());
                        folder.mkdir();
                        File f = new File(directory.trim() + "/" + transactionInfo.dateTimeCode + ".csv");
                        PrintWriter writer = new PrintWriter(
                                new OutputStreamWriter(
                                        new FileOutputStream(f), "windows-1251"));
                        for (ItemInfo item : transactionInfo.itemsList) {
                            String row = item.idBarcode + ";" + item.name + ";" + item.price;
                            writer.println(row);
                        }
                        writer.close();
                    }
                } catch (Exception e) {
                    exception = e;
                }
                sendTransactionBatchMap.put(transactionInfo.id, new SendTransactionBatch(exception));
            }
            return sendTransactionBatchMap;
        }

        @Override
        public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {
            
        }
    }

    public class EasyScalesCSVHandler extends ScalesHandler {

        public EasyScalesCSVHandler() {
        }

        public String getGroupId(TransactionScalesInfo transactionInfo) {
            return "easysc";
        }

        @Override
        public Map<Integer, SendTransactionBatch> sendTransaction(List<TransactionScalesInfo> transactionList) throws IOException {

            Map<Integer, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

            for(TransactionScalesInfo transaction : transactionList) {

                Exception exception = null;
                try {

                    List<String> directoriesList = new ArrayList<>();
                    for (ScalesInfo scalesInfo : transaction.machineryInfoList) {
                        if ((scalesInfo.port != null) && (!directoriesList.contains(scalesInfo.port.trim())))
                            directoriesList.add(scalesInfo.port.trim());
                        if ((scalesInfo.directory != null) && (!directoriesList.contains(scalesInfo.directory.trim())))
                            directoriesList.add(scalesInfo.directory.trim());
                    }

                    for (String directory : directoriesList) {
                        File folder = new File(directory.trim());
                        folder.mkdir();
                        File f = new File(directory.trim() + "/" + transaction.dateTimeCode + ".csv");
                        PrintWriter writer = new PrintWriter(
                                new OutputStreamWriter(
                                        new FileOutputStream(f), "windows-1251"));
                        for (ItemInfo item : transaction.itemsList) {
                            String row = item.idBarcode + ";" + item.name + ";" + item.price;
                            writer.println(row);
                        }
                        writer.close();
                    }
                } catch (Exception e) {
                    exception = e;
                }
                sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(exception));
            }
            return sendTransactionBatchMap;
        }

        @Override
        public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {
        }

        @Override
        public void sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machineryInfoSet) throws IOException {
        }
    }
}
