package equ.api.cashregister;

import equ.api.ItemGroup;
import equ.api.TransactionInfo;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class TransactionCashRegisterInfo extends TransactionInfo<CashRegisterInfo, CashRegisterItemInfo> {
    public Integer departmentNumberGroupCashRegister;
    public String weightCodeGroupCashRegister;
    public String nameStockGroupCashRegister;
    
    public TransactionCashRegisterInfo(Integer id, String dateTimeCode, Date date, String handlerModel, Integer idGroupMachinery,
                                       Integer nppGroupMachinery, String nameGroupMachinery, String description,
                                       Map<String, List<ItemGroup>> itemGroupMap, List<CashRegisterItemInfo> itemsList, 
                                       List<CashRegisterInfo> machineryInfoList, Boolean snapshot, Timestamp lastErrorDate,
                                       Integer departmentNumberGroupCashRegister,
                                       String weightCodeGroupCashRegister, String nameStockGroupCashRegister) {
        super(id, dateTimeCode, date, handlerModel, idGroupMachinery, nppGroupMachinery, nameGroupMachinery, description,
                itemGroupMap, itemsList, machineryInfoList, snapshot, lastErrorDate);
        this.departmentNumberGroupCashRegister = departmentNumberGroupCashRegister;
        this.weightCodeGroupCashRegister = weightCodeGroupCashRegister;
        this.nameStockGroupCashRegister = nameStockGroupCashRegister;
    }
}
