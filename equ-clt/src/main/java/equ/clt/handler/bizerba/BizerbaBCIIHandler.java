package equ.clt.handler.bizerba;

import equ.api.MachineryInfo;
import equ.api.SendTransactionBatch;
import equ.api.SoftCheckInfo;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
import lsfusion.base.OrderedMap;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import javax.naming.CommunicationException;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//Таблица PLST – список все PLUшек
//Таблица ATST – список всех доп.текстов
//Таблица ETST – общие настройки этикеток.
//Таблица FOST – этикетки BLD.

public class BizerbaBCIIHandler extends BizerbaHandler {
    
    private FileSystemXmlApplicationContext springContext;

    public BizerbaBCIIHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    public String getGroupId(TransactionScalesInfo transactionInfo) {
        return "bizerbabcii";
    }

    @Override
    public Map<Integer, SendTransactionBatch> sendTransaction(List<TransactionScalesInfo> transactionList) throws IOException {

        Map<Integer, SendTransactionBatch> sendTransactionBatchMap = new HashMap<Integer, SendTransactionBatch>();

        for(TransactionScalesInfo transaction : transactionList) {

            List<MachineryInfo> succeededScalesList = new ArrayList<MachineryInfo>();
            Exception exception = null;
            try {

                List<ScalesInfo> enabledScalesList = new ArrayList<ScalesInfo>();
                for (ScalesInfo scales : transaction.machineryInfoList) {
                    if (scales.enabled)
                        enabledScalesList.add(scales);
                }

                processTransactionLogger.info("Bizerba: Send Transaction # " + transaction.id);

                if (!transaction.machineryInfoList.isEmpty()) {

                    Map<String, List<String>> errors = new HashMap<String, List<String>>();
                    Set<String> ips = new HashSet<String>();

                    List<ScalesInfo> usingScalesList = enabledScalesList.isEmpty() ? transaction.machineryInfoList : enabledScalesList;

                    processTransactionLogger.info("Bizerba: Starting sending to " + usingScalesList.size() + " scale(s)...");

                    for (ScalesInfo scales : usingScalesList) {
                        List<String> localErrors = new ArrayList<String>();

                        TCPPort port = new TCPPort(scales.port, 1025);

                        String ip = scales.port;
                        if (ip != null) {
                            ips.add(scales.port);

                            processTransactionLogger.info("Bizerba: Processing ip: " + ip);
                            try {

                                processTransactionLogger.info("Bizerba: Connecting..." + ip);
                                port.open();
                                if (!transaction.itemsList.isEmpty() && transaction.snapshot) {
                                    String clear = clearAllPLU(localErrors, port, scales);
                                    if (!clear.equals("0"))
                                        logError(localErrors, String.format("Bizerba: ClearAllPLU, Error %s", clear));
                                    List<Integer> messageList = new ArrayList<Integer>();
                                    for (int i = 1; i <= 5000; i++) {
                                        messageList.add(i);
                                    }
                                    clearMessages(localErrors, port, scales, messageList);
                                }

                                processTransactionLogger.info("Bizerba: Sending items..." + ip);
                                if (localErrors.isEmpty()) {
                                    for (ScalesItemInfo item : transaction.itemsList) {
                                        if (!Thread.currentThread().isInterrupted()) {
                                            item.description = item.description == null ? "" : item.description;
                                            item.descriptionNumber = item.descriptionNumber == null ? 1 : item.descriptionNumber;
                                            clearMessage(localErrors, port, scales, item, true);
                                            loadPLU(localErrors, port, scales, item);
                                        }
                                    }
                                }
                                port.close();

                            } catch (Exception e) {
                                logError(localErrors, "BizerbaHandler error: ", e);
                            } finally {
                                processTransactionLogger.info("Bizerba: Finally disconnecting..." + ip);
                                try {
                                    port.close();
                                } catch (CommunicationException e) {
                                    logError(localErrors, "BizerbaHandler close port error: ", e);
                                }
                            }
                            processTransactionLogger.info("Bizerba: Completed ip: " + ip);
                        }
                        if (localErrors.isEmpty())
                            succeededScalesList.add(scales);
                        else
                            errors.put(ip, localErrors);
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
                        throw new RuntimeException("Bizerba: No IP-addresses defined");

                }
            } catch (Exception e) {
                exception = e;
            }
            sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(succeededScalesList, exception));
        }
        return sendTransactionBatchMap;
    }

    @Override
    public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {
    }


    public void loadPLU(List<String> errors, TCPPort port, ScalesInfo scales, ScalesItemInfo item) throws CommunicationException, IOException {

        Integer pluNumber = getPluNumber(item);

        String var3 = "";
        String command2;
        String captionItem = item.name.trim();
        if (captionItem.isEmpty())
            logError(errors, String.format("PLU name is invalid. Name is empty (item: %s)", item.idItem));

        int department = 1;

        int var5;
        int i;
        Map<Integer, String> messageMap = getPLUMessage(item);
        loadPLUMessages(errors, port, scales, messageMap, item);
        command2 = "TFZU@00@04";
        i = 0;

        for (Iterator messageMapIterator = messageMap.keySet().iterator(); messageMapIterator.hasNext(); ++i) {
            Integer var9 = (Integer) messageMapIterator.next();
            var5 = i + 1;
            if (i < 4) {
                var3 = var3 + "ALT" + var5 + var9 + separator;
            }

            if (i < 10) {
                command2 = command2 + "@" + makeString(var9);
            }
        }

        for (var5 = i; var5 < 10; ++var5) {
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
        sendCommand(errors, port, command1);
        String result = receiveReply(errors, port);
        if (!result.equals("0"))
            logError(errors, String.format("Result is %s, item: %s", result, item.idItem));
    }

    private Map<Integer, String> getPLUMessage(ScalesItemInfo item) {
        OrderedMap<Integer, String> messageMap = new OrderedMap<Integer, String>();
        if(item.description != null) {
            int count = 0;
            String[] splittedMessage = item.description.split("\n");

            boolean isDouble = splittedMessage.length > 4;
            for (int i = 0; i < splittedMessage.length; i = i + (isDouble ? 2 : 1)) {
                String line = splittedMessage[i] + (isDouble && (i+1 < splittedMessage.length) ? (" " + splittedMessage[i+1]) : "");
                line = line.replace('@', 'a');
                if (line.length() > 2000) {
                    line = line.substring(0, 1999);
                }
                int messageNumber = getPluNumber(item) * 10 + count;
                messageMap.put(messageNumber, line);
                ++count;
            }
        }
        return messageMap;
    }

    private void loadPLUMessages(List<String> errors, TCPPort port, ScalesInfo scales, Map<Integer, String> messageMap, ScalesItemInfo item) throws CommunicationException, IOException {
        Integer messageNumber;
        String result;
        for (Map.Entry<Integer, String> entry : messageMap.entrySet()) {
            messageNumber = entry.getKey();
            String messageText = entry.getValue();//prepareRusText(messageText)
            String message = "ATST  \u001bS" + zeroedInt(scales.number, 2) + separator + "WALO0" + separator + "ATNU" + messageNumber + separator + "ATTE" + messageText + endCommand;
            clearReceiveBuffer(port);
            sendCommand(errors, port, message);
            result = receiveReply(errors, port);
            if (!result.equals("0")) {
                logError(errors, String.format("Result is %s, item: %s [msgNo=%s]", result, item.idItem, messageNumber));
                break;
            }
        }
    }

    private String prepareRusText(String value) {
        return value == null ? null : (value.replace('е', 'e').replace('Е', 'E').replace('о', 'o').replace('О', 'O').replace('р', 'p').replace('Р', 'P').replace('а', 'a').replace('А', 'A').replace('д', 'g').replace('к', 'k').replace('К', 'K').replace('х', 'x').replace('Х', 'X').replace('с', 'c').replace('С', 'C').replace('т', 'm').replace('Т', 'T').replace('у', 'y').replace('и', 'u').replace('Ь', 'b').replace('З', '3').replace('В', 'B').replace('Н', 'H').replace('М', 'M').replace('г', 'r'));
    }
    
    private void sendCommand(List<String> errors, TCPPort port, String command) throws CommunicationException, IOException {
        try {
            byte[] commandBytes = command.getBytes(charset);
            port.getOutputStream().write(commandBytes);
            port.getOutputStream().flush();
        } catch (IOException e) {
            logError(errors, "Send command exception: ", e);
        }
    }

    public String clearMessage(List<String> errors, TCPPort port, ScalesInfo scales, ScalesItemInfo item, boolean splitMessage) throws CommunicationException, IOException {
        if(splitMessage) {
            return clearMessages(errors, port, scales, new ArrayList<Integer>(getPLUMessage(item).keySet()));
        } else {

            String command = "ATST  \u001bS" + zeroedInt(scales.number, 2) + separator + "WALO1" + separator + "ATNU" + getPluNumber(item) + endCommand;
            clearReceiveBuffer(port);
            sendCommand(errors, port, command);
            return receiveReply(errors, port);
        }
    }
    
    public String clearMessages(List<String> errors, TCPPort port, ScalesInfo scales, List<Integer> messageList) throws CommunicationException, IOException {
        if(messageList != null) {
            for(int i = 0; i < messageList.size(); ++i) {
                String command = "ATST  \u001bS" + zeroedInt(scales.number, 2) + separator + "WALO1" + separator + "ATNU" + messageList.get(i) + endCommand;
                clearReceiveBuffer(port);
                sendCommand(errors, port, command);
                return receiveReply(errors, port);
            }
        }
        return null;
    }

    public String clearPLU(List<String> errors, TCPPort port, ScalesInfo scales, ScalesItemInfo item) throws CommunicationException, IOException {
        int BIZERBABS_Group = 1;
        String command = "PLST  \u001bS" + zeroedInt(scales.number, 2) + separator + "WALO1" + separator
                + "PNUM" + getPluNumber(item) + separator + "ABNU1" + separator + "ANKE0" + separator + "KLAR1" + separator
                + "GPR10" + separator + "WGNU" + BIZERBABS_Group + separator + "ECO1" + item.idBarcode + separator
                + "HBA10" + separator + "HBA20" + separator + "KLGE0" + separator + "ALT10" + separator + "PLTEXXX"
                + endCommand;
        clearReceiveBuffer(port);
        sendCommand(errors, port, command);
        return receiveReply(errors, port);
    }
    
    public String clearAllPLU(List<String> errors, TCPPort port, ScalesInfo scales) throws CommunicationException, InterruptedException, IOException {
        String command = "PLST  \u001bL" + zeroedInt(scales.number, 2) + endCommand;
        clearReceiveBuffer(port);
        sendCommand(errors, port, command);
        Thread.sleep(5000);
        return receiveReply(errors, port);
    }

    public Integer getPluNumber(ScalesItemInfo itemInfo) {
        try {
            return itemInfo.pluNumber == null ? Integer.parseInt(itemInfo.idBarcode) : itemInfo.pluNumber;
        } catch (Exception e) {
            return 0;
        }
    }
}
