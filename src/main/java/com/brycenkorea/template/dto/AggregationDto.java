package com.brycenkorea.template.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
public class AggregationDto {
    List<String> missingStart;
    List<String> missingEnd;
    List<String> missingPlan;
}
