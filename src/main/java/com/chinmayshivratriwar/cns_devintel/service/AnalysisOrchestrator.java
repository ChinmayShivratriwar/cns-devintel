package com.chinmayshivratriwar.cns_devintel.service;

import com.chinmayshivratriwar.cns_devintel.parser.JavaAstParser;
import com.chinmayshivratriwar.cns_devintel.schema.*;
import com.github.javaparser.ast.CompilationUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisOrchestrator {

    private final JavaAstParser        parser;
    private final EndpointMapperService   endpointMapper;
    private final N1DetectorService       n1Detector;
    private final TransactionalAnalyzerService transactionalAnalyzer;
    private final SecurityAnalyzerService securityAnalyzer;


    public List<Endpoint> mapEndpoints(Path projectRoot) {
        List<CompilationUnit> units = parse(projectRoot);
        return endpointMapper.map(units);
    }

    public List<N1Issue> detectN1Issues(Path projectRoot) {
        List<CompilationUnit> units = parse(projectRoot);
        return n1Detector.detect(units);
    }

    public List<TransactionalIssue> analyzeTransactional(Path projectRoot) {
        List<CompilationUnit> units = parse(projectRoot);
        return transactionalAnalyzer.analyze(units);
    }

    public List<SecurityIssue> analyzeSecurity(Path projectRoot) {
        List<CompilationUnit> units = parse(projectRoot);
        return securityAnalyzer.analyze(units);
    }

    public FullReport runFullAnalysis(Path projectRoot) {
        List<CompilationUnit> units = parse(projectRoot);

        return new FullReport(
                endpointMapper.map(units),
                n1Detector.detect(units),
                transactionalAnalyzer.analyze(units),
                securityAnalyzer.analyze(units)
        );
    }

    private List<CompilationUnit> parse(Path root) {
        // resolve src/main/java if it exists — that's where Spring code lives
        Path srcMain = root.resolve("src/main/java");
        Path parseRoot = (srcMain.toFile().exists()) ? srcMain : root;
        return parser.parseProject(parseRoot.toFile());
    }

    public record FullReport(
            List<Endpoint>           endpoints,
            List<N1Issue>            n1Issues,
            List<TransactionalIssue> transactionalIssues,
            List<SecurityIssue>      securityIssues
    ) {}
}