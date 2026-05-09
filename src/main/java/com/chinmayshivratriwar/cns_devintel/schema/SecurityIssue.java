package com.chinmayshivratriwar.cns_devintel.schema;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SecurityIssue {
    private String issueType;       // UNGUARDED_ENDPOINT | CORS_OPEN | ACTUATOR_EXPOSED
    private String controllerClass;
    private String method;          // null for class-level issues
    private String path;
    private String description;
}
