package com.chinmayshivratriwar.cns_devintel.service;

import com.chinmayshivratriwar.cns_devintel.schema.TransactionalIssue;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Flags @Service methods that call repository methods but lack @Transactional
 * on either the method or the class.
 *
 * Missing @Transactional can cause:
 *  - No rollback on failure
 *  - LazyInitializationException on lazy-loaded associations
 *  - Dirty reads or lost updates under concurrent load
 */
@Service
public class TransactionalAnalyzerService {

    public List<TransactionalIssue> analyze(List<CompilationUnit> units) {
        List<TransactionalIssue> issues = new ArrayList<>();

        for (CompilationUnit cu : units) {
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
                if (!isServiceBean(clazz)) return;

                boolean classTransactional = hasTransactional(clazz);
                Set<String> repoFields = collectRepoFieldNames(clazz);
                if (repoFields.isEmpty()) return;

                String className = clazz.getNameAsString();

                for (MethodDeclaration method : clazz.getMethods()) {
                    // skip if class or method is already @Transactional
                    if (classTransactional || hasTransactional(method)) continue;

                    // find the first repository call in this method
                    method.findAll(MethodCallExpr.class).stream()
                            .filter(call -> call.getScope()
                                    .map(scope -> repoFields.contains(scope.toString()))
                                    .orElse(false))
                            .findFirst()
                            .ifPresent(call -> issues.add(new TransactionalIssue(
                                    className,
                                    method.getNameAsString(),
                                    call.getNameAsString(),
                                    call.getBegin().map(p -> p.line).orElse(-1),
                                    "Method calls '" + call.getNameAsString()
                                            + "' on a repository but has no @Transactional boundary. "
                                            + "Consider adding @Transactional or @Transactional(readOnly=true)."
                            )));
                }
            });
        }

        return issues;
    }

    private boolean isServiceBean(ClassOrInterfaceDeclaration clazz) {
        return clazz.getAnnotationByName("Service").isPresent()
                || clazz.getAnnotationByName("Component").isPresent();
    }

    private boolean hasTransactional(com.github.javaparser.ast.nodeTypes.NodeWithAnnotations<?> node) {
        return node.getAnnotationByName("Transactional").isPresent();
    }

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
                        || typeName.contains("Jpa")) {
                    names.add(var.getNameAsString());
                }
            });
        }
        return names;
    }
}
