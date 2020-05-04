package equ.clt.handler.maxishop;

import equ.api.ItemInfo;
import equ.api.SalesInfo;
import equ.api.SendTransactionBatch;
import equ.api.cashregister.CashRegisterInfo;
import equ.api.cashregister.TransactionCashRegisterInfo;
import equ.clt.handler.DefaultCashRegisterHandler;
import org.xBaseJ.DBF;
import org.xBaseJ.Util;
import org.xBaseJ.fields.*;
import org.xBaseJ.xBaseJException;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static equ.clt.EquipmentServer.sqlTimeToLocalTime;
import static equ.clt.handler.HandlerUtils.safeMultiply;
import static equ.clt.handler.HandlerUtils.safeSubtract;

public class MaxishopHandler extends DefaultCashRegisterHandler<MaxishopSalesBatch> {

    public MaxishopHandler() {
    }

    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        return "maxishop";
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) {

        Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        for(TransactionCashRegisterInfo transaction : transactionList) {

            Exception exception = null;
            try {
                DBF file = null;

                try {
                    NumField POSNO = new NumField("POSNO", 5, 0);
                    CharField CMD = new CharField("CMD", 1);
                    NumField ERRNO = new NumField("ERRNO", 5, 0);
                    CharField PLUCODE = new CharField("PLUCODE", 20);
                    CharField ECRID = new CharField("ECRID", 10);
                    CharField NAME = new CharField("NAME", 100);
                    NumField PRICE1 = new NumField("PRICE1", 20, 0);
                    NumField PRICE2 = new NumField("PRICE2", 20, 0);
                    NumField PRICE3 = new NumField("PRICE3", 20, 0);
                    NumField PRICE4 = new NumField("PRICE4", 20, 0);
                    NumField PRICE5 = new NumField("PRICE5", 20, 0);
                    NumField PRICE6 = new NumField("PRICE6", 20, 0);
                    LogicalField SELPRICE = new LogicalField("SELPRICE");
                    LogicalField MANPRICE = new LogicalField("MANPRICE");
                    NumField TAXNO = new NumField("TAXNO", 5, 0);
                    NumField DISCNO = new NumField("DISCNO", 5, 0);
                    CharField PLUSUPP = new CharField("PLUSUPP", 10);
                    CharField PLUPACK = new CharField("PLUPACK", 10);
                    NumField QUANTPACK = new NumField("QUANTPACK", 5, 0);
                    NumField DEPNO = new NumField("DEPNO", 5, 0);
                    NumField GROUPNO = new NumField("GROUPNO", 5, 0);
                    CharField SERTNO = new CharField("SERTNO", 10);
                    DateField SERTDATE = new DateField("SERTDATE");
                    NumField PQTY2 = new NumField("PQTY2", 5, 0);
                    NumField PQTY3 = new NumField("PQTY3", 5, 0);
                    NumField PQTY4 = new NumField("PQTY4", 5, 0);
                    NumField PQTY5 = new NumField("PQTY5", 5, 0);
                    NumField PQTY6 = new NumField("PQTY6", 5, 0);
                    NumField PMINPRICE = new NumField("PMINPRICE", 10, 0);
                    CharField PSTATUS = new CharField("PSTATUS", 10);
                    CharField PMATVIEN = new CharField("PMATVIEN", 10);

                    List<String> directoriesList = new ArrayList<>();
                    for (CashRegisterInfo cashRegisterInfo : transaction.machineryInfoList) {
                        if ((cashRegisterInfo.port != null) && (!directoriesList.contains(cashRegisterInfo.port)))
                            directoriesList.add(cashRegisterInfo.port);
                        if ((cashRegisterInfo.directory != null) && (!directoriesList.contains(cashRegisterInfo.directory)))
                            directoriesList.add(cashRegisterInfo.directory);
                    }

                    for (String directory : directoriesList) {
                        File folder = new File(directory);
                        if (!folder.exists() && !folder.mkdir())
                            throw new RuntimeException("The folder " + folder.getAbsolutePath() + " can not be created");
                        folder = new File(directory + "/SEND");
                        if (!folder.exists() && !folder.mkdir())
                            throw new RuntimeException("The folder " + folder.getAbsolutePath() + " can not be created");

                        Util.setxBaseJProperty("ignoreMissingMDX", "true");

                        String path = directory + "/SEND/" + transaction.dateTimeCode;
                        file = new DBF(path + ".DBF", DBF.DBASEIV, true, "CP866");


                        file.addField(new Field[]{POSNO, CMD, ERRNO, PLUCODE, ECRID, NAME, PRICE1, PRICE2, PRICE3, PRICE4,
                                PRICE5, PRICE6, SELPRICE, MANPRICE, TAXNO, DISCNO, PLUSUPP, PLUPACK, QUANTPACK, DEPNO, GROUPNO,
                                SERTNO, SERTDATE, PQTY2, PQTY3, PQTY4, PQTY5, PQTY6, PMINPRICE, PSTATUS, PMATVIEN});

                        for (ItemInfo item : transaction.itemsList) {
                            if (!Thread.currentThread().isInterrupted()) {
                                PLUCODE.put(item.idBarcode);
                                NAME.put(item.name);
                                PRICE1.put(item.price.doubleValue());
                                file.write();
                                file.file.setLength(file.file.length() - 1);
                            }
                        }

                        File fileOut = new File(path + ".OUT");
                        if (!fileOut.exists() && !fileOut.createNewFile())
                            throw new RuntimeException("The file " + fileOut.getAbsolutePath() + " can not be created");

                    }
                } finally {
                    if (file != null)
                        file.close();
                }
            } catch (Exception e) {
                exception = e;
            }
            sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(exception));
        }
        return sendTransactionBatchMap;
    }

    @Override
    public MaxishopSalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) throws IOException {
        Map<Integer, CashRegisterInfo> cashRegisterMap = new HashMap<>();
        for (CashRegisterInfo cashRegister : cashRegisterInfoList) {
            if (cashRegister.directory != null) {
                cashRegisterMap.put(cashRegister.number, cashRegister);
            }
        }
        List<SalesInfo> salesInfoList = new ArrayList<>();
        List<String> readFiles = new ArrayList<>();
        for (Map.Entry<Integer, CashRegisterInfo> entry : cashRegisterMap.entrySet()) {
            Integer numberCashRegister = entry.getKey();
            CashRegisterInfo cashRegisterInfo = entry.getValue();
            String dir = cashRegisterInfo.directory;
            DBF importFile = null;
            try {
                if (dir != null && dir.equals(directory)) {
                    File directoryFile = new File(dir + "/READ/");
                    if (directoryFile.isDirectory()) {
                        String[] fileNames = directoryFile.list(new DBFFilter());
                        if (fileNames != null) {
                            for (String fileName : fileNames) {
                                String filePath = dir + "/READ/" + fileName;
                                importFile = new DBF(filePath);
                                readFiles.add(filePath);
                                int recordCount = importFile.getRecordCount();
                                int numberReceiptDetail = 1;
                                Integer oldReceiptNumber = -1;
                                for (int i = 0; i < recordCount; i++) {
                                    importFile.read();
                                    String postType = new String(importFile.getField("JFPOSTYPE").getBytes(), "Cp1251").trim();
                                    if ("P".equals(postType)) {
                                        String zReportNumber = new String(importFile.getField("JFZNO").getBytes(), "Cp1251").trim();
                                        Integer receiptNumber = new Integer(new String(importFile.getField("JFCHECKNO").getBytes(), "Cp1251").trim());
                                        LocalDate date = LocalDate.parse(new String(importFile.getField("JFDATE").getBytes(), "Cp1251").trim(), DateTimeFormatter.ofPattern("yyyyMMdd"));
                                        String timeString = new String(importFile.getField("JFTIME").getBytes(), "Cp1251").trim();
                                        LocalTime time = sqlTimeToLocalTime(Time.valueOf(timeString.substring(0, 2) + ":" + timeString.substring(2, 4) + ":" + timeString.substring(4, 6)));
                                        BigDecimal sumCash = new BigDecimal(new String(importFile.getField("JFTOTSUM").getBytes(), "Cp1251").trim());
                                        String barcodeReceiptDetail = new String(importFile.getField("JFPLUCODE").getBytes(), "Cp1251").trim().replace("E", "");
                                        BigDecimal quantityReceiptDetail = new BigDecimal(new String(importFile.getField("JFQUANT").getBytes(), "Cp1251").trim());
                                        BigDecimal priceReceiptDetail = new BigDecimal(new String(importFile.getField("JFPRICE").getBytes(), "Cp1251").trim());
                                        BigDecimal discountSumReceiptDetail = new BigDecimal(new String(importFile.getField("JFDISCSUM").getBytes(), "Cp1251").trim());
                                        BigDecimal sumReceiptDetail = roundSales(safeSubtract(safeMultiply(priceReceiptDetail, quantityReceiptDetail), discountSumReceiptDetail), 10);

                                        if (!oldReceiptNumber.equals(receiptNumber)) {
                                            numberReceiptDetail = 1;
                                            oldReceiptNumber = receiptNumber;
                                        }
                                        salesInfoList.add(getSalesInfo(numberCashRegister, null, zReportNumber, date, time, receiptNumber, date, time, null, null,
                                                BigDecimal.ZERO, sumCash, BigDecimal.ZERO, barcodeReceiptDetail, null, null, null, quantityReceiptDetail, priceReceiptDetail, sumReceiptDetail,
                                                discountSumReceiptDetail, null, null, numberReceiptDetail, fileName, null, null, cashRegisterInfo));
                                        numberReceiptDetail++;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (xBaseJException e) {
                throw new RuntimeException(e.toString(), e.getCause());
            } finally {
                if (importFile != null)
                    importFile.close();
            }
        }
        return new MaxishopSalesBatch(salesInfoList, readFiles);
    }

    @Override
    public void finishReadingSalesInfo(MaxishopSalesBatch salesBatch) {
        for (String readFile : salesBatch.readFiles) {
            File f = new File(readFile.substring(0, readFile.length() - 3) + "OUT");
            if (!f.delete())
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be deleted");
            f = new File(readFile);
            if (!f.delete())
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be deleted");
        }
    }

    class DBFFilter implements FilenameFilter {
        public boolean accept(File dir, String name) {
            return (name.toLowerCase().endsWith(".dbf") && new File(dir + "/" + name.substring(0, name.length() - 3) + "OUT").exists());
        }
    }

    private BigDecimal roundSales(BigDecimal value, Integer roundSales) {
        Integer round = roundSales != null ? roundSales : 50;
        return BigDecimal.valueOf(Math.round(value.doubleValue() / round) * round);
    }
}
