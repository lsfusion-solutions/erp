package equ.api;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;

public class TransactionPriceCheckerInfo extends TransactionInfo<PriceCheckerInfo, PriceCheckerItemInfo> {

    public TransactionPriceCheckerInfo(Integer id, String dateTimeCode, Date date, String handlerModel, Integer idGroupMachinery, Integer nppGroupMachinery,
                                       String nameGroupMachinery, String description, List<PriceCheckerItemInfo> itemsList,
                                       List<PriceCheckerInfo> machineryInfoList, Boolean snapshot, Timestamp lastErrorDate) {
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
