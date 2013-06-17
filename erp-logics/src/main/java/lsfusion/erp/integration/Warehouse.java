package lsfusion.erp.integration;


public class Warehouse {
    public String idLegalEntity;
    public String idWarehouseGroup;
    public String idWarehouse;
    public String nameWarehouse;
    public String addressWarehouse;


    public Warehouse(String idLegalEntity, String idWarehouseGroup, String idWarehouse, String nameWarehouse, String addressWarehouse) {
        this.idLegalEntity = idLegalEntity;
        this.idWarehouseGroup = idWarehouseGroup;
        this.idWarehouse = idWarehouse;
        this.nameWarehouse = nameWarehouse;
        this.addressWarehouse = addressWarehouse;
    }
}
