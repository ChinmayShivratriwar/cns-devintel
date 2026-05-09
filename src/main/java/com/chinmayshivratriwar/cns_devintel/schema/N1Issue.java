package com.chinmayshivratriwar.cns_devintel.schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class N1Issue {
    private String serviceClass;
    private String method;
    private String repositoryCall;  // the repo method being called inside the loop
    private int    lineNumber;
    private String description;
}