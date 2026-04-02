package com.brycenkorea.template.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Setter
public class AggregationCountDto {
    Map<String, Long> missingStartCount;
    Map<String, Long> missingEndCount;
    Map<String, Long> missingPlanCount;

    public static AggregationCountDto from(AggregationDto dto) {
        AggregationCountDto result = new AggregationCountDto();

        result.setMissingStartCount(dto.getMissingStart()
            .stream()
            .collect(Collectors.groupingBy(name -> name, Collectors.counting())));

        result.setMissingEndCount(dto.getMissingEnd()
            .stream()
            .collect(Collectors.groupingBy(name -> name, Collectors.counting())));

        return result;
    }
}
