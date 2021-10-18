package lsfusion.erp.region.by.machinery.board.fiscalboard;

import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.language.ScriptingLogicsModule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

public abstract class FiscalBoardAction extends InternalAction {
    protected int lineLength = 20;

    public FiscalBoardAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLHandledException {
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
            value = value.setScale(2, RoundingMode.HALF_UP);
            DecimalFormat df = new DecimalFormat();
            df.setMaximumFractionDigits(2);
            df.setMinimumFractionDigits(2);
            df.setGroupingUsed(false);
            result = df.format(value).replace(",", ".");
        }
        return result == null ? "0.00" : result;
    }
}
