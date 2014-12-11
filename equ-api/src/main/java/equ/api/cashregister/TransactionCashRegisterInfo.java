package equ.api.cashregister;

import equ.api.ItemGroup;
import equ.api.MachineryInfo;
import equ.api.TransactionInfo;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class TransactionCashRegisterInfo extends TransactionInfo<CashRegisterInfo, CashRegisterItemInfo> {
    public Integer departmentNumberGroupCashRegister;
    
    public TransactionCashRegisterInfo(Integer id, String dateTimeCode, Date date, String handlerModel, Integer idGroupMachinery,
                                       Integer nppGroupMachinery, String nameGroupMachinery, String description, 
                                       Map<String, List<ItemGroup>> itemGroupMap, List<CashRegisterItemInfo> itemsList, 
                                       List<CashRegisterInfo> machineryInfoList, Boolean snapshot, Integer departmentNumberGroupCashRegister) {
        this.id = id;
        this.dateTimeCode = dateTimeCode;
        this.date = date;
        this.handlerModel = handlerModel;
        this.idGroupMachinery = idGroupMachinery;
        this.nppGroupMachinery = nppGroupMachinery;
        this.nameGroupMachinery = nameGroupMachinery;
        this.description = description;
        this.itemGroupMap = itemGroupMap;
        this.itemsList = itemsList;
        this.machineryInfoList = machineryInfoList;
        this.snapshot = snapshot;
        this.departmentNumberGroupCashRegister = departmentNumberGroupCashRegister;
    }

    @Override
    public List<MachineryInfo> sendTransaction(Object handler, List<CashRegisterInfo> machineryInfoList) throws IOException {
        return ((CashRegisterHandler)handler).sendTransaction(this, machineryInfoList);
    }
}
