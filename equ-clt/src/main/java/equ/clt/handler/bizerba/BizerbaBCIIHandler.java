package equ.clt.handler.bizerba;

import equ.api.MachineryInfo;
import equ.api.SoftCheckInfo;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import javax.naming.CommunicationException;
import java.io.IOException;
import java.sql.CallableStatement;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BizerbaBCIIHandler extends BizerbaHandler {
    
    private FileSystemXmlApplicationContext springContext;

    public BizerbaBCIIHandler(FileSystemXmlApplicationContext springContext) {
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

                TCPPort port = new TCPPort(scales.port, 1025);

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
                                item.description = item.description == null ? "" : item.description;
                                item.descriptionNumber = item.descriptionNumber == null ? 0 : item.descriptionNumber;
                                String resultPLU = loadMessage(localErrors, port, item);
                                if (!resultPLU.equals("0"))
                                    logError(localErrors, String.format("Bizerba: Item # %s, Error %s", item.idBarcode, resultPLU));
                                loadPLU(port, scales, item);
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


    public void loadPLU(TCPPort port, ScalesInfo scales, ScalesItemInfo item) throws CommunicationException, IOException {
        String var3 = "";
        String var4 = "";
        String captionItem = item.name.trim();
        Integer var1Number = item.pluNumber;

        int department = 1;

        int var5;
        int var7;
        boolean splitMessage = false;
        if (splitMessage) {
            TreeMap messageMap = new TreeMap();
            getPLUMessage2(messageMap);
            loadPLUMessages2(port, scales, messageMap);
            var4 = "TFZU@00@04";
            var7 = 0;

            for (Iterator var8 = messageMap.keySet().iterator(); var8.hasNext(); ++var7) {
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
            String alternativeText1 = item.description;
            if (!alternativeText1.equals("")) {
                var5 = var1Number + 100000;
                loadMessage2(port, scales, new Message(var5, alternativeText1));
                var3 = var3 + "ALT2" + var5 + '\u001b';
                var4 = var4 + "@" + makeString(var5);
            } else {
                var4 = var4 + "@00@00@00@00";
            }

            String alternativeText2 = item.description;
            if (!alternativeText2.equals("")) {
                var5 = var1Number + 200000;
                loadMessage2(port, scales, new Message(var5, alternativeText2));
                var3 = var3 + "ALT3" + var5 + '\u001b';
                var4 = var4 + "@" + makeString(var5);
            } else {
                var4 = var4 + "@00@00@00@00";
            }

            String alternativeText3 = item.description;
            if (!alternativeText3.equals("")) {
                var5 = var1Number + 300000;
                loadMessage2(port, scales, new Message(var5, alternativeText3));
                var3 = var3 + "ALT4" + var5 + '\u001b';
                var4 = var4 + "@" + makeString(var5);
            } else {
                var4 = var4 + "@00@00@00@00";
            }

            for (int var13 = 1; var13 <= 6; ++var13) {
                var4 = var4 + "@00@00@00@00";
            }
        }

        boolean BIZERBABS_AddInfoToTFZU = false;
        if (!BIZERBABS_AddInfoToTFZU) {
            var4 = "";
        }

        boolean BIZERBABCII_AddALTTexts = false;
        if (!BIZERBABCII_AddALTTexts) {
            var3 = "ALT10\u001bALT20\u001bALT30\u001bALT40\u001b";
        }

        if (captionItem.equals("")) {
            captionItem = " ";
        }

        boolean var16 = false;
        //if(Configuration.isNonWeightPrefix(var1.barCodePrefix)) {
        //    var16 = true;
        //}

            /*if(Configuration.BIZERBABS_AddExpiredToName && var1.expired > 0) {
                if(var1.expiredType == null) {
                    lastTryErrorCode = "ExpError";
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

        byte var18 = 0;
        int price = item.price.intValue();
            if(price > 999999) {
                price = Math.round((float)(price / 10));
                var18 = 1;
            }

            if(var1Number == 0 || var1Number > 999999 || var1Number < 0) {
                throw new RuntimeException("PLU number is invalid. Number is " + var1Number);
            }

            /*if(var1.department == 0 || var1.department > 999 || var1.department < 0) {
                lastTryErrorCode = "PLUDepError " + var1.department;
                throw new ScalesException("PLU department is invalid. Department is " + var1.department);
            }*/

            /*if(var1.price > 999999 || var1.price < 0) {
                lastTryErrorCode = "PLUPriceError " + var1.price;
                throw new ScalesException("PLU price is invalid. Price is " + var1.price);
            }*/

            /*if(var1.barCodePrefix == 0 || var1.barCodePrefix > 99 || var1.barCodePrefix < 0) {
                lastTryErrorCode = "PLUPrefixError " + var1.barCodePrefix;
                throw new ScalesException("PLU barcode prefix is invalid. Barcode prefix is " + var1.barCodePrefix);
            }*/

            /*if(var1.expired > 999 || var1.expired < 0) {
                lastTryErrorCode = "PLUExpError " + var1.expired;
                throw new ScalesException("PLU expired is invalid. Expired is " + var1.expired);
            }*/

            /*if(var15.equals("")) {
                lastTryErrorCode = "PLUNameError " + var15;
                throw new ScalesException("PLU name is invalid. Name is empty");
            }*/

            /*if(Configuration.BIZERBABS_SetCodeToZero) {
                var1.barCodeWithoutPrefix = 0;
            }*/

        String var10 = "PLST  \u001bS" + zeroedInt(scales.number, 2) + '\u001b' + "WALO0" + '\u001b' + "PNUM" + item.pluNumber + '\u001b' + "ABNU" + department + '\u001b' + "ANKE0" + '\u001b';
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

        captionItem = captionItem.replace('@', 'a');
        var10 = var10 + "GPR1" + price + '\u001b';
        Integer exPrice = price;
        if (exPrice > 0) {
            var10 = var10 + "EXPR" + exPrice + '\u001b';
        }

        int BIZERBABS_Group = 1;
        Integer barCodePrefix = Integer.parseInt(item.idBarcode.substring(0, 2));
        Integer barCodeWithoutPrefix = Integer.parseInt(item.idBarcode.substring(2));
        Integer var1Expired = 1;
        Integer var1TareWeight = 1;
        Integer var1TarePercent = 1;
        var10 = var10 + "RABZ1\u001bPTYP4\u001bWGNU" + BIZERBABS_Group + '\u001b' + "ECO1" + makeBarCode(barCodePrefix, barCodeWithoutPrefix) + '\u001b' + "HBA1" + var1Expired + '\u001b' + "HBA20" + '\u001b' + "TARA" + var1TareWeight + '\u001b' + "TAPR" + var1TarePercent + '\u001b' + "KLGE" + var18 + '\u001b' + var3 + "PLTE" + prepareRusText(captionItem) + '\u001b';
        if (!var4.isEmpty()) {
            var10 = var10 + var4 + '\u001b';
        }

        var10 = var10 + "BLK \u001b";
        clearReceiveBuffer(port);
        sendCommand2(port, var10);
        String var11 = receiveReply2(port);
        if (!var11.equals("0")) {
            throw new RuntimeException("Result is " + var11);
        }
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

    private void getPLUMessage2(Map<Integer, String> messageMap) {
        CallableStatement var3 = null;
        Integer var4 = 1;//Integer.valueOf(var3.getInt(3));
        String var5 = "message";//var3.getString(4);
        if(var5 != null) {
            int var6 = 0;
            //boolean var7 = false;
            var5 = var5.replace("\u0007\n", "\u0007");
            String[] var8 = var5.split("\u0007");
            int var9 = var8.length;

            for(int var10 = 0; var10 < var9; ++var10) {
                String var11 = var8[var10];
                var11 = var11.replace('@', 'a');
                if(var11.length() > 2000) {
                    var11 = var11.substring(0, 1999);
                }

                int var13 = var4.intValue() * 100 + var6;
                messageMap.put(Integer.valueOf(var13), var11);
                ++var6;
            }
        }
    }

    private void loadPLUMessages2(TCPPort port, ScalesInfo scales, Map<Integer, String> messageMap) throws CommunicationException, IOException {
        String var3 = "";
        Iterator var4 = messageMap.keySet().iterator();

        String var2;
        Integer var5;
        String var6;
        do {
            if(!var4.hasNext()) {
                return;
            }

            var5 = (Integer)var4.next();
            var2 = messageMap.get(var5);
            var3 = "ATST  \u001bS" + zeroedInt(scales.number, 2) + '\u001b' + "WALO0" + '\u001b' + "ATNU" + var5 + '\u001b' + "ATTE" + prepareRusText(var2) + '\u001b' + "BLK " + '\u001b';
            clearReceiveBuffer(port);
            sendCommand2(port, var3);
            var6 = receiveReply2(port);
        } while(var6.equals("0"));

        System.out.println("Error");
        throw new RuntimeException("Result is " + var6 + " [msgNo=" + var5 + ";msg=" + var5 + "]");
    }

    private String prepareRusText(String var1) {
        boolean convertRUS = true;
        return var1 == null ? null : (!convertRUS ? var1 : var1.replace('е', 'e').replace('Е', 'E').replace('о', 'o').replace('О', 'O').replace('р', 'p').replace('Р', 'P').replace('а', 'a').replace('А', 'A').replace('д', 'g').replace('к', 'k').replace('К', 'K').replace('х', 'x').replace('Х', 'X').replace('с', 'c').replace('С', 'C').replace('т', 'm').replace('Т', 'T').replace('у', 'y').replace('и', 'u').replace('Ь', 'b').replace('З', '3').replace('В', 'B').replace('Н', 'H').replace('М', 'M').replace('г', 'r'));
    }

    protected String receiveReply2(TCPPort port) throws CommunicationException {
        String var2 = "";
        Pattern var3 = Pattern.compile("QUIT(\\d+)");
        byte[] var4 = new byte[500];

        try {
            long var5 = (new Date()).getTime();

            long var7;
            do {
                if(port.getBisStream().available() != 0) {
                    port.getBisStream().read(var4);
                    var2 = new String(var4, "utf-8");

                    Matcher var10 = var3.matcher(var2);
                    if(var10.find()) {
                        var2 = var10.group(1);
                    }

                    return var2;
                }

                Thread.sleep(10L);
                var7 = (new Date()).getTime();
            } while(var7 - var5 <= 10000L);

            throw new RuntimeException("Scales reply timeout");
        } catch (Exception var9) {
            throw new CommunicationException(var9.toString());
        }
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

    public String loadMessage(List<String> errors, TCPPort port, ScalesItemInfo item) throws CommunicationException {
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

    private void sendCommand2(TCPPort port, String var1) throws CommunicationException, IOException {
            byte[] var2 = var1.getBytes("utf-8");
            
            port.getOutputStream().write(var2);
            port.getOutputStream().flush();
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

    public void loadMessage2(TCPPort port, ScalesInfo scales, Message var1) throws CommunicationException, IOException {
        boolean splitMessage = false;
        if(splitMessage) {
            TreeMap var2 = new TreeMap();
            int var3 = 0;
            boolean var4 = false;
            String var5 = var1.text.replace("\u0007\n", "\u0007");
            String[] var6 = var5.split("\u0007");
            int var7 = var6.length;

            for(int var8 = 0; var8 < var7; ++var8) {
                String var9 = var6[var8];
                var9 = var9.replace('@', 'a');
                if(var9.length() > 2000) {
                    var9 = var9.substring(0, 1999);
                }

                int var12 = var1.id * 100 + var3;
                var2.put(Integer.valueOf(var12), var9);
                ++var3;
            }

            loadPLUMessages2(port, scales, var2);
        } else {
            if(var1.text == null) {
                var1.text = "";
            }

            String var10 = "";
            String var11 = var1.text.replace('@', 'a');
            if(var11.length() > 2000) {
                var11 = var11.substring(0, 1999);
            }

            var10 = "ATST  \u001bS" + zeroedInt(scales.number, 2) + '\u001b' + "WALO0" + '\u001b' + "ATNU" + var1.id + '\u001b' + "ATTE" + prepareRusText(replaceDelimiter(var11)) + '\u001b' + "BLK " + '\u001b';
            clearReceiveBuffer(port);
            sendCommand2(port, var10);
            String var13 = receiveReply2(port);
            if(!var13.equals("0")) {
                throw new RuntimeException("Result is " + var13 + " [JobId=" + var1.jobId + ";JobKey=" + var1.jobKey + "]");
            }
        }

    }


}
