package com.chinmayshivratriwar.cns_devintel.utils;

import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;

public class AnnotationUtils {

    public static String extractPath(AnnotationExpr annotation) {

        if (annotation instanceof SingleMemberAnnotationExpr single) {
            return stripQuotes(single.getMemberValue().toString());
        }

        if (annotation instanceof NormalAnnotationExpr normal) {
            for (MemberValuePair pair : normal.getPairs()) {
                String name = pair.getNameAsString();
                if (name.equals("value") || name.equals("path")) {
                    return stripQuotes(pair.getValue().toString());
                }
            }
        }

        return "";
    }

    /**
     * Checks if a class or method declaration has a given annotation.
     */
    public static boolean hasAnnotation(com.github.javaparser.ast.nodeTypes.NodeWithAnnotations<?> node,
                                        String annotationName) {
        return node.getAnnotationByName(annotationName).isPresent();
    }

    /**
     * Checks if a node has any of the given annotations.
     */
    public static boolean hasAnyAnnotation(com.github.javaparser.ast.nodeTypes.NodeWithAnnotations<?> node,
                                           String... names) {
        for (String name : names) {
            if (hasAnnotation(node, name)) return true;
        }
        return false;
    }

    private static String stripQuotes(String val) {
        return val.replaceAll("^\"|\"$", "");
    }
}