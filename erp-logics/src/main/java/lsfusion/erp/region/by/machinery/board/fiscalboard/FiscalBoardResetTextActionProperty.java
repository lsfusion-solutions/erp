package lsfusion.erp.region.by.machinery.board.fiscalboard;

import lsfusion.server.classes.ValueClass;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.sql.SQLException;
import java.util.Iterator;

public class FiscalBoardResetTextActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface receiptInterface;

    public FiscalBoardResetTextActionProperty(ScriptingLogicsModule LM) throws ScriptingErrorLog.SemanticErrorException {
        super(LM, new ValueClass[]{LM.findClassByCompoundName("Receipt")});

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        receiptInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) {

        DataObject receiptObject = context.getDataKeyValue(receiptInterface);
        
        try {

            Integer comPortBoard = (Integer) getLCP("comPortBoardCurrentCashRegister").read(context);
            Integer baudRateBoard = (Integer) getLCP("baudRateBoardCurrentCashRegister").read(context);

            String line = "";
            for(int i = 0; i<20; i++)
                line += " ";
            
            context.requestUserInteraction(new FiscalBoardDisplayTextClientAction(line, line, baudRateBoard, comPortBoard));


        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        }
    }
}
