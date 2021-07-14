package equ.clt.handler.belcoopsoyuz;

import com.google.common.base.Throwables;
import equ.api.SalesInfo;
import equ.api.SendTransactionBatch;
import equ.api.cashregister.CashRegisterInfo;
import equ.api.cashregister.CashRegisterItem;
import equ.api.cashregister.TransactionCashRegisterInfo;
import equ.clt.handler.DefaultCashRegisterHandler;
import equ.clt.handler.HandlerUtils;
import equ.clt.handler.NumField2;
import lsfusion.base.file.FTPPath;
import net.iryndin.jdbf.core.DbfRecord;
import net.iryndin.jdbf.reader.DbfReader;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.util.FileCopyUtils;
import org.xBaseJ.DBF;
import org.xBaseJ.Util;
import org.xBaseJ.fields.CharField;
import org.xBaseJ.fields.Field;
import org.xBaseJ.xBaseJException;

import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static equ.clt.EquipmentServer.sqlDateToLocalDate;
import static equ.clt.EquipmentServer.sqlTimeToLocalTime;
import static equ.clt.handler.DBFUtils.*;
import static lsfusion.base.file.FTPPath.parseFTPPath;

public class BelCoopSoyuzHandler extends DefaultCashRegisterHandler<BelCoopSoyuzSalesBatch> {

    private FileSystemXmlApplicationContext springContext;

    String charset = "cp1251";

    private static String logPrefix = "BelCoopSoyuz: ";

    public BelCoopSoyuzHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        return "belcoopsoyuz" + transactionInfo.nameGroupMachinery;
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) {

        Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        Map<String, Exception> brokenDirectoriesMap = new HashMap<>();
        for (TransactionCashRegisterInfo transaction : transactionList) {

            Exception exception = null;
            try {

                if (transaction.itemsList.isEmpty()) {
                    processTransactionLogger.info(String.format("BelCoopSoyuz: Transaction # %s has no items", transaction.id));
                } else {
                    processTransactionLogger.info(String.format("BelCoopSoyuz: Send Transaction # %s", transaction.id));

                    Map<String, CashRegisterInfo> directoryMap = new HashMap<>();
                    for (CashRegisterInfo cashRegister : transaction.machineryInfoList) {
                        if(cashRegister.directory != null) {
                            directoryMap.put(cashRegister.directory, cashRegister);
                        }
                    }

                    List<List<Object>> waitList = new ArrayList<>();
                    for (Map.Entry<String, CashRegisterInfo> entry : directoryMap.entrySet()) {

                        String directory = entry.getKey();
                        CashRegisterInfo cashRegister = entry.getValue();
                        if (brokenDirectoriesMap.containsKey(directory)) {
                            exception = brokenDirectoriesMap.get(directory);
                        } else {
                            boolean ftp = directory.startsWith("ftp://");
                            String priceName = "a9sk34lsf";
                            String baseName = "base";
                            if(ftp) {
                                String pricePath = directory + "/" + priceName + ".dbf";
                                String flagPricePath = directory + "/" + priceName + ".lsf";
                                String basePath = directory + "/" + baseName + ".dbf";
                                processTransactionLogger.info(String.format("BelCoopSoyuz: Transaction # %s, writing to %s", transaction.id, directory));

                                File baseFile = File.createTempFile(baseName, ".dbf");
                                File baseMdxFile = new File(baseFile.getAbsolutePath().replace(".dbf", ".mdx"));
                                File flagPriceFile = File.createTempFile(priceName, ".lsf");

                                try {
                                    boolean append = !transaction.snapshot && copyFTPToFile(basePath, baseFile);

                                    //write to local base file
                                    writeBaseFile(transaction, cashRegister, baseFile, append);

                                    processTransactionLogger.info(String.format("BelCoopSoyuz: Transaction # %s started copying %s file", transaction.id, pricePath));
                                    storeFileToFTP(basePath, baseFile);
                                    storeFileToFTP(pricePath, baseFile);
                                    processTransactionLogger.info(String.format("BelCoopSoyuz: Transaction # %s finished copying %s file", transaction.id, pricePath));
                                    storeFileToFTP(flagPricePath, flagPriceFile);
                                    waitList.add(Arrays.asList(ftp, pricePath, flagPricePath, directory));
                                } catch (Exception e) {
                                    brokenDirectoriesMap.put(directory, e);
                                    exception = e;
                                    processTransactionLogger.error("BelCoopSoyuz: error while create files", e);
                                } finally {
                                    safeFileDelete(baseFile, processTransactionLogger);
                                    safeFileDelete(baseMdxFile, processTransactionLogger);
                                    safeFileDelete(flagPriceFile, processTransactionLogger);
                                }
                            } else {
                                String pricePath = directory + "/" + priceName + ".dbf";
                                String flagPricePath = directory + "/" + priceName + ".lsf";
                                String basePath = directory + "/" + baseName + ".dbf";
                                String baseMDXPath = directory + "/" + baseName + ".mdx";
                                if (new File(directory).exists() || new File(directory).mkdirs()) {
                                    processTransactionLogger.info(String.format("BelCoopSoyuz: Transaction # %s, writing to %s", transaction.id, directory));

                                    File baseFile = new File(basePath);
                                    File baseMdxFile = new File(baseMDXPath);

                                    if(transaction.snapshot) {
                                        safeFileDelete(baseFile, processTransactionLogger);
                                        safeFileDelete(baseMdxFile, processTransactionLogger);
                                    }

                                    boolean append = !transaction.snapshot && baseFile.exists();

                                    File priceFile = new File(pricePath);
                                    File flagPriceFile = new File(flagPricePath);
                                    safeFileDelete(flagPriceFile, processTransactionLogger);
                                    try {
                                        //write to local base file
                                        writeBaseFile(transaction, cashRegister, baseFile, append);

                                        processTransactionLogger.info(String.format("BelCoopSoyuz: Transaction # %s started copying %s file", transaction.id, pricePath));
                                        FileCopyUtils.copy(baseFile, priceFile);
                                        processTransactionLogger.info(String.format("BelCoopSoyuz: Transaction # %s finished copying %s file", transaction.id, pricePath));
                                        if (flagPriceFile.createNewFile())
                                            waitList.add(Arrays.asList(ftp, priceFile, flagPriceFile, directory));
                                        else {
                                            processTransactionLogger.error("BelCoopSoyuz: error while create flag file " + flagPriceFile.getAbsolutePath());
                                        }
                                    } catch (Exception e) {
                                        brokenDirectoriesMap.put(directory, e);
                                        exception = e;
                                        processTransactionLogger.error("BelCoopSoyuz: error while create files", e);
                                    }

                                } else {
                                    processTransactionLogger.error("BelCoopSoyuz: error while create files: unable to create dir " + directory);
                                    exception = new RuntimeException("BelCoopSoyuz: error while create files: unable to create dir " + directory);
                                }
                            }
                        }
                    }
                    //processTransactionLogger.info(String.format("BelCoopSoyuz: Transaction # %s wait for deletion", transaction.id));
                    Exception deletionException = null;//waitForDeletion(waitList, brokenDirectoriesMap); не ждём, пока заберут флаг и файл, переходим к следующей транзакции
                    //processTransactionLogger.info(String.format("BelCoopSoyuz: Transaction # %s end waiting for deletion", transaction.id));
                    exception = exception == null ? deletionException : exception;
                }
            } catch (Exception e) {
                exception = e;
            }
            sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(exception));
        }
        return sendTransactionBatchMap;
    }

    public static void storeFileToFTP(String path, File file) throws IOException {
        FTPPath ftpPath = parseFTPPath(path);
        FTPClient ftpClient = new FTPClient();
        ftpClient.setDefaultTimeout(3600000);
        ftpClient.setConnectTimeout(3600000); //1 hour = 3600 sec
        try {

            ftpClient.connect(ftpPath.server, ftpPath.port);
            if (ftpClient.login(ftpPath.username, ftpPath.password)) {
                configureFTPClient(ftpClient, ftpPath);

                InputStream inputStream = new FileInputStream(file);
                ftpClient.setDataTimeout(3600000);
                boolean done = ftpClient.storeFile(ftpPath.remoteFile, inputStream);
                inputStream.close();
                if (!done) {
                    processTransactionLogger.error(String.format("BelCoopSoyuz: Failed writing file to %s", path));
                    throw new RuntimeException("BelCoopSoyuz: Some error occurred while writing file to ftp");
                }
            } else {
                throw new RuntimeException("BelCoopSoyuz: Incorrect login or password. Writing file from ftp failed");
            }
        } finally {
            disconnect(ftpClient);
        }
    }

    private static void configureFTPClient(FTPClient ftpClient, FTPPath ftpPath) throws IOException {
        if (ftpPath.passiveMode) {
            ftpClient.enterLocalPassiveMode();
        }
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        if (ftpPath.binaryTransferMode) {
            ftpClient.setFileTransferMode(FTP.BINARY_FILE_TYPE);
        }
    }

    private boolean copyFTPToFile(String path, File file) {
        FTPPath ftpPath = parseFTPPath(path);
        FTPClient ftpClient = new FTPClient();
        ftpClient.setDefaultTimeout(3600000);
        ftpClient.setConnectTimeout(3600000); //1 hour = 3600 sec
        try {

            ftpClient.connect(ftpPath.server, ftpPath.port);
            ftpClient.login(ftpPath.username, ftpPath.password);
            configureFTPClient(ftpClient, ftpPath);

            OutputStream outputStream = new FileOutputStream(file);
            ftpClient.setDataTimeout(3600000);
            boolean done = ftpClient.retrieveFile(ftpPath.remoteFile, outputStream);
            outputStream.close();
            if (!done)
                processTransactionLogger.info("BelCoopSoyuz: base file was not found, will be created new one");
            return done;
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } finally {
            disconnect(ftpClient);
        }
    }

    private boolean existsFTPFile(String path) {
        FTPPath ftpPath = parseFTPPath(path);
        FTPClient ftpClient = new FTPClient();
        ftpClient.setDefaultTimeout(60000);
        ftpClient.setConnectTimeout(60000); //1 minute = 60 sec
        try {

            ftpClient.connect(ftpPath.server, ftpPath.port);
            ftpClient.login(ftpPath.username, ftpPath.password);
            ftpClient.setBufferSize(1024 * 1024);
            configureFTPClient(ftpClient, ftpPath);

            ftpClient.setDataTimeout(60000);
            InputStream stream = ftpClient.retrieveFileStream(ftpPath.remoteFile);
            int returnCode = ftpClient.getReplyCode();
            return !(stream == null || returnCode == 550);

        } catch (IOException e) {
            throw Throwables.propagate(e);
        } finally {
            disconnect(ftpClient);
        }
    }

    private void writeBaseFile(TransactionCashRegisterInfo transaction, CashRegisterInfo cashRegister, File baseFile, boolean append) throws IOException, xBaseJException {
        DBF dbfFile = null;
        try {

            CharField CEUNIKEY = new CharField("CEUNIKEY", 25);
            CharField CEDOCCOD = new CharField("CEDOCCOD", 25);
            CharField CEOBIDE = new CharField("CEOBIDE", 25);
            CharField CEOBMEA = new CharField("CEOBMEA", 25);
            CharField MEOBNAM = new CharField("MEOBNAM", 100);
            NumField2 NEOBFREE = new NumField2("NEOBFREE", 18, 6);
            NumField2 NEOPLOS = new NumField2("NEOPLOS", 18, 6);
            NumField2 NERECOST = new NumField2("NERECOST", 18, 6);
            CharField CEOPCURO = new CharField("CEOPCURO", 25);
            NumField2 NEOPPRIC = new NumField2("NEOPPRIC", 18, 2);
            NumField2 NEOPPRIE = new NumField2("NEOPPRIE", 18, 2);
            NumField2 NEOPNDS = new NumField2("NEOPNDS", 6, 2);
            CharField CECUCOD = new CharField("CECUCOD", 25);
            CharField FORMAT = new CharField("FORMAT", 10);
            CharField CESUCOD = new CharField("CESUCOD", 25);
            NumField2 NEASPRIC = new NumField2("NEASPRIC", 13, 2);

            if (append) {
                Util.setxBaseJProperty("ignoreMissingMDX", "true");
                dbfFile = new DBF(baseFile.getAbsolutePath(), charset);
            } else {
                dbfFile = new DBF(baseFile.getAbsolutePath(), DBF.DBASEIV, true, charset);
                dbfFile.addField(new Field[]{CEUNIKEY, CEDOCCOD, CEOBIDE, CEOBMEA, MEOBNAM,
                        NEOBFREE, NEOPLOS, NERECOST, CEOPCURO, NEOPPRIC, NEOPPRIE, NEOPNDS, FORMAT,
                        CECUCOD, CESUCOD, NEASPRIC});
            }

            Set<String> usedBarcodes = new HashSet<>();
            Map<String, Integer> barcodeRecordMap = new HashMap<>();
            for (int i = 1; i <= dbfFile.getRecordCount(); i++) {
                dbfFile.read();
                String barcode = getDBFFieldValue(dbfFile, "CEUNIKEY", charset);
                barcodeRecordMap.put(barcode, i);
            }
            dbfFile.startTop();

            putField(dbfFile, CEDOCCOD, "Прайс-лист", 25, append); //константа
            putNumField(dbfFile, NEOPLOS, -1, append); //остаток не контролируется
            putField(dbfFile, CECUCOD, cashRegister.section, 25, append); //секция, "600358416 MF"
            putField(dbfFile, CEOPCURO, "BYN 933 1", 25, append); //валюта
            for (CashRegisterItem item : transaction.itemsList) {
                if (!Thread.currentThread().isInterrupted()) {

                    String barcode = appendBarcode(item.idBarcode);
                    if (!usedBarcodes.contains(barcode)) {
                        Integer recordNumber = null;
                        if (append) {
                            recordNumber = barcodeRecordMap.get(barcode);
                            if (recordNumber != null)
                                dbfFile.gotoRecord(recordNumber);
                        }

                        putField(dbfFile, CEUNIKEY, barcode, 25, append);
                        putField(dbfFile, CEOBIDE, barcode, 25, append);
                        putField(dbfFile, CEOBMEA, item.shortNameUOM, 25, append);
                        putField(dbfFile, MEOBNAM, item.name, 100, append);
                        putNumField(dbfFile, NERECOST, item.price, append);
                        putNumField(dbfFile, NEOPPRIC, item.price, append);
                        putNumField(dbfFile, NEOPPRIE, item.price, append);
                        putNumField(dbfFile, NEOPNDS, item.vat, append);
                        putField(dbfFile, FORMAT, item.splitItem ? "999.999" : "999", 10, append);
                        putNumField(dbfFile, NEOBFREE, item.balance == null ? BigDecimal.ZERO : item.balance, append); //остаток
                        putField(dbfFile, CESUCOD, item.section, 25, append);
                        putNumField(dbfFile, NEASPRIC, item.flags == null || ((item.flags & 16) == 0) ? item.price : item.minPrice != null ? item.minPrice : BigDecimal.ZERO, append);

                        if (recordNumber != null)
                            dbfFile.update();
                        else {
                            dbfFile.write();
                            dbfFile.file.setLength(dbfFile.file.length() - 1);
                            if (append)
                                barcodeRecordMap.put(barcode, barcodeRecordMap.size() + 1);
                        }
                        usedBarcodes.add(barcode);
                    }
                }
            }
        } finally {
            if (dbfFile != null)
                dbfFile.close();
        }
    }

/*    private Exception waitForDeletion(List<List<Object>> waitList, Map<String, Exception> brokenDirectoriesMap) {
        int count = 0;
        while (!Thread.currentThread().isInterrupted() && !waitList.isEmpty()) {
            try {
                List<List<Object>> nextWaitList = new ArrayList<>();
                count++;
                if (count >= 120) {
                    break;
                }
                for (List<Object> waitEntry : waitList) {
                    boolean ftp = (boolean) waitEntry.get(0);
                    if(ftp) {
                        String file = (String) waitEntry.get(1);
                        String flagFile = (String) waitEntry.get(2);
                        String directory = (String) waitEntry.get(3);
                        if (existsFTPFile(flagFile) || existsFTPFile(file))
                            nextWaitList.add(Arrays.<Object>asList(true, file, flagFile, directory));
                    } else {
                        File file = (File) waitEntry.get(1);
                        File flagFile = (File) waitEntry.get(2);
                        String directory = (String) waitEntry.get(3);
                        if (flagFile.exists() || file.exists())
                            nextWaitList.add(Arrays.<Object>asList(false, file, flagFile, directory));
                    }
                }
                waitList = nextWaitList;
                Thread.sleep(1000);
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }

        try {
            String exception = waitList.isEmpty() ? null : "BelCoopSoyuz: files has been created but not processed by cash register machine: ";
            for (List<Object> waitEntry : waitList) {
                boolean ftp = (boolean) waitEntry.get(0);
                if (ftp) {
                    String file = (String) waitEntry.get(1);
                    String flagFile = (String) waitEntry.get(2);
                    String directory = (String) waitEntry.get(3);
                    if (existsFTPFile(file))
                        exception += file + "; ";
                    if (existsFTPFile(flagFile))
                        exception += flagFile + "; ";
                    brokenDirectoriesMap.put(directory, new RuntimeException(exception));
                } else {
                    File file = (File) waitEntry.get(1);
                    File flagFile = (File) waitEntry.get(2);
                    String directory = (String) waitEntry.get(3);
                    if (file.exists())
                        exception += file.getAbsolutePath() + "; ";
                    if (flagFile.exists())
                        exception += flagFile.getAbsolutePath() + "; ";
                    brokenDirectoriesMap.put(directory, new RuntimeException(exception));
                }
            }
            return exception == null ? null : new RuntimeException(exception);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }*/

    @Override
    public BelCoopSoyuzSalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) throws IOException {

        Map<String, CashRegisterInfo> sectionCashRegisterMap = new HashMap<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (fitHandler(c) && c.section != null) {
                sectionCashRegisterMap.put(c.section, c);
            }
        }

        List<SalesInfo> salesInfoList = new ArrayList<>();
        Map<String, Boolean> filePathMap = new HashMap<>();

        String timestamp = getCurrentTimestamp();
        String salesName = "a9ck07lsf";

        boolean ftp = directory.startsWith("ftp://");
        if (ftp) {

            File remoteSalesFile = new File(directory + "/" + salesName + ".dbf");

            String salesPath = directory + "/" + salesName + ".dbf";
            String audPath = directory + "/" + salesName + ".lsf";
            String salesFlagPath = directory + "/" + timestamp + "-" + salesName + ".lsf";

            if (!existsFTPFile(salesPath))
                sendSalesLogger.info(String.format("BelCoopSoyuz: %s.dbf not found in %s", salesName, directory));
            else if (existsFTPFile(audPath))
                sendSalesLogger.info(String.format("BelCoopSoyuz: found %s.dbf with stop flag in %s", salesName, directory));
            else {
               File lsfFlagFile = File.createTempFile(salesName, ".lsf");
                try {
                    storeFileToFTP(salesFlagPath, lsfFlagFile);
                    sendSalesLogger.info(String.format("BelCoopSoyuz: found %s.dbf in %s, lsf flag created", salesName, directory));

                    File salesFile = null;
                    boolean copyError = false;
                    try {
                        salesFile = File.createTempFile(salesName, ".dbf");
                        sendSalesLogger.info(String.format("BelCoopSoyuz: Start copying %s.dbf from %s to local file", salesName, directory));
                        copyFTPToFile(salesPath, salesFile);
                        sendSalesLogger.info(String.format("BelCoopSoyuz: End copying %s.dbf from %s to to local file", salesName, directory));
                    } catch (Exception e) {
                        copyError = true;
                        sendSalesLogger.error("BelCoopSoyuz: File copy failed: " + salesPath, e);
                    }

                    try {
                        if (!copyError)
                            salesInfoList.addAll(readSalesFile(salesFile, sectionCashRegisterMap));

                        if (salesFile != null) {
                            String backupName = salesName + "-" + timestamp + ".dbf";
                            sendSalesLogger.info(String.format("Start copying %s.dbf from %s to %s", salesName, directory, backupName));
                            backupFTPFile(directory, salesName + ".dbf", backupName);
                            sendSalesLogger.info(String.format("End copying %s.dbf from %s to %s", salesName, directory, backupName));
                        }

                    } catch (Throwable e) {
                        sendSalesLogger.error("File copy failed: " + salesPath, e);
                    } finally {
                        if (!copyError) {
                            deleteFTPFile(salesFlagPath);
                            if (salesInfoList.isEmpty()) {
                                deleteFTPFile(salesPath);
                            }
                            else
                                filePathMap.put(remoteSalesFile.getAbsolutePath(), true);
                        }
                        safeFileDelete(salesFile, sendSalesLogger);
                    }
                } finally {
                    safeFileDelete(lsfFlagFile, sendSalesLogger);
                }
            }


        } else {

            File remoteSalesFile = new File(directory + "/" + salesName + ".dbf");
            File audFlagFile = new File(directory + "/" + salesName + ".lsf");
            File lsfFlagFile = new File(directory + "/" + timestamp + "-" + salesName + ".lsf");

            try {
                if (!remoteSalesFile.exists()) {
                    sendSalesLogger.info(String.format("BelCoopSoyuz: %s.dbf not found in %s", salesName, directory));
                } else if (audFlagFile.exists()) {
                    sendSalesLogger.info(String.format("BelCoopSoyuz: found %s.dbf with stop flag in %s", salesName, directory));
                } else if (!lsfFlagFile.createNewFile()) {
                    sendSalesLogger.info(String.format("BelCoopSoyuz: found %s.dbf in %s, lsf flag creation failed", salesName, directory));
                } else {
                    sendSalesLogger.info(String.format("BelCoopSoyuz: found %s.dbf in %s, lsf flag created", salesName, directory));

                    File salesFile = null;
                    boolean copyError = false;
                    try {
                        salesFile = File.createTempFile(salesName, ".dbf");
                        sendSalesLogger.info(String.format("Start copying %s.dbf from %s to %s", salesName, remoteSalesFile.getAbsolutePath(), salesFile.getAbsolutePath()));
                        FileCopyUtils.copy(remoteSalesFile, salesFile);
                        sendSalesLogger.info(String.format("End copying %s.dbf from %s to %s", salesName, remoteSalesFile.getAbsolutePath(), salesFile.getAbsolutePath()));
                    } catch (Exception e) {
                        copyError = true;
                        sendSalesLogger.error("File: " + remoteSalesFile.getAbsolutePath(), e);
                    }

                    try {
                        if (!copyError)
                            salesInfoList.addAll(readSalesFile(salesFile, sectionCashRegisterMap));

                        if (salesFile != null) {
                            new File(directory + "/backup").mkdir();
                            File backupSalesFile = new File(directory + "/backup/" + salesName + "-" + timestamp + ".dbf");
                            sendSalesLogger.info(String.format("Start copying %s.dbf from %s to %s", salesName, salesFile.getAbsolutePath(), backupSalesFile.getAbsolutePath()));
                            FileCopyUtils.copy(salesFile, backupSalesFile);
                            sendSalesLogger.info(String.format("End copying %s.dbf from %s to %s", salesName, salesFile.getAbsolutePath(), backupSalesFile.getAbsolutePath()));
                        }

                    } catch (Throwable e) {
                        sendSalesLogger.error("File: " + remoteSalesFile.getAbsolutePath(), e);
                    } finally {
                        if (!copyError) {
                            if (salesInfoList.isEmpty())
                                safeFileDelete(remoteSalesFile, sendSalesLogger);
                            else
                                filePathMap.put(remoteSalesFile.getAbsolutePath(), false);
                        }
                        safeFileDelete(salesFile, sendSalesLogger);
                    }
                }
            } finally {
                safeFileDelete(lsfFlagFile, sendSalesLogger);
            }
        }
        return (salesInfoList.isEmpty() && filePathMap.isEmpty()) ? null :
                new BelCoopSoyuzSalesBatch(salesInfoList, filePathMap);
    }

    private boolean backupFTPFile(String path, String from, String to) {
        FTPPath ftpPath = parseFTPPath(path);
        FTPClient ftpClient = new FTPClient();
        ftpClient.setDefaultTimeout(3600000);
        ftpClient.setConnectTimeout(3600000); //1 hour = 3600 sec
        try {

            ftpClient.connect(ftpPath.server, ftpPath.port);
            ftpClient.login(ftpPath.username, ftpPath.password);
            configureFTPClient(ftpClient, ftpPath);
            ftpClient.makeDirectory("/" + ftpPath.remoteFile + "/backup");
            ftpClient.setDataTimeout(3600000);
            return ftpClient.rename("/" + ftpPath.remoteFile + "/" + from, "/" + ftpPath.remoteFile + "/" + to) &&
                    ftpClient.rename("/" + ftpPath.remoteFile + "/" + to, "/" + ftpPath.remoteFile + "/backup/" + to);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } finally {
            disconnect(ftpClient);
        }
    }

    //используем jdbf, а не xbasej, т.к. xbasej не умеет работать с foxpro файлами
    private List<SalesInfo> readSalesFile(File salesFile, Map<String, CashRegisterInfo> sectionCashRegisterMap) throws IOException {
        List<SalesInfo> salesInfoList = new ArrayList<>();
        InputStream dbf = new FileInputStream(salesFile);
        try (DbfReader reader = new DbfReader(dbf)) {
            DbfRecord rec;
            Map<Integer, Integer> numberReceiptDetailMap = new HashMap<>();
            List<SalesInfo> curSalesInfoList = new ArrayList<>();
            String section = null;
            while ((rec = reader.read()) != null) {
                rec.setStringCharset(Charset.forName(charset));
                if (!rec.isDeleted()) {

                    Integer numberReceipt = getJDBFIntegerFieldValue(rec, "CEDOCCOD");
                    LocalDate dateReceipt = sqlDateToLocalDate(getJDBFDateFieldValue(rec, "TEDOCINS"));
                    LocalTime timeReceipt = sqlTimeToLocalTime(getJDBFTimeFieldValue(rec, "TEDOCINS"));
                    String barcodeItem = getJDBFFieldValue(rec, "CEOBIDE");
                    String curSection = getJDBFFieldValue(rec, "CESUCOD");
                    if(curSection != null)
                        section = curSection;

                    CashRegisterInfo cashRegister = sectionCashRegisterMap.get(section);
                    Integer nppMachinery = cashRegister == null ? null : cashRegister.number;
                    Integer nppGroupMachinery = cashRegister == null ? null : cashRegister.numberGroup;

                    BigDecimal quantityReceiptDetail = getJDBFBigDecimalFieldValue(rec, "NEOPEXP");
                    BigDecimal priceReceiptDetail = getJDBFBigDecimalFieldValue(rec, "NEOPPRIC");
                    BigDecimal sumReceiptDetail = getJDBFBigDecimalFieldValue(rec, "NEOPSUMCT");
                    BigDecimal discountSum1ReceiptDetail = getJDBFBigDecimalFieldValue(rec, "NEOPSDELC");
                    BigDecimal discountSum2ReceiptDetail = getJDBFBigDecimalFieldValue(rec, "NEOPPDELC");
                    BigDecimal discountSumReceiptDetail = HandlerUtils.safeNegate(HandlerUtils.safeAdd(discountSum1ReceiptDetail, discountSum2ReceiptDetail));

                    Integer numberReceiptDetail = numberReceiptDetailMap.get(numberReceipt);
                    numberReceiptDetail = numberReceiptDetail == null ? 1 : (numberReceiptDetail + 1);
                    numberReceiptDetailMap.put(numberReceipt, numberReceiptDetail);
                    String numberZReport = getJDBFFieldValue(rec, "CEDOCNUM");

                    String idEmployee = getJDBFFieldValue(rec, "CEOPDEV");
                    String idSaleReceiptReceiptReturnDetail = getJDBFFieldValue(rec, "CEUNIREV");
                    String idSection = getJDBFFieldValue(rec, "CESUCOD");

                    String type = getJDBFFieldValue(rec, "CEOBTYP");
                    if (type != null) {
                        switch (type) {
                            case "ТОВАР":
                                curSalesInfoList.add(getSalesInfo(nppGroupMachinery, nppMachinery, numberZReport, dateReceipt, timeReceipt, numberReceipt, dateReceipt,
                                        timeReceipt, idEmployee, null, null/*sumCard*/, null/*sumCash*/, null, null, barcodeItem, null, null, idSaleReceiptReceiptReturnDetail, quantityReceiptDetail,
                                        priceReceiptDetail, sumReceiptDetail, discountSumReceiptDetail, null, null/*idDiscountCard*/, numberReceiptDetail, null, idSection, null, cashRegister));
                                break;
                            case "ТОВАР ВОЗВРАТ":
                                curSalesInfoList.add(getSalesInfo(nppGroupMachinery, nppMachinery, numberZReport, dateReceipt, timeReceipt, numberReceipt, dateReceipt,
                                        timeReceipt, idEmployee, null, null/*sumCard*/, null/*sumCash*/, null, null, barcodeItem, null, null, idSaleReceiptReceiptReturnDetail, HandlerUtils.safeNegate(quantityReceiptDetail),
                                        priceReceiptDetail, HandlerUtils.safeNegate(sumReceiptDetail), discountSumReceiptDetail, null, null/*idDiscountCard*/, numberReceiptDetail, null, idSection, null, cashRegister));
                                break;
                            case "ВСЕГО":
                                for (SalesInfo salesInfo : curSalesInfoList) {
                                    salesInfo.sumCash = getJDBFBigDecimalFieldValue(rec, "NEOPSUMCT");
                                    salesInfoList.add(salesInfo);
                                }
                                curSalesInfoList = new ArrayList<>();
                                break;
                            case "ВОЗВРАТ":
                                for (SalesInfo salesInfo : curSalesInfoList) {
                                    salesInfo.sumCash = HandlerUtils.safeNegate(getJDBFBigDecimalFieldValue(rec, "NEOPSUMCT"));
                                    salesInfoList.add(salesInfo);
                                }
                                curSalesInfoList = new ArrayList<>();
                                break;
                        }
                    }

                }
            }
        } catch (Exception e) {
            sendSalesLogger.error(logPrefix + "ReadSalesFile failed", e);
        }
        return salesInfoList;
    }

    @Override
    public void finishReadingSalesInfo(BelCoopSoyuzSalesBatch salesBatch) {
        sendSalesLogger.info("BelCoopSoyuz: Finish Reading started");
        for (Map.Entry<String, Boolean> readFile : salesBatch.readFiles.entrySet()) {
            try {
                if (readFile.getValue() ? deleteFTPFile(readFile.getKey()) : new File(readFile.getKey()).delete())
                    sendSalesLogger.info("BelCoopSoyuz: file " + readFile.getKey() + " has been deleted");
                else
                    throw new RuntimeException("The file " + readFile.getKey() + " can not be deleted");
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
    }

    public String appendBarcode(String barcode) {
        if (barcode != null) {
            if (barcode.length() == 5)
                barcode = "22" + barcode + "00000";
            if (barcode.length() == 12) {
                try {
                    int checkSum = 0;
                    for (int i = 0; i <= 10; i = i + 2) {
                        checkSum += Integer.parseInt(String.valueOf(barcode.charAt(i)));
                        checkSum += Integer.parseInt(String.valueOf(barcode.charAt(i + 1))) * 3;
                    }
                    checkSum %= 10;
                    if (checkSum != 0)
                        checkSum = 10 - checkSum;
                    barcode = barcode.concat(String.valueOf(checkSum));
                } catch (Exception ignored) {
                }
            }
        }
        return barcode;
    }

    protected String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy-hh-mm-ss"));
    }

    protected String getJDBFFieldValue(DbfRecord rec, String column) {
        return rec.getString(column);
    }

    protected BigDecimal getJDBFBigDecimalFieldValue(DbfRecord rec, String column) {
        return rec.getBigDecimal(column);
    }

    protected Integer getJDBFIntegerFieldValue(DbfRecord rec, String column) {
        Integer result;
        String value = rec.getString(column);
        try {
            result = value == null ? null : Integer.parseInt(value);
        } catch (Exception e) {
            result = null;
        }
        return result;
    }

    protected Date getJDBFDateFieldValue(DbfRecord rec, String column) {
        return new Date(convertFoxProDate(rec.getBytes(column)).getTime());
    }

    protected Time getJDBFTimeFieldValue(DbfRecord rec, String column) {
        return new Time(convertFoxProDate(rec.getBytes(column)).getTime());
    }

    public static Date convertFoxProDate( byte[] foxProDate ) {
        long BASE_FOXPRO_MILLIS = 210866803200000L;
        long DAY_TO_MILLIS_FACTOR = 24L * 60L * 60L * 1000L;

        if ( foxProDate.length != 8 ) {
            throw new IllegalArgumentException("FoxPro date must be 8 bytes long");
        }

        // FoxPro date is stored with bytes reversed.
        byte[] reversedBytes = new byte[8];
        for ( int i = 0;  i < 8; i++ ) {
            reversedBytes[i] = foxProDate[8 - i - 1];
        }

        // Grab the two integer fields from the byte array
        ByteBuffer buf = ByteBuffer.wrap(reversedBytes);

        long timeFieldMillis = buf.getInt();
        long dateFieldDays = buf.getInt();

        //Дата-время хранятся в FoxPro как 2 Integer. Время хранится как число миллисекунд. К примеру, 9 часов по Минску
        //хранятся как 9 часов по UTC (12 по Минску), поэтому при чтении мы отнимаем смещение +3 и получаем в итоге
        //правильные 6 часов по UTC (9 часов по Минску)
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(dateFieldDays * DAY_TO_MILLIS_FACTOR - BASE_FOXPRO_MILLIS + timeFieldMillis - calendar.get(Calendar.ZONE_OFFSET)));
        return new Date(calendar.getTime().getTime());
    }

    private static void disconnect(FTPClient ftpClient) {
        try {
            if (ftpClient.isConnected()) {
                ftpClient.setSoTimeout(10000);
                ftpClient.logout();
                ftpClient.disconnect();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void safeFileDelete(File file, Logger logger) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
            logger.info(String.format("BelCoopSoyuz: cannot delete file %s, will try to deleteOnExit", file.getAbsolutePath()));
        }
    }

    private boolean deleteFTPFile(String path) {
        FTPPath ftpPath = parseFTPPath(path);
        FTPClient ftpClient = new FTPClient();
        ftpClient.setDefaultTimeout(60000);
        ftpClient.setConnectTimeout(60000); //1 minute = 60 sec
        try {

            ftpClient.connect(ftpPath.server, ftpPath.port);
            ftpClient.login(ftpPath.username, ftpPath.password);
            configureFTPClient(ftpClient, ftpPath);

            ftpClient.setDataTimeout(60000);
            return ftpClient.deleteFile(ftpPath.remoteFile);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } finally {
            disconnect(ftpClient);
        }
    }
}
