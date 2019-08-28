package equ.clt.handler.bizerba;

import equ.api.ItemInfo;
import equ.api.scales.ScalesInfo;
import equ.api.scales.ScalesItemInfo;
import equ.clt.handler.TCPPort;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.util.List;

public class BizerbaPCBasedHandler extends BizerbaHandler {

    public BizerbaPCBasedHandler(FileSystemXmlApplicationContext springContext) {
        super(springContext);
    }

    @Override
    protected String getModel() {
        return "bizerbapc";
    }

    @Override
    protected String getIdItemGroup(ItemInfo item) {
        return item.idItemGroup;
    }

    @Override
    protected String loadImages(List<String> errors, ScalesInfo scales, TCPPort port, ScalesItemInfo item) {
        if(item.imagesCount != null) {
            for (int i = 1; i <= item.imagesCount; i++) {
                clearReceiveBuffer(port);
                sendCommand(errors, port, getLoadImageMessage(scales, item, i, 0), scales.port);
                String result = receiveReply(errors, port, scales.port);
                if (!result.equals("0")) {
                    logError(errors, String.format("Bizerba: IP %s Result is %s, item: %s [image %s]", scales.port, result, item.idItem, i));
                    return result;
                }
            }
        } else {
            //шлём удаление. Пока что рассчитано на то, что max imagesCount = 1
            int i = 1;
            clearReceiveBuffer(port);
            sendCommand(errors, port, getLoadImageMessage(scales, item, i, 1), scales.port);
            String result = receiveReply(errors, port, scales.port);
            if (!result.equals("0")) {
                logError(errors, String.format("Bizerba: IP %s Result is %s, item: %s [image %s]", scales.port, result, item.idItem, i));
                return result;
            }
        }
        return null;
    }

    private String getLoadImageMessage(ScalesInfo scales, ScalesItemInfo item, int i, int cancelFlag) {
        Integer pluNumber = getPluNumber(item);
        String image = item.idItem + "_" + i + ".jpg";
        return "MDST  " + separator + "S" + zeroedInt(scales.number, 2) + separator + getCancelFlag(cancelFlag) + separator + "MDK1" + pluNumber + separator + "MDK2" + 1 + separator + "MDK3" + 0
                + separator + "TABB" + "PLST" + separator + "MDLN" + i + separator + "MTYP" + 1 + separator + "MDAT" + image + endCommand;
    }

//    @Override
//    public Integer getTarePercent(ScalesItemInfo item) {
//        return item.extraPercent == null ? 0 : item.extraPercent.multiply(BigDecimal.valueOf(100)).intValue();
//    }
}