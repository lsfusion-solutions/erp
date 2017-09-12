package equ.clt.handler.bizerba;

import equ.api.MachineryInfo;
import equ.api.SendTransactionBatch;
import equ.api.StopListInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BizerbaBCIIHandler extends BizerbaHandler {

    protected String charset = "utf-8";
    protected boolean encode = false;

    public BizerbaBCIIHandler(FileSystemXmlApplicationContext springContext) {
        super(springContext);
    }

    public String getGroupId(TransactionScalesInfo transactionInfo) {
        return getGroupId(springContext, transactionInfo, "bizerbabcii");
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionScalesInfo> transactionList) throws IOException {
        return sendTransaction(transactionList, charset, encode);
    }

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machineryInfoList) throws IOException {
        sendStopListInfo(stopListInfo, machineryInfoList, charset, encode);
    }

    @Override
    public Integer getTarePercent(ScalesItemInfo item) {
        return item.extraPercent == null ? 0 : item.extraPercent.multiply(BigDecimal.valueOf(100)).intValue();
    }
}
