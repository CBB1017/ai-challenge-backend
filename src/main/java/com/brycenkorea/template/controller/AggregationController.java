package com.brycenkorea.template.controller;

import com.brycenkorea.template.dto.AggregationCountDto;
import com.brycenkorea.template.dto.AggregationDto;
import com.brycenkorea.template.service.AggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/aggregation")
public class AggregationController {

    private final AggregationService aggregationService;

    @GetMapping("/daily")
    public Mono<ResponseEntity<AggregationDto>> readDaily(@RequestParam String date) {
        return aggregationService.getAggregatedDailyDataByDate(date).map(ResponseEntity::ok);
    }

    @GetMapping("/monthly")
    public Mono<ResponseEntity<AggregationCountDto>> readMonthly(@RequestParam String date) {
        return aggregationService.getAggregatedMonthlyDataByDate(date)
                                 .map(AggregationCountDto::from)
                                 .map(ResponseEntity::ok);
    }

    @GetMapping("/period")
    public Mono<ResponseEntity<AggregationCountDto>> readPeriod(@RequestParam String start, @RequestParam String end) {
        return aggregationService.getAggregatedPeriodDataByDate(start, end)
                                 .map(AggregationCountDto::from)
                                 .map(ResponseEntity::ok);
    }
}