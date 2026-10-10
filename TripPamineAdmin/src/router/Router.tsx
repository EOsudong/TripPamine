import { BrowserRouter, Routes, Route } from "react-router-dom"
import AdminLogin from "../pages/AdminLogin"
import AdminDashboard from "../pages/AdminDashboard"
import AdminUsers from "../pages/AdminUsers"
import AdminQuests from "../pages/AdminQuests"
import AdminQuestChapters from "../pages/AdminQuestChapters"
import AdminQuestSteps from "../pages/AdminQuestSteps"
import AdminTitles from "../pages/AdminTitles"
import AdminBoards from "../pages/AdminBoards"
import AdminBoardReports from "../pages/AdminBoardReports"
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
                    path="/quests/chapters"
                    element={
                        <ProtectedRoute>
                            <AdminQuestChapters />
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/quests/:questId/steps"
                    element={
                        <ProtectedRoute>
                            <AdminQuestSteps />
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/titles"
                    element={
                        <ProtectedRoute>
                            <AdminTitles />
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
                <Route
                    path="/boards/reports"
                    element={
                        <ProtectedRoute>
                            <AdminBoardReports />
                        </ProtectedRoute>
                    }
                />
            </Routes>
        </BrowserRouter>
    )
}



