package com.chinmayshivratriwar.cns_devintel.service;


import com.chinmayshivratriwar.cns_devintel.schema.SecurityIssue;
import com.chinmayshivratriwar.cns_devintel.utils.AnnotationUtils;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects security issues in Spring Boot controllers:
 *
 *  UNGUARDED_ENDPOINT — REST endpoint with no @PreAuthorize / @Secured / @RolesAllowed
 *  CORS_OPEN          — @CrossOrigin with no restrictions (open to all origins)
 *  NO_SECURITY_CONFIG — No SecurityConfig / WebSecurityConfigurerAdapter found at all
 */
@Service
public class SecurityAnalyzerService {

    private static final Set<String> SECURITY_ANNOTATIONS =
            Set.of("PreAuthorize", "Secured", "RolesAllowed");

    private static final Map<String, String> HTTP_MAPPINGS = Map.of(
            "GetMapping",    "GET",
            "PostMapping",   "POST",
            "PutMapping",    "PUT",
            "DeleteMapping", "DELETE",
            "PatchMapping",  "PATCH",
            "RequestMapping","ANY"
    );

    public List<SecurityIssue> analyze(List<CompilationUnit> units) {
        List<SecurityIssue> issues = new ArrayList<>();

        boolean hasSecurityConfig = detectSecurityConfig(units);

        if (!hasSecurityConfig) {
            issues.add(new SecurityIssue(
                    "NO_SECURITY_CONFIG",
                    "N/A", null, "N/A",
                    "No SecurityFilterChain / WebSecurityConfigurerAdapter detected. "
                            + "All endpoints may be publicly accessible."
            ));
        }

        for (CompilationUnit cu : units) {
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
                if (!isController(clazz)) return;

                String className = clazz.getNameAsString();
                String basePath = extractBasePath(clazz);

                // class-level @CrossOrigin without an origins restriction
                if (clazz.getAnnotationByName("CrossOrigin").isPresent()) {
                    issues.add(new SecurityIssue(
                            "CORS_OPEN",
                            className, null, basePath,
                            "@CrossOrigin on controller '" + className
                                    + "' — verify origins are restricted, not open wildcard."
                    ));
                }

                boolean classGuarded = hasSecurityAnnotation(clazz);

                for (MethodDeclaration method : clazz.getMethods()) {
                    if (!isEndpointMethod(method)) continue;

                    // method-level @CrossOrigin
                    if (method.getAnnotationByName("CrossOrigin").isPresent()) {
                        issues.add(new SecurityIssue(
                                "CORS_OPEN",
                                className,
                                method.getNameAsString(),
                                combinePaths(basePath, extractMethodPath(method)),
                                "@CrossOrigin on method '" + method.getNameAsString()
                                        + "' — verify origins restriction."
                        ));
                    }

                    if (!classGuarded && !hasSecurityAnnotation(method)) {
                        issues.add(new SecurityIssue(
                                "UNGUARDED_ENDPOINT",
                                className,
                                method.getNameAsString(),
                                combinePaths(basePath, extractMethodPath(method)),
                                "No @PreAuthorize / @Secured / @RolesAllowed on '"
                                        + method.getNameAsString()
                                        + "'. Endpoint may be accessible without authorization."
                        ));
                    }
                }
            });
        }

        return issues;
    }

    private boolean detectSecurityConfig(List<CompilationUnit> units) {
        for (CompilationUnit cu : units) {
            for (ClassOrInterfaceDeclaration clazz : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                String name = clazz.getNameAsString();
                // common naming patterns for security config
                if (name.contains("SecurityConfig") || name.contains("SecurityConfiguration")) {
                    return true;
                }
                // or a @Configuration bean with SecurityFilterChain bean method
                if (clazz.getAnnotationByName("Configuration").isPresent()) {
                    boolean hasFilterChain = clazz.getMethods().stream()
                            .anyMatch(m -> m.getTypeAsString().contains("SecurityFilterChain"));
                    if (hasFilterChain) return true;
                }
            }
        }
        return false;
    }

    private boolean isController(ClassOrInterfaceDeclaration clazz) {
        return clazz.getAnnotationByName("RestController").isPresent()
                || clazz.getAnnotationByName("Controller").isPresent();
    }

    private boolean isEndpointMethod(MethodDeclaration method) {
        return HTTP_MAPPINGS.keySet().stream()
                .anyMatch(ann -> method.getAnnotationByName(ann).isPresent());
    }

    private boolean hasSecurityAnnotation(com.github.javaparser.ast.nodeTypes.NodeWithAnnotations<?> node) {
        return SECURITY_ANNOTATIONS.stream()
                .anyMatch(ann -> node.getAnnotationByName(ann).isPresent());
    }

    private String extractBasePath(ClassOrInterfaceDeclaration clazz) {
        return clazz.getAnnotationByName("RequestMapping")
                .map(AnnotationUtils::extractPath)
                .orElse("");
    }

    private String extractMethodPath(MethodDeclaration method) {
        return HTTP_MAPPINGS.keySet().stream()
                .map(ann -> method.getAnnotationByName(ann))
                .filter(java.util.Optional::isPresent)
                .map(opt -> AnnotationUtils.extractPath(opt.get()))
                .findFirst()
                .orElse("");
    }

    private String combinePaths(String base, String path) {
        return (base + "/" + path).replaceAll("//+", "/").replaceAll("/$", "");
    }
}
