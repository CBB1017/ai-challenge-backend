package com.brycenkorea.template.dto.request.member;

import com.brycenkorea.template.contants.YnType;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Schema(
    description = "회원 생성 요청", example = """
    {
      "name": "testMember2",
      "password": "test1234",
      "email": "test2@bry.co.kr"
      "firstLoginYn": "Y",
      "slackMemberId": "asdasdasdawddaasdsd"
      "position": "사원"
    }
    """
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MemberRequest {
    @NotBlank
    @JsonAlias("memberName")
    private String name;
    @NotBlank
    @Size(min = 8, max = 100)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    @NotBlank
    @Email
    private String email;
    @NotBlank
    private String position;
    @Size(max = 30)
    private String slackMemberId;
    @Builder.Default
    private YnType firstLoginYn = YnType.Y;
}