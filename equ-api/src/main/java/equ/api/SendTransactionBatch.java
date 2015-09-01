package equ.api;

import java.util.List;

public class SendTransactionBatch {
    public List<MachineryInfo> succeededMachineryList;
    public List<MachineryInfo> clearedMachineryList;
    public Throwable exception;

    public SendTransactionBatch(Throwable exception) {
        this(null, exception);
    }

    public SendTransactionBatch(List<MachineryInfo> succeededMachineryList, Throwable exception) {
        this(null, succeededMachineryList, exception);
    }

    public SendTransactionBatch(List<MachineryInfo> clearedMachineryList, List<MachineryInfo> succeededMachineryList, Throwable exception) {
        this.clearedMachineryList = clearedMachineryList;
        this.succeededMachineryList = succeededMachineryList;
        this.exception = exception;

    }
}
