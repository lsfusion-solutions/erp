package equ.clt.handler.bizerba;

import equ.api.ItemInfo;
import equ.api.MachineryInfo;
import equ.api.SendTransactionBatch;
import equ.api.StopListInfo;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import equ.api.scales.TransactionScalesInfo;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import javax.naming.CommunicationException;
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

    @Override
    protected String loadImages(List<String> errors, ScalesInfo scales, TCPPort port, ScalesItemInfo item) throws CommunicationException {
        if(item.imagesCount != null) {
            for (int i = 1; i <= item.imagesCount; i++) {
                Integer pluNumber = getPluNumber(item);
                String image = String.valueOf(pluNumber) + i + ".jpg";
                String message = "MDST  " + separator + getCancelFlag(0) + separator + "MDK1" + pluNumber + separator + "MDK2" + 1 + separator + "MDK3" + 1
                        + separator + "TABB" + "PLST" + separator + "MDLN" + i + separator + "MTYP" + 1 + separator + "MDAT" + image + endCommand;
                clearReceiveBuffer(port);
                sendCommand(errors, port, message, charset, scales.port, encode);
                String result = receiveReply(errors, port, charset, scales.port);
                if (!result.equals("0")) {
                    logError(errors, String.format("Bizerba: IP %s Result is %s, item: %s [image %s]", scales.port, result, item.idItem, i));
                    return result;
                }
            }
        }
        return null;
    }
}