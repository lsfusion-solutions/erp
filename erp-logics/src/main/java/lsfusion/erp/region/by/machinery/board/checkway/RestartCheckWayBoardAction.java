package lsfusion.erp.region.by.machinery.board.checkway;

import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.BusinessLogicsBootstrap;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.sql.SQLException;

public class RestartCheckWayBoardAction extends InternalAction {

    public RestartCheckWayBoardAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        CheckWayBoardDaemon boardDaemon = (CheckWayBoardDaemon) BusinessLogicsBootstrap.getSpringContextBean("checkWayBoardDaemon");
        if (boardDaemon != null) {
            boardDaemon.setupDaemon();
        }
    }
}