package com.chinmayshivratriwar.cns_devintel.runner;

import com.chinmayshivratriwar.cns_devintel.parser.JavaAstParser;
import com.chinmayshivratriwar.cns_devintel.schema.*;
import com.chinmayshivratriwar.cns_devintel.service.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;

public class TestRunner {

    public static void main(String[] args) throws JsonProcessingException {

        Path projectPath = Path.of(
                "E:/Academics/Projects/Spring Boot Projects/expense-tracker/expense-tracker"
        );


        JavaAstParser parser = new JavaAstParser();

        EndpointMapperService endpointMapperService =
                new EndpointMapperService();

        N1DetectorService n1DetectorService =
                new N1DetectorService();

        TransactionalAnalyzerService transactionalAnalyzerService =
                new TransactionalAnalyzerService();

        SecurityAnalyzerService securityAnalyzerService =
                new SecurityAnalyzerService();

        AnalysisOrchestrator orchestrator =
                new AnalysisOrchestrator(
                        parser,
                        endpointMapperService,
                        n1DetectorService,
                        transactionalAnalyzerService,
                        securityAnalyzerService
                );

        ObjectMapper mapper = new ObjectMapper();

        //Report seen below is the final report which will be used for generating insights and recommendations
        AnalysisOrchestrator.FullReport report =
                orchestrator.runFullAnalysis(projectPath);

        System.out.println("\n========= FULL REPORT =========\n");

        System.out.println(
                mapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(report)
        );
    }
}