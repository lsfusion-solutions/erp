package equ.clt.handler;

import equ.api.MachineryInfo;
import equ.api.SendTransactionBatch;
import equ.api.stoplist.StopListInfo;
import equ.api.scales.ScalesHandler;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class DefaultScalesHandler extends ScalesHandler {

    protected final static Logger processTransactionLogger = Logger.getLogger("TransactionLogger");
    protected final static Logger processStopListLogger = Logger.getLogger("StopListLogger");

    @Override
    public void sendStopListInfo(StopListInfo stopListInfo, Set<MachineryInfo> machineryInfoList) throws IOException {
    }

    @Override
    public Map<Long, SendTransactionBatch> sendTransaction(List<TransactionScalesInfo> transactionInfoList) throws IOException {
        return null;
    }

    protected abstract String getLogPrefix();

    protected boolean isWeight(ScalesItemInfo item, int type) {
        switch (type) {
            case 0:
            default:
                return item.passScalesItem && item.splitItem;
            case 1:
                return item.passScalesItem && (item.shortNameUOM == null || !item.shortNameUOM.toUpperCase().startsWith("ШТ"));
            case 2:
                return item.splitItem || (item.shortNameUOM == null || !item.shortNameUOM.toUpperCase().startsWith("ШТ"));
        }
    }

    protected List<ScalesInfo> getEnabledScalesList(TransactionScalesInfo transaction, List<MachineryInfo> succeededScalesList) {
        List<ScalesInfo> enabledScalesList = new ArrayList<>();
        for (ScalesInfo scales : transaction.machineryInfoList) {
            if(scales.succeeded)
                succeededScalesList.add(scales);
            else if (scales.enabled)
                enabledScalesList.add(scales);
        }
        if (enabledScalesList.isEmpty())
            for (ScalesInfo scales : transaction.machineryInfoList) {
                if (!scales.succeeded)
                    enabledScalesList.add(scales);
            }
        return enabledScalesList;
    }

    protected void errorMessages(Map<String, List<String>> errors, Set<String> ips, Map<String, String> brokenPortsMap) {
        if (!errors.isEmpty()) {
            String message = "";
            for (Map.Entry<String, List<String>> entry : errors.entrySet()) {
                message += entry.getKey() + ": \n" + StringUtils.join(entry.getValue().iterator(), "\n");
            }
            throw new RuntimeException(message);
        } else if (ips.isEmpty() && brokenPortsMap.isEmpty())
            throw new RuntimeException(getLogPrefix() + "No IP-addresses defined");
    }

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