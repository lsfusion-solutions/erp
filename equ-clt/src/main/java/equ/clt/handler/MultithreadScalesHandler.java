package equ.clt.handler;

import equ.api.MachineryInfo;
import equ.api.SendTransactionBatch;
import equ.api.scales.ScalesInfo;
import equ.api.scales.TransactionScalesInfo;
import equ.clt.EquipmentServer;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public abstract class MultithreadScalesHandler extends DefaultScalesHandler {

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
                            singleTransactionExecutor.shutdown();
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
            SendTransactionResult result = run();
            return result;
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

    protected void safeDelete(File file) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

}