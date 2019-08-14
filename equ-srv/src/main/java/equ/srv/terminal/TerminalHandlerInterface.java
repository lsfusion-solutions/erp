package equ.srv.terminal;

import lsfusion.base.file.RawFileData;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.logics.action.controller.stack.ExecutionStack;
import lsfusion.server.logics.action.session.DataSession;

import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.List;

public interface TerminalHandlerInterface {
    
    void init();
    
    List<Object> readHostPort(DataSession session);

    Object readItem(DataSession session, DataObject user, String barcode, String bin);

    String readItemHtml(DataSession session, String barcode, String idStock);

    RawFileData readBase(DataSession session, DataObject userObject) throws SQLException;

    String savePallet(DataSession session, ExecutionStack stack, DataObject user, String numberPallet, String nameBin);

    String importTerminalDocument(DataSession session, ExecutionStack stack, DataObject userObject, String idTerminal, String idTerminalDocument, List<List<Object>> terminalDocumentDetailList, boolean emptyDocument);

    boolean isActiveTerminal(DataSession session, ExecutionStack stack, String idTerminal);

    DataObject login(DataSession session, ExecutionStack stack, String login, String password, String idTerminal);
}
