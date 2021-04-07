package lsfusion.erp.machinery.terminal;

import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.BusinessLogicsBootstrap;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.sql.SQLException;

public class RestartTerminalAction extends InternalAction {

    public RestartTerminalAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        TerminalServer terminalServer = (TerminalServer) BusinessLogicsBootstrap.getSpringContextBean("terminalHandler");
        if (terminalServer != null) {
            terminalServer.setupDaemon();
        }
    }
}