package equ.clt.handler.easy;

import equ.api.*;
import equ.api.cashregister.CashRegisterInfo;
import equ.api.cashregister.TransactionCashRegisterInfo;
import equ.api.scales.ScalesInfo;
import equ.api.scales.TransactionScalesInfo;
import equ.clt.handler.DefaultCashRegisterHandler;
import equ.clt.handler.DefaultScalesHandler;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EasyCSVHandler {

    public EasyCSVHandler() {

    }

    public class EasyCashRegisterCSVHandler extends DefaultCashRegisterHandler<SalesBatch> {

        public EasyCashRegisterCSVHandler() {
        }

        public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
            return "easycr";
        }
        
        @Override
        public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionInfoList) {

            Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

            for(TransactionCashRegisterInfo transactionInfo : transactionInfoList) {

                Exception exception = null;
                try {

                    List<String> directoriesList = new ArrayList<>();
                    for (CashRegisterInfo cashRegisterInfo : transactionInfo.machineryInfoList) {
                        if ((cashRegisterInfo.directory != null) && (!directoriesList.contains(cashRegisterInfo.directory)))
                            directoriesList.add(cashRegisterInfo.directory);
                    }

                    for (String directory : directoriesList) {
                        File folder = new File(directory);
                        folder.mkdir();
                        File f = new File(directory + "/" + transactionInfo.dateTimeCode + ".csv");
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
        public void finishReadingSalesInfo(SalesBatch salesBatch) {
        }
    }

    public class EasyPriceCheckerCSVHandler extends PriceCheckerHandler {

        public EasyPriceCheckerCSVHandler() {
        }

        public String getGroupId(TransactionPriceCheckerInfo transactionInfo) {
            return "easypc";
        }

        @Override
        public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionPriceCheckerInfo> transactionInfoList) {

            Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

            for(TransactionPriceCheckerInfo transactionInfo : transactionInfoList) {

                Exception exception = null;
                try {

                    List<String> directoriesList = new ArrayList<>();
                    for (PriceCheckerInfo priceCheckerInfo : transactionInfo.machineryInfoList) {
                        if ((priceCheckerInfo.port != null) && (!directoriesList.contains(priceCheckerInfo.port)))
                            directoriesList.add(priceCheckerInfo.port);
                    }

                    for (String directory : directoriesList) {
                        File folder = new File(directory);
                        folder.mkdir();
                        File f = new File(directory + "/" + transactionInfo.dateTimeCode + ".csv");
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
    }

    public class EasyScalesCSVHandler extends DefaultScalesHandler {

        public EasyScalesCSVHandler() {
        }

        @Override
        protected String getLogPrefix() {
            return "Easysc: ";
        }

        public String getGroupId(TransactionScalesInfo transactionInfo) {
            return "easysc";
        }

        @Override
        public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionScalesInfo> transactionList) {

            Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

            for(TransactionScalesInfo transaction : transactionList) {

                Exception exception = null;
                try {

                    List<String> directoriesList = new ArrayList<>();
                    for (ScalesInfo scalesInfo : transaction.machineryInfoList) {
                        if ((scalesInfo.port != null) && (!directoriesList.contains(scalesInfo.port)))
                            directoriesList.add(scalesInfo.port);
                        if ((scalesInfo.directory != null) && (!directoriesList.contains(scalesInfo.directory)))
                            directoriesList.add(scalesInfo.directory);
                    }

                    for (String directory : directoriesList) {
                        File folder = new File(directory);
                        folder.mkdir();
                        File f = new File(directory + "/" + transaction.dateTimeCode + ".csv");
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
    }
}
