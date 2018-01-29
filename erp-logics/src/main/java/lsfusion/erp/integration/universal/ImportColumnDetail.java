package lsfusion.erp.integration.universal;

public class ImportColumnDetail {

    String field;
    private String fullIndex;
    String[] indexes;
    boolean replaceOnlyNull;
    public String propertyCanonicalName;
    public String key;
    public boolean isBoolean;

    public ImportColumnDetail(String field, String singleIndex, boolean replaceOnlyNull) {
        this(field, singleIndex, replaceOnlyNull, false);
    }

    public ImportColumnDetail(String field, String singleIndex, boolean replaceOnlyNull, boolean isBoolean) {
        this(field, null, new String[] {singleIndex}, replaceOnlyNull, isBoolean);
    }
    
    public ImportColumnDetail(String field, String[] indexes, boolean replaceOnlyNull) {
        this(field, null, indexes, replaceOnlyNull, false);
    }
    
    public ImportColumnDetail(String field, String fullIndex, String[] indexes, boolean replaceOnlyNull, boolean isBoolean) {
        this(field, fullIndex, indexes, replaceOnlyNull, null, null, isBoolean);
    }
    
    public ImportColumnDetail(String field, String fullIndex, String[] indexes, boolean replaceOnlyNull, 
                              String propertyCanonicalName, String key, boolean isBoolean) {
        this.field = field;
        this.fullIndex = fullIndex;
        this.indexes = indexes;
        this.replaceOnlyNull = replaceOnlyNull;
        this.propertyCanonicalName = propertyCanonicalName;
        this.key = key;
        this.isBoolean = isBoolean;
    }
    
    public ImportColumnDetail clone(String fieldAndIndex) {
        return new ImportColumnDetail(fieldAndIndex, fieldAndIndex, replaceOnlyNull, isBoolean);
    }

    public String getFullIndex() {
        return fullIndex;
    }
}
