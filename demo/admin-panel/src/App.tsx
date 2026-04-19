import React from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import AdminShell from "./pages/AdminShell";
import LoginPage from "./pages/LoginPage";

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/*" element={<AdminShell />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

