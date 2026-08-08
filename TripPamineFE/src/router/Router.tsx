// React Router 설정 파일.
// URL 주소(경로)와 어떤 페이지 컴포넌트를 보여줄지 여기서 한 번에 관리합니다.
import { BrowserRouter, Routes, Route } from "react-router-dom"
import Home from "../pages/Home"
import Login from "../pages/Login"
import Join from "../pages/Join"
import FindId from "../pages/FindId"
import FindPw from "../pages/FindPw"
import MyPage from "../pages/MyPage"
import Detail from "../pages/Detail"

export default function Router() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Home />} />           {/* 메인 페이지 */}
        <Route path="/login" element={<Login />} />       {/* 로그인 */}
        <Route path="/join" element={<Join />} />         {/* 회원가입 */}
        <Route path="/find-id" element={<FindId />} />    {/* 아이디 찾기 */}
        <Route path="/find-pw" element={<FindPw />} />    {/* 비밀번호 찾기 */}
        <Route path="/mypage" element={<MyPage />} />     {/* 마이페이지 (북마크/저장된 코스) */}
        {/* /detail/destination/jeju , /detail/festival/boryeong-mud 처럼
            type(destination|festival)과 id 값을 URL에서 그대로 받아서 Detail.jsx에서 분기 처리 */}
        <Route path="/detail/:type/:id" element={<Detail />} />
      </Routes>
    </BrowserRouter>
  )
}
