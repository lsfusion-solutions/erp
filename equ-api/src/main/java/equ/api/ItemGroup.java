package equ.api;

import java.io.Serializable;

public class ItemGroup implements Serializable {
    public String idItemGroup;
    public String nameItemGroup;
    public String idParentItemGroup;

    public ItemGroup(String idItemGroup, String nameItemGroup) {
        this(idItemGroup, nameItemGroup, null);
    }

    public ItemGroup(String idItemGroup, String nameItemGroup, String idParentItemGroup) {
        this.idItemGroup = idItemGroup;
        this.nameItemGroup = nameItemGroup;
        this.idParentItemGroup = idParentItemGroup;
    }
}
