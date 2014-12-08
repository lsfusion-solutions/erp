package equ.api.scales;

import equ.api.MachineryInfo;
import equ.api.TransactionInfo;

import java.io.IOException;
import java.util.List;

public class TransactionScalesInfo extends TransactionInfo<ScalesInfo, ScalesItemInfo> {
    public TransactionScalesInfo(Integer id, String dateTimeCode, String handlerModel, Integer idGroupMachinery,
                                 Integer nppGroupMachinery, String nameGroupMachinery, String comment,
                                 List<ScalesItemInfo> itemsList, List<ScalesInfo> machineryInfoList, Boolean snapshot) {
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
    public List<MachineryInfo> sendTransaction(Object handler, List<ScalesInfo> machineryInfoList) throws IOException {
        return ((ScalesHandler)handler).sendTransaction(this, machineryInfoList);
    }
}
