package equ.clt.handler.bizerba;

import equ.api.scales.ScalesHandler;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import lsfusion.base.OrderedMap;
import org.apache.log4j.Logger;

import javax.naming.CommunicationException;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BizerbaHandler extends ScalesHandler {

    //Таблица PLST – список все PLUшек
    //Таблица ATST – список всех доп.текстов
    //Таблица ETST – общие настройки этикеток.
    //Таблица FOST – этикетки BLD.

    //Errors
    //4470: memory is full

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected static final int[] encoders1 = new int[]{65, 192, 66, 193, 194, 69, 195, 197, 198, 199, 75, 200, 77, 72, 79, 201, 80, 67, 84, 202, 203, 88, 208, 209, 210, 211, 212, 213, 215, 216, 217, 218, 97, 224, 236, 225, 226, 101, 227, 229, 230, 231, 237, 232, 238, 239, 111, 233};
    protected static final int[] encoders2 = new int[]{112, 99, 253, 234, 235, 120, 240, 241, 242, 243, 244, 245, 247, 248, 249, 250};

    protected static char separator = '\u001b';
    protected static String endCommand = separator + "BLK " + separator;

    protected String receiveReply(List<String> errors, TCPPort port, String charset) throws CommunicationException {
        return receiveReply(errors, port, charset, false);
    }

    private String receiveReply(List<String> errors, TCPPort port, String charset, boolean ignoreTimeout) throws CommunicationException {
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
                        if (ignoreTimeout)
                            processTransactionLogger.info("Bizerba action finished: " + reply);
                        return matcher.group(1);
                    } else if (ignoreTimeout)
                        processTransactionLogger.info("Bizerba action continues: " + reply);
                }

                Thread.sleep(10L);
                time = (new Date()).getTime();
            } while(time - startTime <= 600000L);

            if (ignoreTimeout) {
                processTransactionLogger.info("Scales reply timeout");
                return "0";
            }
            else {
                logError(errors, "Scales reply timeout");
                return "-1";
            }
        } catch (Exception e) {
            logError(errors, "Receive Reply Error", e);
        }
        return "-1";
    }

    private String zeroedInt(int var1, int var2) {
        String var3;
        for (var3 = (new Integer(var1)).toString(); var3.length() < var2; var3 = "0" + var3) {
        }
        return var3.substring(var3.length() - 2);
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

    private void decode(byte[] var1) {
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
        
    }

    private Integer getPluNumber(ScalesItemInfo itemInfo) {
        try {
            return itemInfo.pluNumber == null ? Integer.parseInt(itemInfo.idBarcode) : itemInfo.pluNumber;
        } catch (Exception e) {
            return 0;
        }
    }

    private String replaceDelimiter(String var1) {
        String var2 = var1.replace('\u0007', '\n');
        return var2;
    }

    private void sendCommand(List<String> errors, TCPPort port, String command, String charset, boolean encode) throws CommunicationException, IOException {
        try {
            byte[] commandBytes = command.getBytes(charset);
            if(encode)
                encode(commandBytes);
            port.getOutputStream().write(commandBytes);
            port.getOutputStream().flush();
        } catch (IOException e) {
            logError(errors, "Send command exception: ", e);
        }
    }

    protected void clearAll(List<String> errors, TCPPort port, ScalesInfo scales, String charset, boolean encode) throws InterruptedException, IOException, CommunicationException {
        processTransactionLogger.info("Bizerba: ClearAllPLU");
        String clear = clearAllPLU(errors, port, scales, charset, encode);
        if (!clear.equals("0"))
            logError(errors, String.format("Bizerba: ClearAllPLU, Error %s", clear));
        processTransactionLogger.info("Bizerba: ClearAllMessages");
        clear = clearAllMessages(errors, port, scales, charset, encode);
        if (!clear.equals("0"))
            logError(errors, String.format("Bizerba: ClearAllMessages, Error %s", clear));
    }

    private String clearAllMessages(List<String> errors, TCPPort port, ScalesInfo scales, String charset, boolean encode) throws CommunicationException, InterruptedException, IOException {
        String command = "ATST  \u001bL" + zeroedInt(scales.number, 2) + endCommand;
        clearReceiveBuffer(port);
        sendCommand(errors, port, command, charset, encode);
        return receiveReply(errors, port, charset, true);
    }

    private String clearAllPLU(List<String> errors, TCPPort port, ScalesInfo scales, String charset, boolean encode) throws CommunicationException, InterruptedException, IOException {
        String command = "PLST  \u001bL" + zeroedInt(scales.number, 2) + endCommand;
        clearReceiveBuffer(port);
        sendCommand(errors, port, command, charset, encode);
        return receiveReply(errors, port, charset, true);
    }

    private String clearMessages(List<String> errors, TCPPort port, ScalesInfo scales, ScalesItemInfo item, String charset, boolean encode) throws CommunicationException, IOException {
        for (int i = 0; i <= 9; i++) {
            String command = "ATST  \u001bS" + zeroedInt(scales.number, 2) + separator + "WALO1" + separator + "ATNU" + (getPluNumber(item) * 10 + i) + endCommand;
            clearReceiveBuffer(port);
            sendCommand(errors, port, command, charset, encode);
            String result = receiveReply(errors, port, charset);
            if (!result.equals("0"))
                return result;
        }
        return "0";
    }

    protected String clearMessage(List<String> errors, TCPPort port, ScalesInfo scales, ScalesItemInfo item, boolean splitMessage, String charset, boolean encode) throws CommunicationException, IOException {
        if(splitMessage) {
            return clearMessages(errors, port, scales, item, charset, encode);
        } else {

            String command = "ATST  \u001bS" + zeroedInt(scales.number, 2) + separator + "WALO1" + separator + "ATNU" + getPluNumber(item) * 10 + endCommand;
            clearReceiveBuffer(port);
            sendCommand(errors, port, command, charset, true);
            return receiveReply(errors, port, charset);
        }
    }

    private void loadPLUMessages(List<String> errors, TCPPort port, ScalesInfo scales, Map<Integer, String> messageMap, ScalesItemInfo item, String charset, boolean encode) throws CommunicationException, IOException {
        for (Map.Entry<Integer, String> entry : messageMap.entrySet()) {
            Integer messageNumber = entry.getKey();
            String messageText = entry.getValue();//prepareRusText(messageText)
            messageText = messageText == null ? "" : messageText;
            String message = "ATST  \u001bS" + zeroedInt(scales.number, 2) + separator + "WALO0" + separator + "ATNU" + messageNumber + separator + "ATTE" + messageText + endCommand;
            clearReceiveBuffer(port);
            sendCommand(errors, port, message, charset, encode);
            String result = receiveReply(errors, port, charset);
            if (!result.equals("0")) {
                logError(errors, String.format("Result is %s, item: %s [msgNo=%s]", result, item.idItem, messageNumber));
                break;
            }
        }
    }

    private Map<Integer, String> getMessageMap(ScalesItemInfo item) {
        OrderedMap<Integer, String> messageMap = new OrderedMap<Integer, String>();
        Integer pluNumber = getPluNumber(item);
        if (item.description != null) {
            int count = 0;
            List<String> splittedMessage = new ArrayList<String>();
            for (String line : item.description.split("\\\\n")) {
                while (line.length() > 255) {
                    splittedMessage.add(line.substring(0, 255));
                    line = line.substring(255);
                }
                splittedMessage.add(line);
            }

            boolean isDouble = splittedMessage.size() > 4;
            for (int i = 0; i < splittedMessage.size(); i = i + (isDouble ? 2 : 1)) {
                String line = splittedMessage.get(i) + (isDouble && (i + 1 < splittedMessage.size()) ? (" " + splittedMessage.get(i + 1)) : "");
                line = line.replace('@', 'a');
                if (line.length() >= 255) {
                    line = line.substring(0, 255);
                }
                int messageNumber = pluNumber * 10 + count;
                messageMap.put(messageNumber, line);
                ++count;
            }
        }
        return messageMap;
    }

    protected void loadPLU(List<String> errors, TCPPort port, ScalesInfo scales, ScalesItemInfo item, String charset, boolean encode) throws CommunicationException, IOException {

        Integer pluNumber = getPluNumber(item);

        String var3 = "";
        String command2;
        String captionItem = item.name.trim();
        if (captionItem.isEmpty())
            logError(errors, String.format("PLU name is invalid. Name is empty (item: %s)", item.idItem));

        int department = 1;

        int count;
        int i;
        Map<Integer, String> messageMap = getMessageMap(item);
        loadPLUMessages(errors, port, scales, messageMap, item, charset, encode);
        command2 = "TFZU@00@04";
        i = 0;

        for (Iterator messageMapIterator = messageMap.keySet().iterator(); messageMapIterator.hasNext(); ++i) {
            Integer var9 = (Integer) messageMapIterator.next();
            count = i + 1;
            if (i < 4) {
                var3 = var3 + "ALT" + count + var9 + separator;
            }

            if (i < 10) {
                command2 = command2 + "@" + makeString(var9);
            }
        }

        for (count = i; count < 10; ++count) {
            command2 = command2 + "@00@00@00@00";
        }

        boolean nonWeight = false;
        //if(Configuration.isNonWeightPrefix(var1.barCodePrefix)) {
        //    nonWeight = true;
        //}

            /*if(Configuration.BIZERBABS_AddExpiredToName && var1.expired > 0) {
                if(var1.expiredType == null) {
                    throw new ScalesException("PLU expired type is invalid. Expired type is null");
                }

                String var17 = " Срок годн. ";
                switch(BizerbaBCII.SyntheticClass_1.$SwitchMap$ru$crystalservice$scales$PLU$ExpiredType[var1.expiredType.ordinal()]) {
                    case 1:
                        var17 = var17 + var1.expired + " час.";
                        break;
                    case 2:
                        var17 = var17 + var1.expired + " сут.";
                }

                var15 = var15 + var17;
            }*/

            /*switch(Configuration.BIZERBABCII_PriceDecimal) {
                case 0:
                    var1.price /= 100;
                    var1.exPrice /= 100;
                    break;
                case 1:
                    var1.price /= 10;
                    var1.exPrice /= 10;
            }*/

        byte priceOverflow = 0;
        int price = item.price.intValue();
        if (price > 999999) {
            price = Math.round((float) (price / 10));
            priceOverflow = 1;
        }

        if (pluNumber <= 0 || pluNumber > 999999) {
            return;
            //throw new RuntimeException("PLU number is invalid. Number is " + pluNumber);
        }

        //if(department == 0 || department > 999 || department < 0) {
        //    logError(errors, "PLU department is invalid. Department is " + department);
        //}

        if (price > 999999 || price < 0) {
            logError(errors, String.format("PLU price is invalid. Price is %s (item: %s)", price, item.idItem));
        }

        if(item.daysExpiry == null)
            item.daysExpiry = 0;
        if (item.daysExpiry > 999 || item.daysExpiry < 0) {
            logError(errors, String.format("PLU expired is invalid. Expired is %s (item: %s)", item.daysExpiry, item.idItem));
        }

        String command1 = "PLST  \u001bS" + zeroedInt(scales.number, 2) + separator + "WALO0" + separator + "PNUM" + pluNumber + separator + "ABNU" + department + separator + "ANKE0" + separator;
        boolean manualWeight = false;
        if (!manualWeight) {
            if (nonWeight) {
                command1 = command1 + "KLAR1\u001b";
            } else {
                command1 = command1 + "KLAR0\u001b";
            }
        } else {
            command1 = command1 + "KLAR4\u001b";
        }

        captionItem = captionItem.replace('@', 'a'); //prepareRusText(captionItem)
        command1 = command1 + "GPR1" + price + separator;
        Integer exPrice = price;
        if (exPrice > 0) {
            command1 = command1 + "EXPR" + exPrice + separator;
        }

        int BIZERBABS_Group = 1;
        String idBarcode = item.idBarcode != null && scales.weightCodeGroupScales != null && item.idBarcode.length()==5 ? ("0" + scales.weightCodeGroupScales + item.idBarcode + "00000") : item.idBarcode;
        Integer tareWeight = 0;
        Integer tarePercent = 0;
        command1 = command1 + "RABZ1\u001bPTYP4\u001bWGNU" + BIZERBABS_Group + separator + "ECO1" + idBarcode
                + separator + "HBA1" + item.daysExpiry + separator + "HBA20" + separator + "TARA" + tareWeight + separator + "TAPR" + tarePercent
                + separator + "KLGE" + priceOverflow + separator + var3 + "PLTE" + captionItem + separator;
        if (!command2.isEmpty()) {
            command1 = command1 + command2 + separator;
        }

        command1 = command1 + "BLK \u001b";
        clearReceiveBuffer(port);
        sendCommand(errors, port, command1, charset, encode);
        String result = receiveReply(errors, port, charset);
        if (!result.equals("0"))
            logError(errors, String.format("Result is %s, item: %s", result, item.idItem));
    }

    protected String formatErrorMessage(Map<String, List<String>> errors) {
        String message = "";
        for (Map.Entry<String, List<String>> entry : errors.entrySet()) {
            message += entry.getKey() + ": \n";
            for (String error : entry.getValue()) {
                message += error + "\n";
            }
        }
        return message;
    }

    protected void logError(List<String> errors, String errorText) {
        logError(errors, errorText, null);
    }

    protected void logError(List<String> errors, String errorText, Throwable t) {
        errors.add(errorText.replace("\u001b", "").replace("\u0000", "") + (t == null ? "" : ('\n' + t.toString())));
        processTransactionLogger.error(errorText, t);
    }
    
}
