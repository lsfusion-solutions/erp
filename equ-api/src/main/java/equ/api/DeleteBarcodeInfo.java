package equ.api;

import equ.api.cashregister.CashRegisterItemInfo;

import java.io.Serializable;
import java.util.List;

public class DeleteBarcodeInfo implements Serializable {
    public List<CashRegisterItemInfo> barcodeList;
    public Integer nppGroupMachinery;
    public Integer overDepartNumberGroupMachinery;
    public String handlerModelGroupMachinery;
    public String directoryGroupMachinery;

    public DeleteBarcodeInfo(List<CashRegisterItemInfo> barcodeList, Integer nppGroupMachinery, Integer overDepartNumberGroupMachinery,
                             String handlerModelGroupMachinery, String directoryGroupMachinery) {
        this.barcodeList = barcodeList;
        this.nppGroupMachinery = nppGroupMachinery;
        this.overDepartNumberGroupMachinery = overDepartNumberGroupMachinery;
        this.handlerModelGroupMachinery = handlerModelGroupMachinery;
        this.directoryGroupMachinery = directoryGroupMachinery;
    }
}