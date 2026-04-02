package com.brycenkorea.template.mapper.mapstruct;

import com.brycenkorea.template.dto.request.member.MemberRequest;
import com.brycenkorea.template.entity.Member;
import org.mapstruct.*;

@Mapper(componentModel = "spring")

public interface MemberRequestMapper {
    @Mapping(target = "id", ignore = true)
    Member toEntity(MemberRequest dto);

    // PATCH: null은 무시(기존 값 유지)
    @Mapping(target = "id", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(MemberRequest dto, @MappingTarget Member entity);

    // PUT: null도 덮어씀(초기화)
    @Mapping(target = "id", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    void overwriteEntityFromDto(MemberRequest dto, @MappingTarget Member entity);
}
