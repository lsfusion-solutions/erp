package equ.api;

import equ.api.cashregister.CashRegisterItem;

import java.io.Serializable;
import java.util.List;

public class DeleteBarcodeInfo implements Serializable {
    public List<CashRegisterItem> barcodeList;
    public Integer nppGroupMachinery;
    public Integer overDepartNumberGroupMachinery;
    public String handlerModelGroupMachinery;
    public String directoryGroupMachinery;

    public DeleteBarcodeInfo(List<CashRegisterItem> barcodeList, Integer nppGroupMachinery, Integer overDepartNumberGroupMachinery,
                             String handlerModelGroupMachinery, String directoryGroupMachinery) {
        this.barcodeList = barcodeList;
        this.nppGroupMachinery = nppGroupMachinery;
        this.overDepartNumberGroupMachinery = overDepartNumberGroupMachinery;
        this.handlerModelGroupMachinery = handlerModelGroupMachinery;
        this.directoryGroupMachinery = directoryGroupMachinery;
    }
}