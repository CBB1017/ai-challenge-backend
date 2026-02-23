package com.brycenkorea.template.dto.request.member;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class TeamWithMembersRequest {
    private String team;
    private List<MemberRequest> members;
}