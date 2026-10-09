package com.mo.swtp.master.tag.service;

import com.mo.swtp.master.tag.repository.TagRepository;
import com.mo.swtp.common.api.BusinessException;
import com.mo.swtp.common.api.CommonErrorCode;
import com.mo.swtp.master.support.UseYn;
import com.mo.swtp.master.tag.domain.Tag;
import com.mo.swtp.master.tag.dto.TagAddListRequest;
import com.mo.swtp.master.tag.dto.TagAddRequest;
import com.mo.swtp.master.tag.dto.TagModifyListRequest;
import com.mo.swtp.master.tag.dto.TagModifyRequest;
import com.mo.swtp.master.tag.dto.TagResponse;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 태그 CRUD.
 *
 * <p><b>목록 조회는 비어 있어도 예외를 던지지 않는다.</b> 지목한 자원이 없는 것(단건 조회·수정 대상)만
 * 404이고, 목록이 0건인 것은 정상 상태다 — 신규 정수장은 기준정보 0건에서 출발하므로
 * 프론트가 "아직 없음"과 "조회 실패"를 구분할 수 있어야 한다(05 「결정 2」).
 * 같은 앱의 {@code DrvmdChgService}가 이미 같은 근거를 적어 두고 있었다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {
    
    private final TagRepository tagRepository;
    
    
    /** 목록 조회 — 비어 있으면 빈 배열이다(404가 아니다) */
    public List<TagResponse> getTagList(UseYn useYn) {
        var tags = useYn != null ? tagRepository.findAllByUseYn(useYn) : tagRepository.findAll();
        return convert(tags, TagResponse::from);
    }
    
    /** 단건 조회 — 지목한 자원이 없는 것은 실제 오류이므로 404다 */
    public TagResponse getTagById(String tagSn) {
        Tag tag = getTagEntity(tagSn);
        return TagResponse.from(tag);
    }
    
    /** 태그타입별 목록 — 목록이므로 비어 있으면 빈 배열이다 */
    public List<TagResponse> getTagByTagTypeCd(String tagTypeCd) {
        return convert(tagRepository.findAllByTagTypeCd(tagTypeCd), TagResponse::from);
    }
    
    @Transactional
    public TagResponse addTag(TagAddRequest request) {
        Tag tag = new Tag(request.tagSn(), request.tagTypeCd(), request.useYn());
        return TagResponse.from(tagRepository.save(tag));
    }
    
    @Transactional
    public List<TagResponse> addTags(TagAddListRequest request) {
        List<Tag> tags = request.requests().stream()
                .map(item -> new Tag(item.tagSn(), item.tagTypeCd(), item.useYn()))
                .toList();
    
        return convert(tagRepository.saveAll(tags), TagResponse::from);
    }
    
    @Transactional
    public TagResponse modifyTag(TagModifyRequest request) {
        Tag tag = getTagEntity(request.tagSn());
        tag.replace(request.tagTypeCd(), request.useYn());
        return TagResponse.from(tag);
    }
    
    
    @Transactional
    public List<TagResponse> modifyTags(TagModifyListRequest request) {
        List<TagModifyRequest> requests = request.requests();
    
        List<String> tagSns = requests.stream()
                .map(TagModifyRequest::tagSn)
                .toList();
    
        List<Tag> tags = tagRepository.findAllById(tagSns);
    
        if (tags.size() != tagSns.size()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
    
        Map<String, TagModifyRequest> requestByTagSn = requests.stream()
                .collect(Collectors.toMap(TagModifyRequest::tagSn, Function.identity()));
    
        tags.forEach(tag -> {
            TagModifyRequest item = requestByTagSn.get(tag.getTagSn());
            tag.replace(item.tagTypeCd(), item.useYn());
        });
    
        return tags.stream()
                .map(TagResponse::from)
                .toList();
    }


    private <R> List<R> convert(List<Tag> tagList, Function<Tag, R> mapper) {
        return tagList.stream().map(mapper).toList();
    }
    
    private Tag getTagEntity(String tagSn) {
        return tagRepository.findById(tagSn)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
