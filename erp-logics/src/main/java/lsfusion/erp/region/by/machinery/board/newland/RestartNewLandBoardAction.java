package lsfusion.erp.region.by.machinery.board.newland;

import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.BusinessLogicsBootstrap;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.sql.SQLException;

public class RestartNewLandBoardAction extends InternalAction {

    public RestartNewLandBoardAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        NewLandBoardDaemon boardDaemon = (NewLandBoardDaemon) BusinessLogicsBootstrap.getSpringContextBean("newLandBoardDaemon");
        if(boardDaemon != null) {
            boardDaemon.setupDaemon();
        }
    }
}