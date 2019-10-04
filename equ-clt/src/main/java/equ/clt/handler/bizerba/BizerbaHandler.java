package equ.clt.handler.bizerba;

import com.google.common.base.Throwables;
import equ.api.ItemInfo;
import equ.api.MachineryInfo;
import equ.api.StopListInfo;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
import equ.clt.EquipmentServer;
import equ.clt.handler.MultithreadScalesHandler;
import equ.clt.handler.ScalesSettings;
import equ.clt.handler.TCPPort;
import lsfusion.base.ExceptionUtils;
import lsfusion.base.Pair;
import lsfusion.base.col.heavy.OrderedMap;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import javax.naming.CommunicationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;

public abstract class BizerbaHandler extends MultithreadScalesHandler {

    //Таблица PLST – список все PLUшек
    //Таблица ATST – список всех доп.текстов
    //Таблица ETST – общие настройки этикеток.
    //Таблица FOST – этикетки BLD.
    //Таблица MDST - изображения

    //Errors
    //1615: кончилась память под состав. Очистить и записать заново
    //4470: memory is full

    protected static final int[] encoders1 = new int[]{65, 192, 66, 193, 194, 69, 195, 197, 198, 199, 75, 200, 77, 72, 79, 201, 80, 67, 84, 202, 203, 88, 208, 209, 210, 211, 212, 213, 215, 216, 217, 218, 97, 224, 236, 225, 226, 101, 227, 229, 230, 231, 237, 232, 238, 239, 111, 233};
    protected static final int[] encoders2 = new int[]{112, 99, 253, 234, 235, 120, 240, 241, 242, 243, 244, 245, 247, 248, 249, 250};

    protected static char separator = '\u001b';
    protected static String endCommand = separator + "BLK " + separator;

    @Override
    protected String getLogPrefix() {
        return "Bizerba: ";
    }

    protected String getCharset() {
        return "utf-8";
    }

    protected boolean isEncode() {
        return false;
    }

    protected FileSystemXmlApplicationContext springContext;

    public BizerbaHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    @Override
    public String getGroupId(TransactionScalesInfo transactionInfo) {
        ScalesSettings bizerbaSettings = springContext.containsBean("bizerbaSettings") ? (ScalesSettings) springContext.getBean("bizerbaSettings") : null;
        boolean allowParallel = bizerbaSettings == null || bizerbaSettings.isAllowParallel();
        if (allowParallel) {
            return super.getGroupId(transactionInfo);
        } else return getModel();
    }

    @Override
    protected SendTransactionTask getTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
        ScalesSettings bizerbaSettings = springContext.containsBean("bizerbaSettings") ? (ScalesSettings) springContext.getBean("bizerbaSettings") : null;
        boolean capitalLetters = bizerbaSettings != null && bizerbaSettings.isCapitalLetters();
        boolean notInvertPrices = bizerbaSettings != null && bizerbaSettings.isNotInvertPrices();
        return new BizerbaSendTransactionTask(transaction, scales, capitalLetters, notInvertPrices);
    }

    class BizerbaSendTransactionTask extends SendTransactionTask {
        boolean capitalLetters;
        boolean notInvertPrices;

        public BizerbaSendTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales, boolean capitalLetters, boolean notInvertPrices) {
            super(transaction, scales);
            this.capitalLetters = capitalLetters;
            this.notInvertPrices = notInvertPrices;
        }

        @Override
        protected Pair<List<String>, Boolean> run() {
            List<String> localErrors = new ArrayList<>();
            boolean cleared = false;
            TCPPort port = new TCPPort(scales.port, 1025);
            String openPortResult = openPort(port, scales.port, true);
            if(openPortResult != null) {
                localErrors.add(openPortResult + ", transaction: " + transaction.id + ";");
            } else {
                int globalError = 0;
                try {
                    boolean needToClear = !transaction.itemsList.isEmpty() && transaction.snapshot && !scales.cleared;
                    if (needToClear) {
                        cleared = clearAll(localErrors, port, scales);
                    }

                    if(cleared || !needToClear) {
                        processTransactionLogger.info("Bizerba: Sending items..." + scales.port);
                        if (localErrors.isEmpty()) {
                            synchronizeTime(localErrors, port, scales.port);
                            int count = 0;
                            for (ScalesItemInfo item : transaction.itemsList) {
                                count++;
                                if (!Thread.currentThread().isInterrupted() && globalError < 5) {
                                    if (item.idBarcode != null && item.idBarcode.length() <= 5) {
                                        processTransactionLogger.info(String.format("Bizerba: IP %s, Transaction #%s, sending item #%s (barcode %s) of %s", scales.port, transaction.id, count, item.idBarcode, transaction.itemsList.size()));
                                        int attempts = 0;
                                        String result = null;
                                        while((result == null || !result.equals("0")) && attempts < 3) {
                                            result = loadPLU(localErrors, port, scales, item, capitalLetters, notInvertPrices);
                                            attempts++;
                                        }
                                        if (result != null && !result.equals("0")) {
                                            logError(localErrors, String.format("Bizerba: IP %s, Result %s, item %s", scales.port, result, item.idItem));
                                            globalError++;
                                        }
                                    } else {
                                        processTransactionLogger.info(String.format("Bizerba: IP %s, Transaction #%s, item #%s: incorrect barcode %s", scales.port, transaction.id, count, item.idBarcode));
                                    }
                                } else break;
                            }
                        }
                        port.close();
                    }

                } catch (Exception e) {
                    logError(localErrors, String.format("Bizerba: IP %s error, transaction %s;", scales.port, transaction.id), e);
                } finally {
                    processTransactionLogger.info("Bizerba: Finally disconnecting..." + scales.port);
                    try {
                        port.close();
                    } catch (CommunicationException e) {
                        logError(localErrors, String.format("Bizerba: IP %s close port error ", scales.port), e);
                    }
                }
            }
            processTransactionLogger.info("Bizerba: Completed ip: " + scales.port);
            return Pair.create(localErrors, cleared);
        }
    }

    public void sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machineryInfoSet) {
        try {
            if (!stopListInfo.stopListItemMap.isEmpty() && !stopListInfo.exclude) {
                processStopListLogger.info("Bizerba: Starting sending StopLists to " + machineryInfoSet.size() + " scales...");
                Collection<Callable<List<String>>> taskList = new LinkedList<>();
                for (MachineryInfo machinery : machineryInfoSet) {
                    TCPPort port = new TCPPort(machinery.port, 1025);
                    if (machinery.port != null && machinery instanceof ScalesInfo) {
                        taskList.add(new SendStopListTask(stopListInfo, (ScalesInfo) machinery, port));
                    }
                }

                if (!taskList.isEmpty()) {
                    ExecutorService singleTransactionExecutor = EquipmentServer.getFixedThreadPool(taskList.size(), "BizerbaSendStopList");
                    List<Future<List<String>>> threadResults = singleTransactionExecutor.invokeAll(taskList);
                    for (Future<List<String>> threadResult : threadResults) {
                        if (!threadResult.get().isEmpty())
                            processStopListLogger.error(threadResult.get().get(0));
                            //throw new RuntimeException(threadResult.get().get(0));
                    }
                    singleTransactionExecutor.shutdown();
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private String openPort(TCPPort port, String ip, boolean transaction) {
        try {
            (transaction ? processTransactionLogger : processStopListLogger).info("Bizerba: Connecting..." + ip);
            port.open();
        } catch (Exception e) {
            (transaction ? processTransactionLogger : processStopListLogger).error("Bizerba Error: ", e);
            return e.getMessage();
        }
        return null;
    }

    protected String receiveReply(List<String> errors, TCPPort port, String ip) {
        return receiveReply(errors, port, ip, false);
    }

    private String receiveReply(List<String> errors, TCPPort port, String ip, boolean longAction) {
        String reply;
        Pattern pattern = Pattern.compile("QUIT(\\d+)");
        byte[] var4 = new byte[500];

        try {
            long startTime = new Date().getTime();

            long time;
            do {
                if(port.getBisStream().available() != 0) {
                    port.getBisStream().read(var4);
                    reply = new String(var4, getCharset());

                    Matcher matcher = pattern.matcher(reply);
                    if (matcher.find()) {
                        if (longAction)
                            processTransactionLogger.info(String.format("Bizerba: IP %s action finished: %s", ip, reply));
                        return matcher.group(1);
                    } else if (longAction)
                        processTransactionLogger.info(String.format("Bizerba: IP %s action continues: %s", ip, reply));
                }

                Thread.sleep(10L);
                time = (new Date()).getTime();
            } while(time - startTime <= (longAction ? 3600000L : 10000L)); //1 hour : 10 seconds

            if (longAction) {
                processTransactionLogger.info(String.format("Bizerba: IP %s scales reply timeout", ip));
                return "0";
            }
            else {
                logError(errors, String.format("Bizerba: IP %s scales reply timeout", ip));
                return "-1";
            }
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            logError(errors, String.format("Bizerba: IP %s receive Reply Error", ip), e);
        }catch (Exception e) {
            logError(errors, String.format("Bizerba: IP %s receive Reply Error", ip), e);
        }
        return "-1";
    }

    protected String zeroedInt(int value, int len) {
        String result = String.valueOf(value);
        if (result.length() > len)
            result = result.substring(0, len);
        while (result.length() < len)
            result = "0" + result;
        return result;
    }

    protected void clearReceiveBuffer(TCPPort port) {
        while (true) {
            try {
                if (port.getBisStream().available() > 0) {
                    port.getBisStream().read();
                    continue;
                }
            } catch (Exception ignored) {
            }
            return;
        }
    }

    private String makeString(int var1) {
        String var2 = Integer.toHexString(var1);
        while(var2.length() < 8) {
            var2 = '0' + var2;
        }
        var2 = var2.substring(0, 2) + '@' + var2.substring(2, 4) + '@' + var2.substring(4, 6) + '@' + var2.substring(6, 8);
        return var2.toUpperCase();
    }

    private void encode(byte[] var1) {
        for(int var2 = 0; var2 < var1.length; ++var2) {
            if(var1[var2] <= -81 && var1[var2] >= -128) {
                var1[var2] = (byte)encoders1[128 + var1[var2]];
            } else if(var1[var2] <= -17 && var1[var2] >= -32) {
                var1[var2] = (byte)encoders2[32 + var1[var2]];
            }
        }
    }

    /*private void decode(byte[] var1) {
        for(int var4 = 0; var4 < var1.length; ++var4) {
            boolean var3 = false;
            int var2 = var1[var4];
            if(var2 < 0) {
                var2 += 256;
            }

            int var5;
            for(var5 = 0; var5 < encoders1.length; ++var5) {
                if(var2 == encoders1[var5]) {
                    var1[var4] = (byte)(-128 + var5);
                    var3 = true;
                }
            }

            if(!var3) {
                for(var5 = 0; var5 < encoders2.length; ++var5) {
                    if(var2 == encoders2[var5]) {
                        var1[var4] = (byte)(-32 + var5);
                        var3 = true;
                    }
                }
            }
        }
    }*/

    protected Integer getPluNumber(ItemInfo itemInfo) {
        return getPluNumber(null, itemInfo);
    }

    private Integer getPluNumber(ScalesInfo scalesInfo, ItemInfo itemInfo) {
        try {
            Integer pluNumber = scalesInfo == null ? null : itemInfo.stockPluNumberMap.get(scalesInfo.idStock);
            return pluNumber != null ? pluNumber : (itemInfo.pluNumber != null ? itemInfo.pluNumber : Integer.parseInt(itemInfo.idBarcode));
        } catch (Exception e) {
            return 0;
        }
    }

    protected String getCancelFlag(Integer flag) {
        return "WALO" + flag; //Cancel flag: 0= record is modified or created; 1= record is deleted
    }

    protected void sendCommand(List<String> errors, TCPPort port, String command, String ip) {
        try {
            byte[] commandBytes = command.getBytes(getCharset());
            if(isEncode())
                encode(commandBytes);
            port.getOutputStream().write(commandBytes);
            port.getOutputStream().flush();
        } catch (IOException e) {
            logError(errors, String.format("Bizerba: %s Send command exception: ", ip), e);
        }
    }

    protected boolean clearAll(List<String> errors, TCPPort port, ScalesInfo scales) throws InterruptedException {
        processTransactionLogger.info(String.format("Bizerba: IP %s ClearAllPLU", scales.port));
        boolean result = true;
        String clear = clearAllPLU(errors, port, scales, scales.port);
        if (!clear.equals("0")) {
            logError(errors, String.format("Bizerba: IP %s ClearAllPLU, Error %s", scales.port, clear));
            result = false;
        } else {
            processTransactionLogger.info(String.format("Bizerba: IP %s ClearAllMessages", scales.port));
            clear = clearAllMessages(errors, port, scales, scales.port);
            if (!clear.equals("0")) {
                logError(errors, String.format("Bizerba: IP %s ClearAllMessages, Error %s", scales.port, clear));
                result = false;
            }
        }
        Thread.sleep(20000);
        return result;
    }

    private String clearAllMessages(List<String> errors, TCPPort port, ScalesInfo scales, String ip) {
        String command = "ATST  " + separator +"L" + zeroedInt(scales.number, 2) + endCommand;
        clearReceiveBuffer(port);
        sendCommand(errors, port, command, ip);
        return receiveReply(errors, port, ip, true);
    }

    private String clearAllPLU(List<String> errors, TCPPort port, ScalesInfo scales, String ip) {
        String command = "PLST  " + separator + "L" + zeroedInt(scales.number, 2) + endCommand;
        clearReceiveBuffer(port);
        sendCommand(errors, port, command, ip);
        return receiveReply(errors, port, ip, true);
    }

    private String clearMessage(List<String> errors, TCPPort port, ScalesInfo scales, int messageNumber) {
        String command = "ATST  " + separator + "S" + zeroedInt(scales.number, 2) + separator + getCancelFlag(1) + separator + "ATNU" + messageNumber + endCommand;
        clearReceiveBuffer(port);
        sendCommand(errors, port, command, scales.port);
        String result = receiveReply(errors, port, scales.port);
        return result.equals("0") ? null : result;
    }

    public String clearPLU(List<String> errors, TCPPort port, ScalesInfo scales, ItemInfo item) {
        Integer plu = getPluNumber(scales, item);
        processStopListLogger.info(String.format("Bizerba: clearing plu %s", plu));
        String command = "PLST  \u001bS" + zeroedInt(scales.number, 2) + separator + getCancelFlag(1) + separator
                + "PNUM" + plu + separator + "ABNU1" + separator + "ANKE0" + separator + "KLAR1" + separator
                + "GPR10" + separator + "WGNU" + getIdItemGroup(item) + separator + "ECO1" + plu + separator
                + "HBA10" + separator + "HBA20" + separator + "KLGE0" + separator + "ALT10" + separator + "PLTEXXX"
                + endCommand;
        clearReceiveBuffer(port);
        sendCommand(errors, port, command, scales.port);
        return receiveReply(errors, port, scales.port);
    }

    private String loadPLUMessages(List<String> errors, TCPPort port, ScalesInfo scales, Map<Integer, String> messageMap, ScalesItemInfo item, String ip) {
        for (Map.Entry<Integer, String> entry : messageMap.entrySet()) {
            Integer messageNumber = entry.getKey();
            String messageText = entry.getValue();
            messageText = messageText == null ? "" : messageText;
            String message = "ATST  " + separator + "S" + zeroedInt(scales.number, 2) + separator + getCancelFlag(0) + separator + "ATNU" + messageNumber + separator + "ATTE" + messageText + endCommand;
            clearReceiveBuffer(port);
            sendCommand(errors, port, message, ip);
            String result = receiveReply(errors, port, ip);
            String error = getError(result, ip, item.idItem, messageNumber);
            if (error != null) {
                logError(errors, error);
                return result;
            }
        }
        return null;
    }

    private Map<Integer, String> getMessageMap(List<String> errors, TCPPort port, ScalesInfo scales, ScalesItemInfo item) {
        OrderedMap<Integer, String> messageMap = new OrderedMap<>();
        Integer pluNumber = getPluNumber(item);
        int count = 0;
        String description = trimToEmpty(item.description);
        if(!description.isEmpty()) {
            if(description.length() > 3000)
                description = description.substring(0, 2999);
            List<String> splittedMessage = new ArrayList<>();
            for (String line : description.split("\\\\n")) {
                while (line.length() > 750) {
                    splittedMessage.add(line.substring(0, 749));
                    line = line.substring(749);
                }
                splittedMessage.add(line);
            }

            boolean isDouble = splittedMessage.size() > 4;
            for (int i = 0; i < splittedMessage.size(); i = i + (isDouble ? 2 : 1)) {
                String line = splittedMessage.get(i) + (isDouble && (i + 1 < splittedMessage.size()) ? (" " + splittedMessage.get(i + 1)) : "");
                line = line.replace('@', 'a');
                if (line.length() >= 750) {
                    line = line.substring(0, 749);
                }
                int messageNumber = pluNumber * 10 + count;
                messageMap.put(messageNumber, line);
                ++count;
            }
        }
        while (count < 4) {
            clearMessage(errors, port, scales, pluNumber * 10 + count);
            ++count;
        }
        return messageMap;
    }

    private String loadPLU(List<String> errors, TCPPort port, ScalesInfo scales, ScalesItemInfo item, boolean capitalLetters, boolean notInvertPrices) {

        Integer pluNumber = getPluNumber(item);

        String command2 = "TFZU@00@04";
        String captionItem = trimToEmpty(item.name).replace('@', 'a');
        if(capitalLetters)
            captionItem = captionItem.toUpperCase();
        if (captionItem.isEmpty())
            logError(errors, String.format("Bizerba: IP %s, PLU name is invalid. Name is empty (item: %s)", scales.port, item.idItem));

        int department = 1;
        boolean manualWeight = false;

        Map<Integer, String> messageMap = getMessageMap(errors, port, scales, item);
        String result = loadPLUMessages(errors, port, scales, messageMap, item, scales.port);
        if(result != null) {
            return result;
        }
        result = loadImages(errors, scales, port, item);
        if(result != null) {
            return result;
        }

        int i = 0;
        String altCommand = "";
        for (Integer messageNumber : messageMap.keySet()) {
            if (i < 4) {
                altCommand += "ALT" + (i + 1) + messageNumber + separator;
            }
            if (i < 10) {
                command2 += "@" + makeString(messageNumber);
            }
            i++;
        }

        for (int count = i; count < 10; count++) {
            command2 += "@00@00@00@00";
        }

        byte priceOverflow = 0;
        int price = getPrice(item.price);
        if (price > 999999) {
            price = Math.round((float) (price / 10));
            priceOverflow = 1;
        }
        if (price > 999999 || price < 0) {
            logError(errors, String.format("Bizerba: IP %s PLU price is invalid. Price is %s (item: %s)", scales.port, price, item.idItem));
        }

        int retailPrice = getPrice(item.retailPrice);
        if (retailPrice > 999999) {
            retailPrice = Math.round((float) (retailPrice / 10));
        }
        if (retailPrice > 999999 || retailPrice < 0) {
            logError(errors, String.format("Bizerba: IP %s PLU retail price is invalid. Retail price is %s (item: %s)", scales.port, retailPrice, item.idItem));
        } else if(retailPrice == 0)
            retailPrice = price;

        if (pluNumber <= 0 || pluNumber > 999999) {
            return "0";
        }

        if (item.daysExpiry == null)
            item.daysExpiry = 0;

        if (item.daysExpiry > 999 || item.daysExpiry < 0) {
            item.daysExpiry = 0;
            //logError(errors, String.format("PLU expired is invalid. Expired is %s (item: %s)", item.daysExpiry, item.idItem));
            //пока временно не грузим
        }

        String command1 = "PLST  " + separator + "S" + zeroedInt(scales.number, 2) + separator + getCancelFlag(0) + separator + "PNUM" + pluNumber + separator + "ABNU" + department + separator + "ANKE0" + separator;
        boolean nonWeight = item.shortNameUOM != null && item.shortNameUOM.toUpperCase().startsWith("ШТ");
        if (!manualWeight) {
            if (nonWeight) {
                command1 = command1 + "KLAR1" + separator;
            } else {
                command1 = command1 + "KLAR0" + separator;
            }
        } else {
            command1 = command1 + "KLAR4" + separator;
        }

        command1+= getPricesCommand(price, retailPrice, notInvertPrices);

        String prefix = scales.pieceCodeGroupScales != null && nonWeight ? scales.pieceCodeGroupScales : scales.weightCodeGroupScales;
        String idBarcode = item.idBarcode != null && prefix != null && item.idBarcode.length() == 5 ? ("0" + prefix + item.idBarcode + "00000") : item.idBarcode;
        Integer tareWeight = 0;
        Integer tarePercent = getTarePercent(item);
        command1 = command1 + "RABZ1" + separator + "PTYP4" + separator + "WGNU" + getIdItemGroup(item) + separator + "ECO1" + idBarcode
                + separator + "HBA1" + item.daysExpiry + separator + "HBA20" + separator + "TARA" + tareWeight + separator + "TAPR" + tarePercent
                + separator + "KLGE" + priceOverflow + separator + altCommand + "PLTE" + captionItem + separator;
        if (!command2.isEmpty()) {
            command1 = command1 + command2 + separator;
        }

        command1 = command1 + "BLK " + separator;
        clearReceiveBuffer(port);
        sendCommand(errors, port, command1, scales.port);
        return receiveReply(errors, port, scales.port);
    }

    protected String getPricesCommand(int price, int retailPrice, boolean notInvertPrices) {
        return "GPR1" + retailPrice + separator + "EXPR" + price + separator;
    }

    private int getPrice(BigDecimal price) {
        return price == null ? 0 : price.multiply(BigDecimal.valueOf(100)).intValue();
    }

    protected abstract String getModel();

    public Integer getTarePercent(ScalesItemInfo item) {
        return 0;
    }

    protected String getIdItemGroup(ItemInfo item) {
        return "1";
    }

    protected String loadImages(List<String> errors, ScalesInfo scales, TCPPort port, ScalesItemInfo item) {
        return null;
    }

    private String synchronizeTime(List<String> errors, TCPPort port, String ip) {
        long timeZero = new Date(1970-1900, Calendar.JANUARY, 1, 0, 0, 0).getTime() / 1000;
        String command = "UHR   " + separator + "N00" + separator + "UUHR" + (System.currentTimeMillis() / 1000 - timeZero) + endCommand;
        clearReceiveBuffer(port);
        sendCommand(errors, port, command, ip);
        return receiveReply(errors, port, ip, false);
    }

    private String getError(String result, String ip, String idItem, Integer messageNumber) {
        String error = null;
        if (result.equals("1615")) {
            error = getLogPrefix() + String.format("IP %s, item %s [msgNo=%s]. Кончилась память под состав. Очистить и записать заново [%s]", ip, idItem, messageNumber, result);
        } else if (!result.equals("0")) {
            error = getLogPrefix() + String.format("IP %s, item %s [msgNo=%s]. Result is %s", ip, idItem, messageNumber, result);
        }
        return error;
    }

    protected void logError(List<String> errors, String errorText) {
        logError(errors, errorText, null);
    }

    protected void logError(List<String> errors, String errorText, Throwable t) {
        errors.add(errorText.replace("\u001b", "").replace("\u0000", "") + (t == null ? "" : ('\n' + ExceptionUtils.getStackTraceString(t))));
        processTransactionLogger.error(errorText, t);
    }

    class SendStopListTask implements Callable<List<String>> {
        StopListInfo stopListInfo;
        ScalesInfo scales;
        TCPPort port;

        public SendStopListTask(StopListInfo stopListInfo, ScalesInfo scales, TCPPort port) {
            this.stopListInfo = stopListInfo;
            this.scales = scales;
            this.port = port;
        }

        @Override
        public List<String> call() {
            List<String> localErrors = new ArrayList<>();
            String openPortResult = openPort(port, scales.port, false);
            if(openPortResult != null) {
                localErrors.add(openPortResult);
            } else {
                int globalError = 0;
                try {

                    processStopListLogger.info("Bizerba: Sending StopLists..." + scales.port);
                    int count = 0;
                    for (ItemInfo item : stopListInfo.stopListItemMap.values()) {
                        count++;
                        if (!Thread.currentThread().isInterrupted() && globalError < 5) {
                            if (item.idBarcode != null && item.idBarcode.length() <= 5) {
                                if(!skip(item.idItem)) {
                                    processStopListLogger.info(String.format("Bizerba: IP %s, sending StopList for item #%s (barcode %s) of %s", scales.port, count, item.idBarcode, stopListInfo.stopListItemMap.values().size()));
                                    String result = clearPLU(localErrors, port, scales, item);
                                    if (!result.equals("0")) {
                                        logError(localErrors, String.format("Bizerba: IP %s, Result %s, item %s", scales.port, result, item.idItem));
                                        globalError++;
                                    }
                                }
                            } else {
                                processStopListLogger.info(String.format("Bizerba: IP %s, item #%s: incorrect barcode %s", scales.port, count, item.idBarcode));
                            }
                        } else break;
                    }
                    port.close();

                } catch (Exception e) {
                    logError(localErrors, String.format("Bizerba: IP %s error ", scales.port), e);
                } finally {
                    processStopListLogger.info("Bizerba: Finally disconnecting..." + scales.port);
                    try {
                        port.close();
                    } catch (CommunicationException e) {
                        logError(localErrors, String.format("Bizerba: IP %s close port error ", scales.port), e);
                    }
                }
            }
            processStopListLogger.info("Bizerba: Completed ip: " + scales.port);
            return localErrors;
        }

        private boolean skip(String idItem) {
            Set<String> skuSet = stopListInfo.inGroupMachineryItemMap.get(scales.numberGroup);
            return skuSet == null || !skuSet.contains(idItem);
        }

    }
}