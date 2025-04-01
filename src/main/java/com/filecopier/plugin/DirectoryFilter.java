package com.filecopier.plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DirectoryFilter {
    public static void main(String[] args) throws IOException {
        Path projectRoot = Paths.get("/path/to/your/project");
        File gitignoreFile = projectRoot.resolve(".gitignore").toFile();
        GitIgnoreParser gitIgnoreParser = new GitIgnoreParser(gitignoreFile);

        Files.walk(projectRoot)
                .map(projectRoot::relativize)
                .map(Path::toString)
                .filter(relativePath -> {
                    File file = projectRoot.resolve(relativePath).toFile();
                    boolean isDirectory = file.isDirectory();
                    return !gitIgnoreParser.isIgnored(relativePath, isDirectory);
                })
                .forEach(System.out::println);
    }
}
