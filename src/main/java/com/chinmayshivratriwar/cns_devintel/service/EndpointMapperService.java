package com.chinmayshivratriwar.cns_devintel.service;

import com.chinmayshivratriwar.cns_devintel.schema.Endpoint;
import com.chinmayshivratriwar.cns_devintel.utils.AnnotationUtils;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EndpointMapperService {

    public List<Endpoint> map(List<CompilationUnit> units) {
        List<Endpoint> endpoints = new ArrayList<>();

        for (CompilationUnit cu : units) {
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {

                if (!isRestController(clazz)) return;

                String basePath      = extractBasePath(clazz);
                String controllerName = clazz.getNameAsString();

                for (MethodDeclaration method : clazz.getMethods()) {
                    extractEndpoint(method, basePath, controllerName)
                            .ifPresent(endpoints::add);
                }
            });
        }

        return endpoints;
    }

    private boolean isRestController(ClassOrInterfaceDeclaration clazz) {
        return clazz.getAnnotations().stream()
                .anyMatch(a ->
                        a.getNameAsString().equals("RestController") ||
                                a.getNameAsString().equals("Controller")
                );
    }

    private String extractBasePath(ClassOrInterfaceDeclaration clazz) {
        return clazz.getAnnotationByName("RequestMapping")
                .map(AnnotationUtils::extractPath)
                .orElse("");
    }

    private Optional<Endpoint> extractEndpoint(MethodDeclaration method,
                                               String basePath,
                                               String controllerName) {
        Map<String, String> mappingTypes = Map.of(
                "GetMapping",    "GET",
                "PostMapping",   "POST",
                "PutMapping",    "PUT",
                "DeleteMapping", "DELETE",
                "PatchMapping",  "PATCH"
        );

        for (Map.Entry<String, String> entry : mappingTypes.entrySet()) {
            Optional<com.github.javaparser.ast.expr.AnnotationExpr> found =
                    method.getAnnotationByName(entry.getKey());

            if (found.isPresent()) {
                String path = AnnotationUtils.extractPath(found.get());
                return Optional.of(new Endpoint(
                        entry.getValue(),
                        combinePaths(basePath, path),
                        controllerName,
                        method.getNameAsString()
                ));
            }
        }

        // fallback: @RequestMapping on the method
        Optional<com.github.javaparser.ast.expr.AnnotationExpr> rm =
                method.getAnnotationByName("RequestMapping");
        if (rm.isPresent()) {
            String path = AnnotationUtils.extractPath(rm.get());
            return Optional.of(new Endpoint(
                    "UNKNOWN",
                    combinePaths(basePath, path),
                    controllerName,
                    method.getNameAsString()
            ));
        }

        return Optional.empty();
    }

    private String combinePaths(String base, String method) {

        String path = ("/" + base + "/" + method)
                .replaceAll("//+", "/")
                .replaceAll("/$", "");

        return path.startsWith("/") ? path : "/" + path;
    }
}