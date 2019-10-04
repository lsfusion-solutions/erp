package equ.clt.handler;

import equ.api.MachineryInfo;
import equ.api.SendTransactionBatch;
import equ.api.scales.ScalesInfo;
import equ.api.scales.TransactionScalesInfo;
import equ.clt.EquipmentServer;
import lsfusion.base.Pair;

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
        for(TransactionScalesInfo transaction : transactionInfoList) {
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
                                taskList.add(getTransactionTask(transaction, scales));
                            }
                        }
                    }

                    if(!taskList.isEmpty()) {
                        beforeStartTransactionExecutor();
                        try {
                            ExecutorService singleTransactionExecutor = EquipmentServer.getFixedThreadPool(taskList.size(), "SendTransaction");
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

    protected abstract SendTransactionTask getTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales);

    protected abstract class SendTransactionTask implements Callable<SendTransactionResult> {
        protected TransactionScalesInfo transaction;
        protected ScalesInfo scales;

        public SendTransactionTask(TransactionScalesInfo transaction, ScalesInfo scales) {
            this.transaction = transaction;
            this.scales = scales;
        }

        protected abstract Pair<List<String>, Boolean> run() throws Exception;

        @Override
        public SendTransactionResult call() throws Exception {
            Pair<List<String>, Boolean> result = run();
            return new SendTransactionResult(scales, result.first, result.second);
        }

    }

    protected class SendTransactionResult {
        public ScalesInfo scalesInfo;
        public List<String> localErrors;
        public boolean cleared;

        public SendTransactionResult(ScalesInfo scalesInfo, List<String> localErrors, boolean cleared) {
            this.scalesInfo = scalesInfo;
            this.localErrors = localErrors;
            this.cleared = cleared;
        }
    }

    protected void safeDelete(File file) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

}