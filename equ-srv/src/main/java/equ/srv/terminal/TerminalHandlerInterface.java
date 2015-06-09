package equ.srv.terminal;

import lsfusion.server.logics.DataObject;
import lsfusion.server.session.DataSession;

import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.List;

public interface TerminalHandlerInterface {

    List<String> readItem(DataSession session, DataObject user, String barcode) throws RemoteException, SQLException;

    String importTerminalDocument(DataSession session, DataObject userObject, String idTerminalDocument, List<List<Object>> terminalDocumentDetailList, boolean emptyDocument) throws RemoteException, SQLException;

    DataObject getUserObject(DataSession session, String login, String password) throws RemoteException, SQLException;
}
