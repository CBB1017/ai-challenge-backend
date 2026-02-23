package com.brycenkorea.template.controller;

import com.brycenkorea.template.dto.AggregationDto;
import com.brycenkorea.template.dto.AggregationCountDto;
import com.brycenkorea.template.service.AggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/aggregation")
public class AggregationController {

    private final AggregationService aggregationService;

    @GetMapping("/daily")
    public ResponseEntity<AggregationDto> readDaily(@RequestParam String date) {
        AggregationDto result = aggregationService.getAggregatedDailyDataByDate(date);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/monthly")
    public ResponseEntity<AggregationCountDto> readMonthly(@RequestParam String date) {
        AggregationCountDto result = AggregationCountDto.from(aggregationService.getAggregatedMonthlyDataByDate(date));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/period")
    public ResponseEntity<AggregationCountDto> readMonthly(@RequestParam String start, @RequestParam String end) {
        AggregationCountDto result = AggregationCountDto.from(aggregationService.getAggregatedPeriodDataByDate(start, end));
        return ResponseEntity.ok(result);
    }

}
