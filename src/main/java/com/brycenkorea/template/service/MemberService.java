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
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder sha256PasswordEncoder;
    private final MemberRequestMapper memberRequestMapper;

    // 1. 신규 저장 (비동기)
    public Mono<Member> save(MemberRequest request) {
        return memberRepository.findByName(request.getName())
                               .flatMap(m -> Mono.<Member>error(new ApiException(ApiResultCode.MEMBER_ALREADY_EXISTS)))
                               .switchIfEmpty(Mono.defer(() -> {
                                   Member member = memberRequestMapper.toEntity(request);
                                   member.setPassword(PasswordUtil.encodeIfNeeded(request.getPassword(), sha256PasswordEncoder));
                                   return memberRepository.save(member);
                               }));
    }

    // 2. 업데이트 (Dirty Check가 없으므로 명시적 save 필요)
    @Transactional
    public Mono<Member> update(Long id, MemberRequest dto) {
        return memberRepository.findById(id)
                               .switchIfEmpty(Mono.error(new ApiException(ApiResultCode.MEMBER_NOT_FOUND)))
                               .flatMap(current -> memberRepository.findByName(dto.getName())
                                                                   .filter(sameMember -> !sameMember.getId().equals(id))
                                                                   .flatMap(m -> Mono.<Member>error(new ApiException(ApiResultCode.MEMBER_ALREADY_EXISTS)))
                                                                   .switchIfEmpty(Mono.defer(() -> {
                                                                       memberRequestMapper.updateEntityFromDto(dto, current);
                                                                       if (StringUtils.isNotBlank(dto.getPassword())) {
                                                                           current.setPassword(PasswordUtil.encodeIfNeeded(dto.getPassword(), sha256PasswordEncoder));
                                                                       }
                                                                       return memberRepository.save(current);
                                                                   }))
                               );
    }

    public Mono<Member> findById(Long id) {
        return memberRepository.findById(id)
                               .switchIfEmpty(Mono.error(new ApiException(ApiResultCode.MEMBER_NOT_FOUND, id)));
    }

    public Flux<Member> findAll() {
        return memberRepository.findAll();
    }

    // R2DBC에서 페이징은 Pageable을 넘겨 Flux로 받습니다.
    public Flux<Member> findAllWithPage(Pageable pageable) {
        return memberRepository.findAllBy(pageable);
    }

    public Mono<Member> findByName(String name) {
        return memberRepository.findByName(name)
                               .switchIfEmpty(Mono.error(new ApiException(ApiResultCode.MEMBER_NOT_FOUND)));
    }

    @Transactional
    public Mono<Void> deleteById(Long id) {
        return memberRepository.existsById(id)
                               .flatMap(exists -> {
                                   if (!exists) return Mono.error(new ApiException(ApiResultCode.MEMBER_NOT_FOUND, id));
                                   return memberRepository.deleteById(id);
                               });
    }

    // 3. 이메일 기준 Upsert (BulkImport 등에서 사용)
    @Transactional
    public Mono<Member> saveOrUpdateByEmail(MemberRequest dto) {
        if (StringUtils.isBlank(dto.getEmail())) {
            return Mono.error(new ApiException(ApiResultCode.INVALID_PARAMETER, "이메일 없음"));
        }

        return memberRepository.findByEmail(dto.getEmail())
                               .flatMap(existing -> {
                                   // Update 로직
                                   memberRequestMapper.updateEntityFromDto(dto, existing);
                                   if (StringUtils.isNotBlank(dto.getPassword())) {
                                       existing.setPassword(PasswordUtil.encodeIfNeeded(dto.getPassword(), sha256PasswordEncoder));
                                   }
                                   return memberRepository.save(existing);
                               })
                               .switchIfEmpty(Mono.defer(() -> {
                                   // Insert 로직
                                   if (StringUtils.isBlank(dto.getPassword())) {
                                       dto.setPassword(PasswordUtil.generatePatternPassword(dto.getName(), dto.getEmail()));
                                   }
                                   Member member = memberRequestMapper.toEntity(dto);
                                   member.setPassword(PasswordUtil.encodeIfNeeded(dto.getPassword(), sha256PasswordEncoder));
                                   return memberRepository.save(member);
                               }));
    }

    public Flux<Member> findAllWithSearch(Pageable pageable, String keyword) {
        return memberRepository.searchByKeyword(keyword, pageable);
    }

    /**
     * 이름 리스트와 직급 리스트에 해당하는 멤버들을 Flux로 반환합니다.
     */
    public Flux<Member> findAllByNameInAndPositionIn(Set<String> names, Set<String> positions) {
        if (CollectionUtils.isEmpty(names) || CollectionUtils.isEmpty(positions)) {
            return Flux.empty();
        }
        return memberRepository.findAllByNameInAndPositionIn(names, positions);
    }
}