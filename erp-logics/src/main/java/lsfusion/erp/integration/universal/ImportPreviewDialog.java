package lsfusion.erp.integration.universal;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ImportPreviewDialog extends JDialog {
    Set<String> articleSet;
    final JTableCheck table;
    private HashMap<String, String> result;
    
    public ImportPreviewDialog(Map<String, Object[]> articles, Set<String> articleSet) {
        super(null, "Пересечение кодов артикулов", ModalityType.APPLICATION_MODAL);
        setMinimumSize(new Dimension(600, 250));
        setLocationRelativeTo(null);
        this.articleSet = articleSet;

        String[] columns = new String[]{"Код артикула", "Старое свойство", "Новое свойство", "Изменить код артикула"};
        int rowCount = articles.size();
        Object[][] data = new Object[rowCount][columns.length];

        int i = 0;
        for (Map.Entry<String, Object[]> entry : articles.entrySet()) {
            data[i][0] = entry.getKey() == null ? "" : entry.getKey();
            data[i][1] = entry.getValue() == null ? "" : entry.getValue()[0];
            data[i][2] = entry.getValue() == null ? "" : entry.getValue()[1];
            data[i][3] = Boolean.FALSE;
            i++;
        }

        table = new JTableCheck(columns, data);
        JScrollPane tablePane = new JScrollPane(table);

        JButton OKButton = new JButton("OK");
        OKButton.addActionListener(e -> onOk());

        JButton cancelButton = new JButton("Отмена");
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
        result = new HashMap<>();
        for (Object[] row : table.getCheckTableModel().data) {
            String oldArticle = (String) row[0];
            String newArticle = oldArticle + ".";
            boolean changeArticle = (Boolean) row[3];
            if(changeArticle) {                
                while(articleSet.contains(newArticle))
                    newArticle += ".";
                result.put(oldArticle, newArticle);
            }
        }
        this.dispose();
    }

    private void onCancel() {
        result = null;
        this.dispose();
    }

    public HashMap<String, String> execute() {
        this.setVisible(true);
        return this.result;
    }
}


class JTableCheck extends JPanel {
    private CheckTableModel checkTableModel;

    public JTableCheck(String[] columns, Object[][] data) {
        initializeUI(columns, data);
    }

    private void initializeUI(String[] columns, Object[][] data) {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(550, 150));

        checkTableModel = new CheckTableModel(columns, data);
        JTable table = new JTable(checkTableModel);
        table.setFillsViewportHeight(true);
        JScrollPane pane = new JScrollPane(table);
        add(pane, BorderLayout.CENTER);

        TableRowSorter<TableModel> sorter = new TableRowSorter(checkTableModel);
        table.setRowSorter(sorter);
    }

    public CheckTableModel getCheckTableModel() {
        return checkTableModel;
    }
}

class CheckTableModel extends AbstractTableModel {
    String[] columns;
    Object[][] data;

    public CheckTableModel(String[] columns, Object[][] data) {
        this.columns = columns;
        this.data = data;
    }

    public int getRowCount() {
        return data.length;
    }

    public int getColumnCount() {
        return columns.length;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
        return data[rowIndex][columnIndex];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        if (columnIndex == 3)
            return true;
        else return false;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        data[rowIndex][columnIndex] = aValue;
    }

    @Override
    public String getColumnName(int column) {
        return columns[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return data[0][columnIndex].getClass();
    }
}