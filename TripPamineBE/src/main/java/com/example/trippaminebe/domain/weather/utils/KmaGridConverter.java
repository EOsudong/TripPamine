package com.example.trippaminebe.domain.weather.utils;

// 현재위치(위경도)를 기상청 격자 좌표(nx, ny)로 바꿔주는 변환기
public class KmaGridConverter {

  // 기상청이 정한 고정값
  private static final double RE = 6371.00877;  // 지구 반경(km)
  private static final double GRID = 5.0;       // 격자 한 칸의 크기(km)
  private static final double SLAT1 = 30.0;     // 투영 기준 위도 1
  private static final double SLAT2 = 60.0;     // 투영 기준 위도 2
  private static final double OLON = 126.0;     // 기준점 경도
  private static final double OLAT = 38.0;      // 기준점 위도
  private static final double XO = 43;          // 기준점의 X 격자 좌표
  private static final double YO = 136;         // 기준점의 Y 격자 좌표

  // 위 값으로 계산해 두는 투영 상수
  private static final double DEGRAD = Math.PI / 180.0; // Radian
  private static final double RE_GRID = RE / GRID;
  private static final double SN;
  private static final double SF;
  private static final double RO;

  static {
    double slat1 = SLAT1 * DEGRAD;
    double slat2 = SLAT2 * DEGRAD;
    double olat = OLAT * DEGRAD;

    double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
    sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
    double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
    sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;
    double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
    ro = RE_GRID * sf / Math.pow(ro, sn);

    SN = sn;
    SF = sf;
    RO = ro;
  }

  // 결과값 저장용
  public record Grid(int nx, int ny) {}

  // 위도, 경도 → 격자 변환
  public static Grid toGrid(double latitude, double longitude) {
    double ra = Math.tan(Math.PI * 0.25 + latitude * DEGRAD * 0.5);
    ra = RE_GRID * SF / Math.pow(ra, SN);

    double theta = longitude * DEGRAD - OLON * DEGRAD;
    if (theta > Math.PI) theta -= 2.0 * Math.PI;
    if (theta < -Math.PI) theta += 2.0 * Math.PI;
    theta *= SN;

    int nx = (int) Math.floor(ra * Math.sin(theta) + XO + 0.5);
    int ny = (int) Math.floor(RO - ra * Math.cos(theta) + YO + 0.5);
    return new Grid(nx, ny);
  } ///toGrid

} ///KmaGridConverter