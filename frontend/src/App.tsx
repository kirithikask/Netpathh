import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { Layout } from './components/Layout';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { PathsPage } from './pages/PathsPage';
import { PathDetailPage } from './pages/PathDetailPage';
import { EndpointsPage } from './pages/EndpointsPage';
import { ApplicationsPage } from './pages/ApplicationsPage';

function RequireAuth() {
  const { isAuthenticated } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <Layout />;
}

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<RequireAuth />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/paths" element={<PathsPage />} />
          <Route path="/paths/:id" element={<PathDetailPage />} />
          <Route path="/endpoints" element={<EndpointsPage />} />
          <Route path="/applications" element={<ApplicationsPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  );
}
