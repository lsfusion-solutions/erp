package equ.clt.handler.bizerba;

import equ.api.MachineryInfo;
import equ.api.SoftCheckInfo;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
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
    public List<MachineryInfo> sendTransaction(TransactionScalesInfo transaction, List<ScalesInfo> scalesList) throws IOException {
        
        List<ScalesInfo> enabledScalesList = new ArrayList<ScalesInfo>();
        for (ScalesInfo scales : scalesList) {
            if (scales.enabled)
                enabledScalesList.add(scales);
        }

        processTransactionLogger.info("Bizerba: Send Transaction # " + transaction.id);
        List<MachineryInfo> succeededScalesList = new ArrayList<MachineryInfo>();

        if (!scalesList.isEmpty()) {

            Map<String, List<String>> errors = new HashMap<String, List<String>>();
            Set<String> ips = new HashSet<String>();

            processTransactionLogger.info("Bizerba: Starting sending to " + enabledScalesList.size() + " scales...");

            for (ScalesInfo scales : enabledScalesList.isEmpty() ? scalesList : enabledScalesList) {
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
                            for(int z = 0; z < 1000; z++) {
                                clearMessage(localErrors, port, scales, z, false);
                            }
                        }

                        processTransactionLogger.info("Bizerba: Sending items..." + ip);
                        if (localErrors.isEmpty()) {
                            for (ScalesItemInfo item : transaction.itemsList) {
                                if(!Thread.currentThread().isInterrupted()) {
                                    item.description = item.description == null ? "" : item.description;
                                    item.descriptionNumber = item.descriptionNumber == null ? 1 : item.descriptionNumber;
                                    String resultPLU = loadMessage2(localErrors, port, scales, new Message(item.descriptionNumber, item.description));
                                    if (!resultPLU.equals("0"))
                                        logError(localErrors, String.format("Bizerba: Item # %s, Error %s", item.idBarcode, resultPLU));
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
                for(Map.Entry<String, List<String>> entry : errors.entrySet()) {
                    message += entry.getKey() + ": \n";
                    for(String error : entry.getValue()) {
                        message += error + "\n";
                    }
                }
                throw new RuntimeException(message);
            } else if (ips.isEmpty())
                throw new RuntimeException("Bizerba: No IP-addresses defined");
            
        }
            
        return succeededScalesList;
    }

    @Override
    public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {

    }


    public void loadPLU(List<String> errors, TCPPort port, ScalesInfo scales, ScalesItemInfo item) throws CommunicationException, IOException {
        String var3 = "";
        String command2;
        String captionItem = item.name.trim();
        if (captionItem.isEmpty())
            logError(errors, "PLU name is invalid. Name is empty");
        Integer var1Number = item.pluNumber;

        int department = 1;

        int var5;
        int var7;
        TreeMap messageMap = new TreeMap();
        getPLUMessage(item, messageMap);
        loadPLUMessages(errors, port, scales, messageMap);
        command2 = "TFZU@00@04";
        var7 = 0;

        for (Iterator messageMapIterator = messageMap.keySet().iterator(); messageMapIterator.hasNext(); ++var7) {
            Integer var9 = (Integer) messageMapIterator.next();
            var5 = var7 + 1;
            if (var7 < 4) {
                var3 = var3 + "ALT" + var5 + var9 + '\u001b';
            }

            if (var7 < 10) {
                command2 = command2 + "@" + makeString(var9.intValue());
            }
        }

        for (var5 = var7; var5 < 10; ++var5) {
            command2 = command2 + "@00@00@00@00";
        }

        boolean BIZERBABS_AddInfoToTFZU = false;
        if (!BIZERBABS_AddInfoToTFZU) {
            command2 = "";
        }

        boolean BIZERBABCII_AddALTTexts = false;
        if (!BIZERBABCII_AddALTTexts) {
            var3 = "ALT10\u001bALT20\u001bALT30\u001bALT40\u001b";
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

        if (var1Number == 0 || var1Number > 999999 || var1Number < 0) {
            throw new RuntimeException("PLU number is invalid. Number is " + var1Number);
        }

        //if(department == 0 || department > 999 || department < 0) {
        //    logError(errors, "PLU department is invalid. Department is " + department);
        //}

        if (price > 999999 || price < 0) {
            logError(errors, "PLU price is invalid. Price is " + price);
        }

        if(item.daysExpiry == null)
            item.daysExpiry = 0;
        if (item.daysExpiry > 999 || item.daysExpiry < 0) {
            logError(errors, "PLU expired is invalid. Expired is " + item.daysExpiry);
        }

        String command1 = "PLST  \u001bS" + zeroedInt(scales.number, 2) + '\u001b' + "WALO0" + '\u001b' + "PNUM" + item.pluNumber + '\u001b' + "ABNU" + department + '\u001b' + "ANKE0" + '\u001b';
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

        captionItem = captionItem.replace('@', 'a');
        command1 = command1 + "GPR1" + price + '\u001b';
        Integer exPrice = price;
        if (exPrice > 0) {
            command1 = command1 + "EXPR" + exPrice + '\u001b';
        }

        int BIZERBABS_Group = 1;
        Integer barCodePrefix = Integer.parseInt(item.idBarcode.substring(0, 2));
        Integer barCodeWithoutPrefix = Integer.parseInt(item.idBarcode.substring(2));
        Integer tareWeight = 0;
        Integer tarePercent = 0;
        command1 = command1 + "RABZ1\u001bPTYP4\u001bWGNU" + BIZERBABS_Group + '\u001b' + "ECO1" + makeBarCode(barCodePrefix, barCodeWithoutPrefix) 
                + '\u001b' + "HBA1" + item.daysExpiry + '\u001b' + "HBA20" + '\u001b' + "TARA" + tareWeight + '\u001b' + "TAPR" + tarePercent 
                + '\u001b' + "KLGE" + priceOverflow + '\u001b' + var3 + "PLTE" + prepareRusText(captionItem) + '\u001b';
        if (!command2.isEmpty()) {
            command1 = command1 + command2 + '\u001b';
        }

        command1 = command1 + "BLK \u001b";
        clearReceiveBuffer(port);
        sendCommand2(port, command1);
        String result = receiveReply2(port);
        if (!result.equals("0"))
            logError(errors, "Result is " + result);
    }

    public static byte[] barcodeRational = new byte[]{(byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6, (byte)6};
    
    private String makeBarCode(int barcodePrefix, int barcodeWithoutPrefix) {
        String var4 = barcodePrefix == 0?"1":"0";

        String var3;
        for(var3 = (new Integer(barcodePrefix)).toString(); var3.length() < 2; var3 = '0' + var3) {
            ;
        }

        var4 = var4 + var3;

        for(var3 = (new Integer(barcodeWithoutPrefix)).toString(); var3.length() < barcodeRational[barcodePrefix]; var3 = '0' + var3) {
            ;
        }

        for(var4 = var4 + var3; var4.length() < 13; var4 = var4 + '0') {
            ;
        }

        var4 = barcodePrefix == 0?var4.substring(0, 12):var4;
        return var4;
    }

    private void getPLUMessage(ScalesItemInfo item, Map<Integer, String> messageMap) {
        Integer pluNumber = item.pluNumber;//item.descriptionNumber;//Integer.valueOf(var3.getInt(3));
        String pluMessage = item.description;
        if(pluMessage != null) {
            int count = 0;
            //pluMessage = pluMessage.replace("\u0007\n", "\u0007");
            String[] splittedMessage = pluMessage.split("\n");
            
            for (String line : splittedMessage) {
                line = line.replace('@', 'a');
                if (line.length() > 2000) {
                    line = line.substring(0, 1999);
                }

                int messageNumber = pluNumber * 100 + count;
                messageMap.put(messageNumber, line);
                ++count;
            }
        }
    }

    private void loadPLUMessages(List<String> errors, TCPPort port, ScalesInfo scales, Map<Integer, String> messageMap) throws CommunicationException {
        Iterator iterator = messageMap.keySet().iterator();
        
        Integer messageNumber;
        String result;
        do {
            if(!iterator.hasNext()) {
                return;
            }
            messageNumber = (Integer)iterator.next();
            String messageText = messageMap.get(messageNumber); //prepareRusText(messageText)
            String message = "ATST  \u001bS" + zeroedInt(scales.number, 2) + '\u001b' + "WALO0" + '\u001b' + "ATNU" + messageNumber + '\u001b' + "ATTE" + messageText + getEndCommand();
            clearReceiveBuffer(port);
            sendCommand(errors, port, message);
            result = receiveReply(errors, port);
        } while(result.equals("0"));
        logError(errors, "Result is " + result + " [msgNo=" + messageNumber + "]");
    }

    private String prepareRusText(String var1) {
        boolean convertRUS = true;
        return var1 == null ? null : (!convertRUS ? var1 : var1.replace('е', 'e').replace('Е', 'E').replace('о', 'o').replace('О', 'O').replace('р', 'p').replace('Р', 'P').replace('а', 'a').replace('А', 'A').replace('д', 'g').replace('к', 'k').replace('К', 'K').replace('х', 'x').replace('Х', 'X').replace('с', 'c').replace('С', 'C').replace('т', 'm').replace('Т', 'T').replace('у', 'y').replace('и', 'u').replace('Ь', 'b').replace('З', '3').replace('В', 'B').replace('Н', 'H').replace('М', 'M').replace('г', 'r'));
    }

    protected String receiveReply2(TCPPort port) throws CommunicationException {
        String reply = "";
        Pattern var3 = Pattern.compile("QUIT(\\d+)");
        byte[] var4 = new byte[500];

        try {
            long var5 = (new Date()).getTime();

            long var7;
            do {
                if(port.getBisStream().available() != 0) {
                    port.getBisStream().read(var4);
                    reply = new String(var4, "utf-8");

                    Matcher var10 = var3.matcher(reply);
                    if(var10.find()) {
                        reply = var10.group(1);
                    }

                    return reply;
                }

                Thread.sleep(10L);
                var7 = (new Date()).getTime();
            } while(var7 - var5 <= 10000L);

            throw new RuntimeException("Scales reply timeout");
        } catch (Exception e) {
            throw new CommunicationException(e.toString());
        }
    }
    
    

    public String clearPLU(List<String> errors, TCPPort port, ScalesInfo scales, ScalesItemInfo item) throws CommunicationException {
        int BIZERBABS_Group = 1;
        String var2 = "PLST  \u001bS" + zeroedInt(scales.number, 2) + '\u001b' + "WALO1" + '\u001b' 
                + "PNUM" + item.pluNumber + '\u001b' + "ABNU1" + '\u001b' + "ANKE0" + '\u001b' + "KLAR1" + '\u001b'
                + "GPR10" + '\u001b' + "WGNU" + BIZERBABS_Group + '\u001b' + "ECO1" + item.idBarcode + '\u001b'
                + "HBA10" + '\u001b' + "HBA20" + '\u001b' + "KLGE0" + '\u001b' + "ALT10" + '\u001b' + "PLTEXXX"
                + getEndCommand();
        clearReceiveBuffer(port);
        sendCommand(errors, port, var2);
        return receiveReply(errors, port);
    }

    public String loadMessage(List<String> errors, TCPPort port, ScalesInfo scales, ScalesItemInfo item) throws CommunicationException, IOException {
        boolean splitMessage = false;
        Integer idMessage = item.descriptionNumber;
        String textMessage = item.description;
        if(splitMessage) {
            TreeMap var2 = new TreeMap();
            int var3 = 0;
            boolean var4 = false;
            String var5 = textMessage.replace("\u0007\n", "\u0007");
            String[] var6 = var5.split("\u0007");
            int var7 = var6.length;

            for(int var8 = 0; var8 < var7; ++var8) {
                String var9 = var6[var8];
                var9 = var9.replace('@', 'a');
                if(var9.length() > 2000) {
                    var9 = var9.substring(0, 1999);
                }

                int var12 = idMessage * 100 + var3;
                var2.put(Integer.valueOf(var12), var9);
                ++var3;
            }
            
            loadPLUMessages(errors, port, scales, var2);
            return null;
        } else {
            if(textMessage == null) {
                textMessage = "";
            }

            String var10 = "";
            String var11 = textMessage.replace('@', 'a');
            if(var11.length() > 2000) {
                var11 = var11.substring(0, 1999);
            }

            var10 = "ATST  \u001bS" + zeroedInt(idMessage, 2) + '\u001b' + "WALO0" + '\u001b' + "ATNU" + idMessage + '\u001b' + "ATTE" + replaceDelimiter(var11) + getEndCommand();
            clearReceiveBuffer(port);
            sendCommand(errors, port, var10);
            return receiveReply(errors, port);
        }
    }

    public String loadMessage2(List<String> errors, TCPPort port, ScalesInfo scales, Message message) throws CommunicationException, IOException {
        boolean splitMessage = true;
        if(splitMessage) {
            TreeMap var2 = new TreeMap();
            int var3 = 0;
            boolean var4 = false;
            String var5 = message.text.replace("\u0007\n", "\u0007");
            String[] var6 = var5.split("\u0007");
            int var7 = var6.length;

            for(int var8 = 0; var8 < var7; ++var8) {
                String var9 = var6[var8];
                var9 = var9.replace('@', 'a');
                if(var9.length() > 2000) {
                    var9 = var9.substring(0, 1999);
                }

                int var12 = message.id * 100 + var3;
                var2.put(Integer.valueOf(var12), var9);
                ++var3;
            }

            loadPLUMessages(errors, port, scales, var2);
            return "0";
        } else {
            if(message.text == null) {
                message.text = "";
            }

            String var10 = "";
            String var11 = message.text.replace('@', 'a');
            if(var11.length() > 2000) {
                var11 = var11.substring(0, 1999);
            }

            var10 = "ATST  \u001bS" + zeroedInt(scales.number, 2) + '\u001b' + "WALO0" + '\u001b' + "ATNU" + message.id + '\u001b' + "ATTE" + prepareRusText(replaceDelimiter(var11)) + getEndCommand();
            clearReceiveBuffer(port);
            sendCommand2(port, var10);
            String reply = receiveReply2(port);
            if(!reply.equals("0")) {
                throw new RuntimeException("Result is " + reply + " [JobId=" + message.jobId + ";JobKey=" + message.jobKey + "]");
            }
            return reply;
        }
    }

    protected void sendCommand(List<String> errors, TCPPort port, String command) throws CommunicationException {
        try {
            byte[] var2 = command.getBytes("cp866");
            encode(var2);
            port.getOutputStream().write(var2);
            port.getOutputStream().flush();
        } catch (IOException e) {
            logError(errors, "Send command exception: ", e);
        }
    }
    
    private void sendCommand2(TCPPort port, String command) throws CommunicationException, IOException {
            byte[] commandBytes = command.getBytes("utf-8");
            port.getOutputStream().write(commandBytes);
            port.getOutputStream().flush();
    }

    public String clearMessages(List<String> errors, TCPPort port, ScalesInfo scales, List<Integer> var1) throws CommunicationException {
        if(var1 != null) {
            for(int var2 = 0; var2 < var1.size(); ++var2) {
                String var3 = "";
                var3 = "ATST  \u001bS" + zeroedInt(scales.number, 2) + '\u001b' + "WALO1" + '\u001b' + "ATNU" + var1.get(var2) + getEndCommand();
                clearReceiveBuffer(port);
                sendCommand(errors, port, var3);
                return receiveReply(errors, port);
            }
        }
        return null;
    }

    public String clearMessage(List<String> errors, TCPPort port, ScalesInfo scales, int var1, boolean splitMessage) throws CommunicationException {
        if(splitMessage) {
            ArrayList var2 = new ArrayList();
            return clearMessages(errors, port, scales, var2);
        } else {
            String var4 = "";
            var4 = "ATST  \u001bS" + zeroedInt(scales.number, 2) + '\u001b' + "WALO1" + '\u001b' + "ATNU" + var1 + getEndCommand();
            clearReceiveBuffer(port);
            sendCommand(errors, port, var4);
            return receiveReply(errors, port);
        }
    }

    public String clearAllPLU(List<String> errors, TCPPort port, ScalesInfo scales) throws CommunicationException, InterruptedException {
        String var1 = "PLST  \u001bL" + zeroedInt(scales.number, 2) + getEndCommand();
        clearReceiveBuffer(port);
        sendCommand(errors, port, var1);
        Thread.sleep(5000);
        return receiveReply(errors, port);
    }
    
    protected String receiveReply(List<String> errors, TCPPort port) throws CommunicationException {
        String reply = "";
        Pattern var3 = Pattern.compile("QUIT(\\d+)");
        byte[] var4 = new byte[500];

        try {
            long var5 = (new Date()).getTime();

            long var7;
            do {
                if(port.getBisStream().available() != 0) {
                    port.getBisStream().read(var4);
                    reply = new String(var4, "cp866");

                    Matcher var10 = var3.matcher(reply);
                    if(var10.find()) {
                        reply = var10.group(1);
                    }
                    return reply;
                }

                Thread.sleep(10L);
                var7 = (new Date()).getTime();
            } while(var7 - var5 <= 10000L);

            throw new RuntimeException("Scales reply timeout");
        } catch (Exception var9) {
            logError(errors, "Reply: " + reply, var9);
            return reply;
        }
    }
    
    private String getEndCommand() {
        return '\u001b' + "BLK " + '\u001b';
    }


}
