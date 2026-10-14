import { useState } from 'react';
import MosaicTab from './components/MosaicTab';
import BestShotTab from './components/BestShotTab';

type TabType = 'mosaic' | 'bestshot';

export default function App() {
  const [activeTab, setActiveTab] = useState<TabType>('mosaic');

  return (
    <div style={{ fontFamily: 'sans-serif', minHeight: '100vh', backgroundColor: '#f8fafc' }}>
      {/* 상단 헤더 */}
      <header style={{ backgroundColor: '#ffffff', borderBottom: '1px solid #e2e8f0', padding: '16px 32px' }}>
        <h1 style={{ margin: 0, fontSize: '20px', color: '#0f172a' }}>Vision AI Studio (Testbed)</h1>
      </header>

      {/* 탭 내비게이션 */}
      <div style={{ display: 'flex', borderBottom: '1px solid #e2e8f0', backgroundColor: '#ffffff', paddingLeft: '32px' }}>
        <button
          onClick={() => setActiveTab('mosaic')}
          style={{
            padding: '12px 24px',
            border: 'none',
            borderBottom: activeTab === 'mosaic' ? '3px solid #2563eb' : '3px solid transparent',
            backgroundColor: 'transparent',
            fontWeight: activeTab === 'mosaic' ? 'bold' : 'normal',
            color: activeTab === 'mosaic' ? '#2563eb' : '#64748b',
            cursor: 'pointer',
          }}
        >
          AI 자동 모자이크 (브러시)
        </button>
        <button
          onClick={() => setActiveTab('bestshot')}
          style={{
            padding: '12px 24px',
            border: 'none',
            borderBottom: activeTab === 'bestshot' ? '3px solid #eab308' : '3px solid transparent',
            backgroundColor: 'transparent',
            fontWeight: activeTab === 'bestshot' ? 'bold' : 'normal',
            color: activeTab === 'bestshot' ? '#ca8a04' : '#64748b',
            cursor: 'pointer',
          }}
        >
          AI 베스트 샷 추천
        </button>
      </div>

      {/* 컨텐츠 영역 */}
      <main style={{ padding: '20px' }}>
        {activeTab === 'mosaic' ? <MosaicTab /> : <BestShotTab />}
      </main>
    </div>
  );
}