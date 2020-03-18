package equ.api.cashregister;

import equ.api.ItemGroup;
import equ.api.TransactionInfo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class TransactionCashRegisterInfo extends TransactionInfo<CashRegisterInfo, CashRegisterItemInfo> {
    public Integer departmentNumberGroupCashRegister;
    public String idDepartmentStoreGroupCashRegister;
    public String weightCodeGroupCashRegister;
    public String nameStockGroupCashRegister;
    
    public TransactionCashRegisterInfo(Long id, String dateTimeCode, LocalDate date, String handlerModel, Long idGroupMachinery,
                                       Integer nppGroupMachinery, String nameGroupMachinery, String description,
                                       Map<String, List<ItemGroup>> itemGroupMap, List<CashRegisterItemInfo> itemsList,
                                       List<CashRegisterInfo> machineryInfoList, Boolean snapshot, LocalDateTime lastErrorDate,
                                       Integer departmentNumberGroupCashRegister, String idDepartmentStoreGroupCashRegister,
                                       String weightCodeGroupCashRegister, String nameStockGroupCashRegister, String info) {
        super(id, dateTimeCode, date, handlerModel, idGroupMachinery, nppGroupMachinery, nameGroupMachinery, description,
                itemGroupMap, itemsList, machineryInfoList, snapshot, lastErrorDate, info);
        this.departmentNumberGroupCashRegister = departmentNumberGroupCashRegister;
        this.idDepartmentStoreGroupCashRegister = idDepartmentStoreGroupCashRegister;
        this.weightCodeGroupCashRegister = weightCodeGroupCashRegister;
        this.nameStockGroupCashRegister = nameStockGroupCashRegister;
    }
}
