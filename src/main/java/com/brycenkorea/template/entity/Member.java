package com.brycenkorea.template.entity;

import com.brycenkorea.template.contants.YnType;
import lombok.*;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Table("member") // @Entity 대신 @Table 사용
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Member extends BaseEntity {

    @Column("email")
    private String email;

    @Column("name")
    private String name;

    @Column("password")
    private String password;

    @Column("position")
    private String position;

    @Column("slack_member_id")
    private String slackMemberId;

    @Column("first_login_yn")
    private YnType firstLoginYn = YnType.Y;

    // R2DBC는 @OneToMany를 지원하지 않습니다.
    // DB 컬럼이 아님을 명시하고, 필요 시 별도의 Service/Repository 레이어에서 채워줘야 합니다.
    @Transient
    private List<Attendance> attendanceList = new ArrayList<>();

    // 도메인 로직은 유지하되, JPA처럼 자동으로 DB에 반영되지 않음을 인지해야 합니다.
    public void addAttendance(Attendance attendance) {
        if (attendanceList == null) {
            attendanceList = new ArrayList<>();
        }
        attendanceList.add(attendance);
    }
}