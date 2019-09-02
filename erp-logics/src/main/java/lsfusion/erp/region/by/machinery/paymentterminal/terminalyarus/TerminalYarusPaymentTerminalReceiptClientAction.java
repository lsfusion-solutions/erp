package lsfusion.erp.region.by.machinery.paymentterminal.terminalyarus;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;

public class TerminalYarusPaymentTerminalReceiptClientAction implements ClientAction {

    String host;
    int port;
    BigDecimal sum;
    boolean isSale;
    String comment;

    public TerminalYarusPaymentTerminalReceiptClientAction(String host, Integer port, BigDecimal sum, boolean isSale, String comment) {
        this.host = host;
        this.port = port;
        this.sum = sum;
        this.isSale = isSale;
        this.comment = comment == null ? "" : comment;
    }

    public Object dispatch(ClientActionDispatcher dispatcher) {

        final JOptionPane optionPane = new JOptionPane("Вставьте банковскую карту в платёжный терминал", JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION, null, new Object[]{}, null);

        Window parent = DefaultFocusManager.getCurrentManager().getActiveWindow().getOwner();
        final JDialog dialog = new JDialog(parent);

        dialog.setLocation(parent.getWidth()/2, parent.getHeight()/2);
        dialog.setContentPane(optionPane);

        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.pack();
        dialog.setVisible(true);
        dialog.update(dialog.getGraphics());

        String result = TerminalYarus.operation(host, port, isSale ? 0 : 1, sum, comment);
        dialog.dispose();
        return result;       
        
    }
}