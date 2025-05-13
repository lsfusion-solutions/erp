package lsfusion.erp.region.by.certificate.declaration;

import lsfusion.base.ApiResourceBundle;

import javax.swing.*;
import java.awt.*;

public class ChooseObjectDialog extends JDialog {
    final JTable table;
    private Integer result;
    
    public ChooseObjectDialog(String title, String[] columnNames, Object[][] data) {
        super(null, title, ModalityType.APPLICATION_MODAL);
        setMinimumSize(new Dimension(400, 200));
        setLocationRelativeTo(null);

        table = new JTable(data, columnNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JScrollPane tablePane = new JScrollPane(table);

        JButton OKButton = new JButton(ApiResourceBundle.getString("dialog.ok"));
        OKButton.addActionListener(e -> onOk());

        JButton cancelButton = new JButton(ApiResourceBundle.getString("dialog.cancel"));
        cancelButton.addActionListener(e -> onCancel());

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());
        mainPanel.add(tablePane, BorderLayout.CENTER);
        JPanel buttonsPanel = new JPanel();
        buttonsPanel.add(OKButton, BorderLayout.EAST);
        buttonsPanel.add(cancelButton, BorderLayout.EAST);
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);

    }

    private void onOk() {
        result = table.getSelectedRow();
        this.dispose();
    }

    private void onCancel() {
        result = null;
        this.dispose();
    }

    public Integer execute() {
        this.setVisible(true);
        return this.result;
    }
}