package lsfusion.erp.integration;

import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.logics.action.session.DataSession;
import lsfusion.server.physics.dev.integration.service.ImportKey;
import lsfusion.server.physics.dev.integration.service.ImportProperty;
import lsfusion.server.physics.dev.integration.service.ImportTable;
import lsfusion.server.physics.dev.integration.service.IntegrationService;

import java.sql.SQLException;
import java.util.Collection;

// IntegrationService is deprecated and has no replacement in java (only IMPORT in lsFusion), so it is created only here
public class IntegrationServiceUtils {

    public static void synchronize(DataSession session, ImportTable table, Collection<? extends ImportKey<?>> keys, Collection<ImportProperty<?>> properties,
                                   boolean replaceNull, boolean replaceEqual) throws SQLException, SQLHandledException {
        new IntegrationService(session, table, keys, properties).synchronize(replaceNull, replaceEqual);
    }
}
