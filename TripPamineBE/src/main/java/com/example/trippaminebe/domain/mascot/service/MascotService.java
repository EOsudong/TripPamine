package com.example.trippaminebe.domain.mascot.service;

import com.example.trippaminebe.domain.mascot.dto.request.MascotCatchRequest;
import com.example.trippaminebe.domain.mascot.dto.request.MascotEncounterRequest;
import com.example.trippaminebe.domain.mascot.dto.request.MascotRequest;
import com.example.trippaminebe.domain.mascot.dto.request.MascotSpawnPointRequest;
import com.example.trippaminebe.domain.mascot.dto.request.RegionRequest;
import com.example.trippaminebe.domain.mascot.dto.response.MascotCatchResponse;
import com.example.trippaminebe.domain.mascot.dto.response.MascotDexResponse;
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
import com.example.trippaminebe.domain.mascot.exception.EncounterTokenInvalidException;
import com.example.trippaminebe.domain.mascot.exception.MascotDeleteConflictException;
import com.example.trippaminebe.domain.mascot.exception.MascotLocationException;
import com.example.trippaminebe.domain.mascot.exception.MascotLockedException;
import com.example.trippaminebe.domain.mascot.exception.MascotSpawnNotFoundException;
import com.example.trippaminebe.domain.mascot.repository.MascotCatchStat;
import com.example.trippaminebe.domain.mascot.repository.MascotRepository;
import com.example.trippaminebe.domain.mascot.repository.MascotSpawnPointRepository;
import com.example.trippaminebe.domain.mascot.repository.RegionRepository;
import com.example.trippaminebe.domain.mascot.repository.UserMascotCollectionRepository;
import com.example.trippaminebe.domain.quest.entity.QuestStatus;
import com.example.trippaminebe.domain.quest.repository.UserQuestLogRepository;
import com.example.trippaminebe.domain.quest.service.GeoUtils;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// quest.service.QuestService와 완전히 같은 뼈대(반경 검증 -> 성공/실패 -> 포인트 지급)를 따르되,
// "조우(encounter) -> (웹/안드로이드 미니게임) -> 확정(catch)" 2단계로 나눈 것이 다른 점이다.
// GeoUtils.distanceMeters()는 quest 도메인 것을 그대로 재사용한다(별도 구현 없음).
//
// =========================================================
// [수정 내역]
// =========================================================
// 1. 재포획 정책: 같은 마스코트를 여러 번 잡을 수 있지만 포인트는 "첫 포획"에만 지급한다.
//    재포획도 USER_MASCOT_COLLECTIONS에 한 줄씩 기록되어 포획 횟수로 집계된다.
//    동시 요청으로 첫 포획 보상이 두 번 나가지 않도록 USERS 행을 잠근 뒤 판단한다.
// 2. 포획 판정 서버 이전: 클라이언트는 미니게임 점수만 보내고, MascotCatchJudge가 확률로 판정한다.
//    토큰 하나당 시도 횟수 제한(EncounterTokenStore.MAX_ATTEMPTS).
// 3. 연동 퀘스트(LINKED_QUEST_ID): 해당 퀘스트를 SUCCESS로 클리어한 사용자에게만 스폰이 보이고 조우 가능.
// 4. 도감 API(findDex) 추가 - 전체 마스코트 + 내 수집 여부/횟수, 지역별 수집률.
// 5. 주변 조회 N+1 제거, 포획 위치에 사용자의 실제 조우 좌표 저장.
// 6. 관리자 마스코트/지역 삭제 추가(연결 데이터가 있으면 409).
// =========================================================
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
    private final UserQuestLogRepository userQuestLogRepository;
    private final EncounterTokenStore encounterTokenStore;
    private final MascotCatchJudge catchJudge;

    // ===================== 사용자용: 지도/조우/포획/도감 =====================

    @Transactional(readOnly = true)
    public List<MascotSpawnPointResponse> findNearby(Long userId, BigDecimal lat,
                                                     BigDecimal lng, Double radiusKm) {
        List<MascotSpawnPoint> spawnPoints = spawnPointRepository.findByActiveYn("Y");

        // [N+1 수정] 스폰마다 exists 쿼리를 날리지 않고 한 번에 조회
        Set<Long> collectedMascotIds = collectionRepository.findCollectedMascotIds(userId);
        Set<Long> clearedQuestIds = findClearedQuestIds(userId);

        return spawnPoints.stream()
                .filter(spawn -> isUnlocked(spawn, clearedQuestIds))
                .filter(spawn -> {
                    if (lat == null || lng == null || radiusKm == null) return true;
                    double distance = GeoUtils.distanceMeters(lat, lng, spawn.getTargetLat(), spawn.getTargetLng());
                    return distance <= radiusKm * 1000;
                })
                .map(spawn -> MascotSpawnPointResponse.from(
                        spawn,
                        collectedMascotIds.contains(spawn.getMascot().getMascotId())
                ))
                .toList();
    }

    // 1단계: "지금 이 위치에서 이 스폰 지점을 조우할 수 있는가" 검증. 통과하면 단기 토큰을 발급한다.
    // 이 토큰이 포획 화면(웹 미니게임 / androidBridge.startArCatch)으로 전달되고,
    // 미니게임이 끝나면 아래 catchMascot()에서 다시 검증된다.
    public MascotEncounterResponse encounter(Long userId, Long spawnId, MascotEncounterRequest request) {
        MascotSpawnPoint spawn = getSpawnOrThrow(spawnId);

        if (!"Y".equals(spawn.getActiveYn())) {
            throw new MascotSpawnNotFoundException("지금은 만날 수 없는 마스코트입니다. id=" + spawnId);
        }

        // [추가] 연동 퀘스트 확인
        if (!isUnlocked(spawn, findClearedQuestIds(userId))) {
            throw new MascotLockedException("연결된 퀘스트를 먼저 클리어해야 이 마스코트를 만날 수 있어요.");
        }

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

        // [수정] 조우 시점의 실제 사용자 좌표를 세션에 같이 저장 (포획 위치 기록용)
        String token = encounterTokenStore.issue(
                userId, spawnId, request.getCurrentLat(), request.getCurrentLng()
        );

        return MascotEncounterResponse.builder()
                .encounterToken(token)
                .expiresInSeconds(encounterTokenStore.ttlSeconds())
                .maxAttempts(EncounterTokenStore.MAX_ATTEMPTS)
                .spawnId(spawnId)
                .mascotId(mascot.getMascotId())
                .mascotName(mascot.getMascotName())
                .mascotRarity(mascot.getRarity().name())
                .mascotImageUrl(mascot.getImageUrl())
                .rewardPoint(mascot.getRewardPoint())
                .alreadyCollected(collectionRepository.existsByUser_IdAndMascot_MascotId(userId, mascot.getMascotId()))
                .build();
    }

    // 2단계: 미니게임이 끝난 뒤 최종 확정.
    // encounterToken으로 "실제로 방금 그 위치에서 조우한 세션이 맞는지" 재확인하고,
    // 포획 성공 여부는 서버가 희귀도 + 미니게임 점수로 판정한다.
    public MascotCatchResponse catchMascot(Long userId, Long spawnId, MascotCatchRequest request) {
        MascotSpawnPoint spawn = getSpawnOrThrow(spawnId);
        EncounterTokenStore.EncounterSession session =
                encounterTokenStore.validate(request.getEncounterToken(), userId, spawnId);

        Mascot mascot = spawn.getMascot();

        // ---------- 1) 서버 측 포획 판정 ----------
        double catchRate = catchJudge.catchRate(mascot.getRarity(), request.getScore());
        boolean success = catchJudge.roll(catchRate);

        if (!success) {
            // 실패 1회 기록. 남은 횟수가 있으면 같은 토큰으로 재도전, 0이면 토큰 폐기 → 다시 조우부터.
            int remaining = encounterTokenStore.recordFailure(request.getEncounterToken(), session);

            return MascotCatchResponse.builder()
                    .caught(false)
                    .firstCatch(false)
                    .catchCount(collectionRepository.countByUser_IdAndMascot_MascotId(userId, mascot.getMascotId()))
                    .remainingAttempts(remaining)
                    .mascotId(mascot.getMascotId())
                    .mascotName(mascot.getMascotName())
                    .rewardPoint(0L)
                    .totalPoints(userRepository.findById(userId)
                            .map(User::getTotalPoints)
                            .orElse(0L))
                    .build();
        }

        // ---------- 2) 토큰 원자적 소모 ----------
        // 같은 토큰으로 동시에 두 요청이 와도 하나만 여기를 통과한다.
        if (!encounterTokenStore.consume(request.getEncounterToken())) {
            throw new EncounterTokenInvalidException("이미 처리된 포획 세션입니다. 다시 마스코트에게 접근해주세요.");
        }

        // ---------- 3) 첫 포획 여부 판단 + 기록 + 보상 ----------
        // USERS 행을 잠근 상태에서 판단하므로, 같은 사용자의 포획 확정 요청은 한 번에 하나씩 처리된다.
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id=" + userId));

        boolean firstCatch = !collectionRepository.existsByUser_IdAndMascot_MascotId(userId, mascot.getMascotId());

        BigDecimal caughtLat = session.encounterLat() != null ? session.encounterLat() : spawn.getTargetLat();
        BigDecimal caughtLng = session.encounterLng() != null ? session.encounterLng() : spawn.getTargetLng();

        collectionRepository.save(UserMascotCollection.of(user, mascot, spawnId, caughtLat, caughtLng));

        // [재포획 정책] 첫 포획만 포인트 지급
        long rewardPoint = 0L;
        if (firstCatch && mascot.getRewardPoint() != null && mascot.getRewardPoint() > 0) {
            rewardPoint = mascot.getRewardPoint();
            user.setTotalPoints(user.getTotalPoints() + rewardPoint);
        }

        // JPQL count 실행 전에 자동 flush되므로 방금 저장한 포획도 포함된다.
        long catchCount = collectionRepository.countByUser_IdAndMascot_MascotId(userId, mascot.getMascotId());

        return MascotCatchResponse.builder()
                .caught(true)
                .firstCatch(firstCatch)
                .catchCount(catchCount)
                .remainingAttempts(0)
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

    // [추가] 도감: 전체 마스코트를 지역별로 묶고, 각 마스코트에 내 수집 여부/횟수를 붙인다.
    @Transactional(readOnly = true)
    public MascotDexResponse findDex(Long userId) {
        List<Mascot> mascots = mascotRepository.findAllWithRegionOrdered();

        Map<Long, MascotCatchStat> statByMascot = collectionRepository.findCatchStats(userId).stream()
                .collect(Collectors.toMap(MascotCatchStat::getMascotId, Function.identity()));

        // 지역 순서를 유지하면서 묶기
        Map<Region, List<Mascot>> byRegion = mascots.stream()
                .collect(Collectors.groupingBy(Mascot::getRegion, LinkedHashMap::new, Collectors.toList()));

        List<MascotDexResponse.RegionDex> regions = new ArrayList<>();
        int total = 0;
        int collected = 0;

        for (Map.Entry<Region, List<Mascot>> entry : byRegion.entrySet()) {
            Region region = entry.getKey();
            List<MascotDexResponse.DexEntry> entries = entry.getValue().stream()
                    .map(mascot -> toDexEntry(mascot, statByMascot.get(mascot.getMascotId())))
                    .toList();

            int regionCollected = (int) entries.stream().filter(MascotDexResponse.DexEntry::collected).count();
            total += entries.size();
            collected += regionCollected;

            regions.add(new MascotDexResponse.RegionDex(
                    region.getRegionId(),
                    region.getRegionName(),
                    entries.size(),
                    regionCollected,
                    entries
            ));
        }

        return new MascotDexResponse(total, collected, MascotDexResponse.rate(collected, total), regions);
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
        Region region = getRegionOrThrow(regionId);
        region.update(request.getRegionName(), request.getSidoCode());
        return RegionResponse.from(region);
    }

    // [추가] 지역 삭제 - 소속 마스코트가 있으면 409
    public void deleteRegion(Long regionId) {
        Region region = getRegionOrThrow(regionId);
        if (mascotRepository.existsByRegion_RegionId(regionId)) {
            throw new MascotDeleteConflictException("이 지역에 소속된 마스코트가 있어 삭제할 수 없습니다. 마스코트를 먼저 정리해주세요.");
        }
        regionRepository.delete(region);
    }

    @Transactional(readOnly = true)
    public List<MascotResponse> findAllMascots() {
        return mascotRepository.findAll().stream().map(MascotResponse::from).toList();
    }

    public MascotResponse createMascot(MascotRequest request) {
        Region region = getRegionOrThrow(request.getRegionId());

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
        Mascot mascot = getMascotOrThrow(mascotId);
        Region region = getRegionOrThrow(request.getRegionId());

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

    // [추가] 마스코트 삭제 - 포획 기록이 있으면 도감/포인트 근거가 사라지므로 막고(409),
    // 스폰 지점이 남아 있으면 먼저 스폰을 정리하도록 안내한다.
    public void deleteMascot(Long mascotId) {
        Mascot mascot = getMascotOrThrow(mascotId);
        if (collectionRepository.existsByMascot_MascotId(mascotId)) {
            throw new MascotDeleteConflictException("이미 포획한 사용자가 있어 삭제할 수 없습니다. 스폰 지점을 비활성화(ACTIVE_YN=N)해주세요.");
        }
        if (spawnPointRepository.existsByMascot_MascotId(mascotId)) {
            throw new MascotDeleteConflictException("이 마스코트의 스폰 지점이 남아 있습니다. 스폰 지점을 먼저 삭제해주세요.");
        }
        mascotRepository.delete(mascot);
    }

    @Transactional(readOnly = true)
    public List<MascotSpawnPointResponse> findAllSpawnPointsForAdmin() {
        return spawnPointRepository.findAll().stream()
                .map(spawn -> MascotSpawnPointResponse.from(spawn, false))
                .toList();
    }

    public MascotSpawnPointResponse createSpawnPoint(MascotSpawnPointRequest request) {
        Mascot mascot = getMascotOrThrow(request.getMascotId());

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

        // 스폰 지점이 가리키는 마스코트도 바꿀 수 있게 하려면 MascotSpawnPoint.update에 mascot 인자를 추가하면 된다.
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

    // 포획 기록(USER_MASCOT_COLLECTIONS)은 SPAWN_ID를 FK 없이 숫자로만 들고 있으므로 스폰 삭제 후에도 도감은 유지된다.
    public void deleteSpawnPoint(Long spawnId) {
        MascotSpawnPoint spawnPoint = getSpawnOrThrow(spawnId);
        spawnPointRepository.delete(spawnPoint);
    }

    // ===================== 내부 헬퍼 =====================

    private MascotSpawnPoint getSpawnOrThrow(Long spawnId) {
        return spawnPointRepository.findById(spawnId)
                .orElseThrow(() -> new MascotSpawnNotFoundException("존재하지 않는 스폰 지점입니다. id=" + spawnId));
    }

    private Mascot getMascotOrThrow(Long mascotId) {
        return mascotRepository.findById(mascotId)
                .orElseThrow(() -> new MascotSpawnNotFoundException("존재하지 않는 마스코트입니다. id=" + mascotId));
    }

    private Region getRegionOrThrow(Long regionId) {
        return regionRepository.findById(regionId)
                .orElseThrow(() -> new MascotSpawnNotFoundException("존재하지 않는 지역입니다. id=" + regionId));
    }

    // 사용자가 SUCCESS로 클리어한 퀘스트 ID 목록 (연동 퀘스트 잠금 판단용)
    private Set<Long> findClearedQuestIds(Long userId) {
        return userQuestLogRepository.findByUser_IdAndStatus(userId, QuestStatus.SUCCESS).stream()
                .map(log -> log.getQuest().getQuestId())
                .collect(Collectors.toSet());
    }

    private boolean isUnlocked(MascotSpawnPoint spawn, Set<Long> clearedQuestIds) {
        return spawn.getLinkedQuestId() == null || clearedQuestIds.contains(spawn.getLinkedQuestId());
    }

    private MascotDexResponse.DexEntry toDexEntry(Mascot mascot, MascotCatchStat stat) {
        boolean collected = stat != null;
        return new MascotDexResponse.DexEntry(
                mascot.getMascotId(),
                mascot.getMascotName(),
                mascot.getRarity().name(),
                mascot.getRarity().getLabel(),
                mascot.getImageUrl(),
                mascot.getRewardPoint(),
                collected,
                collected ? stat.getCatchCount() : 0L,
                collected ? stat.getFirstCaughtAt() : null,
                collected ? stat.getLastCaughtAt() : null
        );
    }
}
