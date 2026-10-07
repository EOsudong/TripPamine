package com.example.trippaminebe.domain.quest.service;

import com.example.trippaminebe.domain.quest.dto.request.TitleRequest;
import com.example.trippaminebe.domain.quest.dto.response.TitleResponse;
import com.example.trippaminebe.domain.quest.entity.Title;
import com.example.trippaminebe.domain.quest.exception.TitleNotFoundException;
import com.example.trippaminebe.domain.quest.repository.TitleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 칭호(TITLES) 마스터 데이터 관리자 CRUD. QuestChapterService와 동일한 패턴.
// 실제 유저의 칭호 보유/장착(USER_TITLES, USER_RPG_STATS.CURRENT_TITLE_ID)은 RpgStatsService가 담당하고,
// 이 Service는 "어떤 칭호가 존재하고 어떤 조건으로 자동 지급되는지"의 마스터 데이터만 다룬다.
@Service
@RequiredArgsConstructor
@Transactional
public class TitleService {

    private final TitleRepository titleRepository;

    @Transactional(readOnly = true)
    public List<TitleResponse> findAll() {
        return titleRepository.findAllByOrderByTitleIdAsc()
            .stream()
            .map(TitleResponse::from)
            .toList();
    }

    public TitleResponse create(TitleRequest request) {
        Title title = Title.builder()
            .titleName(request.getTitleName())
            .description(request.getDescription())
            .iconUrl(request.getIconUrl())
            .requiredConditionType(request.getRequiredConditionType())
            .requiredConditionValue(request.getRequiredConditionValue())
            .build();

        return TitleResponse.from(titleRepository.save(title));
    }

    public TitleResponse update(Long titleId, TitleRequest request) {
        Title title = getTitleOrThrow(titleId);

        title.update(
            request.getTitleName(),
            request.getDescription(),
            request.getIconUrl(),
            request.getRequiredConditionType(),
            request.getRequiredConditionValue()
        );

        return TitleResponse.from(title);
    }

    public void delete(Long titleId) {
        Title title = getTitleOrThrow(titleId);

        try {
            titleRepository.delete(title);
            titleRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // 이미 이 칭호를 획득했거나(USER_TITLES) 현재 장착 중인(USER_RPG_STATS.CURRENT_TITLE_ID)
            // 유저가 있으면 FK 제약으로 삭제할 수 없다.
            throw new IllegalArgumentException(
                "이 칭호를 이미 획득했거나 장착 중인 사용자가 있어 삭제할 수 없습니다."
            );
        }
    }

    private Title getTitleOrThrow(Long titleId) {
        return titleRepository.findById(titleId)
            .orElseThrow(() -> new TitleNotFoundException("존재하지 않는 칭호입니다. id=" + titleId));
    }
}
