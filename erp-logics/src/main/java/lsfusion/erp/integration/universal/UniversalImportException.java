package lsfusion.erp.integration.universal;

public class UniversalImportException extends Exception {

    public UniversalImportException(String message) {
        super(message);
    }

    public UniversalImportException(String field, String column, int row, Throwable cause) {
        super(String.format("Ошибка чтения поля %s. Колонка: %s, ряд: %s.", field, column, (row+1)), cause);
    }

    public UniversalImportException(Throwable cause) {
        super(cause);
    }
    
    public String getTitle() {
        return "Ошибка универсального импорта";
    }
}
