package lsfusion.erp.integration.universal;

public class UniversalImportException extends Exception {

    public UniversalImportException(ImportColumnDetail importColumnDetail, int row, Throwable cause) {
        this(importColumnDetail == null ? null : importColumnDetail.field, importColumnDetail == null ? null : importColumnDetail.getFullIndex(), row, cause);
    }

    public UniversalImportException(String field, String column, int row, Throwable cause) {
        super(String.format("Ошибка чтения поля %s. Колонка: %s", field, column)
                + (column != null && column.matches(":(\\d+)_(\\d+)") ? "" : String.format(", ряд: %s", (row + 1))), cause);
    }

    public String getTitle() {
        return "Ошибка универсального импорта";
    }
}
