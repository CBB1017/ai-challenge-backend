package com.brycenkorea.template.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Schema(
    description = "로그인 요청", example = """
    {
      "userId": "testuser",
      "password": "test1234"
    }
    """
)
@Getter
@Setter
@ToString
public class LoginRequest {
    @NotBlank
    private String userId;
    @NotBlank
    @Size(min = 8, max = 30)
    private String password;
}