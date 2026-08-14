import { useState, createContext, useContext } from "react";
import { logoutApi } from "../api/auth";

const AuthContext = createContext<
  | {
      isLoggedIn: boolean;
      login: (accessToken: string, userId: number) => void;
      logout: () => Promise<void>;
    }
  | undefined
>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [isLoggedIn, setIsLoggedIn] = useState(
    !!localStorage.getItem("accessToken"),
  );

  const login = (accessToken: string, userId: number) => {
    localStorage.setItem("accessToken", accessToken);
    localStorage.setItem("userId", userId.toString());

    setIsLoggedIn(true);
  };

  const logout = async () => {
    try {
      // 백엔드에 로그아웃 요청
      await logoutApi();
    } catch (error) {
      console.error("로그아웃 API 실패:",error);
    }finally {
      localStorage.removeItem("accessToken");
      localStorage.removeItem("userId");

      setIsLoggedIn(false);
    }
  };
  return (
    <AuthContext.Provider value={{ isLoggedIn, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth는 AuthProvider 내부에서만 사용 가능합니다.");
  }
  return context;
}
