export interface TravelMusicRequest {
  placeType: string;
  weather: string;
  timeOfDay: string;
  travelStyle: string;
  preferredGenre: string;
}


export interface Music {
  title: string;
  artist: string;
  mood: string;
  youtubeUrl: string;
  spotifyUrl: string;

  // Last.fm에서 전달받은 이미지
  imageUrl: string;
}


export interface TravelMusicResponse {
  theme: string;
  emoji: string;
  description: string;
  songs: Music[];
}


export async function getTravelMusicRecommendation(
  request: TravelMusicRequest
): Promise<TravelMusicResponse> {

  const token =
    localStorage.getItem("accessToken") ??
    localStorage.getItem("token") ??
    sessionStorage.getItem("accessToken") ??
    sessionStorage.getItem("token");


  console.log(
    "음악 추천 요청 토큰 존재 여부:",
    !!token
  );


  const response = await fetch(
    "http://localhost:8080/api/music/recommend",
    {
      method: "POST",

      headers: {
        "Content-Type": "application/json",

        ...(token
          ? {
              Authorization:
                `Bearer ${token}`,
            }
          : {}),
      },

      body: JSON.stringify(
        request
      ),
    }
  );


  console.log(
    "음악 추천 API 상태코드:",
    response.status
  );


  if (!response.ok) {

    const errorText =
      await response.text();


    console.error(
      "음악 추천 API 오류:",
      response.status,
      errorText
    );


    throw new Error(
      `음악 추천 요청 실패: ${response.status}`
    );
  }


  const data: TravelMusicResponse =
    await response.json();


  console.log(
    "음악 추천 결과:",
    data
  );


  return data;
}