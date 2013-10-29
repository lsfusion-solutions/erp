package lsfusion.erp.region.by.machinery.paymentterminal.terminaljadeeko;

import lsfusion.interop.action.ClientAction;
import lsfusion.interop.action.ClientActionDispatcher;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.math.BigDecimal;


public class TerminalJadeEKOPaymentTerminalReceiptClientAction implements ClientAction {

    int comPort;
    BigDecimal sum;
    boolean isSale;
    String comment;

    public TerminalJadeEKOPaymentTerminalReceiptClientAction(Integer comPort, BigDecimal sum, boolean isSale, String comment) {
        this.comPort = comPort == null ? 1 : comPort;
        this.sum = sum;
        this.isSale = isSale;
        this.comment = comment == null ? "" : comment;
    }


    public Object dispatch(ClientActionDispatcher dispatcher) throws IOException {

        final JOptionPane optionPane = new JOptionPane("Вставьте банковскую карту в платёжный терминал", JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION, null, new Object[]{}, null);

        Window parent = DefaultFocusManager.getCurrentManager().getActiveWindow().getOwner();
        final JDialog dialog = new JDialog(parent);

        dialog.setLocation(parent.getWidth()/2, parent.getHeight()/2);
        dialog.setContentPane(optionPane);

        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.pack();
        dialog.setVisible(true);
        dialog.update(dialog.getGraphics());

        TerminalJadeEKO.init();
        
        String result = TerminalJadeEKO.operation(comPort, isSale ? 0 : 1, sum, comment);
        dialog.dispose();
        return result;       
        
    }
}
