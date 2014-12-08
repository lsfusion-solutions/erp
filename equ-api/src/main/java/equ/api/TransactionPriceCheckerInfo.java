package equ.api;

import java.io.IOException;
import java.util.List;

public class TransactionPriceCheckerInfo extends TransactionInfo<PriceCheckerInfo, PriceCheckerItemInfo> {

    public TransactionPriceCheckerInfo(Integer id, String dateTimeCode, String handlerModel, Integer idGroupMachinery, Integer nppGroupMachinery,
                                       String nameGroupMachinery, String comment, List<PriceCheckerItemInfo> itemsList,
                                       List<PriceCheckerInfo> machineryInfoList, Boolean snapshot) {
        this.id = id;
        this.dateTimeCode = dateTimeCode;
        this.handlerModel = handlerModel;
        this.idGroupMachinery = idGroupMachinery;
        this.nppGroupMachinery = nppGroupMachinery;
        this.nameGroupMachinery = nameGroupMachinery;
        this.comment = comment;
        this.itemsList = itemsList;
        this.machineryInfoList = machineryInfoList;
        this.snapshot = snapshot;
    }

    @Override
    public List<MachineryInfo> sendTransaction(Object handler, List<PriceCheckerInfo> machineryInfoList) throws IOException {
        return ((PriceCheckerHandler)handler).sendTransaction(this, machineryInfoList);
    }
}
