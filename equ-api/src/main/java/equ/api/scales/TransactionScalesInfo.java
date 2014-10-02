package equ.api.scales;

import equ.api.MachineryInfo;
import equ.api.TransactionInfo;

import java.io.IOException;
import java.util.List;

public class TransactionScalesInfo extends TransactionInfo<ScalesInfo, ScalesItemInfo> {
    public boolean snapshot;
    public TransactionScalesInfo(Integer id, String dateTimeCode, List<ScalesItemInfo> itemsList,
                                 List<ScalesInfo> machineryInfoList, boolean snapshot) {
        this.id = id;
        this.dateTimeCode = dateTimeCode;
        this.itemsList = itemsList;
        this.machineryInfoList = machineryInfoList;
        this.snapshot = snapshot;
    }

    @Override
    public List<MachineryInfo> sendTransaction(Object handler, List<ScalesInfo> machineryInfoList) throws IOException {
        return ((ScalesHandler)handler).sendTransaction(this, machineryInfoList);
    }
}
