package equ.clt.handler.artix;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
import equ.clt.handler.DefaultCashRegisterHandler;
import equ.clt.handler.HandlerUtils;
import lsfusion.base.BaseUtils;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.util.FileCopyUtils;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Date;
import java.sql.Time;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static equ.clt.handler.HandlerUtils.*;

public class ArtixHandler extends DefaultCashRegisterHandler<ArtixSalesBatch> {

    protected final static Logger machineryExchangeLogger = Logger.getLogger("MachineryExchangeLogger");
    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected final static Logger processStopListLogger = Logger.getLogger("StopListLogger");
    protected final static Logger sendSalesLogger = Logger.getLogger("SendSalesLogger");

    private static String logPrefix = "Artix: ";

    String encoding = "utf-8";

    private FileSystemXmlApplicationContext springContext;

    public ArtixHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    public String getGroupId(TransactionCashRegisterInfo transactionInfo) {
        String groupId = null;
        for (CashRegisterInfo cashRegister : transactionInfo.machineryInfoList) {
            if (cashRegister.directory != null) {
                groupId = cashRegister.directory;
            }
        }
        return "artix" + groupId;
    }

    @Override
    public Map<Integer, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) throws IOException {

        ArtixSettings artixSettings = springContext.containsBean("artixSettings") ? (ArtixSettings) springContext.getBean("artixSettings") : null;
        boolean appendBarcode = artixSettings != null && artixSettings.isAppendBarcode();

        Map<Integer, SendTransactionBatch> result = new HashMap<>();
        Map<Integer, Exception> failedTransactionMap = new HashMap<>();
        Set<Integer> emptyTransactionSet = new HashSet<>();

        boolean failed = false;
        for (TransactionCashRegisterInfo transaction : transactionList) {
            if(failed) {
                String error = "One of previous transactions failed";
                processTransactionLogger.error(logPrefix + error);
                failedTransactionMap.put(transaction.id, new RuntimeException(error));
            } else {
                try {

                    processTransactionLogger.info(logPrefix + "Send Transaction # " + transaction.id);

                    List<String> directoriesList = new ArrayList<>();
                    for (CashRegisterInfo cashRegisterInfo : transaction.machineryInfoList) {
                        if ((cashRegisterInfo.directory != null) && (!directoriesList.contains(cashRegisterInfo.directory)))
                            directoriesList.add(cashRegisterInfo.directory);
                    }

                    if (directoriesList.isEmpty())
                        emptyTransactionSet.add(transaction.id);

                    for (String directory : directoriesList) {

                        if (!new File(directory).exists()) {
                            processTransactionLogger.info(logPrefix + "exchange directory not found, trying to create: " + directory);
                            if (!new File(directory).mkdir() && !new File(directory).mkdirs())
                                processTransactionLogger.info(logPrefix + "exchange directory not found, failed to create: " + directory);
                        }

                        processTransactionLogger.info(logPrefix + "creating pos file (Transaction " + transaction.id + ") - " + transaction.itemsList.size() + " items");

                        File tmpFile = File.createTempFile("pos", ".aif");

                        if (transaction.snapshot) {
                            //items
                            writeStringToFile(tmpFile, "{\"command\": \"clearInventory\"}\n---\n");

                            //item groups
                            writeStringToFile(tmpFile, "{\"command\": \"clearInventGroup\"}\n---\n");

                            //UOMs
                            writeStringToFile(tmpFile, "{\"command\": \"clearUnit\"}\n---\n");

                            //prices
                            writeStringToFile(tmpFile, "{\"command\": \"clearPrice\"}\n---\n");
                        }

                        //items
                        for (CashRegisterItemInfo item : transaction.itemsList) {
                            if (!Thread.currentThread().isInterrupted()) {
                                writeStringToFile(tmpFile, getAddInventItemJSON(transaction, item, appendBarcode) + "\n---\n");
                            }
                        }

                        //item groups
                        Set<String> usedItemGroups = new HashSet<>();
                        for (CashRegisterItemInfo item : transaction.itemsList) {
                            if (!Thread.currentThread().isInterrupted()) {
                                List<ItemGroup> hierarchyItemGroup = transaction.itemGroupMap.get(item.idItemGroup);
                                if (hierarchyItemGroup != null) {
                                    for (ItemGroup itemGroup : hierarchyItemGroup) {
                                        if (!usedItemGroups.contains(itemGroup.extIdItemGroup)) {
                                            String inventGroup = getAddInventGroupJSON(itemGroup);
                                            if (inventGroup != null)
                                                writeStringToFile(tmpFile, inventGroup + "\n---\n");
                                            usedItemGroups.add(itemGroup.extIdItemGroup);
                                        }
                                    }
                                }
                            }
                        }

                        //UOMs
                        Set<String> usedUOMs = new HashSet<>();
                        for (CashRegisterItemInfo item : transaction.itemsList) {
                            if (!Thread.currentThread().isInterrupted()) {
                                if (!usedUOMs.contains(item.idUOM)) {
                                    String unit = getAddUnitJSON(item);
                                    if (unit != null)
                                        writeStringToFile(tmpFile, unit + "\n---\n");
                                    usedUOMs.add(item.idUOM);
                                }
                            }
                        }

                        //prices
                        /*for (CashRegisterItemInfo item : transaction.itemsList) {
                            if (!Thread.currentThread().isInterrupted()) {
                                    command.append(getAddPriceJSON(item)).append("\n---\n");
                            }
                        }*/

                        //TODO: склады

                        String currentTime = new SimpleDateFormat("yyyyMMddHHmmss").format(System.currentTimeMillis());
                        File file = new File(directory + "/pos" + currentTime + ".aif");
                        FileCopyUtils.copy(tmpFile, file);
                        if (!tmpFile.delete())
                            tmpFile.deleteOnExit();

                        File flagFile = new File(directory + "/pos" + currentTime + ".flz");
                        if (!flagFile.createNewFile())
                            processTransactionLogger.info(String.format(logPrefix + "can't create flag file %s (Transaction %s)", flagFile.getAbsolutePath(), transaction.id));
                        processTransactionLogger.info(String.format(logPrefix + "created pos file (Transaction %s)", transaction.id));

                        waitForDeletion(file, flagFile);
                        result.put(transaction.id, new SendTransactionBatch(null));
                    }
                } catch (Exception e) {
                    processTransactionLogger.error(logPrefix, e);
                    failedTransactionMap.put(transaction.id, e);
                    failed = true;
                }
            }
        }

        for (Map.Entry<Integer, Exception> entry : failedTransactionMap.entrySet()) {
            result.put(entry.getKey(), new SendTransactionBatch(entry.getValue()));
        }
        for (Integer emptyTransaction : emptyTransactionSet) {
            result.put(emptyTransaction, new SendTransactionBatch(null));
        }
        return result;
    }

    private String getAddInventItemJSON(TransactionCashRegisterInfo transaction, CashRegisterItemInfo item, boolean appendBarcode) throws JSONException {
        JSONObject rootObject = new JSONObject();

        JSONObject inventObject = new JSONObject();
        rootObject.put("invent", inventObject);
        inventObject.put("inventcode", trim(item.idItem, 20)); //код товара
        inventObject.put("barcode", removeCheckDigitFromBarcode(item.idBarcode, appendBarcode)); //основной штрих-код
        inventObject.put("deptcode", 1); //код отдела
        inventObject.put("price", item.price); //цена
        inventObject.put("minprice", item.minPrice); //минимальная цена
        //inventObject.put("isInvent", true);
        inventObject.put("isInventItem", true); //признак это товар (1) или группа (0)
        inventObject.put("articul", item.idItem); //артикул
        inventObject.put("rtext", item.name); //текст для чека
        inventObject.put("name", item.name); //наименование товара
        inventObject.put("measurecode", 1); //код единицы измерения
        List<ItemGroup> itemGroupList = transaction.itemGroupMap.get(item.extIdItemGroup);
        if (itemGroupList != null) {
            inventObject.put("inventgroup", itemGroupList.get(0).extIdItemGroup); //код родительской группы товаров
        }
        rootObject.put("command", "addInventItem");
        return rootObject.toString();
    }

    private String getAddInventGroupJSON(ItemGroup itemGroup) throws JSONException {
        if (itemGroup.extIdItemGroup != null) {
            JSONObject rootObject = new JSONObject();

            JSONObject inventGroupObject = new JSONObject();
            rootObject.put("inventGroup", inventGroupObject);
            inventGroupObject.put("groupCode", itemGroup.extIdItemGroup); //идентификационный код группы товаров
            inventGroupObject.put("parentGroupCode", itemGroup.idParentItemGroup); //идентификационный код родительской группы товаров
            inventGroupObject.put("groupname", trim(itemGroup.nameItemGroup, 200)); //название группы товаров
            rootObject.put("command", "addInventGroup");
            return rootObject.toString();
        } else return null;
    }

    private String getAddUnitJSON(CashRegisterItemInfo item) throws JSONException {
        Integer idUOM = parseUOM(item.idUOM);
        if (idUOM != null) {
            JSONObject rootObject = new JSONObject();

            JSONObject inventGroupObject = new JSONObject();
            rootObject.put("unit", inventGroupObject);
            inventGroupObject.put("unitCode", idUOM); //код единицы измерения
            inventGroupObject.put("name", item.shortNameUOM); //наименование единицы измерения
            inventGroupObject.put("fractional", !item.splitItem); //дробная единица измерения: true весовой, false штучный
            rootObject.put("command", "addUnit");
            return rootObject.toString();
        } else return null;
    }

    protected Integer parseUOM(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            processTransactionLogger.info(logPrefix + "Incorrect integer UOM '" + value + "'");
            return null;
        }
    }

    /*private String getAddPriceJSON(CashRegisterItemInfo item) throws JSONException, ParseException {
        JSONObject rootObject = new JSONObject();

        JSONObject inventGroupObject = new JSONObject();
        rootObject.put("price", inventGroupObject);
        inventGroupObject.put("barcode", item.idBarcode); //штрих-код или код переоцениваемого товара
        long currentTime = System.currentTimeMillis();
        inventGroupObject.put("documentid", String.valueOf(currentTime)); //код документа переоценки
        inventGroupObject.put("effectivedate", formatDateTime(currentTime)); //дата и время начала переоценки
        inventGroupObject.put("doctype", 1); //тип документа переоценки: 1 - переоценка, 2 - распродажа
        inventGroupObject.put("price", item.price); //цена
        inventGroupObject.put("minprice", item.minPrice); //минимальная цена
        inventGroupObject.put("pricetype", 3); //тип ценовой схемы (из примера)
        inventGroupObject.put("effectivedateend", formatDateTime(getDate(2020, 1, 1).getTime())); //дата и время окончания переоценки
        rootObject.put("command", "addPrice");
        return rootObject.toString();
    }*/

    private String getAddMCashUserJSON(CashierInfo cashier) throws JSONException, ParseException {
        JSONObject rootObject = new JSONObject();

        JSONObject inventGroupObject = new JSONObject();
        rootObject.put("mcashuser", inventGroupObject);
        inventGroupObject.put("code", cashier.numberCashier); //код пользователя
        inventGroupObject.put("name", cashier.nameCashier); //полное имя пользователя
        inventGroupObject.put("login", cashier.nameCashier); //имя пользователя для входа в систему
        inventGroupObject.put("password", cashier.numberCashier); //пароль для входа в систему
        inventGroupObject.put("keyposition", 1); //номер положения клавиатурного ключа для подтверждения прав у пользователя
        inventGroupObject.put("rank", cashier.idPosition); //должность пользователя

        JSONArray roleUsersArray = new JSONArray();
        JSONObject roleUsersObject = new JSONObject();
        if(cashier.idPosition != null)
            roleUsersObject.put("rolecode", cashier.idPosition.substring(0, 1));
        roleUsersObject.put("rule", 1);
        roleUsersArray.put(roleUsersObject);
        inventGroupObject.put("roleusers", roleUsersArray);

        rootObject.put("command", "addMCashUser");
        return rootObject.toString();
    }

    private String getAddCardJSON(DiscountCard card, boolean active) throws JSONException, ParseException {
        JSONObject rootObject = new JSONObject();

        JSONObject cardObject = new JSONObject();
        rootObject.put("card", cardObject);
        cardObject.put("idcard", card.idDiscountCard); //идентификационный код карты
        cardObject.put("idcardgroup", card.typeDiscountCard); //идентификационный код группы карт
        cardObject.put("idclient", card.numberDiscountCard); //идентификационный код клиента
        cardObject.put("number", card.numberDiscountCard); //номер карты
        cardObject.put("validitydatebeg", formatDate(card.dateFromDiscountCard)); //начало периода валидности
        cardObject.put("validitydateend", formatDate(card.dateToDiscountCard)); //окончание периода валидности
        cardObject.put("cardSum", card.initialSumDiscountCard); //сумма на карте
        cardObject.put("blocked", !active); //состояние карты(заблокирована или нет)
        cardObject.put("discountpercent", card.percentDiscountCard); //процент скидки
        rootObject.put("command", "addCard");
        return rootObject.toString();
    }

    private String getAddCardGroupJSON(String id, String name) throws JSONException, ParseException {
        JSONObject rootObject = new JSONObject();

        JSONObject cardGroupObject = new JSONObject();
        rootObject.put("cardGroup", cardGroupObject);
        cardGroupObject.put("idcardgroup", id); //идентификационный код группы карт
        cardGroupObject.put("name", name != null ? name : id); //имя группы карт
        cardGroupObject.put("text", id); //текст
        cardGroupObject.put("notaddemptycard", true);
        cardGroupObject.put("pattern", "[0-9]");
        cardGroupObject.put("inputmask", 7); //маска способа ввода карты
        rootObject.put("command", "addCardGroup");
        return rootObject.toString();
    }

    private String getAddClientJSON(DiscountCard card) throws JSONException, ParseException {
        JSONObject rootObject = new JSONObject();

        JSONObject clientObject = new JSONObject();
        rootObject.put("client", clientObject);
        clientObject.put("idclient", card.idDiscountCard); //идентификационный номер клиента
        String name = (card.lastNameContact == null ? "" : (card.lastNameContact + " "))
                + (card.firstNameContact == null ? "" : (card.firstNameContact + " "))
                + (card.middleNameContact == null ? "" : card.middleNameContact);
        clientObject.put("name", name.trim()); //ФИО клиента
        clientObject.put("text", name.trim()); //текст
        clientObject.put("sex", card.sexContact); //пол клиента
        if(card.birthdayContact != null)
            clientObject.put("birthday", new SimpleDateFormat("yyyy-MM-dd").format(card.birthdayContact)); //день рождения, год рождения должен быть больше 1900
        rootObject.put("command", "addClient");
        return rootObject.toString();
    }

    private void waitForDeletion(File file, File flagFile) {
        int count = 0;
        while (!Thread.currentThread().isInterrupted() && (file.exists() || flagFile.exists())) {
            try {
                count++;
                if (count >= 180)
                    throw Throwables.propagate(new RuntimeException(String.format(logPrefix + "file %s has been created but not processed by server", file.getAbsolutePath())));
                else
                    Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList, Set<String> directorySet, Set<Integer> succeededRequests,
                                 Map<Integer, Throwable> failedRequests, Map<Integer, Throwable> ignoredRequests) throws IOException, ParseException {
        for (RequestExchange entry : requestExchangeList) {
            for (CashRegisterInfo cashRegister : entry.cashRegisterSet) {

                String directory = cashRegister.directory + "/sale" + cashRegister.number;
                sendSalesLogger.info(logPrefix + "creating request file for directory : " + directory);
                if (new File(directory).exists() || new File(directory).mkdirs()) {
                    String dateFrom = new SimpleDateFormat("dd.MM.yyyy").format(entry.dateFrom);
                    String dateTo = new SimpleDateFormat("dd.MM.yyyy").format(entry.dateTo);

                    Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(directory + "/sale.req"), "utf-8"));
                    String data = String.format("###\n%s-%s", dateFrom, dateTo);
                    writer.write(data);
                    writer.close();
                } else {
                    failedRequests.put(entry.requestExchange, new RuntimeException("Failed to create directory " + directory));
                }
            }
            succeededRequests.add(entry.requestExchange);
        }
    }

    @Override
    public void finishReadingSalesInfo(ArtixSalesBatch salesBatch) {
        sendSalesLogger.info(logPrefix + "Finish Reading started");
        for (String readFile : salesBatch.readFiles) {
            File f = new File(readFile);
            try {
                String directory = f.getParent() + "/success-" + formatDate(new Date(System.currentTimeMillis())) + "/";
                if (new File(directory).exists() || new File(directory).mkdirs())
                    FileCopyUtils.copy(f, new File(directory + f.getName()));
            } catch (IOException | ParseException e) {
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be copied to success files", e);
            }

            if (f.delete()) {
                sendSalesLogger.info(logPrefix + "file " + readFile + " has been deleted");
            } else {
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be deleted");
            }
        }
    }

    /*@Override
    public CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList, Set<String> cashDocumentSet) throws ClassNotFoundException {

        Map<String, CashRegisterInfo> directoryCashRegisterMap = new HashMap<>();
        Set<String> directorySet = new HashSet<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (c.directory != null && c.number != null && c.handlerModel != null && c.handlerModel.endsWith("ArtixHandler")) {
                directoryCashRegisterMap.put(c.directory + "_" + c.number, c);
                directorySet.add(c.directory);
            }
        }

        List<CashDocument> cashDocumentList = new ArrayList<>();
        List<String> readFiles = new ArrayList<>();
        for (String directory : directorySet) {

            String exchangeDirectory = directory + "/cashdoc";

            File[] filesList = new File(exchangeDirectory).listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.getName().startsWith("sales") && pathname.getPath().endsWith(".json");
                }
            });

            if (filesList == null || filesList.length == 0)
                sendSalesLogger.info(logPrefix + "No cash documents found in " + exchangeDirectory);
            else {
                sendSalesLogger.info(logPrefix + "found " + filesList.length + " file(s) in " + exchangeDirectory);

                for (File file : filesList) {
                    try {

                        String fileName = file.getName();
                        sendSalesLogger.info(logPrefix + "reading " + fileName);

                        String fileContent = readFile(file.getAbsolutePath(), encoding);

                        Pattern p = Pattern.compile(".*###\\ssales\\sdata\\sbegin\\s###(.*)###\\ssales\\sdata\\send\\s###.*");
                        Matcher m = p.matcher(fileContent);
                        if (m.matches()) {
                            String[] documents = m.group(1).split("---");

                            for (String document : documents) {

                                JSONObject documentObject = new JSONObject(document);

                                Integer docType = documentObject.getInt("docType");
                                boolean in = docType == 3;
                                boolean out = docType == 4;
                                if (in || out) {

                                    String numberCashDocument = documentObject.getString("docNum");

                                    BigDecimal sumCashDocument = BigDecimal.valueOf(documentObject.getDouble("posSum"));
                                    sumCashDocument = in ? sumCashDocument : safeNegate(sumCashDocument);

                                    Integer numberCashRegister = Integer.parseInt(documentObject.getString("cashCode"));

                                    long dateTimeCashDocument = parseDateTime(documentObject.getString("timeEnd"));
                                    Date dateCashDocument = new Date(dateTimeCashDocument);
                                    Time timeCashDocument = new Time(dateTimeCashDocument);

                                    CashRegisterInfo cashRegister = directoryCashRegisterMap.get(directory + "_" + numberCashRegister);
                                    Integer numberGroup = cashRegister == null ? null : cashRegister.numberGroup;
                                    Date startDate = cashRegister == null ? null : cashRegister.startDate;
                                    if (startDate == null || dateCashDocument.compareTo(startDate) >= 0) {
                                        cashDocumentList.add(new CashDocument(numberCashDocument, numberCashDocument, dateCashDocument, timeCashDocument,
                                                numberGroup, numberCashRegister, null, sumCashDocument));
                                    }
                                }
                            }
                        }
                    } catch (Throwable e) {
                        sendSalesLogger.error(logPrefix + "File " + file.getAbsolutePath(), e);
                    }
                    readFiles.add(file.getAbsolutePath());
                }
            }
        }
        return new CashDocumentBatch(cashDocumentList, readFiles);
    }*/

/*    @Override
    public void finishReadingCashDocumentInfo(CashDocumentBatch cashDocumentBatch) {
        sendSalesLogger.info(logPrefix + "Finish ReadingCashDocumentInfo started");
        for (String readFile : cashDocumentBatch.readFiles) {
            File f = new File(readFile);

            try {
                String directory = f.getParent() + "/../success-" + formatDate(new Date(System.currentTimeMillis())) + "/";
                if (new File(directory).exists() || new File(directory).mkdirs())
                    FileCopyUtils.copy(f, new File(directory + f.getName()));
            } catch (IOException | ParseException e) {
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be copied to success files", e);
            }

            if (f.delete()) {
                sendSalesLogger.info(logPrefix + "file " + readFile + " has been deleted");
            } else {
                throw new RuntimeException("The file " + f.getAbsolutePath() + " can not be deleted");
            }
        }
    }*/

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<String> directorySet) throws IOException {
        //TODO
    }

    @Override
    public boolean sendDeleteBarcodeInfo(DeleteBarcodeInfo deleteBarcodeInfo) throws IOException {
        //TODO
        return true;
    }

    @Override
    public void sendDiscountCardList(List<DiscountCard> discountCardList, RequestExchange requestExchange) throws IOException {
        try {
            machineryExchangeLogger.info(logPrefix + "Send DiscountCardList");

            if (!discountCardList.isEmpty()) {

                ArtixSettings artixSettings = springContext.containsBean("artixSettings") ? (ArtixSettings) springContext.getBean("artixSettings") : null;
                String globalExchangeDirectory = artixSettings != null ? artixSettings.getGlobalExchangeDirectory() : null;
                boolean deleteDiscountCardsBeforeAdd = artixSettings != null && artixSettings.isDeleteDiscountCardsBeforeAdd();
                Map<String, String> discountCardNamesMap = artixSettings != null ? artixSettings.getDiscountCardNamesMap() : new HashMap<String, String>();
                if(globalExchangeDirectory != null) {
                    if (new File(globalExchangeDirectory).exists() || new File(globalExchangeDirectory).mkdirs()) {
                        machineryExchangeLogger.info(String.format(logPrefix + "Send DiscountCards to %s", globalExchangeDirectory));

                        String currentTime = new SimpleDateFormat("yyyyMMddHHmmss").format(System.currentTimeMillis());
                        File file = new File(globalExchangeDirectory + "/pos" + currentTime + ".aif");
                        File tmpFile = File.createTempFile("pos",".aif");
                        machineryExchangeLogger.info(logPrefix + "creating discountCards file " + file.getAbsolutePath());

                        for (DiscountCard d : discountCardList) {
                            if(d.typeDiscountCard != null) {
                                boolean active = requestExchange.startDate == null || (d.dateFromDiscountCard != null && d.dateFromDiscountCard.compareTo(requestExchange.startDate) >= 0);
                                if(deleteDiscountCardsBeforeAdd)
                                    writeStringToFile(tmpFile, String.format("{\"command\": \"deleteCard\", \"idCard\": } %s \n---\n", d.numberDiscountCard));
                                writeStringToFile(tmpFile, getAddCardJSON(d, active) + "\n---\n");
                            }
                        }

                        Set<String> usedGroups = new HashSet<>();
                        for (DiscountCard d : discountCardList) {
                            if (d.typeDiscountCard != null && !usedGroups.contains(d.typeDiscountCard)) {
                                usedGroups.add(d.typeDiscountCard);
                                writeStringToFile(tmpFile, getAddCardGroupJSON(d.typeDiscountCard, discountCardNamesMap.get(d.typeDiscountCard)) + "\n---\n");
                            }
                        }

//                        for (DiscountCard d : discountCardList) {
//                            if(d.typeDiscountCard != null) {
//                                writeStringToFile(tmpFile, getAddClientJSON(d) + "\n---\n");
//                            }
//                        }

                        FileCopyUtils.copy(tmpFile, file);
                        if(!tmpFile.delete())
                            tmpFile.deleteOnExit();

                        File flagFile = new File(globalExchangeDirectory + "/pos" + currentTime + ".flz");
                        if (!flagFile.createNewFile())
                            processTransactionLogger.info(String.format(logPrefix + "can't create flag file %s", flagFile.getAbsolutePath()));

                        machineryExchangeLogger.info(logPrefix + "waiting for deletion of discountCards file " + file.getAbsolutePath());
                        waitForDeletion(file, flagFile);
                    }
                } else {
                    machineryExchangeLogger.error(logPrefix + "globalExchangeDirectory not found, sendDiscountCard skipped");
                    throw new RuntimeException(logPrefix + "globalExchangeDirectory not found, sendDiscountCard skipped");
                }
            }
        } catch (ParseException | JSONException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void sendCashierInfoList(List<CashierInfo> cashierInfoList, Map<String, Set<String>> directoryStockMap) throws IOException {

        machineryExchangeLogger.info(logPrefix + "Send CashierInfoList");

        for (Map.Entry<String, Set<String>> entry : directoryStockMap.entrySet()) {

            try {

                String directory = entry.getKey();

                File tmpFile = File.createTempFile("pos",".aif");

                String currentTime = new SimpleDateFormat("yyyyMMddHHmmss").format(System.currentTimeMillis());
                File file = new File(directory + "/pos" + currentTime + ".aif");

                machineryExchangeLogger.info(logPrefix + "creating cashiers file " + file.getAbsolutePath());

                for (CashierInfo cashier : cashierInfoList) {
                    writeStringToFile(tmpFile, getAddMCashUserJSON(cashier) + "\n---\n");
                }

                FileCopyUtils.copy(tmpFile, file);
                if(!tmpFile.delete())
                    tmpFile.deleteOnExit();

                File flagFile = new File(directory + "/pos" + currentTime + ".flz");
                if(!flagFile.createNewFile())
                    processTransactionLogger.info(String.format(logPrefix + "can't create flag file %s", flagFile.getAbsolutePath()));

                machineryExchangeLogger.info(logPrefix + "waiting for deletion of cashiers file " + file.getAbsolutePath());
                waitForDeletion(file, flagFile);

            } catch (ParseException | JSONException e) {
                throw Throwables.propagate(e);
            }
        }

    }

    private void writeStringToFile(File file, String data) throws IOException {
        FileUtils.writeStringToFile(file, data, true);
    }

    @Override
    public SalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) throws IOException, ParseException, ClassNotFoundException {

        ArtixSettings artixSettings = springContext.containsBean("artixSettings") ? (ArtixSettings) springContext.getBean("artixSettings") : null;
        boolean appendBarcode = artixSettings != null && artixSettings.isAppendBarcode();
        String giftCardRegexp = artixSettings != null ? artixSettings.getGiftCardRegexp() : null;

        //Для каждой кассы отдельная директория, куда приходит реализация только по этой кассе плюс в подпапке online могут быть текущие продажи
        Map<Integer, CashRegisterInfo> departNumberCashRegisterMap = new HashMap<>();
        Set<String> directorySet = new HashSet<>();
        for (CashRegisterInfo c : cashRegisterInfoList) {
            if (c.directory != null && c.directory.equals(directory)) {
                departNumberCashRegisterMap.put(c.number, c);
                directorySet.add(c.directory + "/sale" + c.number);
                directorySet.add(c.directory + "/sale" + c.number + "/online");
            }
        }

        List<SalesInfo> salesInfoList = new ArrayList<>();
        List<String> filePathList = new ArrayList<>();

        List<File> files = new ArrayList<>();
        for(String dir : directorySet) {
            File[] filesList = new File(dir).listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.getName().startsWith("sale") && pathname.getPath().endsWith(".json");
                }
            });
            if(filesList != null)
                files.addAll(Arrays.asList(filesList));
        }

        if (files.isEmpty())
            sendSalesLogger.info(logPrefix + "No checks found in " + directory);
        else {
            sendSalesLogger.info(String.format(logPrefix + "found %s file(s) in %s", files.size(), directory));

            //Set<String> usedBarcodes = new HashSet<>();

            for (File file : files) {
                try {

                    String fileName = file.getName();
                    sendSalesLogger.info(logPrefix + "reading " + fileName);

                    String fileContent = readFile(file.getAbsolutePath(), encoding);

                    Pattern p = Pattern.compile(".*### sales data begin ###(.*)### sales data end ###.*");
                    Matcher m = p.matcher(fileContent);
                    if (m.matches()) {
                        String[] documents = m.group(1).split("---");

                        for (String document : documents) {

                            JSONObject documentObject = new JSONObject(document);

                            Integer docType = documentObject.getInt("docType");
                            boolean isSale = docType == 1;
                            boolean isReturn = docType == 2 || docType == 25;
                            if (isSale || isReturn) {

                                Integer numberCashRegister = Integer.parseInt(documentObject.getString("cashCode"));
                                String numberZReport = String.valueOf(documentObject.getInt("shift"));
                                Integer numberReceipt = Integer.parseInt(documentObject.getString("docNum"));
                                String idEmployee = documentObject.getString("userCode");

                                long dateTimeReceipt = parseDateTime(documentObject.getString("timeEnd"));
                                Date dateReceipt = new Date(dateTimeReceipt);
                                Time timeReceipt = new Time(dateTimeReceipt);

                                BigDecimal sumCard = BigDecimal.ZERO;
                                BigDecimal sumCash = BigDecimal.ZERO;
                                BigDecimal sumGiftCard = BigDecimal.ZERO;
                                Map<String, GiftCard> sumGiftCardMap = new HashMap<>();

                                JSONArray moneyPositionsArray = documentObject.getJSONArray("moneyPositions");

                                for (int i = 0; i < moneyPositionsArray.length(); i++) {
                                    JSONObject moneyPosition = moneyPositionsArray.getJSONObject(i);

                                    Integer paymentType = moneyPosition.getInt("valCode");
                                    Integer operationCode = moneyPosition.getInt("opCode");
                                    BigDecimal sum = BigDecimal.valueOf(moneyPosition.getDouble("sumB"));
                                    if (paymentType != null && ((isSale && operationCode.equals(70)) || (isReturn && operationCode.equals(74)))) {
                                        sum = (sum != null && !isSale) ? sum.negate() : sum;
                                        switch (paymentType) {
                                            case 1:
                                                sumCash = HandlerUtils.safeAdd(sumCash, sum);
                                                break;
                                            case 4:
                                                sumCard = HandlerUtils.safeAdd(sumCard, sum);
                                                break;
                                            case 6:
                                                String numberGiftCard = BaseUtils.trimToNull(moneyPosition.getString("cardnum"));
                                                sumGiftCardMap.put(numberGiftCard, new GiftCard(sum, sum));
                                                break;
                                            default:
                                                sumCash = HandlerUtils.safeAdd(sumCash, sum);
                                                break;
                                        }
                                    }
                                }

                                String seriesNumberDiscountCard = null;
                                JSONArray cardPositionsArray = documentObject.getJSONArray("cardPositions");

                                for (int i = 0; i < cardPositionsArray.length(); i++) {
                                    JSONObject cardPosition = cardPositionsArray.getJSONObject(i);
                                    seriesNumberDiscountCard = cardPosition.getString("number");
                                }

                                JSONArray inventPositionsArray = documentObject.getJSONArray("inventPositions");

                                for (int i = 0; i < inventPositionsArray.length(); i++) {
                                    int count = 1;
                                    JSONObject inventPosition = inventPositionsArray.getJSONObject(i);

                                    String idItem = inventPosition.getString("inventCode");
                                    String barcode = appendCheckDigitToBarcode(inventPosition.getString("barCode"), 7, appendBarcode);
                                    Integer numberReceiptDetail = inventPosition.getInt("posNum");

                                    BigDecimal quantity = BigDecimal.valueOf(inventPosition.getDouble("quant"));
                                    quantity = isReturn ? safeNegate(quantity) : quantity;

                                    BigDecimal price = BigDecimal.valueOf(inventPosition.getDouble("price"));
                                    BigDecimal discountSumReceiptDetail = BigDecimal.valueOf(inventPosition.getDouble("disc_abs"));

                                    BigDecimal sumReceiptDetail = BigDecimal.valueOf((inventPosition.getDouble("posSum")));
                                    sumReceiptDetail = isSale ? sumReceiptDetail : safeNegate(sumReceiptDetail);

                                    //обнаруживаем продажу сертификатов
                                    boolean isGiftCard = false;
                                    /*if (barcode != null && barcode.equals("99999")) {
                                        isGiftCard = true;
                                        while(usedBarcodes.contains(dateTimeReceipt + "/" + count)) {
                                            count++;
                                        }
                                        barcode = dateTimeReceipt + "/" + count;
                                        usedBarcodes.add(barcode);
                                    }*/
                                    if (giftCardRegexp != null && barcode != null) {
                                        Pattern pattern = Pattern.compile(giftCardRegexp);
                                        Matcher matcher = pattern.matcher(barcode);
                                        isGiftCard = matcher.matches();
                                    }

                                    CashRegisterInfo cashRegister = departNumberCashRegisterMap.get(numberCashRegister);
                                    Integer nppGroupMachinery = cashRegister == null ? null : cashRegister.numberGroup;
                                    Date startDate = cashRegister == null ? null : cashRegister.startDate;
                                    if (startDate == null || dateReceipt.compareTo(startDate) >= 0) {
                                        if (sumGiftCard.compareTo(BigDecimal.ZERO) != 0)
                                            sumGiftCardMap.put(null, new GiftCard(sumGiftCard));
                                        salesInfoList.add(new SalesInfo(isGiftCard, nppGroupMachinery, numberCashRegister, numberZReport,
                                                dateReceipt, timeReceipt, numberReceipt, dateReceipt, timeReceipt, idEmployee, null, null,
                                                sumCard, sumCash, sumGiftCardMap, barcode, idItem, null, null, quantity, price, sumReceiptDetail,
                                                discountSumReceiptDetail, null, seriesNumberDiscountCard, numberReceiptDetail, fileName, null));
                                    }

                                }
                            }
                        }
                    }
                } catch (Throwable e) {
                    sendSalesLogger.error("File: " + file.getAbsolutePath(), e);
                }
                filePathList.add(file.getAbsolutePath());
            }
        }
        //общий механизм не понимает, что надо вызвать finish, если нет ни одной записи, так что, чтобы не менять api, вызываем вручную
        ArtixSalesBatch salesBatch = new ArtixSalesBatch(salesInfoList, filePathList);
        if(salesInfoList.isEmpty() && !filePathList.isEmpty())
            finishReadingSalesInfo(salesBatch);
        return (salesInfoList.isEmpty() && filePathList.isEmpty()) ? null : salesBatch;
    }

    static String readFile(String path, String encoding) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding).replace("\n", "");
    }

    private long parseDateTime(String value) throws ParseException {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(value).getTime();
    }

/*    private String formatDateTime(long value) throws ParseException {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(value);
    }*/

    private String formatDate(Date value) throws ParseException {
        return value == null ? null : new SimpleDateFormat("yyyy-MM-dd").format(value);
    }

    private String appendCheckDigitToBarcode(String barcode, Integer minLength, boolean appendBarcode) {
        if(appendBarcode) {
            if (barcode == null || (minLength != null && barcode.length() < minLength))
                return null;

            try {
                if (barcode.length() == 11) {
                    return appendEAN13("0" + barcode).substring(1, 13);
                } else if (barcode.length() == 12) {
                    return appendEAN13(barcode);
                } else if (barcode.length() == 7) {  //EAN-8
                    int checkSum = 0;
                    for (int i = 0; i <= 6; i = i + 2) {
                        checkSum += Integer.valueOf(String.valueOf(barcode.charAt(i))) * 3;
                        checkSum += i == 6 ? 0 : Integer.valueOf(String.valueOf(barcode.charAt(i + 1)));
                    }
                    checkSum %= 10;
                    if (checkSum != 0)
                        checkSum = 10 - checkSum;
                    return barcode.concat(String.valueOf(checkSum));
                } else
                    return barcode;
            } catch (Exception e) {
                return barcode;
            }
        } else
            return barcode;
    }

    private String removeCheckDigitFromBarcode(String barcode, boolean appendBarcode) {
        if (appendBarcode && barcode != null && (barcode.length() == 13 || barcode.length() == 12 || barcode.length() == 8)) {
            return barcode.substring(0, barcode.length() - 1);
        } else
            return barcode;
    }

    private String appendEAN13(String barcode) {
        int checkSum = 0;
        for (int i = 0; i <= 10; i = i + 2) {
            checkSum += Integer.valueOf(String.valueOf(barcode.charAt(i)));
            checkSum += Integer.valueOf(String.valueOf(barcode.charAt(i + 1))) * 3;
        }
        checkSum %= 10;
        if (checkSum != 0)
            checkSum = 10 - checkSum;
        return barcode.concat(String.valueOf(checkSum));
    }
}