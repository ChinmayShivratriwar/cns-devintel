package com.chinmayshivratriwar.cns_devintel.schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Data
@Getter
@Setter
public class Endpoint {
    private String method;
    private String path;
    private String controller;
    private String handler;
}
