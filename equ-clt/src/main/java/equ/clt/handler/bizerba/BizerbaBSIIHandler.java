package equ.clt.handler.bizerba;

import equ.api.MachineryInfo;
import equ.api.SoftCheckInfo;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import javax.naming.CommunicationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BizerbaBSIIHandler extends BizerbaHandler {

    private static String charset = "cp866";
    
    private FileSystemXmlApplicationContext springContext;

    public BizerbaBSIIHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    public String getGroupId(TransactionScalesInfo transactionInfo) {
        return "";
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

                TCPPort port = new TCPPort(scales.port, 1111);

                String ip = scales.port;
                if (ip != null) {
                    ips.add(scales.port);

                    processTransactionLogger.info("Bizerba: Processing ip: " + ip);
                    try {

                        processTransactionLogger.info("Bizerba: Connecting..." + ip);
                        port.open();
                        if (!transaction.itemsList.isEmpty() && transaction.snapshot) {
                            //String clear = clearGoodsDB(localErrors, port);
                            //if (clear != null)
                            //    logError(localErrors, String.format("Bizerba: ClearGoodsDb, Error %s", clear));
                        }

                        processTransactionLogger.info("Bizerba: Sending items..." + ip);
                        if (localErrors.isEmpty()) {
                            for (ScalesItemInfo item : transaction.itemsList) {
                                String description = item.description == null ? "" : item.description;
                                boolean splitMessage = false;
                                String resultPLU = loadMessage(localErrors, port, item, splitMessage, item.descriptionNumber, description);
                                if (resultPLU != null)
                                    logError(localErrors, String.format("Bizerba: Item # %s, Error %s", item.idBarcode, resultPLU));
                                String result = loadPLU(localErrors, port, item);
                                if (result != null)
                                    logError(localErrors, String.format("Bizerba: Item # %s, Error %s", item.idBarcode, result));
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

    public String loadPLU(List<String> errors, TCPPort port, ScalesItemInfo item) throws CommunicationException {
        String var3 = "";
        String var4 = "";

        int len = item.name.length();
        String firstLine = item.name.substring(0, len < 28 ? len : 28);
        String secondLine = len < 28 ? "" : item.name.substring(28, len < 56 ? len : 56);
        int department = 1;

        boolean splitMessage = false;
        int var5;
        int var7;
        if (splitMessage) {
            TreeMap var6 = new TreeMap();
            loadPLUMessages(errors, port, item, var6);
            var4 = "TFZU@00@04";
            var7 = 0;

            for (Iterator var8 = var6.keySet().iterator(); var8.hasNext(); ++var7) {
                Integer var9 = (Integer) var8.next();
                var5 = var7 + 1;
                if (var7 < 4) {
                    var3 = var3 + "ALT" + var5 + var9 + '\u001b';
                }

                if (var7 < 10) {
                    var4 = var4 + "@" + makeString(var9.intValue());
                }
            }

            for (var5 = var7; var5 < 10; ++var5) {
                var4 = var4 + "@00@00@00@00";
            }
        } else {
            var3 = "ALT1" + item.descriptionNumber + '\u001b';
            var4 = "TFZU@00@04@" + makeString(item.descriptionNumber);
            boolean var12 = false;
            if (!item.description.equals("")) {
                var5 = Integer.parseInt(item.idItem) + 100000;
                loadMessage(errors, port, item, splitMessage, var5, item.description);
                var3 = var3 + "ALT2" + var5 + '\u001b';
                var4 = var4 + "@" + makeString(var5);
            } else {
                var4 = var4 + "@00@00@00@00";
            }

            var4 = var4 + "@00@00@00@00";
            var4 = var4 + "@00@00@00@00";
            for (int var13 = 1; var13 <= 6; ++var13) {
                var4 = var4 + "@00@00@00@00";
            }
        }

        String nameDelimiter = "";
        String var14 = (firstLine + nameDelimiter + secondLine).trim();
        int firstLineLength = 28; //неизвестно
        var7 = firstLineLength;
        String delimiterForScaleName = "04";
        String var15;
        if (var7 > 0) {
            var14 = var14 + "                                                                                      ";
            var15 = var14.substring(0, var7 - 1) + delimiterForScaleName + var14.substring(var7 - 1);
            var15 = var15.trim();
        } else {
            var15 = var14.trim();
        }

        if (var15.equals("")) {
            var15 = " ";
        }

        boolean var16 = false;
        if (isNonWeightPrefix(item.idBarcode)) {
            var16 = true;
        }

        if (item.daysExpiry != null) {
            var15 = var15 + " Срок годн. " + item.daysExpiry + " сут.";
        }

        byte var18 = 0;
        if (item.price.intValue() > 999999) {
            item.price = BigDecimal.valueOf(Math.round((float) (item.price.intValue() / 10)));
            var18 = 1;
        }

        if (item.pluNumber == 0 || item.pluNumber > 999999 || item.pluNumber < 0) {
            logError(errors, "PLU number is invalid. Number is " + item.pluNumber);
        }

        //есть ещё exPrice
        if (item.price.intValue() > 999999 || item.price.intValue() < 0) {
            logError(errors, "PLU price is invalid. Price is " + item.price);
        }

        String var10 = "PLST  \u001bS" + zeroedInt(Integer.parseInt(item.idItem), 2) + '\u001b' + "WALO0" + '\u001b' + "PNUM" + item.pluNumber + '\u001b' + "ABNU" + department + '\u001b' + "ANKE0" + '\u001b';
        boolean manualWeight = false;
        if (!manualWeight) {
            if (var16) {
                var10 = var10 + "KLAR1\u001b";
            } else {
                var10 = var10 + "KLAR0\u001b";
            }
        } else {
            var10 = var10 + "KLAR4\u001b";
        }

        var15 = var15.replace('@', 'a');
        var10 = var10 + "GPR1" + item.price.intValue() + '\u001b';
        //exPrice?
        //if (var1.exPrice > 0) {
        //    var10 = var10 + "EXPR" + var1.exPrice + '\u001b';
        //}

        int BIZERBABS_Group = 1;
        boolean expired = false;
        int tareWeight = 0;
        int tarePercent = 0;
        var10 = var10 + "RABZ1\u001bPTYP4\u001bWGNU" + BIZERBABS_Group + '\u001b' + "ECO1" + item.idBarcode + '\u001b' + "HBA1" + expired + '\u001b' + "HBA20" + '\u001b' + "TARA" + tareWeight + '\u001b' + "TAPR" + tarePercent + '\u001b' + "KLGE" + var18 + '\u001b' + var3 + "PLTE" + var15 + '\u001b';
        if (!var4.isEmpty()) {
            var10 = var10 + var4 + '\u001b';
        }

        var10 = var10 + "BLK \u001b";
        clearReceiveBuffer(port);
        sendCommand(errors, port, var10);
        return receiveReply(errors, port);
    }

    public String clearPLU(List<String> errors, TCPPort port, ScalesItemInfo item) throws CommunicationException {
        int BIZERBABS_Group = 1;
        String var2 = "PLST  \u001bS" + zeroedInt(Integer.parseInt(item.idItem), 2) + '\u001b' + "WALO1" + '\u001b' + "PNUM" + item.pluNumber + '\u001b' + "ABNU1" + '\u001b' + "ANKE0" + '\u001b' + "KLAR1" + '\u001b' + "GPR10" + '\u001b' + "WGNU" + BIZERBABS_Group + '\u001b' + "ECO1" + item.idBarcode + '\u001b' + "HBA10" + '\u001b' + "HBA20" + '\u001b' + "KLGE0" + '\u001b' + "ALT10" + '\u001b' + "PLTEXXX" + '\u001b' + "BLK " + '\u001b';
        clearReceiveBuffer(port);
        sendCommand(errors, port, var2);
        return receiveReply(errors, port);
    }

    private void loadPLUMessages(List<String> errors, TCPPort port, ScalesItemInfo item, Map<Integer, String> var1) throws CommunicationException {
        String var3 = "";
        Iterator var4 = var1.keySet().iterator();

        String var2;
        Integer var5;
        String var6;
        do {
            if(!var4.hasNext()) {
                return;
            }

            var5 = (Integer)var4.next();
            var2 = var1.get(var5);
            var3 = "ATST  \u001bS" + zeroedInt(Integer.parseInt(item.idItem), 2) + '\u001b' + "WALO0" + '\u001b' + "ATNU" + var5 + '\u001b' + "ATTE" + var2 + '\u001b' + "BLK " + '\u001b';
            clearReceiveBuffer(port);
            sendCommand(errors, port, var3);
            var6 = receiveReply(errors, port);
        } while(var6.equals("0"));

        logError(errors, "Result is " + var6 + " [msgNo=" + var5 + ";msg=" + var5 + "]");
    }

    public String loadMessage(List<String> errors, TCPPort port, ScalesItemInfo item, boolean splitMessage, Integer idMessage, String textMessage) throws CommunicationException {
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

            loadPLUMessages(errors, port, item, var2);
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

            var10 = "ATST  \u001bS" + zeroedInt(idMessage, 2) + '\u001b' + "WALO0" + '\u001b' + "ATNU" + idMessage + '\u001b' + "ATTE" + replaceDelimiter(var11) + '\u001b' + "BLK " + '\u001b';
            clearReceiveBuffer(port);
            sendCommand(errors, port, var10);
            return receiveReply(errors, port);
        }
    }

    public String clearMessages(List<String> errors, TCPPort port, ScalesItemInfo item, List<Integer> var1) throws CommunicationException {
        if(var1 != null) {
            for(int var2 = 0; var2 < var1.size(); ++var2) {
                String var3 = "";
                var3 = "ATST  \u001bS" + zeroedInt(Integer.parseInt(item.idItem), 2) + '\u001b' + "WALO1" + '\u001b' + "ATNU" + var1.get(var2) + '\u001b' + "BLK " + '\u001b';
                clearReceiveBuffer(port);
                sendCommand(errors, port, var3);
                return receiveReply(errors, port);
            }
        }
        return null;
    }

    public String clearMessage(List<String> errors, TCPPort port, ScalesItemInfo item, int var1, boolean splitMessage) throws CommunicationException {
        if(splitMessage) {
            ArrayList var2 = new ArrayList();
            return clearMessages(errors, port, item, var2);
        } else {
            String var4 = "";
            var4 = "ATST  \u001bS" + zeroedInt(Integer.parseInt(item.idItem), 2) + '\u001b' + "WALO1" + '\u001b' + "ATNU" + var1 + '\u001b' + "BLK " + '\u001b';
            clearReceiveBuffer(port);
            sendCommand(errors, port, var4);
            return receiveReply(errors, port);
        }
    }

    public void clearAllPLU(List<String> errors, TCPPort port, ScalesItemInfo item) throws CommunicationException {
        String var1 = "PLST  \u001bL" + zeroedInt(Integer.parseInt(item.idItem), 2) + '\u001b' + "BLK " + '\u001b';
        clearReceiveBuffer(port);
        sendCommand(errors, port, var1);
        receiveReply(errors, port);
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


}
