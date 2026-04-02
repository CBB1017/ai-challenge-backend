package com.brycenkorea.template.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Schema(
    description = "회원 응답", example = """
    {
      "id": 1,
      "name": "testuser",
      "email": "test@example.com",
      "slackMemberId": "asdasdasdasdwawsd"
    }
    """
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MemberResponse {
    private Long id;
    private String name;
    private String email;
    private String slackMemberId;
    private String position;
}

