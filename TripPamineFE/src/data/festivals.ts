import type { Festival } from "../types"

// 전국 축제/행사 더미 데이터.
// FestivalSection.tsx(목록), Detail.tsx(상세), MyPage.tsx(북마크)에서 공통으로 사용합니다.
// 실제 서비스라면 이 배열 대신 공공데이터포털 등 외부 API에서 받아온 데이터를 사용하면 됩니다.
//
// 필드 설명
// - id       : 상세 페이지 경로(/detail/festival/:id)와 북마크 매칭에 쓰이는 고유 키
// - status   : "ongoing"(진행 중) | "upcoming"(예정) | "ended"(종료) — 카드 뱃지 색상/문구 결정
// - dday     : upcoming일 때 "D-12"처럼 표시할 남은 일수 (더미 값, 실제로는 startDate로 계산 가능)
// - category : 필터 탭(categories.ts의 festivalFilterTabs)과 매칭되는 값
// - tags     : 카드 하단에 노출되는 해시태그 목록
export const festivals: Festival[] = [
  {
    id: "jinju-lantern",
    name: "진주 남강 유등축제",
    location: "경상남도 진주",
    startDate: "2026-10-01",
    endDate: "2026-10-12",
    category: "문화·예술",
    status: "upcoming",
    dday: 58,
    description: "남강을 수놓는 수만 개의 유등이 만들어내는 환상적인 야경을 감상할 수 있는 축제입니다.",
    img: "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=900&h=600&fit=crop&auto=format",
    tags: ["야간", "가족"],
  },
  {
    id: "boryeong-mud",
    name: "보령 머드 축제",
    location: "충청남도 보령",
    startDate: "2026-07-18",
    endDate: "2026-07-27",
    category: "체험",
    status: "ongoing",
    dday: 0,
    description: "머드 슬라이드와 진흙 씨름까지, 온 가족이 함께 즐기는 대한민국 대표 여름 축제입니다.",
    img: "https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?w=900&h=600&fit=crop&auto=format",
    tags: ["여름", "체험"],
  },
  {
    id: "andong-mask",
    name: "안동 국제탈춤 페스티벌",
    location: "경상북도 안동",
    startDate: "2026-09-25",
    endDate: "2026-10-04",
    category: "전통·문화",
    status: "upcoming",
    dday: 52,
    description: "국내외 탈춤 공연단이 한자리에 모이는 안동의 대표 전통 문화 축제입니다.",
    img: "https://images.unsplash.com/photo-1547036967-23d11aacaee0?w=900&h=600&fit=crop&auto=format",
    tags: ["전통", "문화"],
  },
  {
    id: "yeosu-fireworks",
    name: "여수 밤바다 불꽃 축제",
    location: "전라남도 여수",
    startDate: "2026-08-08",
    endDate: "2026-08-10",
    category: "불꽃·야간",
    status: "upcoming",
    dday: 4,
    description: "여수 밤바다를 화려하게 수놓는 불꽃놀이와 함께하는 여름밤 축제입니다.",
    img: "https://images.unsplash.com/photo-1467810563316-b5476525c0f9?w=900&h=600&fit=crop&auto=format",
    tags: ["불꽃", "야간"],
  },
  {
    id: "gangneung-coffee",
    name: "강릉 커피 축제",
    location: "강원도 강릉",
    startDate: "2026-10-08",
    endDate: "2026-10-12",
    category: "음식·음료",
    status: "upcoming",
    dday: 65,
    description: "강릉 커피거리를 배경으로 다양한 원두와 브루잉 체험을 즐길 수 있는 축제입니다.",
    img: "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=900&h=600&fit=crop&auto=format",
    tags: ["커피", "힐링"],
  },
  {
    id: "hadong-tea",
    name: "하동 야생차 문화 축제",
    location: "경상남도 하동",
    startDate: "2026-05-14",
    endDate: "2026-05-18",
    category: "자연·체험",
    status: "ended",
    dday: -77,
    description: "지리산 자락에서 펼쳐지는 하동 전통 야생차 문화 체험 축제입니다.",
    img: "https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=900&h=600&fit=crop&auto=format",
    tags: ["차", "자연"],
  },
]
