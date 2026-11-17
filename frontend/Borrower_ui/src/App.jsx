import { Navigate, Route, Routes } from 'react-router-dom'
import Shell from './layouts/Shell'
import Dashboard from './pages/Dashboard'
import { LocatesPage, NewLocatePage, LocateDetailPage } from './pages/Locates'
import { RequestsPage, NewRequestPage, RequestDetailPage, EligibleMslaPage, CollateralInquiryPage } from './pages/Requests'
import { ContractPortfolioPage, ContractDetailPage, SettlementPage, CashCollateralPage } from './pages/Contracts'
import { MarginPage, ReturnsPage, FeesPage, ReleasesPage, ActivityPage } from './pages/Operations'
import AuthPage from './pages/AuthPage'
import { GlossaryPage } from './pages/Misc'
import CustodianLinkPage from './pages/CustodianLinkPage'
import { useApp } from './hooks/useApp'

/** Borrower-only routes; no Custodian writes, loan approve/reject or settlement simulation. */
export default function App() {
  const { token } = useApp()

  return (
    <Routes>
      <Route path="auth" element={token ? <Navigate replace to="/" /> : <AuthPage />} />
      <Route element={token ? <Shell /> : <Navigate replace to="/auth" />}>
        <Route index element={<Dashboard />} />
        <Route path="locates" element={<LocatesPage />} />
        <Route path="locates/new" element={<NewLocatePage />} />
        <Route path="locates/:reference" element={<LocateDetailPage />} />
        <Route path="requests" element={<RequestsPage />} />
        <Route path="requests/new" element={<NewRequestPage />} />
        <Route path="requests/:reference" element={<RequestDetailPage />} />
        <Route path="eligible-mslas" element={<EligibleMslaPage />} />
        <Route path="collateral-inquiries" element={<CollateralInquiryPage />} />
        <Route path="contracts" element={<ContractPortfolioPage />} />
        <Route path="contracts/:requestReference" element={<ContractDetailPage />} />
        <Route path="settlement" element={<SettlementPage />} />
        <Route path="settlement/:requestReference" element={<SettlementPage />} />
        <Route path="collateral" element={<CashCollateralPage />} />
        <Route path="collateral/:requestReference" element={<CashCollateralPage />} />
        <Route path="margin" element={<MarginPage />} />
        <Route path="margin/:requestReference" element={<MarginPage />} />
        <Route path="returns" element={<ReturnsPage />} />
        <Route path="returns/:requestReference" element={<ReturnsPage />} />
        <Route path="fees" element={<FeesPage />} />
        <Route path="fees/:requestReference" element={<FeesPage />} />
        <Route path="releases" element={<ReleasesPage />} />
        <Route path="releases/:requestReference" element={<ReleasesPage />} />
        <Route path="activity" element={<ActivityPage />} />
        <Route path="custodian-link" element={<CustodianLinkPage />} />
        <Route path="glossary" element={<GlossaryPage />} />
        <Route path="*" element={<Navigate replace to="/" />} />
      </Route>
    </Routes>
  )
}
