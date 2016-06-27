package equ.clt.handler.belcoopsoyuz;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
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
import org.xBaseJ.fields.NumField;
import org.xBaseJ.xBaseJException;

import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BelCoopSoyuzHandler extends CashRegisterHandler<BelCoopSoyuzSalesBatch> {

    private FileSystemXmlApplicationContext springContext;

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected final static Logger sendSalesLogger = Logger.getLogger("SendSalesLogger");

    String charset = "cp1251";

    public BelCoopSoyuzHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        return "belcoopsoyuz" + transactionInfo.nameGroupMachinery;
    }

    @Override
    public Map<Integer, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) throws IOException {

        Map<Integer, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

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
                                    waitList.add(Arrays.<Object>asList(ftp, pricePath, flagPricePath, directory));
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
                                    if(ftp)
                                        copyFTPToFile(basePath, baseFile);

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
                                            waitList.add(Arrays.<Object>asList(ftp, priceFile, flagPriceFile, directory));
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

    private boolean copyFTPToFile(String path, File file) throws IOException {
        List<Object> properties = parseFTPPath(path, 21);
        if (properties != null) {
            String username = (String) properties.get(0);
            String password = (String) properties.get(1);
            String server = (String) properties.get(2);
            Integer port = (Integer) properties.get(3);
            String remoteFile = (String) properties.get(4);
            FTPClient ftpClient = new FTPClient();
            ftpClient.setConnectTimeout(3600000); //1 hour = 3600 sec
            try {

                ftpClient.connect(server, port);
                ftpClient.login(username, password);
                ftpClient.enterLocalPassiveMode();
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

                OutputStream outputStream = new FileOutputStream(file);
                boolean done = ftpClient.retrieveFile(remoteFile, outputStream);
                outputStream.close();
                if (!done)
                    processTransactionLogger.info("BelCoopSoyuz: base file was not found, will be created new one");
                return done;
            } catch (IOException e) {
                throw Throwables.propagate(e);
            } finally {
                try {
                    if (ftpClient.isConnected()) {
                        ftpClient.logout();
                        ftpClient.disconnect();
                    }
                } catch (Exception ignored) {
                }
            }
        } else {
            throw Throwables.propagate(new RuntimeException("BelCoopSoyuz: Incorrect ftp url. Please use format: ftp://username:password@host:port/path_to_file"));
        }
    }

    private boolean existsFTPFile(String path) throws IOException {
        List<Object> properties = parseFTPPath(path, 21);
        if (properties != null) {
            String username = (String) properties.get(0);
            String password = (String) properties.get(1);
            String server = (String) properties.get(2);
            Integer port = (Integer) properties.get(3);
            String remoteFile = (String) properties.get(4);
            FTPClient ftpClient = new FTPClient();
            ftpClient.setConnectTimeout(60000); //1 minute = 60 sec
            try {

                ftpClient.connect(server, port);
                ftpClient.login(username, password);
                ftpClient.enterLocalPassiveMode();
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

                InputStream stream = ftpClient.retrieveFileStream(remoteFile);
                int returnCode = ftpClient.getReplyCode();
                return !(stream == null || returnCode == 550);

            } catch (IOException e) {
                throw Throwables.propagate(e);
            } finally {
                try {
                    if (ftpClient.isConnected()) {
                        ftpClient.logout();
                        ftpClient.disconnect();
                    }
                } catch (Exception ignored) {
                }
            }
        } else {
            throw Throwables.propagate(new RuntimeException("BelCoopSoyuz: Incorrect ftp url. Please use format: ftp://username:password@host:port/path_to_file"));
        }
    }

    private boolean backupFTPFile(String path, String from, String to) throws IOException {
        List<Object> properties = parseFTPPath(path, 21);
        if (properties != null) {
            String username = (String) properties.get(0);
            String password = (String) properties.get(1);
            String server = (String) properties.get(2);
            Integer port = (Integer) properties.get(3);
            String remoteDir = (String) properties.get(4);
            FTPClient ftpClient = new FTPClient();
            ftpClient.setConnectTimeout(3600000); //1 hour = 3600 sec
            try {

                ftpClient.connect(server, port);
                ftpClient.login(username, password);
                ftpClient.enterLocalPassiveMode();
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
                ftpClient.makeDirectory("/" + remoteDir + "/backup");
                return ftpClient.rename("/" + remoteDir + "/" + from, "/" + remoteDir + "/" + to) &&
                        ftpClient.rename("/" + remoteDir + "/" + to, "/" + remoteDir + "/backup/" + to);
            } catch (IOException e) {
                throw Throwables.propagate(e);
            } finally {
                try {
                    if (ftpClient.isConnected()) {
                        ftpClient.logout();
                        ftpClient.disconnect();
                    }
                } catch (Exception ignored) {
                }
            }
        } else {
            throw Throwables.propagate(new RuntimeException("BelCoopSoyuz: Incorrect ftp url. Please use format: ftp://username:password@host:port/path_to_file"));
        }
    }

    private List<Object> parseFTPPath(String path, Integer defaultPort) {
        /*sftp|ftp://username:password@host:port/path_to_file*/
        Pattern connectionStringPattern = Pattern.compile("s?ftp:\\/\\/(.*):(.*)@([^\\/:]*)(?::([^\\/]*))?(?:\\/(.*))?");
        Matcher connectionStringMatcher = connectionStringPattern.matcher(path);
        if (connectionStringMatcher.matches()) {
            String username = connectionStringMatcher.group(1); //lstradeby
            String password = connectionStringMatcher.group(2); //12345
            String server = connectionStringMatcher.group(3); //ftp.harmony.neolocation.net
            boolean noPort = connectionStringMatcher.groupCount() == 4;
            Integer port = noPort || connectionStringMatcher.group(4) == null ? defaultPort : Integer.parseInt(connectionStringMatcher.group(4)); //21
            String remoteFile = connectionStringMatcher.group(noPort ? 4 : 5);
            return Arrays.asList((Object) username, password, server, port, remoteFile);
        } else return null;
    }

    public static void storeFileToFTP(String path, File file) throws IOException {
        /*ftp://username:password@host:port/path_to_file*/
        Pattern connectionStringPattern = Pattern.compile("ftp:\\/\\/(.*):(.*)@([^\\/:]*)(?::([^\\/]*))?(?:\\/(.*))?");
        Matcher connectionStringMatcher = connectionStringPattern.matcher(path);
        if (connectionStringMatcher.matches()) {
            String username = connectionStringMatcher.group(1); //lstradeby
            String password = connectionStringMatcher.group(2); //12345
            String server = connectionStringMatcher.group(3); //ftp.harmony.neolocation.net
            boolean noPort = connectionStringMatcher.group(4) == null;
            Integer port = noPort ? 21 : Integer.parseInt(connectionStringMatcher.group(4)); //21
            String remoteFile = connectionStringMatcher.group(5);
            FTPClient ftpClient = new FTPClient();
            ftpClient.setConnectTimeout(3600000); //1 hour = 3600 sec
            try {

                ftpClient.connect(server, port);
                if (ftpClient.login(username, password)) {
                    ftpClient.enterLocalPassiveMode();
                    ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

                    InputStream inputStream = new FileInputStream(file);
                    boolean done = ftpClient.storeFile(remoteFile, inputStream);
                    inputStream.close();
                    if(!done) {
                        processTransactionLogger.error(String.format("BelCoopSoyuz: Failed writing file to %s", path));
                        throw Throwables.propagate(new RuntimeException("BelCoopSoyuz: Some error occurred while writing file to ftp"));
                    }
                } else {
                    throw Throwables.propagate(new RuntimeException("BelCoopSoyuz: Incorrect login or password. Writing file from ftp failed"));
                }
            } finally {
                try {
                    if (ftpClient.isConnected()) {
                        ftpClient.logout();
                        ftpClient.disconnect();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            throw Throwables.propagate(new RuntimeException("BelCoopSoyuz: Incorrect ftp url. Please use format: ftp://username:password@host:port/path_to_file"));
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

            if (append) {
                Util.setxBaseJProperty("ignoreMissingMDX", "true");
                dbfFile = new DBF(baseFile.getAbsolutePath(), charset);
            } else {
                dbfFile = new DBF(baseFile.getAbsolutePath(), DBF.DBASEIV, true, charset);
                dbfFile.addField(new Field[]{CEUNIKEY, CEDOCCOD, CEOBIDE, CEOBMEA, MEOBNAM,
                        NEOBFREE, NEOPLOS, NERECOST, CEOPCURO, NEOPPRIC, NEOPPRIE, NEOPNDS, FORMAT, CECUCOD});
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
            putField(dbfFile, CEOPCURO, getCurrencyCode(transaction.denominationStage), 25, append); //валюта
            for (CashRegisterItemInfo item : transaction.itemsList) {
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
                        putField(dbfFile, MEOBNAM, trim(item.name, 100), 100, append);
                        BigDecimal price = denominateMultiplyType2(item.price, transaction.denominationStage);
                        putNumField(dbfFile, NERECOST, price, append);
                        putNumField(dbfFile, NEOPPRIC, price, append);
                        putNumField(dbfFile, NEOPPRIE, price, append);
                        putNumField(dbfFile, NEOPNDS, item.vat, append);
                        putField(dbfFile, FORMAT, item.splitItem ? "999.999" : "999", 10, append);
                        putNumField(dbfFile, NEOBFREE, item.balance == null ? BigDecimal.ZERO : item.balance, append); //остаток

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

    private String getCurrencyCode(String denominationStage) {
        return denominationStage != null && denominationStage.endsWith("after") ? "BYN 933 1" : "BYR 974 1";
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

    private void putField(DBF dbfFile, Field field, String value, int length, boolean append) throws xBaseJException {
        value = value == null ? "null" : value;
        while (value.length() < length)
            value += " ";
        if (append)
            dbfFile.getField(field.getName()).put(value);
        else
            field.put(value);
    }

    private void putNumField(DBF dbfFile, NumField2 field, BigDecimal value, boolean append) throws xBaseJException {
        if(value != null)
            putNumField(dbfFile, field, value.doubleValue(), append);
    }

    private void putNumField(DBF dbfFile, NumField2 field, double value, boolean append) throws xBaseJException {
        if (append)
            putDouble((NumField) dbfFile.getField(field.getName()), value);
        else
            field.put(value);
    }

    //from NumField2
    private void putDouble(NumField field, double inDouble) throws xBaseJException {
        String inString;

        StringBuilder formatString = new StringBuilder(field.getLength());
        for (int j = 0; j < field.getLength(); j++) {
            formatString.append("#");
        }
        if (field.getDecimalPositionCount() > 0) {
            formatString.setCharAt(field.getDecimalPositionCount(), '.');
        }

        DecimalFormat df = new DecimalFormat(formatString.toString());
        //df.setRoundingMode(RoundingMode.UNNECESSARY);
        inString = df.format(inDouble).trim();
        inString = inString.replace(NumField2.decimalSeparator, '.');

        if (inString.length() > field.Length) {
            throw new xBaseJException("Field length too long; inDouble=" + inString + " (maxLength=" + field.Length + " / format=" + formatString + ")");
        }

        int i;

        //-- fill database
        byte b[];
        try {
            b = inString.getBytes(DBF.encodedType);
        } catch (UnsupportedEncodingException uee) {
            b = inString.getBytes();
        }

        for (i = 0; i < b.length; i++) {
            field.buffer[i] = b[i];
        }

        byte fill;
        if (Util.fieldFilledWithSpaces()) {
            fill = (byte) ' ';
        } else {
            fill = 0;
        }

        for (i = inString.length(); i < field.Length; i++) {
            field.buffer[i] = fill;
        }
    }

    private Exception waitForDeletion(List<List<Object>> waitList, Map<String, Exception> brokenDirectoriesMap) {
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
    }

    @Override
    public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {
    }

    @Override
    public CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList, Set<String> cashDocumentSet) throws ClassNotFoundException {
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

    @Override
    public void finishReadingCashDocumentInfo(CashDocumentBatch cashDocumentBatch) {
    }

    @Override
    public Map<String, Timestamp> requestSucceededSoftCheckInfo(Set<String> directorySet) throws ClassNotFoundException, SQLException {
        return null;
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<String> directorySet) throws IOException {

    }

    @Override
    public SalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) throws IOException, ParseException, ClassNotFoundException {

        Map<String, CashRegisterInfo> sectionCashRegisterMap = new HashMap<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (c.section != null && c.handlerModel != null && c.handlerModel.endsWith("BelCoopSoyuzHandler")) {
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

    private boolean deleteFTPFile(String path) throws IOException {
        List<Object> properties = parseFTPPath(path, 21);
        if (properties != null) {
            String username = (String) properties.get(0);
            String password = (String) properties.get(1);
            String server = (String) properties.get(2);
            Integer port = (Integer) properties.get(3);
            String remoteFile = (String) properties.get(4);
            FTPClient ftpClient = new FTPClient();
            try {

                ftpClient.connect(server, port);
                ftpClient.login(username, password);
                ftpClient.enterLocalPassiveMode();
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

                return ftpClient.deleteFile(remoteFile);
            } catch (IOException e) {
                throw Throwables.propagate(e);
            } finally {
                try {
                    if (ftpClient.isConnected()) {
                        ftpClient.logout();
                        ftpClient.disconnect();
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return true;
    }

    //используем jdbf, а не xbasej, т.к. xbasej не умеет работать с foxpro файлами
    private List<SalesInfo> readSalesFile(File salesFile, Map<String, CashRegisterInfo> sectionCashRegisterMap) throws IOException {
        List<SalesInfo> salesInfoList = new ArrayList<>();
        InputStream dbf = new FileInputStream(salesFile);
        try (DbfReader reader = new DbfReader(dbf)) {
            DbfRecord rec;
            Map<Integer, Integer> numberReceiptDetailMap = new HashMap<>();
            List<SalesInfo> curSalesInfoList = new ArrayList<>();
            while ((rec = reader.read()) != null) {
                rec.setStringCharset(Charset.forName(charset));
                if (!rec.isDeleted()) {

                    Integer numberReceipt = getJDBFIntegerFieldValue(rec, "CEDOCCOD");
                    Date dateReceipt = getJDBFDateFieldValue(rec, "TEDOCINS");
                    Time timeReceipt = getJDBFTimeFieldValue(rec, "TEDOCINS");
                    String barcodeItem = getJDBFFieldValue(rec, "CEOBIDE");
                    String section = getJDBFFieldValue(rec, "CESUCOD");

                    CashRegisterInfo cashRegister = sectionCashRegisterMap.get(section);
                    Integer nppMachinery = cashRegister == null ? null : cashRegister.number;
                    Integer nppGroupMachinery = cashRegister == null ? null : cashRegister.numberGroup;
                    String denominationStage = cashRegister == null ? null : cashRegister.denominationStage;

                    BigDecimal quantityReceiptDetail = getJDBFBigDecimalFieldValue(rec, "NEOPEXP");
                    BigDecimal priceReceiptDetail = denominateDivideType2(getJDBFBigDecimalFieldValue(rec, "NEOPPRIC"), denominationStage);
                    BigDecimal sumReceiptDetail = denominateDivideType2(getJDBFBigDecimalFieldValue(rec, "NEOPSUMCT"), denominationStage);
                    BigDecimal discountSum1ReceiptDetail = denominateDivideType2(getJDBFBigDecimalFieldValue(rec, "NEOPSDELC"), denominationStage);
                    BigDecimal discountSum2ReceiptDetail = denominateDivideType2(getJDBFBigDecimalFieldValue(rec, "NEOPPDELC"), denominationStage);
                    BigDecimal discountSumReceiptDetail = safeNegate(safeAdd(discountSum1ReceiptDetail, discountSum2ReceiptDetail));

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
                                curSalesInfoList.add(new SalesInfo(false, nppGroupMachinery, nppMachinery, numberZReport, dateReceipt, timeReceipt, numberReceipt, dateReceipt,
                                        timeReceipt, idEmployee, null, null, null/*sumCard*/, null/*sumCash*/, null, barcodeItem, null, null, idSaleReceiptReceiptReturnDetail, quantityReceiptDetail,
                                        priceReceiptDetail, sumReceiptDetail, discountSumReceiptDetail, null, null/*idDiscountCard*/, numberReceiptDetail, null, idSection));
                                break;
                            case "ТОВАР ВОЗВРАТ":
                                curSalesInfoList.add(new SalesInfo(false, nppGroupMachinery, nppMachinery, numberZReport, dateReceipt, timeReceipt, numberReceipt, dateReceipt,
                                        timeReceipt, idEmployee, null, null, null/*sumCard*/, null/*sumCash*/, null, barcodeItem, null, null, idSaleReceiptReceiptReturnDetail, safeNegate(quantityReceiptDetail),
                                        priceReceiptDetail, safeNegate(sumReceiptDetail), discountSumReceiptDetail, null, null/*idDiscountCard*/, numberReceiptDetail, null, idSection));
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
                                    salesInfo.sumCash = safeNegate(getJDBFBigDecimalFieldValue(rec, "NEOPSUMCT"));
                                    salesInfoList.add(salesInfo);
                                }
                                curSalesInfoList = new ArrayList<>();
                                break;
                        }
                    }

                }
            }
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

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList, Set<String> directorySet,
                                 Set<Integer> succeededRequests, Map<Integer, String> failedRequests, Map<Integer, String> ignoredRequests) throws IOException, ParseException {
    }

    protected BigDecimal safeAdd(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else return (operand1 == null ? operand2 : (operand2 == null ? operand1 : operand1.add(operand2)));
    }

    protected BigDecimal safeNegate(BigDecimal operand) {
        return operand == null ? null : operand.negate();
    }

    protected String trim(String input, Integer length) {
        return input == null ? null : (length == null || length >= input.trim().length() ? input.trim() : input.trim().substring(0, length));
    }

    public String appendBarcode(String barcode) {
        if (barcode != null) {
            if (barcode.length() == 5)
                barcode = "22" + barcode + "00000";
            if (barcode.length() == 12) {
                try {
                    int checkSum = 0;
                    for (int i = 0; i <= 10; i = i + 2) {
                        checkSum += Integer.valueOf(String.valueOf(barcode.charAt(i)));
                        checkSum += Integer.valueOf(String.valueOf(barcode.charAt(i + 1))) * 3;
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
        return new SimpleDateFormat("dd-MM-yyyy-hh-mm-ss").format(Calendar.getInstance().getTime());
    }

    protected String getDBFFieldValue(DBF importFile, String fieldName, String charset) throws UnsupportedEncodingException {
        try {
            String result = new String(importFile.getField(fieldName).getBytes(), charset).trim();
            return result.isEmpty() ? null : result;
        } catch (xBaseJException e) {
            return null;
        }
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

        // Convert to Java date by converting days to milliseconds and adjusting to Java epoch.
        return new Date(dateFieldDays * DAY_TO_MILLIS_FACTOR - BASE_FOXPRO_MILLIS + timeFieldMillis);
    }


    private void safeFileDelete(File file, Logger logger) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
            logger.info(String.format("BelCoopSoyuz: cannot delete file %s, will try to deleteOnExit", file.getAbsolutePath()));
        }
    }
}
