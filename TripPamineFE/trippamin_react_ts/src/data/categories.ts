// 여행 테마/카테고리 관련 더미 상수 모음.
// 실제 서비스에서는 이 배열들을 서버(API) 응답으로 대체하면 됩니다.

// PopularDestinations.tsx의 상단 필터 버튼("전체", "바다", "산/자연"...)에서 사용
export const categories: string[] = ["전체", "바다", "산/자연", "역사·문화", "미식", "힐링", "액티비티"]

// FestivalSection.tsx의 상단 필터 탭("전체", "진행 중", "예정"...)에서 사용
export const festivalFilterTabs: string[] = ["전체", "진행 중", "예정", "문화·예술", "체험", "음식·음료"]

// Hero.tsx(AI 여행 조건 입력 폼)의 "여행 유형" 선택지이자,
// 히어로 하단의 "AI 테마 추천 칩" 목록으로도 동시에 사용됨
export const travelTypes: string[] = ["힐링·휴양", "액티비티·모험", "맛집 탐방", "역사·문화", "자연·트레킹", "사진 여행"]
