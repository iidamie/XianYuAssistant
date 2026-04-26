package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KamiConfigRespDTO {

    private Long id;

    private Long xianyuAccountId;

    private String aliasName;

    private Integer deliveryMethod;

    private Integer allowRepeat;

    private Integer totalCount;

    private Integer usedCount;

    private Integer availableCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
