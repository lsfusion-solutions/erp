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
    public String idGroupCashRegister;
    public Integer nppGroupCashRegister;
    public Integer departmentNumberGroupCashRegister;
    public String nameGroupCashRegister;
    
    public TransactionCashRegisterInfo(Integer id, String dateTimeCode, Date date, String handlerModel, Map<String, List<ItemGroup>> itemGroupMap,
                                       List<CashRegisterItemInfo> itemsList, List<CashRegisterInfo> machineryInfoList, boolean snapshot, 
                                       String idGroupCashRegister, Integer nppGroupCashRegister, Integer departmentNumberGroupCashRegister, 
                                       String nameGroupCashRegister) {
        this.id = id;
        this.dateTimeCode = dateTimeCode;
        this.date = date;
        this.handlerModel = handlerModel;
        this.itemGroupMap = itemGroupMap;
        this.itemsList = itemsList;
        this.machineryInfoList = machineryInfoList;
        this.snapshot = snapshot;
        this.idGroupCashRegister = idGroupCashRegister;
        this.nppGroupCashRegister = nppGroupCashRegister;
        this.departmentNumberGroupCashRegister = departmentNumberGroupCashRegister;
        this.nameGroupCashRegister = nameGroupCashRegister;
    }

    @Override
    public List<MachineryInfo> sendTransaction(Object handler, List<CashRegisterInfo> machineryInfoList) throws IOException {
        return ((CashRegisterHandler)handler).sendTransaction(this, machineryInfoList);
    }
}
