package equ.api;

import java.io.Serializable;

public class ItemGroup implements Serializable {
    public String idItemGroup;
    public String extIdItemGroup;
    public String nameItemGroup;
    public String idParentItemGroup;

    public ItemGroup(String idItemGroup, String extIdItemGroup, String nameItemGroup, String idParentItemGroup) {
        this.idItemGroup = idItemGroup;
        this.extIdItemGroup = extIdItemGroup;
        this.nameItemGroup = nameItemGroup;
        this.idParentItemGroup = idParentItemGroup;
    }
}
