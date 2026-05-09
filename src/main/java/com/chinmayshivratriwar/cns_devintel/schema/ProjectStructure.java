package com.chinmayshivratriwar.cns_devintel.schema;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class ProjectStructure {
    private int controllerCount;
    private int serviceCount;
    private int repositoryCount;
    private int entityCount;
    private int componentCount;
    private int configurationCount;
    private int totalClassCount;

    private List<String> controllers    = new ArrayList<>();
    private List<String> services       = new ArrayList<>();
    private List<String> repositories   = new ArrayList<>();
    private List<String> entities       = new ArrayList<>();

    private List<String> detectedProfiles   = new ArrayList<>();
    private List<String> mavenDependencies  = new ArrayList<>();  // from pom.xml if present
}
