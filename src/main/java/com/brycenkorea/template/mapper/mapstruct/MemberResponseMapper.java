package com.brycenkorea.template.mapper.mapstruct;


import com.brycenkorea.template.dto.response.MemberResponse;
import com.brycenkorea.template.entity.Member;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MemberResponseMapper {
    MemberResponse toResponse(Member entity);
    List<MemberResponse> toDtoList(List<Member> entities);
}