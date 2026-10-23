package com.example.trippaminebe.domain.music.service;

import com.example.trippaminebe.domain.music.dto.LastFmTrackDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Service
public class LastFmMusicService {

    // =====================================================
    // Last.fm API 주소
    // =====================================================

    private static final String LASTFM_BASE_URL =
            "https://ws.audioscrobbler.com";


    // =====================================================
    // API KEY
    // =====================================================

    @Value("${lastfm.api.key}")
    private String apiKey;


    // =====================================================
    // RestClient
    // =====================================================

    private final RestClient restClient;


    // =====================================================
    // Constructor
    // =====================================================

    public LastFmMusicService() {

        this.restClient =
                RestClient.create(
                        LASTFM_BASE_URL
                );
    }


    // =====================================================
    // 기존 방식
    //
    // page를 지정하지 않으면 1페이지
    // =====================================================

    public List<LastFmTrackDto> getTopTracksByTag(
            String tag,
            int limit
    ) {

        return getTopTracksByTag(
                tag,
                limit,
                1
        );
    }


    // =====================================================
    // Tag 기반 음악 조회
    // =====================================================

    public List<LastFmTrackDto> getTopTracksByTag(
            String tag,
            int limit,
            int page
    ) {

        // =================================================
        // API Key 확인
        // =================================================

        if (
                apiKey == null
                        ||
                        apiKey.isBlank()
        ) {

            throw new IllegalStateException(
                    "Last.fm API Key가 설정되지 않았습니다."
            );
        }


        // =================================================
        // Tag 확인
        // =================================================

        if (
                tag == null
                        ||
                        tag.isBlank()
        ) {

            return List.of();
        }


        // =================================================
        // 안전값
        // =================================================

        final int safeLimit =
                limit <= 0
                        ? 10
                        : limit;


        final int safePage =
                page <= 0
                        ? 1
                        : page;


        try {

            // =================================================
            // Last.fm
            // tag.getTopTracks
            // =================================================

            LastFmResponse response =
                    restClient
                            .get()

                            .uri(
                                    uriBuilder ->
                                            uriBuilder

                                                    .path("/2.0/")

                                                    .queryParam(
                                                            "method",
                                                            "tag.getTopTracks"
                                                    )

                                                    .queryParam(
                                                            "tag",
                                                            tag
                                                    )

                                                    .queryParam(
                                                            "limit",
                                                            safeLimit
                                                    )

                                                    .queryParam(
                                                            "page",
                                                            safePage
                                                    )

                                                    .queryParam(
                                                            "api_key",
                                                            apiKey
                                                    )

                                                    .queryParam(
                                                            "format",
                                                            "json"
                                                    )

                                                    .build()
                            )

                            .retrieve()

                            .body(
                                    LastFmResponse.class
                            );


            // =================================================
            // Response 확인
            // =================================================

            if (
                    response == null
                            ||
                            response.tracks() == null
                            ||
                            response.tracks().track() == null
            ) {

                return List.of();
            }


            List<LastFmTrackDto> result =
                    new ArrayList<>();


            List<Track> tracks =
                    response
                            .tracks()
                            .track();


            // =================================================
            // Last.fm → DTO
            // =================================================

            for (
                    int i = 0;
                    i < tracks.size();
                    i++
            ) {

                Track track =
                        tracks.get(i);


                if (
                        track == null
                                ||
                                track.name() == null
                                ||
                                track.name().isBlank()
                                ||
                                track.artist() == null
                                ||
                                track.artist().name() == null
                                ||
                                track.artist().name().isBlank()
                ) {

                    continue;
                }


                // =============================================
                // 전체 순위 계산
                // =============================================

                int rank =
                        (
                                (safePage - 1)
                                        * safeLimit
                        )
                                + i
                                + 1;


                // =============================================
                // tag.getTopTracks에서 받은 이미지
                // =============================================

                String imageUrl =
                        findBestImage(
                                track.image()
                        );


                LastFmTrackDto music =
                        new LastFmTrackDto(

                                track.name(),

                                track.artist().name(),

                                tag,

                                rank,

                                imageUrl
                        );


                result.add(
                        music
                );
            }


            // =================================================
            // Console
            // =================================================

            System.out.println(
                    "========================================="
            );

            System.out.println(
                    "[Last.fm Tag 음악 조회 성공]"
            );

            System.out.println(
                    "태그 = "
                            + tag
            );

            System.out.println(
                    "페이지 = "
                            + safePage
            );

            System.out.println(
                    "조회 곡 수 = "
                            + result.size()
            );

            System.out.println(
                    "========================================="
            );


            return result;


        } catch (Exception e) {

            System.err.println(
                    "========================================="
            );

            System.err.println(
                    "[Last.fm Tag 음악 조회 실패]"
            );

            System.err.println(
                    "태그 = "
                            + tag
            );

            System.err.println(
                    "페이지 = "
                            + safePage
            );

            System.err.println(
                    "에러 = "
                            + e.getMessage()
            );

            System.err.println(
                    "========================================="
            );


            return List.of();
        }
    }


    // =====================================================
    // ★ 새로 추가
    //
    // 실제 곡 정보에서 앨범 커버 조회
    //
    // Last.fm
    // track.getInfo
    // =====================================================

    public String getTrackImage(
            String artist,
            String title
    ) {

        // =================================================
        // API KEY 확인
        // =================================================

        if (
                apiKey == null
                        ||
                        apiKey.isBlank()
        ) {

            throw new IllegalStateException(
                    "Last.fm API Key가 설정되지 않았습니다."
            );
        }


        // =================================================
        // Artist / Title 확인
        // =================================================

        if (
                artist == null
                        ||
                        artist.isBlank()
                        ||
                        title == null
                        ||
                        title.isBlank()
        ) {

            return "";
        }


        try {

            // =================================================
            // track.getInfo 호출
            // =================================================

            TrackInfoResponse response =
                    restClient
                            .get()

                            .uri(
                                    uriBuilder ->
                                            uriBuilder

                                                    .path("/2.0/")

                                                    .queryParam(
                                                            "method",
                                                            "track.getInfo"
                                                    )

                                                    .queryParam(
                                                            "artist",
                                                            artist
                                                    )

                                                    .queryParam(
                                                            "track",
                                                            title
                                                    )

                                                    /*
                                                     * Last.fm이
                                                     * 가수명/곡명을 자동 보정
                                                     */
                                                    .queryParam(
                                                            "autocorrect",
                                                            1
                                                    )

                                                    .queryParam(
                                                            "api_key",
                                                            apiKey
                                                    )

                                                    .queryParam(
                                                            "format",
                                                            "json"
                                                    )

                                                    .build()
                            )

                            .retrieve()

                            .body(
                                    TrackInfoResponse.class
                            );


            // =================================================
            // 응답 없음
            // =================================================

            if (
                    response == null
                            ||
                            response.track() == null
            ) {

                return "";
            }


            // =================================================
            // Album 없음
            //
            // 싱글 / 일부 곡은 album 정보가
            // 없을 수도 있음
            // =================================================

            if (
                    response.track().album() == null
            ) {

                System.out.println(
                        "[Last.fm 앨범 정보 없음] "
                                + artist
                                + " - "
                                + title
                );


                return "";
            }


            // =================================================
            // 가장 좋은 이미지 선택
            // =================================================

            String imageUrl =
                    findBestImage(
                            response
                                    .track()
                                    .album()
                                    .image()
                    );


            System.out.println(
                    "[Last.fm 앨범 커버] "
                            + artist
                            + " - "
                            + title
                            + " → "
                            + (
                            imageUrl.isBlank()
                                    ? "이미지 없음"
                                    : "이미지 있음"
                    )
            );


            return imageUrl;


        } catch (Exception e) {

            /*
             * 앨범 이미지 때문에
             * 전체 추천 기능이 실패하면 안 됩니다.
             *
             * 이미지 조회 실패 시 빈 문자열 반환
             * → React에서 fallback 아이콘 표시
             */
            System.err.println(
                    "[Last.fm 앨범 커버 조회 실패] "
                            + artist
                            + " - "
                            + title
            );


            System.err.println(
                    "에러 = "
                            + e.getMessage()
            );


            return "";
        }
    }


    // =====================================================
    // 가장 좋은 이미지 찾기
    // =====================================================

    private String findBestImage(
            List<LastFmImage> images
    ) {

        if (
                images == null
                        ||
                        images.isEmpty()
        ) {

            return "";
        }


        /*
         * Last.fm에서는 보통
         *
         * small
         * medium
         * large
         * extralarge
         *
         * 순서로 내려옵니다.
         *
         * 가장 뒤에 있는 유효 URL부터 사용
         */
        for (
                int i = images.size() - 1;
                i >= 0;
                i--
        ) {

            LastFmImage image =
                    images.get(i);


            if (
                    image != null
                            &&
                            image.url() != null
                            &&
                            !image.url().isBlank()
            ) {

                return image.url();
            }
        }


        return "";
    }


    // =====================================================
    // tag.getTopTracks Response
    // =====================================================

    public record LastFmResponse(

            Tracks tracks

    ) {
    }


    public record Tracks(

            List<Track> track

    ) {
    }


    public record Track(

            String name,

            Artist artist,

            List<LastFmImage> image

    ) {
    }


    public record Artist(

            String name

    ) {
    }


    // =====================================================
    // track.getInfo Response
    // =====================================================

    public record TrackInfoResponse(

            TrackInfoData track

    ) {
    }


    public record TrackInfoData(

            String name,

            AlbumInfo album

    ) {
    }


    public record AlbumInfo(

            String title,

            List<LastFmImage> image

    ) {
    }


    // =====================================================
    // 공통 이미지 구조
    //
    // JSON:
    //
    // {
    //   "#text": "https://...",
    //   "size": "large"
    // }
    // =====================================================

    public record LastFmImage(

            @JsonProperty("#text")
            String url,

            String size

    ) {
    }
}