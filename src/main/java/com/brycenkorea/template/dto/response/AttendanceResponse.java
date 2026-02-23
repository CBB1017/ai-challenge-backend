package com.brycenkorea.template.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Schema(description = "근태 데이터 처리 응답", example = """
{
  "total": 10,
  "exe": 8,
  "failedMembers": [
    "문병찬대리", "홍길동신입사원"
  ]
}
""")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class AttendanceResponse {
    @Schema(description = "총 요청 건수", example = "10")
    private int total;

    @Schema(description = "저장 성공 건수", example = "8")
    private int exe;

    @Schema(description = "멤버를 못 찾은 경우 등", example = "[\"문병찬대리\", \"홍길동신입사원\"]")
    private List<String> failedMembers;
}
