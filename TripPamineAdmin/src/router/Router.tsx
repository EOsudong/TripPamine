import { BrowserRouter, Routes, Route } from "react-router-dom"
import AdminLogin from "../pages/AdminLogin"
import AdminDashboard from "../pages/AdminDashboard"
import AdminUsers from "../pages/AdminUsers"
import AdminQuests from "../pages/AdminQuests"
import AdminBoards from "../pages/AdminBoards"
import ProtectedRoute from "../components/ProtectedRoute"

export default function Router() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/login" element={<AdminLogin />} />
                <Route
                    path="/"
                    element={
                        <ProtectedRoute>
                            <AdminDashboard />
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/users"
                    element={
                        <ProtectedRoute>
                            <AdminUsers />
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/quests"
                    element={
                        <ProtectedRoute>
                            <AdminQuests />
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/boards"
                    element={
                        <ProtectedRoute>
                            <AdminBoards />
                        </ProtectedRoute>
                    }
                />
            </Routes>
        </BrowserRouter>
    )
}



