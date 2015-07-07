package equ.api.scales;

import equ.api.TransactionInfo;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;

public class TransactionScalesInfo extends TransactionInfo<ScalesInfo, ScalesItemInfo> {
    public TransactionScalesInfo(Integer id, String dateTimeCode, Date date, String handlerModel, Integer idGroupMachinery,
                                 Integer nppGroupMachinery, String nameGroupMachinery, String description,
                                 List<ScalesItemInfo> itemsList, List<ScalesInfo> machineryInfoList, Boolean snapshot, Timestamp lastErrorDate) {
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
        this.lastErrorDate = lastErrorDate;
    }
}
