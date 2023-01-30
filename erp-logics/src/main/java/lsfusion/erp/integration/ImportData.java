package lsfusion.erp.integration;

import java.util.List;

public class ImportData {
    private List<Item> itemsList;
    private List<UOM> uomsList;
    private List<ItemGroup> itemGroupsList;
    private List<ItemGroup> parentGroupsList;
    private List<Bank> banksList;
    private List<LegalEntity> legalEntitiesList;
    private List<WarehouseGroup> warehouseGroupsList;
    private List<Warehouse> warehousesList;
    private List<LegalEntity> storesList;
    private List<DepartmentStore> departmentStoresList;
    private List<Contract> contractsList;
    private List<UserInvoiceDetail> userInvoicesList;
    private boolean skipKeys;
    
    public ImportData() {
    }

    public List<Item> getItemsList() {
        return itemsList;
    }

    public void setItemsList(List<Item> itemsList) {
        this.itemsList = itemsList;
    }

    public List<UOM> getUOMsList() {
        return uomsList;
    }

    public void setUOMsList(List<UOM> uomsList) {
        this.uomsList = uomsList;
    }

    public List<ItemGroup> getItemGroupsList() {
        return itemGroupsList;
    }

    public void setItemGroupsList(List<ItemGroup> itemGroupsList) {
        this.itemGroupsList = itemGroupsList;
    }

    public List<ItemGroup> getParentGroupsList() {
        return parentGroupsList;
    }

    public void setParentGroupsList(List<ItemGroup> parentGroupsList) {
        this.parentGroupsList = parentGroupsList;
    }

    public List<Bank> getBanksList() {
        return banksList;
    }

    public void setBanksList(List<Bank> banksList) {
        this.banksList = banksList;
    }

    public List<LegalEntity> getLegalEntitiesList() {
        return legalEntitiesList;
    }

    public void setLegalEntitiesList(List<LegalEntity> legalEntitiesList) {
        this.legalEntitiesList = legalEntitiesList;
    }

    public List<WarehouseGroup> getWarehouseGroupsList() {
        return warehouseGroupsList;
    }

    public void setWarehouseGroupsList(List<WarehouseGroup> warehouseGroupsList) {
        this.warehouseGroupsList = warehouseGroupsList;
    }

    public List<Warehouse> getWarehousesList() {
        return warehousesList;
    }

    public void setWarehousesList(List<Warehouse> warehousesList) {
        this.warehousesList = warehousesList;
    }

    public List<LegalEntity> getStoresList() {
        return storesList;
    }

    public void setStoresList(List<LegalEntity> storesList) {
        this.storesList = storesList;
    }

    public List<DepartmentStore> getDepartmentStoresList() {
        return departmentStoresList;
    }

    public void setDepartmentStoresList(List<DepartmentStore> departmentStoresList) {
        this.departmentStoresList = departmentStoresList;
    }

    public List<Contract> getContractsList() {
        return contractsList;
    }

    public void setContractsList(List<Contract> contractsList) {
        this.contractsList = contractsList;
    }

    public List<UserInvoiceDetail> getUserInvoicesList() {
        return userInvoicesList;
    }

    public void setUserInvoicesList(List<UserInvoiceDetail> userInvoicesList) {
        this.userInvoicesList = userInvoicesList;
    }

    public boolean getSkipKeys() {
        return skipKeys;
    }

    public void setSkipKeys(boolean skipKeys) {
        this.skipKeys = skipKeys;
    }
}
