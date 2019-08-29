package equ.clt.handler.astron;

import equ.api.SendTransactionBatch;
import equ.api.cashregister.TransactionCashRegisterInfo;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//TODO: удалить, когда закончится переход
public class AstronTempHandler extends AstronHandler {

    public AstronTempHandler(FileSystemXmlApplicationContext springContext) {
        super(springContext);
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionCashRegisterInfo> transactionList) {
        return new HashMap<>();
    }

    @Override
    protected void createSalesIndex(Connection conn) {
        //do nothing
    }

    @Override
    protected String getFusionProcessedIndexName() {
        return "Sales_FUSION_PROCESSED";
    }

    @Override
    protected String getSalesNumField() {
        return "SalesId";
    }

    @Override
    protected String getSalesRefundField() {
        return "SalesRefu";
    }
}