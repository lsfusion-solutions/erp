package lsfusion.erp.region.by.machinery.board.fiscalboard;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingActionProperty;
import lsfusion.server.language.ScriptingLogicsModule;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.DecimalFormat;

public abstract class FiscalBoardActionProperty extends ScriptingActionProperty {
    protected int lineLength = 20;

    public FiscalBoardActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
    }

    protected String fillSpaces(String line, int length) {
        return fillSpaces(line, length, false);
    }

    protected String fillSpaces(String line, int length, boolean append) {
        if (line.length() > length)
            line = line.substring(0, length);
        StringBuilder lineBuilder = new StringBuilder(line);
        while (lineBuilder.length() < length)
            if (append)
                lineBuilder.append(" ");
            else
                lineBuilder.insert(0, " ");
        line = lineBuilder.toString();
        return line;
    }

    protected String toStr(BigDecimal value) {
        String result = null;
        if (value != null) {
            value = value.setScale(2, BigDecimal.ROUND_HALF_UP);
            DecimalFormat df = new DecimalFormat();
            df.setMaximumFractionDigits(2);
            df.setMinimumFractionDigits(2);
            df.setGroupingUsed(false);
            result = df.format(value).replace(",", ".");
        }
        return result == null ? "0.00" : result;
    }
}
