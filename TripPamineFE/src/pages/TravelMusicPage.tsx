import { useState } from "react";

import Header from "../components/Header";
import Sidebar from "../components/Sidebar";

import {
  getTravelMusicRecommendation,
  type TravelMusicResponse,
} from "../api/travelMusicApi";


interface Option {
  label: string;
  value: string;
}

interface OptionGroupProps {
  title: string;
  description?: string;
  options: Option[];
  selected: string;
  onSelect: (value: string) => void;
}


export default function TravelMusicPage() {

  // =====================================================
  // Sidebar
  // =====================================================

  const [sidebarOpen, setSidebarOpen] =
  useState(false);
  // =====================================================
  // 사용자 선택
  // =====================================================

  const [placeType, setPlaceType] =
    useState("SEA");

  const [weather, setWeather] =
    useState("SUNNY");

  const [timeOfDay, setTimeOfDay] =
    useState("AFTERNOON");

  const [travelStyle, setTravelStyle] =
    useState("DRIVE");

  const [preferredGenre, setPreferredGenre] =
    useState("INDIE");


  // =====================================================
  // 추천 결과
  // =====================================================

  const [result, setResult] =
    useState<TravelMusicResponse | null>(null);

  const [loading, setLoading] =
    useState(false);

  const [error, setError] =
    useState("");


  // =====================================================
  // 음악 추천
  // =====================================================

  const handleRecommend = async () => {

    try {

      setLoading(true);
      setError("");

      const response =
        await getTravelMusicRecommendation({
          placeType,
          weather,
          timeOfDay,
          travelStyle,
          preferredGenre,
        });

      setResult(response);

    } catch (err) {

      console.error(
        "여행 음악 추천 오류:",
        err
      );

      setError(
        "음악 추천 결과를 불러오지 못했습니다."
      );

    } finally {

      setLoading(false);

    }
  };


  return (

   <div className="min-h-screen bg-slate-50 text-slate-900">


      {/* ================================================= */}
      {/* Sidebar */}
      {/* ================================================= */}

      <Sidebar
        open={sidebarOpen}
        onClose={() =>
          setSidebarOpen(false)
        }
      />


      {/* ================================================= */}
      {/* Header */}
      {/* ================================================= */}

      <Header
        sidebarOpen={sidebarOpen}
        onToggleSidebar={() =>
          setSidebarOpen(
            (prev) => !prev
          )
        }
      />


      {/* ================================================= */}
      {/* Main */}
      {/* ================================================= */}

      <main
        className={`
          min-h-screen
          pt-16
          transition-all
          duration-300

          ${
            sidebarOpen
              ? "lg:pl-64"
              : "lg:pl-16"
          }
        `}
      >


        {/* ================================================= */}
        {/* 상단 Hero */}
        {/* ================================================= */}

        <section
          className="
            relative
            overflow-hidden

            border-b
            border-sky-100

            bg-gradient-to-br
            from-sky-50
            via-white
            to-indigo-50
          "
        >

          {/* 배경 장식 */}
          <div
            className="
              pointer-events-none
              absolute
              -right-24
              -top-24
              h-72
              w-72
              rounded-full
              bg-sky-200/30
              blur-3xl
            "
          />

          <div
            className="
              pointer-events-none
              absolute
              -bottom-32
              left-1/3
              h-64
              w-64
              rounded-full
              bg-indigo-200/20
              blur-3xl
            "
          />


          <div
            className="
              relative
              mx-auto
              w-full
              max-w-[1500px]

              px-4
              py-10

              md:px-6
              md:py-14

              xl:px-10
            "
          >

            <div
              className="
                flex
                flex-col
                gap-6

                lg:flex-row
                lg:items-center
                lg:justify-between
              "
            >

              <div className="max-w-[760px]">

                {/* Badge */}
                <div
                  className="
                    mb-4
                    inline-flex
                    items-center
                    gap-2

                    rounded-full
                    border
                    border-sky-200

                    bg-white/80

                    px-3
                    py-1.5

                    text-[11px]
                    font-black
                    tracking-[0.12em]
                    text-sky-600

                    shadow-sm
                    backdrop-blur
                  "
                >
                  <span
                    className="
                      h-2
                      w-2
                      rounded-full
                      bg-sky-500
                    "
                  />

                  AI TRAVEL PLAYLIST
                </div>


                <h1
                  className="
                    text-[30px]
                    font-black
                    tracking-[-0.04em]
                    text-slate-900

                    md:text-[38px]
                  "
                >
                  여행의 순간을
                  <span
                    className="
                      bg-gradient-to-r
                      from-sky-500
                      to-indigo-500

                      bg-clip-text
                      text-transparent
                    "
                  >
                    {" "}음악으로 연결하세요
                  </span>
                </h1>


                <p
                  className="
                    mt-4
                    max-w-[660px]

                    text-sm
                    leading-7
                    text-slate-500
                  "
                >
                  여행 장소, 날씨, 시간,
                  여행 스타일과 개인 음악 취향을 분석해
                  지금 이 순간에 어울리는 플레이리스트를 추천합니다.
                </p>

              </div>


              {/* Hero Icon */}
              <div
                className="
                  hidden
                  h-28
                  w-28
                  shrink-0
                  items-center
                  justify-center

                  rounded-[30px]

                  bg-gradient-to-br
                  from-sky-500
                  to-indigo-500

                  text-white

                  shadow-[0_20px_50px_rgba(79,70,229,0.22)]

                  lg:flex
                "
              >

                <svg
                  className="h-12 w-12"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                  strokeWidth={1.5}
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M9 18V5l10-2v13"
                  />

                  <circle
                    cx="6"
                    cy="18"
                    r="3"
                  />

                  <circle
                    cx="16"
                    cy="16"
                    r="3"
                  />
                </svg>

              </div>

            </div>

          </div>

        </section>


        {/* ================================================= */}
        {/* Content */}
        {/* ================================================= */}

        <div
          className="
            mx-auto
            w-full
            max-w-[1500px]

            px-4
            pb-20
            pt-8

            md:px-6
            md:pt-10

            xl:px-10
          "
        >


          <div
            className="
              grid
              grid-cols-1
              gap-6

              xl:grid-cols-[430px_minmax(0,1fr)]
            "
          >


            {/* ================================================= */}
            {/* 왼쪽 - 여행 조건 */}
            {/* ================================================= */}

            <section
              className="
                self-start

                overflow-hidden
                rounded-3xl

            border
                border-slate-200/70

                bg-white

                shadow-[0_8px_30px_rgba(15,23,42,0.05)]
              "
            >


              {/* 카드 헤더 */}
              <div
                className="
                  border-b
                  border-slate-100

                 bg-gradient-to-r
                    from-sky-50/80
                    via-white
                    to-indigo-50/70

                  px-6
                  py-5
                "
              >

                <div
                  className="
                    flex
                    items-center
                    gap-3
                  "
                >

                  <div
                    className="
                      flex
                      h-10
                      w-10
                      items-center
                      justify-center

                      rounded-xl

                      bg-gradient-to-br
                      from-sky-500
                      to-indigo-500

                      text-white

                      shadow-md
                      shadow-sky-100
                    "
                  >
                    <svg
                      className="h-5 w-5"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                      strokeWidth={1.8}
                    >
                      <circle
                        cx="12"
                        cy="12"
                        r="3"
                      />

                      <path d="M12 2v3" />
                      <path d="M12 19v3" />
                      <path d="M2 12h3" />
                      <path d="M19 12h3" />

                      <path d="M4.9 4.9l2.1 2.1" />
                      <path d="M17 17l2.1 2.1" />
                    </svg>
                  </div>


                  <div>
                    <h2
                      className="
                        text-[15px]
                        font-black
                        text-slate-800
                      "
                    >
                      여행 분위기 설정
                    </h2>

                    <p
                      className="
                        mt-1
                        text-[11px]
                        text-slate-400
                      "
                    >
                      현재 여행과 음악 취향을 선택해주세요.
                    </p>
                  </div>

                </div>

              </div>


              {/* Options */}
              <div className="space-y-7 p-6">


                <OptionGroup
                  title="여행 장소"
                  description="어디에서 음악을 듣고 있나요?"
                  selected={placeType}
                  onSelect={setPlaceType}
                  options={[
                    {
                      label: "바다",
                      value: "SEA",
                    },
                    {
                      label: "산",
                      value: "MOUNTAIN",
                    },
                    {
                      label: "도시",
                      value: "CITY",
                    },
                  ]}
                />


                <OptionGroup
                  title="날씨"
                  description="현재 여행지의 날씨를 선택해주세요."
                  selected={weather}
                  onSelect={setWeather}
                  options={[
                    {
                      label: "맑음",
                      value: "SUNNY",
                    },
                    {
                      label: "비",
                      value: "RAINY",
                    },
                    {
                      label: "흐림",
                      value: "CLOUDY",
                    },
                    {
                      label: "눈",
                      value: "SNOWY",
                    },
                  ]}
                />


                <OptionGroup
                  title="여행 시간"
                  description="음악을 듣고 있는 시간대입니다."
                  selected={timeOfDay}
                  onSelect={setTimeOfDay}
                  options={[
                    {
                      label: "아침",
                      value: "MORNING",
                    },
                    {
                      label: "오후",
                      value: "AFTERNOON",
                    },
                    {
                      label: "저녁",
                      value: "EVENING",
                    },
                    {
                      label: "밤",
                      value: "NIGHT",
                    },
                  ]}
                />


                <OptionGroup
                  title="여행 스타일"
                  description="현재 즐기고 있는 여행 방식을 선택해주세요."
                  selected={travelStyle}
                  onSelect={setTravelStyle}
                  options={[
                    {
                      label: "드라이브",
                      value: "DRIVE",
                    },
                    {
                      label: "산책",
                      value: "WALK",
                    },
                    {
                      label: "휴식",
                      value: "RELAX",
                    },
                    {
                      label: "데이트",
                      value: "DATE",
                    },
                    {
                      label: "액티브",
                      value: "ACTIVE",
                    },
                  ]}
                />


                <OptionGroup
                  title="선호 장르"
                  description="평소 즐겨 듣는 음악 장르입니다."
                  selected={preferredGenre}
                  onSelect={setPreferredGenre}
                  options={[
                    {
                      label: "Indie",
                      value: "INDIE",
                    },
                    {
                      label: "R&B",
                      value: "RNB",
                    },
                    {
                      label: "Hip-Hop",
                      value: "HIPHOP",
                    },
                    {
                      label: "Pop",
                      value: "POP",
                    },
                    {
                      label: "Jazz",
                      value: "JAZZ",
                    },
                  ]}
                />


                {/* 추천 버튼 */}
                <button
                  type="button"
                  disabled={loading}
                  onClick={handleRecommend}
                  className="
                    mt-3
                    flex
                    w-full
                    items-center
                    justify-center
                    gap-2

                    rounded-xl

                    bg-gradient-to-r
                    from-sky-500
                    to-indigo-500

                    px-5
                    py-3.5

                    text-sm
                    font-black
                    text-white

                    shadow-lg
                    shadow-sky-100

                    transition-all
                    duration-200

                    hover:-translate-y-0.5
                    hover:shadow-xl

                    disabled:cursor-not-allowed
                    disabled:opacity-60
                  "
                >

                  {loading ? (

                    <>
                      <span
                        className="
                          h-4
                          w-4
                          animate-spin

                          rounded-full
                          border-2
                          border-white/30
                          border-t-white
                        "
                      />

                      플레이리스트 분석 중...
                    </>

                  ) : (

                    <>
                      <svg
                        className="h-4 w-4"
                        fill="none"
                        stroke="currentColor"
                        viewBox="0 0 24 24"
                        strokeWidth={2}
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          d="M9 18V5l10-2v13"
                        />
                        <circle cx="6" cy="18" r="3" />
                        <circle cx="16" cy="16" r="3" />
                      </svg>

                      맞춤 플레이리스트 추천
                    </>

                  )}

                </button>


                {error && (
                  <div
                    className="
                      rounded-xl
                      border
                      border-rose-100
                      bg-rose-50

                      px-4
                      py-3

                      text-xs
                      font-medium
                      text-rose-600
                    "
                  >
                    {error}
                  </div>
                )}

              </div>

            </section>


            {/* ================================================= */}
            {/* 오른쪽 결과 */}
            {/* ================================================= */}

            <section className="min-w-0">


              {!result ? (

                /* ================================================= */
                /* 추천 전 */
                /* ================================================= */

                <div
                  className="
                    flex
                    min-h-[650px]
                    flex-col
                    items-center
                    justify-center

                    overflow-hidden
                    rounded-3xl

                    border
                    border-slate-100

                    bg-white

                    px-7
                    text-center

                    shadow-[0_10px_40px_rgba(15,23,42,0.06)]
                  "
                >

                  <div
                    className="
                      flex
                      h-16
                      w-16
                      items-center
                      justify-center

                      rounded-2xl

                      bg-gradient-to-br
                      from-sky-500
                      to-indigo-500

                      text-white

                      shadow-xl
                      shadow-sky-100
                    "
                  >

                    <svg
                      className="h-7 w-7"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                      strokeWidth={1.7}
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        d="M9 18V5l10-2v13"
                      />
                      <circle cx="6" cy="18" r="3" />
                      <circle cx="16" cy="16" r="3" />
                    </svg>

                  </div>


                  <p
                    className="
                      mt-6
                      text-[10px]
                      font-black
                      tracking-[0.18em]
                      text-sky-500
                    "
                  >
                    PERSONALIZED MUSIC
                  </p>


                  <h2
                    className="
                      mt-2
                      text-xl
                      font-black
                      tracking-[-0.03em]
                      text-slate-800

                      md:text-2xl
                    "
                  >
                    나만의 여행 플레이리스트를 만들어보세요
                  </h2>


                  <p
                    className="
                      mt-3
                      max-w-[470px]

                      text-sm
                      leading-6
                      text-slate-400
                    "
                  >
                    여행 환경과 음악 취향을 기반으로
                    각 음악의 적합도를 계산해 가장 어울리는 곡을 추천합니다.
                  </p>


                  {/* 설명 카드 */}
                  <div
                    className="
                      mt-8
                      grid
                      w-full
                      max-w-[620px]

                      grid-cols-1
                      gap-3

                      sm:grid-cols-3
                    "
                  >

                    <InfoCard
                      number="01"
                      title="여행 분석"
                      text="장소·날씨·시간 분석"
                    />

                    <InfoCard
                      number="02"
                      title="취향 분석"
                      text="선호 장르·스타일 반영"
                    />

                   <InfoCard
                        number="03"
                        title="음악 추천"
                        text="적합도 높은 TOP 10"
                        />

                  </div>

                </div>

              ) : (

                /* ================================================= */
                /* 추천 결과 */
                /* ================================================= */

                <div
                  className="
                    overflow-hidden
                    rounded-3xl

                    border
                    border-slate-100

                    bg-white

                    shadow-[0_10px_40px_rgba(15,23,42,0.06)]
                  "
                >


                  {/* Result Hero */}
                  <div
                    className="
                      relative
                      overflow-hidden

                     bg-gradient-to-br
                    from-sky-500
                    via-blue-500
                    to-indigo-500
                      px-6
                      py-8

                      text-white

                      md:px-8
                    "
                  >

                    <div
                      className="
                        pointer-events-none
                        absolute
                        -right-16
                        -top-16
                        h-48
                        w-48
                        rounded-full
                        bg-white/10
                      "
                    />


                    <div
                      className="
                        relative
                        flex
                        flex-col
                        gap-5

                        md:flex-row
                        md:items-end
                        md:justify-between
                      "
                    >

                      <div>

                        <p
                          className="
                            text-[10px]
                            font-black
                            tracking-[0.2em]
                            text-white/70
                          "
                        >
                          YOUR TRAVEL PLAYLIST
                        </p>


                        <h2
                          className="
                            mt-2
                            text-[26px]
                            font-black
                            tracking-[-0.04em]

                            md:text-[32px]
                          "
                        >
                          {result.theme}
                        </h2>


                        <p
                          className="
                            mt-3
                            max-w-[650px]

                            text-sm
                            leading-6
                            text-white/80
                          "
                        >
                          {result.description}
                        </p>

                      </div>


                    <div
  className="
    flex
    flex-col
    items-start
    gap-3

    md:items-end
  "
>

  {/* 추천 정보 */}
  <div
    className="
      flex
      flex-wrap
      gap-2
    "
  >

    <span
      className="
        rounded-full
        bg-white/15

        px-3
        py-1.5

        text-[10px]
        font-black

        backdrop-blur
      "
    >
      {preferredGenre}
    </span>


    <span
      className="
        rounded-full
        bg-white/15

        px-3
        py-1.5

        text-[10px]
        font-black

        backdrop-blur
      "
    >
      {result.songs.length} TRACKS
    </span>

  </div>


           
            {/* 다시 추천 */}
                <button
                type="button"
                disabled={loading}
                onClick={handleRecommend}
                className="
                    inline-flex
                    items-center
                    justify-center
                    gap-2

                    rounded-xl

                    bg-gradient-to-r
                    from-sky-500
                    to-indigo-500

                    px-4
                    py-2.5

                    text-[11px]
                    font-black
                    text-white

                    shadow-md
                    shadow-sky-200/40

                    transition-all
                    duration-200

                    hover:-translate-y-0.5
                    hover:shadow-lg

                    disabled:cursor-not-allowed
                    disabled:opacity-60
                "
                >

                <svg
                    className={`
                    h-4
                    w-4

                    ${loading ? "animate-spin" : ""}
                    `}
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                    strokeWidth={2}
                >
                    <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M20 11a8.1 8.1 0 0 0-15.5-2M4 5v4h4"
                    />

                    <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M4 13a8.1 8.1 0 0 0 15.5 2M20 19v-4h-4"
                    />
                </svg>


                {loading
                    ? "새 플레이리스트 만드는 중..."
                    : "다시 추천"}

                </button>

        
                      </div>

                    </div>

                  </div>


                  {/* 분석 조건 */}
                  <div
                    className="
                      flex
                      flex-wrap
                      gap-2

                      border-b
                      border-slate-100

                      bg-sky-50/40

                      px-6
                      py-4

                      md:px-8
                    "
                  >

                    <ConditionBadge
                      label={getPlaceLabel(placeType)}
                    />

                    <ConditionBadge
                      label={getWeatherLabel(weather)}
                    />

                    <ConditionBadge
                      label={getTimeLabel(timeOfDay)}
                    />

                    <ConditionBadge
                      label={getStyleLabel(travelStyle)}
                    />

                  </div>


                  {/* Track Header */}
                  <div
                    className="
                      hidden
                      grid-cols-[50px_minmax(0,1fr)_170px_190px]

                      border-b
                      border-slate-100

                      px-6
                      py-3

                      text-[10px]
                      font-black
                      uppercase
                      tracking-[0.14em]
                      text-slate-400

                      md:grid
                      md:px-8
                    "
                  >
                    <span>#</span>
                    <span>Track</span>
                    <span>Mood</span>
                    <span className="text-right">
                      Listen
                    </span>
                  </div>


                 {/* Songs */}
                    <div>

                    {result.songs.map(
                        (song, index) => (

                        <div
                            key={`${song.artist}-${song.title}`}
                            className="
                            group

                            grid
                            grid-cols-[40px_minmax(0,1fr)]

                            gap-x-3
                            gap-y-3

                            border-b
                            border-slate-100

                            px-6
                            py-5

                            transition-all

                            last:border-0

                            hover:bg-sky-50/40

                            md:grid-cols-[50px_minmax(0,1fr)_170px_190px]
                            md:items-center
                            md:gap-0
                            md:px-8
                            "
                        >

                            {/* 번호 */}
                            <div
                            className="
                                text-xs
                                font-black
                                text-sky-500
                            "
                            >
                            {String(index + 1).padStart(
                                2,
                                "0"
                            )}
                            </div>


                            {/* 제목 + 앨범 이미지 */}
                            <div
                            className="
                                flex
                                min-w-0
                                items-center
                                gap-3
                            "
                            >

                            <SongCover
                                imageUrl={song.imageUrl}
                                title={song.title}
                            />


                            <div className="min-w-0">

                                <p
                                className="
                                    truncate
                                    text-sm
                                    font-black
                                    text-slate-800
                                "
                                >
                                {song.title}
                                </p>


                                <p
                                className="
                                    mt-1
                                    truncate
                                    text-xs
                                    text-slate-400
                                "
                                >
                                {song.artist}
                                </p>

                            </div>

                            </div>


                            {/* Mood */}
                            <div
                            className="
                                col-start-2
                                md:col-start-auto
                            "
                            >

                            <span
                                className="
                                inline-flex

                                rounded-full

                                bg-sky-50

                                px-3
                                py-1.5

                                text-[10px]
                                font-bold
                                text-sky-600
                                "
                            >
                                {song.mood}
                            </span>

                            </div>


                            {/* External Buttons */}
                            <div
                            className="
                                col-start-2

                                flex
                                items-center
                                gap-2

                                md:col-start-auto
                                md:justify-end
                            "
                            >

                            <a
                                href={song.youtubeUrl}
                                target="_blank"
                                rel="noreferrer"
                                className="
                                rounded-lg
                                border
                                border-slate-200

                                bg-white

                                px-3
                                py-2

                                text-[11px]
                                font-bold
                                text-slate-600

                                transition

                                hover:border-sky-300
                                hover:bg-sky-50
                                hover:text-sky-600
                                "
                            >
                                YouTube
                            </a>


                            <a
                                href={song.spotifyUrl}
                                target="_blank"
                                rel="noreferrer"
                                className="
                                rounded-lg

                                bg-gradient-to-r
                                from-sky-500
                                to-indigo-500

                                px-3
                                py-2

                                text-[11px]
                                font-bold
                                text-white

                                shadow-sm

                                transition

                                hover:brightness-105
                                "
                            >
                                Spotify
                            </a>

                            </div>

                        </div>

                        )
                    )}

                    </div>

                  {/* Footer */}
                  <div
                    className="
                      flex
                      flex-col
                      gap-1

                      border-t
                      border-slate-100

                      bg-slate-50/70

                      px-6
                      py-4

                      text-[10px]
                      text-slate-400

                      sm:flex-row
                      sm:justify-between

                      md:px-8
                    "
                  >
                    <span>
                      TripPamine은 음원을 직접 저장하지 않습니다.
                    </span>

                    <span>
                      YouTube · Spotify 외부 서비스 연결
                    </span>
                  </div>

                </div>

              )}

            </section>

          </div>

        </div>

      </main>

    </div>
  );
}


// =====================================================
// 옵션 버튼
// =====================================================

function OptionGroup({
  title,
  description,
  options,
  selected,
  onSelect,
}: OptionGroupProps) {

  return (

    <div>

      <div className="mb-3">

        <p
          className="
            text-[12px]
            font-black
            text-slate-700
          "
        >
          {title}
        </p>

        {description && (
          <p
            className="
              mt-1
              text-[10px]
              text-slate-400
            "
          >
            {description}
          </p>
        )}

      </div>


      <div
        className="
          flex
          flex-wrap
          gap-2
        "
      >

        {options.map(
          (option) => {

            const active =
              selected === option.value;

            return (

              <button
                key={option.value}
                type="button"
                onClick={() =>
                  onSelect(option.value)
                }
                className={`
                  rounded-xl
                  border

                  px-3.5
                  py-2.5

                  text-[11px]
                  font-bold

                  transition-all
                  duration-200

                  ${
                    active
                      ? `
                        border-sky-500
                        bg-gradient-to-r
                        from-sky-500
                        to-indigo-500
                        text-white
                        shadow-md
                        shadow-sky-100
                      `
                      : `
                        border-slate-200
                        bg-white
                        text-slate-500

                        hover:border-sky-200
                        hover:bg-sky-50
                        hover:text-sky-600
                      `
                  }
                `}
              >
                {option.label}
              </button>

            );
          }
        )}

      </div>

    </div>
  );
}


// =====================================================
// 안내 카드
// =====================================================

function InfoCard({
  number,
  title,
  text,
}: {
  number: string;
  title: string;
  text: string;
}) {

  return (

    <div
      className="
        rounded-2xl
        border
        border-slate-100

        bg-gradient-to-br
        from-white
        to-sky-50/60

        p-4

        text-left
      "
    >

      <span
        className="
          text-[10px]
          font-black
          tracking-[0.15em]
          text-sky-500
        "
      >
        {number}
      </span>

      <p
        className="
          mt-2
          text-xs
          font-black
          text-slate-700
        "
      >
        {title}
      </p>

      <p
        className="
          mt-1
          text-[10px]
          leading-4
          text-slate-400
        "
      >
        {text}
      </p>

    </div>
  );
}


// =====================================================
// 결과 조건 Badge
// =====================================================

function ConditionBadge({
  label,
}: {
  label: string;
}) {

  return (
    <span
      className="
        rounded-full
        border
        border-sky-100

        bg-white

        px-3
        py-1.5

        text-[10px]
        font-bold
        text-sky-600

        shadow-sm
      "
    >
      {label}
    </span>
  );
}


// =====================================================
// Label 변환
// =====================================================

function getPlaceLabel(
  value: string
) {

  switch (value) {
    case "SEA":
      return "바다";
    case "MOUNTAIN":
      return "산";
    case "CITY":
      return "도시";
    default:
      return value;
  }
}


function getWeatherLabel(
  value: string
) {

  switch (value) {
    case "SUNNY":
      return "맑음";
    case "RAINY":
      return "비";
    case "CLOUDY":
      return "흐림";
    case "SNOWY":
      return "눈";
    default:
      return value;
  }
}


function getTimeLabel(
  value: string
) {

  switch (value) {
    case "MORNING":
      return "아침";
    case "AFTERNOON":
      return "오후";
    case "EVENING":
      return "저녁";
    case "NIGHT":
      return "밤";
    default:
      return value;
  }
}


function getStyleLabel(
  value: string
) {

  switch (value) {
    case "DRIVE":
      return "드라이브";
    case "WALK":
      return "산책";
    case "RELAX":
      return "휴식";
    case "DATE":
      return "데이트";
    case "ACTIVE":
      return "액티브";
    default:
      return value;
  }
}
// =====================================================
// 앨범 / 트랙 Cover
// =====================================================

function SongCover({
  imageUrl,
  title,
}: {
  imageUrl: string;
  title: string;
}) {

  const [imageError, setImageError] =
    useState(false);


  const hasImage =
    imageUrl &&
    imageUrl.trim() !== "" &&
    !imageError;


  // ===================================================
  // 실제 이미지가 있는 경우
  // ===================================================

  if (hasImage) {

    return (

      <img
        src={imageUrl}
        alt={`${title} cover`}
        onError={() =>
          setImageError(true)
        }
        className="
          h-12
          w-12
          shrink-0

          rounded-xl

          border
          border-slate-100

          object-cover

          shadow-sm
        "
      />

    );
  }


  // ===================================================
  // Last.fm 이미지가 없는 경우
  // ===================================================

  return (

    <div
      className="
        flex
        h-12
        w-12
        shrink-0
        items-center
        justify-center

        rounded-xl

        bg-gradient-to-br
        from-sky-100
        to-indigo-100

        text-sky-500

        shadow-sm
      "
    >

      <svg
        className="h-5 w-5"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
        strokeWidth={1.8}
      >

        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="M9 18V5l10-2v13"
        />

        <circle
          cx="6"
          cy="18"
          r="3"
        />

        <circle
          cx="16"
          cy="16"
          r="3"
        />

      </svg>

    </div>
  );
}