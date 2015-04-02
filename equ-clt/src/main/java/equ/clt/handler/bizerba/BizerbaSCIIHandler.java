package equ.clt.handler.bizerba;

import equ.api.MachineryInfo;
import equ.api.SendTransactionBatch;
import equ.api.SoftCheckInfo;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.ScalesSettings;
import equ.api.scales.TransactionScalesInfo;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import javax.naming.CommunicationException;
import java.io.IOException;
import java.util.*;

public class BizerbaSCIIHandler extends BizerbaHandler {

    private FileSystemXmlApplicationContext springContext;
    protected static String charset = "cp866";

    public BizerbaSCIIHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    public String getGroupId(TransactionScalesInfo transactionInfo) {

        ScalesSettings bizerbaSettings = springContext.containsBean("bizerbaSettings") ? (ScalesSettings) springContext.getBean("bizerbaSettings") : null;
        boolean allowParallel = bizerbaSettings == null || bizerbaSettings.isAllowParallel();
        if (allowParallel) {
            String groupId = "";
            for (MachineryInfo scales : transactionInfo.machineryInfoList) {
                groupId += scales.port + ";";
            }
            return "bizerbascii" + groupId;
        } else return "bizerbascii";
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
                                    String clear = clearAllPLU(localErrors, port, scales, charset, false);
                                    if (!clear.equals("0"))
                                        logError(localErrors, String.format("Bizerba: ClearAllPLU, Error %s", clear));
                                    clear = clearAllMessages(localErrors, port, scales, charset, false);
                                    if (!clear.equals("0"))
                                        logError(localErrors, String.format("Bizerba: ClearAllMessages, Error %s", clear));
                                }

                                processTransactionLogger.info("Bizerba: Sending items..." + ip);
                                if (localErrors.isEmpty()) {
                                    int count = 0;
                                    for (ScalesItemInfo item : transaction.itemsList) {
                                        if (!Thread.currentThread().isInterrupted()) {
                                            processTransactionLogger.info(String.format("Bizerba: Transaction  #%s, sending item #%s of %s", transaction.id, count, transaction.itemsList.size()));
                                            if (item.idBarcode != null && item.idBarcode.length() <= 5) {
                                                item.description = item.description == null ? "" : item.description;
                                                item.descriptionNumber = item.descriptionNumber == null ? 1 : item.descriptionNumber;
                                                String clear = clearMessage(localErrors, port, scales, item, true, charset, false);
                                                if (clear.equals("0")) {
                                                    loadPLU(localErrors, port, scales, item, charset, false);
                                                    count++;
                                                } else
                                                    logError(localErrors, String.format("Bizerba: ClearMessage, Error %s", clear));
                                            }
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
}
