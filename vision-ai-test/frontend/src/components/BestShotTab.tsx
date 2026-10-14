import React, { useState } from 'react';
import { Upload, Crown, Sparkles, Award, Image as ImageIcon, BarChart2 } from 'lucide-react';

interface BestShotResult {
  rank: number;
  total_score: number;
  blur_score: number;
  color_score: number;
  comp_score: number;
  tags: string[];
  filename: string;
  image_data: string;
}

export default function BestShotTab() {
  const [files, setFiles] = useState<File[]>([]);
  const [previews, setPreviews] = useState<string[]>([]);
  const [results, setResults] = useState<BestShotResult[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(false);

  // 다중 파일 선택
  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      const selectedFiles = Array.from(e.target.files);
      if (selectedFiles.length < 2) {
        alert('베스트 샷 비교 분석을 위해 2장 이상의 사진을 선택해 주세요.');
        return;
      }
      setFiles(selectedFiles);
      setPreviews(selectedFiles.map((file) => URL.createObjectURL(file)));
      setResults([]);
    }
  };

  // AI 베스트 샷 분석 요청
  const handleEvaluate = async () => {
    if (files.length < 2) return;
    setIsLoading(true);

    try {
      const formData = new FormData();
      files.forEach((file) => {
        formData.append('images', file);
      });

      const response = await fetch('http://localhost:8000/api/best-shot', {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) throw new Error('분석 실패');

      const data = await response.json();
      setResults(data.results);
    } catch (err) {
      alert('베스트 샷 평가 중 오류가 발생했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div style={{ padding: '20px', maxWidth: '1100px', margin: '0 auto' }}>
      <div style={{ marginBottom: '20px' }}>
        <h2 style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#1e293b' }}>
          <Sparkles color="#eab308" size={24} /> AI 여행 사진 베스트 샷 추천
        </h2>
        <p style={{ color: '#64748b' }}>
          여러 장의 사진을 업로드하면 AI가 <b>화질(선명도)</b>, <b>색감 및 노출</b>, <b>구도 및 인물 표정</b>을 종합 분석하여 최상의 베스트 샷을 순위별로 추천해 드립니다.
        </p>
      </div>

      {/* 파일 업로드 버튼 및 실행 버튼 */}
      <div style={{ display: 'flex', gap: '15px', alignItems: 'center', marginBottom: '25px' }}>
        <input
          type="file"
          multiple
          accept="image/*"
          onChange={handleFileChange}
          id="best-shot-upload"
          style={{ display: 'none' }}
        />
        <label
          htmlFor="best-shot-upload"
          style={{
            padding: '10px 20px',
            background: '#2563eb',
            color: '#fff',
            borderRadius: '8px',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            fontWeight: 'bold',
          }}
        >
          <Upload size={18} /> 사진 여러 장 선택 (2장 이상)
        </label>

        {files.length >= 2 && (
          <button
            onClick={handleEvaluate}
            disabled={isLoading}
            style={{
              padding: '10px 24px',
              background: isLoading ? '#9ca3af' : '#eab308',
              color: '#fff',
              border: 'none',
              borderRadius: '8px',
              cursor: isLoading ? 'not-allowed' : 'pointer',
              fontWeight: 'bold',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
            }}
          >
            <Crown size={18} />
            {isLoading ? 'AI 가치 평가 및 베스트 샷 산출 중...' : `베스트 샷 평가 실행 (${files.length}장)`}
          </button>
        )}
      </div>

      {/* 원본 사진 대기열 미리보기 */}
      {previews.length > 0 && results.length === 0 && (
        <div>
          <h4 style={{ marginBottom: '12px', color: '#334155' }}>업로드된 사진 목록 ({previews.length}장)</h4>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: '15px' }}>
            {previews.map((url, idx) => (
              <div key={idx} style={{ border: '1px solid #e2e8f0', borderRadius: '8px', overflow: 'hidden' }}>
                <img src={url} alt={`preview-${idx}`} style={{ width: '100%', height: '160px', objectFit: 'cover' }} />
                <div style={{ padding: '6px', fontSize: '12px', textAlign: 'center', color: '#64748b', backgroundColor: '#f8fafc' }}>
                  {files[idx]?.name || `사진 #${idx + 1}`}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 베스트 샷 랭킹 결과 화면 */}
      {results.length > 0 && (
        <div>
          <h3 style={{ marginBottom: '20px', color: '#0f172a', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Award size={22} color="#eab308" /> AI 베스트 샷 평가 결과
          </h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            {results.map((item) => {
              const isBest = item.rank === 1;

              return (
                <div
                  key={item.rank}
                  style={{
                    display: 'flex',
                    flexWrap: 'wrap',
                    gap: '20px',
                    padding: '20px',
                    borderRadius: '12px',
                    border: isBest ? '3px solid #eab308' : '1px solid #e2e8f0',
                    backgroundColor: isBest ? '#fefce8' : '#ffffff',
                    boxShadow: isBest ? '0 10px 25px -5px rgba(234, 179, 8, 0.25)' : 'none',
                    position: 'relative',
                  }}
                >
                  {/* 순위 뱃지 */}
                  <div
                    style={{
                      position: 'absolute',
                      top: '15px',
                      left: '15px',
                      padding: '6px 14px',
                      borderRadius: '20px',
                      backgroundColor: isBest ? '#eab308' : '#64748b',
                      color: '#fff',
                      fontWeight: 'bold',
                      fontSize: '14px',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '4px',
                      zIndex: 2,
                    }}
                  >
                    {isBest ? <Crown size={16} /> : <Award size={16} />}
                    {isBest ? 'BEST SHOT 👑' : `#${item.rank}`}
                  </div>

                  {/* 이미지 표시 */}
                  <div style={{ flex: '1 1 320px', minWidth: '280px', position: 'relative' }}>
                    <img
                      src={item.image_data}
                      alt={`rank-${item.rank}`}
                      style={{
                        width: '100%',
                        height: 'auto',
                        maxHeight: '350px',
                        objectFit: 'contain',
                        borderRadius: '8px',
                        backgroundColor: '#0f172a',
                      }}
                    />
                  </div>

                  {/* 세부 점수 및 정보 리포트 */}
                  <div style={{ flex: '1 1 400px', display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: '15px' }}>
                      <span style={{ fontSize: '18px', fontWeight: 'bold', color: '#1e293b' }}>
                        {item.filename}
                      </span>
                      <div style={{ textAlign: 'right' }}>
                        <span style={{ fontSize: '32px', fontWeight: '800', color: isBest ? '#ca8a04' : '#2563eb' }}>
                          {item.total_score}
                        </span>
                        <span style={{ fontSize: '16px', color: '#64748b' }}> / 100점</span>
                      </div>
                    </div>

                    {/* 태그 모음 */}
                    <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginBottom: '20px' }}>
                      {item.tags.map((tag, idx) => (
                        <span
                          key={idx}
                          style={{
                            padding: '4px 10px',
                            borderRadius: '15px',
                            backgroundColor: isBest ? '#fef08a' : '#f1f5f9',
                            color: isBest ? '#854d0e' : '#334155',
                            fontSize: '13px',
                            fontWeight: 'bold',
                          }}
                        >
                          {tag}
                        </span>
                      ))}
                    </div>

                    {/* 3가지 세부 지표 스코어바 */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                      {/* 1. 화질 점수 */}
                      <div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', marginBottom: '4px', color: '#475569' }}>
                          <span>📸 선명도 / 화질</span>
                          <b>{item.blur_score}점</b>
                        </div>
                        <div style={{ width: '100%', height: '8px', backgroundColor: '#e2e8f0', borderRadius: '4px', overflow: 'hidden' }}>
                          <div style={{ width: `${item.blur_score}%`, height: '100%', backgroundColor: '#3b82f6' }} />
                        </div>
                      </div>

                      {/* 2. 색감/노출 점수 */}
                      <div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', marginBottom: '4px', color: '#475569' }}>
                          <span>🎨 색감 및 적정 노출</span>
                          <b>{item.color_score}점</b>
                        </div>
                        <div style={{ width: '100%', height: '8px', backgroundColor: '#e2e8f0', borderRadius: '4px', overflow: 'hidden' }}>
                          <div style={{ width: `${item.color_score}%`, height: '100%', backgroundColor: '#10b981' }} />
                        </div>
                      </div>

                      {/* 3. 구도 및 인물 표정 점수 */}
                      <div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', marginBottom: '4px', color: '#475569' }}>
                          <span>👤 인물 구도 및 표정</span>
                          <b>{item.comp_score}점</b>
                        </div>
                        <div style={{ width: '100%', height: '8px', backgroundColor: '#e2e8f0', borderRadius: '4px', overflow: 'hidden' }}>
                          <div style={{ width: `${item.comp_score}%`, height: '100%', backgroundColor: '#8b5cf6' }} />
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}