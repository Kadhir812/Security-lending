import { Navigate, Route, Routes } from 'react-router-dom'
import Shell from './layouts/Shell'
import Dashboard from './pages/Dashboard'
import { LocatesPage, NewLocatePage, LocateDetailPage } from './pages/Locates'
import { RequestsPage, NewRequestPage, RequestDetailPage, EligibleMslaPage, CollateralInquiryPage } from './pages/Requests'
import { ContractPortfolioPage, ContractDetailPage, SettlementPage, CashCollateralPage } from './pages/Contracts'
import { MarginPage, ReturnsPage, FeesPage, ReleasesPage, ActivityPage } from './pages/Operations'
import { AuthPage, GlossaryPage } from './pages/Misc'
/** Borrower-only routes; no Custodian writes, loan approve/reject or settlement simulation. */
export default function App(){return <Routes><Route element={<Shell/>}>
  <Route index element={<Dashboard/>}/>
  <Route path="locates" element={<LocatesPage/>}/><Route path="locates/new" element={<NewLocatePage/>}/><Route path="locates/:reference" element={<LocateDetailPage/>}/>
  <Route path="requests" element={<RequestsPage/>}/><Route path="requests/new" element={<NewRequestPage/>}/><Route path="requests/:reference" element={<RequestDetailPage/>}/>
  <Route path="eligible-mslas" element={<EligibleMslaPage/>}/><Route path="collateral-inquiries" element={<CollateralInquiryPage/>}/>
  <Route path="contracts" element={<ContractPortfolioPage/>}/><Route path="contracts/:transactionReference" element={<ContractDetailPage/>}/>
  <Route path="settlement" element={<SettlementPage/>}/><Route path="settlement/:transactionReference" element={<SettlementPage/>}/>
  <Route path="collateral" element={<CashCollateralPage/>}/><Route path="collateral/:requestReference" element={<CashCollateralPage/>}/>
  <Route path="margin" element={<MarginPage/>}/><Route path="margin/:transactionReference" element={<MarginPage/>}/>
  <Route path="returns" element={<ReturnsPage/>}/><Route path="returns/:transactionReference" element={<ReturnsPage/>}/>
  <Route path="fees" element={<FeesPage/>}/><Route path="fees/:transactionReference" element={<FeesPage/>}/>
  <Route path="releases" element={<ReleasesPage/>}/><Route path="releases/:transactionReference" element={<ReleasesPage/>}/>
  <Route path="activity" element={<ActivityPage/>}/><Route path="auth" element={<AuthPage/>}/><Route path="glossary" element={<GlossaryPage/>}/>
  <Route path="*" element={<Navigate replace to="/"/>}/>
</Route></Routes>}
