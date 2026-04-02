package com.brycenkorea.template.service;

import com.brycenkorea.template.contants.NotificationResult;
import com.brycenkorea.template.dto.NotificationSendDto;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackPushService {
    private final Slack slack = Slack.getInstance();
    private final NotificationService notificationService;

    @Value("${spring.slack.token}")
    private String slackToken;

    public Mono<Void> sendSlackPush(NotificationSendDto dto) {
        String slackInfo = dto.member().getSlackMemberId();

        // 1. 알림 발송 여부 확인 (비동기 결과에 따른 분기)
        return notificationService.shouldSendAlert(dto.member(), dto.status()).flatMap(shouldSend -> {
            if (!shouldSend) {
                return saveResult(dto, NotificationResult.SKIP, null);
            }

            if (StringUtils.isBlank(slackInfo)) {
                return saveResult(dto, NotificationResult.FAIL, "slackInfo is blank");
            }

            // 2. Slack 메시지 발송 (동기 SDK 호출을 비동기로 감쌈)
            return Mono.fromCallable(() -> {
                    MethodsClient methods = slack.methods(slackToken);
                    ChatPostMessageResponse res = methods.chatPostMessage(r -> r.channel(slackInfo).text(dto.message()));

                    if (!res.isOk()) {
                        throw new RuntimeException(res.getError());
                    }
                    return res;
                })
                .subscribeOn(Schedulers.boundedElastic()) // 블로킹 IO를 위한 전용 쓰레드 할당
                .flatMap(res -> saveResult(dto, NotificationResult.SUCCESS, null))
                .onErrorResume(e -> saveResult(dto, NotificationResult.FAIL, e.getMessage()));
        });
    }

    // 결과 저장을 위한 헬퍼 메서드
    private Mono<Void> saveResult(NotificationSendDto dto, NotificationResult result, String errorReason) {
        return notificationService.saveNotification(new NotificationSendDto(
            dto.member(),
            dto.status(),
            dto.message(),
            result,
            errorReason
        )).then();
    }
}