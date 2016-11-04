package equ.api;

import java.io.Serializable;
import java.util.List;

public class DeleteBarcodeInfo implements Serializable {
    public List<String> barcodeList;
    public Integer nppGroupMachinery;
    public String handlerModelGroupMachinery;

    public DeleteBarcodeInfo(List<String> barcodeList, Integer nppGroupMachinery, String handlerModelGroupMachinery) {
        this.barcodeList = barcodeList;
        this.nppGroupMachinery = nppGroupMachinery;
        this.handlerModelGroupMachinery = handlerModelGroupMachinery;
    }
}