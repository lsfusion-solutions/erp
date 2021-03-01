package equ.api.scales;

import equ.api.TransactionInfo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class TransactionScalesInfo extends TransactionInfo<ScalesInfo, ScalesItem> {
    public TransactionScalesInfo(Long id, String dateTimeCode, LocalDate date, String handlerModel, Long idGroupMachinery,
                                 Integer nppGroupMachinery, String nameGroupMachinery, String description,
                                 List<ScalesItem> itemsList, List<ScalesInfo> machineryInfoList, Boolean snapshot,
                                 LocalDateTime lastErrorDate, String info) {
        super(id, dateTimeCode, date, handlerModel, idGroupMachinery, nppGroupMachinery, nameGroupMachinery, description, null,
                itemsList, machineryInfoList, snapshot, lastErrorDate, info);
    }
}
