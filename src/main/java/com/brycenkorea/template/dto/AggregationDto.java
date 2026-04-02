package com.brycenkorea.template.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AggregationDto {
    List<String> missingStart;
    List<String> missingEnd;
    List<String> missingPlan;
}
