package com.brycenkorea.template.service;

import com.brycenkorea.template.dto.api.ApiResultCode;
import com.brycenkorea.template.dto.request.member.MemberRequest;
import com.brycenkorea.template.entity.Member;
import com.brycenkorea.template.exception.ApiException;
import com.brycenkorea.template.mapper.mapstruct.MemberRequestMapper;
import com.brycenkorea.template.repository.MemberRepository;
import com.brycenkorea.template.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder sha256PasswordEncoder;
    private final MemberRequestMapper memberRequestMapper;
    // JPA 방식
    public Member save(MemberRequest userCreateRequest) {
        // username 중복 체크
        if (memberRepository.findByName(userCreateRequest.getName()).isPresent()) {
            throw new ApiException(ApiResultCode.MEMBER_ALREADY_EXISTS);
        }
        Member member = memberRequestMapper.toEntity(userCreateRequest);
        member.setPassword(PasswordUtil.encodeIfNeeded(userCreateRequest.getPassword(), sha256PasswordEncoder));
        return memberRepository.save(member);
    }

    @Transactional
    public Member update(Long id, MemberRequest dto) {
        Member current = memberRepository.findById(id)
                                         .orElseThrow(() -> new ApiException(ApiResultCode.MEMBER_NOT_FOUND));

        Optional<Member> sameMember = memberRepository.findByName(dto.getName());
        if (sameMember.isPresent() && !sameMember.get().getId().equals(id)) {
            throw new ApiException(ApiResultCode.MEMBER_ALREADY_EXISTS);
        }

        // MapStruct로 부분 업데이트
        memberRequestMapper.updateEntityFromDto(dto, current);
        if (dto.getPassword() != null) {
            current.setPassword(PasswordUtil.encodeIfNeeded(dto.getPassword(), sha256PasswordEncoder));
        }

        return memberRepository.save(current);
    }

    public Member findById(Long id) {
        return memberRepository.findById(id).orElseThrow(() -> new ApiException(ApiResultCode.MEMBER_NOT_FOUND, id));
    }

    public List<Member> findAll() {
        return memberRepository.findAll();
    }

    public Page<Member> findAllWithPage(Pageable pageable) {
        return memberRepository.findAll(pageable);
    }

    public Page<Member> findAllWithSearch(Pageable pageable, String keyword) {
        return memberRepository.searchByKeyword(keyword, pageable);
    }

    public Member findByName(String name) {
        return memberRepository.findByName(name).orElseThrow(() -> new ApiException(ApiResultCode.MEMBER_NOT_FOUND));
    }

    public void deleteById(Long id) {
        if (!memberRepository.existsById(id)) {
            throw new ApiException(ApiResultCode.MEMBER_NOT_FOUND, id);
        }
        memberRepository.deleteById(id);
    }

    // 이메일로 신규 또는 업데이트(이메일이 PK인 셈)
    @Transactional
    public void saveOrUpdateByEmail(MemberRequest dto) {
        if (dto.getEmail() == null || dto.getEmail().trim().isEmpty()) {
            throw new ApiException(ApiResultCode.INVALID_PARAMETER, "이메일 없음");
        }

        Optional<Member> existing = memberRepository.findByEmail(dto.getEmail());

        if (existing.isPresent()) {
            // 기존 멤버는 update (패스워드 등 정책 적용)
            Member current = existing.get();
            memberRequestMapper.updateEntityFromDto(dto, current);
            if (StringUtils.isNotBlank(dto.getPassword())) {
                current.setPassword(PasswordUtil.encodeIfNeeded(dto.getPassword(), sha256PasswordEncoder));
            }
        } else {
            // 신규 멤버는 저장
            if (dto.getPassword() == null || dto.getPassword().trim().isEmpty()) {
                String generated = PasswordUtil.generatePatternPassword(dto.getName(), dto.getEmail());
                dto.setPassword(generated);
            }
            Member member = memberRequestMapper.toEntity(dto);
            member.setPassword(PasswordUtil.encodeIfNeeded(dto.getPassword(), sha256PasswordEncoder));
            memberRepository.save(member);
        }
    }
}
