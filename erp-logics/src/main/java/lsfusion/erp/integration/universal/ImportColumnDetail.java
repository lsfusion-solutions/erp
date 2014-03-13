package lsfusion.erp.integration.universal;

public class ImportColumnDetail {

    String field;
    private String fullIndex;
    String[] indexes;
    boolean replaceOnlyNull;
    String moduleName;
    String property;
    String key;

    public ImportColumnDetail(String field, String singleIndex, boolean replaceOnlyNull) {
        this(field, null, new String[] {singleIndex}, replaceOnlyNull);
    }
    
    public ImportColumnDetail(String field, String[] indexes, boolean replaceOnlyNull) {
        this(field, null, indexes, replaceOnlyNull);
    }
    
    public ImportColumnDetail(String field, String fullIndex, String[] indexes, boolean replaceOnlyNull) {
        this(field, fullIndex, indexes, replaceOnlyNull, null, null, null);
    }
    
    public ImportColumnDetail(String field, String fullIndex, String[] indexes, boolean replaceOnlyNull, 
                              String moduleName, String property, String key) {
        this.field = field;
        this.fullIndex = fullIndex;
        this.indexes = indexes;
        this.replaceOnlyNull = replaceOnlyNull;
        this.moduleName = moduleName;
        this.property = property;
        this.key = key;
    }
    
    public ImportColumnDetail clone(String field, String singleIndex) {
        return new ImportColumnDetail(field, singleIndex, replaceOnlyNull);
    }

    public String getFullIndex() {
        return fullIndex;
    }
}
