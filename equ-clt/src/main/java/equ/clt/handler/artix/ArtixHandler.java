package equ.clt.handler.artix;

import com.google.common.base.Throwables;
import equ.api.*;
import equ.api.cashregister.*;
import equ.clt.handler.DefaultCashRegisterHandler;
import equ.clt.handler.HandlerUtils;
import lsfusion.base.BaseUtils;
import lsfusion.base.Pair;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.util.FileCopyUtils;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static equ.clt.EquipmentServer.*;
import static equ.clt.handler.HandlerUtils.*;
import static lsfusion.base.BaseUtils.nvl;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public class ArtixHandler extends DefaultCashRegisterHandler<ArtixSalesBatch> {

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
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) {

        ArtixSettings artixSettings = springContext.containsBean("artixSettings") ? (ArtixSettings) springContext.getBean("artixSettings") : new ArtixSettings();
        boolean appendBarcode = artixSettings.isAppendBarcode();
        boolean isExportSoftCheckItem = artixSettings.isExportSoftCheckItem();
        Integer timeout = artixSettings.getTimeout() == null ? 180 : artixSettings.getTimeout();
        boolean medicineMode = artixSettings.isMedicineMode();

        Map<Long, SendTransactionBatch> result = new HashMap<>();
        Map<Long, Exception> failedTransactionMap = new HashMap<>();
        Set<Long> emptyTransactionSet = new HashSet<>();
        usedCountries = new HashSet<>();

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

                            //scale items
                            writeStringToFile(tmpFile, "{\"command\": \"clearTmcScale\"}\n---\n");

                            //barcodes
                            writeStringToFile(tmpFile, "{\"command\": \"clearBarcode\"}\n---\n");

                            if(isExportSoftCheckItem) {
                                //искуственный товар для мягких чеков
                                writeStringToFile(tmpFile, getAddInventItemSoftJSON() + "\n---\n");
                            }

                            if(medicineMode) {
                                writeStringToFile(tmpFile, "{\"command\": \"clearMedicine\"}\n---\n");
                            }
                        }

                        List<String> batchItems = new ArrayList<>();
                        if(medicineMode) {
                            for (CashRegisterItem item : transaction.itemsList) {
                                if(item.batchList != null) {
                                    for(CashRegisterItemBatch batch : item.batchList) {
                                        if (!Thread.currentThread().isInterrupted()) {
                                            String country = getAddCountryJSON(batch);
                                            if (country != null) {
                                                writeStringToFile(tmpFile, country + "\n---\n");
                                            }
                                            String medicine = getAddMedicineJSON(item, batch, appendBarcode);
                                            if (medicine != null) {
                                                batchItems.add(item.mainBarcode);
                                                writeStringToFile(tmpFile, medicine + "\n---\n");
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        //items
                        Map<String, List<CashRegisterItem>> barcodeMap = new HashMap<>();
                        for (CashRegisterItem item : transaction.itemsList) {
                            List<CashRegisterItem> items = barcodeMap.get(item.mainBarcode);
                            if (items == null)
                                items = new ArrayList<>();
                            items.add(item);
                            barcodeMap.put(item.mainBarcode, items);
                        }

                        for (Map.Entry<String, List<CashRegisterItem>> barcodeEntry : barcodeMap.entrySet()) {
                            if (!Thread.currentThread().isInterrupted()) {
                                String inventItem = getAddInventItemJSON(transaction, batchItems, barcodeEntry.getKey(), barcodeEntry.getValue(), appendBarcode);
                                if(inventItem != null) {
                                    writeStringToFile(tmpFile, inventItem + "\n---\n");
                                } else {
                                    //сейчас inventItem == null только при отсутствии UOM
                                    processTransactionLogger.error(logPrefix + "NO UOM! inventItem record not created for barcode " + barcodeEntry.getKey());
                                }
                            }
                        }

                        //item groups
                        Set<String> usedItemGroups = new HashSet<>();
                        for (CashRegisterItem item : transaction.itemsList) {
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

                        //Tax groups
                        Set<BigDecimal> usedTaxGroups = new HashSet<>();
                        for (CashRegisterItem item : transaction.itemsList) {
                            if (!Thread.currentThread().isInterrupted()) {
                                if (item.vat != null && !usedTaxGroups.contains(item.vat)) {
                                    String group = getAddTaxGroup(item);
                                    if (group != null)
                                        writeStringToFile(tmpFile, group + "\n---\n");
                                    usedTaxGroups.add(item.vat);
                                }
                            }
                        }

                        //UOMs
                        Set<String> usedUOMs = new HashSet<>();
                        for (CashRegisterItem item : transaction.itemsList) {
                            if (!Thread.currentThread().isInterrupted()) {
                                if (!usedUOMs.contains(item.idUOM)) {
                                    String unit = getAddUnitJSON(item);
                                    if (unit != null)
                                        writeStringToFile(tmpFile, unit + "\n---\n");
                                    usedUOMs.add(item.idUOM);
                                }
                            }
                        }

                        //scale items
                        for (CashRegisterItem item : transaction.itemsList) {
                            if (!Thread.currentThread().isInterrupted() && item.passScalesItem) {
                                writeStringToFile(tmpFile, getAddTmcScaleJSON(transaction, item) + "\n---\n");
                            }
                        }

                        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
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

                        processTransactionLogger.info(String.format(logPrefix + "created pos file %s (Transaction %s)", file.getAbsolutePath(), transaction.id));
                        waitForDeletion(file, flagFile, timeout);
                        processTransactionLogger.info(String.format(logPrefix + "processed pos file %s (Transaction %s)", file.getAbsolutePath(), transaction.id));

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

    private String getAddInventItemJSON(TransactionCashRegisterInfo transaction, List<String> batchItems, String mainBarcode, List<CashRegisterItem> items, boolean appendBarcode) throws JSONException {
        Set<CashRegisterItem> barcodes = new HashSet<>();
        for(CashRegisterItem item : items) {
            //если есть addMedicine, дополнительные ШК не выгружаем
            if(!batchItems.contains(item.mainBarcode) && !item.idBarcode.equals(item.mainBarcode)) {
                barcodes.add(item);
            }
        }
        CashRegisterItem item = items.get(0);
        Integer idUOM = parseUOM(item.idUOM);
        if(idUOM != null) {
            JSONObject rootObject = new JSONObject();

            JSONObject inventObject = new JSONObject();
            rootObject.put("invent", inventObject);
            inventObject.put("inventcode", trim(item.idItem != null ? item.idItem : item.idBarcode, 20)); //код товара
            inventObject.put("barcode", removeCheckDigitFromBarcode(mainBarcode, appendBarcode));

            if(!barcodes.isEmpty()) {
                JSONArray barcodesArray = new JSONArray();
                for(CashRegisterItem barcode : barcodes) {
                    JSONObject barcodeObject = new JSONObject();
                    barcodeObject.put("barcode", removeCheckDigitFromBarcode(barcode.idBarcode, appendBarcode));
                    barcodesArray.put(barcodeObject);
                }
                inventObject.put("barcodes", barcodesArray);
            }

            boolean noMinPrice = item.flags == null || (item.flags & 16) == 0;
            boolean disableInventBack = item.flags != null && (item.flags & 32) != 0;
            boolean ageVerify = item.flags != null && (item.flags & 64) != 0;
            boolean disableInventSale = item.flags != null && (item.flags & 128) != 0;

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
            if (item.vat != null)
                inventObject.put("taxgroupcode", item.vat.intValue());
            if (item.balance != null) {
                inventObject.put("remain", item.balance);
                inventObject.put("remaindate", (item.balanceDate != null ? item.balanceDate : LocalDateTime.now()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
            }
            List<ItemGroup> itemGroupList = transaction.itemGroupMap.get(item.extIdItemGroup);
            if (itemGroupList != null) {
                inventObject.put("inventgroup", itemGroupList.get(0).extIdItemGroup); //код родительской группы товаров
            }

            Integer requireSaleRestrict = null;
            boolean hasQuantityOptions = false;
            if(item.info != null) {
                JSONObject infoJSON = new JSONObject(item.info).optJSONObject("artix");
                if (infoJSON != null) {
                    Double alcoholPercent = infoJSON.optDouble("alcoholpercent");
                    if(!alcoholPercent.isNaN()) {
                        requireSaleRestrict = infoJSON.getInt("requiresalerestrict");
                        inventObject.put("alcoholpercent", alcoholPercent);
                    }

                    Double secondPrice = infoJSON.optDouble("secondPrice");
                    Double thirdPrice = infoJSON.optDouble("thirdPrice");
                    if(!secondPrice.isNaN() || !thirdPrice.isNaN()) {
                        JSONArray additionalPrices = new JSONArray();
                        if(!secondPrice.isNaN()) {
                            additionalPrices.put(getAdditionalPriceJSON(2, secondPrice, "VIP"));
                        }
                        if(!thirdPrice.isNaN()) {
                            additionalPrices.put(getAdditionalPriceJSON(3, thirdPrice, "Опт"));
                        }
                        inventObject.put("additionalprices", additionalPrices);
                    }

                    hasQuantityOptions = infoJSON.optBoolean("hasquantityoptions");
                }
            }

            JSONObject itemOptions = new JSONObject();

            JSONObject inventItemOptions = new JSONObject();
            inventItemOptions.put("disableinventback", disableInventBack ? 1 : 0);
            inventItemOptions.put("disableinventsale", disableInventSale ? 1 : 0);
            inventItemOptions.put("ageverify", ageVerify ? 1 : 0);
            if(requireSaleRestrict != null) {
                inventItemOptions.put("requiresalerestrict", requireSaleRestrict);

                if(hasQuantityOptions) {
                    JSONObject quantityoptions = new JSONObject();
                    quantityoptions.put("quantitylimit", 1.000); //лимит количества товара в позиции
                    quantityoptions.put("documentquantlimit", 1.000); //лимит количества товара в чеке
                    quantityoptions.put("enablequantitylimit", true); //включить ограничение количества товара
                    quantityoptions.put("enabledocumentquantitylimit", 2); //включить ограничение количества товара в чеке

                    itemOptions.put("quantityoptions", quantityoptions);
                }
            }

            itemOptions.put("inventitemoptions", inventItemOptions);

            inventObject.put("options", itemOptions);

            Integer blisterAmount = getMaxBlisterAmount(item);
            if(blisterAmount != null) {
                inventObject.put("cquant", blisterAmount);
            }


//            "additionalprices": [ {
//                "pricecode": 2,
//                        "price": 2.50,
//                        "name": "VIP"}, {
//                "pricecode": 3,
//                        "price": 1.50,
//                        "name": "Опт"
//            }
//]

            rootObject.put("command", "addInventItem");
            return rootObject.toString();
        } else return null;
    }

    private JSONObject getAdditionalPriceJSON(int priceCode, Double price, String name) {
        JSONObject additionalPrice = new JSONObject();
        additionalPrice.put("pricecode", priceCode);
        additionalPrice.put("price", price);
        additionalPrice.put("name", name);
        return additionalPrice;
    }

    private Integer getMaxBlisterAmount(CashRegisterItem item) {
        //кол-во блистеров в упаковке
        Integer blisterAmount = null;
        if(item.batchList != null) {
            for (CashRegisterItemBatch batch : item.batchList) {
                if(blisterAmount == null) {
                    blisterAmount = batch.blisterAmount;
                } else {
                    blisterAmount = batch.blisterAmount == null ? blisterAmount : Math.max(blisterAmount, batch.blisterAmount);
                }

            }
            blisterAmount = nvl(blisterAmount, 1);
        }
        return blisterAmount;
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

    Set<String> usedCountries = new HashSet<>();
    private String getAddCountryJSON(CashRegisterItemBatch batch) throws JSONException {
        if(batch.countryCode != null && !usedCountries.contains(batch.countryCode)) {
            usedCountries.add(batch.countryCode);
            JSONObject rootObject = new JSONObject();
            JSONObject medicineObject = new JSONObject();
            rootObject.put("country", medicineObject);
            medicineObject.put("code", batch.countryCode);
            medicineObject.put("name", batch.countryName);
            rootObject.put("command", "addCountry");
            return rootObject.toString();
        } else return null;
    }

    private String getAddMedicineJSON(CashRegisterItem item, CashRegisterItemBatch batch, boolean appendBarcode) throws JSONException {
        JSONObject rootObject = new JSONObject();
        JSONObject medicineObject = new JSONObject();
        rootObject.put("medicine", medicineObject);
        medicineObject.put("code", batch.idBatch);
        medicineObject.put("party", batch.dateBatch);
        //medicineObject.put("supplydate", batch.dateBatch); //todo: раскомментить, когда будет готов их сервер
        medicineObject.put("barcode", removeCheckDigitFromBarcode(item.mainBarcode, appendBarcode));
        medicineObject.put("shelflife", batch.expiryDate);
        medicineObject.put("series", batch.seriesPharmacy);
        medicineObject.put("producer", batch.nameManufacturer);
        medicineObject.put("price", batch.price);
        medicineObject.put("inn", batch.nameSubstance);
        medicineObject.put("cquant", nvl(batch.blisterAmount, 1));
        //medicineObject.put("packquant", batch.balance);  //todo: раскомментить, когда будет готов их сервер
        medicineObject.put("remainquant", batch.balanceBlister);
        medicineObject.put("remaindatetime", batch.balanceDate);
        medicineObject.put("countrycode", batch.countryCode);
        medicineObject.put("options", batch.flag);
        rootObject.put("command", "addMedicine");
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

    private String getAddTaxGroup(CashRegisterItem item) throws JSONException {
        if (item.vat != null) {
            JSONObject rootObject = new JSONObject();

            JSONObject taxGroupObject = new JSONObject();
            rootObject.put("taxGroup", taxGroupObject);
            taxGroupObject.put("idTaxGroup", item.vat.intValue());

            JSONArray taxesArray = new JSONArray();
            JSONObject tax = new JSONObject();
            tax.put("changebase", false);
            tax.put("name", item.vat + "%");
            tax.put("rate", item.vat.doubleValue());
            taxesArray.put(tax);

            taxGroupObject.put("taxes", taxesArray);
            rootObject.put("command", "addTaxGroup");
            return rootObject.toString();
        } else return null;
    }

    private String getAddUnitJSON(CashRegisterItem item) throws JSONException {
        Integer idUOM = parseUOM(item.idUOM);
        if (idUOM != null) {
            JSONObject rootObject = new JSONObject();

            JSONObject inventGroupObject = new JSONObject();
            rootObject.put("unit", inventGroupObject);
            inventGroupObject.put("unitCode", idUOM); //код единицы измерения
            inventGroupObject.put("name", item.shortNameUOM); //наименование единицы измерения

            Integer blisterAmount = getMaxBlisterAmount(item);
            inventGroupObject.put("fractional", blisterAmount != null ? blisterAmount.compareTo(1) > 0 : item.splitItem); //дробная единица измерения: true весовой, false штучный
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

    private String getAddTmcScaleJSON(TransactionCashRegisterInfo transaction, CashRegisterItem item) throws JSONException {
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

        tmcScaleObject.put("produceddate", item.manufactureDays); //Дата производства, в сутках, вычитается из даты упаковки, Дата упаковки - текущая дата взвешивания и печати этикетки
        tmcScaleObject.put("sellbydate", item.daysExpiry); //Срок годности, в сутках, прибавляется к дате упаковки
        tmcScaleObject.put("sellbytime", item.hoursExpiry); //Срок годности, в часах, прибавляется к дате упаковки

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

        if(d.extInfo != null) {
            JSONObject infoJSON = new JSONObject(d.extInfo).optJSONObject("artix");
            if (infoJSON != null) {
                try {
                    cardGroupObject.put("cardmode", infoJSON.getInt("cardmode"));
                } catch (Exception ignored) {
                }
            }
        }

        cardGroupObject.put("notaddemptycard", true);
        cardGroupObject.put("pattern", "[0-9]");
        cardGroupObject.put("inputmask", 7); //маска способа ввода карты
        rootObject.put("command", "addCardGroup");
        return rootObject.toString();
    }

    private String getAddChangeCardAccountJSON(DiscountCard d) throws JSONException {
        JSONObject rootObject = new JSONObject();

        JSONObject changeCardAccountObject = new JSONObject();
        rootObject.put("ChangeCardAccount", changeCardAccountObject);
        changeCardAccountObject.put("cardNumber", d.numberDiscountCard);
        changeCardAccountObject.put("accountnumber", d.numberDiscountCard);
        changeCardAccountObject.put("cardstatus", "EARN_ONLY");

        rootObject.put("command", "addChangeCardAccount");
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
        if(card.birthdayContact != null && card.birthdayContact.compareTo(LocalDate.of(1900, 1, 1)) > 0 )
            clientObject.put("birthday", card.birthdayContact.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))); //день рождения, год рождения должен быть больше 1900
        rootObject.put("command", "addClient");
        return rootObject.toString();
    }

    private void waitForDeletion(File file, File flagFile, int timeout) {
        int count = 0;
        while (!Thread.currentThread().isInterrupted() && (file.exists() || flagFile.exists())) {
            try {
                count++;
                if (count >= timeout)
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
                        machineryExchangeLogger.info(logPrefix + "creating request file for directory: " + directory);
                        if (new File(directory).exists() || new File(directory).mkdirs()) {
                            String dateFrom = entry.dateFrom.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                            String dateTo = entry.dateTo.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

                            File reqFile = new File(directory + "/sale.req");
                            if (!reqFile.exists()) {
                                Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(reqFile), StandardCharsets.UTF_8));
                                String data = String.format("###\n%s-%s", dateFrom, dateTo);
                                writer.write(data);
                                writer.close();
                                machineryExchangeLogger.info(logPrefix + "created request file for directory: " + directory);
                            } else {
                                machineryExchangeLogger.error(logPrefix + "request file already exists: " + reqFile.getAbsolutePath());
                            }
                        } else {
                            machineryExchangeLogger.error(logPrefix + "failed to create directory: " + directory);
                            failedRequests.put(entry.requestExchange, new RuntimeException("Failed to create directory " + directory));
                        }
                    } catch (Exception e) {
                        machineryExchangeLogger.error("Exception while creating sale.req in directory " + directory, e);
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
    public Map<String, LocalDateTime> requestSucceededSoftCheckInfo(List<String> directoryList) {

        ArtixSettings artixSettings = springContext.containsBean("artixSettings") ? (ArtixSettings) springContext.getBean("artixSettings") : null;
        Integer maxFilesCount = artixSettings == null ? null : artixSettings.getMaxFilesCount();
        int priorityDirectoriesCount = artixSettings == null ? 0 : artixSettings.getPriorityDirectoriesCount();
        boolean disableSoftCheck = artixSettings != null && artixSettings.isDisableSoftCheck();

        Map<String, LocalDateTime> result = new HashMap<>();
        softCheckLogger.info(logPrefix + "reading SoftCheckInfo");

        //ArtixSettings artixSettings = springContext.containsBean("artixSettings") ? (ArtixSettings) springContext.getBean("artixSettings") : null;
        //boolean disable = artixSettings != null && artixSettings.isDisableCopyToSuccess();

        List<Pair<File, Boolean>> files = new ArrayList<>();
        //правильнее брать только только нужные подпапки, как в readSales, но для этого пришлось бы менять equ-api.
        //так что пока ищем во всех подпапках + в подпапках в папке online.
        //потенциальная проблема - левые файлы, а также файлы из папок выключенных касс
        for (Pair<File, Boolean> dirEntry : getSoftCheckDirectories(directoryList, priorityDirectoriesCount)) {
            File dir = dirEntry.first;
            boolean priority = dirEntry.second;
            File[] filesList = dir.listFiles(pathname -> pathname.getName().startsWith("sale") && pathname.getPath().endsWith(".json"));
            if (filesList != null) {
                for (File file : filesList) {
                        files.add(Pair.create(file, priority));
                }
            }
        }

        files.sort((f1, f2) -> {
            if (f1.second) return f2.second ? compareDates(f1.first, f2.first) : -1; //f2 is not priority
            if (f2.second) return 1; //f1 is not priority
            return compareDates(f1.first, f2.first);
        });

        int totalFilesCount = files.size();

        if(maxFilesCount == null || maxFilesCount > totalFilesCount)
            maxFilesCount = totalFilesCount;
        List<File> subFiles = new ArrayList<>();
        for(int i = 0; i < maxFilesCount; i++) {
            subFiles.add(files.get(i).first);
        }

        readFiles = new HashSet<>(subFiles);

        if(!disableSoftCheck) {

            if (subFiles.isEmpty()) softCheckLogger.info(logPrefix + "No sale files found");
            else {
                softCheckLogger.info(String.format(logPrefix + "found %s sale file(s), read %s sale file(s)", totalFilesCount, subFiles.size()));

                for (File file : subFiles) {
                    if (!Thread.currentThread().isInterrupted()) {
                        try {

                            String fileName = file.getAbsolutePath();
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
                                            Long timeEnd = parseDateTime(documentObject.get("timeEnd"));
                                            Timestamp dateTimeReceipt = timeEnd != null ? new Timestamp(timeEnd) : null;
                                            JSONArray inventPositionsArray = documentObject.getJSONArray("inventPositions");
                                            for (int i = 0; i < inventPositionsArray.length(); i++) {
                                                JSONObject inventPosition = inventPositionsArray.getJSONObject(i);
                                                String invoiceNumber = inventPosition.getString("additionalbarcode");
                                                invoiceNumber = invoiceNumber.length() >= 7 ? invoiceNumber.substring(invoiceNumber.length() - 7) : invoiceNumber;
                                                softCheckLogger.info(logPrefix + "found softCheck " + invoiceNumber);
                                                result.put(invoiceNumber, sqlTimestampToLocalDateTime(dateTimeReceipt));
                                            }
                                        }
                                    }
                                }
                            }
//                          copyToSuccess(file, disable);
//                          safeFileDelete(file, false);
                        } catch (Throwable e) {
                            softCheckLogger.error("File: " + file.getAbsolutePath(), e);
                            throw new RuntimeException(logPrefix + "failed to parse " + file.getAbsolutePath(), e);
                        }
                    }
                }
            }

        }
        return result;
    }

    private int compareDates(File f1, File f2) {
        return Long.compare(f1.lastModified(), f2.lastModified());
    }

    private List<Pair<File, Boolean>> getSoftCheckDirectories(List<String> directories, int priorityDirectoriesCount) {

        List<Pair<File, Boolean>> result = new ArrayList<>();
        for(int i = 0; i < directories.size(); i++) {
            String directory = directories.get(i);
            boolean priority = i < priorityDirectoriesCount;
            if(directory != null) {
                File[] subDirectoryList = new File(directory).listFiles(File::isDirectory);
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
                File[] filesList = new File(directory).listFiles(pathname -> pathname.getName().startsWith("sale") && pathname.getPath().endsWith(".json"));

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

                                            int docType = documentObject.getInt("docType");
                                            boolean in = docType == 3;
                                            boolean out = docType == 4;
                                            if (in || out) {

                                                int numberCashDocument = documentObject.getInt("docNum");
                                                String idEmployee = documentObject.getString("userCode");
                                                int shift = documentObject.getInt("shift");

                                                BigDecimal sumCashDocument = BigDecimal.valueOf(documentObject.getDouble("docSum"));
                                                sumCashDocument = in ? sumCashDocument : safeNegate(sumCashDocument);

                                                Integer numberCashRegister = Integer.parseInt(documentObject.getString("cashCode"));

                                                Long timeEnd = parseDateTime(documentObject.get("timeEnd"));
                                                LocalDate dateCashDocument = timeEnd != null ? sqlDateToLocalDate(new Date(timeEnd)) : null;
                                                LocalTime timeCashDocument = timeEnd != null ? sqlTimeToLocalTime(new Time(timeEnd)) : null;

                                                if (cashRegister.number.equals(numberCashRegister)) {
                                                    if (cashRegister.startDate == null || (dateCashDocument != null && dateCashDocument.compareTo(cashRegister.startDate) >= 0)) {
                                                        String idCashDocument = cashRegister.numberGroup + "/" + numberCashRegister + "/" + numberCashDocument + "/" + shift + "/" + docType;
                                                        cashDocumentList.add(new CashDocument(idCashDocument, String.valueOf(numberCashDocument), dateCashDocument, timeCashDocument,
                                                                cashRegister.numberGroup, numberCashRegister, String.valueOf(shift), sumCashDocument, idEmployee));
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
                Integer timeout = artixSettings == null || artixSettings.getTimeout() == null ? 180 : artixSettings.getTimeout();
                if(globalExchangeDirectory != null) {
                    if (new File(globalExchangeDirectory).exists() || new File(globalExchangeDirectory).mkdirs()) {
                        machineryExchangeLogger.info(String.format(logPrefix + "Send DiscountCards to %s", globalExchangeDirectory));

                        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
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
                            if(d.extInfo != null) {
                                JSONObject infoJSON = new JSONObject(d.extInfo).optJSONObject("artix");
                                if (infoJSON != null) {
                                    if(infoJSON.optBoolean("ChangeCardAccount")) {
                                        writeStringToFile(tmpFile, getAddChangeCardAccountJSON(d) + "\n---\n");
                                    }
                                }
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
                        waitForDeletion(file, flagFile, timeout);
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
                String directory = file.getParent() + "/success-" + formatDate(LocalDate.now()) + "/";
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
        Integer timeout = artixSettings == null || artixSettings.getTimeout() == null ? 180 : artixSettings.getTimeout();
        String globalExchangeDirectory = artixSettings != null ? artixSettings.getGlobalExchangeDirectory() : null;
        if (globalExchangeDirectory != null) {
            File directory = new File(globalExchangeDirectory);
            if (directory.exists() || directory.mkdirs()) {
                try {

                    File tmpFile = File.createTempFile("pos", ".aif");

                    String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
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
                    waitForDeletion(file, flagFile, timeout);

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
    public ArtixSalesBatch readSalesInfo(String directory, List<CashRegisterInfo> cashRegisterInfoList) {

        ArtixSettings artixSettings = springContext.containsBean("artixSettings") ? (ArtixSettings) springContext.getBean("artixSettings") : new ArtixSettings();
        boolean appendBarcode = artixSettings.isAppendBarcode();
        boolean bonusesInDiscountPositions = artixSettings.isBonusesInDiscountPositions();
        boolean giftCardPriceInCertificatePositions = artixSettings.isGiftCardPriceInCertificatePositions();
        boolean notDeleteEmptyFiles = artixSettings.isNotDeleteEmptyFiles();
        Set<Integer> cashPayments = parsePayments(artixSettings.getCashPayments());
        Set<Integer> cardPayments = parsePayments(artixSettings.getCardPayments());
        Set<Integer> giftCardPayments = parsePayments(artixSettings.getGiftCardPayments());
        Set<Integer> customPayments = parsePayments(artixSettings.getCustomPayments());
        int externalSumType = artixSettings.getExternalSumType();
        boolean medicineMode = artixSettings.isMedicineMode();
        boolean receiptIdentifiersToExternalNumber = artixSettings.isReceiptIdentifiersToExternalNumber();

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
            File[] filesList = new File(dir).listFiles(pathname -> pathname.getName().startsWith("sale") && pathname.getPath().endsWith(".json"));
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

                        Map<String, Map<String, Object>> externalSumMap = new HashMap<>();
                        List<ShiftInfo> shiftList = new ArrayList<>();
                        Map<String, String> cashiersMap = new HashMap();
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

                                    JSONArray kkms = documentObject.getJSONArray("kkms");
                                    BigDecimal sumCashEnd = null;
                                    BigDecimal sumProtectedBeg = null;
                                    BigDecimal sumProtectedEnd = null;
                                    BigDecimal sumBack = null;
                                    for (int i = 0; i < kkms.length(); i++) {
                                        JSONObject kkmsObject = kkms.getJSONObject(i);
                                        sumCashEnd = getBigDecimal(kkmsObject, "sumCashEnd");
                                        sumProtectedBeg = getBigDecimal(kkmsObject, "sumProtectedBeg");
                                        sumProtectedEnd = getBigDecimal(kkmsObject, "sumProtectedEnd");
                                        sumBack = getBigDecimal(kkmsObject, "sumBack");
                                    }

                                    Long timeBeg = parseDateTime(documentObject.get("timeBeg"));
                                    Long timeEnd = parseDateTime(documentObject.get("timeEnd"));
                                    if (timeBeg != null) {
                                        Map<String, Object> zReportExtraFields = new HashMap<>();
                                        zReportExtraFields.put("sumCashEnd", sumCashEnd);
                                        zReportExtraFields.put("sumProtectedEnd", sumProtectedEnd);
                                        zReportExtraFields.put("sumBack", sumBack);
                                        BigDecimal externalSum = externalSumType == 0 ? sumGain : safeSubtract(safeSubtract(sumProtectedEnd, sumProtectedBeg), sumBack);
                                        zReportExtraFields.put("externalSum", externalSum);
                                        externalSumMap.put(numberCashRegister + "/" + numberZReport, zReportExtraFields);
                                        shiftList.add(new ShiftInfo(numberCashRegister, numberZReport, timeBeg, timeEnd));
                                    }

                                    JSONArray users = documentObject.optJSONArray("users");
                                    if(users != null) {
                                        for(int i = 0; i < users.length(); i++) {
                                            JSONObject user = users.getJSONObject(i);
                                            String userCode = user.optString("usercode");
                                            String userName = user.optString("username");
                                            if(userCode != null && userName != null) {
                                                cashiersMap.put(userCode, userName);
                                            }
                                        }
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
                                    boolean isSaleInvoice = docType == 18; //Импортируем как продажу, записываем json в receiptDetailExtraFields
                                    boolean isSale = docType == 1 || docType == 8 || isSaleInvoice; //sale or sale cancellation
                                    boolean isReturn = docType == 2 || docType == 25 || docType == 7; //return or return cancellation
                                    boolean isSkip = docType == 7 || docType == 8 || isSaleInvoice;
                                    if (isSale || isReturn) {

                                        Integer numberCashRegister = Integer.parseInt(documentObject.getString("cashCode"));
                                        String numberZReport = String.valueOf(documentObject.getInt("shift"));
                                        Integer numberReceipt = documentObject.getInt("docNum");
                                        String idEmployee = documentObject.getString("userCode");
                                        String nameEmployee = cashiersMap.get(idEmployee);

                                        String identifier = documentObject.optString("identifier");
                                        String sourceIdentifier = documentObject.optString("sourceidentifier");

                                        Long timeEnd = parseDateTime(documentObject.get("timeEnd"));
                                        LocalDate dateReceipt = timeEnd != null ? sqlDateToLocalDate(new Date(timeEnd)) : null;
                                        Time timeReceipt = timeEnd != null ? new Time(timeEnd) : null;

                                        Long dateTimeShift = getDateTimeShiftByReceipt(shiftList, numberCashRegister, numberZReport, timeEnd);
                                        LocalDate dateZReport = dateTimeShift == null ? dateReceipt : sqlDateToLocalDate(new Date(dateTimeShift));
                                        Time timeZReport = dateTimeShift == null ? timeReceipt : new Time(dateTimeShift);

                                        BigDecimal sumCard = BigDecimal.ZERO;
                                        BigDecimal sumCash = BigDecimal.ZERO;
                                        BigDecimal sumGiftCard = BigDecimal.ZERO;
                                        Map<String, GiftCard> sumGiftCardMap = new HashMap<>();
                                        Map<String, BigDecimal> customPaymentsMap = new HashMap<>();

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
                                            sum = (sum != null && !isSale) ? sum.negate() : sum;

                                            if (paymentType != null && ((isSale && operationCode.equals(70)) || (isReturn && (operationCode.equals(74) || operationCode.equals(100))))) {

                                                if(customPayments.contains(paymentType)) {
                                                    BigDecimal customPaymentSum = customPaymentsMap.get(String.valueOf(paymentType));
                                                    customPaymentsMap.put(String.valueOf(paymentType), safeAdd(customPaymentSum, sum));
                                                } else {
                                                    if (cashPayments.contains(paymentType)) //нал
                                                        paymentType = 1;
                                                    else if (cardPayments.contains(paymentType)) //безнал
                                                        paymentType = 4;
                                                    else if (giftCardPayments.contains(paymentType)) //сертификат
                                                        paymentType = 6;


                                                    switch (paymentType) {
                                                        case 4:
                                                            sumCard = HandlerUtils.safeAdd(sumCard, sum);
                                                            break;
                                                        case 6:
                                                        case 7:
                                                            String certificate = BaseUtils.trimToNull(moneyPosition.optString("cardnum"));
//                                                            String numberGiftCard = certificate != null && certificate.length() >= 11 ? appendCheckDigitToBarcode(certificate, 11, appendBarcode) : certificate;
//                                                          пока отключаем, так как в bcode_main нет контрольной цифры
                                                            String numberGiftCard = certificate;
                                                            BigDecimal price = certificatePriceMap.get(certificate);
                                                            price = sum != null && price != null ? price : sum;
                                                            if (sumGiftCardMap.containsKey(numberGiftCard)) { // пока вот такой чит, так как в cardnum бывают пустые значения
                                                                sumGiftCardMap.get(numberGiftCard).sum = HandlerUtils.safeAdd(sumGiftCardMap.get(numberGiftCard).sum, sum);
                                                                sumGiftCardMap.get(numberGiftCard).price = HandlerUtils.safeAdd(sumGiftCardMap.get(numberGiftCard).price, price);
                                                            } else
                                                                sumGiftCardMap.put(numberGiftCard, new GiftCard(sum, price));
                                                            break;
                                                        case 1:
                                                        default:
                                                            sumCash = HandlerUtils.safeAdd(sumCash, sum);
                                                            break;
                                                    }
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
                                            String barcodeString = BaseUtils.trimToNull(inventPosition.getString("barCode"));
                                            int opCode = inventPosition.getInt("opCode");

                                            // вот такой вот чит из-за того, что могут ввести код товара в кассе
                                            String barcode = idItem != null && idItem.equals(barcodeString) ? null :
                                                    appendCheckDigitToBarcode(barcodeString, 7, appendBarcode);

                                            //обнаруживаем продажу сертификатов
                                            boolean isGiftCard = false;
                                            if (opCode == 63) {
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

                                            BigDecimal bonusSum = null;
                                            JSONArray bonusPositionsArray = inventPosition.getJSONArray("bonusPositions");
                                            for (int j = 0; j < bonusPositionsArray.length(); j++) {
                                                JSONObject bonusPosition = bonusPositionsArray.getJSONObject(j);
                                                BigDecimal amount = bonusPosition.getBigDecimal("amount");
                                                if(amount != null)
                                                    bonusSum = safeAdd(bonusSum, amount);
                                            }

                                            BigDecimal bonusPaid = null;
                                            JSONArray discountPositionsArray = inventPosition.getJSONArray("discountPositions");
                                            for (int j = 0; j < discountPositionsArray.length(); j++) {
                                                JSONObject discountPosition = discountPositionsArray.getJSONObject(j);
                                                String discType = discountPosition.getString("discType");
                                                if(discType != null && discType.equals("bonus"))
                                                    bonusPaid = safeAdd(bonusPaid, discountPosition.getBigDecimal("discSum"));
                                            }

                                            String externalNumber;
                                            if(receiptIdentifiersToExternalNumber) {
                                                externalNumber = isSale ? identifier : (sourceIdentifier + "/" + inventPosition.optString("posNum"));
                                            } else {
                                                externalNumber = trimToNull(inventPosition.optString("extdocid"));
                                            }

                                            String extendedOptions = trimToNull(inventPosition.optString("extendetoptions"));

                                            if(bonusesInDiscountPositions && bonusPaid != null) {
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
                                                    BigDecimal discSize = null;
                                                    String discType = discountPosition.getString("discType");
                                                    if(discType.equals("summ")) {
                                                        //рассчитываем процент вручную
                                                        BigDecimal discSum = BigDecimal.valueOf(discountPosition.getDouble("discSum"));
                                                        BigDecimal checkSum = BigDecimal.valueOf(discountPosition.getDouble("checkSum"));
                                                        discSize = safeMultiply(safeDivide(discSum, checkSum), 100);
                                                    } else if (!discType.equals("bonus")) {
                                                        discSize = BigDecimal.valueOf(discountPosition.getDouble("discSize"));
                                                    }
                                                    discountPercentReceiptDetail = safeAdd(discountPercentReceiptDetail, discSize);
                                                }
                                            }

                                            String idBatch = null;
                                            if(medicineMode) {
                                                JSONObject medicine = inventPosition.optJSONObject("medicine");
                                                idBatch = medicine != null ? medicine.optString("code") : null;
                                                if (idBatch != null && !idBatch.isEmpty())
                                                    idBatch = StringUtils.leftPad(idBatch, 10, "0");
                                            }

                                            sumReceiptDetail = isSale ? sumReceiptDetail : safeNegate(sumReceiptDetail);

                                            CashRegisterInfo cashRegister = departNumberCashRegisterMap.get(numberCashRegister);
                                            if (cashRegister == null)
                                                sendSalesLogger.error(logPrefix + String.format("CashRegister %s not found (file %s)", numberCashRegister, file.getAbsolutePath()));
                                            Integer nppGroupMachinery = cashRegister == null ? null : cashRegister.numberGroup;
                                            LocalDate startDate = cashRegister == null ? null : cashRegister.startDate;
                                            if (startDate == null || dateReceipt.compareTo(startDate) >= 0) {
                                                if (sumGiftCard.compareTo(BigDecimal.ZERO) != 0)
                                                    sumGiftCardMap.put(null, new GiftCard(sumGiftCard));

                                                Map<String, Object> receiptDetailExtraFields = new HashMap<>();
                                                if(isSaleInvoice) {
                                                    //as in equ-srv
                                                    receiptDetailExtraFields.put("idReceipt", nppGroupMachinery + "_" + numberCashRegister + "_" + numberZReport + "_" + dateZReport.format(DateTimeFormatter.ofPattern("ddMMyyyy")) + "_" + numberReceipt);
                                                }
                                                if(extendedOptions != null) {
                                                    receiptDetailExtraFields.put("extendedOptions", extendedOptions);
                                                }

                                                SalesInfo salesInfo = getSalesInfo(isGiftCard, false, nppGroupMachinery, numberCashRegister, numberZReport,
                                                        dateZReport, sqlTimeToLocalTime(timeZReport), numberReceipt, dateReceipt, sqlTimeToLocalTime(timeReceipt), idEmployee, nameEmployee, null,
                                                        sumCard, sumCash, sumGiftCardMap, customPaymentsMap, barcode, idItem, null, null, quantity, price, sumReceiptDetail,
                                                        discountPercentReceiptDetail, discountSumReceiptDetail, null, seriesNumberDiscountCard,
                                                        numberReceiptDetail, fileName, null, isSkip, receiptDetailExtraFields, cashRegister);
                                                salesInfo.detailExtraFields = new HashMap<>();
                                                if(!bonusesInDiscountPositions) {
                                                    salesInfo.detailExtraFields.put("bonusSum", bonusSum);
                                                    salesInfo.detailExtraFields.put("bonusPaid", bonusPaid);
                                                }
                                                salesInfo.detailExtraFields.put("idBatch", idBatch);
                                                salesInfo.detailExtraFields.put("externalNumber", externalNumber);
                                                currentSalesInfoList.add(salesInfo);
                                            }
                                        }
                                    }
                                }
                            }
                            if(!externalSumMap.isEmpty()) {
                                for(SalesInfo salesInfo : currentSalesInfoList) {
                                    salesInfo.zReportExtraFields = externalSumMap.get(salesInfo.nppMachinery + "/" + salesInfo.numberZReport);
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
                        break;
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
        } else if(object instanceof Double) {
            return BigDecimal.valueOf((Double) object);
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
                    int opcode = documentObject.getInt("opcode");
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
                                result.add(new CashierTime(idCashierTime, numberCashier, numberCashRegister, nppGroupMachinery, sqlTimestampToLocalDateTime(logOnCashier), sqlTimestampToLocalDateTime(logOffCashier), true));
                                logOnCashier = null;
                            }
                            break;
                    }
                }
            }
        }
        if(!result.isEmpty())
            sendSalesLogger.info(logPrefix + "found " + result.size() + " CashierTime(s) in " + file.getName());
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

    private Long parseDateTime(Object value) throws ParseException {
        return value instanceof String && !value.equals("null") ? new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse((String) value).getTime() : null;
    }

    private String formatTimestamp(Timestamp date) {
        return date == null ? null : new SimpleDateFormat("dd.MM.yyyy H:mm:ss").format(date);
    }

    private String formatDate(LocalDate value) {
        return value == null ? null : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
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
                        checkSum += Integer.parseInt(String.valueOf(barcode.charAt(i))) * 3;
                        checkSum += i == 6 ? 0 : Integer.parseInt(String.valueOf(barcode.charAt(i + 1)));
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
            checkSum += Integer.parseInt(String.valueOf(barcode.charAt(i)));
            checkSum += Integer.parseInt(String.valueOf(barcode.charAt(i + 1))) * 3;
        }
        checkSum %= 10;
        if (checkSum != 0)
            checkSum = 10 - checkSum;
        return barcode.concat(String.valueOf(checkSum));
    }

    private Long getDateTimeShiftByReceipt(List<ShiftInfo> shiftList, Integer numberCashRegister, String numberZReport, Long dateTimeReceipt) {
        //ищем по вхождению в интервал
        for(ShiftInfo shift : shiftList) {
            if(shift.numberCashRegister.equals(numberCashRegister) && shift.numberZReport.equals(numberZReport)
                    && shift.from <= dateTimeReceipt && (shift.to == null || shift.to >= dateTimeReceipt)) {
                return shift.from;
            }
        }
        //ищем просто по кассе и номеру
        for(ShiftInfo shift : shiftList) {
            if(shift.numberCashRegister.equals(numberCashRegister) && shift.numberZReport.equals(numberZReport)) {
                return shift.from;
            }
        }
        return null;
    }

    private class ShiftInfo {
        Integer numberCashRegister;
        String numberZReport;
        Long from;
        Long to;

        public ShiftInfo(Integer numberCashRegister, String numberZReport, Long from, Long to) {
            this.numberCashRegister = numberCashRegister;
            this.numberZReport = numberZReport;
            this.from = from;
            this.to = to;
        }
    }
}