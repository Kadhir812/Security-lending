/** Reference-inspired presentation fixtures. NEVER returned from API modules or used for writes. */
export const ticker = [
  { symbol: 'RELIANCE', price: '2,954.60', move: '+0.84%', up: true },
  { symbol: 'INFY', price: '1,688.20', move: '+1.12%', up: true },
  { symbol: 'TCS', price: '4,309.50', move: '+0.63%', up: true },
  { symbol: 'HDFCBANK', price: '1,642.75', move: '−0.27%', up: false },
  { symbol: 'NIFTY50', price: '26,347.80', move: '+0.71%', up: true },
]
export const demoRequests = [
  { requestReference: 'LR-10091', mslaReference: 'MSLA-BR-001', securityIsin: 'INE002A01018', symbol: 'RELIANCE', quantity: 500000, status: 'PENDING', amount: '₹124.50 Cr' },
  { requestReference: 'LR-10092', mslaReference: 'MSLA-BR-002', securityIsin: 'INE009A01021', symbol: 'INFY', quantity: 300000, status: 'APPROVED', amount: '₹42.00 Cr' },
  { requestReference: 'LR-10093', mslaReference: 'MSLA-BR-001', securityIsin: 'INE040A01034', symbol: 'HDFCBANK', quantity: 250000, status: 'PENDING', amount: '₹36.25 Cr' },
  { requestReference: 'LR-10094', mslaReference: 'MSLA-BR-003', securityIsin: 'INE467B01029', symbol: 'TCS', quantity: 100000, status: 'APPROVED', amount: '₹43.10 Cr' },
]
export const demoContracts = [
  { requestReference: 'LR-10092', transactionReference: 'TX-20260081', borrower: 'Fund Alpha', symbol: 'RELIANCE', securityIsin: 'INE002A01018', quantity: 500000, amount: '₹124.50 Cr', collateral: '₹130.72 Cr', status: 'ON_LOAN' },
  { requestReference: 'LR-10095', transactionReference: 'TX-20260076', borrower: 'Fund Beta', symbol: 'INFY', securityIsin: 'INE009A01021', quantity: 300000, amount: '₹42.00 Cr', collateral: '₹43.96 Cr', status: 'ON_LOAN' },
  { requestReference: 'LR-10097', transactionReference: 'TX-20260074', borrower: 'Fund Alpha', symbol: 'HDFCBANK', securityIsin: 'INE040A01034', quantity: 250000, amount: '₹36.25 Cr', collateral: '₹37.80 Cr', status: 'ON_LOAN' },
  { requestReference: 'LR-10101', transactionReference: 'TX-20260073', borrower: 'Fund Gamma', symbol: 'TCS', securityIsin: 'INE467B01029', quantity: 100000, amount: '₹43.10 Cr', collateral: '₹45.26 Cr', status: 'PENDING_SETTLEMENT' },
]
export const trendValues = [762,778,776,789,787,777,784,808,803,819,824,838,826,836,821,825,846,842]
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
  { time: '10:23:52', code: 'U1', name: 'Loan Request' },
  { time: '10:22:37', code: 'BA', name: 'Collateral Report' },
  { time: '10:21:55', code: 'BB', name: 'Collateral Inquiry' },
  { time: '10:20:14', code: 'U2', name: 'Security Delivery' },
]
