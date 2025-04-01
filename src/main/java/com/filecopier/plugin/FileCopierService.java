package com.filecopier.plugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing the file copier state and interactions
 */
@Service
public final class FileCopierService {
    private final Project project;
    private List<FileItem> selectedItems = new ArrayList<>();

    public FileCopierService(Project project) {
        this.project = project;
    }

    /**
     * Gets the singleton instance of the service for the specified project
     */
    public static FileCopierService getInstance(Project project) {
        return project.getService(FileCopierService.class);
    }

    /**
     * Adds a file to the selection
     */
    public void addSelectedFile(String path) {
        FileItem item = new FileItem(path, false);
        if (!selectedItems.contains(item)) {
            selectedItems.add(item);
            notifySelectionChanged();
        }
    }

    /**
     * Adds a folder to the selection
     */
    public void addSelectedFolder(String path) {
        FileItem item = new FileItem(path, true);
        if (!selectedItems.contains(item)) {
            selectedItems.add(item);
            notifySelectionChanged();
        }
    }

    /**
     * Copies all selected files to clipboard
     */
    public void copySelectedFilesToClipboard() {
        ChatPanel chatPanel = getChatPanel();
        if (chatPanel != null) {
            chatPanel.copySelectedFilesToClipboard();
        }
    }

    /**
     * Clears the selection
     */
    public void clearSelection() {
        selectedItems.clear();
        notifySelectionChanged();

        ChatPanel chatPanel = getChatPanel();
        if (chatPanel != null) {
            chatPanel.clearSelection();
        }
    }

    /**
     * Gets the list of selected items
     */
    public List<FileItem> getSelectedItems() {
        return new ArrayList<>(selectedItems);
    }

    /**
     * Gets the chat panel instance
     */
    private ChatPanel getChatPanel() {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("FileCopier");
        if (toolWindow != null) {
            Content content = toolWindow.getContentManager().getContent(0);
            if (content != null && content.getComponent() instanceof ChatPanel) {
                return (ChatPanel) content.getComponent();
            }
        }
        return null;
    }

    /**
     * Notifies that the selection has changed
     */
    private void notifySelectionChanged() {
        // This could be expanded to use a proper event system if needed
        ApplicationManager.getApplication().invokeLater(() -> {
            ChatPanel chatPanel = getChatPanel();
            if (chatPanel != null) {
                chatPanel.updateSelectedItems(selectedItems);
            }
        });
    }

    /**
     * Model class for selected files
     */
    public static class FileItem {
        private final String path;
        private final boolean isDirectory;

        public FileItem(String path, boolean isDirectory) {
            this.path = path;
            this.isDirectory = isDirectory;
        }

        public String getPath() {
            return path;
        }

        public boolean isDirectory() {
            return isDirectory;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FileItem fileItem = (FileItem) o;
            return isDirectory == fileItem.isDirectory && path.equals(fileItem.path);
        }

        @Override
        public int hashCode() {
            return 31 * path.hashCode() + (isDirectory ? 1 : 0);
        }
    }
}