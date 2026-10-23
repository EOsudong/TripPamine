package com.example.trippaminebe.domain.music.service;

import com.example.trippaminebe.domain.music.dto.LastFmTrackDto;
import com.example.trippaminebe.domain.music.dto.MusicDto;
import com.example.trippaminebe.domain.music.dto.TravelMusicRequestDto;
import com.example.trippaminebe.domain.music.dto.TravelMusicResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class TravelMusicRecommendationService {

    private final LastFmMusicService lastFmMusicService;


    // =====================================================
    // 추천 조건 종류
    // =====================================================

    private enum SourceType {

        GENRE,

        STYLE,

        ENVIRONMENT,

        TIME
    }


    // =====================================================
    // Last.fm에 요청할 Tag 정보
    // =====================================================

    private record ContextTag(

            String tag,

            int weight,

            SourceType sourceType

    ) {
    }


    // =====================================================
    // 음악 추천
    // =====================================================

    public TravelMusicResponseDto recommend(
            TravelMusicRequestDto request
    ) {

        // =================================================
        // 1. 사용자 입력 정리
        // =================================================

        String placeType =
                normalize(
                        request.getPlaceType()
                );


        String weather =
                normalize(
                        request.getWeather()
                );


        String timeOfDay =
                normalize(
                        request.getTimeOfDay()
                );


        String travelStyle =
                normalize(
                        request.getTravelStyle()
                );


        String preferredGenre =
                normalize(
                        request.getPreferredGenre()
                );


        // =================================================
        // 2. 조건별 Last.fm 태그 생성
        // =================================================

        String genreTag =
                getGenreTag(
                        preferredGenre
                );


        String styleTag =
                getTravelStyleTag(
                        travelStyle
                );


        String environmentTag =
                getEnvironmentTag(
                        placeType,
                        weather
                );


        String timeTag =
                getTimeTag(
                        timeOfDay
                );


        // =================================================
        // 3. 추천 Context 생성
        //
        // 이전:
        //
        // 장르 16
        // 스타일 11
        // 환경 8
        // 시간 6
        //
        // → 장르가 너무 강했음
        //
        // 현재:
        //
        // 서로 비슷한 중요도로 조정
        // =================================================

        List<ContextTag> contexts =
                List.of(

                        new ContextTag(
                                genreTag,
                                8,
                                SourceType.GENRE
                        ),

                        new ContextTag(
                                styleTag,
                                8,
                                SourceType.STYLE
                        ),

                        new ContextTag(
                                environmentTag,
                                7,
                                SourceType.ENVIRONMENT
                        ),

                        new ContextTag(
                                timeTag,
                                7,
                                SourceType.TIME
                        )
                );


        System.out.println(
                "=========================================="
        );

        System.out.println(
                "[TripPamine Balanced Music Recommendation]"
        );

        System.out.println(
                "장소 = "
                        + placeType
        );

        System.out.println(
                "날씨 = "
                        + weather
        );

        System.out.println(
                "시간 = "
                        + timeOfDay
        );

        System.out.println(
                "스타일 = "
                        + travelStyle
        );

        System.out.println(
                "장르 = "
                        + preferredGenre
        );


        for (
                ContextTag context
                : contexts
        ) {

            System.out.println(
                    context.sourceType()
                            + " → "
                            + context.tag()
                            + " / weight="
                            + context.weight()
            );
        }


        System.out.println(
                "=========================================="
        );


        // =================================================
        // 4. 음악 후보 저장
        // =================================================

        Map<String, ScoredTrack> candidateMap =
                new LinkedHashMap<>();


        // =================================================
        // 5. 각 조건별로 Last.fm 조회
        // =================================================

        for (
                ContextTag context
                : contexts
        ) {

            // =============================================
            // 페이지 다양화
            // =============================================

            int randomPage =
                    ThreadLocalRandom
                            .current()
                            .nextInt(
                                    1,
                                    4
                            );


            System.out.println(
                    "[Last.fm 요청]"
                            + " source="
                            + context.sourceType()
                            + " / tag="
                            + context.tag()
                            + " / page="
                            + randomPage
            );


            // =============================================
            // 조건당 20곡
            //
            // 최대 약 80개의 후보
            // =============================================

            List<LastFmTrackDto> tracks =
                    lastFmMusicService
                            .getTopTracksByTag(

                                    context.tag(),

                                    20,

                                    randomPage
                            );


            System.out.println(
                    "[Last.fm 결과] "
                            + context.tag()
                            + " → "
                            + tracks.size()
                            + "곡"
            );


            // =============================================
            // 점수 계산
            // =============================================

            addCandidates(

                    candidateMap,

                    tracks,

                    context.weight(),

                    context.sourceType()
            );
        }


        // =================================================
        // 6. 후보가 없으면 fallback
        // =================================================

        if (
                candidateMap.isEmpty()
        ) {

            System.out.println(
                    "[Music] 후보가 없어 chill fallback 실행"
            );


            List<LastFmTrackDto> fallback =
                    lastFmMusicService
                            .getTopTracksByTag(
                                    "chill",
                                    20,
                                    1
                            );


            addCandidates(

                    candidateMap,

                    fallback,

                    5,

                    SourceType.STYLE
            );
        }


        // =================================================
        // 7. 전체 후보 정렬
        // =================================================

        List<ScoredTrack> sortedCandidates =
                candidateMap
                        .values()
                        .stream()

                        .sorted(
                                Comparator

                                        .comparingInt(
                                                ScoredTrack::score
                                        )

                                        .reversed()

                                        .thenComparing(
                                                ScoredTrack::artist
                                        )

                                        .thenComparing(
                                                ScoredTrack::title
                                        )
                        )

                        .toList();


        // =================================================
        // ★ 8. 균형 추천
        //
        // 각 조건에서 최소 1곡씩 선발
        // =================================================

        List<ScoredTrack> selectedTracks =
                new ArrayList<>();


        // =============================================
        // 사용자 선호 장르 1곡
        // =============================================

        addBestTrackFromSource(

                sortedCandidates,

                selectedTracks,

                SourceType.GENRE
        );


        // =============================================
        // 여행 스타일 1곡
        // =============================================

        addBestTrackFromSource(

                sortedCandidates,

                selectedTracks,

                SourceType.STYLE
        );


        // =============================================
        // 장소 / 날씨 1곡
        // =============================================

        addBestTrackFromSource(

                sortedCandidates,

                selectedTracks,

                SourceType.ENVIRONMENT
        );


        // =============================================
        // 시간대 1곡
        // =============================================

        addBestTrackFromSource(

                sortedCandidates,

                selectedTracks,

                SourceType.TIME
        );


        // =================================================
        // 9. 남은 자리는 종합 점수 순
        // =================================================

        for (
                ScoredTrack candidate
                : sortedCandidates
        ) {

            if (
                    selectedTracks.size()
                            >= 10
            ) {

                break;
            }


            if (
                    !selectedTracks.contains(
                            candidate
                    )
            ) {

                selectedTracks.add(
                        candidate
                );
            }
        }


        // =================================================
        // 10. 최종 리스트 5개 제한
        // =================================================

        List<ScoredTrack> finalTracks =
                selectedTracks
                        .stream()

                        .limit(10)

                        .toList();


        // =================================================
        // ★ 11. TOP 5 앨범 커버 조회
        //
        // track.getInfo
        // =================================================

        for (
                ScoredTrack track
                : finalTracks
        ) {

            String albumImage =
                    lastFmMusicService
                            .getTrackImage(

                                    track.artist(),

                                    track.title()
                            );


            if (
                    albumImage != null
                            &&
                            !albumImage.isBlank()
            ) {

                track.setImageUrl(
                        albumImage
                );


                System.out.println(
                        "[앨범 이미지 적용] "
                                + track.artist()
                                + " - "
                                + track.title()
                );

            } else {

                System.out.println(
                        "[앨범 이미지 없음] "
                                + track.artist()
                                + " - "
                                + track.title()
                );
            }
        }


        // =================================================
        // 12. MusicDto
        // =================================================

        List<MusicDto> songs =
                finalTracks
                        .stream()

                        .map(
                                this::toMusicDto
                        )

                        .toList();


        // =================================================
        // 13. Theme
        // =================================================

        String theme =
                determineTheme(

                        placeType,

                        weather,

                        timeOfDay,

                        travelStyle
                );


        // =================================================
        // 14. Emoji
        // =================================================

        String emoji =
                determineEmoji(

                        placeType,

                        weather,

                        timeOfDay
                );


        // =================================================
        // 15. Description
        // =================================================

        String description =
                createDescription(

                        placeType,

                        weather,

                        travelStyle,

                        preferredGenre,

                        candidateMap.size()
                );


        // =================================================
        // 최종 결과 Console
        // =================================================

        System.out.println(
                "=========================================="
        );

        System.out.println(
                "[최종 균형 추천 결과]"
        );

        System.out.println(
                "전체 후보곡 수 = "
                        + candidateMap.size()
        );


        for (
                int i = 0;
                i < finalTracks.size();
                i++
        ) {

            ScoredTrack track =
                    finalTracks.get(i);


            System.out.println(
                    (i + 1)
                            + ". "
                            + track.artist()
                            + " - "
                            + track.title()
            );


            System.out.println(
                    "   score = "
                            + track.score()
            );


            System.out.println(
                    "   tags = "
                            + track.tags()
            );


            System.out.println(
                    "   sources = "
                            + track.sources()
            );


            System.out.println(
                    "   image = "
                            + (
                            track.imageUrl() == null
                                    ||
                                    track.imageUrl().isBlank()

                                    ? "없음"

                                    : "있음"
                    )
            );
        }


        System.out.println(
                "=========================================="
        );


        return new TravelMusicResponseDto(

                theme,

                emoji,

                description,

                songs
        );
    }


    // =====================================================
    // 후보 추가 + 점수 계산
    // =====================================================

    private void addCandidates(

            Map<String, ScoredTrack> candidateMap,

            List<LastFmTrackDto> tracks,

            int baseWeight,

            SourceType sourceType

    ) {

        for (
                LastFmTrackDto track
                : tracks
        ) {

            if (
                    track == null
                            ||
                            track.title() == null
                            ||
                            track.title().isBlank()
                            ||
                            track.artist() == null
                            ||
                            track.artist().isBlank()
            ) {

                continue;
            }


            // =============================================
            // Artist + Title로 중복 제거
            // =============================================

            String key =
                    (
                            track.artist()
                                    + "::"
                                    + track.title()
                    )
                            .trim()

                            .toLowerCase(
                                    Locale.ROOT
                            );


            // =============================================
            // 인기도 Bonus
            // =============================================

            int popularityBonus =
                    calculateRankBonus(
                            track.rank()
                    );


            int addedScore =
                    baseWeight
                            + popularityBonus;


            ScoredTrack existing =
                    candidateMap.get(
                            key
                    );


            // =============================================
            // 처음 발견한 음악
            // =============================================

            if (
                    existing == null
            ) {

                List<String> tags =
                        new ArrayList<>();


                if (
                        track.sourceTag() != null
                                &&
                                !track.sourceTag().isBlank()
                ) {

                    tags.add(
                            track.sourceTag()
                    );
                }


                EnumSet<SourceType> sources =
                        EnumSet.of(
                                sourceType
                        );


                ScoredTrack newTrack =
                        new ScoredTrack(

                                track.title(),

                                track.artist(),

                                addedScore,

                                tags,

                                sources,

                                track.imageUrl()
                        );


                candidateMap.put(
                        key,
                        newTrack
                );

            } else {

                // =========================================
                // 다른 조건에서도 같은 곡 발견
                // =========================================

                boolean newSource =
                        !existing
                                .sources()
                                .contains(
                                        sourceType
                                );


                // 기존 조건 점수 추가
                existing.addScore(
                        addedScore
                );


                // =========================================
                // ★ 다중 조건 일치 보너스
                //
                // 장르 + 휴식
                // 장르 + 밤
                // 휴식 + 밤
                //
                // 등 여러 조건에서 동시에 발견되면
                // 매우 적합한 음악으로 평가
                // =========================================

                if (
                        newSource
                ) {

                    existing.addScore(
                            6
                    );
                }


                existing.addSource(
                        sourceType
                );


                existing.addTag(
                        track.sourceTag()
                );


                existing.useImageIfEmpty(
                        track.imageUrl()
                );
            }
        }
    }


    // =====================================================
    // 조건별 최고곡 하나 선택
    // =====================================================

    private void addBestTrackFromSource(

            List<ScoredTrack> candidates,

            List<ScoredTrack> selected,

            SourceType sourceType

    ) {

        for (
                ScoredTrack candidate
                : candidates
        ) {

            if (
                    selected.contains(
                            candidate
                    )
            ) {

                continue;
            }


            if (
                    candidate
                            .sources()
                            .contains(
                                    sourceType
                            )
            ) {

                selected.add(
                        candidate
                );


                System.out.println(
                        "[균형 선발] "
                                + sourceType
                                + " → "
                                + candidate.artist()
                                + " - "
                                + candidate.title()
                );


                return;
            }
        }
    }


    // =====================================================
    // Last.fm 인기 순위 점수
    //
    // 이전보다 인기순위 영향력을 줄였습니다.
    // =====================================================

    private int calculateRankBonus(
            int rank
    ) {

        if (
                rank <= 10
        ) {

            return 5;
        }


        if (
                rank <= 25
        ) {

            return 4;
        }


        if (
                rank <= 50
        ) {

            return 2;
        }


        return 1;
    }


    // =====================================================
    // MusicDto 변환
    // =====================================================

    private MusicDto toMusicDto(
            ScoredTrack track
    ) {

        String keyword =
                track.artist()
                        + " "
                        + track.title();


        String encoded =
                URLEncoder.encode(

                        keyword,

                        StandardCharsets.UTF_8
                );


        String youtubeUrl =
                "https://www.youtube.com/results?search_query="
                        + encoded;


        String spotifyUrl =
                "https://open.spotify.com/search/"
                        + encoded;


        // =================================================
        // 실제 매칭된 태그를 Mood에 표시
        //
        // 예:
        //
        // hip-hop
        // chill
        // nature
        // night
        //
        // 또는
        //
        // hip-hop · night
        // =================================================

        String mood =
                track.tags().isEmpty()

                        ? "travel"

                        : String.join(
                        " · ",
                        track.tags()
                );


        return new MusicDto(

                track.title(),

                track.artist(),

                mood,

                youtubeUrl,

                spotifyUrl,

                track.imageUrl()
        );
    }


    // =====================================================
    // Genre
    // =====================================================

    private String getGenreTag(
            String genre
    ) {

        return switch (
                genre
                ) {

            case "INDIE" ->
                    "indie";

            case "RNB" ->
                    "rnb";

            case "HIPHOP" ->
                    "hip-hop";

            case "POP" ->
                    "pop";

            case "JAZZ" ->
                    "jazz";

            default ->
                    "chill";
        };
    }


    // =====================================================
    // 여행 스타일
    // =====================================================

    private String getTravelStyleTag(
            String travelStyle
    ) {

        return switch (
                travelStyle
                ) {

            case "DRIVE" ->
                    "road trip";

            case "WALK" ->
                    "walking";

            case "RELAX" ->
                    "chill";

            case "DATE" ->
                    "romantic";

            case "ACTIVE" ->
                    "energetic";

            default ->
                    "chill";
        };
    }


    // =====================================================
    // 장소 + 날씨
    // =====================================================

    private String getEnvironmentTag(

            String placeType,

            String weather

    ) {

        // =============================================
        // 날씨가 강한 분위기를 가지고 있으면
        // 날씨 우선
        // =============================================

        if (
                "RAINY".equals(
                        weather
                )
        ) {

            return "rainy day";
        }


        if (
                "SNOWY".equals(
                        weather
                )
        ) {

            return "winter";
        }


        // =============================================
        // 장소
        // =============================================

        if (
                "SEA".equals(
                        placeType
                )
        ) {

            return "summer";
        }


        if (
                "MOUNTAIN".equals(
                        placeType
                )
        ) {

            return "nature";
        }


        if (
                "CITY".equals(
                        placeType
                )
        ) {

            return "urban";
        }


        return "chill";
    }


    // =====================================================
    // 시간
    // =====================================================

    private String getTimeTag(
            String timeOfDay
    ) {

        return switch (
                timeOfDay
                ) {

            case "MORNING" ->
                    "morning";

            case "AFTERNOON" ->
                    "feel good";

            case "EVENING" ->
                    "sunset";

            case "NIGHT" ->
                    "night";

            default ->
                    "chill";
        };
    }


    // =====================================================
    // Theme
    // =====================================================

    private String determineTheme(

            String placeType,

            String weather,

            String timeOfDay,

            String travelStyle

    ) {

        if (
                "RAINY".equals(
                        weather
                )
        ) {

            return "RAINY TRIP";
        }


        if (
                "SEA".equals(
                        placeType
                )
                        &&
                        "SUNNY".equals(
                                weather
                        )
                        &&
                        "DRIVE".equals(
                                travelStyle
                        )
        ) {

            return "OCEAN CHILL DRIVE";
        }


        if (
                "SEA".equals(
                        placeType
                )
                        &&
                        (
                                "EVENING".equals(
                                        timeOfDay
                                )
                                        ||
                                        "NIGHT".equals(
                                                timeOfDay
                                        )
                        )
        ) {

            return "SUNSET OCEAN";
        }


        if (
                "MOUNTAIN".equals(
                        placeType
                )
                        &&
                        "MORNING".equals(
                                timeOfDay
                        )
        ) {

            return "HIKING ENERGY";
        }


        if (
                "CITY".equals(
                        placeType
                )
                        &&
                        "NIGHT".equals(
                                timeOfDay
                        )
        ) {

            return "CITY NIGHT";
        }


        if (
                "DATE".equals(
                        travelStyle
                )
        ) {

            return "ROMANTIC TRAVEL";
        }


        if (
                "DRIVE".equals(
                        travelStyle
                )
        ) {

            return "TRAVEL DRIVE";
        }


        if (
                "RELAX".equals(
                        travelStyle
                )
        ) {

            return "CHILL TRAVEL";
        }


        return "PERSONAL TRAVEL MIX";
    }


    // =====================================================
    // Emoji
    // =====================================================

    private String determineEmoji(

            String placeType,

            String weather,

            String timeOfDay

    ) {

        if (
                "RAINY".equals(
                        weather
                )
        ) {

            return "🌧️";
        }


        if (
                "SNOWY".equals(
                        weather
                )
        ) {

            return "❄️";
        }


        if (
                "SEA".equals(
                        placeType
                )
        ) {

            if (
                    "EVENING".equals(
                            timeOfDay
                    )
                            ||
                            "NIGHT".equals(
                                    timeOfDay
                            )
            ) {

                return "🌅";
            }


            return "🌊";
        }


        if (
                "MOUNTAIN".equals(
                        placeType
                )
        ) {

            return "🏔️";
        }


        if (
                "CITY".equals(
                        placeType
                )
        ) {

            return "🌃";
        }


        return "🎵";
    }


    // =====================================================
    // 설명
    // =====================================================

    private String createDescription(

            String placeType,

            String weather,

            String travelStyle,

            String preferredGenre,

            int candidateCount

    ) {

        String place =
                switch (
                        placeType
                        ) {

                    case "SEA" ->
                            "바다 여행";

                    case "MOUNTAIN" ->
                            "산 여행";

                    case "CITY" ->
                            "도시 여행";

                    default ->
                            "여행";
                };


        String weatherText =
                switch (
                        weather
                        ) {

                    case "SUNNY" ->
                            "맑은 날";

                    case "RAINY" ->
                            "비 오는 날";

                    case "CLOUDY" ->
                            "흐린 날";

                    case "SNOWY" ->
                            "눈 오는 날";

                    default ->
                            "";
                };


        String style =
                switch (
                        travelStyle
                        ) {

                    case "DRIVE" ->
                            "드라이브";

                    case "WALK" ->
                            "산책";

                    case "RELAX" ->
                            "휴식";

                    case "DATE" ->
                            "데이트";

                    case "ACTIVE" ->
                            "액티브 여행";

                    default ->
                            "여행";
                };


        String genre =
                switch (
                        preferredGenre
                        ) {

                    case "INDIE" ->
                            "Indie";

                    case "RNB" ->
                            "R&B";

                    case "HIPHOP" ->
                            "Hip-Hop";

                    case "POP" ->
                            "Pop";

                    case "JAZZ" ->
                            "Jazz";

                    default ->
                            "다양한 장르";
                };


        return weatherText
                + " "
                + place
                + "에서 "
                + style
                + "를 즐기는 당신을 위해 "
                + genre
                + " 취향뿐 아니라 여행 분위기와 시간대까지 균형 있게 분석해 "
                + candidateCount
                + "개의 후보 중 음악을 골랐어요.";
    }


    // =====================================================
    // Normalize
    // =====================================================

    private String normalize(
            String value
    ) {

        if (
                value == null
        ) {

            return "";
        }


        return value
                .trim()
                .toUpperCase(
                        Locale.ROOT
                );
    }


    // =====================================================
    // 내부 추천곡 객체
    // =====================================================

    private static class ScoredTrack {

        private final String title;

        private final String artist;

        private int score;

        private final List<String> tags;

        private final EnumSet<SourceType> sources;

        private String imageUrl;


        public ScoredTrack(

                String title,

                String artist,

                int score,

                List<String> tags,

                EnumSet<SourceType> sources,

                String imageUrl

        ) {

            this.title =
                    title;

            this.artist =
                    artist;

            this.score =
                    score;

            this.tags =
                    tags;

            this.sources =
                    sources;

            this.imageUrl =
                    imageUrl;
        }


        public String title() {

            return title;
        }


        public String artist() {

            return artist;
        }


        public int score() {

            return score;
        }


        public List<String> tags() {

            return tags;
        }


        public EnumSet<SourceType> sources() {

            return sources;
        }


        public String imageUrl() {

            return imageUrl;
        }


        private void addScore(
                int value
        ) {

            this.score +=
                    value;
        }


        private void addSource(
                SourceType source
        ) {

            this.sources.add(
                    source
            );
        }


        private void addTag(
                String tag
        ) {

            if (
                    tag != null
                            &&
                            !tag.isBlank()
                            &&
                            !tags.contains(
                                    tag
                            )
            ) {

                tags.add(
                        tag
                );
            }
        }


        private void useImageIfEmpty(
                String newImageUrl
        ) {

            if (
                    (
                            imageUrl == null
                                    ||
                                    imageUrl.isBlank()
                    )
                            &&
                            newImageUrl != null
                            &&
                            !newImageUrl.isBlank()
            ) {

                this.imageUrl =
                        newImageUrl;
            }
        }


        private void setImageUrl(
                String newImageUrl
        ) {

            this.imageUrl =
                    newImageUrl;
        }
    }
}