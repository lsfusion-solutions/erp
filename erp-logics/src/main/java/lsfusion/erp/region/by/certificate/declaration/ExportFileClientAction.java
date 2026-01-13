package lsfusion.erp.region.by.certificate.declaration;

import lsfusion.base.BaseUtils;
import lsfusion.base.SystemUtils;
import lsfusion.base.file.RawFileData;
import lsfusion.interop.action.ClientActionDispatcher;
import lsfusion.interop.action.ExecuteClientAction;
import org.jfree.ui.ExtensionFileFilter;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static lsfusion.base.ApiResourceBundle.getString;

public class ExportFileClientAction extends ExecuteClientAction {

    // в качестве ключей - имена файлов, не пути к ним
    public Map<String, RawFileData> files;

    public ExportFileClientAction(Map<String, RawFileData> files) {
        this.files = files;
    }

    @Override
    public void execute(ClientActionDispatcher dispatcher) throws IOException {
        Map<String, RawFileData> chosenFiles = showSaveFileDialog(files);
        for(Map.Entry<String, RawFileData> fileEntry : chosenFiles.entrySet()) {
            fileEntry.getValue().write(fileEntry.getKey());
        }
    }

    private Map<String, RawFileData> showSaveFileDialog(Map<String, RawFileData> files) {
        Map<String, RawFileData> resultMap = new HashMap<>();
        JFileChooser fileChooser = new JFileChooser();
        Map<String, String> chosenFiles = chooseFiles(fileChooser, files.keySet());

        for (Map.Entry<String, String> chosenFile : chosenFiles.entrySet()) {
            File file = new File(chosenFile.getValue());
            if (chosenFiles.size() == 1 && file.exists()) {
                int answer = showConfirmDialog(fileChooser, getString("layout.menu.file.already.exists.replace"),
                        getString("layout.menu.file.already.exists"));
                if (answer != JOptionPane.YES_OPTION) {
                    break;
                }
            }
            resultMap.put(chosenFile.getValue(), files.get(chosenFile.getKey()));
        }
        return resultMap;
    }

    private Map<String, String> chooseFiles(JFileChooser fileChooser, Set<String> files) {
        Map<String, String> result = new HashMap<>();
        fileChooser.setCurrentDirectory(SystemUtils.loadCurrentDirectory());
        boolean singleFile;
        fileChooser.setAcceptAllFileFilterUsed(false);
        if (files.size() > 1) {
            singleFile = false;
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        } else {
            singleFile = true;
            File file = new File(files.iterator().next());
            fileChooser.setSelectedFile(file);
            String extension = BaseUtils.getFileExtension(file);
            if (!BaseUtils.isRedundantString(extension)) {
                ExtensionFileFilter filter = new ExtensionFileFilter("." + extension, extension);
                fileChooser.addChoosableFileFilter(filter);
            }
        }
        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            String path = fileChooser.getSelectedFile().getAbsolutePath();
            for (String file : files) {
                if (singleFile) {
                    result.put(file, path);
                } else {
                    result.put(file, path + "\\" + file);
                }
            }
            SystemUtils.saveCurrentDirectory(singleFile ? new File(path).getParentFile() : new File(path));
        }
        return result;
    }

    private int showConfirmDialog(Component parentComponent, Object message, String title) {

        Object[] options = {UIManager.getString("OptionPane.yesButtonText"),
                UIManager.getString("OptionPane.noButtonText")};

        JOptionPane dialogPane = new JOptionPane(message,
                JOptionPane.QUESTION_MESSAGE,
                JOptionPane.YES_NO_OPTION,
                null, options, options[0]);

        addFocusTraversalKey(dialogPane, KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, KeyStroke.getKeyStroke("RIGHT"));
        addFocusTraversalKey(dialogPane, KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, KeyStroke.getKeyStroke("UP"));
        addFocusTraversalKey(dialogPane, KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, KeyStroke.getKeyStroke("LEFT"));
        addFocusTraversalKey(dialogPane, KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, KeyStroke.getKeyStroke("DOWN"));

        dialogPane.createDialog(parentComponent, title).setVisible(true);

        if (dialogPane.getValue() == JOptionPane.UNINITIALIZED_VALUE)
            return 0;
        if (dialogPane.getValue() == options[0]) {
            return JOptionPane.YES_OPTION;
        } else {
            return JOptionPane.NO_OPTION;
        }
    }

    private void addFocusTraversalKey(Component comp, int id, KeyStroke key) {
        Set keys = comp.getFocusTraversalKeys(id);
        Set newKeys = new HashSet(keys);
        newKeys.add(key);
        comp.setFocusTraversalKeys(id, newKeys);
    }
}
