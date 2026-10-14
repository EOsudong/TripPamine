import React, { useState } from 'react';
import { Upload } from 'lucide-react';

export default function CuratorTab() {
  const [images, setImages] = useState<string[]>([]);

  const handleMultipleUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      const files = Array.from(e.target.files);
      const urls = files.map((file) => URL.createObjectURL(file));
      setImages(urls);
    }
  };

  return (
    <div style={{ padding: '20px', maxWidth: '1000px', margin: '0 auto' }}>
      <h2>2. AI 베스트 샷 큐레이터 (NIMA 화질/구도 평가)</h2>
      <p style={{ color: '#666' }}>
        여러 장의 여행 사진을 업로드하면 AI가 구도, 색감, 화질을 평가해 베스트 샷을 추천합니다.
      </p>

      <input type="file" multiple accept="image/*" onChange={handleMultipleUpload} id="multi-upload" style={{ display: 'none' }} />
      <label
        htmlFor="multi-upload"
        style={{
          padding: '8px 16px',
          background: '#9333ea',
          color: '#fff',
          borderRadius: '6px',
          cursor: 'pointer',
          display: 'inline-flex',
          alignItems: 'center',
          gap: '6px',
        }}
      >
        <Upload size={18} /> 사진 여러 장 선택 (최대 10장)
      </label>

      {images.length > 0 && (
        <div style={{ marginTop: '20px', display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: '15px' }}>
          {images.map((url, idx) => (
            <div key={idx} style={{ border: '1px solid #eee', borderRadius: '8px', overflow: 'hidden' }}>
              <img src={url} alt={`sample-${idx}`} style={{ width: '100%', height: '140px', objectFit: 'cover' }} />
              <div style={{ padding: '8px', textAlign: 'center', fontSize: '12px', background: '#f9fafb' }}>
                분석 대기 중...
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}