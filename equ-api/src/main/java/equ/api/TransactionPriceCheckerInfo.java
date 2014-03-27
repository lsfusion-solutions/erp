package equ.api;

import java.io.IOException;
import java.util.List;

public class TransactionPriceCheckerInfo extends TransactionInfo<PriceCheckerInfo, PriceCheckerItemInfo> {

    public TransactionPriceCheckerInfo(Integer id, String dateTimeCode, List<PriceCheckerItemInfo> itemsList,
                                       List<PriceCheckerInfo> machineryInfoList) {
        this.id = id;
        this.dateTimeCode = dateTimeCode;
        this.itemsList = itemsList;
        this.machineryInfoList = machineryInfoList;
    }

    @Override
    public void sendTransaction(Object handler, List<PriceCheckerInfo> machineryInfoList) throws IOException {
        ((PriceCheckerHandler)handler).sendTransaction(this, machineryInfoList);
    }
}
