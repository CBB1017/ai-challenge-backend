package com.brycenkorea.template.mapper.mapstruct;

import com.brycenkorea.template.dto.request.AttendanceDto;
import com.brycenkorea.template.entity.Attendance;
import com.brycenkorea.template.entity.Member;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface AttendanceMapper {

    @Mapping(target = "position", source = "dto.position")
    Attendance toEntity(AttendanceDto dto, Member member, String attendanceDt);

    @Mapping(target = "id", ignore = true) // id는 덮어쓰지 않음
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    void updateEntity(@MappingTarget Attendance target, Attendance source);

}