package com.alibaba.cloud.ai.dataagent.enums;

import lombok.Getter;

@Getter
public enum TableType {

    TABLE("TABLE"),
    VIEW("VIEW");

    private final String type;

    TableType(String type) {
        this.type = type;
    }
}
