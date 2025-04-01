package com.filecopier.plugin;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBTextArea;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class PastePathAppender {
    private final JBTextArea inputField;
    private final Project project;
    private int lastLength = 0;
    private String lastText = "";

    public PastePathAppender(JBTextArea inputField, Project project) {
        this.inputField = inputField;
        this.project = project;

        inputField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(() -> detectAndAppendPath());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                lastLength = inputField.getText().length();
                lastText = inputField.getText();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {}
        });
    }

    private void detectAndAppendPath() {
        try {
            String currentText = inputField.getText();
            int currentLength = currentText.length();
            int change = currentLength - lastLength;

            if (change > 50) { // Heuristic paste threshold
                int caretPos = inputField.getCaretPosition();

                String inserted = currentText.substring(caretPos - change, caretPos);
                String filePath = getCurrentEditorRelativePath();

                if (filePath != null && inserted != null && !inserted.isEmpty()) {
                    String withPath = "#" + filePath + "\n" + inserted;
                    String newText = currentText.substring(0, caretPos - change)
                            + withPath
                            + currentText.substring(caretPos);

                    inputField.setText(newText);
                    inputField.setCaretPosition(caretPos + filePath.length() + 2); // position after paste
                }
            }

            lastLength = inputField.getText().length();
            lastText = inputField.getText();

        } catch (Exception ex) {

        }
    }

    private String getCurrentEditorRelativePath() {
        FileEditorManager manager = FileEditorManager.getInstance(project);
        VirtualFile[] files = manager.getSelectedFiles();
        if (files.length > 0) {
            VirtualFile file = files[0];
            VirtualFile base = project.getBaseDir();
            if (file.getPath().startsWith(base.getPath())) {
                return file.getPath().substring(base.getPath().length() + 1);
            }
        }
        return null;
    }
}
