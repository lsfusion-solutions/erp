package equ.clt.handler.atol;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
import org.apache.commons.lang.time.DateUtils;
import org.apache.log4j.Logger;
import org.springframework.util.FileCopyUtils;

import java.io.*;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class AtolHandler extends CashRegisterHandler<AtolSalesBatch> {

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected final static Logger sendSalesLogger = Logger.getLogger("SendSalesLogger");
    protected final static Logger sendSoftCheckLogger = Logger.getLogger("SoftCheckLogger");

    public AtolHandler() {
    }

    @Override
    public List<MachineryInfo> sendTransaction(TransactionCashRegisterInfo transactionInfo, List<CashRegisterInfo> machineryInfoList) throws IOException {

        processTransactionLogger.info("Atol: Send Transaction # " + transactionInfo.id);

        List<String> directoriesList = new ArrayList<String>();
        for (CashRegisterInfo cashRegisterInfo : machineryInfoList) {
            if ((cashRegisterInfo.port != null) && (!directoriesList.contains(cashRegisterInfo.port.trim())))
                directoriesList.add(cashRegisterInfo.port.trim());
            if ((cashRegisterInfo.directory != null) && (!directoriesList.contains(cashRegisterInfo.directory.trim())))
                directoriesList.add(cashRegisterInfo.directory.trim());
        }

        for (String directory : directoriesList) {

            String exchangeDirectory = directory + "/IN/";

            File goodsFlagFile = createGoodsFlagFile(exchangeDirectory);

            File goodsFile = new File(exchangeDirectory + "goods.txt");
            PrintWriter goodsWriter = new PrintWriter(goodsFile, "cp1251");

            goodsWriter.println("##@@&&");
            goodsWriter.println("#");

            goodsWriter.println("$$$ADDENTERPRISES");
            goodsWriter.println(format(transactionInfo.nppGroupCashRegister, ";") + ";" + format(transactionInfo.nameGroupCashRegister, ";"));

            if (!transactionInfo.itemsList.isEmpty()) {
                goodsWriter.println("$$$ADDQUANTITY");

                LinkedHashMap<String, String[]> itemGroups = new LinkedHashMap<String, String[]>();
                for (CashRegisterItemInfo item : transactionInfo.itemsList) {

                    List<ItemGroup> hierarchyItemGroup = transactionInfo.itemGroupMap.get(item.idItemGroup);
                    for (int i = hierarchyItemGroup.size() - 1; i >= 0; i--) {
                        String idItemGroup = hierarchyItemGroup.get(i).idItemGroup;
                        if (!itemGroups.containsKey(idItemGroup)) {
                            String nameItemGroup = hierarchyItemGroup.get(i).nameItemGroup;
                            String parentItemGroup = hierarchyItemGroup.size() <= (i + 1) ? null : hierarchyItemGroup.get(i + 1).idItemGroup;
                            itemGroups.put(idItemGroup, new String[]{nameItemGroup, parentItemGroup, item.splitItem ? "1" : "0"});
                        }
                    }
                }

                for (Map.Entry<String, String[]> itemGroupEntry : itemGroups.entrySet()) {
                    String itemGroupRecord = format(itemGroupEntry.getKey(), ";") + ";" + format(itemGroupEntry.getValue()[0], 100, ";") + //3
                            format(itemGroupEntry.getValue()[0], 100, ";") + ";;;" + formatFlags(itemGroupEntry.getValue()[2], ";") + //8
                            ";;;;;;;" + format(itemGroupEntry.getValue()[1], ";") + "0;" + ";;;;;;;;;;;;;;;;;;;;;;;;;" +
                            (transactionInfo.nppGroupCashRegister == null ? "1" : transactionInfo.nppGroupCashRegister) + ";";
                    goodsWriter.println(itemGroupRecord);
                }

                for (CashRegisterItemInfo item : transactionInfo.itemsList) {
                    String idItemGroup = item.idItemGroup == null ? "" : item.idItemGroup;
                    String record = format(item.idItem, ";") + format(item.idBarcode, ";") + format(item.name, 100, ";") + //3
                            format(item.name, 100, ";") + format(item.price, ";") + ";;" + formatFlags(item.splitItem ? "1" : "0", ";") + //8
                            ";;;;;;;" + format(idItemGroup, ";") + "1;" + ";;;;;;;;;;;;;;;;;;;;;;;;;" +
                            (transactionInfo.nppGroupCashRegister == null ? "1" : transactionInfo.nppGroupCashRegister) + ";";
                    goodsWriter.println(record);
                }
            }
            goodsWriter.close();

            processGoodsFlagFile(goodsFile, goodsFlagFile);
        }
        return null;
    }

    private boolean checkGoodsFile(String path) throws FileNotFoundException {
        Scanner scanner = new Scanner(new File(path), "cp1251");
        boolean result = scanner.hasNextLine() && scanner.nextLine().equals("##@@&&") && scanner.hasNextLine() && scanner.nextLine().equals("@");
        scanner.close();
        return result;
    }

    @Override
    public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {

        sendSoftCheckLogger.info("Atol: Sending soft checks");

        for (String directory : softCheckInfo.directorySet) {

            String exchangeDirectory = directory + "/IN/";

            File goodsFlagFile = createGoodsFlagFile(exchangeDirectory);
            File goodsFile = new File(exchangeDirectory + "goods.txt");
            PrintWriter goodsWriter = new PrintWriter(goodsFile, "cp1251");

            goodsWriter.println("##@@&&");
            goodsWriter.println("#");

            for (Map.Entry<String, SoftCheckInvoice> invoiceEntry : softCheckInfo.invoiceMap.entrySet()) {

                String record = format("99999", ";") + format(invoiceEntry.getKey(), ";") + format("ПРИХОД", ";") + //3
                        ";;" + ";" + ";" + formatFlags("0", ";") + //8
                        ";;;;;;;;" + "1;" + ";;;;;;;;;;;;;;;;;;;;;;;;;" +
                        (invoiceEntry.getValue().idCustomerStock == null ? "1" : invoiceEntry.getValue().idCustomerStock) + ";";
                goodsWriter.println(record);

            }
            goodsWriter.close();
            processGoodsFlagFile(goodsFile, goodsFlagFile);
        }
    }

    private File createGoodsFlagFile(String exchangeDirectory) {
        File goodsFlagFile = new File(exchangeDirectory + "goods-flag.txt");
        if (goodsFlagFile.exists())
            throw Throwables.propagate(new RuntimeException("Goods flag file already exists"));
        return goodsFlagFile;
    }

    private boolean processGoodsFlagFile(File goodsFile, File goodsFlagFile) throws FileNotFoundException, UnsupportedEncodingException {
        sendSoftCheckLogger.info("Atol: waiting for processing of goods file");
        PrintWriter flagWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(goodsFlagFile), "windows-1251"));
        flagWriter.close();
        while (goodsFlagFile.exists() || !checkGoodsFile(goodsFile.getAbsolutePath())) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
        }
        sendSoftCheckLogger.info("Atol: deletion of goods file");
        return goodsFile.delete();
    }

    @Override
    public String requestSalesInfo(List<RequestExchange> requestExchangeList) throws IOException, ParseException {

        for (RequestExchange entry : requestExchangeList) {
            if (entry.isSalesInfoExchange()) {
                String dateFrom = new SimpleDateFormat("dd.MM.yyyy").format(entry.dateFrom);
                String dateTo = new SimpleDateFormat("dd.MM.yyyy").format(entry.dateTo);

                sendSalesLogger.info("Atol: creating request files");
                for (String directory : entry.directorySet) {
                    String exchangeDirectory = directory + "/IN";
                    if (new File(exchangeDirectory).exists() || new File(exchangeDirectory).mkdirs()) {
                        File salesFlagFile = new File(exchangeDirectory + "/sales-flag.txt");
                        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(salesFlagFile), "utf-8"));
                        writer.write("$$$TRANSACTIONSBYDATERANGE");
                        writer.newLine();
                        writer.write(dateFrom + ";" + dateTo);
                        writer.close();
                    } else
                        return "Error: " + exchangeDirectory + " doesn't exist. Request creation failed.";
                }
            }
        }
        return null;
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

                    File outputFile = File.createTempFile("output", "txt");
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
                    outputFile.delete();
                }
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public Map<String, Timestamp> requestSucceededSoftCheckInfo(Set<String> directorySet) throws ClassNotFoundException, SQLException {

        sendSalesLogger.info("Atol: requesting succeeded SoftCheckInfo");

        Map<String, Timestamp> result = new HashMap<String, Timestamp>();
        for (String directory : directorySet) {

            try {

                String exchangeDirectory = directory + "/OUT/";

                File[] filesList = new File(exchangeDirectory).listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return acceptSalesFile(pathname);
                    }
                });
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
                                Timestamp dateTime = new Timestamp(DateUtils.parseDate((date + " " + time), new String[] {"dd.MM.yyyy HH:mm:ss"}).getTime());
                                String entryType = getStringValue(entry, 3);
                                boolean isSale = entryType != null && (entryType.equals("1") || entryType.equals("11"));
                                String documentType = getStringValue(entry, 22);
                                String numberSoftCheck = getStringValue(entry, 18);
                                if (isSale && documentType.equals("2000001"))
                                    result.put(numberSoftCheck, dateTime);
                            }
                            scanner.close();

                        }
                    }

                    if (result.size() == 0)
                        sendSalesLogger.info("Atol: no soft checks found");
                    else
                        sendSalesLogger.info(String.format("Atol: found %s soft check(s)", result.size()));
                }
            } catch (FileNotFoundException e) {
                throw Throwables.propagate(e);
            } catch (ParseException e) {
                throw Throwables.propagate(e);
            }
        }
        return result;
    }

    @Override
    public List<List<Object>> checkZReportSum(Map<String, BigDecimal> zReportSumMap, List<Integer> nppCashRegisterList) throws ClassNotFoundException, SQLException {
        return null;
    }

    @Override
    public ExtraCheckZReportBatch extraCheckZReportSum(List<CashRegisterInfo> cashRegisterInfoList, Map<String, BigDecimal> zReportSumMap) throws ClassNotFoundException, SQLException {
        return null;
    }

    @Override
    public CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList, Set<String> cashDocumentSet) throws ClassNotFoundException {

        try {

            Set<String> directorySet = new HashSet<String>();
            for (CashRegisterInfo c : cashRegisterInfoList) {
                if (c.directory != null && c.handlerModel.endsWith("AtolHandler"))
                    directorySet.add(c.directory);
            }

            Set<String> cancelCashDocumentSet = new HashSet<String>();
            List<CashDocument> result = new ArrayList<CashDocument>();
            for (String directory : directorySet) {

                String exchangeDirectory = directory + "/OUT/";

                File[] filesList = new File(exchangeDirectory).listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return acceptSalesFile(pathname);
                    }
                });
                if (filesList != null) {
                    for (File file : filesList) {

                        boolean isCurrent = file.getName().contains("current");
                        List<CashDocument> currentResult = new ArrayList<CashDocument>();

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
                                    BigDecimal sumCashDocument = isOutputCashDocument ? safeNegate(getBigDecimalValue(entry, 11)) : getBigDecimalValue(entry, 11);
                                    currentResult.add(new CashDocument(numberCashDocument, dateReceipt, timeReceipt, numberCashRegister, sumCashDocument));

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
        } catch (FileNotFoundException e) {
            throw Throwables.propagate(e);
        } catch (ParseException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void finishReadingCashDocumentInfo(CashDocumentBatch cashDocumentBatch) {
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<String> directorySet) throws IOException {       
    }

    @Override
    public void sendDiscountCardList(List<DiscountCard> discountCardList, Set<String> directory) throws IOException {
    }

    @Override
    public void sendPromotionInfo(PromotionInfo promotionInfo, Set<String> directory) throws IOException {       
    }

    @Override
    public SalesBatch readSalesInfo(List<CashRegisterInfo> cashRegisterInfoList) throws IOException, ParseException, ClassNotFoundException {

        List<String> unusedEntryTypes = Arrays.asList("4", "14", "21", "23", "42", "43", "45", "49", "50", "51", "55", "60", "61", "63");

        Set<String> directorySet = new HashSet<String>();
        Map<String, Integer> directoryGroupCashRegisterMap = new HashMap<String, Integer>();
        Map<String, Date> directoryStartDateMap = new HashMap<String, Date>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (c.directory != null && c.handlerModel.endsWith("AtolHandler"))
                directorySet.add(c.directory);
            if (c.directory != null && c.number != null && c.numberGroup != null)
                directoryGroupCashRegisterMap.put(c.directory + "_" + c.number, c.numberGroup);
            if (c.directory != null && c.number != null && c.startDate != null)
                directoryStartDateMap.put(c.directory + "_" + c.number, c.startDate);
        }

        Set<Integer> cancelReceiptSet = new HashSet<Integer>();
        List<SalesInfo> salesInfoList = new ArrayList<SalesInfo>();
        Map<String, Boolean> filePathList = new HashMap<String, Boolean>();
        for (String directory : directorySet) {

            String exchangeDirectory = directory + "/OUT/";

            File[] filesList = new File(exchangeDirectory).listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return acceptSalesFile(pathname);
                }
            });

            if (filesList != null) {
                for (File file : filesList) {

                    boolean isCurrent = file.getName().contains("current");
                    List<SalesInfo> currentSalesInfoList = new ArrayList<SalesInfo>();

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
                                    if (salesInfo.numberReceipt != null && numberReceipt != null && salesInfo.numberReceipt.equals(numberReceipt)) {
                                        Integer paymentType = getIntValue(entry, 8);
                                        BigDecimal sum = getBigDecimalValue(entry, 9);
                                        if (paymentType != null) {
                                            switch (paymentType) {
                                                case 2:
                                                    salesInfo.sumCard = safeAdd(salesInfo.sumCard, sum);
                                                    break;
                                                case 1:
                                                default:
                                                    salesInfo.sumCash = safeAdd(salesInfo.sumCash, sum);
                                                    break;
                                            }
                                        }
                                    }
                                }
                            } else if (isSale && documentType.equals("2000001")) {
                                //nothing to do: it's soft check
                            } else if (isSale || isReturn) {
                                Date dateReceipt = getDateValue(entry, 1);
                                Time timeReceipt = getTimeValue(entry, 2);
                                Integer numberCashRegister = getIntValue(entry, 4);
                                Integer itemObject = getIntValue(entry, 7);
                                BigDecimal priceReceiptDetail = getBigDecimalValue(entry, 9);
                                BigDecimal quantityReceiptDetail = getBigDecimalValue(entry, 10);
                                BigDecimal sumReceiptDetail = getBigDecimalValue(entry, 11);
                                String numberZReport = getStringValue(entry, 13);
                                BigDecimal discountedSumReceiptDetail = getBigDecimalValue(entry, 14);
                                BigDecimal discountSumReceiptDetail = safeSubtract(sumReceiptDetail, sumReceiptDetail.compareTo(BigDecimal.ZERO) < 0 ? safeNegate(discountedSumReceiptDetail) : discountedSumReceiptDetail);
                                String barcodeItem = getStringValue(entry, 18);

                                Date startDate = directoryStartDateMap.get(directory + "_" + numberCashRegister);
                                if (dateReceipt == null || startDate == null || dateReceipt.compareTo(startDate) >= 0)
                                    currentSalesInfoList.add(new SalesInfo(directoryGroupCashRegisterMap.get(directory + "_" + numberCashRegister),
                                            numberCashRegister, numberZReport, numberReceipt, dateReceipt, timeReceipt, null, null, null, null/*sumCard*/,
                                            null/*sumCash*/, null/*sumGiftCard*/, barcodeItem, itemObject, quantityReceiptDetail, priceReceiptDetail,
                                            sumReceiptDetail, discountSumReceiptDetail, null/*discountSumReceipt*/, null, numberReceiptDetail, file.getName()));
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

    private BigDecimal getBigDecimalValue(String[] entry, int index) {
        return entry.length >= (index + 1) ? new BigDecimal(entry[index]) : null;
    }

    private Date getDateValue(String[] entry, int index) throws ParseException {
        return entry.length >= (index + 1) ? new Date(DateUtils.parseDate(entry[index], new String[]{"dd.MM.yyyy"}).getTime()) : null;
    }

    private Time getTimeValue(String[] entry, int index) throws ParseException {
        return entry.length >= (index + 1) ? new Time(DateUtils.parseDate(entry[index], new String[]{"HH:mm:ss"}).getTime()) : null;
    }

    protected BigDecimal safeAdd(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else return (operand1 == null ? operand2 : (operand2 == null ? operand1 : operand1.add(operand2)));
    }

    protected BigDecimal safeSubtract(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else
            return (operand1 == null ? operand2.negate() : (operand2 == null ? operand1 : operand1.subtract((operand2))));
    }

    protected BigDecimal safeNegate(BigDecimal operand) {
        return operand == null ? null : operand.negate();
    }
}
