package equ.clt.handler;

import equ.api.MachineryInfo;
import equ.api.SendTransactionBatch;
import equ.api.StopListInfo;
import equ.api.scales.ScalesHandler;
import equ.api.scales.TransactionScalesInfo;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class DefaultScalesHandler extends ScalesHandler {

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machineryInfoList) throws IOException {
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionScalesInfo> transactionInfoList) throws IOException {
        return null;
    }
}