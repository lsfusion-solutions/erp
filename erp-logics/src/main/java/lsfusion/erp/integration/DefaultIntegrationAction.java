package lsfusion.erp.integration;

import com.google.common.base.Throwables;
import lsfusion.base.col.SetFact;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.action.session.DataSession;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.classes.data.file.CustomStaticFormatFileClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.admin.Settings;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.physics.dev.integration.service.*;
import org.apache.commons.lang3.time.DateUtils;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.SQLException;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;

public class DefaultIntegrationAction extends InternalAction {

    public DefaultIntegrationAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public DefaultIntegrationAction(ScriptingLogicsModule LM, ValueClass valueClass) {
        super(LM, valueClass);
    }

    public DefaultIntegrationAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    protected boolean allowNulls() {
        return true;
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
    }

    private static ExecutorService executor = Executors.newCachedThreadPool();

    static final Locale RU_LOCALE = new Locale("ru");
    static final DateFormatSymbols RU_SYMBOLS = new DateFormatSymbols(RU_LOCALE);
    static final String[] RU_MONTHS = {"января", "февраля", "марта", "апреля", "мая",
            "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"};

    static {
        RU_SYMBOLS.setMonths(RU_MONTHS);
    }

    protected static Date parseDate(String value) throws ParseException {
        return parseDate(value, null);    
    }
    
    protected static Date parseDate(String value, Date defaultValue) throws ParseException {
        if (value == null) return defaultValue;
        value = value.trim();
        if (value.isEmpty() || value.replace(".", "").trim().isEmpty()) return defaultValue;
        if (value.matches("\\d{8}")) {
            try {
                //чит для отличия ddMMyyyy от yyyyMMdd
                Integer intValue = Integer.parseInt(value.substring(4, 6));
                if(intValue > 12)
                    return new Date(DateUtils.parseDate(value, "ddMMyyyy").getTime());
            } catch(Exception e) {
                return defaultValue;
            }            
        } 
        if (value.contains("г")) {
            //чит для даты с месяцем прописью
            return new Date(new SimpleDateFormat("dd MMMM yyyy г.", RU_SYMBOLS).parse(value.toLowerCase()).getTime());
        }
        switch (value.length()) {
            case 4:
                return new Date(DateUtils.parseDate(value, "MMyy").getTime());
            case 5:
                return new Date(DateUtils.parseDate(value, "MM.yy", "MM/yy").getTime());
            case 6:
                return new Date(DateUtils.parseDate(value, "MM,yy_", "d.MM.yy").getTime());
            case 7:
                return new Date(DateUtils.parseDate(value, "MM.yyyy", "MM-yyyy").getTime());
            case 8:
                return new Date(DateUtils.parseDate(value, "yyyyMMdd", "dd.MM.yy", "dd/MM/yy", "dd-MM-yy").getTime());
            case 10:
                return new Date(DateUtils.parseDate(value, "dd.MM.yyyy", "dd/MM/yyyy", "yyyy-MM-dd", "dd-MM-yyyy").getTime());
            case 16:
                return new Date(DateUtils.parseDate(value, "dd.MM.yyyy HH:mm").getTime());
            case 19:
                return new Date(DateUtils.parseDate(value, "dd.MM.yyyy HH:mm:ss").getTime());
        }
        return new Date(DateUtils.parseDate(value, "MM,yy_", "d.MM.yy", "MM.yyyy", "MM-yyyy", "MMyy", "MM.yy", "MM/yy",
                "yyyyMMdd", "dd.MM.yy", "dd/MM/yy", "dd.MM.yyyy", "dd/MM/yyyy", "dd.MM.yyyy HH:mm", "dd.MM.yyyy HH:mm:ss").getTime());
    }

    protected BigDecimal parseBigDecimal(String value) {
        return parseBigDecimal(value, null);
    }

    protected BigDecimal parseBigDecimal(String value, BigDecimal defaultValue) {
        try {
            value = trim(value); //char 160 = nbsp
            return value == null || value.isEmpty() ? defaultValue : new BigDecimal(value.replace(" ", "").replace(String.valueOf((char) 160), "").replace(",", "."));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    protected BigDecimal safeAdd(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else return (operand1 == null ? operand2 : (operand2 == null ? operand1 : operand1.add(operand2)));
    }

    protected BigDecimal safeSubtract(BigDecimal operand1, BigDecimal operand2) {
        if (operand1 == null && operand2 == null)
            return null;
        else
            return (operand1 == null ? operand2.negate() : (operand2 == null ? operand1 : operand1.subtract((operand2))));
    }

    protected BigDecimal safeMultiply(BigDecimal operand1, int operand2) {
        return safeMultiply(operand1, BigDecimal.valueOf(operand2));
    }

    protected BigDecimal safeMultiply(BigDecimal operand1, BigDecimal operand2) {
        return safeMultiply(operand1, operand2, null);
    }

    protected BigDecimal safeMultiply(BigDecimal operand1, BigDecimal operand2, BigDecimal defaultValue) {
        if (operand1 == null || operand1.doubleValue() == 0 || operand2 == null || operand2.doubleValue() == 0)
            return defaultValue;
        else return operand1.multiply(operand2);
    }

    protected BigDecimal safeDivide(BigDecimal dividend, int quotient) {
        return safeDivide(dividend, BigDecimal.valueOf(quotient));
    }

    protected BigDecimal safeDivide(BigDecimal dividend, BigDecimal quotient) {
        return safeDivide(dividend, quotient, 3);
    }

    protected BigDecimal safeDivide(BigDecimal dividend, int quotient, int scale) {
        return safeDivide(dividend, BigDecimal.valueOf(quotient), scale);
    }
    
    protected BigDecimal safeDivide(BigDecimal dividend, BigDecimal quotient, int scale) {
        if (dividend == null || quotient == null || quotient.doubleValue() == 0)
            return null;
        return dividend.divide(quotient, scale, RoundingMode.HALF_UP);
    }

    protected BigDecimal safeNegate(BigDecimal operand) {
        return operand == null ? null : operand.negate();
    }

    protected String trim(String input) {
        return input == null ? null : input.trim();
    }

    protected String trim(String input, String defaultValue) {
        return input == null ? defaultValue : input.trim();
    }

    protected String trim(String input, Integer length) {
        return input == null ? null : (length == null || length >= input.trim().length() ? input.trim() : input.trim().substring(0, length));
    }

    protected String nullIfEmpty(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }
    
    protected boolean notNullNorEmpty(String value) {
        return value != null && !value.isEmpty();
    }
    
    protected boolean notNullNorEmpty(List value) {
        return value != null && !value.isEmpty();
    }

    protected void checkFileExistence(String filePath) {
        if(Settings.get().isSafeCheckFileExistence())
            safeCheckFileExistence(filePath);
        else
            unsafeCheckFileExistence(filePath);
    }

    private void unsafeCheckFileExistence(String filePath) {
        if (!(new File(filePath).exists()))
            throw new RuntimeException("Запрашиваемый файл " + filePath + " не найден");
    }

    private void safeCheckFileExistence(final String filePath) {
        try {
            final Future<Boolean> future = executor.submit((Callable) () -> new File(filePath).exists());

            boolean result = false;
            try {
                result = future.get(1000, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
            }

            if (!result)
                throw new RuntimeException("Запрашиваемый файл " + filePath + " не найден");
        } catch (InterruptedException | ExecutionException e) {
            throw Throwables.propagate(e);
        }
    }

    protected void safeFileDelete(File file) {
        if (file != null && !file.delete()) {
            file.deleteOnExit();
        }
    }

    public static java.sql.Time localTimeToSqlTime(LocalTime value) {
        return value != null ? java.sql.Time.valueOf(value) : null;
    }

    public static LocalTime sqlTimeToLocalTime(java.sql.Time value) {
        return value != null ? value.toLocalTime() : null;
    }

    public static java.sql.Date localDateToSqlDate(LocalDate value) {
        return value != null ? java.sql.Date.valueOf(value) : null;
    }

    public static LocalDate sqlDateToLocalDate(java.sql.Date value) {
        return value != null ? value.toLocalDate() : null;
    }

    public static java.sql.Timestamp localDateTimeToSqlTimestamp(LocalDateTime value) {
        return value != null ? java.sql.Timestamp.valueOf(value) : null;
    }

    public static LocalDateTime sqlTimestampToLocalDateTime(java.sql.Timestamp value) {
        return value != null ? value.toLocalDateTime() : null;
    }

    public void integrationServiceSynchronize(ExecutionContext context, List<ImportField> fields, List<List<Object>> data, Collection<? extends ImportKey<?>> keys,
                                              Collection<ImportProperty<?>> properties) throws SQLException, SQLHandledException {
        new IntegrationService(context, new ImportTable(fields, data), keys, properties).synchronize(true, false);
    }

    public void integrationServiceSynchronize(DataSession session, List<ImportField> fields, List<List<Object>> data, Collection<? extends ImportKey<?>> keys,
                                              Collection<ImportProperty<?>> properties) throws SQLException, SQLHandledException {
        new IntegrationService(session, new ImportTable(fields, data), keys, properties).synchronize(true, false);
    }

    public ObjectValue requestUserData(ExecutionContext context, String description, String extensions) {
        CustomStaticFormatFileClass valueClass = CustomStaticFormatFileClass.get(false, false, description, SetFact.toExclSet(extensions.split(" ")));
        return context.requestUserData(valueClass, null);
    }

}