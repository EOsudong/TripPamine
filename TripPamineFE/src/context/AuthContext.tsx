import { useState, createContext, useContext } from "react";

const AuthContext = createContext<
  | {
      isLoggedIn: boolean;
      login: (accessToken: string, userId: number) => void;
      logout: () => void;
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

  const logout = () => {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("userId");

    setIsLoggedIn(false);
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
