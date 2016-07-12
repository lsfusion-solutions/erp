package equ.srv.terminal;

import lsfusion.server.logics.DataObject;
import lsfusion.server.context.ExecutionStack;
import lsfusion.server.session.DataSession;

import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.List;

public interface TerminalHandlerInterface {

    List<Object> readHostPort(DataSession session) throws RemoteException, SQLException;

    List<String> readItem(DataSession session, DataObject user, String barcode) throws RemoteException, SQLException;

    String readItemHtml(DataSession session, String barcode, String idStock) throws RemoteException, SQLException;

    byte[] readBase(DataSession session, DataObject userObject) throws RemoteException, SQLException;

    String importTerminalDocument(DataSession session, ExecutionStack stack, DataObject userObject, String idTerminalDocument, List<List<Object>> terminalDocumentDetailList, boolean emptyDocument) throws RemoteException, SQLException;

    boolean isActiveTerminal(DataSession session, String idTerminal) throws RemoteException, SQLException;

    DataObject login(DataSession session, ExecutionStack stack, String login, String password, String idTerminal) throws RemoteException, SQLException;
}
