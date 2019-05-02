package equ.clt.handler.dreamkas;

import equ.api.SalesInfo;
import equ.api.ZReportInfo;
import equ.api.cashregister.CashDocument;
import equ.api.cashregister.CashRegisterInfo;
import equ.api.cashregister.CashRegisterItemInfo;
import equ.api.cashregister.TransactionCashRegisterInfo;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.math.BigDecimal.ROUND_DOWN;

public class DreamkasServer {
    public String token = "";                                                   // Токен доступа
    public String baseURL = "";                                                 // Базовый URL
    public String cResult = "";                                                 // Результат запроса
    public String eMessage = "";                                                // Текст ошибки
    public Integer webStatus = 0;                                               // Статус выполнения команды
    public String logMessage = "";                                              // Текст для лог файла
    public Integer salesDays = 10;                                              // Кол-во дней принимаемой реализации
    public Integer salesLimitReceipt = 500;                                     // Кол-во чеков за 1 запрос (200..1000)
    public List<CashRegisterInfo> cashRegisterInfoList;                         // Список обрабатываемых устройств
    public List<SalesInfo> salesInfoList = null;                                // Строки принятой реализацииж
    public List<CashDocument> cashDocList = null;                               // Кассовые документы (внесения, изъятия)
    private ArrayList<String> aPriceList = new ArrayList<>();             // Массив частей JSON для выполнения PATCH
//    private Map<String, DtShift> hmShift = new HashMap<String, DtShift>();    // Map deviceId_shift:объект даты и времени

    //  --- Основной метод загрузки кассового сервера новыми товарами (POST) или обновление товаров (PATCH)
    public boolean sendPriceList(TransactionCashRegisterInfo transaction) throws IOException {
        if (transaction.itemsList == null) return true;
        // Очистка БД товаров, если snapshot
        if (transaction.snapshot) {
            if (!deleteAllPLU()) return false;
        }
        // Вначале выполняем POST - считаем, что все товары новые. Готовим текст JSON
        prepPriceList(transaction);
        cResult = "";
        for (int i = 0; i < aPriceList.size(); i++) {
            if (cResult.length() > 0) cResult += ",";
            cResult += aPriceList.get(i);
        }
        if (cResult.length() == 0)
            return errBox("POST: Отправляемый пакет не содержит данных", "", "", false);
        if (!webExec("POST", "products", "[" + cResult + "]"))
            return errBox("Товары не были переданы на сервер", eMessage, "", false); // Ошибки WEB
        // Может быть одиночная ошибка (webStatus!=200) или группа ошибок (webStatus=200) - разные структуры Json
        // Если webStatus > 2xx, то ответ может содержаться в разных структурах Json, поэтому не парсируем
        if (!(webStatus == 200))
            return errBox("Товары не переданы на сервер", getErrorWeb(webStatus), cResult, true);
        JsonReadProcess oJs = new JsonReadProcess();
        if (!oJs.load(cResult))
            return errBox("Ошибка парсинга ответа от WEB", oJs.eMessage, cResult, true);
        if (!oJs.getPathJson("errors"))
            return errBox("Ошибка парсинга ответа от WEB", oJs.eMessage, cResult, true);
        if (!oJs.load(oJs.cResult))
            return errBox("Ошибка парсинга ответа от WEB", oJs.eMessage, cResult, true);
        if (oJs.nSize == 0) return true; // Ошибок нет, все записалось
        if (!oJs.getKeys())
            return errBox("Ошибка парсинга ответа от WEB", oJs.eMessage, cResult, true);
        // Формируем пакет для метода PATCH

        String prevCResult = cResult;
        String lastMsg = "";
        cResult = "";
        String lPart, rPart;
        for (String cPart : oJs.cResult.split(",")) {
            lPart = cPart.split(":")[0];
            rPart = cPart.split(":")[1];
            if (lPart.equals("Object")) {
                oJs.getPathValue(rPart + ".data.errors[0].message");
                lastMsg = oJs.cResult;
                if (oJs.cResult.equals("id must be unique")) {
                    if (cResult.length() > 0) cResult += ",";
                    cResult += aPriceList.get(Integer.parseInt(rPart));
                }
            }
        }
        if (cResult.length() == 0)
            return errBox("Пакет для PATCH не содержит данных. Товары не переданы на сервер.", lastMsg, prevCResult, true);
        if (!webExec("PATCH", "products", "[" + cResult + "]"))
            return errBox(eMessage, "Товары не переданы на сервер", cResult, true); // Ошибки WEB
        if (webStatus == 204) return true;
        return errBox(getErrorWeb(webStatus), "Товары не были переданы на сервер", cResult, true);
    }

    //  --- Основной метод чтения реализации
    public boolean getSales() {
//        if (!getShifts()) return false;
        String url;
        Integer offset = 0;
        Boolean lRet = true;
        url = "receipts?" + getRangeDate() + "&limit=" + salesLimitReceipt.toString();
        salesInfoList = new ArrayList<>();
        JsonReadProcess oJs = new JsonReadProcess();
        while (true) {
            if (!webExec("GET", url + "&offset=" + offset.toString(), "")) {
                lRet = false;
                break;
            }
            if (!(webStatus == 200)) {
                lRet = errBox("Ошибка приема реализации", getErrorWeb(webStatus), cResult, true);
                break;
            }
            offset += salesLimitReceipt;
            if (!oJs.load(cResult)) {
                lRet = errBox("Ошибка JSON структуры принятой реализации", "", cResult, true);
                break;
            }
            oJs.getKeys();
            if (!oJs.cResult.contains("data")) {
                lRet = errBox("Ошибка JSON структуры принятой реализации", "", cResult, true);
                break;
            }
            // Обработка чеков из пришедшего пакета
            oJs.getArraySize("data");
            if (oJs.nCount == 0) break;
            for (int i = 0; i < oJs.nCount; ++i) {
                oJs.getPathJson("data[" + Integer.toString(i) + "]");
                parseReceipt(oJs.cResult);
            }
        }
        return lRet;
    }

    // ----- Основной метод чтения документов кассы
    public boolean getDocInfo() {
        return readDocInfo();
    }

    // чтение кассовых документов
    private boolean readDocInfo() {
        String url;
        Integer offset = 0;
        Boolean lRet = true;
        url = "encashments?" + getRangeDate() + "&limit=" + salesLimitReceipt.toString();
        cashDocList = new ArrayList<>();
        JsonReadProcess oJs = new JsonReadProcess();
        while (true) {
            if (!webExec("GET", url + "&offset=" + offset.toString(), "")) {
                lRet = false;
                break;
            }
            if (!(webStatus == 200)) {
                lRet = errBox("Ошибка приема кассовых документов", getErrorWeb(webStatus), cResult, true);
                break;
            }
            offset += salesLimitReceipt;
            if (!oJs.load(cResult)) {
                lRet = errBox("Ошибка JSON структуры кассовых документов", "", cResult, true);
                break;
            }
            oJs.getKeys();
            if (!oJs.cResult.contains("data")) {
                lRet = errBox("Ошибка JSON структуры кассовых документов", "", cResult, true);
                break;
            }
            // Обработка документов из пришедшего пакета
            oJs.getArraySize("data");
            if (oJs.nCount == 0) break;
            for (int i = 0; i < oJs.nCount; ++i) {
                oJs.getPathJson("data[" + Integer.toString(i) + "]");
                parseDocInfo(oJs.cResult);
            }
        }
        return lRet;
    }

    //  Метод опроса номеров смен - метод пока не используется
    private boolean getShifts() {
        String url;
        Integer offset = 0;
        Integer limit = 500;
        Boolean lRet = true;
        url = "shifts?" + getRangeDate() + "&limit=" + limit.toString();
        JsonReadProcess oJs = new JsonReadProcess();
        while (true) {
            if (!webExec("GET", url + "&offset=" + offset.toString(), "")) {
                lRet = false;
                break;
            }
            if (!(webStatus == 200)) {
                lRet = errBox("Ошибка чтения смен", getErrorWeb(webStatus), cResult, true);
                break;
            }
            offset += limit;
            if (!oJs.load(cResult)) {
                lRet = errBox("Ошибка JSON структуры массива смен", "", cResult, true);
                break;
            }
            if (!oJs.tResult.equals("Array")) {
                lRet = errBox("Ошибка JSON структуры массива смен", "", cResult, true);
                break;
            }
            oJs.getKeys();
            if (!oJs.cResult.contains("data")) {
                lRet = errBox("Ошибка структуры принятой реализации", "", cResult, true);
                break;
            }
            // Обработка чеков из пришедшего пакета
            oJs.getArraySize("data");
            if (oJs.nCount == 0) break;
            for (int i = 0; i < oJs.nCount; ++i) {
                oJs.getPathJson("data[" + Integer.toString(i) + "]");
                parseReceipt(oJs.cResult);
            }
        }
        return lRet;
    }

    //  Чтение чека
    private void parseReceipt(String cJson) {
        int iPos;
        Integer i, iMax;
        JsonReadProcess oHeader = new JsonReadProcess(); // Парсировщик шапки чека
        JsonReadProcess oLines = new JsonReadProcess();   // Парсировщик строк чека
        oHeader.load(cJson);
        oHeader.getPathValue("deviceId");
        iPos = chkDeviceId(oHeader.cResult);
        if (iPos == -1) return;                         // ID устройства не прописан в настройках
        Integer nppGroupMachinery, nppMachinery, numberReceipt, numberReceiptDetail;
        java.sql.Date dateZReport, dateReceipt;
        Time timeZReport, timeReceipt;
        String numberZReport, idEmployee, firstNameContact, lastNameContact, barcodeItem, cSign;
//        String seriesNumberDiscountCard; // пока не используется
        BigDecimal sumCard, sumCash, quantityReceiptDetail, priceReceiptDetail, sumReceiptDetail;
//        BigDecimal discountPercentReceiptDetail, discountSumReceiptDetail, discountSumReceipt; // пока не используется
        // ----- шапка чека -----
        oHeader.getPathValue("type");          // Тип продажи: Продажа (SALE), Возврат (REFUND)
        switch (oHeader.cResult) {
            case "SALE":
                cSign = "";
                break;
            case "REFUND":
                cSign = "-";
                break;
            default:
                return;
        }
        nppGroupMachinery = cashRegisterInfoList.get(iPos).numberGroup;
        nppMachinery = cashRegisterInfoList.get(iPos).number;
        oHeader.getPathValue("shiftId");                                    // Номер смены
        numberZReport = oHeader.cResult;
        oHeader.getPathValue("number");                                     // Номер Чека
        numberReceipt = Integer.parseInt(oHeader.cResult);
        oHeader.getPathValue("localDate");                                  // Дата чека
        dateZReport = java.sql.Date.valueOf(oHeader.cResult.substring(0, 10));    // Дата Z отчета
        dateReceipt = java.sql.Date.valueOf(oHeader.cResult.substring(0, 10));    // Дата чека
        timeReceipt = Time.valueOf(oHeader.cResult.substring(11));                // Время чека
        timeZReport = Time.valueOf("00:00:00");                                   // Время Z отчета
        oHeader.getPathValue("cashier.id");                                 // ID кассира
        idEmployee = oHeader.cResult;
        oHeader.getPathValue("cashier.name");                               // Имя кассира
        firstNameContact = "";
        lastNameContact = "";
        if (oHeader.cResult.length() > 0) {
            String[] cCashier = oHeader.cResult.split(" ");
            firstNameContact = cCashier[0];
            if (cCashier.length > 1) lastNameContact = cCashier[1];
        }
//        seriesNumberDiscountCard = null;                                        // Номер карты дисконтного клиента
        oHeader.getArraySize("payments");
        iMax = oHeader.nCount;
        sumCard = new BigDecimal("0.00");
        sumCash = new BigDecimal("0.00");
        for (i = 0; i < iMax; ++i) {
            oHeader.getPathValue("payments[" + i.toString() + "].type");   // Виды оплат CASH-нал, CASHLESS - карта
            switch (oHeader.cResult) {
                case "CASH":
                    oHeader.getPathValue("payments[" + i.toString() + "].amount");
                    sumCash = getBigDecimal(oHeader.cResult, 2);
                    break;
                case "CASHLESS":
                    oHeader.getPathValue("payments[" + i.toString() + "].amount");
                    sumCard = getBigDecimal(oHeader.cResult, 2);
                    break;
            }
        }
        oHeader.getPathValue("discount");                                // Сумма скидки на чек
//         discountSumReceipt = getBigDecimal(oHeader.cResult, 2);
        // ----- строки чека -----
        oHeader.getArraySize("positions");
        for (i = 0; i < oHeader.nCount; ++i) {
            oHeader.getPathJson("positions[" + Integer.toString(i) + "]");
            oLines.load(oHeader.cResult);
            numberReceiptDetail = i + 1;                                        // Номер строки чека
            oLines.getPathValue("barcode");                              // Штрих код
            barcodeItem = oLines.cResult;
            oLines.getPathValue("quantity");                             // Кол-во товаров
            quantityReceiptDetail = getBigDecimal(oLines.cResult, 3);
            oLines.getPathValue("price");                                // Цена товара
            priceReceiptDetail = getBigDecimal(oLines.cResult, 2);
//            discountPercentReceiptDetail = null;                             // % скидки - нет
//            discountSumReceiptDetail = null;                                 // сумма скидки, массив discount - пока нет
            // Сумма товара - реквзит отсутствует, вычисляем самостоятельно
            sumReceiptDetail = quantityReceiptDetail.multiply(priceReceiptDetail).setScale(2, RoundingMode.HALF_UP);
            if (cSign.equals("-")) quantityReceiptDetail = quantityReceiptDetail.multiply(new BigDecimal(-1));

            salesInfoList.add(new SalesInfo(false, nppGroupMachinery, nppMachinery, numberZReport, dateZReport, timeZReport,
                    numberReceipt, dateReceipt, timeReceipt, idEmployee, firstNameContact, lastNameContact, sumCard, sumCash,
                    (BigDecimal) null, barcodeItem, null, null, null, quantityReceiptDetail,
                    priceReceiptDetail, sumReceiptDetail, null, null,
                    null, numberReceiptDetail, "", null));
        }

        for(SalesInfo salesInfo : salesInfoList) {
            salesInfo.zReportInfo = new ZReportInfo(null, null, null);
        }
    }

    // Парсировка кассовых документов
    private void parseDocInfo(String cJson) {
        int iPos;
        JsonReadProcess oLines = new JsonReadProcess();   // Парсировщик строки документа
        oLines.load(cJson);
        String idCashDocument, numberZReport;
        java.sql.Date dateCashDocument;
        Time timeCashDocument;
        Integer nppGroupMachinery, nppMachinery;
        BigDecimal sumCashDocument;
        oLines.getPathValue("localDate");
        dateCashDocument = java.sql.Date.valueOf(oLines.cResult.substring(0, 10));
        timeCashDocument = Time.valueOf(oLines.cResult.substring(11));
        idCashDocument = oLines.cResult.replace("-", "").replace(":", "").replace(" ", "");
        oLines.getPathValue("deviceId");
        iPos = chkDeviceId(oLines.cResult);
        if (iPos == -1) return;                         // ID устройства не прописан в настройках
        nppGroupMachinery = cashRegisterInfoList.get(iPos).numberGroup;
        nppMachinery = cashRegisterInfoList.get(iPos).number;
        oLines.getPathValue("shiftId");
        numberZReport = oLines.cResult;
        oLines.getPathValue("sum");
        sumCashDocument = getBigDecimal(oLines.cResult, 2);
        oLines.getPathValue("type");
        if (oLines.cResult.equals("MONEY_OUT"))
            sumCashDocument = sumCashDocument.multiply(new BigDecimal(-1));
        idCashDocument += "/" + nppMachinery.toString() + "/" + numberZReport;
        // idCashDocument, numberCashDocument, date, time, cashRegister.numberGroup, nppMachinery, numberZReport, sum
        cashDocList.add(new CashDocument(idCashDocument, null, dateCashDocument, timeCashDocument,
                nppGroupMachinery, nppMachinery, numberZReport, sumCashDocument));
    }

    // Возвращает переобразование в тип BigDecimals
    private BigDecimal getBigDecimal(String sValue, int nPoint) {
        BigDecimal nRet;
        if (sValue.length() == 0) sValue = "0";
        sValue = "000" + sValue;
        if (nPoint <= 0) return new BigDecimal(sValue);
        if (sValue.length() > nPoint) {
            nRet = new BigDecimal(sValue.substring(0, sValue.length() - nPoint) + "." + sValue.substring(sValue.length() - nPoint));
        } else nRet = new BigDecimal(sValue);
        return nRet;
    }

    //  Проверяет устройство (deviceId) из чека имеет отношение к cashRegisterInfoList
    //  это проще по времени выполнения, чем организовывать цикл чтения реализации по конкретному устройству
    private Integer chkDeviceId(String deviceId) {
        Integer iRet = -1;
        int iMax = cashRegisterInfoList.size();
        int nId = Integer.parseInt(deviceId);
        for (int i = 0; i < iMax; ++i) {
            if (cashRegisterInfoList.get(i).number == nId) {
                iRet = i;
                break;
            }
        }
        return iRet;
    }

    //  Возвращает диапазон дат принимаемой реализации
    private String getRangeDate() {
        String cFrom, cTo;
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        cTo = "to=" + dateFormat.format(calendar.getTimeInMillis()) + "T23:59:59.999";
        calendar.add(Calendar.DATE, -salesDays);
        cFrom = "from=" + dateFormat.format(calendar.getTimeInMillis()) + "T00:00:00.000";
        return cFrom + "&" + cTo;
    }

    //  Создает текст JSON для отправки цен (POST) и заполняет массив для выполнения PATCH
    private void prepPriceList(TransactionCashRegisterInfo transaction) throws IOException {
        int nPos;
        for (CashRegisterItemInfo item : transaction.itemsList) {
            cResult = "";
            addKeyValue("{", "id", getUUID(item), "", "");                // UUID товара
            addKeyValue(",", "name", item.name.trim(), "", "");           // Название товара
            if ((item.passScalesItem) && (item.splitItem)) {                               // Тип весовой или штучный товар
                addKeyValue(",", "type", "SCALABLE", "", "");
            } else {
                addKeyValue(",", "type", "COUNTABLE", "", "");
            }
            addKeyValue(",", "quantity", "1000", "", "*");         // Единица товара
            // Блок цен на отд. кассы
            addKeyValue(",", "prices", "", "[", "");
            nPos = 0;
            for (CashRegisterInfo dItem : transaction.machineryInfoList) {
                nPos += 1;
                if (nPos > 1) cResult += ",";
                addKeyValue("{", "deviceId", dItem.number.toString(), "", "*");
                addKeyValue(",", "value", getPrice(item.price), "}", "*");
            }
            addKeyValue("]", "", "", "", "");
            // конец блока на отд. кассы
            addKeyValue(",", "meta", "", "{}", "");                // Дополнительные сведения о товаре
            addKeyValue(",", "barcodes", "", "[", "");             // Код товара: передается как массив кодов
            addKeyValue("", "", item.idBarcode.trim(), "]", "");
            addKeyValue(",", "tax", getVat(item.vat), "}", "");          // Ставки НДС
            aPriceList.add(cResult);
        }
    }

    //  Удаляет полностью все товары, вызывается из sendPriceList
    private boolean deleteAllPLU() {
        if (!webExec("GET", "products", "")) return false;
        if (!(webStatus == 200))
            return errBox(getErrorWeb(webStatus), "Ошибка запроса списка товаров", cResult, true);
        JsonReadProcess oJs = new JsonReadProcess();
        JsonReadProcess oJs2 = new JsonReadProcess();
        if (!oJs.load(cResult))
            return errBox("Ошибка чтения ответа от WEB", oJs.eMessage, cResult, true);
        cResult = "";
        for (int i = 0; i < oJs.nSize; i++) {
            oJs.getObjectFromArray(i);
            oJs2.load(oJs.cResult);
            oJs2.getPathValue("id");
            if (cResult.length() > 0) cResult += ",";
            addKeyValue("{", "id", oJs2.cResult, "}", "");
        }
        cResult = "[" + cResult + "]";
        if (!webExec("DELETE", "products", cResult)) return false;
        if (webStatus == 204) return true;
        return errBox(getErrorWeb(webStatus), "Операция удаления товаров не выполнена", "", false);
    }

    //  Конструктур JSON выражений
    private void addKeyValue(String ch1, String key, String value, String ch2, String ctype) {
        cResult += ch1;
        if (key.length() > 0) {
            cResult += "\"" + key + "\":";
        }
        if (value.length() > 0) {
            if (ctype.equals("*")) cResult += value;
            else {
                value = value.replace("\"", "'");
                cResult += "\"" + value.trim() + "\"";
            }
        }
        cResult += ch2;
    }

    //  Возвращает цену: цена, как строка без точки, последние 2 символа - копейки
    private String getPrice(BigDecimal price) {
        return String.valueOf(price.multiply(new BigDecimal(100)).intValue());
    }

    //  Возвращает ставку НДС
    private String getVat(BigDecimal vat) {
        String cRet;
        if (vat == null) return "NDS_0";
        cRet = "NDS_" + vat.setScale(2, ROUND_DOWN).toString();
        return cRet.replace(".", "_");
    }

    //  Возвращает UUID индификатор товара, входной параметр код товара
    private String getUUID(CashRegisterItemInfo item) throws IOException {
        String cData = item.idBarcode.trim();
        byte[] bytes = cData.getBytes("UTF-8");
        UUID uuid = UUID.nameUUIDFromBytes(bytes);
        return uuid.toString();
    }

    //  Промежуточный метод обертка выполнения HTTP запросов
    private boolean webExec(String cMethod, String cURL, String cData) {
        DreamkasHttp ob = new DreamkasHttp();
        ob.addHeader("Content-Type", "application/json");
        ob.addHeader("Authorization", "Bearer " + token);
        cURL = baseURL + cURL;
        if (!ob.send(cMethod, cURL, cData))
            return errBox(ob.eMessage, "", "", false);
        cResult = ob.cResult;
        webStatus = ob.nStatus;
        return true;
    }


    //  Общая обработка статуса WEB запроса
    private String getErrorWeb(Integer nCode) {
        if (nCode == 400) {
            return "Ошибка WEB, код: " + nCode.toString() + ", Ошибка валидации";
        } else if (nCode == 401) {
            return "Ошибка WEB, код: " + nCode.toString() + ", Ошибка авторизации";
        } else if (nCode == 403) {
            return "Ошибка WEB, код: " + nCode.toString() + ", Доступ запрещен";
        } else if (nCode == 404) {
            return "Ошибка WEB, код: " + nCode.toString() + ", Ресурс не найден";
        } else if (nCode == 410) {
            return "Ошибка WEB, код: " + nCode.toString() + ", Ресурс удален";
        } else if (nCode == 429) {
            return "Ошибка WEB, код: " + nCode.toString() + ", Достигнут лимит запросов";
        }
        return "Ошибка WEB, код: " + nCode.toString();
    }

    //  Обработка ошибки
    private boolean errBox(String eMsg, String eMsg2, String eMsg3, boolean log) {
        eMessage = eMsg;
        logMessage = "";
        if (eMsg2.length() > 0) eMessage += " " + eMsg2;
        if (log) {
            logMessage = eMsg;
            if (eMsg2.length() > 0) logMessage += "\n" + eMsg2;
            if (eMsg3.length() > 0) logMessage += "\n" + eMsg3;
        }
        return false;
    }
}

// Класс даты и времени смен для HashMap (когда заработает сервис по сменам)
/*
class DtShift {
    java.sql.Date date;
    Time time;

    DtShift(String cdt) {
        this.date = java.sql.Date.valueOf(cdt.substring(0, 10));
        this.time = Time.valueOf(cdt.substring(11, 19));
    }
}
*/