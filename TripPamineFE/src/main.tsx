// React 앱의 진입점(entry point).
// index.html의 <div id="root">에 App 컴포넌트를 실제로 그려 넣는 역할만 합니다.
import React from "react"
import ReactDOM from "react-dom/client"
import App from "./App"
import "./index.css" // Tailwind CSS 불러오기

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
