package com.filecopier.plugin;

import org.eclipse.jgit.ignore.IgnoreNode;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class GitIgnoreParser {
    private final IgnoreNode ignoreNode = new IgnoreNode();

    public GitIgnoreParser(File gitignoreFile) throws IOException {
        try (InputStream in = new FileInputStream(gitignoreFile)) {
            ignoreNode.parse(in);
        }
    }

    public boolean isIgnored(String relativePath, boolean isDirectory) {
        return ignoreNode.isIgnored(relativePath, isDirectory) == IgnoreNode.MatchResult.IGNORED;
    }
}