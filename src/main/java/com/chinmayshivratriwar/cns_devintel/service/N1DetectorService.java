package com.chinmayshivratriwar.cns_devintel.service;


import com.chinmayshivratriwar.cns_devintel.schema.N1Issue;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects potential N+1 query problems.
 *
 * Heuristic: find @Service/@Component classes where a method
 * contains a loop (for/foreach/while) that calls a method on
 * a field whose type ends with "Repository" or whose name ends with "repo/repository".
 *
 * This is AST-level analysis — no symbol resolution — so it produces
 * conservative false-positives the caller's LLM can filter intelligently.
 */
@Service
public class N1DetectorService {

    public List<N1Issue> detect(List<CompilationUnit> units) {
        List<N1Issue> issues = new ArrayList<>();

        for (CompilationUnit cu : units) {
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
                if (!isServiceBean(clazz)) return;

                // collect repository field names in this class
                Set<String> repoFields = collectRepoFieldNames(clazz);
                if (repoFields.isEmpty()) return;

                for (MethodDeclaration method : clazz.getMethods()) {
                    scanMethodForN1(clazz.getNameAsString(), method, repoFields, issues);
                }
            });
        }

        return issues;
    }

    private boolean isServiceBean(ClassOrInterfaceDeclaration clazz) {
        return clazz.getAnnotationByName("Service").isPresent()
                || clazz.getAnnotationByName("Component").isPresent();
    }

    /**
     * Returns field names whose declared type or name suggests a JPA Repository.
     */
    private Set<String> collectRepoFieldNames(ClassOrInterfaceDeclaration clazz) {
        Set<String> names = new HashSet<>();
        for (FieldDeclaration field : clazz.getFields()) {
            String typeName = field.getElementType().asString();
            field.getVariables().forEach(var -> {
                String varName = var.getNameAsString().toLowerCase();
                if (typeName.endsWith("Repository")
                        || varName.endsWith("repository")
                        || varName.endsWith("repo")
                        || typeName.endsWith("Dao")
                        || typeName.contains("Jpa")
                ) {
                    names.add(var.getNameAsString());
                }
            });
        }
        return names;
    }

    private void scanMethodForN1(String className,
                                 MethodDeclaration method,
                                 Set<String> repoFields,
                                 List<N1Issue> issues) {
        String methodName = method.getNameAsString();

        // ForEach loops
        method.findAll(ForEachStmt.class).forEach(loop -> {
            findRepoCallsInNode(loop, repoFields).forEach(call ->
                    issues.add(new N1Issue(
                            className, methodName,
                            call.getNameAsString(),
                            call.getBegin().map(p -> p.line).orElse(-1),
                            "Potential N+1: repository method '" + call.getNameAsString()
                                    + "' called inside a for-each loop."
                    ))
            );
        });

        // Classic for loops
        method.findAll(ForStmt.class).forEach(loop -> {
            findRepoCallsInNode(loop, repoFields).forEach(call ->
                    issues.add(new N1Issue(
                            className, methodName,
                            call.getNameAsString(),
                            call.getBegin().map(p -> p.line).orElse(-1),
                            "Potential N+1: repository method '" + call.getNameAsString()
                                    + "' called inside a for loop."
                    ))
            );
        });

        // While loops
        method.findAll(WhileStmt.class).forEach(loop -> {
            findRepoCallsInNode(loop, repoFields).forEach(call ->
                    issues.add(new N1Issue(
                            className, methodName,
                            call.getNameAsString(),
                            call.getBegin().map(p -> p.line).orElse(-1),
                            "Potential N+1: repository method '" + call.getNameAsString()
                                    + "' called inside a while loop."
                    ))
            );
        });
    }

    /**
     * Within an AST node (a loop body), find all method calls whose scope
     * matches one of our known repository field names.
     */
    private List<MethodCallExpr> findRepoCallsInNode(com.github.javaparser.ast.Node node,
                                                     Set<String> repoFields) {
        List<MethodCallExpr> found = new ArrayList<>();
        node.findAll(MethodCallExpr.class).forEach(call -> {
            call.getScope().ifPresent(scope -> {
                if (repoFields.contains(scope.toString())) {
                    found.add(call);
                }
            });
        });
        return found;
    }
}
