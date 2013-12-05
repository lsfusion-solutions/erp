package lsfusion.erp.integration.universal;

public class ImportColumnDetail {
    
    String[] indexes;
    boolean replaceOnlyNull;
    
    public ImportColumnDetail(String[] indexes, boolean replaceOnlyNull) {
        this.indexes = indexes;
        this.replaceOnlyNull = replaceOnlyNull;
    }
}
