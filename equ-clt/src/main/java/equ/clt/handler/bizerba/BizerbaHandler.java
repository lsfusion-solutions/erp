package equ.clt.handler.bizerba;

import equ.api.MachineryInfo;
import equ.api.SendTransactionBatch;
import equ.api.scales.*;
import equ.clt.handler.ScalesSettings;
import lsfusion.base.OrderedMap;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import javax.naming.CommunicationException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BizerbaHandler extends ScalesHandler {

    //Таблица PLST – список все PLUшек
    //Таблица ATST – список всех доп.текстов
    //Таблица ETST – общие настройки этикеток.
    //Таблица FOST – этикетки BLD.

    //Errors
    //1615: кончилась память под состав. Очистить и записать заново
    //4470: memory is full

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected static final int[] encoders1 = new int[]{65, 192, 66, 193, 194, 69, 195, 197, 198, 199, 75, 200, 77, 72, 79, 201, 80, 67, 84, 202, 203, 88, 208, 209, 210, 211, 212, 213, 215, 216, 217, 218, 97, 224, 236, 225, 226, 101, 227, 229, 230, 231, 237, 232, 238, 239, 111, 233};
    protected static final int[] encoders2 = new int[]{112, 99, 253, 234, 235, 120, 240, 241, 242, 243, 244, 245, 247, 248, 249, 250};

    protected static char separator = '\u001b';
    protected static String endCommand = separator + "BLK " + separator;

    protected FileSystemXmlApplicationContext springContext;

    public BizerbaHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    protected String getGroupId(FileSystemXmlApplicationContext springContext, TransactionScalesInfo transactionInfo, String model) {
        ScalesSettings bizerbaSettings = springContext.containsBean("bizerbaSettings") ? (ScalesSettings) springContext.getBean("bizerbaSettings") : null;
        boolean allowParallel = bizerbaSettings == null || bizerbaSettings.isAllowParallel();
        if (allowParallel) {
            String groupId = "";
            for (MachineryInfo scales : transactionInfo.machineryInfoList) {
                groupId += scales.port + ";";
            }
            return model + groupId;
        } else return model;
    }

    public Map<Integer, SendTransactionBatch> sendTransaction(List<TransactionScalesInfo> transactionList, String charset, boolean encode) throws IOException {

        Map<Integer, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        Map<String, String> brokenPortsMap = new HashMap<>();
        for(TransactionScalesInfo transaction : transactionList) {
            processTransactionLogger.info("Bizerba: Send Transaction # " + transaction.id);

            ScalesSettings bizerbaSettings = springContext.containsBean("bizerbaSettings") ? (ScalesSettings) springContext.getBean("bizerbaSettings") : null;
            boolean capitalLetters = bizerbaSettings != null && bizerbaSettings.isCapitalLetters();

            List<MachineryInfo> succeededScalesList = new ArrayList<>();
            Exception exception = null;
            try {

                if (!transaction.machineryInfoList.isEmpty()) {

                    List<ScalesInfo> enabledScalesList = getEnabledScalesList(transaction, succeededScalesList);
                    Map<String, List<String>> errors = new HashMap<>();
                    Set<String> ips = new HashSet<>();

                    processTransactionLogger.info("Bizerba: Starting sending to " + enabledScalesList.size() + " scale(s)...");
                    Collection<Callable<SendTransactionResult>> taskList = new LinkedList<>();
                    for (ScalesInfo scales : enabledScalesList) {
                        TCPPort port = new TCPPort(scales.port, 1025);
                        if (scales.port != null) {
                            ips.add(scales.port);
                            if (openPort(port, scales.port, brokenPortsMap, errors))
                                taskList.add(new SendTransactionTask(transaction, scales, port, charset, encode, capitalLetters));
                        }
                    }

                    if(!taskList.isEmpty()) {
                        ExecutorService singleTransactionExecutor = Executors.newFixedThreadPool(taskList.size());
                        List<Future<SendTransactionResult>> threadResults = singleTransactionExecutor.invokeAll(taskList);
                        for (Future<SendTransactionResult> threadResult : threadResults) {
                            if(threadResult.get().localErrors.isEmpty())
                                succeededScalesList.add(threadResult.get().scalesInfo);
                            else {
                                brokenPortsMap.put(threadResult.get().scalesInfo.port, threadResult.get().localErrors.get(0));
                                errors.put(threadResult.get().scalesInfo.port, threadResult.get().localErrors);
                            }
                        }
                    }
                    if(!enabledScalesList.isEmpty())
                    errorMessages(errors, ips, brokenPortsMap);

                }
            } catch (Exception e) {
                exception = e;
            }
            sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(succeededScalesList, exception));
        }
        return sendTransactionBatchMap;
    }

    private boolean openPort(TCPPort port, String ip, Map<String, String> brokenPortsMap, Map<String, List<String>> errors) {
        boolean result = false;
        try {
            if (brokenPortsMap.containsKey(ip)) {
                errors.put(ip, Collections.singletonList(brokenPortsMap.get(ip)));
            } else {
                processTransactionLogger.info("Bizerba: Connecting..." + ip);
                port.open();
                result = true;
            }
        } catch (Exception e) {
            processTransactionLogger.error(e);
            brokenPortsMap.put(ip, "Bizerba: error " + e.getMessage());
            errors.put(ip, Collections.singletonList(e.getMessage()));
        }
        return result;
    }

    protected List<ScalesInfo> getEnabledScalesList(TransactionScalesInfo transaction, List<MachineryInfo> succeededScalesList) {
        List<ScalesInfo> enabledScalesList = new ArrayList<>();
        for (ScalesInfo scales : transaction.machineryInfoList) {
            if(scales.succeeded)
                succeededScalesList.add(scales);
            else if (scales.enabled)
                enabledScalesList.add(scales);
        }
        if (enabledScalesList.isEmpty())
            for (ScalesInfo scales : transaction.machineryInfoList) {
                if (!scales.succeeded)
                    enabledScalesList.add(scales);
            }
        return enabledScalesList;
    }

    protected void errorMessages(Map<String, List<String>> errors, Set<String> ips, Map<String, String> brokenPortsMap) {
        if (!errors.isEmpty()) {
            String message = "";
            for (Map.Entry<String, List<String>> entry : errors.entrySet()) {
                message += entry.getKey() + ": \n";
                for (String error : entry.getValue()) {
                    message += error + "\n";
                }
            }
            throw new RuntimeException(message);
        } else if (ips.isEmpty() && brokenPortsMap.isEmpty())
            throw new RuntimeException("Bizerba: No IP-addresses defined");
    }

    protected String receiveReply(List<String> errors, TCPPort port, String charset, String ip) throws CommunicationException {
        return receiveReply(errors, port, charset, ip, false);
    }

    private String receiveReply(List<String> errors, TCPPort port, String charset, String ip, boolean longAction) throws CommunicationException {
        String reply;
        Pattern pattern = Pattern.compile("QUIT(\\d+)");
        byte[] var4 = new byte[500];

        try {
            long startTime = new Date().getTime();

            long time;
            do {
                if(port.getBisStream().available() != 0) {
                    port.getBisStream().read(var4);
                    reply = new String(var4, charset);

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

    private String zeroedInt(int value, int len) {
        String result = String.valueOf(value);
        while (result.length() < len)
            result = "0" + result;
        return result;
    }

    private void clearReceiveBuffer(TCPPort port) {
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
        if(var2.length() > 8) {
            var2 = var2.substring(0, 8);
        }
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

    private Integer getPluNumber(ScalesItemInfo itemInfo) {
        try {
            return itemInfo.pluNumber == null ? Integer.parseInt(itemInfo.idBarcode) : itemInfo.pluNumber;
        } catch (Exception e) {
            return 0;
        }
    }

    private void sendCommand(List<String> errors, TCPPort port, String command, String charset, String ip, boolean encode) throws CommunicationException, IOException {
        try {
            byte[] commandBytes = command.getBytes(charset);
            if(encode)
                encode(commandBytes);
            port.getOutputStream().write(commandBytes);
            port.getOutputStream().flush();
        } catch (IOException e) {
            logError(errors, String.format("Bizerba: %s Send command exception: ", ip), e);
        }
    }

    protected void clearAll(List<String> errors, TCPPort port, ScalesInfo scales, String charset, boolean encode) throws InterruptedException, IOException, CommunicationException {
        processTransactionLogger.info(String.format("Bizerba: IP %s ClearAllPLU", scales.port));
        String clear = clearAllPLU(errors, port, scales, charset, scales.port, encode);
        if (!clear.equals("0"))
            logError(errors, String.format("Bizerba: IP %s ClearAllPLU, Error %s", scales.port, clear));
        processTransactionLogger.info(String.format("Bizerba: IP %s ClearAllMessages", scales.port));
        clear = clearAllMessages(errors, port, scales, charset, scales.port, encode);
        if (!clear.equals("0"))
            logError(errors, String.format("Bizerba: IP %s ClearAllMessages, Error %s", scales.port, clear));
        Thread.sleep(10000);
    }

    private String clearAllMessages(List<String> errors, TCPPort port, ScalesInfo scales, String charset, String ip, boolean encode) throws CommunicationException, InterruptedException, IOException {
        String command = "ATST  " + separator +"L" + zeroedInt(scales.number, 2) + endCommand;
        clearReceiveBuffer(port);
        sendCommand(errors, port, command, charset, ip, encode);
        return receiveReply(errors, port, charset, ip, true);
    }

    private String clearAllPLU(List<String> errors, TCPPort port, ScalesInfo scales, String charset, String ip, boolean encode) throws CommunicationException, InterruptedException, IOException {
        String command = "PLST  " + separator + "L" + zeroedInt(scales.number, 2) + endCommand;
        clearReceiveBuffer(port);
        sendCommand(errors, port, command, charset, ip, encode);
        return receiveReply(errors, port, charset, ip, true);
    }

    /*private String clearMessages(List<String> errors, TCPPort port, ScalesInfo scales, ScalesItemInfo item, String charset, String ip, boolean encode) throws CommunicationException, IOException {
        for (int i = 0; i <= 9; i++) {
            String command = "ATST  " + separator + "S" + zeroedInt(scales.number, 2) + separator + "WALO1" + separator + "ATNU" + (getPluNumber(item) * 10 + i) + endCommand;
            clearReceiveBuffer(port);
            sendCommand(errors, port, command, charset, ip, encode);
            String result = receiveReply(errors, port, charset, ip);
            if (!result.equals("0"))
                return result;
        }
        return "0";
    }*/

    /*protected String clearMessage(List<String> errors, TCPPort port, ScalesInfo scales, ScalesItemInfo item, boolean splitMessage, String charset, String ip, boolean encode) throws CommunicationException, IOException {
        if(splitMessage) {
            return clearMessages(errors, port, scales, item, charset, ip, encode);
        } else {

            String command = "ATST  " + separator + "S" + zeroedInt(scales.number, 2) + separator + "WALO1" + separator + "ATNU" + getPluNumber(item) * 10 + endCommand;
            clearReceiveBuffer(port);
            sendCommand(errors, port, command, charset, ip, true);
            return receiveReply(errors, port, charset, ip);
        }
    }*/

    private String loadPLUMessages(List<String> errors, TCPPort port, ScalesInfo scales, Map<Integer, String> messageMap, ScalesItemInfo item, String charset, String ip, boolean encode) throws CommunicationException, IOException {
        for (Map.Entry<Integer, String> entry : messageMap.entrySet()) {
            Integer messageNumber = entry.getKey();
            String messageText = entry.getValue();
            messageText = messageText == null ? "" : messageText;
            String message = "ATST  " + separator + "S" + zeroedInt(scales.number, 2) + separator + "WALO0" + separator + "ATNU" + messageNumber + separator + "ATTE" + messageText + endCommand;
            clearReceiveBuffer(port);
            sendCommand(errors, port, message, charset, ip, encode);
            String result = receiveReply(errors, port, charset, ip);
            if (!result.equals("0")) {
                logError(errors, String.format("Bizerba: IP %s Result is %s, item: %s [msgNo=%s]", ip, result, item.idItem, messageNumber));
                return result;
            }
        }
        return "0";
    }

    private Map<Integer, String> getMessageMap(ScalesItemInfo item) {
        OrderedMap<Integer, String> messageMap = new OrderedMap<>();
        Integer pluNumber = getPluNumber(item);
        String description = item.description == null ? "" : item.description;
        if(description.length() > 3000)
            description = description.substring(0, 2999);
        int count = 0;
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
        while(count < 4) {
            int messageNumber = pluNumber * 10 + count;
            messageMap.put(messageNumber, "");
            ++count;
        }
        return messageMap;
    }

    private String loadPLU(List<String> errors, TCPPort port, ScalesInfo scales, ScalesItemInfo item, String charset, boolean encode, boolean capitalLetters) throws CommunicationException, IOException {

        Integer pluNumber = getPluNumber(item);

        String command2 = "TFZU@00@04";
        String captionItem = trim(item.name, "").replace('@', 'a');
        if(capitalLetters)
            captionItem = captionItem.toUpperCase();
        if (captionItem.isEmpty())
            logError(errors, String.format("Bizerba: IP %s, PLU name is invalid. Name is empty (item: %s)", scales.port, item.idItem));

        int department = 1;
        boolean manualWeight = false;
        boolean nonWeight = false;

        Map<Integer, String> messageMap = getMessageMap(item);
        String messagesResult = loadPLUMessages(errors, port, scales, messageMap, item, charset, scales.port, encode);
        if(!messagesResult.equals("0")) {
            return messagesResult;
        } else {
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
            int price = item.price.intValue();
            if (price > 999999) {
                price = Math.round((float) (price / 10));
                priceOverflow = 1;
            }

            if (pluNumber <= 0 || pluNumber > 999999) {
                return "0";
            }

            if (price > 999999 || price < 0) {
                logError(errors, String.format("Bizerba: IP %s PLU price is invalid. Price is %s (item: %s)", scales.port, price, item.idItem));
            }

            if (item.daysExpiry == null)
                item.daysExpiry = 0;

            if (item.daysExpiry > 999 || item.daysExpiry < 0) {
                item.daysExpiry = 0;
//            logError(errors, String.format("PLU expired is invalid. Expired is %s (item: %s)", item.daysExpiry, item.idItem));
//          пока временно не грузим            
            }

            String command1 = "PLST  " + separator + "S" + zeroedInt(scales.number, 2) + separator + "WALO0" + separator + "PNUM" + pluNumber + separator + "ABNU" + department + separator + "ANKE0" + separator;
            if (!manualWeight) {
                if (nonWeight) {
                    command1 = command1 + "KLAR1" + separator;
                } else {
                    command1 = command1 + "KLAR0" + separator;
                }
            } else {
                command1 = command1 + "KLAR4" + separator;
            }

            command1 = command1 + "GPR1" + price + separator;
            Integer exPrice = price;
            if (exPrice > 0) {
                command1 = command1 + "EXPR" + exPrice + separator;
            }

            int BIZERBABS_Group = 1;
            String idBarcode = item.idBarcode != null && scales.weightCodeGroupScales != null && item.idBarcode.length() == 5 ? ("0" + scales.weightCodeGroupScales + item.idBarcode + "00000") : item.idBarcode;
            Integer tareWeight = 0;
            Integer tarePercent = 0;
            command1 = command1 + "RABZ1" + separator + "PTYP4" + separator + "WGNU" + BIZERBABS_Group + separator + "ECO1" + idBarcode
                    + separator + "HBA1" + item.daysExpiry + separator + "HBA20" + separator + "TARA" + tareWeight + separator + "TAPR" + tarePercent
                    + separator + "KLGE" + priceOverflow + separator + altCommand + "PLTE" + captionItem + separator;
            if (!command2.isEmpty()) {
                command1 = command1 + command2 + separator;
            }

            command1 = command1 + "BLK " + separator;
            clearReceiveBuffer(port);
            sendCommand(errors, port, command1, charset, scales.port, encode);
            return receiveReply(errors, port, charset, scales.port);
        }
    }

    private String synchronizeTime(List<String> errors, TCPPort port, String charset, String ip, boolean encode) throws CommunicationException, InterruptedException, IOException {
        long timeZero = new Date(1970-1900, 0, 1, 0, 0, 0).getTime() / 1000;
        String command = "UHR   " + separator + "N00" + separator + "UUHR" + (System.currentTimeMillis() / 1000 - timeZero) + endCommand;
        clearReceiveBuffer(port);
        sendCommand(errors, port, command, charset, ip, encode);
        return receiveReply(errors, port, charset, ip, false);
    }

    protected void logError(List<String> errors, String errorText) {
        logError(errors, errorText, null);
    }

    protected void logError(List<String> errors, String errorText, Throwable t) {
        errors.add(errorText.replace("\u001b", "").replace("\u0000", "") + (t == null ? "" : ('\n' + t.toString())));
        processTransactionLogger.error(errorText, t);
    }

    protected String trim(String input, String defaultValue) {
        return input == null ? defaultValue : input.trim();
    }

    class SendTransactionTask implements Callable<SendTransactionResult> {
        TransactionScalesInfo transaction;
        ScalesInfo scales;
        TCPPort port;
        String charset;
        boolean encode;
        boolean capitalLetters;

        public SendTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales, TCPPort port, String charset, boolean encode, boolean capitalLetters) {
            this.transaction = transaction;
            this.scales = scales;
            this.port = port;
            this.charset = charset;
            this.encode = encode;
            this.capitalLetters = capitalLetters;
        }

        @Override
        public SendTransactionResult call() throws Exception {
            List<String> localErrors = new ArrayList<>();
            int globalError = 0;
            try {
                if (!transaction.itemsList.isEmpty() && transaction.snapshot) {
                    clearAll(localErrors, port, scales, charset, encode);
                }

                processTransactionLogger.info("Bizerba: Sending items..." + scales.port);
                if (localErrors.isEmpty()) {
                    synchronizeTime(localErrors, port, charset, scales.port, encode);
                    int count = 0;
                    for (ScalesItemInfo item : transaction.itemsList) {
                        count++;
                        if (!Thread.currentThread().isInterrupted() && globalError < 5) {
                            if (item.idBarcode != null && item.idBarcode.length() <= 5) {
                                processTransactionLogger.info(String.format("Bizerba: IP %s, Transaction #%s, sending item #%s (barcode %s) of %s", scales.port, transaction.id, count, item.idBarcode, transaction.itemsList.size()));
                                String result = loadPLU(localErrors, port, scales, item, charset, encode, capitalLetters);
                                if (!result.equals("0")) {
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

            } catch (Exception e) {
                logError(localErrors, String.format("Bizerba: IP %s error ", scales.port), e);
            } finally {
                processTransactionLogger.info("Bizerba: Finally disconnecting..." + scales.port);
                try {
                    port.close();
                } catch (CommunicationException e) {
                    logError(localErrors, String.format("Bizerba: IP %s close port error ", scales.port), e);
                }
            }
            processTransactionLogger.info("Bizerba: Completed ip: " + scales.port);
            return new SendTransactionResult(scales, localErrors);
        }

    }

    class SendTransactionResult {
        public ScalesInfo scalesInfo;
        public List<String> localErrors;

        public SendTransactionResult(ScalesInfo scalesInfo, List<String> localErrors) {
            this.scalesInfo = scalesInfo;
            this.localErrors = localErrors;
        }
    }
    
}