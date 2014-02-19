package lsfusion.erp.integration.universal;

public class ImportColumnDetail {

    String field;
    private String fullIndex;
    String[] indexes;
    boolean replaceOnlyNull;

    public ImportColumnDetail(String field, String singleIndex, boolean replaceOnlyNull) {
        this(field, null, new String[] {singleIndex}, replaceOnlyNull);
    }
    
    public ImportColumnDetail(String field, String[] indexes, boolean replaceOnlyNull) {
        this(field, null, indexes, replaceOnlyNull);
    }
    
    public ImportColumnDetail(String field, String fullIndex, String[] indexes, boolean replaceOnlyNull) {
        this.field = field;
        this.fullIndex = fullIndex;
        this.indexes = indexes;
        this.replaceOnlyNull = replaceOnlyNull;
    }
    
    public ImportColumnDetail modify(String field, String singleIndex) {
        this.field = field;
        this.indexes =  new String[] {singleIndex};
        return this;
    }

    public String getFullIndex() {
        return fullIndex;
    }
}
