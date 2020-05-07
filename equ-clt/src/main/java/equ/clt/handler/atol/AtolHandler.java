package equ.clt.handler.atol;

import com.google.common.base.Throwables;
import equ.api.ItemGroup;
import equ.api.RequestExchange;
import equ.api.SalesInfo;
import equ.api.SendTransactionBatch;
import equ.api.cashregister.*;
import equ.clt.handler.DefaultCashRegisterHandler;
import equ.clt.handler.HandlerUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.util.FileCopyUtils;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static equ.clt.EquipmentServer.*;

public class AtolHandler extends DefaultCashRegisterHandler<AtolSalesBatch> {

    public AtolHandler() {
    }

    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        return "atol";
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionInfoList) {

        Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        for(TransactionCashRegisterInfo transaction : transactionInfoList) {

            Exception exception = null;
            try {

                processTransactionLogger.info("Atol: Send Transaction # " + transaction.id);

                List<String> directoriesList = new ArrayList<>();
                for (CashRegisterInfo cashRegisterInfo : transaction.machineryInfoList) {
                    if ((cashRegisterInfo.directory != null) && (!directoriesList.contains(cashRegisterInfo.directory)))
                        directoriesList.add(cashRegisterInfo.directory);
                }

                for (String directory : directoriesList) {

                    String exchangeDirectory = directory + "/IN/";

                    File goodsFlagFile = createGoodsFlagFile(exchangeDirectory);

                    File goodsFile = new File(exchangeDirectory + "goods.txt");
                    PrintWriter goodsWriter = new PrintWriter(goodsFile, "cp1251");

                    goodsWriter.println("##@@&&");
                    goodsWriter.println("#");

                    goodsWriter.println("$$$ADDENTERPRISES");
                    goodsWriter.println(format(transaction.nppGroupMachinery, ";") + ";" + format(transaction.nppGroupMachinery, ";"));

                    if (!transaction.itemsList.isEmpty()) {
                        goodsWriter.println("$$$ADDQUANTITY");

                        LinkedHashMap<String, String[]> itemGroups = new LinkedHashMap<>();
                        for (CashRegisterItemInfo item : transaction.itemsList) {
                            if (!Thread.currentThread().isInterrupted()) {
                                List<ItemGroup> hierarchyItemGroup = transaction.itemGroupMap.get(item.idItemGroup);
                                if(hierarchyItemGroup != null) {
                                    for (int i = hierarchyItemGroup.size() - 1; i >= 0; i--) {
                                        String idItemGroup = hierarchyItemGroup.get(i).idItemGroup;
                                        if (!itemGroups.containsKey(idItemGroup)) {
                                            String nameItemGroup = hierarchyItemGroup.get(i).nameItemGroup;
                                            String parentItemGroup = hierarchyItemGroup.size() <= (i + 1) ? null : hierarchyItemGroup.get(i + 1).idItemGroup;
                                            itemGroups.put(idItemGroup, new String[]{nameItemGroup, parentItemGroup, item.splitItem ? "1" : "0"});
                                        }
                                    }
                                }
                            }
                        }

                        for (Map.Entry<String, String[]> itemGroupEntry : itemGroups.entrySet()) {
                            if (!Thread.currentThread().isInterrupted()) {
                                String itemGroupRecord = format(itemGroupEntry.getKey(), ";") + ";" + format(itemGroupEntry.getValue()[0], 100, ";") + //3
                                        format(itemGroupEntry.getValue()[0], 100, ";") + ";;;" + formatFlags(itemGroupEntry.getValue()[2], ";") + //8
                                        ";;;;;;;" + format(itemGroupEntry.getValue()[1], ";") + "0;" + ";;;;;;;;;;;;;;;;;;;;;;;;;" +
                                        (transaction.nppGroupMachinery == null ? "1" : transaction.nppGroupMachinery) + ";";
                                goodsWriter.println(itemGroupRecord);
                            }
                        }

                        for (CashRegisterItemInfo item : transaction.itemsList) {
                            if (!Thread.currentThread().isInterrupted()) {
                                String idItemGroup = item.idItemGroup == null ? "" : item.idItemGroup;
                                String record = format(item.idItem, ";") + format(item.idBarcode, ";") + format(item.name, 100, ";") + //3
                                        format(item.name, 100, ";") + format(item.price, ";") + ";;" + formatFlags(item.splitItem ? "1" : "0", ";") + //8
                                        ";;;;;;;" + format(idItemGroup, ";") + "1;" + ";;;;;;;;;;;;;;;;;;;;;;;;;" +
                                        (transaction.nppGroupMachinery == null ? "1" : transaction.nppGroupMachinery) + ";";
                                goodsWriter.println(record);
                            }
                        }
                    }
                    goodsWriter.close();

                    processGoodsFlagFile(goodsFile, goodsFlagFile);
                }
            } catch (Exception e) {
                exception = e;
            }
            sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(exception));
        }
        return sendTransactionBatchMap;
    }

    private boolean checkGoodsFile(String path) throws FileNotFoundException {
        Scanner scanner = new Scanner(new File(path), "cp1251");
        boolean result = scanner.hasNextLine() && scanner.nextLine().equals("##@@&&") && scanner.hasNextLine() && scanner.nextLine().equals("@");
        scanner.close();
        return result;
    }

    private File createGoodsFlagFile(String exchangeDirectory) {
        File goodsFlagFile = new File(exchangeDirectory + "goods-flag.txt");
        if (goodsFlagFile.exists())
            throw new RuntimeException("Goods flag file already exists");
        return goodsFlagFile;
    }

    private boolean processGoodsFlagFile(File goodsFile, File goodsFlagFile) throws FileNotFoundException, UnsupportedEncodingException {
        processTransactionLogger.info("Atol: waiting for processing of goods file");
        PrintWriter flagWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(goodsFlagFile), "windows-1251"));
        flagWriter.close();
        while (!Thread.currentThread().isInterrupted() && (goodsFlagFile.exists() || !checkGoodsFile(goodsFile.getAbsolutePath()))) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
        }
        processTransactionLogger.info("Atol: deletion of goods file");
        return goodsFile.delete();
    }

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList,
                                 Set<Long> succeededRequests, Map<Long, Throwable> failedRequests, Map<Long, Throwable> ignoredRequests) throws IOException {

        for (RequestExchange entry : requestExchangeList) {
            int count = 0;
            String requestResult = null;
            String dateFrom = entry.dateFrom.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            String dateTo = entry.dateTo.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

            machineryExchangeLogger.info("Atol: creating request files");
            for (String directory : getDirectorySet(entry)) {
                String exchangeDirectory = directory + "/IN";
                if (new File(exchangeDirectory).exists() || new File(exchangeDirectory).mkdirs()) {
                    File salesFlagFile = new File(exchangeDirectory + "/sales-flag.txt");
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(salesFlagFile), StandardCharsets.UTF_8));
                    writer.write("$$$TRANSACTIONSBYDATERANGE");
                    writer.newLine();
                    writer.write(dateFrom + ";" + dateTo);
                    writer.close();
                } else
                    requestResult = "Error: " + exchangeDirectory + " doesn't exist. Request creation failed.";
                count++;
            }
            if (count > 0) {
                if (requestResult == null)
                    succeededRequests.add(entry.requestExchange);
                else
                    failedRequests.put(entry.requestExchange, new RuntimeException(requestResult));
            }
        }
    }

    @Override
    public void finishReadingSalesInfo(AtolSalesBatch salesBatch) {
        sendSalesLogger.info("Atol: Finish Reading started");
        for (Map.Entry<String, Boolean> readFile : salesBatch.readFiles.entrySet()) {

            try {
                File inputFile = new File(readFile.getKey());

                if (readFile.getValue()) {
                    if (inputFile.delete()) {
                        sendSalesLogger.info("Atol: file " + readFile.getKey() + " has been deleted");
                    } else {
                        throw new RuntimeException("The file " + inputFile.getAbsolutePath() + " can not be deleted");
                    }
                } else {
                    BufferedReader br = new BufferedReader(new FileReader(inputFile));

                    File outputFile = File.createTempFile("output", ".txt");
                    BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));

                    String line;
                    while ((line = br.readLine()) != null) {
                        bw.write(line.equals("#") ? "@" : line);
                        bw.newLine();
                    }
                    bw.flush();
                    bw.close();
                    br.close();

                    FileCopyUtils.copy(outputFile, inputFile);
                    if(!outputFile.delete())
                        outputFile.deleteOnExit();
                }
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public Map<String, LocalDateTime> requestSucceededSoftCheckInfo(List<String> directoryList) {

        softCheckLogger.info("Atol: requesting succeeded SoftCheckInfo");

        Map<String, LocalDateTime> result = new HashMap<>();
        for (String directory : directoryList) {

            try {

                String exchangeDirectory = directory + "/OUT/";

                File[] filesList = new File(exchangeDirectory).listFiles(this::acceptSalesFile);
                if (filesList != null) {
                    for (File file : filesList) {

                        boolean isCurrent = file.getName().contains("current");

                        if (file.getName().contains("_current"))
                            break;
                        Scanner scanner = new Scanner(file, "cp1251");
                        if (!isCurrent && (!scanner.hasNextLine() || !scanner.nextLine().equals("#"))) {
                            break;
                        } else {
                            if (!isCurrent) {
                                scanner.nextLine(); //db
                                scanner.nextLine(); //reportNumber
                            }
                            while (scanner.hasNextLine()) {
                                String[] entry = scanner.nextLine().split(";");
                                String date = getStringValue(entry, 1);
                                String time = getStringValue(entry, 2);
                                Timestamp dateTime = new Timestamp(DateUtils.parseDate((date + " " + time), "dd.MM.yyyy HH:mm:ss").getTime());
                                String entryType = getStringValue(entry, 3);
                                boolean isSale = entryType != null && (entryType.equals("1") || entryType.equals("11"));
                                String documentType = getStringValue(entry, 22);
                                String numberSoftCheck = getStringValue(entry, 18);
                                if (isSale && documentType != null && documentType.equals("2000001"))
                                    result.put(numberSoftCheck, sqlTimestampToLocalDateTime(dateTime));
                            }
                            scanner.close();

                        }
                    }

                    if (result.size() == 0)
                        softCheckLogger.info("Atol: no soft checks found");
                    else
                        softCheckLogger.info(String.format("Atol: found %s soft check(s)", result.size()));
                }
            } catch (FileNotFoundException | ParseException e) {
                throw Throwables.propagate(e);
            }
        }
        return result;
    }

    @Override
    public CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList, Set<String> cashDocumentSet) {

        try {

            Map<String, CashRegisterInfo> directoryCashRegisterMap = new HashMap<>();
            Set<String> directorySet = new HashSet<>();
            for (CashRegisterInfo c : cashRegisterInfoList) {
                if (fitHandler(c) && c.directory != null) {
                    directorySet.add(c.directory);
                    if (c.number != null && c.numberGroup != null) {
                        directoryCashRegisterMap.put(c.directory + "_" + c.number, c);
                    }
                }
            }

            Set<String> cancelCashDocumentSet = new HashSet<>();
            List<CashDocument> result = new ArrayList<>();
            for (String directory : directorySet) {

                String exchangeDirectory = directory + "/OUT/";

                File[] filesList = new File(exchangeDirectory).listFiles(this::acceptSalesFile);
                if (filesList != null) {
                    for (File file : filesList) {

                        boolean isCurrent = file.getName().contains("current");
                        List<CashDocument> currentResult = new ArrayList<>();

                        if (file.getName().contains("_current"))
                            break;
                        Scanner scanner = new Scanner(file, "cp1251");
                        if (!isCurrent && (!scanner.hasNextLine() || !scanner.nextLine().equals("#"))) {
                            break;
                        } else {
                            if (!isCurrent) {
                                scanner.nextLine(); //db
                                scanner.nextLine(); //reportNumber
                            }
                            while (scanner.hasNextLine()) {
                                String[] entry = scanner.nextLine().split(";");

                                String entryType = getStringValue(entry, 3);
                                boolean isInputCashDocument = entryType != null && entryType.equals("50");
                                boolean isOutputCashDocument = entryType != null && entryType.equals("51");
                                boolean isCancelDocument = entryType != null && entryType.equals("56");

                                String numberCashDocument = getStringValue(entry, 0);

                                if (isCancelDocument) {
                                    cancelCashDocumentSet.add(numberCashDocument);
                                } else if (isInputCashDocument || isOutputCashDocument) {
                                    Date dateReceipt = getDateValue(entry, 1);
                                    Time timeReceipt = getTimeValue(entry, 2);
                                    Integer numberCashRegister = getIntValue(entry, 4);
                                    CashRegisterInfo cashRegister = directoryCashRegisterMap.get(directory + "_" + numberCashRegister);
                                    Integer nppGroupMachinery = cashRegister == null ? null : cashRegister.numberGroup;
                                    BigDecimal sumCashDocument = isOutputCashDocument ? HandlerUtils.safeNegate(getBigDecimalValue(entry, 11)) : getBigDecimalValue(entry, 11);
                                    currentResult.add(new CashDocument(numberCashDocument, numberCashDocument, sqlDateToLocalDate(dateReceipt), sqlTimeToLocalTime(timeReceipt),
                                            nppGroupMachinery, numberCashRegister, null, sumCashDocument));

                                }
                            }
                            scanner.close();

                        }

                        for (CashDocument salesInfo : currentResult) {
                            if (!cancelCashDocumentSet.contains(salesInfo.numberCashDocument))
                                result.add(salesInfo);
                        }

                    }
                }
            }

            if (result.size() == 0)
                sendSalesLogger.info("Atol: no CashDocuments found");
            else
                sendSalesLogger.info(String.format("Atol: found %s CashDocument(s)", result.size()));
            return new CashDocumentBatch(result, null);
        } catch (FileNotFoundException | ParseException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public AtolSalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) throws IOException, ParseException {

        List<String> unusedEntryTypes = Arrays.asList("4", "14", "21", "23", "42", "43", "45", "49", "50", "51", "55", "60", "61", "63");

        Map<String, CashRegisterInfo> directoryCashRegisterMap = new HashMap<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (c.directory != null && c.number != null)
                directoryCashRegisterMap.put(c.directory + "_" + c.number, c);
        }

        Set<Integer> cancelReceiptSet = new HashSet<>();
        List<SalesInfo> salesInfoList = new ArrayList<>();
        Map<String, Boolean> filePathList = new HashMap<>();

        String exchangeDirectory = directory + "/OUT/";

        File[] filesList = new File(exchangeDirectory).listFiles(this::acceptSalesFile);

        if (filesList != null) {
            for (File file : filesList) {

                boolean isCurrent = file.getName().contains("current");
                List<SalesInfo> currentSalesInfoList = new ArrayList<>();

                if (file.getName().contains("_current")) {
                    filePathList.put(file.getAbsolutePath(), true);
                    break;
                }
                Scanner scanner = new Scanner(file, "cp1251");
                if (!isCurrent && (!scanner.hasNextLine() || !scanner.nextLine().equals("#"))) {
                    break;
                } else {
                    if (!isCurrent) {
                        scanner.nextLine(); //db
                        scanner.nextLine(); //reportNumber
                    }
                    while (scanner.hasNextLine()) {
                        String[] entry = scanner.nextLine().split(";");

                        String entryType = getStringValue(entry, 3);
                        boolean isSale = entryType != null && (entryType.equals("1") || entryType.equals("11"));
                        boolean isReturn = entryType != null && (entryType.equals("2") || entryType.equals("12"));
                        boolean isPayment = entryType != null && (entryType.equals("40") || entryType.equals("41"));
                        boolean isCancelDocument = entryType != null && entryType.equals("56");

                        Integer numberReceiptDetail = getIntValue(entry, 0);

                        Integer numberReceipt = getIntValue(entry, 5);
                        String documentType = getStringValue(entry, 22);

                        if (isPayment) {
                            for (SalesInfo salesInfo : currentSalesInfoList) {
                                if (salesInfo.numberReceipt != null && salesInfo.numberReceipt.equals(numberReceipt)) {
                                    Integer paymentType = getIntValue(entry, 8);
                                    BigDecimal sum = getBigDecimalValue(entry, 9);
                                    if (paymentType != null) {
                                        switch (paymentType) {
                                            case 2:
                                                salesInfo.sumCard = HandlerUtils.safeAdd(salesInfo.sumCard, sum);
                                                break;
                                            case 1:
                                            default:
                                                salesInfo.sumCash = HandlerUtils.safeAdd(salesInfo.sumCash, sum);
                                                break;
                                        }
                                    }
                                }
                            }
                        } else if (isSale && documentType != null && documentType.equals("2000001")) {
                            //nothing to do: it's soft check
                        } else if (isSale || isReturn) {
                            LocalDate dateReceipt = sqlDateToLocalDate(getDateValue(entry, 1));
                            LocalTime timeReceipt = sqlTimeToLocalTime(getTimeValue(entry, 2));
                            Integer numberCashRegister = getIntValue(entry, 4);

                            CashRegisterInfo cashRegister = directoryCashRegisterMap.get(directory + "_" + numberCashRegister);
                            LocalDate startDate = cashRegister == null ? null : cashRegister.startDate;
                            Integer nppGroupMachinery = cashRegister == null ? null : cashRegister.numberGroup;

                            Long itemObject = getLongValue(entry, 7);
                            BigDecimal priceReceiptDetail = getBigDecimalValue(entry, 9);
                            BigDecimal quantityReceiptDetail = getBigDecimalValue(entry, 10);
                            BigDecimal sumReceiptDetail = getBigDecimalValue(entry, 11);
                            String numberZReport = getStringValue(entry, 13);
                            BigDecimal discountedSumReceiptDetail = getBigDecimalValue(entry, 14);
                            BigDecimal discountSumReceiptDetail = HandlerUtils.safeSubtract(sumReceiptDetail, sumReceiptDetail != null && sumReceiptDetail.compareTo(BigDecimal.ZERO) < 0 ?
                                    HandlerUtils.safeNegate(discountedSumReceiptDetail) : discountedSumReceiptDetail);
                            String barcodeItem = getStringValue(entry, 18);

                            if (dateReceipt == null || startDate == null || dateReceipt.compareTo(startDate) >= 0)
                                currentSalesInfoList.add(getSalesInfo(nppGroupMachinery, numberCashRegister, numberZReport,
                                        dateReceipt, timeReceipt, numberReceipt, dateReceipt, timeReceipt, null, null, null,
                                        null, null, barcodeItem, null, itemObject, null, quantityReceiptDetail, priceReceiptDetail,
                                        sumReceiptDetail, discountSumReceiptDetail, null, null, numberReceiptDetail, file.getName(),
                                        null, null, cashRegister));
                        } else if (isCancelDocument) {
                            cancelReceiptSet.add(numberReceipt);
                        } else {
                            assert unusedEntryTypes.contains(entryType);
                        }
                    }
                    scanner.close();
                }

                for (SalesInfo salesInfo : currentSalesInfoList) {
                    if (!cancelReceiptSet.contains(salesInfo.numberReceipt) && (notNull(salesInfo.sumCash) || notNull(salesInfo.sumCard)))
                        salesInfoList.add(salesInfo);
                }
                filePathList.put(file.getAbsolutePath(), isCurrent);
            }
        }
        return (salesInfoList.isEmpty() && filePathList.isEmpty()) ? null :
                new AtolSalesBatch(salesInfoList, filePathList);
    }

    private boolean notNull(BigDecimal value) {
        return value != null && value.doubleValue() != 0;
    }

    private boolean acceptSalesFile(File pathname) {
        return (pathname.getName().startsWith("sales") && pathname.getPath().endsWith(".txt")) ||
                (pathname.getName().startsWith("current") && pathname.getPath().endsWith(".txt") && new File(pathname.getParentFile().getPath() + "/_" + pathname.getName()).exists()) ||
                (pathname.getName().startsWith("_current") && pathname.getPath().endsWith(".txt"));
    }

    private String formatFlags(String splitItem, String postfix) {
        return splitItem + ",,,,,1" + (postfix == null ? "" : postfix);
    }

    private String format(Object input, String postfix) {
        return format(input, null, postfix);
    }

    private String format(Object input, Integer length, String postfix) {
        String result = "";
        if (input != null) {
            if (input instanceof BigDecimal)
                result = String.valueOf(input);
            else if (input instanceof Boolean)
                result = ((Boolean) input) ? "1" : "0";
            else {
                String str = String.valueOf(input).trim();
                result = length == null || length >= str.length() ? str : str.substring(0, length);
            }
        }
        return result.replace(";", "¤") + (postfix == null ? "" : postfix);
    }

    private String getStringValue(String[] entry, int index) {
        return entry.length >= (index + 1) ? entry[index] : null;
    }

    private Integer getIntValue(String[] entry, int index) {
        return entry.length >= (index + 1) ? Integer.parseInt(entry[index]) : null;
    }

    private Long getLongValue(String[] entry, int index) {
        return entry.length >= (index + 1) ? Long.parseLong(entry[index]) : null;
    }

    private BigDecimal getBigDecimalValue(String[] entry, int index) {
        return entry.length >= (index + 1) ? new BigDecimal(entry[index]) : null;
    }

    private Date getDateValue(String[] entry, int index) throws ParseException {
        return entry.length >= (index + 1) ? new Date(DateUtils.parseDate(entry[index], "dd.MM.yyyy").getTime()) : null;
    }

    private Time getTimeValue(String[] entry, int index) throws ParseException {
        return entry.length >= (index + 1) ? new Time(DateUtils.parseDate(entry[index], "HH:mm:ss").getTime()) : null;
    }
}
