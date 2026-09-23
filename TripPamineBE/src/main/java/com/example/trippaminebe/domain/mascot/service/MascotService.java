package com.example.trippaminebe.domain.mascot.service;

import com.example.trippaminebe.domain.mascot.dto.request.MascotCatchRequest;
import com.example.trippaminebe.domain.mascot.dto.request.MascotEncounterRequest;
import com.example.trippaminebe.domain.mascot.dto.request.MascotRequest;
import com.example.trippaminebe.domain.mascot.dto.request.MascotSpawnPointRequest;
import com.example.trippaminebe.domain.mascot.dto.request.RegionRequest;
import com.example.trippaminebe.domain.mascot.dto.response.MascotCatchResponse;
import com.example.trippaminebe.domain.mascot.dto.response.MascotEncounterResponse;
import com.example.trippaminebe.domain.mascot.dto.response.MascotResponse;
import com.example.trippaminebe.domain.mascot.dto.response.MascotSpawnPointResponse;
import com.example.trippaminebe.domain.mascot.dto.response.RegionResponse;
import com.example.trippaminebe.domain.mascot.dto.response.UserMascotCollectionResponse;
import com.example.trippaminebe.domain.mascot.entity.Mascot;
import com.example.trippaminebe.domain.mascot.entity.MascotRarity;
import com.example.trippaminebe.domain.mascot.entity.MascotSpawnPoint;
import com.example.trippaminebe.domain.mascot.entity.Region;
import com.example.trippaminebe.domain.mascot.entity.UserMascotCollection;
import com.example.trippaminebe.domain.mascot.exception.MascotLocationException;
import com.example.trippaminebe.domain.mascot.exception.MascotSpawnNotFoundException;
import com.example.trippaminebe.domain.mascot.repository.MascotRepository;
import com.example.trippaminebe.domain.mascot.repository.MascotSpawnPointRepository;
import com.example.trippaminebe.domain.mascot.repository.RegionRepository;
import com.example.trippaminebe.domain.mascot.repository.UserMascotCollectionRepository;
import com.example.trippaminebe.domain.quest.service.GeoUtils;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

// quest.service.QuestService와 완전히 같은 뼈대(반경 검증 -> 성공/실패 -> 포인트 지급)를 따르되,
// "조우(encounter) -> (안드로이드 의사 AR 미니게임) -> 확정(catch)" 2단계로 나눈 것이 다른 점이다.
// GeoUtils.distanceMeters()는 quest 도메인 것을 그대로 재사용한다(별도 구현 없음).
@Service
@RequiredArgsConstructor
@Transactional
public class MascotService {

    // quest.service.QuestService.MAX_ACCEPTABLE_ACCURACY_METERS와 동일한 기준을 그대로 사용한다.
    private static final double MAX_ACCEPTABLE_ACCURACY_METERS = 200.0;

    private final RegionRepository regionRepository;
    private final MascotRepository mascotRepository;
    private final MascotSpawnPointRepository spawnPointRepository;
    private final UserMascotCollectionRepository collectionRepository;
    private final UserRepository userRepository;
    private final EncounterTokenStore encounterTokenStore;

    // ===================== 사용자용: 지도/조우/포획/도감 =====================

    @Transactional(readOnly = true)
    public List<MascotSpawnPointResponse> findNearby(Long userId, BigDecimal lat,
                                                       BigDecimal lng, Double radiusKm) {
        List<MascotSpawnPoint> spawnPoints = spawnPointRepository.findByActiveYn("Y");

        return spawnPoints.stream()
                .filter(spawn -> {
                    if (lat == null || lng == null || radiusKm == null) return true;
                    double distance = GeoUtils.distanceMeters(lat, lng, spawn.getTargetLat(), spawn.getTargetLng());
                    return distance <= radiusKm * 1000;
                })
                .map(spawn -> MascotSpawnPointResponse.from(
                        spawn,
                        collectionRepository.existsByUser_IdAndMascot_MascotId(userId, spawn.getMascot().getMascotId())
                ))
                .toList();
    }

    // 1단계: "지금 이 위치에서 이 스폰 지점을 조우할 수 있는가" 검증. 통과하면 단기 토큰을 발급한다.
    // 이 토큰이 프론트 -> androidBridge.startArCatch(...) -> 의사 AR 화면으로 전달되고,
    // 미니게임이 끝나면 아래 catchMascot()에서 다시 검증된다.
    public MascotEncounterResponse encounter(Long userId, Long spawnId, MascotEncounterRequest request) {
        MascotSpawnPoint spawn = getSpawnOrThrow(spawnId);

        if (request.getAccuracyMeters() != null && request.getAccuracyMeters() > MAX_ACCEPTABLE_ACCURACY_METERS) {
            throw new MascotLocationException(
                    "GPS 신호가 약합니다. 실외의 개방된 장소에서 다시 시도해주세요. (오차 반경: "
                            + request.getAccuracyMeters() + "m)"
            );
        }

        double distance = GeoUtils.distanceMeters(
                spawn.getTargetLat(), spawn.getTargetLng(),
                request.getCurrentLat(), request.getCurrentLng()
        );

        if (distance > spawn.getCatchRadius()) {
            throw new MascotLocationException(
                    "마스코트와 아직 너무 멀어요. 조금 더 가까이 다가가주세요. (남은 거리 약 "
                            + Math.round(distance - spawn.getCatchRadius()) + "m)"
            );
        }

        Mascot mascot = spawn.getMascot();
        String token = encounterTokenStore.issue(userId, spawnId);

        return MascotEncounterResponse.builder()
                .encounterToken(token)
                .expiresInSeconds(encounterTokenStore.ttlSeconds())
                .spawnId(spawnId)
                .mascotId(mascot.getMascotId())
                .mascotName(mascot.getMascotName())
                .mascotRarity(mascot.getRarity().name())
                .mascotImageUrl(mascot.getImageUrl())
                .rewardPoint(mascot.getRewardPoint())
                .build();
    }

    // 2단계: 의사 AR 화면(안드로이드 ArCatchActivity)에서 포획 미니게임 결과가 나온 뒤 최종 확정.
    // encounterToken을 검증해서 "실제로 방금 그 위치에서 조우한 세션이 맞는지" 재확인한다.
    public MascotCatchResponse catchMascot(Long userId, Long spawnId, MascotCatchRequest request) {
        MascotSpawnPoint spawn = getSpawnOrThrow(spawnId);
        EncounterTokenStore.EncounterSession session =
                encounterTokenStore.validate(request.getEncounterToken(), userId, spawnId);

        Mascot mascot = spawn.getMascot();

        if (!request.isSuccess()) {
            // 실패면 토큰을 소모하지 않아서, TTL이 남아있는 동안은 같은 조우로 재도전할 수 있다.
            return MascotCatchResponse.builder()
                    .caught(false)
                    .mascotId(mascot.getMascotId())
                    .mascotName(mascot.getMascotName())
                    .rewardPoint(0L)
                    .totalPoints(userRepository.findById(userId)
                            .map(User::getTotalPoints)
                            .orElse(0L))
                    .build();
        }

        encounterTokenStore.invalidate(request.getEncounterToken());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id=" + userId));

        UserMascotCollection collection = UserMascotCollection.of(
                user, mascot, spawnId,
                spawn.getTargetLat(), spawn.getTargetLng()
        );
        collectionRepository.save(collection);

        Long rewardPoint = mascot.getRewardPoint() != null ? mascot.getRewardPoint() : 0L;
        user.setTotalPoints(user.getTotalPoints() + rewardPoint);

        return MascotCatchResponse.builder()
                .caught(true)
                .mascotId(mascot.getMascotId())
                .mascotName(mascot.getMascotName())
                .rewardPoint(rewardPoint)
                .totalPoints(user.getTotalPoints())
                .build();
    }

    @Transactional(readOnly = true)
    public List<UserMascotCollectionResponse> findMyCollection(Long userId) {
        return collectionRepository.findByUser_IdOrderByCaughtAtDesc(userId).stream()
                .map(UserMascotCollectionResponse::from)
                .toList();
    }

    // ===================== 관리자용: 지역/마스코트/스폰 지점 CRUD =====================

    @Transactional(readOnly = true)
    public List<RegionResponse> findAllRegions() {
        return regionRepository.findAll().stream().map(RegionResponse::from).toList();
    }

    public RegionResponse createRegion(RegionRequest request) {
        Region region = Region.builder()
                .regionName(request.getRegionName())
                .sidoCode(request.getSidoCode())
                .build();
        return RegionResponse.from(regionRepository.save(region));
    }

    public RegionResponse updateRegion(Long regionId, RegionRequest request) {
        Region region = regionRepository.findById(regionId)
                .orElseThrow(() -> new MascotSpawnNotFoundException("존재하지 않는 지역입니다. id=" + regionId));
        region.update(request.getRegionName(), request.getSidoCode());
        return RegionResponse.from(region);
    }

    @Transactional(readOnly = true)
    public List<MascotResponse> findAllMascots() {
        return mascotRepository.findAll().stream().map(MascotResponse::from).toList();
    }

    public MascotResponse createMascot(MascotRequest request) {
        Region region = regionRepository.findById(request.getRegionId())
                .orElseThrow(() -> new MascotSpawnNotFoundException("존재하지 않는 지역입니다. id=" + request.getRegionId()));

        Mascot mascot = Mascot.builder()
                .region(region)
                .mascotName(request.getMascotName())
                .rarity(request.getRarity() != null ? request.getRarity() : MascotRarity.COMMON)
                .imageUrl(request.getImageUrl())
                .modelUrl(request.getModelUrl())
                .rewardPoint(request.getRewardPoint() != null ? request.getRewardPoint() : 0L)
                .build();

        return MascotResponse.from(mascotRepository.save(mascot));
    }

    public MascotResponse updateMascot(Long mascotId, MascotRequest request) {
        Mascot mascot = mascotRepository.findById(mascotId)
                .orElseThrow(() -> new MascotSpawnNotFoundException("존재하지 않는 마스코트입니다. id=" + mascotId));

        Region region = regionRepository.findById(request.getRegionId())
                .orElseThrow(() -> new MascotSpawnNotFoundException("존재하지 않는 지역입니다. id=" + request.getRegionId()));

        mascot.update(
                region,
                request.getMascotName(),
                request.getRarity() != null ? request.getRarity() : mascot.getRarity(),
                request.getImageUrl(),
                request.getModelUrl(),
                request.getRewardPoint() != null ? request.getRewardPoint() : mascot.getRewardPoint()
        );
        return MascotResponse.from(mascot);
    }

    @Transactional(readOnly = true)
    public List<MascotSpawnPointResponse> findAllSpawnPointsForAdmin() {
        return spawnPointRepository.findAll().stream()
                .map(spawn -> MascotSpawnPointResponse.from(spawn, false))
                .toList();
    }

    public MascotSpawnPointResponse createSpawnPoint(MascotSpawnPointRequest request) {
        Mascot mascot = mascotRepository.findById(request.getMascotId())
                .orElseThrow(() -> new MascotSpawnNotFoundException("존재하지 않는 마스코트입니다. id=" + request.getMascotId()));

        MascotSpawnPoint spawnPoint = MascotSpawnPoint.builder()
                .mascot(mascot)
                .spotName(request.getSpotName())
                .targetLat(request.getTargetLat())
                .targetLng(request.getTargetLng())
                .catchRadius(request.getCatchRadius() != null
                        ? request.getCatchRadius()
                        : MascotSpawnPoint.DEFAULT_CATCH_RADIUS_METERS)
                .linkedQuestId(request.getLinkedQuestId())
                .activeYn(request.getActiveYn() != null ? request.getActiveYn() : "Y")
                .build();

        return MascotSpawnPointResponse.from(spawnPointRepository.save(spawnPoint), false);
    }

    public MascotSpawnPointResponse updateSpawnPoint(Long spawnId, MascotSpawnPointRequest request) {
        MascotSpawnPoint spawnPoint = getSpawnOrThrow(spawnId);

        spawnPoint.update(
                request.getSpotName(),
                request.getTargetLat(),
                request.getTargetLng(),
                request.getCatchRadius() != null ? request.getCatchRadius() : spawnPoint.getCatchRadius(),
                request.getLinkedQuestId(),
                request.getActiveYn() != null ? request.getActiveYn() : spawnPoint.getActiveYn()
        );
        return MascotSpawnPointResponse.from(spawnPoint, false);
    }

    public void deleteSpawnPoint(Long spawnId) {
        MascotSpawnPoint spawnPoint = getSpawnOrThrow(spawnId);
        spawnPointRepository.delete(spawnPoint);
    }

    // ===================== 내부 헬퍼 =====================

    private MascotSpawnPoint getSpawnOrThrow(Long spawnId) {
        return spawnPointRepository.findById(spawnId)
                .orElseThrow(() -> new MascotSpawnNotFoundException("존재하지 않는 스폰 지점입니다. id=" + spawnId));
    }
}
