package equ.clt.handler.bizerba;

import equ.api.MachineryInfo;
import equ.api.SendTransactionBatch;
import equ.api.StopListInfo;
import equ.api.scales.TransactionScalesInfo;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BizerbaBSHandler extends BizerbaHandler {

    protected String charset = "cp866";
    protected boolean encode = true;

    public BizerbaBSHandler(FileSystemXmlApplicationContext springContext) {
        super(springContext);
    }

    public String getGroupId(TransactionScalesInfo transactionInfo) {
        return getGroupId(springContext, transactionInfo, "bizerbabs");
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionScalesInfo> transactionList) throws IOException {
        return sendTransaction(transactionList, charset, encode);
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machineryInfoSet) throws IOException {
        sendStopListInfo(stopListInfo, machineryInfoSet, charset, encode);
    }

    @Override
    protected String getPricesCommand(int price, int retailPrice) {
        return "GPR1" + price + separator + "EXPR" + retailPrice + separator;
    }
}
