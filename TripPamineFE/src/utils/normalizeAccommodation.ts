/**
 * 실제 마이리얼트립 숙소 검색 성공 응답(2026-10-21) 기준 정규화 모듈.
 * 값이 0인 판매가는 무료 숙박이 아닌 가격 미확인으로 처리한다.
 */
export interface AccommodationCard {
  key: string;
  itemId: string;
  name: string;
  price: number | null;
  originalPrice: number | null;
  starRating: number | null;
  reviewScore: number | null;
  reviewCount: number | null;
  imageUrl: string | null;
  productUrl: string | null;
}

export interface AccommodationResult {
  cards: AccommodationCard[];
  totalCount: number;
  page: number;
  size: number;
  schemaUnverified: boolean;
}

function asObject(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

function nonNegativeInt(value: unknown): number | null {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0 ? value : null;
}

function positivePrice(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) && value > 0 ? value : null;
}

function reviewScore(value: unknown): number | null {
  // 실응답은 "4.4"처럼 문자열이므로 숫자/문자열 모두 정확히 판별한다.
  const numeric = typeof value === "string" && /^\d(?:\.\d+)?$/.test(value.trim())
    ? Number(value.trim())
    : typeof value === "number" ? value : NaN;
  return Number.isFinite(numeric) && numeric >= 0 && numeric <= 5 ? numeric : null;
}

function safeHttpsUrl(value: unknown): string | null {
  if (typeof value !== "string") return null;
  try {
    const url = new URL(value);
    return url.protocol === "https:" && !url.username && !url.password && !url.port
      ? url.href : null;
  } catch {
    return null;
  }
}

/**
 * 프론트에서도 숙소 ID/URL/날짜/인원을 확인한다.
 * 실제 제휴 발급 직전 백엔드에서 같은 범위를 다시 검증한다.
 */
export function safeAccommodationProductUrl(value: string | null, itemId: string): string | null {
  if (!value || !/^\d{1,20}$/.test(itemId)) return null;
  try {
    const url = new URL(value);
    if (url.protocol !== "https:" || url.hostname !== "accommodation.myrealtrip.com"
        || url.port || url.username || url.password || url.hash
        || url.pathname !== `/union/products/${itemId}`) return null;
    const query = url.search.startsWith("?") ? url.search.slice(1) : "";
    if (!/^checkIn=\d{4}-\d{2}-\d{2}&checkOut=\d{4}-\d{2}-\d{2}&adultCount=(?:[1-9]|1\d|20)&childCount=0&childAges=$/.test(query)) return null;
    return url.href;
  } catch {
    return null;
  }
}

/** 검증된 data.items 형태 외에는 다른 배열을 상품으로 추측하지 않는다. */
export function normalizeAccommodation(raw: unknown): AccommodationResult {
  const root = asObject(raw);
  const data = asObject(root?.data);
  const result = asObject(root?.result);
  const rows = data?.items;

  if (!root || !data || !Array.isArray(rows) || result?.status !== 200) {
    return { cards: [], totalCount: 0, page: 0, size: 0, schemaUnverified: true };
  }

  const totalCount = nonNegativeInt(data.totalCount) ?? rows.length;
  const page = nonNegativeInt(data.page) ?? 0;
  const size = nonNegativeInt(data.size) ?? rows.length;
  const seen = new Set<string>();
  const cards: AccommodationCard[] = [];

  for (const item of rows) {
    const row = asObject(item);
    if (!row) continue;
    const id = nonNegativeInt(row.itemId);
    const name = typeof row.itemName === "string" ? row.itemName.trim() : "";
    if (id === null || id === 0 || !name || seen.has(String(id))) continue;

    const itemId = String(id);
    seen.add(itemId);
    const price = positivePrice(row.salePrice);
    const originalPrice = positivePrice(row.originalPrice);
    const productUrl = safeAccommodationProductUrl(safeHttpsUrl(row.productUrl), itemId);
    const rawStars = nonNegativeInt(row.starRating);

    cards.push({
      key: itemId,
      itemId,
      name,
      price,
      originalPrice: originalPrice !== null && price !== null && originalPrice > price ? originalPrice : null,
      starRating: rawStars !== null && rawStars >= 1 && rawStars <= 5 ? rawStars : null,
      reviewScore: reviewScore(row.reviewScore),
      reviewCount: nonNegativeInt(row.reviewCount),
      imageUrl: safeHttpsUrl(row.imageUrl),
      productUrl,
    });
  }

  return {
    cards,
    totalCount,
    page,
    size,
    schemaUnverified: rows.length > 0 && cards.length === 0,
  };
}
