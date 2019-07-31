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

    protected abstract String getLogPrefix();

    protected String fillLeadingZeroes(Object input, int length) {
        if (input == null)
            return null;
        if(!(input instanceof String))
            input = String.valueOf(input);
        if (((String) input).length() > length)
            input = ((String) input).substring(0, length);
        while (((String) input).length() < length)
            input = "0" + input;
        return (String) input;
    }

    protected String fillTrailingSpaces(String input, int length) {
        if (input != null) {
            if (input.length() > length) {
                input = input.substring(0, length);
            } else while (input.length() < length) {
                input = input + " ";
            }
        }
        return input;
    }
}