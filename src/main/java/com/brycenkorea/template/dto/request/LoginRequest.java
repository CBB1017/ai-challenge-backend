package com.brycenkorea.template.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Schema(
    description = "로그인 요청", example = """
    {
      "name": "testuser",
      "password": "test1234"
    }
    """
)
@Getter
@Setter
@ToString
public class LoginRequest {
    @NotBlank
    private String name;
    @NotBlank
    @Size(min = 8, max = 30)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
}