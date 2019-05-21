package equ.clt.handler.shtrihPrint;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
//import com.jacob.com.LibraryLoader;
import com.jacob.com.Variant;
import equ.api.*;
import equ.api.scales.*;
import equ.clt.handler.DefaultScalesHandler;
import equ.clt.handler.ScalesSettings;
import lsfusion.base.ExceptionUtils;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import javax.naming.CommunicationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.*;

public class ShtrihPrintHandler extends DefaultScalesHandler {

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    private static String password = "0030";

    private FileSystemXmlApplicationContext springContext;

    public ShtrihPrintHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    public String getGroupId(TransactionScalesInfo transactionInfo) {
        
        ScalesSettings shtrihSettings = springContext.containsBean("shtrihSettings") ? (ScalesSettings) springContext.getBean("shtrihSettings") : null;
        boolean allowParallel = shtrihSettings == null || shtrihSettings.isAllowParallel();
        // нельзя делать параллельно, так как на большом количестве одновременных подключений через ADSL на весы идут Connection Error   
        if (allowParallel) {
            String groupId = "";
            for (MachineryInfo scales : transactionInfo.machineryInfoList) {
                groupId += scales.port + ";";
            }
            return "shtrihPrint" + groupId;
        } else return "shtrihPrint";
        
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionScalesInfo> transactionList) throws IOException {

        //System.setProperty(LibraryLoader.JACOB_DLL_PATH, "E:\\work\\Кассы-весы\\dll\\jacob-1.15-M3-x86.dll");

        Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        for(TransactionScalesInfo transaction : transactionList) {

            List<MachineryInfo> succeededScalesList = new ArrayList<>();
            Exception exception = null;
            try {

                processTransactionLogger.info("Shtrih: Reading settings...");
                ScalesSettings shtrihSettings = springContext.containsBean("shtrihSettings") ? (ScalesSettings) springContext.getBean("shtrihSettings") : null;
                boolean usePLUNumberInMessage = shtrihSettings == null || shtrihSettings.isUsePLUNumberInMessage();
                boolean newLineNoSubstring = shtrihSettings == null || shtrihSettings.isNewLineNoSubstring();
                boolean useSockets = shtrihSettings == null || shtrihSettings.isUseSockets();
                boolean capitalLetters = shtrihSettings != null && shtrihSettings.isCapitalLetters();
                int advancedClearMaxPLU = shtrihSettings == null || shtrihSettings.getAdvancedClearMaxPLU() == null ? 0 : shtrihSettings.getAdvancedClearMaxPLU();

                List<ScalesInfo> enabledScalesList = new ArrayList<>();
                for (ScalesInfo scales : transaction.machineryInfoList) {
                    if (scales.enabled)
                        enabledScalesList.add(scales);
                }

                processTransactionLogger.info("Shtrih: Send Transaction # " + transaction.id);

                if (!transaction.machineryInfoList.isEmpty()) {

                    Map<String, List<String>> errors = new HashMap<>();
                    Set<String> ips = new HashSet<>();

                    List<ScalesInfo> usingScalesList = enabledScalesList.isEmpty() ? transaction.machineryInfoList : enabledScalesList;

                    processTransactionLogger.info("Shtrih: Starting sending to " + usingScalesList.size() + " scales...");

                    if (useSockets) {

                        for (ScalesInfo scales : usingScalesList) {
                            int globalError = 0;
                            List<String> localErrors = new ArrayList<>();

                            UDPPort port = new UDPPort(scales.port, 1111, 10000);

                            String ip = scales.port;
                            if (ip != null) {
                                ips.add(scales.port);

                                for (ScalesItemInfo item : transaction.itemsList) {
                                    if (item.pluNumber == null) {
                                        logError(localErrors, String.format("Обнаружен товар без номера PLU: id %s (%s)", item.idItem, item.name));
                                    }
                                }
                                if (localErrors.isEmpty()) {
                                    processTransactionLogger.info("Shtrih: Processing ip: " + ip);
                                    try {

                                        processTransactionLogger.info("Shtrih: Connecting..." + ip);
                                        port.open();
                                        if (!transaction.itemsList.isEmpty() && transaction.snapshot && advancedClearMaxPLU == 0) {
                                            int clear = clearGoodsDB(localErrors, port);
                                            if (clear != 0)
                                                logError(localErrors, String.format("Shtrih: ClearGoodsDb, Error # %s (%s)", clear, getErrorText(clear)));
                                        }

                                        processTransactionLogger.info("Shtrih: Sending items..." + ip);
                                        if (localErrors.isEmpty()) {
                                            Set<Integer> usedPLUNumberSet = new HashSet<>();
                                            for (ScalesItemInfo item : transaction.itemsList) {

                                                if (!Thread.currentThread().isInterrupted()) {

                                                    int error;
                                                    int attempt = 0;
                                                    List<String> itemErrors;
                                                    do {
                                                        error = 0;
                                                        attempt++;
                                                        itemErrors = new ArrayList<>();
                                                        Integer barcode = Integer.parseInt(item.idBarcode.substring(0, 5));
                                                        Integer shelfLife = item.expiryDate == null ? (item.daysExpiry == null ? 0 : item.daysExpiry) : 0;

                                                        String nameItem = item.name != null && capitalLetters ? item.name.toUpperCase() : item.name;
                                                        int len = nameItem.length();
                                                        String firstName = nameItem.substring(0, len < 28 ? len : 28);
                                                        String secondName = len < 28 ? "" : nameItem.substring(28, len < 56 ? len : 56);
                                                        Date expiryDate = item.expiryDate == null ? new Date(2001 - 1900, 0, 1) : item.expiryDate;
                                                        Integer groupCode = 0; //item.idItemGroup == null ? 0 : Integer.parseInt(item.idItemGroup.replace("_", ""));
                                                        String description = item.description == null ? "" : item.description;
                                                        int messageNumber = usePLUNumberInMessage ? item.pluNumber : item.descriptionNumber;
                                                        int start = 0;
                                                        int total = description.length();
                                                        int i = 0;
                                                        while (i < 8) {
                                                            String message = getMessage(description, start, total, newLineNoSubstring);
                                                            start += message.length() + 1;
                                                            int result = setMessageData(itemErrors, port, messageNumber, i + 1, message);
                                                            if (result != 0) {
                                                                error = result;
                                                                break;
                                                            }
                                                            i++;
                                                        }

                                                        if (error == 0) {
                                                            processTransactionLogger.info("Shtrih: sending item " + item.pluNumber);
                                                            int result = setPLUDataEx(itemErrors, port, item.pluNumber, barcode, firstName, secondName,
                                                                    item.price, shelfLife, groupCode, messageNumber, expiryDate, item.splitItem ? 0 : 1);
                                                            if (result != 0)
                                                                error = result;
                                                        }
                                                    } while (attempt < 5 && error != 0);

                                                    if (error != 0) {
                                                        if (itemErrors != null && !itemErrors.isEmpty())
                                                            localErrors.addAll(itemErrors);
                                                        logError(localErrors, String.format("Shtrih: Item # %s, Error # %s (%s)", item.idBarcode, error, getErrorText(error)));
                                                        //поменяли логику: три товара по 5 попыток не прогрузились - прекращаем загрузку всех последующих
                                                        globalError++;
                                                        if(globalError >= 3)
                                                            break;
                                                    }
                                                    usedPLUNumberSet.add(item.pluNumber);
                                                }
                                            }

                                            //зануляем незадействованные pluNumber
                                            if (transaction.snapshot && advancedClearMaxPLU != 0 && globalError < 3) {
                                                String firstLine = "Недопустимый штрих-код!";
                                                String secondLine = "";
                                                String message = "";
                                                for (int i = 1; i <= advancedClearMaxPLU; i++)
                                                    if (!Thread.currentThread().isInterrupted() && !usedPLUNumberSet.contains(i)) {
                                                        int error;
                                                        int attempt = 0;
                                                        List<String> itemErrors;
                                                        do {
                                                            error = 0;
                                                            attempt++;
                                                            itemErrors = new ArrayList<>();

                                                            int j = 0;
                                                            while (j < 8) {
                                                                int result = setMessageData(itemErrors, port, i, j + 1, message);
                                                                if (result != 0) {
                                                                    error = result;
                                                                    break;
                                                                }
                                                                j++;
                                                            }

                                                            if (error == 0) {
                                                                processTransactionLogger.info("Shtrih: resetting item " + i);
                                                                int result = setPLUDataEx(itemErrors, port, i, i, firstLine, secondLine, BigDecimal.valueOf(9999.99),
                                                                        0, 0, i, new Date(2001 - 1900, 0, 1), 0);
                                                                if (result != 0)
                                                                    error = result;
                                                            }
                                                        } while (attempt < 5 && error != 0);

                                                        if (error != 0) {
                                                            if (itemErrors != null && !itemErrors.isEmpty())
                                                                localErrors.addAll(itemErrors);
                                                            logError(localErrors, String.format("Shtrih: Item # %s, Error # %s (%s)", i, error, getErrorText(error)));
                                                            globalError++;
                                                            if(globalError >= 3)
                                                                break;
                                                        }
                                                    }
                                            }

                                        }
                                        port.close();

                                    } catch (Exception e) {
                                        logError(localErrors, "ShtrihPrintHandler error: ", e);
                                    } finally {
                                        processTransactionLogger.info("Shtrih: Finally disconnecting..." + ip);
                                        try {
                                            port.close();
                                        } catch (CommunicationException e) {
                                            logError(localErrors, "ShtrihPrintHandler close port error: ", e);
                                        }
                                    }
                                    processTransactionLogger.info("Shtrih: Completed ip: " + ip);
                                }
                            }
                            if (localErrors.isEmpty())
                                succeededScalesList.add(scales);
                            else
                                errors.put(ip, localErrors);
                        }
                    } else {

                        processTransactionLogger.info("Shtrih: Initializing COM-Object AddIn.DrvLP...");
                        ActiveXComponent shtrihActiveXComponent = null;
                        Dispatch shtrihDispatch = null;

                        try {

                            shtrihActiveXComponent = new ActiveXComponent("AddIn.DrvLP");
                            processTransactionLogger.info("Shtrih: Initializing DrvLP (Get Object)...");
                            shtrihDispatch = shtrihActiveXComponent.getObject();

                            Variant pass = new Variant(30);

                            for (ScalesInfo scales : enabledScalesList.isEmpty() ? transaction.machineryInfoList : enabledScalesList) {
                                List<String> localErrors = new ArrayList<>();
                                String ip = scales.port;
                                if (ip != null) {
                                    ips.add(scales.port);

                                    for (ScalesItemInfo item : transaction.itemsList) {
                                        if (item.pluNumber == null) {
                                            logError(localErrors, String.format("Обнаружен товар без номера PLU: id %s (%s)", item.idItem, item.name));
                                        }
                                    }
                                    if (localErrors.isEmpty()) {
                                        processTransactionLogger.info("Shtrih: Processing ip: " + ip);
                                        try {

                                            shtrihActiveXComponent.setProperty("LDInterface", new Variant(1));
                                            shtrihActiveXComponent.setProperty("LDRemoteHost", new Variant(ip));
                                            Dispatch.call(shtrihDispatch, "AddLD");
                                            Dispatch.call(shtrihDispatch, "SetActiveLD");

                                            processTransactionLogger.info("Shtrih: Connecting..." + ip);
                                            Variant result = Dispatch.call(shtrihDispatch, "Connect");
                                            if (!isError(result)) {

                                                processTransactionLogger.info("Shtrih: Setting password..." + ip);
                                                shtrihActiveXComponent.setProperty("Password", pass);
                                                if (!transaction.itemsList.isEmpty() && transaction.snapshot) {
                                                    Variant clear = Dispatch.call(shtrihDispatch, "ClearGoodsDB");
                                                    if (isError(clear))
                                                        logError(localErrors, String.format("Shtrih: ClearGoodsDb, Error # %s (%s)", clear.getInt(), getErrorText(clear.getInt())));
                                                }

                                                processTransactionLogger.info("Shtrih: Sending items..." + ip);
                                                if (localErrors.isEmpty()) {
                                                    for (ScalesItemInfo item : transaction.itemsList) {
                                                        Integer barcode = Integer.parseInt(item.idBarcode.substring(0, 5));
                                                        Integer shelfLife = item.expiryDate == null ? (item.daysExpiry == null ? 0 : item.daysExpiry) : 0;

                                                        String nameItem = item.name != null && capitalLetters ? item.name.toUpperCase() : item.name;
                                                        int len = nameItem.length();
                                                        String firstName = nameItem.substring(0, len < 28 ? len : 28);
                                                        String secondName = len < 28 ? "" : nameItem.substring(28, len < 56 ? len : 56);

                                                        shtrihActiveXComponent.setProperty("PLUNumber", new Variant(item.pluNumber));
                                                        shtrihActiveXComponent.setProperty("Price", new Variant(item.price == null ? 0 : item.price.multiply(BigDecimal.valueOf(100)).intValue()));
                                                        shtrihActiveXComponent.setProperty("Tare", new Variant(0));
                                                        shtrihActiveXComponent.setProperty("ItemCode", new Variant(barcode));
                                                        shtrihActiveXComponent.setProperty("NameFirst", new Variant(firstName));
                                                        shtrihActiveXComponent.setProperty("NameSecond", new Variant(secondName));
                                                        shtrihActiveXComponent.setProperty("ShelfLife", new Variant(shelfLife)); //срок хранения в днях
                                                        String groupCode = null; //item.idItemGroup == null ? null : item.idItemGroup.replace("_", "");
                                                        shtrihActiveXComponent.setProperty("GroupCode", new Variant(groupCode));
                                                        shtrihActiveXComponent.setProperty("PictureNumber", new Variant(0));
                                                        shtrihActiveXComponent.setProperty("ROSTEST", new Variant(0));
                                                        shtrihActiveXComponent.setProperty("ExpiryDate", new Variant(item.expiryDate == null ? new Date(2001 - 1900, 0, 1) : item.expiryDate));
                                                        shtrihActiveXComponent.setProperty("GoodsType", new Variant(item.splitItem ? 0 : 1));

                                                        String description = item.description == null ? "" : item.description;
                                                        int start = 0;
                                                        int total = description.length();
                                                        int i = 0;
                                                        while (i < 8) {
                                                            shtrihActiveXComponent.setProperty("MessageNumber", new Variant(usePLUNumberInMessage ? item.pluNumber : item.descriptionNumber));
                                                            shtrihActiveXComponent.setProperty("StringNumber", new Variant(i + 1));
                                                            String message = getMessage(description, start, total, newLineNoSubstring);
                                                            shtrihActiveXComponent.setProperty("MessageString", new Variant(message));
                                                            start += message.length() + 1;
                                                            i++;

                                                            result = Dispatch.call(shtrihDispatch, "SetMessageData");
                                                            if (isError(result))
                                                                logError(localErrors, String.format("Shtrih: Item # %s, Error # %s (%s)", item.idBarcode, result.getInt(), getErrorText(result.getInt())));
                                                        }

                                                        result = Dispatch.call(shtrihDispatch, "SetPLUDataEx");
                                                        if (isError(result))
                                                            logError(localErrors, String.format("Shtrih: Item # %s, Error # %s (%s)", item.idBarcode, result.getInt(), getErrorText(result.getInt())));
                                                    }
                                                }
                                                processTransactionLogger.info("Shtrih: Disconnecting..." + ip);
                                                result = Dispatch.call(shtrihDispatch, "Disconnect");
                                                if (isError(result)) {
                                                    logError(localErrors, String.format("Shtrih: Disconnection error # %s (%s)", result.getInt(), getErrorText(result.getInt())));
                                                    continue;
                                                }
                                            } else {
                                                Dispatch.call(shtrihDispatch, "Disconnect");
                                                logError(localErrors, String.format("Shtrih: Connection error # %s (%s)", result.getInt(), getErrorText(result.getInt())));
                                                continue;
                                            }
                                        } finally {
                                            processTransactionLogger.info("Shtrih: Finally disconnecting..." + ip);
                                            Dispatch.call(shtrihDispatch, "Disconnect");
                                        }
                                        processTransactionLogger.info("Shtrih: Completed ip: " + ip);
                                    }
                                }
                                if (localErrors.isEmpty())
                                    succeededScalesList.add(scales);
                                else
                                    errors.put(ip, localErrors);
                            }

                        } finally {
                            if (shtrihDispatch != null)
                                shtrihDispatch.safeRelease();
                            if (shtrihActiveXComponent != null)
                                shtrihActiveXComponent.safeRelease();
                        }
                    }

                    if (!errors.isEmpty()) {
                        String message = "";
                        for (Map.Entry<String, List<String>> entry : errors.entrySet()) {
                            message += entry.getKey() + ": \n";
                            for (String error : entry.getValue()) {
                                message += error + "\n";
                            }
                        }
                        throw new RuntimeException(message);
                    } else if (ips.isEmpty())
                        throw new RuntimeException("Shtrih: No IP-addresses defined");

                }
            } catch (Exception e) {
                exception = e;
            }
            sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(succeededScalesList, exception));
        }
        return sendTransactionBatchMap;
    }

    private String getErrorText(int index) {
        switch (index) {
            case -21: 
                return "Блок данных имеет максимальную длину";
            case -20: 
                return "Соединение не установлено";
            case -19: 
                return "TCP - порт занят другим приложением";
            case -18: 
                return "Неверный тип устройства";
            case -17: 
                return "Неверная высота штрих - кода";
            case -16: 
                return "Нет активного логического устройства";
            case -15: 
                return "Команда не реализуется в данной версии";
            case -14: 
                return "Удаление логического устройства невозможно";
            case -13: 
                return "Устройство занято";
            case -12: 
                return "Нет ответа на предыдущую команду";
            case -11: 
                return "Команда не является широковещательной";
            case -10: 
                return "Неверный номер логического устройства";
            case -9: 
                return "Параметр вне диапазона";
            case -3: 
                return "Сом - порт недоступен";
            case -2: 
                return "Сом - порт занят другим приложением";
            case -1: 
                return "Нет связи";
            case 0:
                return "Ошибок нет";
            case 1:
                return "Нет бумаги";
            case 2:
                return "Этикетка не спозиционирована";
            case 3:
                return "Открыта печатающая головка";
            case 4:
                return "Не снята отпечатанная этикетка";
            case 5:
                return "Перегрев печатной головки";
            case 6:
                return "Перегрев печатной головки во время печати";
            case 7:
            case 8:
            case 21:
            case 22:
            case 23:
            case 24:
            case 25:
            case 26:
            case 27:
            case 28:
            case 29:
            case 30:
            case 31:
            case 32:
            case 33:
            case 34:
            case 35:
            case 36:
            case 37:
            case 38:
            case 39:
            case 40:
            case 41:
            case 42:
            case 43:
            case 44:
            case 45:
            case 46:
            case 47:
            case 48:
            case 49:
            case 50:
            case 51:
            case 52:
            case 53:
            case 54:
            case 55:
            case 56:
            case 57:
            case 58:
            case 59:
            case 60:
            case 61:
            case 62:
            case 63:
            case 64:
            case 65:
            case 66:
            case 67:
            case 68:
            case 69:
            case 70:
            case 71:
            case 72:
            case 73:
            case 74:
            case 75:
            case 76:
            case 77:
            case 78:
            case 79:
            case 80:
            case 81:
            case 82:
            case 83:
            case 84:
            case 85:
            case 86:
            case 87:
            case 88:
            case 89:
            case 90:
            case 91:
            case 92:
            case 93:
            case 94:
            case 95:
            case 96:
            case 97:
            case 98:
            case 99:
            case 116:
            case 117:
            case 118:
            case 119:
            case 137:
            case 138:
            case 143:
            case 144:
            case 154:
            case 155:
            case 156:
            case 157:
            case 158:
            case 159:
            case 160:
            case 166:
            default:
                return "Неизвестная ошибка";
            case 9:
                return "Печать прервана / неполная печать";
            case 10:
                return "Ошибка при чтении часов";
            case 11:
                return "Ошибка при паковке / распаковке даты";
            case 12:
                return "Ошибка при чтении сообщений";
            case 13:
                return "Ошибка при чтении накоплений";
            case 14:
                return "Ошибка при формировании ШК";
            case 15:
                return "Ошибка в значении количества";
            case 16:
                return "Ошибка в значении веса";
            case 17:
                return "Ошибка в значении тары";
            case 18:
                return "Ошибка в значении цены";
            case 19:
                return "Ошибка в значении стоимости";
            case 20:
                return "Нулевая стоимость";
            case 100:
                return "Совпадение весового и штучного префиксов";
            case 101:
                return "Неверный префикс итоговой этикетки";
            case 102:
                return "Совпадение номера весов и префикса итоговой этикетки";
            case 103:
                return "Совпадение группового кода товара и префикса итоговой этикетки";
            case 104:
                return "Совпадение префикса весового товара и префикса итоговой этикетки";
            case 105:
                return "Совпадение префикса штучного товара и префикса итоговой этикетки";
            case 106:
                return "Неверный тип префикса ШК";
            case 107:
                return "Неверный номер весов";
            case 108:
                return "Неверный номер группового кода товара";
            case 109:
                return "Неверное количество строк в наименовании товара";
            case 110:
                return "Неверное количество строк в наименовании магазина";
            case 111:
                return "Неверный весовой префикс";
            case 112:
                return "Неверный штучный префикс";
            case 113:
                return "Неверный номер формата этикетки";
            case 114:
                return "Неверный номер формата ШК";
            case 115:
                return "Печать опционально запрещена";
            case 120:
                return "Неизвестная команда";
            case 121:
                return "Неверная длина данных команды";
            case 122:
                return "Неверный пароль";
            case 123:
                return "Команда не реализуется в данном режиме";
            case 124:
                return "Неверное значение параметра";
            case 125:
                return "Порт не поддерживается";
            case 126:
                return "Поддерживается только чтение";
            case 127:
                return "Невозможна печать копии";
            case 128:
                return "Неверный номер ПЛУ";
            case 129:
                return "Неверный номер строки сообщения";
            case 130:
                return "Неверный код товара";
            case 131:
                return "Неверная цена товара";
            case 132:
                return "Неверный срок годности товара";
            case 133:
                return "Неверная тара товара";
            case 134:
                return "Неверный групповой код товара";
            case 135:
                return "Неверный номер сообщения";
            case 136:
                return "Неверный номер изображения";
            case 139:
                return "Таблица товаров пуста";
            case 140:
                return "Пустое ПЛУ";
            case 141:
                return "Товар выбран";
            case 142:
                return "Неверная дата реализации";
            case 145:
                return "Сумматор не пуст";
            case 146:
                return "Сумматор пуст";
            case 147:
                return "Добавление в сумматор невозможно";
            case 148:
                return "Отмена последнего добавления в сумматор невозможна";
            case 149:
                return "Печать итоговой этикетки запрещена";
            case 150:
                return "Ошибка при попытке установки нуля";
            case 151:
                return "Ошибка при установке тары";
            case 152:
                return "Вес не фиксирован";
            case 153:
                return "Переполнение стоимости";
            case 161:
                return "Размер изображения превышает лимит";
            case 162:
                return "Неверный номер символа";
            case 163:
                return "Неверный размер символа";
            case 164:
                return "Неверный номер блока";
            case 165:
                return "Сбой часов";
            case 167:
                return "Не реализуется интерфейсом";
            case 168:
                return "Ошибка структуры базы";
            case 169:
                return "Не инициализирована или неисправна SRAM";
            case 170:
                return "Исчерпан лимит попыток обращения с неверным паролем";
        }
    }
    
    private String getMessage(String description, int start, int total, boolean newLineNoSubstring) {
        String message = "";
        if (!description.isEmpty() && start < total) {
            if (newLineNoSubstring) {
                message = description.substring(start, total).split("\n")[0];
                message = message.substring(0, Math.min(message.length(), 50));
            } else {
                String[] splitted = description.substring(start, Math.min(start + 50, total)).split("\n");
                message = splitted.length == 0 ? "" : splitted[0];
            }
        }
        return message;
    }
    
    private boolean isError(Variant value) {
        return !value.toString().equals("0");
    }

    private int clearGoodsDB(List<String> errors, UDPPort port) throws IOException, CommunicationException, InterruptedException {
        ByteBuffer bytes = ByteBuffer.allocate(5);
        bytes.put((byte) 24); //18H
        bytes.put(getPassword().getBytes("cp1251"), 0, 4);
        int result = sendCommand(errors, port, bytes.array());
        if(result == 0) {
            boolean finished = false;
            while(!finished) {
                ByteBuffer stateBytes = ByteBuffer.allocate(1);
                stateBytes.put((byte) 18); //12H
                //12H doesn't work
                finished = true;//sendStateCommand(port, stateBytes.array());
                processTransactionLogger.info("Shtrih: ClearGoodsDB in process...");
                Thread.sleep(5000);
            }
        }
        return result;
    }
    
    private int setMessageData(List<String> errors, UDPPort port, int messageNumber, int stringNumber, String messageString) throws IOException, CommunicationException {
        ByteBuffer bytes = ByteBuffer.allocate(58);
        bytes.put((byte) 82); //52H
        bytes.put(getPassword().getBytes("cp1251"), 0, 4); //4 байта
        bytes.putShort(Short.reverseBytes((short) messageNumber)); //2 байта
        bytes.put((byte) stringNumber); //1 байт
        bytes.put(leftString(messageString, 50).getBytes("cp1251"), 0, 50); //50 байт
        return sendCommand(errors, port, bytes.array());
    }

    private int setPLUDataEx(List<String> errors, UDPPort port, int pluNumber, int barcode, String firstName, String secondName, BigDecimal price,
                             int shelfLife, int groupCode, int messageNumber, Date expiryDate, int goodsType) throws IOException, CommunicationException {
        ByteBuffer bytes = ByteBuffer.allocate(87);
        bytes.put((byte) 87); //57H
        bytes.put(getPassword().getBytes("cp1251"), 0, 4); //4 байта
        bytes.putShort(Short.reverseBytes((short) pluNumber)); //2 байта
        bytes.putInt(Integer.reverseBytes(barcode)); //4 байта
        bytes.put(leftString(firstName, 28).getBytes("cp1251"), 0, 28); //28 байт
        bytes.put(leftString(secondName, 28).getBytes("cp1251"), 0, 28); //28 байт
        bytes.putInt(Integer.reverseBytes(price == null ? 0 : price.multiply(BigDecimal.valueOf(100)).intValue())); //4 байта
        bytes.putShort(Short.reverseBytes((short)shelfLife)); //2 байта
        bytes.putShort(Short.reverseBytes((short)0)); //тара, 2 байта
        bytes.putShort(Short.reverseBytes((short) groupCode)); //2 байта
        bytes.putShort(Short.reverseBytes((short)messageNumber)); //2 байта
        bytes.put((byte) 0); //pictureNumber, 1 байта
        bytes.putInt(Integer.reverseBytes(0)); //ROSTEST, 4 байта
        bytes.put((byte) expiryDate.getDate()); //1 байт
        bytes.put((byte) (expiryDate.getMonth() + 1)); //1 байт
        int year = expiryDate.getYear();
        bytes.put((byte) (year > 100 ? (year - 100) : year)); //1 байт
        //bytes.put((byte) goodsType); //1 байт
        return sendCommand(errors, port, bytes.array());
    }

    private int sendCommand(List<String> errors, UDPPort port, byte[] var1) {
        int attempts = 0;
        while(attempts < 3) {
            try {
                byte[] var3 = new byte[2 + var1.length];
                var3[0] = 2;
                var3[1] = (byte) var1.length;
                System.arraycopy(var1, 0, var3, 2, var1.length);
                port.sendCommand(var3);
                return receiveReply(errors, port);
            } catch(CommunicationException e) {
                attempts++;
                if(attempts == 3)
                    logError(errors, "SendCommand Errors: ", e);
            }
        }
        return -1;
    }

    /*private boolean sendStateCommand(List<String> errors, UDPPort port, byte[] var1) throws CommunicationException {

        byte[] var3 = new byte[2 + var1.length];
        var3[0] = 2;
        var3[1] = (byte) var1.length;

        System.arraycopy(var1, 0, var3, 2, var1.length);

        port.sendCommand(var3);

        return receiveStateReply(errors, port);
    }*/

    private int receiveReply(List<String> errors, UDPPort port) throws CommunicationException {
        try {
            byte[] var2 = new byte[255];
            port.receiveCommand(var2);
            int var5 = var2[3];
            if (var5 < 0) {
                var5 += 256;
            }
            return var5;
        } catch(Exception e) {
            logError(errors, "Receive reply error: ", e);
            return -1;
        }
    }

    /*private boolean receiveStateReply(List<String> errors, UDPPort port) throws CommunicationException {
        try {
        byte[] var2 = new byte[255];
        port.receiveCommand(var2);
        //byte state = var2[1];
        //boolean finished = (0 == ((var2[2] >> 1) & 1));
        //int error = (state >> 3) & 1;
        //if (error != 0) {
        //    throw new RuntimeException("Error clearGoodsDB");
        //}
        return (0 == ((var2[2] >> 1) & 1));
        } catch(Exception e) {
            logError(errors, "Receive reply error: ", e);
            return false;
        }
    }*/

    private String leftString(String var1, int var3) {
        String var4 = var1;
        if(var4.length() > var3) {
            return var4.substring(0, var3);
        } else if(var4.length() == var3) {
            return var4;
        } else {
            while(var4.length() < var3) {
                var4 = var4 + '\u0000';
            }

            return var4;
        }
    }

    private String getPassword() {
        return rightString(password, '0', 4);
    }

    private String rightString(String var1, char var2, int var3) {
        String var4 = var1;
        if(var4.length() > var3) {
            return var4.substring(0, var3);
        } else if(var4.length() == var3) {
            return var4;
        } else {
            while(var4.length() < var3) {
                var4 = var2 + var4;
            }
            return var4;
        }
    }

    private void logError(List<String> errors, String errorText) {
        logError(errors, errorText, null);
    }

    private void logError(List<String> errors, String errorText, Throwable t) {
        errors.add(errorText + (t == null ? "" : ('\n' + ExceptionUtils.getStackTraceString(t))));
        processTransactionLogger.error(errorText, t);
    }
}
