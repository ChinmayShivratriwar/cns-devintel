package com.chinmayshivratriwar.cns_devintel.schema;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionalIssue {
    private String serviceClass;
    private String method;
    private String repositoryCallFound;  // the repo call that triggered the flag
    private int    lineNumber;
    private String description;
}
