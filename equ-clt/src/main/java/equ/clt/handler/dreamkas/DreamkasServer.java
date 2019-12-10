package equ.clt.handler.dreamkas;

import equ.api.SalesInfo;
import equ.api.cashregister.CashDocument;
import equ.api.cashregister.CashRegisterInfo;
import equ.api.cashregister.CashRegisterItemInfo;
import equ.api.cashregister.TransactionCashRegisterInfo;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static equ.clt.handler.HandlerUtils.getJSONObject;
import static java.math.BigDecimal.ROUND_DOWN;

public class DreamkasServer {

    protected final static Logger sendSalesLogger = Logger.getLogger("SendSalesLogger");

    public String token = "";                                                   // Токен доступа
    public String baseURL = "";                                                 // Базовый URL
    public String uuidSuffix = null;                                              // Суфикс для кода товара
    public String cResult = "";                                                 // Результат запроса
    public String eMessage = "";                                                // Текст ошибки
    public int webStatus = 0;                                               // Статус выполнения команды
    public String logMessage = "";                                              // Текст для лог файла
    public Integer salesLimitReceipt = 500;                                     // Кол-во чеков за 1 запрос (200..1000)
    public Integer stepSend = 100;                                              // Шаг передачи - количество товаров для передачи за 1 раз
    public List<CashRegisterInfo> cashRegisterInfoList;                         // Список обрабатываемых устройств
    public List<SalesInfo> salesInfoList = new ArrayList<>();                                // Строки принятой реализацииж
    public List<CashDocument> cashDocList = null;                               // Кассовые документы (внесения, изъятия)
    private ArrayList<String> aPriceList = new ArrayList<>();                   // Массив частей JSON для выполнения PATCH
//    private Map<String, DtShift> hmShift = new HashMap<String, DtShift>();    // Map deviceId_shift:объект даты и времени


    public DreamkasServer() {
    }

    public DreamkasServer(List<CashRegisterInfo> cashRegisterInfoList) {
        this.cashRegisterInfoList = cashRegisterInfoList;
    }

    //  --- Основной метод загрузки кассового сервера новыми товарами (POST) или обновление товаров (PATCH)
    public boolean sendPriceList(TransactionCashRegisterInfo transaction) {
        boolean lRet = true;
        if (transaction.itemsList == null) return true;
        // Очистка БД товаров, если snapshot
        if (transaction.snapshot) {
            if (!deleteAllPLU()) return false;
        }
        // Весь список товаров в массиве aPriceList, каждый элемент Json товара
        prepPriceList(transaction);
        // Цикл передачи в соответствии с шагом передачи
        int imax = 0; int maxSize = aPriceList.size();
        while (imax < maxSize) {
            if ((imax + stepSend) > maxSize) {
                lRet = sendPricePart(imax,maxSize - 1);
            } else {
                lRet = sendPricePart(imax,imax + stepSend - 1);
            }
            imax += stepSend;
            if (!lRet) break;
        }
        return lRet;
    }

    // Передает часть товаров (stepSend) на сервер
    private boolean sendPricePart(Integer n1,Integer n2) {
        // Вначале выполняем POST - считаем, что все товары новые.
        cResult = "";
        for (int i = n1; i <= n2; i++) {
            if (cResult.length() > 0) cResult += ",";
            cResult += aPriceList.get(i);
        }
        if (cResult.length() == 0)
            return errBox("POST: Отправляемый пакет не содержит данных", "", "", false);
        String request = "[" + cResult + "]";
        if (!webExec("POST", "products", request))
            return errBox("Товары не были переданы на сервер", eMessage, request, false); // Ошибки WEB
        // Может быть одиночная ошибка (webStatus!=200) или группа ошибок (webStatus=200) - разные структуры Json
        // Если webStatus > 2xx, то ответ может содержаться в разных структурах Json, поэтому не парсируем
        if (!(webStatus == 200))
            return errBox("Товары не переданы на сервер", getErrorWeb(webStatus), cResult + "\nRequest: " + request, true);
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
                    cResult += aPriceList.get(n1 + Integer.parseInt(rPart));
                }
            }
        }
        if (cResult.length() == 0)
            return errBox("Пакет для PATCH не содержит данных. Товары не переданы на сервер.", lastMsg, prevCResult, true);
        if (!webExec("PATCH", "products", "[" + cResult + "]"))
            return errBox(eMessage, "Товары не переданы на сервер", cResult + "\nRequest: " + request, true); // Ошибки WEB
        if (webStatus == 204) return true;
        return errBox(getErrorWeb(webStatus), "Товары не были переданы на сервер", cResult + "\nRequest: " + request, true);
    }

    //  --- Основной метод чтения реализации
    public boolean getSales(String receiptsQuery) {
        salesInfoList = new ArrayList<>();
        JsonReadProcess oJs = new JsonReadProcess();
        int offset = 0;
        boolean lRet = true;
        String url = receiptsQuery + "&limit=" + salesLimitReceipt;
        sendSalesLogger.info("Dreamkas: request url " + url);
        while (true) {
            if (!webExec("GET", url + "&offset=" + offset, "")) {
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
                oJs.getPathJson("data[" + i + "]");
                parseReceipt(oJs.cResult);
            }
        }
        return lRet;
    }

    // ----- Основной метод чтения документов кассы
    public boolean getDocInfo(Integer salesHours) {
        return readDocInfo(salesHours);
    }

    // чтение кассовых документов
    private boolean readDocInfo(Integer salesHours) {
        int offset = 0;
        boolean lRet = true;
        String url = "encashments?" + DreamkasHandler.getRangeDatesSubQuery(salesHours) + "&limit=" + salesLimitReceipt;
        cashDocList = new ArrayList<>();
        JsonReadProcess oJs = new JsonReadProcess();
        while (true) {
            if (!webExec("GET", url + "&offset=" + offset, "")) {
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
                oJs.getPathJson("data[" + i + "]");
                parseDocInfo(oJs.cResult);
            }
        }
        return lRet;
    }

    //  Метод опроса номеров смен - метод пока не используется
    private boolean getShifts(Integer salesHours) {
        int offset = 0;
        int limit = 500;
        boolean lRet = true;

        String url = "shifts?" + DreamkasHandler.getRangeDatesSubQuery(salesHours) + "&limit=" + limit;
        JsonReadProcess oJs = new JsonReadProcess();
        while (true) {
            if (!webExec("GET", url + "&offset=" + offset, "")) {
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
                oJs.getPathJson("data[" + i + "]");
                parseReceipt(oJs.cResult);
            }
        }
        return lRet;
    }

    //  Чтение чека
    private void parseReceipt(String cJson) {
        JsonReadProcess oHeader = new JsonReadProcess(); // Парсировщик шапки чека
        JsonReadProcess oLines = new JsonReadProcess();   // Парсировщик строк чека
        oHeader.load(cJson);
        oHeader.getPathValue("deviceId");
        CashRegisterInfo cashRegister = getCashRegister(oHeader.cResult);
        if (cashRegister != null) {                // ID устройства прописан в настройках
            boolean isCancel = false;
            // ----- шапка чека -----
            oHeader.getPathValue("type");          // Тип продажи: Продажа (SALE), Возврат (REFUND), Аннулирование продажи (SALE_ANNUL)
            boolean isReturn;
            switch (oHeader.cResult) {
                case "SALE":
                    isReturn = false;
                    break;
                case "REFUND":
                    isReturn = true;
                    break;
                case "SALE_ANNUL":
                    isReturn = true;
                    isCancel = true;
                    break;
                default:
                    return;
            }
            oHeader.getPathValue("shiftId");                                    // Номер смены
            String numberZReport = oHeader.cResult;
            oHeader.getPathValue("number");                                     // Номер Чека
            Integer numberReceipt = Integer.parseInt(oHeader.cResult);
            oHeader.getPathValue("localDate");                                  // Дата чека
            java.sql.Date dateZReport = java.sql.Date.valueOf(oHeader.cResult.substring(0, 10));    // Дата Z отчета
            java.sql.Date dateReceipt = java.sql.Date.valueOf(oHeader.cResult.substring(0, 10));    // Дата чека
            Time timeReceipt = Time.valueOf(oHeader.cResult.substring(11));                // Время чека
            Time timeZReport = Time.valueOf("00:00:00");                                   // Время Z отчета
            oHeader.getPathValue("cashier.tabNumber");                                 // ID кассира
            String idEmployee = oHeader.cResult;
            oHeader.getPathValue("cashier.name");                               // Имя кассира
            String firstNameContact = "";
            String lastNameContact = "";
            if (oHeader.cResult.length() > 0) {
                String[] cCashier = oHeader.cResult.split(" ");
                firstNameContact = cCashier[0];
                if (cCashier.length > 1) lastNameContact = cCashier[1];
            }
            oHeader.getArraySize("payments");
            int iMax = oHeader.nCount;
            BigDecimal sumCard = new BigDecimal("0.00");
            BigDecimal sumCash = new BigDecimal("0.00");
            for (int i = 0; i < iMax; ++i) {
                oHeader.getPathValue("payments[" + i + "].type");   // Виды оплат CASH-нал, CASHLESS - карта
                switch (oHeader.cResult) {
                    case "CASH":
                        oHeader.getPathValue("payments[" + i + "].amount");
                        sumCash = getBigDecimal(oHeader.cResult, 2);
                        if (isReturn) sumCash = sumCash.negate();
                        break;
                    case "CASHLESS":
                        oHeader.getPathValue("payments[" + i + "].amount");
                        sumCard = getBigDecimal(oHeader.cResult, 2);
                        if (isReturn) sumCard = sumCard.negate();
                        break;
                }
            }
            oHeader.getPathValue("discount");                                // Сумма скидки на чек
        //         discountSumReceipt = getBigDecimal(oHeader.cResult, 2);
            // ----- строки чека -----
            oHeader.getArraySize("positions");
            for (int i = 0; i < oHeader.nCount; ++i) {
                oHeader.getPathJson("positions[" + i + "]");
                oLines.load(oHeader.cResult);
                Integer numberReceiptDetail = i + 1;                                        // Номер строки чека
                oLines.getPathValue("barcode");                              // Штрих код
                String barcodeItem = oLines.cResult;
                oLines.getPathValue("quantity");                             // Кол-во товаров
                BigDecimal quantityReceiptDetail = getBigDecimal(oLines.cResult, 3);
                oLines.getPathValue("price");                                // Цена товара
                BigDecimal priceReceiptDetail = getBigDecimal(oLines.cResult, 2);
                // Сумма товара - реквзит отсутствует, вычисляем самостоятельно
                if (isReturn) quantityReceiptDetail = quantityReceiptDetail.negate();
                BigDecimal sumReceiptDetail = quantityReceiptDetail.multiply(priceReceiptDetail).setScale(2, RoundingMode.HALF_UP);

                salesInfoList.add(DreamkasHandler.getSalesInfo(false, false, cashRegister.numberGroup, cashRegister.number, numberZReport, dateZReport, timeZReport,
                        numberReceipt, dateReceipt, timeReceipt, idEmployee, idEmployee != null ? firstNameContact : null,
                        idEmployee != null ? lastNameContact : null, sumCard, sumCash,
                        null, barcodeItem, null, null, null, quantityReceiptDetail, priceReceiptDetail, sumReceiptDetail, null,
                        null, null, null, numberReceiptDetail, "", null, isCancel, null));
            }
        }
    }

    // Парсировка кассовых документов
    private void parseDocInfo(String cJson) {
        JsonReadProcess oLines = new JsonReadProcess();   // Парсировщик строки документа
        oLines.load(cJson);
        oLines.getPathValue("localDate");
        java.sql.Date dateCashDocument = java.sql.Date.valueOf(oLines.cResult.substring(0, 10));
        Time timeCashDocument = Time.valueOf(oLines.cResult.substring(11));
        String idCashDocument = oLines.cResult.replace("-", "").replace(":", "").replace(" ", "");
        oLines.getPathValue("deviceId");
        CashRegisterInfo cashRegister = getCashRegister(oLines.cResult);
        if (cashRegister != null) {                       // ID устройства не прописан в настройках
            oLines.getPathValue("shiftId");
            String numberZReport = oLines.cResult;
            oLines.getPathValue("sum");
            BigDecimal sumCashDocument = getBigDecimal(oLines.cResult, 2);
            oLines.getPathValue("type");
            if (oLines.cResult.equals("MONEY_OUT"))
                sumCashDocument = sumCashDocument.multiply(new BigDecimal(-1));
            idCashDocument += "/" + cashRegister.number + "/" + numberZReport;
            cashDocList.add(new CashDocument(idCashDocument, null, dateCashDocument, timeCashDocument,
                    cashRegister.numberGroup, cashRegister.number, numberZReport, sumCashDocument));
        }
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
    private CashRegisterInfo getCashRegister(String deviceId) {
        int nId = Integer.parseInt(deviceId);
        for (CashRegisterInfo cashRegister : cashRegisterInfoList) {
            if (cashRegister.number == nId) {
                return cashRegister;
            }
        }
        return null;
    }

    //  Создает текст JSON для отправки цен (POST) и заполняет массив для выполнения PATCH
    private void prepPriceList(TransactionCashRegisterInfo transaction) {
        int nPos;
        for (CashRegisterItemInfo item : transaction.itemsList) {
            cResult = "";
            addKeyValue("{", "id", getUUID(item), "", "");                // UUID товара
            addKeyValue(",", "name", item.name.trim(), "", "");           // Название товара
            addKeyValue(",", "type", (item.passScalesItem || item.splitItem) ? "SCALABLE" : "COUNTABLE", "", ""); // Тип весовой или штучный товар
            addKeyValue(",", "quantity", (item.passScalesItem || item.splitItem) ? "1" : "1000", "", "*");         // Единица товара
            // Блок цен на отд. кассы
            addKeyValue(",", "prices", "", "[", "");
            nPos = 0;
            for (CashRegisterInfo dItem : transaction.machineryInfoList) {
                nPos += 1;
                if (nPos > 1) cResult += ",";
                addKeyValue("{", "deviceId", dItem.number.toString(), "", "*");
                addKeyValue(",", "value", getPrice(item.price), "}", "*");
            }
            JSONObject info = getJSONObject(item.info, "dreamkas");
            if(info != null) {
                JSONArray extraPrices = info.optJSONArray("extraPrices");
                if (extraPrices != null) {
                    for (int i = 0; i < extraPrices.length(); i++) {
                        if (nPos >= 1) cResult += ",";
                        cResult += extraPrices.getJSONObject(i).toString();
                    }
                }
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
        if (vat == null) {
            return "NDS_0";
        } else {
            return ("NDS_" + vat.setScale(2, ROUND_DOWN)).replace(".", "_");
        }
    }

    //  Возвращает UUID индификатор товара, входной параметр код товара
    private String getUUID(CashRegisterItemInfo item) {
        String cData = item.idBarcode.trim() + uuidSuffix;
        byte[] bytes = cData.getBytes(StandardCharsets.UTF_8);
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
    private String getErrorWeb(int nCode) {
        switch (nCode) {
            case 400:
                return "Ошибка WEB, код: " + nCode + ", Ошибка валидации";
            case 401:
                return "Ошибка WEB, код: " + nCode + ", Ошибка авторизации";
            case 403:
                return "Ошибка WEB, код: " + nCode + ", Доступ запрещен";
            case 404:
                return "Ошибка WEB, код: " + nCode + ", Ресурс не найден";
            case 410:
                return "Ошибка WEB, код: " + nCode + ", Ресурс удален";
            case 429:
                return "Ошибка WEB, код: " + nCode + ", Достигнут лимит запросов";
            default:
                return "Ошибка WEB, код: " + nCode;
        }
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