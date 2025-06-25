package equ.clt.handler.dataprint;

import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItem;
import equ.api.scales.TransactionScalesInfo;
import equ.clt.handler.MultithreadScalesHandler;
import equ.clt.handler.TCPPort;
import lsfusion.base.BaseUtils;
import lsfusion.base.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import javax.naming.CommunicationException;
import java.io.IOException;
import java.util.*;

import static equ.clt.ProcessMonitorEquipmentServer.notInterruptedTransaction;
import static lsfusion.base.BaseUtils.nvl;

public class DataPrintWaspHandler extends MultithreadScalesHandler {
    
    private static class Result {
        protected boolean error;
        protected int errorCode;
        protected String errorMessage;
        
        Result(boolean error, int errorCode, String errorMessage) {
            this.error = error;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }
        
        public boolean success() {
            return !error;
        }
        
        @Override
        public String toString() {
            return "success: " + !error + ", errorCode: " + errorCode + ", errorMessage: " + (StringUtils.isEmpty(errorMessage) ? "null" : errorMessage);
        }
        
        static class Success extends DataPrintWaspHandler.Result {
            public Success() {
                super(false, 0, null);
            }
        }
        
        static class Error extends DataPrintWaspHandler.Result {
            public Error(int errorCode, String errorMessage) {
                super(true, errorCode, errorMessage);
            }
            public String toString() {
                return "resultCode: " + this.errorCode + ", errorMessage: " + (StringUtils.isEmpty(errorMessage) ? "null" : errorMessage);
            }
        }
    }
    
    private class Data {
        int timeout = 5000;
        LinkedList<String> segments = new LinkedList<>();
        private void clear() {
            timeout = 5000;
            segments.clear();
        }
    }
    
    protected final static Logger logger = Logger.getLogger("DPWaspLogger");
    
    private static final String ENDL = "\r\n";
    private static final String TAB = "\t";
    
    private TCPPort port;
    private DataPrintWaspSettings settings;
    protected FileSystemXmlApplicationContext springContext;
    
    //включить для вывода в лог отправляемых запросов
    private boolean debugMode = false;
    
    public DataPrintWaspHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }
    
    @Override
    protected String getLogPrefix() {
        return "DP Wasp: ";
    }
    
    private Result sendData(Data data) {
        
        String line = "";
        if (data.segments != null) {

            if (debugMode)
                logger.info(getLogPrefix() + String.format("ip: %s segments: %s", port.getAddress(), data.segments));
            
            line = StringUtils.join(data.segments, TAB);
        }
        if (!StringUtils.isBlank(line))
            line += TAB;
        line += ENDL;
        
        try {
            
            if (debugMode) {
                String printableLine = line.replace("\t", "<09>").replace("\n", "<0A>").replace("\r", "<0D>");
                logger.info(getLogPrefix() + String.format("ip: %s >> %s", port.getAddress(), printableLine));
            }
            
            port.getOutputStream().write(line.getBytes());
            port.getOutputStream().flush();
            
        } catch (IOException e) {
            logger.error(getLogPrefix() + String.format("ip %s, error: %s", port.getAddress(), e.getMessage()));
            return new Result.Error(-1, e.getMessage());
        }
        
        return new Result.Success();
    }
    
    private Result readData(Data data) {
        
        if (data.segments != null)
            data.segments.clear();
    
        try {
            port.setSoTimeout(data.timeout);
        } catch (Exception e) {
            return new Result.Error(-1, e.getMessage());
        }
        
        byte[] b = new byte[1];
        List<Byte> buffer = new ArrayList<>();
        
        long started = System.currentTimeMillis();
        
        while (true) {
            
            try {
                int bytes = port.getInputStream().read(b, 0, 1);
                
                long elapsed = System.currentTimeMillis() - started;
                
                if (elapsed > data.timeout) {
                    return new Result.Error(-1, "Socket read timeout");
                }
                
                if (bytes > 0) {
                    
                    started = System.currentTimeMillis();
                    
                    buffer.add(b[0]);
                    
                    if (buffer.size() > 1) {
                        
                        if (buffer.get(buffer.size() - 1) == (byte)'\n' && buffer.get(buffer.size()-2) == (byte)'\r') {
                            if (buffer.size() == 2) {
                                buffer.clear();
                                continue;
                            }
                            
                            String line = "";
                            
                            for (byte ch: buffer) {
                                line += (char)ch;
                            }
                            
                            data.segments = new LinkedList<>(Arrays.asList(line.split(TAB)));
    
                            if (debugMode)
                                logger.info(getLogPrefix() + String.format("ip: %s, << %s", port.getAddress(), data.segments));
                            
                            return new Result.Success();
                        }
                    }
                }
            } catch (IOException e) {
                logger.error(getLogPrefix() + String.format("ip %s, error: %s", port.getAddress(), e.getMessage()));
                return new Result.Error(-1, e.getMessage());
            }
        }
    }
    
    private Result sendPLU(ScalesInfo scales, ScalesItem item) {
    
        Data data = new Data();
        //data.segments.add("DWL");
        //data.segments.add("PLU");
    
        //if ((result = sendData(data)).success()) {
    
            //data.clear();
    
            String pluNumber = getPluNumber(item);
            
            data.segments.add("PLU");   // 1 Fixed Text "PLU"
            data.segments.add(pluNumber);     // 2 Number (ID) Number type
            data.segments.add(pluNumber);     // 3 Item Code 0 Number type (articul)
            data.segments.add("");      //4 Index Barcode String type
            data.segments.add(item.splitItem ? "3" : "2");     //5 Unit number of PLU. 3 Number : 1=weight,2=pcs, 3=kg, 4=g, 5=ton,6=lb,7=500g,8=100g, 9=1/4lb
            data.segments.add(floatValue(item.price != null ? item.price.doubleValue() : 0)); //6 Price 0,0 SPECIAL FLOAT
            data.segments.add("0,0");   // 7 Cost 0,0 SPECIAL FLOAT
            data.segments.add("0,0");   // 8 Tare 0,0 SPECIAL FLOAT
            
            int labelBill1 = BaseUtils.nvl(settings.getLabelBill1(), 11);
            int barcodeBill1 = BaseUtils.nvl(settings.getBarcodeBill1(), 10);
            int labelBill2 = BaseUtils.nvl(settings.getLabelBill2(), 0);
            int barcodeBill2 = BaseUtils.nvl(settings.getBarcodeBill2(), 0);
            
            data.segments.add(String.valueOf(labelBill1));   // 9 Label in bill 1 0 Number type
            data.segments.add(String.valueOf(barcodeBill1)); //10 Barcode in bill 1 0 Number type
            data.segments.add("22"); //11 PLU Flag in Barcode in bill 1 0 Number type
            data.segments.add(String.valueOf(labelBill2)); //12 Label in bill 2 0 Number type
            data.segments.add(String.valueOf(barcodeBill2)); //13 Barcode in bill 2 0 Number type
            data.segments.add("0"); //14 PLU Flag in Barcode in bill 2 0 Number type
            data.segments.add("9"); //15 Class 9 Number type
            data.segments.add(stringValue(item.name)); //16 PLU name String type
    
            int descriptionLineLength = nvl(settings.getDescriptionLineLength(), 510);
            String line = stringValue(item.description);
            for (int i = 0; i < 7; ++i) {
                String subLine = "";
                if (!StringUtils.isBlank(line)) {
                    subLine = StringUtils.left(line, descriptionLineLength);
                    line = line.substring(subLine.length());
                }
                data.segments.add(subLine);
            }
            
/*
            long saleDays = 0;
            if (item.daysExpiry != null) {
                Period period = Period.between(item.expiryDate, LocalDate.now());
                saleDays = period.getDays();
            }
*/
            
            data.segments.add(item.daysExpiry != null ? "1" : "0"); //24 Print Sale Data state 0CNumber :0 for not print, 1forprint
            data.segments.add("0"); //25 Print Sale Time state 0
            data.segments.add("1"); //26 Print Pack Data state 0
            data.segments.add("1"); //27 Print Pack Time state 0
            data.segments.add(item.hoursExpiry != null ? "2" : "0"); //28 Print Shelf Data state 0
            
            data.segments.add(item.daysExpiry != null ? String.valueOf(item.daysExpiry) : "0"); //29 Print Sale Data data 0 Number type
            data.segments.add("0"); //30 Print Sale Time data 0 Number type
            data.segments.add("0"); //31 Print Pack Data data 0 Number type
            data.segments.add("0"); //32 Print Pack Time data 0 Number type
            data.segments.add(item.hoursExpiry != null ? String.valueOf(item.hoursExpiry): "0"); //33 Print Shelf Data data 0 Number:number of shelf day
            
            data.segments.add("0"); //34 Lowwer of Discount Manual sort 0 Number type
            data.segments.add("0"); //35 Upper of Discount Manual sort 0 Number type
            data.segments.add("0,0"); //36 Lowwer of Discount Manual data 0,0 SPECIAL FLOAT
            data.segments.add("0,0"); //37 Upper of Discount Manual data 0,0 SPECIAL FLOAT
            data.segments.add("0"); //38 Auto Discount 1: Sort 0 Number type
            data.segments.add("127"); //39 Auto Discount 1: Weekdays 127 Number type
            data.segments.add("0,0"); //40 Auto Discount 1: Range Lowwer 0,0 Number type
            data.segments.add("0,0"); //41 Auto Discount 1: Range Upper 0,0 Number type
            data.segments.add("0,0"); //42 Auto Discount 1: Target Value 0,0 SPECIAL FLOAT
            data.segments.add("0"); // 43 Auto Discount 2: Sort 0 Number type
            data.segments.add("127"); //44 Auto Discount 2: Weekdays 127 Number type
            data.segments.add("0,0"); //45 Auto Discount 2: Range Lowwer 0,0 Number type
            data.segments.add("0,0"); //46 Auto Discount 2: Range Upper 0,0 Number type
            data.segments.add("0,0"); //47 Auto Discount 2: Target Value 0,0 SPECIAL FLOAT
            data.segments.add("0"); // 48 Auto Discount 3: Sort 0 Number type
            data.segments.add("127"); //49 Auto Discount 3: Weekdays 127 Number type
            data.segments.add("0,0"); //50 Auto Discount 3: Range Lowwer 0,0 Number type
            data.segments.add("0,0"); //51 Auto Discount 3: Range Upper 0,0 Number type
            data.segments.add("0,0"); //52 Auto Discount 3: Target Value 0,0 SPECIAL FLOAT
            data.segments.add("0"); //53 Auto Discount 4: Sort 0 Number type
            data.segments.add("127"); //54 Auto Discount 4: Weekdays 127 Number type
            data.segments.add("0,0"); //55 Auto Discount 4: Range Lowwer 0,0 Number type
            data.segments.add("0,0"); //56 Auto Discount 4: Range Upper 0,0 Number type
            data.segments.add("0,0"); //57 Auto Discount 4: Target Value 0,0 SPECIAL FLOAT
            data.segments.add("0"); //58 Tax sort 0 Number type
            data.segments.add("0"); //59 Tax rate 0 1=0.01%
            data.segments.add("0"); //60 SspValue 1 0 Number type
            data.segments.add("0"); //61 SspValue 2 0 Number type
            data.segments.add("0"); //62 SspValue 3 0 Number type
            data.segments.add("0"); //63 SspValue 4 0 Number type
            data.segments.add("0"); //64 PLU Type 0 0=Normal, 1=Tare only
            data.segments.add(""); //65 Alfa function Number type
            data.segments.add("0"); //66 PLU Flag 0 Number type
            data.segments.add("0"); //67 PLU Flag Value 0 Number type
            data.segments.add("0"); //68 Bitmap
            
            return sendData(data);
        
//            data.clear();
//            data.segments.add("END");
//            data.segments.add("PLU");
//            sendData(data);
        //}
    
        //return result;
    }
    
    private Result clearPLU() {
        
        Result result;
        
        Data data = new Data();
        data.segments.add("CLR");
        data.segments.add("PLU");
        data.segments.add("-1");
        
        if ((result = sendData(data)).success()) {
            
            data.clear();
            data.segments.add("CLR");
            data.segments.add("END");
            
            result = sendData(data);
        }
        
        return result;
    }
    
    private String getPluNumber(ScalesItem item) {
        return item.pluNumber != null ? String.valueOf(item.pluNumber) : item.idBarcode != null ? item.idBarcode : "";
    }
    
    String floatValue(double value) {
        if (value > 0)
            return String.format("%.2f", value).replace(",", "").replace(".", "") + ",2";
        return "0,0";
    }
    
    String stringValue(String value) {
        if (StringUtils.isBlank(value))
            return "";
        return value.replace("\t", "").replace('\n', (char)0x0b).replace("\r", "");
    }
    
    private static boolean interrupted = false; //прерываем загрузку в рамках одной транзакции. Устанавливается при interrupt exception и сбрасывается при release
    
    @Override
    protected SendTransactionTask getTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
        return new DPWaspSendTransactionTask(transaction, scales);
    }
    
    class DPWaspSendTransactionTask extends SendTransactionTask {
        public DPWaspSendTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
            super(transaction, scales);
            initSettings();
        }
        
        @Override
        protected SendTransactionResult run() {
            
            String[] hostPort = scales.port.split(":");
            port = hostPort.length == 1 ? new TCPPort(scales.port, 33581) : new TCPPort(hostPort[0], Integer.parseInt(hostPort[1]));
    
            String error = null;
            boolean cleared = false;
            
            try {
    
                logger.info(getLogPrefix() + String.format("Connect, ip %s, transaction %s", scales.port, transaction.id));
                port.open();
                
                Result result = new Result.Success();
                
                boolean needToClear = !transaction.itemsList.isEmpty() && transaction.snapshot && !scales.cleared;
                if (needToClear) {
                    result = clearPLU();
                    cleared = result.success();
                }
    
                if (cleared || !needToClear) {
                    logger.info(getLogPrefix() + String.format("transaction %s, ip %s, sending %s items...", transaction.id, scales.port, transaction.itemsList.size()));
                    
                    if (result.success()) {
                        
                        Data data = new Data();
                        data.segments.add("DWL");
                        data.segments.add("PLU");
    
                        if ((result = sendData(data)).success()) {
    
                            int count = 0;
    
                            for (ScalesItem item : transaction.itemsList) {
                                count++;
                                if (notInterruptedTransaction(transaction.id)) {
                                    logger.info(String.format(getLogPrefix() + "IP %s, Transaction #%s, sending item #%s (barcode %s) of %s", scales.port, transaction.id, count, item.idBarcode, transaction.itemsList.size()));
            
                                    result = sendPLU(scales, item);
            
                                    if (!result.success()) {
                                        error = result.errorMessage;
                                        logger.error(getLogPrefix() + String.format("ip %s, error: %s", scales.port, error));
                                        break;
                                    }
                                }
                                else break;
                            }

                            data.clear();
                            data.segments.add("END");
                            data.segments.add("PLU");
    
                            if (!(result = sendData(data)).success()) {
                                error = result.errorMessage;
                                logger.error(getLogPrefix() + String.format("ip %s, error: %s", scales.port, error));
                            }
                        }
                        else {
                            error = result.errorMessage;
                            logger.error(getLogPrefix() + String.format("ip %s, error: %s", scales.port, error));
                        }
                    }
                }
    
                logger.info(getLogPrefix() + String.format("Disconnect, ip %s, transaction %s", scales.port, transaction.id));
                port.close();
                
            } catch (Throwable t) {
                interrupted = t instanceof InterruptedException;
                error = String.format(getLogPrefix() + "ip %s error, transaction %s: %s", scales.port, transaction.id, ExceptionUtils.getStackTraceString(t));
                logger.error(error);
            }
            finally {
                try {
                    logger.info(getLogPrefix() + String.format("Disconnect, ip %s, transaction %s", scales.port, transaction.id));
                    port.close();
                } catch (CommunicationException ignored) {}
            }
    
            logger.info(getLogPrefix() + "Completed ip: " + scales.port);
            return new SendTransactionResult(scales, error != null ? Collections.singletonList(error) : new ArrayList<>(), interrupted, cleared);
        }
    
        protected void initSettings() {
            settings = springContext.containsBean("dataPrintWaspSettings") ? (DataPrintWaspSettings) springContext.getBean("dataPrintWaspSettings") : new DataPrintWaspSettings();
            debugMode = nvl(settings.getDebugMode(), false);
        }
        
    }
}
