package equ.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class TransactionPriceCheckerInfo extends TransactionInfo<PriceCheckerInfo, PriceCheckerItem> {

    public TransactionPriceCheckerInfo(Long id, String dateTimeCode, LocalDate date, String handlerModel, Long idGroupMachinery, Integer nppGroupMachinery,
                                       String nameGroupMachinery, String description, List<PriceCheckerItem> itemsList,
                                       List<PriceCheckerInfo> machineryInfoList, Boolean snapshot, LocalDateTime lastErrorDate, String info) {
        super(id, dateTimeCode, date, handlerModel, idGroupMachinery, nppGroupMachinery, nameGroupMachinery, description,
                null, itemsList, machineryInfoList, snapshot, lastErrorDate, info);
    }
}
