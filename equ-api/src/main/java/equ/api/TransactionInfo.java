package equ.api;

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

public abstract class TransactionInfo <M extends MachineryInfo, I extends ItemInfo> implements Serializable {
    public Integer id;
    public String dateTimeCode;
    public Date date;
    public Map<String, List<ItemGroup>> itemGroupMap;
    public List<I> itemsList;
    public List<M> machineryInfoList;

    public abstract List<MachineryInfo> sendTransaction(Object handler, List<M> machineryInfoList) throws IOException;
}
