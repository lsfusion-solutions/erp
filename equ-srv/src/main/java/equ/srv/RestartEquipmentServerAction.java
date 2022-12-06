package equ.srv;

import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.BusinessLogicsBootstrap;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.sql.SQLException;

public class RestartEquipmentServerAction extends InternalAction {

    public RestartEquipmentServerAction(ScriptingLogicsModule LM) {
        super(LM);
    }

    @Override
    protected void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        EquipmentServer equipmentServer = (EquipmentServer) BusinessLogicsBootstrap.getSpringContextBean("equipmentServer");
        if (equipmentServer != null) {
            equipmentServer.restartEquipmentServer();
        }
    }
}