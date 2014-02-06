package lsfusion.erp.region.by.machinery;

import com.google.common.base.Throwables;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import lsfusion.server.session.DataSession;

import java.sql.SQLException;
import java.util.Iterator;

public class RequestSalesInfoStockActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface stockInterface;

    public RequestSalesInfoStockActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, LM.findClassByCompoundName("Stock"));

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        stockInterface = i.next();
    }

    @Override
    protected void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            DataSession session = context.createSession();

            DataObject stockObject = context.getDataKeyValue(stockInterface);

            getLCP("requestSalesInfoStock").change(true, session, stockObject);

            session.apply(context);

            getLAP("formRefresh").execute(context);                    
            
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw Throwables.propagate(e);
        }
    }
}
