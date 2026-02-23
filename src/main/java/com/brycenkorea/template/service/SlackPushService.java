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


@Slf4j
@Service
@RequiredArgsConstructor
public class SlackPushService {
    private final Slack slack = Slack.getInstance();
    private final NotificationService notificationService;
    @Value("${spring.slack.token}")
    private String slackToken;

    public void sendSlackPush(NotificationSendDto dto) {
        NotificationResult result = NotificationResult.SUCCESS;
        String errorReason = null;
        String slackInfo = dto.member().getSlackMemberId();

        if(!notificationService.shouldSendAlert(dto.member(), dto.status())){
            result = NotificationResult.SKIP;
        }else if (StringUtils.isBlank(slackInfo)) {
            result = NotificationResult.FAIL;
            errorReason = "slackInfo is blank";
        } else {
            try {
                MethodsClient methods = slack.methods(slackToken);
                ChatPostMessageResponse res = methods.chatPostMessage(r -> r.channel(slackInfo).text(dto.message()));
                if (!res.isOk()) {
                    result = NotificationResult.FAIL;
                    errorReason = res.getError();
                }
            } catch (Exception e) {
                result = NotificationResult.FAIL;
                errorReason = e.getMessage();
            }
        }
        notificationService.saveNotification(new NotificationSendDto(
            dto.member(),
            dto.status(),
            dto.message(),
            result,
            errorReason
        ));
    }
}
