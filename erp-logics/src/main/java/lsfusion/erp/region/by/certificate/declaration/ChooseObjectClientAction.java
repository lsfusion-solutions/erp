package lsfusion.erp.region.by.certificate.declaration;


import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

public class ChooseObjectClientAction implements ClientAction {

    String title;
    String[] columnNames;
    Object[][] data;

    public ChooseObjectClientAction(String title, String[] columnNames, Object[][] data) {
        this.title = title;
        this.columnNames = columnNames;
        this.data = data;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) {
        ChooseObjectDialog dialog = new ChooseObjectDialog(title, columnNames, data);
        return dialog.execute();
    }
}