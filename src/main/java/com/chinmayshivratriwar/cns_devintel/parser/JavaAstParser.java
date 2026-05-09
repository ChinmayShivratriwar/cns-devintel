package com.chinmayshivratriwar.cns_devintel.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
public class JavaAstParser {

    private static final ParserConfiguration CONFIG = new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);

    public List<CompilationUnit> parseProject(File root) {
        List<CompilationUnit> units = new ArrayList<>();

        Collection<File> files = listJavaFiles(root);

        for (File file : files) {
            try {
                new JavaParser(CONFIG)
                        .parse(file)
                        .getResult()
                        .ifPresent(units::add);
            } catch (Exception e) {
                // skip broken / unparseable files
            }
        }

        return units;
    }

    private Collection<File> listJavaFiles(File dir) {
        List<File> files = new ArrayList<>();

        File[] list = dir.listFiles();
        if (list == null) return files;

        for (File f : list) {
            if (f.isDirectory()) {
                files.addAll(listJavaFiles(f));
            } else if (f.getName().endsWith(".java")) {
                files.add(f);
            }
        }

        return files;
    }
}