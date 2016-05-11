package equ.api.scales;

import equ.api.TransactionInfo;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;

public class TransactionScalesInfo extends TransactionInfo<ScalesInfo, ScalesItemInfo> {
    public TransactionScalesInfo(Integer id, String dateTimeCode, Date date, String handlerModel, Integer idGroupMachinery,
                                 Integer nppGroupMachinery, String nameGroupMachinery, String description,
                                 List<ScalesItemInfo> itemsList, List<ScalesInfo> machineryInfoList, Boolean snapshot,
                                 Timestamp lastErrorDate, String denominationStage) {
        super(id, dateTimeCode, date, handlerModel, idGroupMachinery, nppGroupMachinery, nameGroupMachinery, description, null,
                itemsList, machineryInfoList, snapshot, lastErrorDate, denominationStage);
    }
}
