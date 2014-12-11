package equ.api.scales;

import equ.api.MachineryInfo;
import equ.api.TransactionInfo;

import java.io.IOException;
import java.sql.Date;
import java.util.List;

public class TransactionScalesInfo extends TransactionInfo<ScalesInfo, ScalesItemInfo> {
    public TransactionScalesInfo(Integer id, String dateTimeCode, Date date, String handlerModel, Integer idGroupMachinery,
                                 Integer nppGroupMachinery, String nameGroupMachinery, String description,
                                 List<ScalesItemInfo> itemsList, List<ScalesInfo> machineryInfoList, Boolean snapshot) {
        this.id = id;
        this.dateTimeCode = dateTimeCode;
        this.date = date;
        this.handlerModel = handlerModel;
        this.idGroupMachinery = idGroupMachinery;
        this.nppGroupMachinery = nppGroupMachinery;
        this.nameGroupMachinery = nameGroupMachinery;
        this.description = description;
        this.itemsList = itemsList;
        this.machineryInfoList = machineryInfoList;
        this.snapshot = snapshot;
    }

    @Override
    public List<MachineryInfo> sendTransaction(Object handler, List<ScalesInfo> machineryInfoList) throws IOException {
        return ((ScalesHandler)handler).sendTransaction(this, machineryInfoList);
    }
}
