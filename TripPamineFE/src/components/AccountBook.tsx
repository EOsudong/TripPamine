import React, { useState, useEffect, useRef } from 'react';
import api from '../api/axios';

interface Transaction {
  id: number;
  username: string;
  transactionDate: string;
  description: string;
  amount: number;
  type: 'income' | 'expense';
  category: string;
}

interface AccountBookProps {
  username?: string;
}

const CATEGORY_MAP: Record<string, string> = {
  FOOD: '식비/카페',
  TRANSPORT: '교통/차량',
  SHOPPING: '쇼핑/생활',
  MEDICAL: '의료/건강',
  CULTURE: '문화/여가',
  HOUSING: '주거/통신',
  SALARY: '급여/수입',
  ETC: '기타/용돈',
};

export default function AccountBook({ username = '여행자' }: AccountBookProps) {
  const dateInputRef = useRef<HTMLInputElement>(null);

  const getNowLocalDateTime = () => {
    const now = new Date();
    const pad = (n: number) => n.toString().padStart(2, '0');
    return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}`;
  };

  const getTodayString = () => {
    const now = new Date();
    const pad = (n: number) => n.toString().padStart(2, '0');
    return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  };

  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [description, setDescription] = useState<string>('');
  const [amount, setAmount] = useState<number>(0);
  const [type, setType] = useState<'income' | 'expense'>('expense');
  const [category, setCategory] = useState<string>('FOOD');
  const [sortBy, setSortBy] = useState<string>('timeDesc');
  const [isOcrLoading, setIsOcrLoading] = useState<boolean>(false);

  const [transactionDate, setTransactionDate] = useState<string>(getNowLocalDateTime());
  const [filterMode, setFilterMode] = useState<'year' | 'month' | 'day'>('day');
  const [filterDate, setFilterDate] = useState<string>(getTodayString());

  // 모달 상태
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false);
  const [selectedTransaction, setSelectedTransaction] = useState<Transaction | null>(null);
  const [editDescription, setEditDescription] = useState<string>('');
  const [editAmount, setEditAmount] = useState<number>(0);
  const [editType, setEditType] = useState<'income' | 'expense'>('expense');
  const [editCategory, setEditCategory] = useState<string>('FOOD');
  const [editDate, setEditDate] = useState<string>('');

  // 1. 거래 내역 목록 조회 (GET)
  const fetchTransactions = async () => {
    try {
      const response = await api.get('/accountbook');
      setTransactions(response.data);
    } catch (error) {
      console.error("데이터 읽기 실패:", error);
    }
  };

  useEffect(() => {
    fetchTransactions();
  }, [username]);

  useEffect(() => {
    if (type === 'income') {
      setCategory('SALARY');
    } else {
      setCategory('FOOD');
    }
  }, [type]);

  // 2. 거래 추가 (POST)
  const handleAddTransaction = async (e: React.FormEvent) => {
    e.preventDefault();

    try {
      const payload = {
        transactionDate: new Date(transactionDate).toISOString(),
        description: description,
        amount: amount,
        type: type,
        category: category
      };

      await api.post('/accountbook/add', payload);
      fetchTransactions();
      setDescription('');
      setAmount(0);

    } catch (error: any) {
      if (error.response && error.response.data) {
        const serverMessage = error.response.data.message || '입력값이 올바르지 않습니다.';
        alert(`[입력 오류] ${serverMessage}`);
      } else {
        alert('저장 중 오류가 발생했습니다.');
      }
    }
  };

  // 3. 거래 수정 (PUT)
  const handleUpdate = async () => {
    if (!selectedTransaction || !editDescription || editAmount <= 0 || !editDate) return;

    const payload = {
      description: editDescription,
      amount: editAmount,
      type: editType,
      category: editCategory,
      transactionDate: `${editDate}:00`
    };

    try {
      await api.put(`/accountbook/update/${selectedTransaction.id}`, payload);
      setIsModalOpen(false);
      fetchTransactions();
    } catch (error) {
      console.error("수정 실패:", error);
    }
  };

  // 4. 거래 삭제 (DELETE)
  const handleDelete = async () => {
    if (!selectedTransaction) return;

    if (window.confirm("정말 이 내역을 삭제하시겠습니까?")) {
      try {
        await api.delete(`/accountbook/delete/${selectedTransaction.id}`);
        setIsModalOpen(false);
        fetchTransactions();
      } catch (error) {
        console.error("삭제 실패:", error);
      }
    }
  };

  const openEditModal = (t: Transaction) => {
    setSelectedTransaction(t);
    setEditDescription(t.description);
    setEditAmount(t.amount);
    setEditType(t.type);
    setEditCategory(t.category || 'ETC');

    const dateObj = new Date(t.transactionDate);
    const pad = (n: number) => n.toString().padStart(2, '0');
    const localDateTimeStr = `${dateObj.getFullYear()}-${pad(dateObj.getMonth() + 1)}-${pad(dateObj.getDate())}T${pad(dateObj.getHours())}:${pad(dateObj.getMinutes())}`;
    
    setEditDate(localDateTimeStr);
    setIsModalOpen(true);
  };

  const formatDateTime = (dateStr: string) => {
    const date = new Date(dateStr);
    const pad = (n: number) => n.toString().padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
  };

  const formatCompactDate = (dateTimeStr: string) => {
    try {
      const date = new Date(dateTimeStr);
      const pad = (n: number) => n.toString().padStart(2, '0');
      return `📅 ${pad(date.getMonth() + 1)}/${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
    } catch (e) {
      return "📅 날짜 선택";
    }
  };

  const handleCompactDateButtonClick = () => {
    if (dateInputRef.current) {
      if (typeof dateInputRef.current.showPicker === 'function') {
        dateInputRef.current.showPicker();
      } else {
        dateInputRef.current.click();
      }
    }
  };

  const getSortedTransactions = () => {
    const filtered = transactions.filter((t) => {
      const tDate = new Date(t.transactionDate);
      const fDate = new Date(filterDate);
      
      if (isNaN(tDate.getTime()) || isNaN(fDate.getTime())) return true;

      if (filterMode === 'year') {
        return tDate.getFullYear() === fDate.getFullYear();
      } else if (filterMode === 'month') {
        return tDate.getFullYear() === fDate.getFullYear() && tDate.getMonth() === fDate.getMonth();
      } else {
        return (
          tDate.getFullYear() === fDate.getFullYear() &&
          tDate.getMonth() === fDate.getMonth() &&
          tDate.getDate() === fDate.getDate()
        );
      }
    });

    switch (sortBy) {
      case 'timeDesc': return filtered.sort((a, b) => new Date(b.transactionDate).getTime() - new Date(a.transactionDate).getTime());
      case 'timeAsc': return filtered.sort((a, b) => new Date(a.transactionDate).getTime() - new Date(b.transactionDate).getTime());
      case 'nameAsc': return filtered.sort((a, b) => a.description.localeCompare(b.description));
      case 'nameDesc': return filtered.sort((a, b) => b.description.localeCompare(a.description));
      case 'amountDesc': return filtered.sort((a, b) => b.amount - a.amount);
      case 'amountAsc': return filtered.sort((a, b) => a.amount - b.amount);
      default: return filtered;
    }
  };

  const currentFilteredList = getSortedTransactions();
  const totalIncome = currentFilteredList.filter(t => t.type === 'income').reduce((sum, t) => sum + t.amount, 0);
  const totalExpense = currentFilteredList.filter(t => t.type === 'expense').reduce((sum, t) => sum + t.amount, 0);
  const netBalance = totalIncome - totalExpense;

  // 5. 영수증 이미지 OCR 분석 (Multipart Form-Data POST)
  const handleReceiptOcr = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;

    const targetFile = files[0];
    const formData = new FormData();
    formData.append("file", targetFile);

    setIsOcrLoading(true);

    try {
      const response = await api.post('/accountbook/ocr', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      const data = response.data;
      
      setDescription(data.description || '영수증 지출');
      setAmount(data.amount || 0);
      setType('expense'); 
      setCategory(data.category || 'ETC');

      if (data.transactionDate) {
        setTransactionDate(data.transactionDate);
        setFilterDate(data.transactionDate.substring(0, 10));
        setFilterMode('day');
      } else {
        setTransactionDate(getNowLocalDateTime());
      }

      alert(`🔮 영수증 분석 완료!\n카테고리: ${CATEGORY_MAP[data.category || 'ETC']}\n내역: ${data.description}\n금액: ${data.amount.toLocaleString()}원`);
    } catch (error) {
      console.error("OCR API 에러:", error);
      alert("❌ 구글 OCR 서버 통신 중 오류가 발생했습니다.");
    } finally {
      setIsOcrLoading(false);
    }
  };

  return (
    <div className="w-full text-left space-y-6">
      {/* 1. 요약 정보 카드 세트 */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* 총 수입 카드 */}
        <div className="bg-gradient-to-br from-sky-500 to-sky-600 rounded-2xl p-5 text-white shadow-md shadow-sky-200/50 flex flex-col justify-between">
          <div className="flex justify-between items-center mb-3">
            <span className="text-xs font-bold text-sky-100 uppercase tracking-wider">총 수입</span>
            <span className="text-lg">🎯</span>
          </div>
          <span className="text-xl font-extrabold tracking-tight">+{totalIncome.toLocaleString()}원</span>
        </div>

        {/* 총 지출 카드 */}
        <div className="bg-gradient-to-br from-amber-500 to-orange-500 rounded-2xl p-5 text-white shadow-md shadow-amber-200/50 flex flex-col justify-between">
          <div className="flex justify-between items-center mb-3">
            <span className="text-xs font-bold text-amber-100 uppercase tracking-wider">총 지출</span>
            <span className="text-lg">⚡</span>
          </div>
          <span className="text-xl font-extrabold tracking-tight">-{totalExpense.toLocaleString()}원</span>
        </div>

        {/* 현재 잔액 카드 */}
        <div className="bg-gradient-to-br from-slate-800 to-slate-900 rounded-2xl p-5 text-white shadow-md shadow-slate-300/50 flex flex-col justify-between">
          <div className="flex justify-between items-center mb-3">
            <span className="text-xs font-bold text-slate-300 uppercase tracking-wider">현재 잔액</span>
            <span className="text-lg">❤️</span>
          </div>
          <span className="text-xl font-extrabold tracking-tight">{netBalance.toLocaleString()}원</span>
        </div>
      </div>

      {/* 2. 내역 추가 입력 폼 */}
      <form onSubmit={handleAddTransaction} className="bg-slate-50 border border-slate-200/80 rounded-2xl p-4 transition-all">
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2.5 items-center">
          {/* 유형 선택 */}
          <select 
            value={type} 
            onChange={(e) => setType(e.target.value as 'income' | 'expense')} 
            className="w-full px-3 py-2.5 rounded-xl border-2 border-slate-200 focus:border-sky-500 outline-none text-xs font-semibold text-slate-700 bg-white transition-colors cursor-pointer"
          >
            <option value="expense">지출</option>
            <option value="income">수입</option>
          </select>

          {/* 카테고리 선택 */}
          <select 
            value={category} 
            onChange={(e) => setCategory(e.target.value)} 
            className="w-full px-3 py-2.5 rounded-xl border-2 border-sky-100 bg-sky-50/50 focus:border-sky-500 outline-none text-xs font-bold text-sky-700 transition-colors cursor-pointer"
          >
            {Object.entries(CATEGORY_MAP).map(([key, value]) => (
              <option key={key} value={key}>{value}</option>
            ))}
          </select>

          {/* 날짜 선택 버튼 */}
          <div className="relative">
            <input 
              type="datetime-local"
              ref={dateInputRef}
              value={transactionDate}
              onChange={(e) => setTransactionDate(e.target.value)}
              className="hidden"
            />
            <button 
              type="button" 
              onClick={handleCompactDateButtonClick}
              className="w-full px-3 py-2.5 rounded-xl border-2 border-slate-200 hover:border-sky-300 text-xs font-bold text-sky-600 bg-white transition-colors truncate text-left"
            >
              {formatCompactDate(transactionDate)}
            </button>
          </div>

          {/* 내역 입력 */}
          <input 
            type="text" 
            placeholder="내역 입력" 
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className="w-full px-3.5 py-2.5 rounded-xl border-2 border-slate-200 focus:border-sky-500 outline-none text-xs text-slate-800 bg-white transition-colors placeholder-slate-400"
          />

          {/* 금액 입력 */}
          <input 
            type="number" 
            placeholder="금액 입력" 
            value={amount || ''}
            onChange={(e) => setAmount(Number(e.target.value))}
            className="w-full px-3.5 py-2.5 rounded-xl border-2 border-slate-200 focus:border-sky-500 outline-none text-xs text-slate-800 bg-white transition-colors placeholder-slate-400"
          />

          {/* 추가 버튼 */}
          <button 
            type="submit" 
            className="w-full py-2.5 bg-sky-500 hover:bg-sky-600 text-white font-bold text-xs rounded-xl transition-colors shadow-md shadow-sky-200"
          >
            추가
          </button>
        </div>
      </form>

      {/* 3. 상단 헤더 (필터, 정렬, OCR 버튼) */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-2">
        <div className="flex items-center gap-2 flex-wrap">
          <h4 className="text-base font-bold text-slate-800 flex items-center gap-1.5">
            <span>🧾</span> 최근 입출금 내역
          </h4>
          
          {/* OCR 영수증 스캔 버튼 */}
          <label className="cursor-pointer inline-flex items-center gap-1.5 px-3 py-1.5 bg-rose-50 hover:bg-rose-100 text-rose-600 text-xs font-bold rounded-xl transition-colors">
            {isOcrLoading ? (
              <>
                <span className="w-3 h-3 border-2 border-rose-500 border-t-transparent rounded-full animate-spin"></span>
                <span>분석중...</span>
              </>
            ) : (
              <>
                <span>📷</span>
                <span>영수증 등록</span>
              </>
            )}
            <input 
              type="file" 
              accept="image/*" 
              capture="environment" 
              onChange={handleReceiptOcr} 
              className="hidden"
              disabled={isOcrLoading}
            />
          </label>

          {/* 일별/월별/연도별 필터 */}
          <select 
            value={filterMode} 
            onChange={(e) => setFilterMode(e.target.value as 'year' | 'month' | 'day')}
            className="px-2.5 py-1.5 rounded-xl border border-slate-200 text-xs font-semibold text-sky-700 bg-sky-50/60 outline-none focus:border-sky-500 cursor-pointer"
          >
            <option value="day">일별 단위</option>
            <option value="month">월별 통합</option>
            <option value="year">연도별 통합</option>
          </select>

          {/* 날짜 선택 인풋 */}
          <input 
            type={filterMode === 'year' ? "number" : filterMode === 'month' ? "month" : "date"}
            value={filterMode === 'year' ? new Date(filterDate).getFullYear() : filterMode === 'month' ? filterDate.substring(0, 7) : filterDate}
            onChange={(e) => {
              const val = e.target.value;
              if (!val) return;
              if (filterMode === 'year') {
                setFilterDate(`${val}-01-01`);
              } else if (filterMode === 'month') {
                setFilterDate(`${val}-01`);
              } else {
                setFilterDate(val);
              }
            }}
            className="px-2.5 py-1 rounded-xl border border-slate-200 text-xs font-semibold text-slate-700 bg-white outline-none focus:border-sky-500"
          />
        </div>

        {/* 정렬 옵션 드롭다운 */}
        <select 
          value={sortBy} 
          onChange={(e) => setSortBy(e.target.value)} 
          className="px-3 py-1.5 rounded-xl border border-slate-200 text-xs font-semibold text-slate-600 bg-white outline-none focus:border-sky-500 cursor-pointer shrink-0"
        >
          <option value="timeDesc">최신 시간순</option>
          <option value="timeAsc">과거 시간순</option>
          <option value="nameAsc">가나다순</option>
          <option value="nameDesc">가나다 역순</option>
          <option value="amountDesc">높은 금액순</option>
          <option value="amountAsc">낮은 금액순</option>
        </select>
      </div>

      {/* 4. 거래 내역 리스트 */}
      <div className="max-h-[320px] overflow-y-auto pr-1 space-y-2.5">
        {currentFilteredList.map((t) => (
          <div 
            key={t.id} 
            className="flex items-center justify-between p-4 bg-white rounded-2xl border border-slate-100 hover:border-sky-200 transition-all shadow-sm"
          >
            <div className="text-left space-y-1">
              <div className="flex items-center gap-2 text-[11px] text-slate-400 font-medium">
                <span>{formatDateTime(t.transactionDate)}</span>
                <span className="px-2 py-0.5 rounded-full bg-sky-50 text-sky-600 font-bold text-[10px]">
                  #{CATEGORY_MAP[t.category || 'ETC']?.split(' ')[1] || '기타'}
                </span>
              </div>
              <p className="font-bold text-slate-800 text-sm leading-snug">{t.description}</p>
            </div>

            <div className="flex items-center gap-3 shrink-0">
              <span className={`font-extrabold text-sm ${t.type === 'income' ? 'text-sky-600' : 'text-rose-500'}`}>
                {t.type === 'income' ? '+' : '-'}{t.amount.toLocaleString()}원
              </span>
              <button 
                onClick={() => openEditModal(t)} 
                className="p-1.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-500 text-xs transition-colors"
                title="내역 수정"
              >
                ⚙️
              </button>
            </div>
          </div>
        ))}

        {currentFilteredList.length === 0 && (
          <div className="py-12 text-center text-slate-400 text-xs bg-slate-50/50 rounded-2xl border border-dashed border-slate-200">
            선택하신 기간 내 조건에 부합하는 내역이 존재하지 않습니다.
          </div>
        )}
      </div>

      {/* 5. 내역 수정 모달 */}
      {isModalOpen && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-3xl p-6 shadow-2xl max-w-sm w-full border border-slate-100 space-y-4">
            <h3 className="text-base font-bold text-slate-800 text-center flex items-center justify-center gap-1.5">
              <span>⚙️</span> 내역 편집
            </h3>
            
            <div className="space-y-3 text-left">
              <div>
                <label className="text-[11px] font-semibold text-slate-500 block mb-1">유형</label>
                <select 
                  value={editType} 
                  onChange={(e) => setEditType(e.target.value as 'income' | 'expense')} 
                  className="w-full px-3.5 py-2.5 rounded-xl border-2 border-slate-200 focus:border-sky-500 outline-none text-xs text-slate-800 transition-colors bg-white cursor-pointer"
                >
                  <option value="expense">지출 (출금)</option>
                  <option value="income">수입 (입금)</option>
                </select>
              </div>

              <div>
                <label className="text-[11px] font-semibold text-slate-500 block mb-1">카테고리</label>
                <select 
                  value={editCategory} 
                  onChange={(e) => setEditCategory(e.target.value)} 
                  className="w-full px-3.5 py-2.5 rounded-xl border-2 border-slate-200 focus:border-sky-500 outline-none text-xs text-slate-800 transition-colors bg-white cursor-pointer"
                >
                  {Object.entries(CATEGORY_MAP).map(([key, value]) => (
                    <option key={key} value={key}>{value}</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="text-[11px] font-semibold text-slate-500 block mb-1">날짜 및 시분 설정</label>
                <input 
                  type="datetime-local" 
                  value={editDate} 
                  onChange={(e) => setEditDate(e.target.value)} 
                  className="w-full px-3.5 py-2.5 rounded-xl border-2 border-slate-200 focus:border-sky-500 outline-none text-xs text-slate-800 transition-colors bg-white"
                />
              </div>

              <div>
                <label className="text-[11px] font-semibold text-slate-500 block mb-1">내역</label>
                <input 
                  type="text" 
                  value={editDescription} 
                  onChange={(e) => setEditDescription(e.target.value)} 
                  className="w-full px-3.5 py-2.5 rounded-xl border-2 border-slate-200 focus:border-sky-500 outline-none text-xs text-slate-800 transition-colors bg-white"
                />
              </div>

              <div>
                <label className="text-[11px] font-semibold text-slate-500 block mb-1">금액</label>
                <input 
                  type="number" 
                  value={editAmount} 
                  onChange={(e) => setEditAmount(Number(e.target.value))} 
                  className="w-full px-3.5 py-2.5 rounded-xl border-2 border-slate-200 focus:border-sky-500 outline-none text-xs text-slate-800 transition-colors bg-white"
                />
              </div>
            </div>

            <div className="flex gap-2 pt-2">
              <button 
                onClick={handleUpdate} 
                className="flex-1 py-2.5 bg-sky-500 hover:bg-sky-600 text-white font-bold text-xs rounded-xl transition-colors shadow-md shadow-sky-200"
              >
                저장
              </button>
              <button 
                onClick={handleDelete} 
                className="flex-1 py-2.5 bg-rose-500 hover:bg-rose-600 text-white font-bold text-xs rounded-xl transition-colors shadow-md shadow-rose-200"
              >
                삭제
              </button>
              <button 
                onClick={() => setIsModalOpen(false)} 
                className="flex-1 py-2.5 bg-slate-100 hover:bg-slate-200 text-slate-600 font-bold text-xs rounded-xl transition-colors"
              >
                취소
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}