package equ.api;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

public abstract class TransactionInfo <M extends MachineryInfo, I extends ItemInfo> implements Serializable {
    public Integer id;
    public String dateTimeCode;
    public Date date;
    public String handlerModel;
    public Integer idGroupMachinery;
    public Integer nppGroupMachinery;
    public String nameGroupMachinery;
    public String idStock;
    public String description;
    public Map<String, List<ItemGroup>> itemGroupMap;
    public List<I> itemsList;
    public List<M> machineryInfoList;
    public Boolean snapshot;

    public Integer getUniqueId() {
        String result = String.valueOf(id) + idGroupMachinery;
        for(M machineryInfo : machineryInfoList)
            result += machineryInfo.number;
        for(I item : itemsList)
            result += item.idBarcode;
        return result.hashCode();
    }
}
