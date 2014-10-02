package equ.api.cashregister;

import equ.api.ItemGroup;
import equ.api.MachineryInfo;
import equ.api.TransactionInfo;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class TransactionCashRegisterInfo extends TransactionInfo<CashRegisterInfo, CashRegisterItemInfo> {
    public boolean snapshot;
    public Integer nppGroupCashRegister;
    public String nameGroupCashRegister;
    public List<DiscountCard> discountCardList;
    
    public TransactionCashRegisterInfo(Integer id, String dateTimeCode, Date date, Map<String, List<ItemGroup>> itemGroupMap,
                                       List<CashRegisterItemInfo> itemsList, List<CashRegisterInfo> machineryInfoList, 
                                       boolean snapshot, Integer nppGroupCashRegister, String nameGroupCashRegister, 
                                       List<DiscountCard> discountCardList) {
        this.id = id;
        this.dateTimeCode = dateTimeCode;
        this.date = date;
        this.itemGroupMap = itemGroupMap;
        this.itemsList = itemsList;
        this.machineryInfoList = machineryInfoList;
        this.snapshot = snapshot;
        this.nppGroupCashRegister = nppGroupCashRegister;
        this.nameGroupCashRegister = nameGroupCashRegister;
        this.discountCardList = discountCardList;
    }

    @Override
    public List<MachineryInfo> sendTransaction(Object handler, List<CashRegisterInfo> machineryInfoList) throws IOException {
        return ((CashRegisterHandler)handler).sendTransaction(this, machineryInfoList);
    }
}
