/** Screenshot-specific, illustrative datasets. Never presented as backend state. */
export const ticker = [
  { symbol: 'RELIANCE', price: '2,954.60', move: '+0.84%', up: true },
  { symbol: 'INFY', price: '1,688.20', move: '+1.12%', up: true },
  { symbol: 'TCS', price: '4,309.50', move: '+0.63%', up: true },
  { symbol: 'HDFCBANK', price: '1,642.75', move: '−0.27%', up: false },
  { symbol: 'NIFTY50', price: '26,347.80', move: '+0.71%', up: true },
]
export const demoRequests = [
  { requestReference: 'LR-10091', borrowerOrganizationReference: 'Fund Alpha', securityIsin: 'RELIANCE', quantity: 500000, exposure: '₹124.50 Cr', status: 'PENDING', mslaReference: 'MSLA-001', requestedSettlementDate: '2026-11-12' },
  { requestReference: 'LR-10092', borrowerOrganizationReference: 'Fund Beta', securityIsin: 'INFY', quantity: 300000, exposure: '₹42.00 Cr', status: 'PENDING', mslaReference: 'MSLA-002', requestedSettlementDate: '2026-11-12' },
  { requestReference: 'LR-10093', borrowerOrganizationReference: 'Fund Alpha', securityIsin: 'HDFCBANK', quantity: 250000, exposure: '₹36.25 Cr', status: 'PENDING', mslaReference: 'MSLA-001', requestedSettlementDate: '2026-11-13' },
  { requestReference: 'LR-10094', borrowerOrganizationReference: 'Fund Gamma', securityIsin: 'TCS', quantity: 100000, exposure: '₹43.10 Cr', status: 'REVIEW', mslaReference: 'MSLA-003', requestedSettlementDate: '2026-11-13' },
]
export const demoContracts = [
  { transactionReference: 'TX-20260081', borrower: 'Fund Alpha', security: 'RELIANCE', quantity: 500000, exposure: '₹124.50 Cr', collateral: '₹130.72 Cr', status: 'ON_LOAN' },
  { transactionReference: 'TX-20260076', borrower: 'Fund Beta', security: 'INFY', quantity: 300000, exposure: '₹42.00 Cr', collateral: '₹43.96 Cr', status: 'ON_LOAN' },
  { transactionReference: 'TX-20260074', borrower: 'Fund Alpha', security: 'HDFCBANK', quantity: 250000, exposure: '₹36.25 Cr', collateral: '₹37.80 Cr', status: 'ON_LOAN' },
  { transactionReference: 'TX-20260073', borrower: 'Fund Gamma', security: 'TCS', quantity: 100000, exposure: '₹43.10 Cr', collateral: '₹45.26 Cr', status: 'SETTLING' },
]
export const trendValues = [765, 780, 777, 790, 789, 777, 784, 807, 804, 818, 822, 834, 820, 830, 818, 824, 838, 842.3]
export const exposures = [
  { symbol: 'RELIANCE', amount: '₹297.62 Cr', portion: '35.4%', color: 'deep' },
  { symbol: 'INFY', amount: '₹124.30 Cr', portion: '14.8%', color: 'light' },
  { symbol: 'TCS', amount: '₹95.10 Cr', portion: '11.3%', color: 'gold' },
  { symbol: 'HDFCBANK', amount: '₹86.70 Cr', portion: '10.3%', color: 'light' },
  { symbol: 'ICICIBANK', amount: '₹72.40 Cr', portion: '8.6%', color: 'sand' },
  { symbol: 'Others', amount: '₹166.18 Cr', portion: '19.7%', color: 'olive' },
]
export const fixMessages = [
  { time: '10:24:11', code: 'AK', name: 'Trade Confirmation' },
  { time: '10:23:52', code: 'AE', name: 'Trade Capture Report' },
  { time: '10:22:37', code: 'BA', name: 'Collateral Report' },
  { time: '10:21:55', code: 'BB', name: 'Collateral Inquiry' },
  { time: '10:20:14', code: 'AF', name: 'Request For Security' },
]
export const auditEvents = [
  { time: '11 Nov, 10:24', title: 'Trade confirmation published', detail: 'AK · TX-20260081 · Fund Alpha', tone: 'success' },
  { time: '11 Nov, 10:23', title: 'Collateral check completed', detail: 'BA · RELIANCE · 103.2% coverage', tone: 'success' },
  { time: '11 Nov, 10:22', title: 'Margin exception identified', detail: 'Exposure review · Fund Gamma', tone: 'warning' },
  { time: '11 Nov, 10:21', title: 'Loan request submitted', detail: 'U1 · LR-10091 · RELIANCE', tone: 'info' },
]
