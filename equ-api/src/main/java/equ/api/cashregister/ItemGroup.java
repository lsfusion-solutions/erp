package equ.api.cashregister;

import java.io.Serializable;

public class ItemGroup implements Serializable {
    public ItemGroup(String idItemGroup, String nameItemGroup) {
        this.idItemGroup = idItemGroup;
        this.nameItemGroup = nameItemGroup;
    }

    public String idItemGroup;
    public String nameItemGroup;
}
