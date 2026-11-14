import { Navigate, Route, Routes } from 'react-router-dom'
import Shell from './layouts/Shell'
import Dashboard from './pages/Dashboard'
import ResourcePage from './pages/ResourcePage'
import Contracts from './pages/Contracts'
import { RequestQueue, RequestReview } from './pages/Requests'
import { AuditPage, OperationsPage } from './pages/Operations'
import { CollateralInquiries, ReleaseQueue } from './pages/MiscPages'
import LocateQueue from './pages/LocateQueue'
import AuthPage from './pages/AuthPage'
import { useApp } from './hooks/useApp'
/** App: all Custodian-only views are routed; no Borrower operations are exposed. */
export default function App(){
  const {token}=useApp()
  return <Routes>
    <Route path="auth" element={<AuthPage/>}/>
    <Route element={token?<Shell/>:<Navigate replace to="/auth"/>}>
      <Route index element={<Dashboard/>}/>
      <Route path="requests" element={<RequestQueue/>}/>
      <Route path="locate-requests" element={<LocateQueue/>}/>
      <Route path="requests/review" element={<RequestReview/>}/>
      <Route path="requests/review/:reference" element={<RequestReview/>}/>
      <Route path="organizations" element={<ResourcePage name="organizations"/>}/>
      <Route path="mandates" element={<ResourcePage name="mandates"/>}/>
      <Route path="inventory" element={<ResourcePage name="inventory"/>}/>
      <Route path="mslas" element={<ResourcePage name="mslas"/>}/>
      <Route path="credit-limits" element={<ResourcePage name="credit"/>}/>
      <Route path="haircuts" element={<ResourcePage name="haircuts"/>}/>
      <Route path="lending-fees" element={<ResourcePage name="fees"/>}/>
      <Route path="contracts" element={<Contracts/>}/>
      <Route path="settlement" element={<OperationsPage kind="settlement"/>}/>
      <Route path="margin" element={<OperationsPage kind="margin"/>}/>
      <Route path="recalls" element={<OperationsPage kind="recalls"/>}/>
      <Route path="payments" element={<OperationsPage kind="payments"/>}/>
      <Route path="release-queue" element={<ReleaseQueue/>}/>
      <Route path="close" element={<OperationsPage kind="close"/>}/>
      <Route path="audit" element={<AuditPage/>}/>
      <Route path="collateral-inquiries" element={<CollateralInquiries/>}/>
      <Route path="*" element={<Navigate replace to="/"/>}/>
    </Route>
  </Routes>
}
