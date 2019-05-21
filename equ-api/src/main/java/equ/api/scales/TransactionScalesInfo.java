package equ.api.scales;

import equ.api.TransactionInfo;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;

public class TransactionScalesInfo extends TransactionInfo<ScalesInfo, ScalesItemInfo> {
    public TransactionScalesInfo(Long id, String dateTimeCode, Date date, String handlerModel, Long idGroupMachinery,
                                 Integer nppGroupMachinery, String nameGroupMachinery, String description,
                                 List<ScalesItemInfo> itemsList, List<ScalesInfo> machineryInfoList, Boolean snapshot,
                                 Timestamp lastErrorDate, String info) {
        super(id, dateTimeCode, date, handlerModel, idGroupMachinery, nppGroupMachinery, nameGroupMachinery, description, null,
                itemsList, machineryInfoList, snapshot, lastErrorDate, info);
    }
}
