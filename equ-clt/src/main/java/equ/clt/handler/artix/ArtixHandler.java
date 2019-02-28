package equ.clt.handler.artix;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
import equ.clt.handler.DefaultCashRegisterHandler;
import equ.clt.handler.HandlerUtils;
import lsfusion.base.BaseUtils;
import lsfusion.base.Pair;
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
import java.sql.Timestamp;
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
    protected final static Logger softCheckLogger = Logger.getLogger("SoftCheckLogger");

    private static String logPrefix = "Artix: ";

    String encoding = "utf-8";

    private Set<File> readFiles = new HashSet<>();

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
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) throws IOException {

        ArtixSettings artixSettings = springContext.containsBean("artixSettings") ? (ArtixSettings) springContext.getBean("artixSettings") : null;
        boolean appendBarcode = artixSettings != null && artixSettings.isAppendBarcode();
        boolean isExportSoftCheckItem = artixSettings != null && artixSettings.isExportSoftCheckItem();

        Map<Long, SendTransactionBatch> result = new HashMap<>();
        Map<Long, Exception> failedTransactionMap = new HashMap<>();
        Set<Long> emptyTransactionSet = new HashSet<>();

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
                            //writeStringToFile(tmpFile, "{\"command\": \"clearPrice\"}\n---\n");

                            //scale items
                            writeStringToFile(tmpFile, "{\"command\": \"clearTmcScale\"}\n---\n");

                            //scale item groups
                            //writeStringToFile(tmpFile, "{\"command\": \"clearTmcScaleGroup\"}\n---\n");

                            //barcodes
                            writeStringToFile(tmpFile, "{\"command\": \"clearBarcode\"}\n---\n");

                            if(isExportSoftCheckItem) {
                                //искуственный товар для мягких чеков
                                writeStringToFile(tmpFile, getAddInventItemSoftJSON() + "\n---\n");
                            }
                        }

                        //items
                        Map<String, List<CashRegisterItemInfo>> barcodeMap = new HashMap<>();
                        for (CashRegisterItemInfo item : transaction.itemsList) {
                            List<CashRegisterItemInfo> items = barcodeMap.get(item.mainBarcode);
                            if (items == null)
                                items = new ArrayList<>();
                            items.add(item);
                            barcodeMap.put(item.mainBarcode, items);
                        }

                        for (Map.Entry<String, List<CashRegisterItemInfo>> barcodeEntry : barcodeMap.entrySet()) {
                            if (!Thread.currentThread().isInterrupted()) {
                                String inventItem = getAddInventItemJSON(transaction, barcodeEntry.getKey(), barcodeEntry.getValue(), appendBarcode);
                                if(inventItem != null)
                                    writeStringToFile(tmpFile, inventItem + "\n---\n");
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

                        //scale items
                        for (CashRegisterItemInfo item : transaction.itemsList) {
                            if (!Thread.currentThread().isInterrupted() && item.passScalesItem) {
                                writeStringToFile(tmpFile, getAddTmcScaleJSON(transaction, item) + "\n---\n");
                            }
                        }

                        //scale item groups
                        //мы за это не отвечаем
                        /*usedItemGroups = new HashSet<>();
                        for (CashRegisterItemInfo item : transaction.itemsList) {
                            if (!Thread.currentThread().isInterrupted() && item.passScalesItem) {
                                List<ItemGroup> hierarchyItemGroup = transaction.itemGroupMap.get(item.idItemGroup);
                                if (hierarchyItemGroup != null) {
                                    for (ItemGroup itemGroup : hierarchyItemGroup) {
                                        if (!usedItemGroups.contains(itemGroup.extIdItemGroup)) {
                                            String inventGroup = getAddTmcScaleGroupJSON(itemGroup);
                                            if (inventGroup != null)
                                                writeStringToFile(tmpFile, inventGroup + "\n---\n");
                                            usedItemGroups.add(itemGroup.extIdItemGroup);
                                        }
                                    }
                                }
                            }
                        }*/

                        String currentTime = new SimpleDateFormat("yyyyMMddHHmmss").format(System.currentTimeMillis());
                        File file = new File(directory + "/pos" + currentTime + ".aif");
                        try {
                            FileCopyUtils.copy(tmpFile, file);
                        } finally {
                            if (!tmpFile.delete()) {
                                processTransactionLogger.info(String.format(logPrefix + "unable to delete pos file %s", tmpFile.getAbsolutePath()));
                                tmpFile.deleteOnExit();
                            }
                        }

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

        for (Map.Entry<Long, Exception> entry : failedTransactionMap.entrySet()) {
            result.put(entry.getKey(), new SendTransactionBatch(entry.getValue()));
        }
        for (Long emptyTransaction : emptyTransactionSet) {
            result.put(emptyTransaction, new SendTransactionBatch(null));
        }
        return result;
    }

    private String getAddInventItemJSON(TransactionCashRegisterInfo transaction, String mainBarcode, List<CashRegisterItemInfo> items, boolean appendBarcode) throws JSONException {
        Set<CashRegisterItemInfo> barcodes = new HashSet<>();
        for(CashRegisterItemInfo item : items) {
            if(!item.idBarcode.equals(item.mainBarcode)) {
                barcodes.add(item);
            }
        }
        CashRegisterItemInfo item = items.get(0);
        Integer idUOM = parseUOM(item.idUOM);
        if(idUOM != null) {
            JSONObject rootObject = new JSONObject();

            JSONObject inventObject = new JSONObject();
            rootObject.put("invent", inventObject);
            inventObject.put("inventcode", trim(item.idItem != null ? item.idItem : item.idBarcode, 20)); //код товара
            inventObject.put("barcode", removeCheckDigitFromBarcode(mainBarcode, appendBarcode));

            if(!barcodes.isEmpty()) {
                JSONArray barcodesArray = new JSONArray();
                for(CashRegisterItemInfo barcode : barcodes) {
                    JSONObject barcodeObject = new JSONObject();

//                    barcodeObject.put("additionalprices", new JSONArray());
                    barcodeObject.put("barcode", removeCheckDigitFromBarcode(barcode.idBarcode, appendBarcode));
//                    barcodeObject.put("cquant", barcode.amountBarcode);
//                    barcodeObject.put("measurecode", barcode.idUOM);
//                    barcodeObject.put("minprice",  barcode.flags == null || ((barcode.flags & 16) == 0) ? barcode.price : barcode.minPrice != null ? barcode.minPrice : BigDecimal.ZERO);
//                    barcodeObject.put("name", barcode.name);
//                    barcodeObject.put("price", barcode.price);

                    barcodesArray.put(barcodeObject);
                }
                inventObject.put("barcodes", barcodesArray);
            }

            boolean noMinPrice = item.flags == null || (item.flags & 16) == 0;
            boolean disableInventBack = item.flags != null && (item.flags & 32) != 0;
            boolean ageVerify = item.flags != null && (item.flags & 64) != 0;

            //основной штрих-код
            inventObject.put("deptcode", 1); //код отдела
            inventObject.put("price", item.price); //цена
            inventObject.put("minprice", noMinPrice ? item.price : item.minPrice != null ? item.minPrice : BigDecimal.ZERO); //минимальная цена
            //inventObject.put("isInvent", true);
            inventObject.put("isInventItem", true); //признак это товар (1) или группа (0)
            inventObject.put("articul", item.idItem); //артикул
            inventObject.put("rtext", item.name); //текст для чека
            inventObject.put("name", item.name); //наименование товара
            inventObject.put("measurecode", idUOM); //код единицы измерения
            if (item.balance != null) {
                inventObject.put("remain", item.balance);
                inventObject.put("remaindate", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(item.balanceDate != null ? item.balanceDate : Calendar.getInstance().getTime()));
            }
            List<ItemGroup> itemGroupList = transaction.itemGroupMap.get(item.extIdItemGroup);
            if (itemGroupList != null) {
                inventObject.put("inventgroup", itemGroupList.get(0).extIdItemGroup); //код родительской группы товаров
            }

            JSONObject inventItemOptions = new JSONObject();
            inventItemOptions.put("disableinventback", disableInventBack ? 1 : 0);
            inventItemOptions.put("ageverify", ageVerify ? 1 : 0);
            JSONObject itemOptions = new JSONObject();
            itemOptions.put("inventitemoptions", inventItemOptions);

            inventObject.put("options", itemOptions);

            rootObject.put("command", "addInventItem");
            return rootObject.toString();
        } else return null;
    }

    private String getAddInventItemSoftJSON() throws JSONException {
        JSONObject rootObject = new JSONObject();
        JSONObject inventObject = new JSONObject();
        rootObject.put("invent", inventObject);
        inventObject.put("inventcode", "9999"); //код товара
        inventObject.put("name", "Приходная накладная");
        inventObject.put("barcode", "9999"); //основной штрих-код

        JSONObject inventItemOptions = new JSONObject();
        inventItemOptions.put("freesale", 1);

        JSONObject itemOptions = new JSONObject();
        itemOptions.put("inventitemoptions", inventItemOptions);
        
        inventObject.put("options", itemOptions);
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
            inventGroupObject.put("fractional", item.splitItem); //дробная единица измерения: true весовой, false штучный
            rootObject.put("command", "addUnit");
            return rootObject.toString();
        } else return null;
    }

    protected Integer parseUOM(String value) {
        try {
            return value == null ? null : Integer.parseInt(value);
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

    private String getAddTmcScaleJSON(TransactionCashRegisterInfo transaction, CashRegisterItemInfo item) throws JSONException {
        JSONObject rootObject = new JSONObject();

        JSONObject tmcScaleObject = new JSONObject();
        rootObject.put("tmcscale", tmcScaleObject);
        tmcScaleObject.put("tmcscalecode", trim(item.idBarcode, 5)); //Штрих-код товара на весах
        tmcScaleObject.put("tmccode", trim(item.idItem, 100)); //код товара
        tmcScaleObject.put("tmcscalegroupcode", 1); //Код ассортиментной группы товаров на весах
        tmcScaleObject.put("plu", getPluNumber(item)); //Номер ячейки памяти на весах

        tmcScaleObject.put("ingredients", trim(item.description, 1000)); //Состав весового товара
        tmcScaleObject.put("manufacturer", item.idBrand); //Производитель весового товара
        if(transaction.weightCodeGroupCashRegister != null)
            tmcScaleObject.put("prefix", Integer.parseInt(transaction.weightCodeGroupCashRegister)); //Префикс штрих-кода

        rootObject.put("command", "addTmcScale");
        return rootObject.toString();
    }

    private Integer getPluNumber(ItemInfo item) {
        try {
            return item.pluNumber != null ? item.pluNumber : Integer.parseInt(trim(item.idBarcode, 5));
        } catch (Exception e) {
            return 0;
        }
    }

/*    private String getAddTmcScaleGroupJSON(ItemGroup itemGroup) throws JSONException {
        if (itemGroup.extIdItemGroup != null) {
            JSONObject rootObject = new JSONObject();

            JSONObject tmcScaleGroupObject = new JSONObject();
            rootObject.put("tmcscalegroup", tmcScaleGroupObject);
            tmcScaleGroupObject.put("tmcscalegroupcode", trim(itemGroup.extIdItemGroup, 100)); //Код ассортиментной группы товаров
            tmcScaleGroupObject.put("groupname", trim(itemGroup.nameItemGroup, 255)); //Название ассортиментной группы товаров
            tmcScaleGroupObject.put("description", trim(itemGroup.nameItemGroup, 1000)); //Описание ассортиментной группы товаров
            rootObject.put("command", "addTmcScaleGroup");
            return rootObject.toString();
        } else return null;
    }*/

    private String getAddMCashUserJSON(CashierInfo cashier) throws JSONException {
        JSONObject rootObject = new JSONObject();

        JSONObject inventGroupObject = new JSONObject();
        rootObject.put("mcashuser", inventGroupObject);
        inventGroupObject.put("code", cashier.numberCashier); //код пользователя
        inventGroupObject.put("name", cashier.nameCashier); //полное имя пользователя
        inventGroupObject.put("login", cashier.nameCashier); //имя пользователя для входа в систему
        inventGroupObject.put("password", cashier.numberCashier); //пароль для входа в систему
        //inventGroupObject.put("keyposition", 1); //номер положения клавиатурного ключа для подтверждения прав у пользователя
        inventGroupObject.put("rank", cashier.idPosition); //должность пользователя

        JSONArray roleUsersArray = new JSONArray();
        JSONObject roleUsersObject = new JSONObject();
        if(cashier.idPosition != null)
            roleUsersObject.put("rolecode", getRoleCode(cashier.idPosition));
        roleUsersObject.put("rule", 1);
        roleUsersArray.put(roleUsersObject);
        inventGroupObject.put("roleusers", roleUsersArray);

        rootObject.put("command", "addMCashUser");
        return rootObject.toString();
    }

    private int getRoleCode(String idPosition) {
        try {
            return Integer.parseInt(idPosition.substring(0, 1));
        } catch (Exception e) {
            return 1;
        }
    }

    private String getAddCardJSON(DiscountCard card, boolean active) throws JSONException {
        JSONObject rootObject = new JSONObject();

        JSONObject cardObject = new JSONObject();
        rootObject.put("card", cardObject);
        cardObject.put("idcard", card.numberDiscountCard); //идентификационный код карты
        cardObject.put("idcardgroup", card.idDiscountCardType); //идентификационный код группы карт
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

    private String getAddCardGroupJSON(DiscountCard d) throws JSONException {
        JSONObject rootObject = new JSONObject();

        JSONObject cardGroupObject = new JSONObject();
        rootObject.put("cardGroup", cardGroupObject);
        cardGroupObject.put("idcardgroup", d.idDiscountCardType); //идентификационный код группы карт
        cardGroupObject.put("name", d.nameDiscountCardType != null ? d.nameDiscountCardType : d.idDiscountCardType); //имя группы карт
        cardGroupObject.put("text", d.idDiscountCardType); //текст
        cardGroupObject.put("notaddemptycard", true);
        cardGroupObject.put("pattern", "[0-9]");
        cardGroupObject.put("inputmask", 7); //маска способа ввода карты
        rootObject.put("command", "addCardGroup");
        return rootObject.toString();
    }

    private String getAddClientJSON(DiscountCard card) throws JSONException {
        JSONObject rootObject = new JSONObject();

        JSONObject clientObject = new JSONObject();
        rootObject.put("client", clientObject);
        clientObject.put("idclient", card.numberDiscountCard); //идентификационный номер клиента
        String name = (card.lastNameContact == null ? "" : (card.lastNameContact + " "))
                + (card.firstNameContact == null ? "" : (card.firstNameContact + " "))
                + (card.middleNameContact == null ? "" : card.middleNameContact);
        clientObject.put("name", name.trim()); //ФИО клиента
        //clientObject.put("text", name.trim()); //текст
        clientObject.put("sex", card.sexContact); //пол клиента
        if(card.birthdayContact != null && card.birthdayContact.compareTo(new Date(0, 0, 1)) > 0 )
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
                    throw new RuntimeException(String.format(logPrefix + "file %s has been created but not processed by server", file.getAbsolutePath()));
                else
                    Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    @Override
    public void requestSalesInfo(List<RequestExchange> requestExchangeList, Set<Long> succeededRequests,
                                 Map<Long, Throwable> failedRequests, Map<Long, Throwable> ignoredRequests) {
        for (RequestExchange entry : requestExchangeList) {
            Set<String> usedDirectories = new HashSet<>();
            for (CashRegisterInfo cashRegister : getCashRegisterSet(entry, true)) {
                String directory = cashRegister.directory + "/sale" + cashRegister.number;
                if (!usedDirectories.contains(directory)) {
                    usedDirectories.add(directory);
                    try {
                        sendSalesLogger.info(logPrefix + "creating request file for directory: " + directory);
                        if (new File(directory).exists() || new File(directory).mkdirs()) {
                            String dateFrom = new SimpleDateFormat("dd.MM.yyyy").format(entry.dateFrom);
                            String dateTo = new SimpleDateFormat("dd.MM.yyyy").format(entry.dateTo);

                            File reqFile = new File(directory + "/sale.req");
                            if (!reqFile.exists()) {
                                Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(reqFile), "utf-8"));
                                String data = String.format("###\n%s-%s", dateFrom, dateTo);
                                writer.write(data);
                                writer.close();
                                sendSalesLogger.info(logPrefix + "created request file for directory: " + directory);
                            } else {
                                sendSalesLogger.error(logPrefix + "request file already exists: " + reqFile.getAbsolutePath());
                            }
                        } else {
                            sendSalesLogger.error(logPrefix + "failed to create directory: " + directory);
                            failedRequests.put(entry.requestExchange, new RuntimeException("Failed to create directory " + directory));
                        }
                    } catch (Exception e) {
                        sendSalesLogger.error("Exception while creating sale.req in directory " + directory, e);
                        failedRequests.put(entry.requestExchange, new RuntimeException("Exception while creating sale.req in directory " + directory, e));
                    }
                }
            }
            if (!failedRequests.containsKey(entry.requestExchange))
                succeededRequests.add(entry.requestExchange);
        }
    }

    @Override
    public void finishReadingSalesInfo(ArtixSalesBatch salesBatch) {
        ArtixSettings artixSettings = springContext.containsBean("artixSettings") ? (ArtixSettings) springContext.getBean("artixSettings") : null;
        boolean disable = artixSettings != null && artixSettings.isDisableCopyToSuccess();

        sendSalesLogger.info(logPrefix + "Finish Reading started");
        RuntimeException error = null;
        for (String readFile : salesBatch.readFiles) {
            File f = new File(readFile);

            RuntimeException copyResult = copyToSuccess(f, disable);
            if(error == null)
                error = copyResult;
            safeFileDelete(f, true);
        }
        if(error != null) {
            throw error;
        }
    }

    @Override
    public Map<String, Timestamp> requestSucceededSoftCheckInfo(List<String> directoryList) {

        ArtixSettings artixSettings = springContext.containsBean("artixSettings") ? (ArtixSettings) springContext.getBean("artixSettings") : null;
        Integer maxFilesCount = artixSettings == null ? null : artixSettings.getMaxFilesCount();
        //Integer maxFilesDirectoryCount = artixSettings == null ? null : artixSettings.getMaxFilesDirectoryCount();
        String priorityDirectoriesString = artixSettings == null ? null : artixSettings.getPriorityDirectories();
        Set<String> priorityDirectories = priorityDirectoriesString == null ? new HashSet<String>() : new HashSet<>(Arrays.asList(priorityDirectoriesString.split(",")));

        Map<String, Timestamp> result = new HashMap<>();
        softCheckLogger.info(logPrefix + "reading SoftCheckInfo");

        //ArtixSettings artixSettings = springContext.containsBean("artixSettings") ? (ArtixSettings) springContext.getBean("artixSettings") : null;
        //boolean disable = artixSettings != null && artixSettings.isDisableCopyToSuccess();

        List<Pair<File, Boolean>> files = new ArrayList<>();
        //правильнее брать только только нужные подпапки, как в readSales, но для этого пришлось бы менять equ-api.
        //так что пока ищем во всех подпапках + в подпапках в папке online.
        //потенциальная проблема - левые файлы, а также файлы из папок выключенных касс
        for (Pair<File, Boolean> dirEntry : getSoftCheckDirectories(directoryList, priorityDirectories)) {
            File dir = dirEntry.first;
            boolean priority = dirEntry.second;
            File[] filesList = dir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.getName().startsWith("sale") && pathname.getPath().endsWith(".json");
                }
            });
            if (filesList != null) {
                for (File file : filesList) {
                        files.add(Pair.create(file, priority));
                }
            }
        }

        Collections.sort(files, new Comparator<Pair<File, Boolean>>() {
            public int compare(Pair<File, Boolean> f1, Pair<File, Boolean> f2) {
                if(f1.second) return f2.second ? compareDates(f1.first, f2.first) : 1; //f2 is not priority
                if(f2.second) return -1; //f1 is not priority
                return compareDates(f1.first, f2.first);
            }
        });

        int totalFilesCount = files.size();

        if(maxFilesCount == null)
            maxFilesCount = totalFilesCount;
        List<File> subFiles = new ArrayList<>();
        for(int i = 0; i < maxFilesCount; i++) {
            subFiles.add(files.get(i).first);
        }

        readFiles = new HashSet<>(subFiles);

        if (subFiles.isEmpty())
            softCheckLogger.info(logPrefix + "No sale files found");
        else {
            softCheckLogger.info(String.format(logPrefix + "found %s sale file(s), read %s sale file(s)", totalFilesCount, subFiles.size()));

            for (File file : subFiles) {
                if (!Thread.currentThread().isInterrupted()) {
                    try {

                        String fileName = file.getName();
                        softCheckLogger.info(logPrefix + "reading " + fileName);

                        String fileContent = readFile(file.getAbsolutePath(), encoding);

                        Pattern p = Pattern.compile("(?:.*)?### sales data begin ###(.*)### sales data end ###(?:.*)?");
                        Matcher m = p.matcher(fileContent);
                        if (m.matches()) {
                            //добавляем }, поскольку внутри элемента тоже может быть ---
                            String[] documents = m.group(1).split("}---");

                            for (String document : documents) {
                                if (!document.isEmpty()) {
                                    JSONObject documentObject = new JSONObject(document + "}");
                                    if (documentObject.getInt("docType") == 16) {
                                        Timestamp dateTimeReceipt = new Timestamp(parseDateTime(documentObject.getString("timeEnd")));
                                        JSONArray inventPositionsArray = documentObject.getJSONArray("inventPositions");
                                        for (int i = 0; i < inventPositionsArray.length(); i++) {
                                            JSONObject inventPosition = inventPositionsArray.getJSONObject(i);
                                            String invoiceNumber = inventPosition.getString("additionalbarcode");
                                            invoiceNumber = invoiceNumber.length() >= 7 ? invoiceNumber.substring(invoiceNumber.length() - 7) : invoiceNumber;
                                            softCheckLogger.info(logPrefix + "found softCheck " + invoiceNumber);
                                            result.put(invoiceNumber, dateTimeReceipt);
                                        }
                                    }
                                }
                            }
                        }
//                        copyToSuccess(file, disable);
//                        safeFileDelete(file, false);
                    } catch (Throwable e) {
                        softCheckLogger.error("File: " + file.getAbsolutePath(), e);
                    }
                }
            }
        }
        return result;
    }

    private int compareDates(File f1, File f2) {
        return Long.compare(f1.lastModified(), f2.lastModified());
    }

    private List<Pair<File, Boolean>> getSoftCheckDirectories(List<String> directories, Set<String> priorityDirectories) {

        List<String> directoryList = new ArrayList<>();
        for(String priorityDirectory : priorityDirectories) {
            if(directories.contains(priorityDirectory))
                directoryList.add(priorityDirectory);
        }
        for(String directory : directories) {
            if(!directoryList.contains(directory)) {
                directoryList.add(directory);
            }
        }

        List<Pair<File, Boolean>> result = new ArrayList<>();
        for(String directory : directoryList) {
            boolean priority = priorityDirectories.contains(directory);
            if(directory != null) {
                File[] subDirectoryList = new File(directory).listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        return file.isDirectory();
                    }
                });
                if (subDirectoryList != null) {
                    for (File subDirectory : subDirectoryList) {
                        result.add(Pair.create(subDirectory, priority));
                        File onlineDir = new File(subDirectory.getAbsolutePath() + "/online");
                        if (onlineDir.exists())
                            result.add(Pair.create(onlineDir, priority));
                    }
                }
            }
        }
        return result;
    }

    @Override
    public CashDocumentBatch readCashDocumentInfo(List<CashRegisterInfo> cashRegisterInfoList, Set<String> cashDocumentSet) {

        List<CashDocument> cashDocumentList = new ArrayList<>();

        ArtixSettings artixSettings = springContext.containsBean("artixSettings") ? (ArtixSettings) springContext.getBean("artixSettings") : null;
        boolean readCashDocuments = artixSettings != null && artixSettings.isReadCashDocuments();

        if (readCashDocuments) {
            //Для каждой кассы отдельная директория, куда приходит реализация только по этой кассе плюс в подпапке online могут быть текущие продажи
            Map<String, CashRegisterInfo> directoryCashRegisterMap = new HashMap<>();
            for (CashRegisterInfo c : cashRegisterInfoList) {
                if (fitHandler(c) && c.directory != null && c.number != null) {
                    directoryCashRegisterMap.put(c.directory + "/sale" + c.number, c);
                    directoryCashRegisterMap.put(c.directory + "/sale" + c.number + "/online", c);
                }
            }

            for (Map.Entry<String, CashRegisterInfo> directoryEntry : directoryCashRegisterMap.entrySet()) {
                String directory = directoryEntry.getKey();
                CashRegisterInfo cashRegister = directoryEntry.getValue();
                File[] filesList = new File(directory).listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return pathname.getName().startsWith("sale") && pathname.getPath().endsWith(".json");
                    }
                });

                if (filesList != null && filesList.length > 0) {
                    for (File file : filesList) {
                        if (!Thread.currentThread().isInterrupted() && readFiles.contains(file)) {
                            try {
                                sendSalesLogger.info(logPrefix + "reading " + file.getName());

                                Pattern p = Pattern.compile("(?:.*)?### sales data begin ###(.*)### sales data end ###(?:.*)?");
                                Matcher m = p.matcher(readFile(file.getAbsolutePath(), encoding));
                                if (m.matches()) {
                                    int count = 0;

                                    //добавляем }, поскольку внутри элемента тоже может быть ---
                                    for (String document : m.group(1).split("}---")) {
                                        if (!document.isEmpty()) {
                                            JSONObject documentObject = new JSONObject(document + "}");

                                            Integer docType = documentObject.getInt("docType");
                                            boolean in = docType == 3;
                                            boolean out = docType == 4;
                                            if (in || out) {

                                                String numberCashDocument = documentObject.getString("docNum");
                                                String idEmployee = documentObject.getString("userCode");
                                                String dopData = documentObject.getString("dopdata");

                                                BigDecimal sumCashDocument = BigDecimal.valueOf(documentObject.getDouble("docSum"));
                                                sumCashDocument = in ? sumCashDocument : safeNegate(sumCashDocument);

                                                Integer numberCashRegister = Integer.parseInt(documentObject.getString("cashCode"));

                                                long dateTimeCashDocument = parseDateTime(documentObject.getString("timeEnd"));
                                                Date dateCashDocument = new Date(dateTimeCashDocument);
                                                Time timeCashDocument = new Time(dateTimeCashDocument);

                                                if (cashRegister.number.equals(numberCashRegister)) {
                                                    if (cashRegister.startDate == null || dateCashDocument.compareTo(cashRegister.startDate) >= 0) {
                                                        String idCashDocument = cashRegister.numberGroup + "/" + numberCashRegister + "/" + numberCashDocument + "/" + dopData;
                                                        cashDocumentList.add(new CashDocument(idCashDocument, numberCashDocument, dateCashDocument, timeCashDocument,
                                                                cashRegister.numberGroup, numberCashRegister, null, sumCashDocument, idEmployee));
                                                    }
                                                }
                                                count++;
                                            }

                                        }
                                    }
                                    if(count > 0)
                                        sendSalesLogger.info(String.format(logPrefix + "found %s cashDocument(s) in %s", count, file.getAbsolutePath()));
                                }
                            } catch (Throwable e) {
                                sendSalesLogger.error(logPrefix + "File " + file.getAbsolutePath(), e);
                            }
                        }
                    }
                }
            }
        }
        return new CashDocumentBatch(cashDocumentList, null);
    }

    @Override
    public void sendDiscountCardList(List<DiscountCard> discountCardList, RequestExchange requestExchange) throws IOException {
        try {
            machineryExchangeLogger.info(logPrefix + "Send DiscountCardList");

            if (!discountCardList.isEmpty()) {

                ArtixSettings artixSettings = springContext.containsBean("artixSettings") ? (ArtixSettings) springContext.getBean("artixSettings") : null;
                String globalExchangeDirectory = artixSettings != null ? artixSettings.getGlobalExchangeDirectory() : null;
                boolean exportClients = artixSettings != null && artixSettings.isExportClients();
                if(globalExchangeDirectory != null) {
                    if (new File(globalExchangeDirectory).exists() || new File(globalExchangeDirectory).mkdirs()) {
                        machineryExchangeLogger.info(String.format(logPrefix + "Send DiscountCards to %s", globalExchangeDirectory));

                        String currentTime = new SimpleDateFormat("yyyyMMddHHmmss").format(System.currentTimeMillis());
                        File file = new File(globalExchangeDirectory + "/pos" + currentTime + ".aif");
                        File tmpFile = File.createTempFile("pos",".aif");
                        machineryExchangeLogger.info(logPrefix + "creating discountCards file " + file.getAbsolutePath());

                        for (DiscountCard d : discountCardList) {
                            if(d.idDiscountCardType != null) {
                                boolean active = requestExchange.startDate == null || (d.dateFromDiscountCard != null && d.dateFromDiscountCard.compareTo(requestExchange.startDate) >= 0);
                                writeStringToFile(tmpFile, getAddCardJSON(d, active) + "\n---\n");
                            }
                        }

                        Set<String> usedGroups = new HashSet<>();
                        for (DiscountCard d : discountCardList) {
                            if (d.idDiscountCardType != null && !usedGroups.contains(d.idDiscountCardType)) {
                                usedGroups.add(d.idDiscountCardType);
                                writeStringToFile(tmpFile, getAddCardGroupJSON(d) + "\n---\n");
                            }
                        }

                        if(exportClients) {
                            for (DiscountCard d : discountCardList) {
                                if (d.idDiscountCard != null) {
                                    writeStringToFile(tmpFile, getAddClientJSON(d) + "\n---\n");
                                }
                            }
                        }

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
        } catch (JSONException e) {
            throw Throwables.propagate(e);
        }
    }

    private RuntimeException copyToSuccess(File file, boolean disable) {
        RuntimeException result = null;
        if (!disable) {
            try {
                String directory = file.getParent() + "/success-" + formatDate(new Date(System.currentTimeMillis())) + "/";
                if (new File(directory).exists() || new File(directory).mkdirs())
                    FileCopyUtils.copy(file, new File(directory + file.getName()));
            } catch (IOException e) {
                sendSalesLogger.error("The file " + file.getAbsolutePath() + " can not be copied to success files", e);
                result = new RuntimeException("The file " + file.getAbsolutePath() + " can not be copied to success files", e);
            }
        }
        return result;
    }

    private Timestamp parseTimestamp(String value) throws ParseException {
        return new Timestamp(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(value).getTime());
    }

    @Override
    public void sendCashierInfoList(List<CashierInfo> cashierInfoList, RequestExchange requestExchange) throws IOException {

        machineryExchangeLogger.info(logPrefix + "Send CashierInfoList");

        ArtixSettings artixSettings = springContext.containsBean("artixSettings") ? (ArtixSettings) springContext.getBean("artixSettings") : null;
        String globalExchangeDirectory = artixSettings != null ? artixSettings.getGlobalExchangeDirectory() : null;
        if (globalExchangeDirectory != null) {
            File directory = new File(globalExchangeDirectory);
            if (directory.exists() || directory.mkdirs()) {
                try {

                    File tmpFile = File.createTempFile("pos", ".aif");

                    String currentTime = new SimpleDateFormat("yyyyMMddHHmmss").format(System.currentTimeMillis());
                    File file = new File(directory + "/pos" + currentTime + ".aif");

                    machineryExchangeLogger.info(logPrefix + "creating cashiers file " + file.getAbsolutePath());

                    for (CashierInfo cashier : cashierInfoList) {
                        writeStringToFile(tmpFile, getAddMCashUserJSON(cashier) + "\n---\n");
                    }

                    FileCopyUtils.copy(tmpFile, file);
                    if (!tmpFile.delete())
                        tmpFile.deleteOnExit();

                    File flagFile = new File(directory + "/pos" + currentTime + ".flz");
                    if (!flagFile.createNewFile())
                        processTransactionLogger.info(String.format(logPrefix + "can't create flag file %s", flagFile.getAbsolutePath()));

                    machineryExchangeLogger.info(logPrefix + "waiting for deletion of cashiers file " + file.getAbsolutePath());
                    waitForDeletion(file, flagFile);

                } catch (JSONException e) {
                    throw Throwables.propagate(e);
                }
            }
        }

    }

    private void writeStringToFile(File file, String data) throws IOException {
        FileUtils.writeStringToFile(file, data, encoding, true);
    }

    @Override
    public SalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) {

        ArtixSettings artixSettings = springContext.containsBean("artixSettings") ? (ArtixSettings) springContext.getBean("artixSettings") : null;
        boolean appendBarcode = artixSettings != null && artixSettings.isAppendBarcode();
        String giftCardRegexp = artixSettings != null ? artixSettings.getGiftCardRegexp() : null;
        boolean bonusesInDiscountPositions = artixSettings != null && artixSettings.isBonusesInDiscountPositions();
        boolean giftCardPriceInCertificatePositions = artixSettings != null && artixSettings.isGiftCardPriceInCertificatePositions();
        boolean notDeleteEmptyFiles = artixSettings != null && artixSettings.isNotDeleteEmptyFiles();

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

        Set<String> filePathSet = new HashSet<>();

        List<CashierTime> cashierTimeList = new ArrayList<>();
        List<SalesInfo> salesInfoList = new ArrayList<>();

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

            for (File file : files) {
                if (!Thread.currentThread().isInterrupted() && readFiles.contains(file)) {
                    try {

                        String fileName = file.getName();
                        sendSalesLogger.info(logPrefix + "reading " + fileName);

                        List<CashierTime> currentCashierTimeList = readCashierTime(file, departNumberCashRegisterMap);
                        cashierTimeList.addAll(currentCashierTimeList);

                        String fileContent = readFile(file.getAbsolutePath(), encoding);

                        List<SalesInfo> currentSalesInfoList = new ArrayList<>();

                        Map<String, ZReportInfo> externalSumMap = new HashMap<>();
                        Map<String, Long> dateTimeShiftMap = new HashMap<>();
                        Pattern shiftPattern = Pattern.compile("(?:.*)?### shift info begin ###(.*)### shift info end ###(?:.*)?");
                        Matcher shiftMatcher = shiftPattern.matcher(fileContent);
                        if (shiftMatcher.matches()) {
                            String[] documents = shiftMatcher.group(1).split("---");
                            for (String document : documents) {
                                if (!document.isEmpty()) {
                                    JSONObject documentObject = new JSONObject(document);

                                    Integer numberCashRegister = Integer.parseInt(documentObject.getString("cashCode"));
                                    String numberZReport = String.valueOf(documentObject.getInt("shift"));
                                    BigDecimal sumGain = BigDecimal.valueOf(documentObject.getDouble("sumGain"));
                                    String timeBeg = documentObject.getString("timeBeg");

                                    JSONArray kkms = documentObject.getJSONArray("kkms");
                                    BigDecimal sumProtectedEnd = null;
                                    BigDecimal sumBack = null;
                                    for (int i = 0; i < kkms.length(); i++) {
                                        JSONObject kkmsObject = kkms.getJSONObject(i);
                                        sumProtectedEnd = getBigDecimal(kkmsObject, "sumProtectedEnd");
                                        sumBack = getBigDecimal(kkmsObject, "sumBack");
                                    }
                                    
                                    if (!timeBeg.equals("null")) {
                                        long timestamp = parseDateTime(timeBeg);

                                        externalSumMap.put(numberCashRegister + "/" + numberZReport, new ZReportInfo(sumGain, sumProtectedEnd, sumBack));
                                        dateTimeShiftMap.put(numberCashRegister + "/" + numberZReport, timestamp);
                                    }
                                }
                            }
                        }

                        Pattern p = Pattern.compile("(?:.*)?### sales data begin ###(.*)### sales data end ###(?:.*)?");
                        Matcher m = p.matcher(fileContent);
                        if (m.matches()) {
                            //добавляем }, поскольку внутри элемента тоже может быть ---
                            String[] documents = m.group(1).split("}---");

                            for (String document : documents) {
                                if (!document.isEmpty()) {
                                    JSONObject documentObject = new JSONObject(document + "}");

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

                                        Long dateTimeShift = dateTimeShiftMap.get(numberCashRegister + "/" + numberZReport);
                                        Date dateZReport = dateTimeShift == null ? dateReceipt : new Date(dateTimeShift);
                                        Time timeZReport = dateTimeShift == null ? timeReceipt : new Time(dateTimeShift);

                                        BigDecimal sumCard = BigDecimal.ZERO;
                                        BigDecimal sumCash = BigDecimal.ZERO;
                                        BigDecimal sumGiftCard = BigDecimal.ZERO;
                                        Map<String, GiftCard> sumGiftCardMap = new HashMap<>();

                                        Map<String, BigDecimal> certificatePriceMap = new HashMap<>();
                                        if(giftCardPriceInCertificatePositions) {
                                            JSONArray certificatePositionsArray = documentObject.getJSONArray("certificatePositions");
                                            for (int i = 0; i < certificatePositionsArray.length(); i++) {
                                                JSONObject certificatePosition = certificatePositionsArray.getJSONObject(i);
                                                String certificateNumber = certificatePosition.getString("number");
                                                BigDecimal sum = BigDecimal.valueOf(certificatePosition.getDouble("sum"));
                                                certificatePriceMap.put(certificateNumber, sum);
                                            }
                                        }

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
                                                    case 7:
                                                        String certificate = BaseUtils.trimToNull(moneyPosition.getString("cardnum"));
                                                        String numberGiftCard = certificate != null && certificate.length() >= 11 ? appendCheckDigitToBarcode(certificate, 11, appendBarcode) : certificate;
                                                        BigDecimal price = certificatePriceMap.get(certificate);
                                                        price = sum != null && price != null ? price : sum;
                                                        if (sumGiftCardMap.containsKey(numberGiftCard)) { // пока вот такой чит, так как в cardnum бывают пустые значения
                                                            sumGiftCardMap.get(numberGiftCard).sum = HandlerUtils.safeAdd(sumGiftCardMap.get(numberGiftCard).sum, sum);
                                                            sumGiftCardMap.get(numberGiftCard).price = HandlerUtils.safeAdd(sumGiftCardMap.get(numberGiftCard).price, price);
                                                        } else
                                                            sumGiftCardMap.put(numberGiftCard, new GiftCard(sum, price));
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
                                            JSONObject inventPosition = inventPositionsArray.getJSONObject(i);

                                            String idItem = inventPosition.getString("inventCode");
                                            String barcodeString = inventPosition.getString("barCode");
                                            String opCode = inventPosition.getString("opCode");

                                            // вот такой вот чит из-за того, что могут ввести код товара в кассе
                                            String barcode = idItem != null && barcodeString != null && idItem.equals(barcodeString) ? null :
                                                    appendCheckDigitToBarcode(barcodeString, 7, appendBarcode);

                                            //обнаруживаем продажу сертификатов
                                            boolean isGiftCard = false;
                                            if (giftCardRegexp != null && barcodeString != null) {
                                                Pattern pattern = Pattern.compile(giftCardRegexp);
                                                Matcher matcher = pattern.matcher(barcodeString);
                                                isGiftCard = matcher.matches();
                                            } else if (opCode != null && opCode.equals("63")) {
                                                barcode = inventPosition.getString("bcode_main");
                                                isGiftCard = true;
                                            }

                                            Integer numberReceiptDetail = inventPosition.getInt("posNum");

                                            BigDecimal quantity = BigDecimal.valueOf(inventPosition.getDouble("quant"));
                                            quantity = isReturn ? safeNegate(quantity) : quantity;

                                            BigDecimal price = BigDecimal.valueOf(inventPosition.getDouble("price"));
                                            BigDecimal sumReceiptDetail = BigDecimal.valueOf((inventPosition.getDouble("posSum")));

                                            BigDecimal discountSumReceiptDetail = null;
                                            BigDecimal discountPercentReceiptDetail = null;

                                            boolean hasBonus = false;
                                            JSONArray discountPositionsArray = inventPosition.getJSONArray("discountPositions");
                                            for (int j = 0; j < discountPositionsArray.length(); j++) {
                                                JSONObject discountPosition = discountPositionsArray.getJSONObject(j);
                                                String discType = discountPosition.getString("discType");
                                                if(discType != null && discType.equals("bonus"))
                                                    hasBonus = true;
                                            }

                                            if(bonusesInDiscountPositions && hasBonus) {
                                                BigDecimal baseSum = BigDecimal.valueOf((inventPosition.getDouble("baseSum")));
                                                for (int j = 0; j < discountPositionsArray.length(); j++) {
                                                    JSONObject discountPosition = discountPositionsArray.getJSONObject(j);
                                                    BigDecimal discSum = BigDecimal.valueOf(discountPosition.getDouble("discSum"));
                                                    discountSumReceiptDetail = safeAdd(discountSumReceiptDetail, discSum);
                                                }
                                                sumReceiptDetail = safeSubtract(baseSum, discountSumReceiptDetail);
                                                discountPercentReceiptDetail = safeMultiply(safeDivide(discountSumReceiptDetail, baseSum), 100);
                                            } else {
                                                discountSumReceiptDetail = BigDecimal.valueOf(inventPosition.getDouble("disc_abs"));
                                                for (int j = 0; j < discountPositionsArray.length(); j++) {
                                                    JSONObject discountPosition = discountPositionsArray.getJSONObject(j);
                                                    BigDecimal discSize;
                                                    String discType = discountPosition.getString("discType");
                                                    if(discType.equals("summ")) {
                                                        //рассчитываем процент вручную
                                                        BigDecimal discSum = BigDecimal.valueOf(discountPosition.getDouble("discSum"));
                                                        BigDecimal checkSum = BigDecimal.valueOf(discountPosition.getDouble("checkSum"));
                                                        discSize = safeMultiply(safeDivide(discSum, checkSum), 100);
                                                    } else {
                                                        discSize = BigDecimal.valueOf(discountPosition.getDouble("discSize"));
                                                    }
                                                    discountPercentReceiptDetail = safeAdd(discountPercentReceiptDetail, discSize);
                                                }
                                            }

                                            sumReceiptDetail = isSale ? sumReceiptDetail : safeNegate(sumReceiptDetail);

                                            CashRegisterInfo cashRegister = departNumberCashRegisterMap.get(numberCashRegister);
                                            if (cashRegister == null)
                                                sendSalesLogger.error(logPrefix + String.format("CashRegister %s not found (file %s)", numberCashRegister, file.getAbsolutePath()));
                                            Integer nppGroupMachinery = cashRegister == null ? null : cashRegister.numberGroup;
                                            Date startDate = cashRegister == null ? null : cashRegister.startDate;
                                            if (startDate == null || dateReceipt.compareTo(startDate) >= 0) {
                                                if (sumGiftCard.compareTo(BigDecimal.ZERO) != 0)
                                                    sumGiftCardMap.put(null, new GiftCard(sumGiftCard));
                                                currentSalesInfoList.add(new SalesInfo(isGiftCard, nppGroupMachinery, numberCashRegister, numberZReport,
                                                        dateZReport, timeZReport, numberReceipt, dateReceipt, timeReceipt, idEmployee, null, null,
                                                        sumCard, sumCash, sumGiftCardMap, barcode, idItem, null, null, quantity, price, sumReceiptDetail,
                                                        discountPercentReceiptDetail, discountSumReceiptDetail, null, seriesNumberDiscountCard,
                                                        numberReceiptDetail, fileName, null, cashRegister));
                                            }
                                        }
                                    }
                                }
                            }
                            if(!externalSumMap.isEmpty()) {
                                for(SalesInfo salesInfo : currentSalesInfoList) {
                                    salesInfo.zReportInfo = externalSumMap.get(salesInfo.nppMachinery + "/" + salesInfo.numberZReport);
                                }
                            }
                            salesInfoList.addAll(currentSalesInfoList);
                        }

                        if (currentCashierTimeList.isEmpty() && currentSalesInfoList.isEmpty()) {
                            if(notDeleteEmptyFiles) {
                                sendSalesLogger.info(String.format("File %s has no sales nor cashierTime, but not deleted", file.getAbsolutePath()));
                            } else {
                                safeFileDelete(file, false);
                            }
                        } else {
                            filePathSet.add(file.getAbsolutePath());
                        }
                    } catch (Throwable e) {
                        sendSalesLogger.error("File: " + file.getAbsolutePath(), e);
                    }
                }
            }
        }

        return (cashierTimeList.isEmpty() && salesInfoList.isEmpty() && filePathSet.isEmpty()) ? null :
                new ArtixSalesBatch(salesInfoList, cashierTimeList, filePathSet);
    }

    private BigDecimal getBigDecimal(JSONObject obj, String key) throws JSONException {
        Object object = obj.get(key);
        if (object instanceof BigDecimal) {
            return (BigDecimal)object;
        } else return null;
    }

    public List<CashierTime> readCashierTime(File file, Map<Integer, CashRegisterInfo> departNumberCashRegisterMap) throws JSONException, ParseException, IOException {
        List<CashierTime> result = new ArrayList<>();

        String fileContent = readFile(file.getAbsolutePath(), encoding);
        Pattern p = Pattern.compile("(?:.*)?### securitylog info begin ###(.*)### securitylog info end ###(?:.*)?");
        Matcher m = p.matcher(fileContent);
        if (m.matches()) {
            String[] documents = m.group(1).split("---");

            Timestamp logOnCashier = null;
            String numberCashier = null;
            for (String document : documents) {
                if (!document.isEmpty()) {
                    JSONObject documentObject = new JSONObject(document);
                    Integer opcode = documentObject.getInt("opcode");
                    switch (opcode) {
                        case 3:
                            logOnCashier = parseTimestamp(documentObject.getString("optime"));
                            numberCashier = documentObject.getString("cashiercard");
                            break;
                        case 13:
                            if (logOnCashier != null) {
                                Timestamp logOffCashier = parseTimestamp(documentObject.getString("optime"));

                                Integer numberCashRegister = Integer.parseInt(documentObject.getString("cashcode"));
                                CashRegisterInfo cashRegister = departNumberCashRegisterMap.get(numberCashRegister);
                                if (cashRegister == null)
                                    sendSalesLogger.error(logPrefix + String.format("CashRegister %s not found (file %s)", numberCashRegister, file.getAbsolutePath()));
                                Integer nppGroupMachinery = cashRegister == null ? null : cashRegister.numberGroup;

                                String idCashierTime = numberCashier + "/" + numberCashRegister + "/" + nppGroupMachinery + "/" + formatTimestamp(logOnCashier) + "/" + formatTimestamp(logOffCashier);
                                result.add(new CashierTime(idCashierTime, numberCashier, numberCashRegister, nppGroupMachinery, logOnCashier, logOffCashier, true));
                                logOnCashier = null;
                            }
                            break;
                    }
                }
            }
        }
        if(!result.isEmpty())
            sendSalesLogger.info(logPrefix + "found " + result.size() + "CashierTime(s) in " + file.getName());
        return result;
    }

    static String readFile(String path, String encoding) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding).replace("\n", "");
    }

    private void safeFileDelete(File file, boolean throwException) {
        sendSalesLogger.info(logPrefix + String.format("deleting file %s", file.getAbsolutePath()));
        if (!file.delete()) {
            if(throwException) {
                throw new RuntimeException("The file " + file.getAbsolutePath() + " can not be deleted");
            } else {
                file.deleteOnExit();
                sendSalesLogger.info(logPrefix + String.format("failed to delete file %s, will try to deleteOnExit", file.getAbsolutePath()));
            }
        }
    }

    private long parseDateTime(String value) throws ParseException {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(value).getTime();
    }

    private String formatTimestamp(Timestamp date) {
        return date == null ? null : new SimpleDateFormat("dd.MM.yyyy H:mm:ss").format(date);
    }

    private String formatDate(Date value) {
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