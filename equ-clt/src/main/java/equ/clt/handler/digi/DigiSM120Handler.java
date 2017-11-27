package equ.clt.handler.digi;

import equ.api.MachineryInfo;
import equ.api.SendTransactionBatch;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
import equ.clt.EquipmentServer;
import org.apache.commons.codec.DecoderException;
import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import javax.naming.CommunicationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static equ.clt.handler.HandlerUtils.safeMultiply;

public class DigiSM120Handler extends DigiHandler {

    private final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");

    private static String separator = ",";

    public DigiSM120Handler(FileSystemXmlApplicationContext springContext) {
        super(springContext);
    }

    protected String getLogPrefix() {
        return "Digi SM120: ";
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionScalesInfo> transactionList) throws IOException {

        Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        Map<String, String> brokenPortsMap = new HashMap<>();
        if(transactionList.isEmpty()) {
            processTransactionLogger.error(getLogPrefix() + "Empty transaction list!");
        }
        for(TransactionScalesInfo transaction : transactionList) {
            processTransactionLogger.info(getLogPrefix() + "Send Transaction # " + transaction.id);

            List<MachineryInfo> succeededScalesList = new ArrayList<>();
            List<MachineryInfo> clearedScalesList = new ArrayList<>();
            Exception exception = null;
            try {

                if (!transaction.machineryInfoList.isEmpty()) {

                    List<ScalesInfo> enabledScalesList = getEnabledScalesList(transaction, succeededScalesList);
                    Map<String, List<String>> errors = new HashMap<>();
                    Set<String> ips = new HashSet<>();

                    processTransactionLogger.info(getLogPrefix() + "Starting sending to " + enabledScalesList.size() + " scales...");
                    Collection<Callable<SendTransactionResult>> taskList = new LinkedList<>();
                    for (ScalesInfo scales : enabledScalesList) {
                        if (scales.port != null) {
                            String brokenPortError = brokenPortsMap.get(scales.port);
                            if(brokenPortError != null) {
                                errors.put(scales.port, Collections.singletonList(String.format("Broken ip: %s, error: %s", scales.port, brokenPortError)));
                            } else {
                                ips.add(scales.port);
                                taskList.add(new SendTransactionTask(transaction, scales));
                            }
                        }
                    }

                    if(!taskList.isEmpty()) {
                        ExecutorService singleTransactionExecutor = EquipmentServer.getFixedThreadPool(taskList.size(), "DigiSendTransaction");
                        List<Future<SendTransactionResult>> threadResults = singleTransactionExecutor.invokeAll(taskList);
                        for (Future<SendTransactionResult> threadResult : threadResults) {
                            if(threadResult.get().localErrors.isEmpty())
                                succeededScalesList.add(threadResult.get().scalesInfo);
                            else {
                                brokenPortsMap.put(threadResult.get().scalesInfo.port, threadResult.get().localErrors.get(0));
                                errors.put(threadResult.get().scalesInfo.port, threadResult.get().localErrors);
                            }
                            if(threadResult.get().cleared)
                                clearedScalesList.add(threadResult.get().scalesInfo);
                        }
                        singleTransactionExecutor.shutdown();
                    }
                    if(!enabledScalesList.isEmpty())
                        errorMessages(errors, ips, brokenPortsMap);

                }
            } catch (Exception e) {
                exception = e;
            }
            sendTransactionBatchMap.put(transaction.id, new SendTransactionBatch(clearedScalesList, succeededScalesList, exception));
        }
        return sendTransactionBatchMap;
    }

    private byte[] makeRecord(ScalesItemInfo item) throws IOException, DecoderException {

        Integer plu = item.pluNumber == null ? Integer.parseInt(item.idItem) : item.pluNumber;
        String pluNumber = fillLeadingZeroes(plu, 6);

        int flagForDelete = 0; //No data/0: Add or Change, 1: Delete
        int isWeight = item.splitItem ? 0 : 1; //0: Weighed item   1: Non-weighed item
        String price = getPrice(item.price); //max 9999.99
        String labelFormat1 = "017";
        String labelFormat2 = "0";
        String barcodeFormat = "12";
        String barcodeFlagOfEANData = "12";

        String itemCodeOfEANData = "3456789012"; //6-digit Item code + 4-digit Expanded item code
        String extendItemCodeOfEANData = "";
        String barcodeTypeOfEANData = "0"; //0: EAN 9: ITF
        String rightSideDataOfEANData = "1"; //0: Price 1: Weight 2: QTY 3: Original price 4: Weight/QTY 5: U.P. 6: U.P. after discount

        String cellByDate = fillLeadingZeroes(item.daysExpiry == null ? 0 : item.daysExpiry, 3); //days
        String cellByTime = fillLeadingZeroes(item.hoursExpiry == null ? 0 : item.hoursExpiry, 2) + "00";//HHmm

        String quantity = "0001";
        String quantitySymbol = "00"; //0 No print, 1 PCS, 2 FOR, 3 kg, 4 lb, 5 g, 6 oz

        String stepDiscountStartDate = "000000"; //YY MM DD
        String stepDiscountStartTime = "0000"; //HH MM
        String stepDiscountEndDate = "000000"; //YY MM DD
        String stepDiscountEndTime = "0000"; //HH MM

        String stepDiscountPoint1 = "000000"; //Weight or Quantity
        String stepDiscountValue1 = getPrice(item.price); //Price value or Percent value
        String stepDiscountPoint2 = "99999"; //Weight or Quantity
        String stepDiscountValue2 = getPrice(item.price); //Price value or Percent value
        String stepDiscountType = "02"; //"0: No step discount, 1: Free item, 2: Unit price discount, 3: Unit price % discount, 4: Total price discount, 5: Total price % discount, 6: Fixed price discount, 11: U.P./PCS - U.P./kg
        String typeOfMarkdown = "0"; //"0: No markdown, 1: Unit price markdown, 2: Price markdown, 3: Unit price and price markdown

        String emptyNameLine = getNameLine("00", ""); //можно задать до 4 строк наименования, но пока ограничимся одной

        byte[] dataBytes = getBytes(pluNumber + separator + flagForDelete + separator + isWeight + separator + "0" + separator +
                "1" + separator + "1" + separator + "1" + separator + "1" + separator + "1" + separator  + "0" + separator +
                "0" + separator + "0" + separator + "0" + separator + "0" + separator + "0" + separator + "0" + separator +
                "0" + separator + "0" + separator + price + separator + labelFormat1 + separator + labelFormat2 + separator +
                barcodeFormat + separator + barcodeFlagOfEANData + separator + itemCodeOfEANData + separator + extendItemCodeOfEANData + separator +
                barcodeTypeOfEANData + separator + rightSideDataOfEANData + separator + "000000" + separator + "000000" + separator +
                cellByDate + separator + cellByTime + separator + "000" + separator + "000" + separator + "0000" + separator +
                "000000" + separator + "0000" + separator + quantity + separator + quantitySymbol + separator + "0" + separator +
                "0000" + separator + "00" + separator + "00" + separator + "00" + separator + "00" + separator + "00" + separator +
                "00" + separator + "00" + separator + "00" + separator + "00" + separator + "00" + separator + pluNumber + separator +
                pluNumber + separator + "000000" + separator + "000000" + separator + "000000" + separator + "000000" + separator +
                "000000" + separator + "000000" + separator + "000000" + separator + "000000" + separator + "000000" + separator +
                "000000" + separator + "000000" + separator + "000000" + separator + "000000" + separator + "000000" + separator +
                "000000" + separator + "000000" + separator + "000000" + separator + "000000" + separator + "000000" + separator +
                "000000" + separator + "000000" + separator + "000000" + separator + stepDiscountStartDate + separator + stepDiscountStartTime + separator +
                stepDiscountEndDate + separator + stepDiscountEndTime + separator + stepDiscountPoint1 + separator + stepDiscountValue1 + separator +
                stepDiscountPoint2 + separator + stepDiscountValue2 + separator + stepDiscountType + separator + typeOfMarkdown + separator +
                "0" + separator + "0" + separator + "0" + separator + "0" + separator + "0" + separator + "0" + separator +
                "0" + separator + "0" + separator + "000000" + separator + "000000" + separator + "000000" + separator +
                "000000" + separator + "000000" + separator + "00000000" + separator + "00000000" + separator + "00000" + separator +
                "000000" + separator + getNameLine("09", item.name) + separator + emptyNameLine + separator + emptyNameLine + separator +
                emptyNameLine + separator + "000000" + separator + "000000" + separator + "0" + separator + "000000" + separator +
                "000000" + separator + "00" + separator + "00");

        int totalSize = dataBytes.length + 8;
        ByteBuffer bytes = ByteBuffer.allocate(totalSize);
        bytes.putInt(plu); //4 bytes
        bytes.putShort((short) totalSize); //2 bytes
        bytes.put(dataBytes);
        bytes.put(new byte[] {0x0d, 0x0a});

        return bytes.array();
    }

    private String getPrice(BigDecimal price) {
        return fillLeadingZeroes(safeMultiply(price, 100).intValue(), 6);
    }

    private String getNameLine(String font, String line) {
        return font + separator + "\"" + line.replace("\"", "\"\"") + "\"";
    }

    class SendTransactionTask implements Callable<SendTransactionResult> {
        TransactionScalesInfo transaction;
        ScalesInfo scales;

        public SendTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
            this.transaction = transaction;
            this.scales = scales;
        }

        @Override
        public SendTransactionResult call() throws Exception {
            List<String> localErrors = new ArrayList<>();
            boolean cleared = false;
            DataSocket socket = new DataSocket(scales.port);
            try {
                socket.open();
                int globalError = 0;
                boolean needToClear = !transaction.itemsList.isEmpty() && transaction.snapshot && !scales.cleared;
                if (needToClear) {
                    processTransactionLogger.info(getLogPrefix() + "Deleting all plu at scales " + scales.port);
                    int reply = sendRecord(socket, cmdCls, filePLU, new byte[0]);
                    if (reply != 0) {
                        logError(localErrors, String.format("Deleting all plu at scales %s failed. Error: %s\n", scales.port, reply));
                    } else
                        cleared = true;
                }

                if (cleared || !needToClear) {
                    processTransactionLogger.info(getLogPrefix() + "Sending items..." + scales.port);
                    if (localErrors.isEmpty()) {
                        int count = 0;
                        for (ScalesItemInfo item : transaction.itemsList) {
                            count++;
                            if (!Thread.currentThread().isInterrupted() && globalError < 3) {
                                processTransactionLogger.info(String.format(getLogPrefix() + "IP %s, Transaction #%s, sending item #%s (barcode %s) of %s", scales.port, transaction.id, count, item.idBarcode, transaction.itemsList.size()));
                                int barcode = Integer.parseInt(item.idBarcode.substring(0, 5));
                                int pluNumber = item.pluNumber == null ? barcode : item.pluNumber;
                                if(item.idBarcode.length() <= 5) {
                                    byte[] record = makeRecord(item);
                                    processTransactionLogger.info(String.format(getLogPrefix() + "Sending item %s to scales %s", pluNumber, scales.port));
                                    int reply = sendRecord(socket, cmdWrite, filePLU, record);
                                    if (reply != 0) {
                                        logError(localErrors, String.format(getLogPrefix() + "Send item %s to scales %s failed. Error: %s", pluNumber, scales.port, reply));
                                        globalError++;
                                    }
                                } else {
                                    processTransactionLogger.info(String.format(getLogPrefix() + "Sending item %s to scales %s failed: incorrect barcode %s", pluNumber, scales.port, item.idBarcode));
                                }
                            } else break;
                        }
                    }
                    socket.close();
                }

            } catch (Exception e) {
                logError(localErrors, String.format(getLogPrefix() + "IP %s error, transaction %s;", scales.port, transaction.id), e);
            } finally {
                processTransactionLogger.info(getLogPrefix() + "Finally disconnecting..." + scales.port);
                try {
                    socket.close();
                } catch (CommunicationException e) {
                    logError(localErrors, String.format(getLogPrefix() + "IP %s close port error ", scales.port), e);
                }
            }
            processTransactionLogger.info(getLogPrefix() + "Completed ip: " + scales.port);
            return new SendTransactionResult(scales, localErrors, cleared);
        }
    }
}