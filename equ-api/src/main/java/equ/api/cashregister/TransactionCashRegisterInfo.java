package equ.api.cashregister;

import equ.api.TransactionInfo;

import java.io.IOException;
import java.util.Date;
import java.util.List;

public class TransactionCashRegisterInfo extends TransactionInfo<CashRegisterInfo, CashRegisterItemInfo> {
    public boolean snapshot;
    public Integer nppGroupCashRegister;
    public String nameGroupCashRegister;
    
    public TransactionCashRegisterInfo(Integer id, String dateTimeCode, Date date, List<CashRegisterItemInfo> itemsList,
                                       List<CashRegisterInfo> machineryInfoList, boolean snapshot, Integer nppGroupCashRegister,
                                       String nameGroupCashRegister) {
        this.id = id;
        this.dateTimeCode = dateTimeCode;
        this.date = date;
        this.itemsList = itemsList;
        this.machineryInfoList = machineryInfoList;
        this.snapshot = snapshot;
        this.nppGroupCashRegister = nppGroupCashRegister;
        this.nameGroupCashRegister = nameGroupCashRegister;
    }

    @Override
    public void sendTransaction(Object handler, List<CashRegisterInfo> machineryInfoList) throws IOException {
        ((CashRegisterHandler)handler).sendTransaction(this, machineryInfoList);
    }
}
