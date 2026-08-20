import { BrowserRouter, Routes, Route } from "react-router-dom"
import AdminLogin from "../pages/AdminLogin"
import AdminDashboard from "../pages/AdminDashboard"
import AdminUsers from "../pages/AdminUsers"
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
      </Routes>
    </BrowserRouter>
  )
}
