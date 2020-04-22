package equ.clt.handler.inventoryTech;

import com.google.common.base.Throwables;
import equ.api.MachineryInfo;
import equ.api.SendTransactionBatch;
import equ.api.TransactionInfo;
import equ.api.terminal.*;
import equ.clt.handler.HandlerUtils;
import equ.clt.handler.NumField2;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.xBaseJ.DBF;
import org.xBaseJ.fields.CharField;
import org.xBaseJ.fields.Field;
import org.xBaseJ.xBaseJException;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static equ.clt.handler.DBFUtils.*;

public class InventoryTechHandler extends TerminalHandler {

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected final static Logger sendTerminalDocumentLogger = Logger.getLogger("TerminalDocumentLogger");

    String charset = "cp866";

    public InventoryTechHandler() {
    }

    @Override
    public String getGroupId(TransactionInfo transactionInfo) {
        return "inventoryTech";
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List transactionList) {

        Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        for(Object transaction : transactionList) {

            Exception exception = null;
            try {

                processTransactionLogger.info("InventoryTechTerminal: send Transaction #" + ((TransactionInfo)transaction).id);

                Set<String> directorySet = new HashSet<>();
                for (Object m : ((TransactionInfo) transaction).machineryInfoList) {
                    TerminalInfo t = (TerminalInfo) m;
                    if (t.directory != null)
                        directorySet.add(t.directory);
                }

                for (String path : directorySet) {
                    if (!Thread.currentThread().isInterrupted()) {
                        File directory = new File(path);
                        if (!directory.exists() && !directory.mkdir())
                            processTransactionLogger.info("Failed to create directory " + directory.getAbsolutePath());

                        if (!directory.exists())
                            processTransactionLogger.info("Directory " + directory.getAbsolutePath() + " doesn't exist");

                        try {
                            Class.forName("org.sqlite.JDBC");

                            createGoodsFile((TransactionTerminalInfo) transaction, path);
                            createBarcodeFile((TransactionTerminalInfo) transaction, path);
                            createSpravFile((TransactionTerminalInfo) transaction, path);
                            createSprDocFile((TransactionTerminalInfo) transaction, path);

                            createEmptyDocFile(path);
                            createEmptyPosFile(path);

                            if(!createBasesUpdFile(path))
                                processTransactionLogger.error("Failed to create " + path + "/BASES.UPD");

                        } catch (Exception e) {
                            processTransactionLogger.error("InventoryTech Error: ", e);
                            throw Throwables.propagate(e);
                        }
                    }
                }
            } catch(Exception e) {
                exception = e;
            }
            sendTransactionBatchMap.put(((TransactionInfo) transaction).id, new SendTransactionBatch(exception));
        }
        return sendTransactionBatchMap;
    }

    @Override
    public void sendTerminalOrderList(List list, MachineryInfo machinery) {
    }

    @Override
    public void saveTransactionTerminalInfo(TransactionTerminalInfo transactionInfo) {
    }

    @Override
    public void finishReadingTerminalDocumentInfo(TerminalDocumentBatch terminalDocumentBatch) {

        for(Map.Entry<String, Set<Integer>> entry : ((InventoryTerminalDocumentBatch) terminalDocumentBatch).docRecordsMap.entrySet()) {

            DBF dbfFile = null;
            try {

                CharField ACCEPTED = new CharField("ACCEPTED", 1);

                dbfFile = new DBF(entry.getKey());

                for (Integer recordNumber : entry.getValue()) {
                    if (recordNumber != null) {
                        dbfFile.gotoRecord(recordNumber);
                        putField(dbfFile, ACCEPTED, "1", 1, true);
                        dbfFile.update();
                    }
                }
            } catch (Exception e) {
                throw Throwables.propagate(e);
            } finally {
                try {
                    if (dbfFile != null)
                        dbfFile.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Override
    public InventoryTerminalDocumentBatch readTerminalDocumentInfo(List machineryInfoList) {

        try {

            Set<String> directorySet = new HashSet<>();
            Map<String, TerminalInfo> directoryMachineryMap = new HashMap<>();
            for (Object m : machineryInfoList) {
                TerminalInfo t = (TerminalInfo) m;
                if (fitHandler(t) && t.directory != null) {
                    directorySet.add(t.directory);
                    directoryMachineryMap.put(t.directory, t);
                }
            }

            List<TerminalDocumentDetail> terminalDocumentDetailList = new ArrayList<>();
            Map<String, Set<Integer>> docRecordsMap = new HashMap<>();

            for (String directory : directorySet) {

                if (!Thread.currentThread().isInterrupted()) {

                    File flagFile = new File(directory + "/doc.upd");
                    if (flagFile.exists()) {

                        String[] docFiles = {"DOC.DBF", "DOC.dbf", "Doc.DBF", "Doc.dbf", "doc.DBF", "doc.dbf"};
                        File docFile = null;
                        for(String name : docFiles) {
                            docFile = new File(directory + "/" + name);
                            if(docFile.exists())
                                break;
                        }

                        String[] posFiles = {"POS.DBF", "POS.dbf", "Pos.DBF", "Pos.dbf", "pos.DBF", "pos.dbf"};
                        File posFile = null;
                        for(String name : posFiles) {
                            posFile = new File(directory + "/" + name);
                            if(posFile.exists())
                                break;
                        }

                        if (!docFile.exists() || !posFile.exists())
                            sendTerminalDocumentLogger.info("InventoryTech: doc.dbf or pos.dbf not found in " + directory);
                        else {
                            sendTerminalDocumentLogger.info("InventoryTech: found doc.dbf and pos.dbf in " + directory);
                            int count = 0;
                            Map<String, List<Object>> docDataMap = readDocFile(docFile);

                            DBF dbfFile = null;
                            try {

                                dbfFile = new DBF(posFile.getAbsolutePath());
                                int recordCount = dbfFile.getRecordCount();

                                for (int i = 0; i < recordCount; i++) {
                                    dbfFile.read();
                                    if (dbfFile.deleted()) continue;
                                    String idDoc = getDBFFieldValue(dbfFile, "IDDOC", charset);
                                    List<Object> docEntry = docDataMap.get(idDoc);
                                    if (docEntry != null) {
                                        String title = (String) docEntry.get(0);
                                        String idTerminalHandbookType1 = (String) docEntry.get(1);
                                        String idTerminalHandbookType2 = (String) docEntry.get(2);
                                        BigDecimal quantityDocument = (BigDecimal) docEntry.get(3);
                                        String idDocumentType = (String) docEntry.get(4);
                                        LocalDateTime dateTime = (LocalDateTime) docEntry.get(5);
                                        LocalDate date = dateTime != null ? dateTime.toLocalDate() : null;
                                        LocalTime time = dateTime != null ? dateTime.toLocalTime() : null;

                                        TerminalInfo terminal = directoryMachineryMap.get(directory);
                                        Integer numberGroup = terminal == null ? null : terminal.numberGroup;

                                        String idBarcode = getDBFFieldValue(dbfFile, "ARTICUL", charset);
                                        String name = getDBFFieldValue(dbfFile, "NAME", charset);
                                        String number = getDBFFieldValue(dbfFile, "NOMPOS", charset);
                                        BigDecimal price = getDBFBigDecimalFieldValue(dbfFile, "PRICE", charset);
                                        BigDecimal quantity = getDBFBigDecimalFieldValue(dbfFile, "QUAN", charset);
                                        BigDecimal sum = HandlerUtils.safeMultiply(price, quantity);
                                        String idDocument = numberGroup + "/" + idDoc + "/" + dateTime;
                                        String idDocumentDetail = idDocument + "/" + i;
                                        count++;
                                        terminalDocumentDetailList.add(new TerminalDocumentDetail(idDocument, title, date, time,
                                                null, directory, idTerminalHandbookType1, idTerminalHandbookType2, idDocumentType,
                                                quantityDocument, idDocumentDetail, number, idBarcode, name, price, quantity, sum));
                                    }
                                }
                            } finally {
                                if (dbfFile != null)
                                    dbfFile.close();
                            }

                            Set<Integer> docRecordsSet = new HashSet<>();
                            for (Map.Entry<String, List<Object>> entry : docDataMap.entrySet()) {
                                Integer recordNumber = (Integer) entry.getValue().get(6);
                                if (recordNumber != null)
                                    docRecordsSet.add(recordNumber);
                            }
                            docRecordsMap.put(docFile.getAbsolutePath(), docRecordsSet);
                            if (count > 0) {
                                sendTerminalDocumentLogger.info(String.format("InventoryTech: processed %s records in %s", count, directory));
                            }
                        }
                        if (!flagFile.delete())
                            flagFile.deleteOnExit();
                    }
                }
            }

            return new InventoryTerminalDocumentBatch(terminalDocumentDetailList, docRecordsMap);
        } catch (Exception e) {
            sendTerminalDocumentLogger.error("InventoryTech Error: ", e);
            throw Throwables.propagate(e);
        }
    }

    private Map<String, List<Object>> readDocFile(File file) throws IOException, xBaseJException, ParseException {

        Map<String, List<Object>> data = new HashMap<>();
        DBF dbfFile = null;
        try {

            dbfFile = new DBF(file.getAbsolutePath());
            int recordCount = dbfFile.getRecordCount();

            for (int i = 0; i < recordCount; i++) {
                dbfFile.read();
                if (dbfFile.deleted()) continue;
                String idDoc = getDBFFieldValue(dbfFile, "IDDOC", charset);
                String title = getDBFFieldValue(dbfFile, "TITLE", charset);
                String dateTimeValue = getDBFFieldValue(dbfFile, "CRE_DTST", charset);
                LocalDateTime dateTime = dateTimeValue == null ? null : LocalDateTime.parse(dateTimeValue, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                String idTerminalHandbookType1 = getDBFFieldValue(dbfFile, "CSPR1", charset);
                String idTerminalHandbookType2 = getDBFFieldValue(dbfFile, "CSPR2", charset);
                BigDecimal quantityDocument = getDBFBigDecimalFieldValue(dbfFile, "QUANDOC", charset);
                String idDocumentType = getDBFFieldValue(dbfFile, "CVIDDOC", charset);
                String accepted = getDBFFieldValue(dbfFile, "ACCEPTED", charset, false);
                if(accepted != null && (accepted.equals("0") || accepted.equals(" ")))
                    data.put(idDoc, Arrays.asList(title, idTerminalHandbookType1, idTerminalHandbookType2,
                            quantityDocument, idDocumentType, dateTime, i + 1));

            }
        } finally {
            if (dbfFile != null)
                dbfFile.close();
        }
        return data;
    }

    private void createGoodsFile(TransactionTerminalInfo transaction, String path) throws IOException, xBaseJException {

        if (listNotEmpty(transaction.itemsList)) {

            CharField ARTICUL = new CharField("ARTICUL", 15);
            CharField NAME = new CharField("NAME", 200);
            NumField2 QUAN = new NumField2("QUAN", 9, 3);
            NumField2 PRICE = new NumField2("PRICE", 11, 2);
            NumField2 PRICE2 = new NumField2("PRICE2", 11, 2);
            CharField GR_NAME = new CharField("GR_NAME", 200);
            NumField2 FLAGS = new NumField2("FLAGS", 8, 0);
            NumField2 INBOX = new NumField2("INBOX", 9, 3);
            NumField2 IDSET = new NumField2("IDSET", 8, 0);

            File directory = new File(path);
            if (directory.exists()) {
                File fileDBF = new File(path + "/GOODS.dbf");
                File tempFileDPF = File.createTempFile("goods", ".dbf");
                if (!transaction.snapshot && fileDBF.exists()) {
                    FileUtils.copyFile(fileDBF, tempFileDPF);
                }

                File fileMDX = new File(path + "/GOODS.mdx");
                File fileCDX = new File(path + "/GOODS.cdx");
                if (transaction.snapshot) {
                    if(!fileDBF.delete())
                        fileDBF.deleteOnExit();
                    if(!fileMDX.delete())
                        fileMDX.deleteOnExit();
                    if(!fileCDX.delete())
                        fileCDX.deleteOnExit();
                }
                boolean append = !transaction.snapshot && fileDBF.exists();
                DBF dbfWriter = null;
                try {
                    dbfWriter = append ? new DBF(tempFileDPF.getAbsolutePath(), charset) : new DBF(tempFileDPF.getAbsolutePath(), DBF.DBASEIV, true, charset);

                    if (!append)
                        dbfWriter.addField(new Field[]{ARTICUL, NAME, QUAN, PRICE, PRICE2, GR_NAME, FLAGS, INBOX, IDSET});

                    Map<String, Integer> barcodeRecordMap = new HashMap<>();
                    for (int i = 1; i <= dbfWriter.getRecordCount(); i++) {
                        dbfWriter.read();
                        String barcode = getDBFFieldValue(dbfWriter, "ARTICUL", charset);
                        barcodeRecordMap.put(barcode, i);
                    }
                    dbfWriter.startTop();

                    Set<String> usedBarcodes = new HashSet<>();
                    for (TerminalItemInfo item : transaction.itemsList) {
                        if (!Thread.currentThread().isInterrupted()) {
                            if (!usedBarcodes.contains(item.idBarcode)) {
                                Integer recordNumber = null;
                                if (append) {
                                    recordNumber = barcodeRecordMap.get(item.idBarcode);
                                    if (recordNumber != null)
                                        dbfWriter.gotoRecord(recordNumber);
                                }

                                putField(dbfWriter, ARTICUL, item.idBarcode, 15, append);
                                putField(dbfWriter, NAME, item.name, 200, append);
                                putNumField(dbfWriter, QUAN, item.quantity == null ? 1 : item.quantity.intValue(), append);
                                putNumField(dbfWriter, PRICE, item.price == null ? 0 : item.price.doubleValue(), append);

                                if (recordNumber != null)
                                    dbfWriter.update();
                                else {
                                    dbfWriter.write();
                                    dbfWriter.file.setLength(dbfWriter.file.length() - 1);
                                    if (append)
                                        barcodeRecordMap.put(item.idBarcode, barcodeRecordMap.size() + 1);
                                }
                                usedBarcodes.add(item.idBarcode);
                            }
                        }
                    }
                } finally {
                    if(dbfWriter != null)
                        dbfWriter.close();
                }
                FileUtils.copyFile(tempFileDPF, fileDBF);
                safeDelete(tempFileDPF);
            }
        }
    }

    private void createBarcodeFile(TransactionTerminalInfo transaction, String path) throws IOException, xBaseJException {

        if (listNotEmpty(transaction.itemsList)) {

            CharField ARTICUL = new CharField("ARTICUL", 15);
            CharField BARCODE = new CharField("BARCODE", 26);
            NumField2 IDSET = new NumField2("IDSET", 8, 0);

            File directory = new File(path);
            if (directory.exists()) {
                File fileDBF = new File(path + "/BARCODE.dbf");
                File tempFileDPF = File.createTempFile("barcode", ".dbf");
                if (!transaction.snapshot && fileDBF.exists()) {
                    FileUtils.copyFile(fileDBF, tempFileDPF);
                }

                File fileMDX = new File(path + "/BARCODE.mdx");
                File fileCDX = new File(path + "/BARCODE.cdx");
                if (transaction.snapshot) {
                    if(!fileDBF.delete())
                        fileDBF.deleteOnExit();
                    if(!fileMDX.delete())
                        fileMDX.deleteOnExit();
                    if(!fileCDX.delete())
                        fileCDX.deleteOnExit();
                }
                boolean append = !transaction.snapshot && fileDBF.exists();
                DBF dbfWriter = null;
                try {
                    dbfWriter = append ? new DBF(tempFileDPF.getAbsolutePath(), charset) : new DBF(tempFileDPF.getAbsolutePath(), DBF.DBASEIV, true, charset);

                    if (!append)
                        dbfWriter.addField(new Field[]{ARTICUL, BARCODE, IDSET});

                    Map<String, Integer> barcodeRecordMap = new HashMap<>();
                    for (int i = 1; i <= dbfWriter.getRecordCount(); i++) {
                        dbfWriter.read();
                        String barcode = getDBFFieldValue(dbfWriter, "ARTICUL", charset);
                        barcodeRecordMap.put(barcode, i);
                    }
                    dbfWriter.startTop();

                    Set<String> usedBarcodes = new HashSet<>();
                    for (TerminalItemInfo item : transaction.itemsList) {
                        if (!Thread.currentThread().isInterrupted()) {
                            if (!usedBarcodes.contains(item.idBarcode)) {
                                Integer recordNumber = null;
                                if (append) {
                                    recordNumber = barcodeRecordMap.get(item.idBarcode);
                                    if (recordNumber != null)
                                        dbfWriter.gotoRecord(recordNumber);
                                }

                                putField(dbfWriter, ARTICUL, item.idBarcode, 15, append);
                                putField(dbfWriter, BARCODE, item.idBarcode, 26, append);

                                if (recordNumber != null)
                                    dbfWriter.update();
                                else {
                                    dbfWriter.write();
                                    dbfWriter.file.setLength(dbfWriter.file.length() - 1);
                                    if (append)
                                        barcodeRecordMap.put(item.idBarcode, barcodeRecordMap.size() + 1);
                                }
                                usedBarcodes.add(item.idBarcode);
                            }
                        }
                    }
                } finally {
                    if(dbfWriter != null)
                        dbfWriter.close();
                }
                FileUtils.copyFile(tempFileDPF, fileDBF);
                safeDelete(tempFileDPF);
            }
        }
    }

    private void createSpravFile(TransactionTerminalInfo transaction, String path) throws IOException, xBaseJException {

        if (listNotEmpty(transaction.terminalLegalEntityList)) {

            CharField CODE = new CharField("CODE", 15);
            CharField NAME = new CharField("NAME", 200);
            NumField2 VIDSPR = new NumField2("VIDSPR", 8, 0);
            CharField COMMENT = new CharField("COMMENT", 200);
            NumField2 IDTERM = new NumField2("IDTERM", 8, 0);
            NumField2 MTERM = new NumField2("MTERM", 8, 0);
            NumField2 DISCOUNT = new NumField2("DISCOUNT", 5, 2);
            NumField2 ROUND = new NumField2("ROUND", 11, 2);
            NumField2 FLAGS = new NumField2("FLAGS", 8, 0);
            NumField2 IDSET = new NumField2("IDSET", 8, 0);

            File directory = new File(path);
            if (directory.exists()) {
                File fileDBF = new File(path + "/SPRAV.dbf");
                File tempFileDPF = File.createTempFile("sprav", ".dbf");
                if (!transaction.snapshot && fileDBF.exists()) {
                    FileUtils.copyFile(fileDBF, tempFileDPF);
                }
                File fileMDX = new File(path + "/SPRAV.mdx");
                File fileCDX = new File(path + "/SPRAV.cdx");
                if (transaction.snapshot) {
                    if(!fileDBF.delete())
                        fileDBF.deleteOnExit();
                    if(!fileMDX.delete())
                        fileMDX.deleteOnExit();
                    if(!fileCDX.delete())
                        fileCDX.deleteOnExit();
                }
                boolean append = !transaction.snapshot && fileDBF.exists();
                DBF dbfWriter = null;
                try {
                    dbfWriter = append ? new DBF(tempFileDPF.getAbsolutePath(), charset) : new DBF(tempFileDPF.getAbsolutePath(), DBF.DBASEIV, true, charset);

                    if (!append)
                        dbfWriter.addField(new Field[]{CODE, NAME, VIDSPR, COMMENT, IDTERM, MTERM, DISCOUNT, ROUND, FLAGS, IDSET});

                    Map<String, Integer> recordMap = new HashMap<>();
                    for (int i = 1; i <= dbfWriter.getRecordCount(); i++) {
                        dbfWriter.read();
                        String code = getDBFFieldValue(dbfWriter, "CODE", charset);
                        recordMap.put(code, i);
                    }
                    dbfWriter.startTop();

                    Set<String> usedCodes = new HashSet<>();
                    putNumField(dbfWriter, VIDSPR, 10, append);
                    for (TerminalLegalEntity le : transaction.terminalLegalEntityList) {
                        if (!Thread.currentThread().isInterrupted()) {
                            if (!usedCodes.contains(le.idLegalEntity)) {
                                Integer recordNumber = null;
                                if (append) {
                                    recordNumber = recordMap.get(le.idLegalEntity);
                                    if (recordNumber != null)
                                        dbfWriter.gotoRecord(recordNumber);
                                }

                                putField(dbfWriter, CODE, le.idLegalEntity, 15, append);
                                putField(dbfWriter, NAME, le.nameLegalEntity, 200, append);

                                if (recordNumber != null)
                                    dbfWriter.update();
                                else {
                                    dbfWriter.write();
                                    dbfWriter.file.setLength(dbfWriter.file.length() - 1);
                                    if (append)
                                        recordMap.put(le.idLegalEntity, recordMap.size() + 1);
                                }
                                usedCodes.add(le.idLegalEntity);
                            }
                            //count++;
                        }
                    }
                } finally {
                    if(dbfWriter != null)
                        dbfWriter.close();
                }
                FileUtils.copyFile(tempFileDPF, fileDBF);
                safeDelete(tempFileDPF);
            }
        }
    }

    private void createSprDocFile(TransactionTerminalInfo transaction, String path) throws IOException, xBaseJException {

        if (listNotEmpty(transaction.terminalDocumentTypeList)) {

            CharField CODE = new CharField("CODE", 15);
            CharField NAME = new CharField("NAME", 50);
            CharField SPRT1 = new CharField("SPRT1", 15);
            NumField2 VIDSPR1 = new NumField2("VIDSPR1", 8, 0);
            CharField SPRT2 = new CharField("SPRT2", 15);
            NumField2 VIDSPR2 = new NumField2("VIDSPR2", 8, 0);
            NumField2 IDTERM = new NumField2("IDTERM", 8, 0);
            NumField2 MTERM = new NumField2("MTERM", 8, 0);
            NumField2 DISCOUNT = new NumField2("DISCOUNT", 5, 2);
            NumField2 COEF = new NumField2("COEF", 8, 0);
            NumField2 ROUND = new NumField2("ROUND", 11, 2);
            NumField2 FLAGS = new NumField2("FLAGS", 8, 0);
            NumField2 IDSET = new NumField2("IDSET",  8, 0);

            File directory = new File(path);
            if (directory.exists()) {
                File fileDBF = new File(path + "/SPRDOC.dbf");
                File tempFileDPF = File.createTempFile("sprdoc", ".dbf");
                if (!transaction.snapshot && fileDBF.exists()) {
                    FileUtils.copyFile(fileDBF, tempFileDPF);
                }
                File fileMDX = new File(path + "/SPRDOC.mdx");
                File fileCDX = new File(path + "/SPRDOC.cdx");
                if (transaction.snapshot) {
                    if(!fileDBF.delete())
                        fileDBF.deleteOnExit();
                    if(!fileMDX.delete())
                        fileMDX.deleteOnExit();
                    if(!fileCDX.delete())
                        fileCDX.deleteOnExit();
                }
                boolean append = !transaction.snapshot && fileDBF.exists();
                DBF dbfWriter = null;
                try {
                    dbfWriter = append ? new DBF(tempFileDPF.getAbsolutePath(), charset) : new DBF(tempFileDPF.getAbsolutePath(), DBF.DBASEIV, true, charset);

                    if (!append)
                        dbfWriter.addField(new Field[]{CODE, NAME, SPRT1, VIDSPR1, SPRT2, VIDSPR2, IDTERM, MTERM, DISCOUNT, COEF, ROUND, FLAGS, IDSET});

                    Map<String, Integer> recordMap = new HashMap<>();
                    for (int i = 1; i <= dbfWriter.getRecordCount(); i++) {
                        dbfWriter.read();
                        String code = getDBFFieldValue(dbfWriter, "CODE", charset);
                        recordMap.put(code, i);
                    }
                    dbfWriter.startTop();

                    Set<String> usedCodes = new HashSet<>();
                    for (TerminalDocumentType tdt : transaction.terminalDocumentTypeList) {
                        if (!Thread.currentThread().isInterrupted()) {
                            if (!usedCodes.contains(tdt.id)) {
                                Integer recordNumber = null;
                                if (append) {
                                    recordNumber = recordMap.get(tdt.id);
                                    if (recordNumber != null)
                                        dbfWriter.gotoRecord(recordNumber);
                                }

                                putField(dbfWriter, CODE, tdt.id, 15, append);
                                putField(dbfWriter, NAME, tdt.name, 50, append);
                                String sprt1 = tdt.analytics1 == null ? "" : tdt.analytics1.equals("ПС") ? "Организация" : tdt.analytics1;
                                Integer vidspr1 = tdt.analytics1 == null ? 0 : tdt.analytics1.equals("ПС") ? 10 : parseInt(tdt.analytics1);
                                String sprt2 = tdt.analytics2 == null ? "" : tdt.analytics2.equals("ПС") ? "Организация" : tdt.analytics2;
                                Integer vidspr2 = tdt.analytics2 == null ? 0 : tdt.analytics2.equals("ПС") ? 10 : parseInt(tdt.analytics2);
                                putNumField(dbfWriter, VIDSPR1, vidspr1, append);
                                putField(dbfWriter, SPRT1, sprt1, 15, append);
                                putNumField(dbfWriter, VIDSPR2, vidspr2, append);
                                putField(dbfWriter, SPRT2, sprt2, 15, append);

                                if (recordNumber != null)
                                    dbfWriter.update();
                                else {
                                    dbfWriter.write();
                                    dbfWriter.file.setLength(dbfWriter.file.length() - 1);
                                    if (append)
                                        recordMap.put(tdt.id, recordMap.size() + 1);
                                }
                                usedCodes.add(tdt.id);
                            }
                        }
                    }
                } finally {
                    if(dbfWriter != null)
                        dbfWriter.close();
                }
                FileUtils.copyFile(tempFileDPF, fileDBF);
                safeDelete(tempFileDPF);
            }
        }
    }

    private Integer parseInt(String value) {
        try {
            return value == null ? null : Integer.parseInt(value);
        } catch (Exception e) {
            return null;
        }
    }

    private void createEmptyDocFile(String path) throws IOException, xBaseJException {
        File directory = new File(path);
        File fileDBF = new File(path + "/DOC.dbf");
        if (directory.exists() && !fileDBF.exists()) {
            NumField2 IDDOC = new NumField2("IDDOC", 8, 0);
            CharField CVIDDOC = new CharField("CVIDDOC", 15);
            CharField CSPR1 = new CharField("CSPR1", 15);
            CharField CSPR2 = new CharField("CSPR2", 15);
            NumField2 QUANDOC = new NumField2("QUANDOC", 13, 3);
            NumField2 SUMDOC = new NumField2("SUMDOC", 13, 2);
            CharField TITLE = new CharField("TITLE", 200);
            NumField2 IDTERM = new NumField2("IDTERM", 8, 0);
            NumField2 DISCOUNT = new NumField2("DISCOUNT", 13, 2);
            NumField2 FLAGS = new NumField2("FLAGS", 8, 0);
            CharField CRE_DTST = new CharField("CRE_DTST", 14);
            CharField MOD_DTST = new CharField("MOD_DTST", 14);
            NumField2 ACCEPTED = new NumField2("ACCEPTED", 1, 0);
            NumField2 IDSET = new NumField2("IDSET", 8, 0);

            DBF dbfWriter = null;
            try {
                dbfWriter = new DBF(fileDBF.getAbsolutePath(), DBF.DBASEIV, true, charset);
                dbfWriter.addField(new Field[]{IDDOC, CVIDDOC, CSPR1, CSPR2, QUANDOC, SUMDOC, TITLE, IDTERM, DISCOUNT, FLAGS, CRE_DTST, MOD_DTST, ACCEPTED, IDSET});
            } finally {
                if (dbfWriter != null)
                    dbfWriter.close();
            }
        }
    }

    private void createEmptyPosFile(String path) throws IOException, xBaseJException {
        File directory = new File(path);
        File fileDBF = new File(path + "/POS.dbf");
        if (directory.exists() && !fileDBF.exists()) {
            NumField2 IDDOC = new NumField2("IDDOC", 8, 0);
            CharField ARTICUL = new CharField("ARTICUL", 15);
            NumField2 QUAN = new NumField2("QUAN", 13, 3);
            NumField2 CHR_QUAN = new NumField2("CHR_QUAN", 13, 3);
            NumField2 PRICE = new NumField2("PRICE", 13, 2);
            NumField2 PRICEREST = new NumField2("PRICEREST", 13, 2);
            NumField2 QUANREST = new NumField2("QUANREST", 13, 3);
            CharField NAME = new CharField("NAME", 200);
            CharField MOD_DTST = new CharField("MOD_DTST", 14);
            NumField2 NOMPOS = new NumField2("NOMPOS", 4, 0);

            DBF dbfWriter = null;
            try {
                dbfWriter = new DBF(fileDBF.getAbsolutePath(), DBF.DBASEIV, true, charset);
                dbfWriter.addField(new Field[]{IDDOC, ARTICUL, QUAN, CHR_QUAN, PRICE, PRICEREST, QUANREST, NAME, MOD_DTST, NOMPOS});
            } finally {
                if (dbfWriter != null)
                    dbfWriter.close();
            }
        }
    }

    private boolean createBasesUpdFile(String path) throws IOException {
        return new File(path + "/BASES.UPD").createNewFile();
    }

    protected BigDecimal getDBFBigDecimalFieldValue(DBF importFile, String fieldName, String charset) throws UnsupportedEncodingException {
        String result = getDBFFieldValue(importFile, fieldName, charset);
        return (result == null || result.isEmpty() ? null : new BigDecimal(result.replace(",", ".")));
    }

    protected boolean listNotEmpty(List list) {
        return list != null && !list.isEmpty();
    }

    protected String trim(String input, Integer length) {
        return input == null ? null : (length == null || length >= input.trim().length() ? input.trim() : input.trim().substring(0, length));
    }

    public static void safeDelete(File file) {
        if (file != null && !file.delete()) {
            file.deleteOnExit();
        }
    }
}