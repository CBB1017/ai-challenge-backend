package com.brycenkorea.template.entity;

import com.brycenkorea.template.contants.YnType;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Member extends BaseEntity {

    @Column(name = "email", length = 200, nullable = false, unique = true)
    private String email;

    @Column(name = "name", length = 200, nullable = false)
    private String name;           // 이름 + 직급

    @Column(name = "password", length = 200, nullable = false)
    private String password;

    @Column(name = "position", length = 30)
    private String position;

    @Column(name = "slack_member_id", length = 30, unique = true)
    private String slackMemberId;

    @Column(name = "first_login_yn", length = 1, nullable = false)
    @Enumerated(EnumType.STRING)
    private YnType firstLoginYn = YnType.Y;

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private List<Attendance> attendanceList = new ArrayList<>();

    public void addAttendance(Attendance attendance) {
        attendanceList.add(attendance);
        attendance.setMember(this);  // 양방향 관계 유지
    }

    public void removeAttendance(Attendance attendance) {
        attendanceList.remove(attendance);
        attendance.setMember(null);
    }
}
