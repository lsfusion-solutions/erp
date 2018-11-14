package equ.clt.handler.bizerba;

import equ.api.ItemInfo;
import equ.api.MachineryInfo;
import equ.api.SendTransactionBatch;
import equ.api.StopListInfo;
import equ.api.scales.TransactionScalesInfo;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BizerbaPCBasedHandler extends BizerbaHandler {

    protected String charset = "utf-8";
    protected boolean encode = false;

    public BizerbaPCBasedHandler(FileSystemXmlApplicationContext springContext) {
        super(springContext);
    }

    @Override
    protected String getModel() {
        return "bizerbapc";
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
    protected String getIdItemGroup(ItemInfo item) {
        return item.idItemGroup;
    }
}