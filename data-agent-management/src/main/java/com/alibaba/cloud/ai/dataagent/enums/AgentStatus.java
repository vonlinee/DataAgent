package com.alibaba.cloud.ai.dataagent.enums;

import lombok.Getter;

@Getter
public enum AgentStatus {

    PUBLISHED("published"),
    ;

    private final String status;
    
    AgentStatus(String status) {
        this.status = status;
    }

}
