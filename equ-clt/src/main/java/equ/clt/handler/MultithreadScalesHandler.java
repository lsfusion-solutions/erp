package equ.clt.handler;

import equ.api.MachineryInfo;
import equ.api.scales.ScalesInfo;
import equ.api.scales.TransactionScalesInfo;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.List;

public abstract class MultithreadScalesHandler extends DefaultScalesHandler {

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");

    @Override
    public String getGroupId(TransactionScalesInfo transactionInfo) {
        StringBuilder groupId = new StringBuilder();
        for (MachineryInfo scales : transactionInfo.machineryInfoList) {
            groupId.append(scales.port).append(";");
        }
        return getLogPrefix() + groupId;
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