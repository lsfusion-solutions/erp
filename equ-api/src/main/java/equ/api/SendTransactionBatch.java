package equ.api;

import java.util.List;
import java.util.Set;

public class SendTransactionBatch {
    public List<MachineryInfo> succeededMachineryList;
    public List<MachineryInfo> clearedMachineryList;
    public Integer nppGroupMachinery;
    public Set<String> deleteBarcodeSet;
    public Throwable exception;

    public SendTransactionBatch(Throwable exception) {
        this(null, exception);
    }

    public SendTransactionBatch(List<MachineryInfo> succeededMachineryList, Throwable exception) {
        this(null, succeededMachineryList, exception);
    }

    public SendTransactionBatch(List<MachineryInfo> clearedMachineryList, List<MachineryInfo> succeededMachineryList, Throwable exception) {
        this(clearedMachineryList, succeededMachineryList, null, null, exception);
    }

    public SendTransactionBatch(List<MachineryInfo> succeededMachineryList, List<MachineryInfo> clearedMachineryList, Integer nppGroupMachinery, Set<String> deleteBarcodeSet, Throwable exception) {
        this.succeededMachineryList = succeededMachineryList;
        this.clearedMachineryList = clearedMachineryList;
        this.nppGroupMachinery = nppGroupMachinery;
        this.deleteBarcodeSet = deleteBarcodeSet;
        this.exception = exception;
    }
}
