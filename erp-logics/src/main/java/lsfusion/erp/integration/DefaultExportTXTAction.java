package lsfusion.erp.integration;

import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.language.ScriptingLogicsModule;

import java.io.UnsupportedEncodingException;
import java.sql.SQLException;

public class DefaultExportTXTAction extends DefaultExportAction {

    protected final int LEFT = 0;
    protected final int RIGHT = 1;
    protected final int CENTER = 2;
    
    public DefaultExportTXTAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    public DefaultExportTXTAction(ScriptingLogicsModule LM, ValueClass valueClass) {
        super(LM, valueClass);
    }

    public DefaultExportTXTAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
    }

    protected String fillSymbolsLine(char c, int length) {
        return fillSymbolsLine("", c, length);
    }

    protected static String fillSymbolsLine(String postfix, char c, int length) {
        postfix = postfix == null ? "" : postfix;
        while (postfix.length() < length)
            postfix = c + postfix;
        return postfix;
    }
    
    protected String formatString(String input, String encoding, int length, int position) throws UnsupportedEncodingException {
        return formatString(input, encoding, length, position, false);
    }

    protected String formatString(String input, String encoding, int length, int position, boolean newLine) throws UnsupportedEncodingException {
        input = input == null ? "" : input.substring(0, Math.min(input.length(), length));
        while (input.length() < length) {
            switch (position) {
                case LEFT:
                    input = input + " ";
                    break;
                case RIGHT:
                    input = " " + input;
                    break;
                case CENTER:
                    input = " " + input;
                    if (input.length() < length)
                        input = input + " ";
                    break;
            }
        }
        return new String((input + (newLine ? "\n" : "")).getBytes(encoding), encoding);
    }
}