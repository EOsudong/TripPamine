import React, { useState, useRef } from 'react';
import { Paintbrush, RotateCcw, Upload, CheckCircle, Users, UserCheck } from 'lucide-react';

export default function MosaicTab() {
  const [subMode, setSubMode] = useState<'single' | 'multi'>('single');

  // --- 단일 모드 상태 ---
  const [imageSrc, setImageSrc] = useState<string | null>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [brushSize, setBrushSize] = useState<number>(30);
  const [isDrawing, setIsDrawing] = useState<boolean>(false);
  const [singleResultImage, setSingleResultImage] = useState<string | null>(null);
  const [isSingleLoading, setIsSingleLoading] = useState<boolean>(false);

  const imageRef = useRef<HTMLImageElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  // --- 다중 모드 상태 ---
  const [multiFiles, setMultiFiles] = useState<File[]>([]);
  const [multiPreviews, setMultiPreviews] = useState<string[]>([]);
  const [multiResults, setMultiResults] = useState<string[]>([]);
  const [isMultiLoading, setIsMultiLoading] = useState<boolean>(false);

  // 단일 모드 파일 로드
  const handleSingleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setSelectedFile(file);
      setImageSrc(URL.createObjectURL(file));
      setSingleResultImage(null);
    }
  };

  const handleImageLoad = () => {
    const img = imageRef.current;
    const canvas = canvasRef.current;
    if (img && canvas) {
      canvas.width = img.clientWidth;
      canvas.height = img.clientHeight;
      resetCanvas();
    }
  };

  const resetCanvas = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.fillStyle = 'black';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
  };

  const startDrawing = (e: React.MouseEvent<HTMLCanvasElement>) => {
    setIsDrawing(true);
    draw(e);
  };

  const stopDrawing = () => {
    setIsDrawing(false);
    const ctx = canvasRef.current?.getContext('2d');
    if (ctx) ctx.beginPath();
  };

  const draw = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (!isDrawing) return;
    const canvas = canvasRef.current;
    if (!canvas) return;

    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.lineWidth = brushSize;
    ctx.lineCap = 'round';
    ctx.strokeStyle = 'white';

    ctx.lineTo(x, y);
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(x, y);
  };

  // 단일 모자이크 요청
  const handleApplySingleMosaic = async () => {
    if (!selectedFile || !canvasRef.current) return;
    setIsSingleLoading(true);

    try {
      const canvas = canvasRef.current;
      const maskBlob = await new Promise<Blob | null>((resolve) =>
        canvas.toBlob(resolve, 'image/png')
      );

      if (!maskBlob) return;

      const formData = new FormData();
      formData.append('image', selectedFile);
      formData.append('mask', maskBlob, 'mask.png');

      const response = await fetch('http://localhost:8000/api/mosaic', {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) throw new Error('처리 실패');

      const blob = await response.blob();
      setSingleResultImage(URL.createObjectURL(blob));
    } catch (err) {
      alert('모자이크 처리 중 오류가 발생했습니다.');
    } finally {
      setIsSingleLoading(false);
    }
  };

  // 다중 모드 파일 업로드 핸들러
  const handleMultiImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      const files = Array.from(e.target.files);
      if (files.length < 2) {
        alert('공통 인물 자동 비교를 위해 2장 이상의 사진을 업로드해 주세요.');
        return;
      }
      setMultiFiles(files);
      setMultiPreviews(files.map((file) => URL.createObjectURL(file)));
      setMultiResults([]);
    }
  };

  // 다중 모자이크 요청 (2장 이상 등장 공통 인물 보존)
  const handleApplyMultiMosaic = async () => {
    if (multiFiles.length < 2) return;
    setIsMultiLoading(true);

    try {
      const formData = new FormData();
      multiFiles.forEach((file) => {
        formData.append('images', file);
      });

      const response = await fetch('http://localhost:8000/api/multi-mosaic', {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) throw new Error('다중 처리 실패');

      const data = await response.json();
      setMultiResults(data.results);
    } catch (err) {
      alert('다중 모자이크 처리 중 오류가 발생했습니다.');
    } finally {
      setIsMultiLoading(false);
    }
  };

  return (
    <div style={{ padding: '20px', maxWidth: '1100px', margin: '0 auto' }}>
      {/* 서브 모드 선택 서브바 */}
      <div style={{ display: 'flex', gap: '10px', marginBottom: '20px' }}>
        <button
          onClick={() => setSubMode('single')}
          style={{
            padding: '8px 16px',
            borderRadius: '20px',
            border: 'none',
            backgroundColor: subMode === 'single' ? '#2563eb' : '#e2e8f0',
            color: subMode === 'single' ? '#fff' : '#475569',
            fontWeight: 'bold',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
          }}
        >
          <UserCheck size={16} /> 1장 수동 지정 (브러시 모드)
        </button>
        <button
          onClick={() => setSubMode('multi')}
          style={{
            padding: '8px 16px',
            borderRadius: '20px',
            border: 'none',
            backgroundColor: subMode === 'multi' ? '#2563eb' : '#e2e8f0',
            color: subMode === 'multi' ? '#fff' : '#475569',
            fontWeight: 'bold',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
          }}
        >
          <Users size={16} /> 여러 장 자동 판별 (공통 인물 보존 모드)
        </button>
      </div>

      {/* 1. 단일 사진 브러시 모드 */}
      {subMode === 'single' && (
        <div>
          <h2>1. 단일 사진 (보존할 인물 브러시 선택)</h2>
          <p style={{ color: '#666' }}>
            사진을 업로드한 후, <b>모자이크에서 제외할(보존할) 사람 위를 브러시로 칠하세요.</b>
          </p>

          <div style={{ display: 'flex', gap: '15px', alignItems: 'center', marginBottom: '15px' }}>
            <input type="file" accept="image/*" onChange={handleSingleImageUpload} id="single-upload" style={{ display: 'none' }} />
            <label htmlFor="single-upload" style={{ padding: '8px 16px', background: '#2563eb', color: '#fff', borderRadius: '6px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <Upload size={18} /> 사진 업로드
            </label>

            {imageSrc && (
              <>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Paintbrush size={18} />
                  <span>브러시 크기:</span>
                  <input type="range" min="10" max="80" value={brushSize} onChange={(e) => setBrushSize(Number(e.target.value))} />
                </div>

                <button onClick={resetCanvas} style={{ padding: '8px 12px', background: '#f3f4f6', border: '1px solid #ccc', borderRadius: '6px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <RotateCcw size={16} /> 브러시 초기화
                </button>

                <button onClick={handleApplySingleMosaic} disabled={isSingleLoading} style={{ padding: '8px 20px', background: isSingleLoading ? '#9ca3af' : '#16a34a', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 'bold' }}>
                  {isSingleLoading ? 'AI 처리 중...' : '모자이크 실행'}
                </button>
              </>
            )}
          </div>

          <div style={{ display: 'flex', gap: '20px', flexWrap: 'wrap', marginTop: '20px' }}>
            {imageSrc && (
              <div>
                <h4 style={{ margin: '0 0 10px 0' }}>작업 영역</h4>
                <div style={{ position: 'relative', display: 'inline-block', border: '1px solid #ddd', borderRadius: '8px', overflow: 'hidden' }}>
                  <img ref={imageRef} src={imageSrc} alt="Target" onLoad={handleImageLoad} style={{ display: 'block', maxWidth: '480px', height: 'auto' }} />
                  <canvas ref={canvasRef} onMouseDown={startDrawing} onMouseUp={stopDrawing} onMouseMove={draw} onMouseLeave={stopDrawing} style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', opacity: 0.5, cursor: 'crosshair' }} />
                </div>
              </div>
            )}

            {singleResultImage && (
              <div>
                <h4 style={{ margin: '0 0 10px 0', color: '#16a34a', display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <CheckCircle size={18} /> 변환 결과
                </h4>
                <div style={{ border: '2px solid #16a34a', borderRadius: '8px', overflow: 'hidden' }}>
                  <img src={singleResultImage} alt="Result" style={{ display: 'block', maxWidth: '480px', height: 'auto' }} />
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* 2. 여러 장 자동 공통 인물 모드 */}
      {subMode === 'multi' && (
        <div>
          <h2>2. 여러 장 자동 모자이크 (공통 인물 자동 보존)</h2>
          <p style={{ color: '#666' }}>
            여행 사진을 <b>2장 이상 업로드</b>하면, 2장 이상의 사진에 공통으로 나타난 동행자/주인공을 AI가 자동으로 인지하여 보존하고, <b>1장에만 스쳐 지나간 배경 행인들만 자동으로 모자이크</b>합니다.
          </p>

          <div style={{ display: 'flex', gap: '15px', alignItems: 'center', marginBottom: '20px' }}>
            <input type="file" multiple accept="image/*" onChange={handleMultiImageUpload} id="multi-upload" style={{ display: 'none' }} />
            <label htmlFor="multi-upload" style={{ padding: '8px 16px', background: '#2563eb', color: '#fff', borderRadius: '6px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <Upload size={18} /> 사진 여러 장 선택 (2장 이상)
            </label>

            {multiFiles.length >= 2 && (
              <button onClick={handleApplyMultiMosaic} disabled={isMultiLoading} style={{ padding: '8px 20px', background: isMultiLoading ? '#9ca3af' : '#16a34a', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 'bold' }}>
                {isMultiLoading ? 'AI 얼굴 분석 & 클러스터링 중...' : `공통 인물 자동 분석 및 모자이크 실행 (${multiFiles.length}장)`}
              </button>
            )}
          </div>

          {/* 원본 미리보기 갤러리 */}
          {multiPreviews.length > 0 && multiResults.length === 0 && (
            <div>
              <h4>업로드된 원본 사진 목록 ({multiPreviews.length}장)</h4>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: '15px' }}>
                {multiPreviews.map((url, idx) => (
                  <div key={idx} style={{ border: '1px solid #ddd', borderRadius: '8px', overflow: 'hidden' }}>
                    <img src={url} alt={`preview-${idx}`} style={{ width: '100%', height: '180px', objectFit: 'cover' }} />
                    <div style={{ padding: '6px', fontSize: '12px', textAlign: 'center', backgroundColor: '#f8fafc' }}>
                      사진 #{idx + 1}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* 모자이크 처리된 결과 갤러리 */}
          {multiResults.length > 0 && (
            <div>
              <h4 style={{ color: '#16a34a', display: 'flex', alignItems: 'center', gap: '6px' }}>
                <CheckCircle size={18} /> AI 처리 완료 결과 (2장 이상 등장 공통 인물 보존됨)
              </h4>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: '20px' }}>
                {multiResults.map((url, idx) => (
                  <div key={idx} style={{ border: '2px solid #16a34a', borderRadius: '8px', overflow: 'hidden' }}>
                    <img src={url} alt={`result-${idx}`} style={{ width: '100%', height: 'auto', display: 'block' }} />
                    <div style={{ padding: '8px', fontSize: '13px', fontWeight: 'bold', textAlign: 'center', backgroundColor: '#f0fdf4', color: '#166534' }}>
                      결과 사진 #{idx + 1}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}