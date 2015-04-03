package equ.clt.handler.bizerba;

import equ.api.SendTransactionBatch;
import equ.api.SoftCheckInfo;
import equ.api.scales.TransactionScalesInfo;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class BizerbaBSHandler extends BizerbaHandler {

    private FileSystemXmlApplicationContext springContext;
    protected String charset = "cp866";
    protected boolean encode = true;

    public BizerbaBSHandler(FileSystemXmlApplicationContext springContext) {
        this.springContext = springContext;
    }

    public String getGroupId(TransactionScalesInfo transactionInfo) {
        return getGroupId(springContext, transactionInfo, "bizerbabs");
    }

    @Override
    public Map<Integer, SendTransactionBatch> sendTransaction(List<TransactionScalesInfo> transactionList) throws IOException {
        return sendTransaction(transactionList, charset, encode);
    }

    @Override
    public void sendSoftCheck(SoftCheckInfo softCheckInfo) throws IOException {
    }
}
