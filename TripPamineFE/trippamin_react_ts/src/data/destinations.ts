import type { Destination } from "../types"

// 인기 여행지 더미 데이터.
// PopularDestinations.tsx(목록), Detail.tsx(상세), MyPage.tsx(북마크)에서 공통으로 사용합니다.
// 실제 서비스라면 이 배열 대신 백엔드 API에서 받아온 데이터를 사용하면 됩니다.
//
// 필드 설명
// - id          : 상세 페이지 경로(/detail/destination/:id)와 북마크 매칭에 쓰이는 고유 키
// - tag         : 카드 왼쪽 위에 붙는 뱃지 문구 (예: "인기 1위")
// - theme       : 카테고리 필터("바다", "힐링" 등)와 매칭되는 테마 값
// - reason      : "AI가 왜 이 여행지를 추천하는지"를 보여주는 한 줄 문구
// - description : 상세 페이지에 노출되는 여행지 소개 문구
export const destinations: Destination[] = [
  {
    id: "jeju",
    name: "제주도",
    region: "제주특별자치도",
    tag: "인기 1위",
    theme: "바다",
    reason: "이국적인 화산섬 풍경 속에서 힐링하고 싶은 분께 추천해요.",
    description:
      "에메랄드빛 바다와 오름, 화산섬 특유의 이국적인 풍경을 함께 즐길 수 있는 대한민국 대표 여행지입니다.",
    img: "https://images.unsplash.com/photo-1598965402089-897ce52e8355?w=900&h=600&fit=crop&auto=format",
  },
  {
    id: "busan",
    name: "부산",
    region: "경상남도",
    tag: "여름 추천",
    theme: "바다",
    reason: "바다와 도심을 함께 즐기는 짧은 힐링 여행에 잘 맞아요.",
    description: "해운대와 광안리, 감천문화마을까지 바다와 도심이 어우러진 매력적인 항구 도시입니다.",
    img: "https://images.unsplash.com/photo-1469442103015-805bf378b9bc?w=900&h=600&fit=crop&auto=format",
  },
  {
    id: "gyeongju",
    name: "경주",
    region: "경상북도",
    tag: "역사 여행",
    theme: "역사·문화",
    reason: "천년 고도의 유적지를 천천히 둘러보고 싶은 분께 추천해요.",
    description: "천년 신라의 역사가 살아 숨 쉬는 도시. 불국사, 첨성대 등 유적지 탐방에 제격입니다.",
    img: "https://images.unsplash.com/photo-1547036967-23d11aacaee0?w=900&h=600&fit=crop&auto=format",
  },
  {
    id: "gangneung",
    name: "강릉",
    region: "강원도",
    tag: "바다 추천",
    theme: "힐링",
    reason: "커피 한 잔과 함께 여유로운 힐링 여행을 원하는 분께 추천해요.",
    description: "커피거리와 경포호, 안목해변까지 여유로운 힐링 여행을 즐기기 좋은 동해안 도시입니다.",
    img: "https://images.unsplash.com/photo-1559827291-72ee739d0d9a?w=900&h=600&fit=crop&auto=format",
  },
]
