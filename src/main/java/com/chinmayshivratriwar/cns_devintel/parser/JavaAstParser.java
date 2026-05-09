package com.chinmayshivratriwar.cns_devintel.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component  // added: needs to be a Spring bean
public class JavaAstParser {

    public List<CompilationUnit> parseProject(File root) {
        List<CompilationUnit> units = new ArrayList<>();

        Collection<File> files = listJavaFiles(root);

        for (File file : files) {
            try {
                CompilationUnit cu = StaticJavaParser.parse(file);
                units.add(cu);
            } catch (Exception e) {
                // skip broken files
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