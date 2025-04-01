package com.filecopier.plugin;

import com.filecopier.plugin.FileCopierService.FileItem;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import java.awt.event.KeyListener;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Main chat panel UI component that implements the file searching and copying functionality
 */
public class ChatPanel extends JPanel {
    private GitIgnoreParser gitIgnoreParser;
    private final Project project;
    private final JBTextArea inputField;
    private final JPanel chatMessageContainer;
    private final JPanel suggestionPanel;
    private final JBList<FileSuggestion> suggestionList;
    private final DefaultListModel<FileSuggestion> suggestionModel;
    private final JBScrollPane scrollPane;
    private final List<FileItem> selectedItems = new ArrayList<>();
    private boolean navigatingSuggestions = false;

    public ChatPanel(Project project) {
        this.project = project;
        setLayout(new BorderLayout());

        // Create the chat messages container
        chatMessageContainer = new JPanel();
        chatMessageContainer.setLayout(new BoxLayout(chatMessageContainer, BoxLayout.Y_AXIS));
        chatMessageContainer.setBorder(JBUI.Borders.empty(10));

        // Create a welcome message
        addSystemMessage("Welcome to File Copier! Type # followed by a file or folder name to search. " +
                "Use Ctrl+Enter to copy selected files to clipboard.");

        // Add scroll pane for chat messages
        scrollPane = new JBScrollPane(chatMessageContainer);
        scrollPane.setBorder(JBUI.Borders.empty());
        add(scrollPane, BorderLayout.CENTER);

        // Create the input panel at the bottom
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBorder(JBUI.Borders.empty(10, 10, 10, 10));

        // Create the input field
        inputField = new JBTextArea(3, 50);
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        inputField.setLineWrap(true);
        inputField.setWrapStyleWord(true);

        // Add document listener to detect # characters
        inputField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                handleInputChange();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                handleInputChange();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                handleInputChange();
            }
        });



        // Add key listener for special key combinations
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_ENTER) {
                    e.consume();
                    copySelectedFilesToClipboard();
                } else if ((e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_UP) && suggestionPanel.isVisible()) {
                    e.consume();
                    navigatingSuggestions = true;
                    suggestionList.requestFocusInWindow();
                    int size = suggestionList.getModel().getSize();
                    if (size > 0) {
                        int newIndex = e.getKeyCode() == KeyEvent.VK_DOWN ? 0 : size - 1;
                        suggestionList.setSelectedIndex(newIndex);
                        suggestionList.ensureIndexIsVisible(newIndex);
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE && suggestionPanel.isVisible()) {
                    e.consume();
                    hideSuggestions();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER && suggestionPanel.isVisible() &&
                        suggestionList.getSelectedValue() != null) {
                    e.consume();
                    selectSuggestion(suggestionList.getSelectedValue());
                }
            }
        });

        JScrollPane inputScrollPane = new JBScrollPane(inputField);
        inputScrollPane.setBorder(JBUI.Borders.empty());
        inputPanel.add(inputScrollPane, BorderLayout.CENTER);

        // Create toolbar with actions
        DefaultActionGroup actionGroup = new DefaultActionGroup();

        // Add send button (currently used to copy to clipboard)
        actionGroup.add(new AnAction("Copy to Clipboard", "Copy selected files to clipboard",
                AllIcons.Actions.Copy) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                copySelectedFilesToClipboard();
            }
        });

        // Add clear button
        actionGroup.add(new AnAction("Clear Selection", "Clear selected files",
                AllIcons.Actions.GC) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                clearSelection();
            }
        });

        ActionToolbar actionToolbar = ActionManager.getInstance()
                .createActionToolbar("FileCopier", actionGroup, true);
        actionToolbar.setTargetComponent(inputPanel);
        inputPanel.add(actionToolbar.getComponent(), BorderLayout.EAST);

        add(inputPanel, BorderLayout.SOUTH);

        // Create suggestions dropdown (initially hidden)
        suggestionModel = new DefaultListModel<>();
        suggestionList = new JBList<>(suggestionModel);
        suggestionList.setCellRenderer(new FileSuggestionRenderer());
        suggestionList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    e.consume();
                    if (suggestionList.getSelectedValue() != null) {
                        selectSuggestion(suggestionList.getSelectedValue());
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    e.consume();
                    hideSuggestions();
                    inputField.requestFocusInWindow();
                }
            }
        });

        suggestionList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && suggestionList.getSelectedValue() != null) {
                if (!navigatingSuggestions) {
                    selectSuggestion(suggestionList.getSelectedValue());
                }
            }

        });

        suggestionList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) &&
                        suggestionList.getSelectedValue() != null) {
                    selectSuggestion(suggestionList.getSelectedValue());
                }
            }
        });

        //New for selection of up and down
        // Add enhanced key navigation behavior to suggestion list
        suggestionList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    e.consume();
                    navigatingSuggestions = false; // Manual selection now
                    if (suggestionList.getSelectedValue() != null) {
                        selectSuggestion(suggestionList.getSelectedValue());
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    e.consume();
                    navigatingSuggestions = false;
                    hideSuggestions();
                    inputField.requestFocusInWindow();
                } else if (e.getKeyCode() != KeyEvent.VK_UP && e.getKeyCode() != KeyEvent.VK_DOWN) {
                    navigatingSuggestions = false; // Other key pressed
                    inputField.requestFocusInWindow();
                }
            }
        });

        // Add key listener to inputField for up/down arrows to focus suggestion list
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_ENTER) {
                    e.consume();
                    copySelectedFilesToClipboard();
                } else if ((e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_UP) && suggestionPanel.isVisible()) {
                    e.consume();
                    suggestionList.requestFocusInWindow();
                    int size = suggestionList.getModel().getSize();
                    if (size > 0) {
                        int newIndex = e.getKeyCode() == KeyEvent.VK_DOWN ? 0 : size - 1;
                        suggestionList.setSelectedIndex(newIndex);
                        suggestionList.ensureIndexIsVisible(newIndex);
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE && suggestionPanel.isVisible()) {
                    e.consume();
                    hideSuggestions();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER && suggestionPanel.isVisible() &&
                        suggestionList.getSelectedValue() != null) {
                    e.consume();
                    selectSuggestion(suggestionList.getSelectedValue());
                }
            }
        });

        suggestionPanel = new JPanel(new BorderLayout());
        suggestionPanel.add(new JBScrollPane(suggestionList), BorderLayout.CENTER);
        suggestionPanel.setBorder(BorderFactory.createLineBorder(JBColor.border()));
        suggestionPanel.setVisible(false);

        add(suggestionPanel, BorderLayout.NORTH);
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir != null) {
            File gitignore = new File(baseDir.getPath(), ".gitignore");
            if (gitignore.exists()) {
                try {
                    gitIgnoreParser = new GitIgnoreParser(gitignore);
                } catch (IOException e) {
                    addSystemMessage("⚠️ Failed to parse .gitignore: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Handles input changes to detect # for file search
     */
    private void handleInputChange() {
        String text = inputField.getText();
        int caretPosition = inputField.getCaretPosition();

        // Guard against caret being outside of text bounds
        if (caretPosition > text.length() || text.isEmpty()) {
            hideSuggestions();
            return;
        }

        // Find the # that precedes the current caret position
        int hashIndex = -1;
        for (int i = caretPosition - 1; i >= 0; i--) {
            if (i >= text.length()) continue;
            if (text.charAt(i) == '#') {
                hashIndex = i;
                break;
            } else if (text.charAt(i) == '\n') {
                break;
            }
        }

        if (hashIndex >= 0) {
            String query = text.substring(hashIndex + 1, caretPosition).trim().toLowerCase();
            if (!query.isEmpty()) {
                showFileSuggestions(query);
            } else {
                hideSuggestions();
            }
        } else {
            hideSuggestions();
        }
    }

    /**
     * Shows file and folder suggestions matching the query
     */
    private void showFileSuggestions(String query) {
        suggestionModel.clear();

        // Search for files and folders in the project
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<FileSuggestion> suggestions = new ArrayList<>();
            VirtualFile baseDir = project.getBaseDir();

            if (baseDir == null) return;

            findMatchingFilesAndFolders(baseDir, query, suggestions);

            if (!suggestions.isEmpty()) {
                // Sort: folders first, then files, then alphabetically
                suggestions.sort((a, b) -> {
                    if (a.isDirectory != b.isDirectory) {
                        return a.isDirectory ? -1 : 1;
                    }
                    return a.path.compareToIgnoreCase(b.path);
                });

                // Limit to 30 suggestions
                List<FileSuggestion> limitedSuggestions = suggestions.stream()
                        .limit(30)
                        .collect(Collectors.toList());

                // Update UI on EDT
                SwingUtilities.invokeLater(() -> {
                    for (FileSuggestion suggestion : limitedSuggestions) {
                        suggestionModel.addElement(suggestion);
                    }

                    showSuggestionPanel();
                });
            } else {
                SwingUtilities.invokeLater(this::hideSuggestions);
            }
        });
    }

    /**
     * Recursively finds files and folders matching the query
     */
    private void findMatchingFilesAndFolders(VirtualFile dir, String query, List<FileSuggestion> suggestions) {
        if (!dir.isValid()) return;

        for (VirtualFile child : dir.getChildren()) {
            try {
                String relativePath = getRelativePath(project.getBaseDir(), child);

                // 🔥 Skip ignored files
                if (gitIgnoreParser != null && gitIgnoreParser.isIgnored(relativePath, child.isDirectory())) {
                    continue;
                }

                if (child.getName().toLowerCase().contains(query) ||
                        relativePath.toLowerCase().contains(query)) {
                    suggestions.add(new FileSuggestion(relativePath, child.isDirectory()));
                }

                if (child.isDirectory()) {
                    findMatchingFilesAndFolders(child, query, suggestions);
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Gets the path relative to the project base directory
     */
    private String getRelativePath(VirtualFile baseDir, VirtualFile file) {
        String basePath = baseDir.getPath();
        String filePath = file.getPath();

        if (filePath.startsWith(basePath)) {
            return filePath.substring(basePath.length() + 1); // +1 to remove the leading slash
        }

        return filePath;
    }

    /**
     * Shows the suggestion panel
     */
    private void showSuggestionPanel() {
        if (!suggestionPanel.isVisible() && suggestionModel.getSize() > 0) {
            suggestionPanel.setVisible(true);

            // Position the suggestion panel below the input field
            Point inputPosition = inputField.getLocationOnScreen();
            Point panelPosition = getLocationOnScreen();

            int x = inputPosition.x - panelPosition.x;
            int y = inputPosition.y - panelPosition.y - suggestionPanel.getPreferredSize().height;

            suggestionPanel.setLocation(x, y);
            suggestionPanel.setSize(inputField.getWidth(), 200);

            suggestionPanel.revalidate();
            suggestionPanel.repaint();
        }
    }

    /**
     * Hides the suggestion panel
     */
    private void hideSuggestions() {
        suggestionPanel.setVisible(false);
    }

    /**
     * Handles selection of a file or folder suggestion
     */
    private void selectSuggestion(FileSuggestion suggestion) {
        // Add to selected items
        FileItem item = new FileItem(suggestion.path, suggestion.isDirectory);
        if (!selectedItems.contains(item)) {
            selectedItems.add(item);

            // Add message to chat showing the selected item
            String icon = suggestion.isDirectory ? "📁" : "📄";
            addUserMessage("Selected " + icon + " " + suggestion.path);

            // Clear the input field
            inputField.setText("");
        }

        hideSuggestions();
        inputField.requestFocusInWindow();
    }

    /**
     * Copies all selected files to clipboard
     */
    public void copySelectedFilesToClipboard() {
        if (selectedItems.isEmpty()) {
            addSystemMessage("No files selected. Type # followed by a filename to search and select files.");
            return;
        }

        // Start a background task
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Copying Files to Clipboard") {
            private String clipboardContent = "";
            private int fileCount = 0;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                StringBuilder contentBuilder = new StringBuilder();

                for (FileItem item : selectedItems) {
                    if (indicator.isCanceled()) return;

                    if (item.isDirectory()) {
                        processDirectory(item.getPath(), contentBuilder, indicator);
                    } else {
                        processFile(item.getPath(), contentBuilder, indicator);
                    }
                }

                clipboardContent = contentBuilder.toString();
            }

            private void processDirectory(String dirPath, StringBuilder contentBuilder, ProgressIndicator indicator) {
                VirtualFile dir = project.getBaseDir().findFileByRelativePath(dirPath);
                if (dir == null || !dir.isValid() || !dir.isDirectory()) return;

                contentBuilder.append("### Folder: ").append(dirPath).append(" ###\n\n");

                for (VirtualFile child : dir.getChildren()) {
                    if (indicator.isCanceled()) return;

                    if (child.isDirectory()) {
                        processDirectory(getRelativePath(project.getBaseDir(), child), contentBuilder, indicator);
                    } else {
                        processFile(getRelativePath(project.getBaseDir(), child), contentBuilder, indicator);
                    }
                }
            }

            private void processFile(String filePath, StringBuilder contentBuilder, ProgressIndicator indicator) {
                indicator.setText("Processing " + filePath);

                VirtualFile file = project.getBaseDir().findFileByRelativePath(filePath);
                if (file == null || !file.isValid() || file.isDirectory()) return;

                try {
                    // Skip binary files
                    if (file.getFileType().isBinary()) {
                        contentBuilder.append("### File: ").append(filePath)
                                .append(" (binary file, content not copied) ###\n\n");
                        return;
                    }

                    contentBuilder.append("### File: ").append(filePath).append(" ###\n");

                    // Read the file content
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (indicator.isCanceled()) return;
                            contentBuilder.append(line).append("\n");
                        }
                    }

                    contentBuilder.append("\n\n");
                    fileCount++;

                } catch (IOException e) {
                    contentBuilder.append("### Error reading ").append(filePath)
                            .append(": ").append(e.getMessage()).append(" ###\n\n");
                }
            }

            @Override
            public void onSuccess() {
                // Copy to clipboard
                StringSelection selection = new StringSelection(clipboardContent);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(selection, null);

                // Add confirmation message
                addSystemMessage("✅ Copied " + fileCount + " files to clipboard!");
            }

            @Override
            public void onCancel() {
                addSystemMessage("❌ Operation canceled");
            }
        });
    }

    /**
     * Clears the selected files list
     */
    public void clearSelection() {
        selectedItems.clear();
        addSystemMessage("Cleared all selected files and folders");
    }

    /**
     * Updates the selected items from the service
     */
    public void updateSelectedItems(List<FileItem> items) {
        this.selectedItems.clear();
        this.selectedItems.addAll(items);

        // Optionally, refresh the UI to show the current selections
        // For example, you could add a visual indicator in the chat
        addSystemMessage("Selection updated: " + items.size() + " items selected");
    }

    /**
     * Adds a user message to the chat
     */
    private void addUserMessage(String message) {
        JPanel messagePanel = createMessagePanel(message, true);
        chatMessageContainer.add(messagePanel);
        chatMessageContainer.add(Box.createVerticalStrut(10));
        chatMessageContainer.revalidate();
        chatMessageContainer.repaint();

        // Scroll to bottom
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    /**
     * Adds a system message to the chat
     */
    private void addSystemMessage(String message) {
        JPanel messagePanel = createMessagePanel(message, false);
        chatMessageContainer.add(messagePanel);
        chatMessageContainer.add(Box.createVerticalStrut(10));
        chatMessageContainer.revalidate();
        chatMessageContainer.repaint();

        // Scroll to bottom
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    /**
     * Creates a styled message panel for the chat
     */
    private JPanel createMessagePanel(String message, boolean isUser) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        JTextArea textArea = new JTextArea();
        textArea.setText(message);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setOpaque(true);

        if (isUser) {
            textArea.setBackground(JBColor.namedColor("EditorPane.background", JBColor.WHITE));
            Border lineBorder = BorderFactory.createLineBorder(JBColor.namedColor("TextField.selectedSeparatorColor", JBColor.GRAY), 1);
            Border emptyBorder = new EmptyBorder(10, 10, 10, 10);
            textArea.setBorder(new CompoundBorder(lineBorder, emptyBorder));
            panel.add(textArea, BorderLayout.CENTER);
        } else {
            textArea.setBackground(JBColor.namedColor("EditorPane.background", JBColor.WHITE));
            Border lineBorder = BorderFactory.createLineBorder(JBColor.namedColor("TextField.separatorColor", JBColor.LIGHT_GRAY), 1);
            Border emptyBorder = new EmptyBorder(10, 10, 10, 10);
            textArea.setBorder(new CompoundBorder(lineBorder, emptyBorder));

            JPanel iconPanel = new JPanel(new BorderLayout());
            iconPanel.setOpaque(false);
            iconPanel.setPreferredSize(new Dimension(30, 30));

            JLabel iconLabel = new JLabel(AllIcons.General.BalloonInformation);
            iconPanel.add(iconLabel, BorderLayout.NORTH);

            panel.add(iconPanel, BorderLayout.WEST);
            panel.add(textArea, BorderLayout.CENTER);
        }

        return panel;
    }

    /**
     * Model class for file suggestions
     */
    private static class FileSuggestion {
        final String path;
        final boolean isDirectory;

        FileSuggestion(String path, boolean isDirectory) {
            this.path = path;
            this.isDirectory = isDirectory;
        }

        @Override
        public String toString() {
            return path;
        }
    }

    /**
     * Custom renderer for file suggestions
     */
    private static class FileSuggestionRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof FileSuggestion) {
                FileSuggestion suggestion = (FileSuggestion) value;

                if (suggestion.isDirectory) {
                    label.setIcon(AllIcons.Nodes.Folder);
                    label.setText(suggestion.path);
                } else {
                    label.setIcon(AllIcons.FileTypes.Text);
                    label.setText(suggestion.path);
                }
            }

            return label;
        }
    }
}