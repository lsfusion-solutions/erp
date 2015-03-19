package equ.api;

import java.util.List;

public class SendTransactionBatch {
    public List<MachineryInfo> succeededMachineryList;
    public Throwable exception;

    public SendTransactionBatch(Throwable exception) {
        this(null, exception);
    }

    public SendTransactionBatch(List<MachineryInfo> succeededMachineryList, Throwable exception) {
        this.succeededMachineryList = succeededMachineryList;
        this.exception = exception;
    }
}
