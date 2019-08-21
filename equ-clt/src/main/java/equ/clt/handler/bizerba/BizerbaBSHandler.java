package equ.clt.handler.bizerba;

import equ.api.MachineryInfo;
import equ.api.SendTransactionBatch;
import equ.api.StopListInfo;
import equ.api.scales.TransactionScalesInfo;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class BizerbaBSHandler extends BizerbaHandler {

    protected String charset = "cp866";
    protected boolean encode = true;

    public BizerbaBSHandler(FileSystemXmlApplicationContext springContext) {
        super(springContext);
    }

    @Override
    protected String getModel() {
        return "bizerbabs";
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionScalesInfo> transactionList) {
        return sendTransaction(transactionList, charset, encode);
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machineryInfoSet) {
        sendStopListInfo(stopListInfo, machineryInfoSet, charset, encode);
    }

    @Override
    protected String getPricesCommand(int price, int retailPrice, boolean notInvertPrices) {
        return notInvertPrices ? super.getPricesCommand(price, retailPrice, true) : ("GPR1" + price + separator + "EXPR" + retailPrice + separator);
    }
}
