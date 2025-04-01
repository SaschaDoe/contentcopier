package com.filecopier.plugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Factory class that creates the tool window content.
 */
public class FileCopierToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        FileCopierToolWindow fileCopierToolWindow = new FileCopierToolWindow(project);

        // Use ContentFactory.getInstance() instead of the deprecated SERVICE version
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(fileCopierToolWindow.getContent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }

    @Override
    public void init(ToolWindow window) {
        window.setStripeTitle("File Copier");
        window.setTitle("File Copier");
    }
}

/**
 * The main tool window class that contains the chat interface.
 */
class FileCopierToolWindow {
    private final Project project;
    private final ChatPanel chatPanel;

    public FileCopierToolWindow(Project project) {
        this.project = project;
        this.chatPanel = new ChatPanel(project);
    }

    public ChatPanel getContent() {
        return chatPanel;
    }
}