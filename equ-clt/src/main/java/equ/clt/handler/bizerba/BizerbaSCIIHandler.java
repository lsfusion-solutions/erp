package equ.clt.handler.bizerba;

import equ.api.MachineryInfo;
import equ.api.SendTransactionBatch;
import equ.api.StopListInfo;
import equ.api.scales.TransactionScalesInfo;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class BizerbaSCIIHandler extends BizerbaHandler {

    protected String charset = "utf-8";
    protected boolean encode = false;

    public BizerbaSCIIHandler(FileSystemXmlApplicationContext springContext) {
        super(springContext);
    }

    @Override
    protected String getModel() {
        return "bizerbascii";
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionScalesInfo> transactionList) {
        return sendTransaction(transactionList, charset, encode);
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machineryInfoSet) {
        sendStopListInfo(stopListInfo, machineryInfoSet, charset, encode);
    }
}
