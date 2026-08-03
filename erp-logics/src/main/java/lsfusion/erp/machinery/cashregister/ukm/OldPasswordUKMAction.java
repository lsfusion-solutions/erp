package lsfusion.erp.machinery.cashregister.ukm;

import com.google.common.base.Throwables;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

//хеш пароля в формате MySQL до версии 4.1 (функция OLD_PASSWORD, удалена в MySQL 5.7.5)
public class OldPasswordUKMAction extends InternalAction {

    private static final long MASK = 0xFFFFFFFFL;

    public OldPasswordUKMAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try {
            String password = (String) getParam(0, context);
            findProperty("oldPasswordUKM[]").change(password == null || password.isEmpty() ? null : hash(password), context);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }

    private String hash(String password) {
        long nr = 1345345333L;
        long nr2 = 0x12345671L;
        long add = 7;
        for (byte b : password.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if (c == ' ' || c == '\t')
                continue;
            nr = (nr ^ ((((nr & 63) + add) * c) + (nr << 8))) & MASK;
            nr2 = (nr2 + ((nr2 << 8) ^ nr)) & MASK;
            add += c;
        }
        return String.format("%08x%08x", nr & 0x7FFFFFFFL, nr2 & 0x7FFFFFFFL);
    }
}
