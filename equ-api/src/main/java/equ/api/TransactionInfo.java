package equ.api;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Map;

public abstract class TransactionInfo <M extends MachineryInfo, I extends ItemInfo> implements Serializable {
    public Long id;
    public String dateTimeCode;
    public Date date;
    public String handlerModel;
    public Long idGroupMachinery;
    public Integer nppGroupMachinery;
    public String nameGroupMachinery;
    public String description;
    public Map<String, List<ItemGroup>> itemGroupMap;
    public List<I> itemsList;
    public List<M> machineryInfoList;
    public Boolean snapshot;
    public Timestamp lastErrorDate;
    public String info;

    public TransactionInfo() {
    }

    public TransactionInfo(Long id, String dateTimeCode, Date date, String handlerModel, Long idGroupMachinery,
                           Integer nppGroupMachinery, String nameGroupMachinery, String description,
                           Map<String, List<ItemGroup>> itemGroupMap, List<I> itemsList, List<M> machineryInfoList,
                           Boolean snapshot, Timestamp lastErrorDate, String info) {
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
        this.lastErrorDate = lastErrorDate;
        this.info = info;
    }
}
