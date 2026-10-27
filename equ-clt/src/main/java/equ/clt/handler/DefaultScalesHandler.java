package equ.clt.handler;

import com.google.common.base.Throwables;
import equ.api.ItemInfo;
import equ.api.MachineryInfo;
import equ.api.SendTransactionBatch;
import equ.api.stoplist.StopListInfo;
import equ.api.scales.ScalesHandler;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItem;
import equ.api.scales.TransactionScalesInfo;
import equ.clt.EquipmentServer;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public abstract class DefaultScalesHandler extends ScalesHandler {

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected final static Logger processStopListLogger = Logger.getLogger("StopListLogger");

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machineryInfoList) throws IOException {
    }

    @Override
    public String getGroupId(TransactionScalesInfo transactionInfo) {
        StringBuilder groupId = new StringBuilder();
        for (MachineryInfo scales : transactionInfo.machineryInfoList) {
            groupId.append(scales.port).append(";");
        }
        return getLogPrefix() + groupId;
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionScalesInfo> transactionInfoList) {

        Map<Long, SendTransactionBatch> sendTransactionBatchMap = new HashMap<>();

        Map<String, String> brokenPortsMap = new HashMap<>();
        if(transactionInfoList.isEmpty()) {
            processTransactionLogger.error(getLogPrefix() + "Empty transaction list!");
        }
        boolean interrupted = false; //если на одной из транзакций случился InterruptedException, то следующие транзакции не выполняем
        for(TransactionScalesInfo transaction : transactionInfoList) {
            processTransactionLogger.info(getLogPrefix() + "Send Transaction # " + transaction.id);

            List<MachineryInfo> succeededScalesList = new ArrayList<>();
            List<MachineryInfo> clearedScalesList = new ArrayList<>();
            Exception exception = null;
            try {

                if(interrupted) {
                    throw new RuntimeException("Previous transaction has been interrupted");
                }

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
                                taskList.add(getTransactionTask(transaction, scales));
                            }
                        }
                    }

                    if(!taskList.isEmpty()) {
                        beforeStartTransactionExecutor();
                        try {
                            ExecutorService singleTransactionExecutor = EquipmentServer.getFixedThreadPool(getThreadPoolSize(taskList), "SendTransaction");
                            try {
                                List<Future<SendTransactionResult>> threadResults = singleTransactionExecutor.invokeAll(taskList);
                                for (Future<SendTransactionResult> threadResult : threadResults) {
                                    SendTransactionResult result = threadResult.get();
                                    if (result.localErrors.isEmpty())
                                        succeededScalesList.add(result.scales);
                                    else {
                                        String error = result.localErrors.get(0);
                                        int secondOccurrence = StringUtils.ordinalIndexOf(error, "\n", 2);
                                        brokenPortsMap.put(result.scales.port, error.substring(0, secondOccurrence > 0 ? secondOccurrence : error.length()));
                                        errors.put(result.scales.port, result.localErrors);
                                    }
                                    if(result.cleared)
                                        clearedScalesList.add(result.scales);
                                    if(result.interrupted) {
                                        interrupted = true;
                                        singleTransactionExecutor.shutdownNow();
                                        break;
                                    }
                                }
                            } finally {
                                singleTransactionExecutor.shutdown();
                            }
                        } finally {
                            afterFinishTransactionExecutor();
                        }

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

    protected void beforeStartTransactionExecutor() {
    }

    protected void afterFinishTransactionExecutor() {
    }

    protected int getThreadPoolSize(Collection<Callable<SendTransactionResult>> taskList) {
        return taskList.size();
    }

    protected abstract SendTransactionTask getTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales);

    //loads one item with retries: only retryable results (e.g. no reply from the scales) are retried,
    //errors of the previous attempts are discarded, only errors of the last attempt go to localErrors (also if it throws an exception)
    protected <R> R loadWithRetries(List<String> localErrors, int maxAttempts, Predicate<R> retryable, ItemLoader<R> loader) throws Exception {
        R result;
        List<String> attemptErrors;
        int attempt = 0;
        do {
            attemptErrors = new ArrayList<>();
            try {
                result = loader.load(attemptErrors, attempt++);
            } catch (Exception e) {
                localErrors.addAll(attemptErrors);
                throw e;
            }
        } while (retryable.test(result) && attempt < maxAttempts);
        localErrors.addAll(attemptErrors);
        return result;
    }

    protected interface ItemLoader<R> {
        //attempt starts from 0, attempt > 0 means retry (e.g. the port can be reopened)
        R load(List<String> errors, int attempt) throws Exception;
    }

    //sends stop-list to all scales in parallel, taskFactory creates the task for one scales, the task logs and returns errors
    protected void sendStopListParallel(StopListInfo stopListInfo, Set<MachineryInfo> machineryInfoSet,
                                        BiFunction<StopListInfo, ScalesInfo, Callable<List<String>>> taskFactory) {
        if (stopListInfo == null || stopListInfo.exclude)
            return;
        processStopListLogger.info(String.format(getLogPrefix() + "Send StopList # %s to %s scales", stopListInfo.number, machineryInfoSet.size()));
        try {
            Collection<Callable<List<String>>> taskList = new LinkedList<>();
            int skipped = 0;
            for (MachineryInfo machinery : machineryInfoSet) {
                if (machinery.port != null && machinery instanceof ScalesInfo) {
                    //don't connect to scales with nothing to delete
                    if (hasItemsToDelete(stopListInfo, (ScalesInfo) machinery))
                        taskList.add(taskFactory.apply(stopListInfo, (ScalesInfo) machinery));
                    else
                        skipped++;
                }
            }
            if (skipped > 0)
                processStopListLogger.info(String.format(getLogPrefix() + "StopList #%s: no items to delete, %s scales skipped", stopListInfo.number, skipped));

            if (!taskList.isEmpty()) {
                ExecutorService singleTransactionExecutor = EquipmentServer.getFixedThreadPool(taskList.size(), "SendStopList");
                try {
                    for (Future<List<String>> threadResult : singleTransactionExecutor.invokeAll(taskList))
                        threadResult.get(); //rethrows task exception
                } finally {
                    singleTransactionExecutor.shutdown();
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    protected boolean hasItemsToDelete(StopListInfo stopListInfo, ScalesInfo scales) {
        return stopListInfo.stopListItemMap.values().stream().anyMatch(this::isPLU);
    }

    protected boolean isPLU(ItemInfo item) {
        return item.idBarcode != null && item.idBarcode.length() <= 5;
    }

    protected abstract String getLogPrefix();

    //log of items sending to one scales: start, progress every minute and the list of sent items at the end instead of a line per item
    protected class SendItemsLog {
        private static final long progressInterval = 60000; //1 minute

        private final Logger logger;
        private final String prefix;
        private final int total;
        private final List<String> barcodes = new ArrayList<>();
        private int lastNumber;
        private long lastProgressTime;

        public SendItemsLog(Logger logger, String ip, Long transactionId, int total) {
            this.logger = logger;
            this.prefix = getLogPrefix() + String.format("IP %s, Transaction #%s, ", ip, transactionId);
            this.total = total;
            this.lastProgressTime = System.currentTimeMillis();
            logger.info(prefix + String.format("start sending %s items", total));
        }

        //number - position of the item in the transaction
        public void sending(int number, String barcode) {
            barcodes.add(barcode);
            lastNumber = number;
            long now = System.currentTimeMillis();
            if (now - lastProgressTime >= progressInterval) {
                logger.info(prefix + String.format("sending item #%s of %s", number, total));
                lastProgressTime = now;
            }
        }

        public void finish() {
            logger.info(prefix + String.format("sent %s items (last #%s of %s): %s", barcodes.size(), lastNumber, total, StringUtils.join(barcodes, ",")));
        }
    }

    protected JSONObject getExtInfo(String extInfo, String id) {
        return extInfo != null && !extInfo.isEmpty() ? new JSONObject(extInfo).optJSONObject(id) : null;
    }

    protected boolean isWeight(ScalesItem item, int type) {
        switch (type) {
            case 0:
            default:
                return item.passScalesItem && item.splitItem;
            case 1:
                return item.passScalesItem && !isPieceUOM(item);
            case 2:
                return item.splitItem || !isPieceUOM(item);
            case 3:
                return !isPieceUOM(item);
        }
    }

    private boolean isPieceUOM(ScalesItem item) {
        return item.shortNameUOM != null && item.shortNameUOM.toUpperCase().startsWith("ШТ");
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
                message += entry.getKey() + ": \n" + StringUtils.join(entry.getValue().iterator(), "\n");
            }
            throw new RuntimeException(message);
        } else if (ips.isEmpty() && brokenPortsMap.isEmpty())
            throw new RuntimeException(getLogPrefix() + "No IP-addresses defined");
    }

    protected String fillLeadingZeroes(Object input, int length) {
        if (input == null)
            return null;
        if(!(input instanceof String))
            input = String.valueOf(input);
        if (((String) input).length() > length)
            input = ((String) input).substring(0, length);
        while (((String) input).length() < length)
            input = "0" + input;
        return (String) input;
    }

    protected String fillTrailingSpaces(String input, int length) {
        if (input != null) {
            if (input.length() > length) {
                input = input.substring(0, length);
            } else while (input.length() < length) {
                input = input + " ";
            }
        }
        return input;
    }

    protected abstract class SendTransactionTask implements Callable<SendTransactionResult> {
        protected TransactionScalesInfo transaction;
        protected ScalesInfo scales;

        public SendTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
            this.transaction = transaction;
            this.scales = scales;
        }

        protected abstract SendTransactionResult run() throws Exception;

        @Override
        public SendTransactionResult call() throws Exception {
            return run();
        }

    }

    protected class SendTransactionResult {
        public ScalesInfo scales;
        public List<String> localErrors;
        public boolean interrupted;
        public boolean cleared;

        public SendTransactionResult(ScalesInfo scales, List<String> localErrors, boolean cleared) {
            this(scales, localErrors, false, cleared);
        }

        public SendTransactionResult(ScalesInfo scales, List<String> localErrors, boolean interrupted, boolean cleared) {
            this.scales = scales;
            this.localErrors = localErrors;
            this.interrupted = interrupted;
            this.cleared = cleared;
        }
    }
}