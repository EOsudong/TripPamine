package com.example.trippaminebe.domain.nego.service;

import com.example.trippaminebe.domain.nego.dto.response.AiNegoLogResponse;
import com.example.trippaminebe.domain.nego.entity.AiNegoLog;
import com.example.trippaminebe.domain.nego.repository.AiNegoLogRepository;
import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AiNegoService {

    private static final Logger log = LoggerFactory.getLogger(AiNegoService.class);

    // DDL 주석 기준: 발송 시점 + 60초 후 카운트다운 만료
    private static final long OFFER_TTL_SECONDS = 60;

    // 출발 임박 블라인드/일반 여행에 대한 임시 할인율 (실 서비스에서는 상품/재고 API 연동으로 대체)
    private static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.30");

    private final AiNegoLogRepository aiNegoLogRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AiNegoLogResponse> findActiveOffers(Long userId) {
        return aiNegoLogRepository
            .findByUser_IdAndExpiredAtAfterOrderByNegoIdDesc(userId, LocalDateTime.now())
            .stream()
            .map(AiNegoLogResponse::from)
            .toList();
    }

    // 시간 내 결제 완료 처리 (전환)
    public AiNegoLogResponse accept(Long userId, Long negoId) {
        AiNegoLog offer = aiNegoLogRepository.findById(negoId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 네고 제안입니다. id=" + negoId));

        if (!offer.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("본인에게 발송된 제안만 수락할 수 있습니다.");
        }
        if (offer.isExpired()) {
            throw new IllegalArgumentException("이미 만료된 제안입니다.");
        }

        offer.convert();
        return AiNegoLogResponse.from(offer);
    }

    // 출발 임박 여행 계획(TravelPlan) 하나에 대해 AI 핫딜 제안을 1건 생성
    // - 이미 유효한 제안이 있으면 중복 발송하지 않음
    public void generateOfferForPlan(TravelPlan plan) {
        User user = userRepository.findById(plan.getUser().getId())
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id=" + plan.getUser().getId()));

        String itemName = plan.getPlanName() + " 막판 특가 패키지";

        boolean alreadyOffered = aiNegoLogRepository
            .findFirstByUser_IdAndItemNameAndExpiredAtAfter(itemName, user.getId(), LocalDateTime.now())
            .isPresent();
        if (alreadyOffered) {
            return;
        }

        BigDecimal offeredPrice = plan.getTotalBudget()
            .multiply(BigDecimal.ONE.subtract(DISCOUNT_RATE))
            .setScale(0, RoundingMode.HALF_UP);

        AiNegoLog offer = AiNegoLog.builder()
            .user(user)
            .itemName(itemName)
            .offeredPrice(offeredPrice)
            .expiredAt(LocalDateTime.now().plusSeconds(OFFER_TTL_SECONDS))
            .conversionYn("N")
            .build();

        aiNegoLogRepository.save(offer);
        log.info("AI 타임 네고 발송: userId={}, item={}, offeredPrice={}", user.getId(), itemName, offeredPrice);
    }
}
