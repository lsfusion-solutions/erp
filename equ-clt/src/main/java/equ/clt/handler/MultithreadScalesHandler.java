package equ.clt.handler;

import equ.api.scales.ScalesInfo;
import org.apache.log4j.Logger;

import java.util.List;

public abstract class MultithreadScalesHandler extends DefaultScalesHandler {

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");

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

}