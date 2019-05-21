package equ.api;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;

public class TransactionPriceCheckerInfo extends TransactionInfo<PriceCheckerInfo, PriceCheckerItemInfo> {

    public TransactionPriceCheckerInfo(Long id, String dateTimeCode, Date date, String handlerModel, Long idGroupMachinery, Integer nppGroupMachinery,
                                       String nameGroupMachinery, String description, List<PriceCheckerItemInfo> itemsList,
                                       List<PriceCheckerInfo> machineryInfoList, Boolean snapshot, Timestamp lastErrorDate, String info) {
        super(id, dateTimeCode, date, handlerModel, idGroupMachinery, nppGroupMachinery, nameGroupMachinery, description,
                null, itemsList, machineryInfoList, snapshot, lastErrorDate, info);
    }
}
