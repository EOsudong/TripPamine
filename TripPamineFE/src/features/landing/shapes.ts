/**
 * =========================================================
 * Landing 3D Particle Shapes
 * =========================================================
 *
 * 랜딩 페이지의 3D 형상을 구성하는 점(파티클) 좌표 모음.
 *
 * 모든 형상은 같은 개수(POINT_COUNT)의 점을 갖고
 * 대략 [-1, 1] 범위의 단위 좌표계를 사용한다.
 * 그래서 스크롤 진행도에 따라 i 번째 점끼리 보간(morph)하면
 * 형상이 자연스럽게 다른 형상으로 변신한다.
 *
 * 0. Sphere  : AI 코어 (초개인화 엔진)
 * 1. Helix   : 라이프 DNA (패턴 학습)
 * 2. Torus   : 궤도 / 여행
 * 3. Bars    : 자산 차트 / 자산관리
 * 4. Cube    : 게임 블록 / 퀘스트 · 커뮤니티
 * 5. Galaxy  : 하나의 플랫폼으로 연결된 세계
 */

export interface Point3 {
    x: number;
    y: number;
    z: number;
}

/** 6 x 6 x 6 큐브 격자와 개수를 맞추기 위해 216개 */
export const POINT_COUNT = 216;

const N = POINT_COUNT;
const TAU = Math.PI * 2;
const GOLDEN = Math.PI * (3 - Math.sqrt(5));


/** 결정적(seeded) 난수 — 새로고침해도 같은 형상 */
function seeded(seed: number) {
    let s = seed;
    return () => {
        s = (s * 16807) % 2147483647;
        return (s - 1) / 2147483646;
    };
}


function sphere(): Point3[] {
    return Array.from({ length: N }, (_, i) => {
        const y = 1 - (i / (N - 1)) * 2;
        const r = Math.sqrt(1 - y * y);
        const theta = GOLDEN * i;
        return {
            x: Math.cos(theta) * r * 0.95,
            y: y * 0.95,
            z: Math.sin(theta) * r * 0.95,
        };
    });
}


function helix(): Point3[] {
    const half = N / 2;
    return Array.from({ length: N }, (_, i) => {
        const strand = i % 2;
        const t = Math.floor(i / 2) / (half - 1);
        const angle = t * TAU * 2.2 + strand * Math.PI;
        return {
            x: Math.cos(angle) * 0.48,
            y: (t - 0.5) * 2.1,
            z: Math.sin(angle) * 0.48,
        };
    });
}


function torus(): Point3[] {
    const ringSegments = 36;
    const tubeSegments = N / ringSegments; // 6
    return Array.from({ length: N }, (_, i) => {
        const u = ((i % ringSegments) / ringSegments) * TAU;
        const v = (Math.floor(i / ringSegments) / tubeSegments) * TAU;
        const R = 0.78;
        const r = 0.24;
        return {
            x: (R + r * Math.cos(v)) * Math.cos(u),
            y: r * Math.sin(v),
            z: (R + r * Math.cos(v)) * Math.sin(u),
        };
    });
}


function bars(): Point3[] {
    // 6 x 6 개의 막대, 막대당 6개의 점
    const rand = seeded(7);
    const heights = Array.from({ length: 36 }, (_, c) => {
        const gx = c % 6;
        const gz = Math.floor(c / 6);
        // 우상향하는 자산 그래프 느낌
        const trend = (gx + gz) / 10;
        return 0.35 + trend * 1.1 + rand() * 0.35;
    });

    return Array.from({ length: N }, (_, i) => {
        const column = Math.floor(i / 6);
        const level = i % 6;
        const gx = column % 6;
        const gz = Math.floor(column / 6);
        const h = heights[column];
        return {
            x: (gx / 5 - 0.5) * 1.5,
            y: -0.75 + (level / 5) * h,
            z: (gz / 5 - 0.5) * 1.5,
        };
    });
}


function cube(): Point3[] {
    return Array.from({ length: N }, (_, i) => {
        const x = i % 6;
        const y = Math.floor(i / 6) % 6;
        const z = Math.floor(i / 36);
        return {
            x: (x / 5 - 0.5) * 1.25,
            y: (y / 5 - 0.5) * 1.25,
            z: (z / 5 - 0.5) * 1.25,
        };
    });
}


function galaxy(): Point3[] {
    const rand = seeded(42);
    const arms = 3;
    return Array.from({ length: N }, (_, i) => {
        const arm = i % arms;
        const t = Math.floor(i / arms) / (N / arms);
        const radius = Math.sqrt(t) * 1.15;
        const angle = t * TAU * 1.6 + (arm / arms) * TAU;
        const spread = (rand() - 0.5) * 0.12;
        return {
            x: Math.cos(angle) * radius + spread,
            y: (rand() - 0.5) * 0.12 * (1 - t),
            z: Math.sin(angle) * radius + spread,
        };
    });
}


export const SHAPES: Point3[][] = [
    sphere(),
    helix(),
    torus(),
    bars(),
    cube(),
    galaxy(),
];


/**
 * 점마다 고정된 색상.
 * 인덱스를 따라 violet → cyan → pink 로 흐르는 그라데이션.
 */
export const POINT_COLORS: [number, number, number][] = Array.from(
    { length: N },
    (_, i) => {
        const t = i / (N - 1);
        const stops: [number, number, number][] = [
            [139, 124, 255], // violet
            [34, 211, 238], // cyan
            [244, 114, 182], // pink
        ];
        const seg = t < 0.5 ? 0 : 1;
        const lt = t < 0.5 ? t * 2 : (t - 0.5) * 2;
        const a = stops[seg];
        const b = stops[seg + 1];
        return [
            Math.round(a[0] + (b[0] - a[0]) * lt),
            Math.round(a[1] + (b[1] - a[1]) * lt),
            Math.round(a[2] + (b[2] - a[2]) * lt),
        ];
    },
);
