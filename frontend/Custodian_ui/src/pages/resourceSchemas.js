/** Form contracts transcribed from the Custodian Java request DTOs. */
const orgTypes = ['LENDER', 'BORROWER', 'CUSTODIAN']
const activeInactive = ['ACTIVE', 'INACTIVE']
const states = ['ACTIVE', 'INACTIVE', 'EXPIRED']
const assetTypes = ['EQUITY','CORPORATE_BOND','GOVT_BOND','ETF']
const collateralTypes = ['CASH','ETF','GOVT_BOND','CORPORATE_BOND','EQUITY']
const f = (name, options = {}) => ({ name, label: options.label, ...options })
const reference = (name) => f(name, { required: true, max: 80, hint: 'Must be a unique business reference within your authorized scope.' })
export const schemas = {
  organizations: {
    title: 'Organizations', subtitle: 'Register and maintain Lender, Borrower and Custodian counterparties inside your authorized organization scope.', resource: 'organizations', key: 'organizationReference', icon: 'Organizations', category: 'Master data', fix: 'Identity & party validation before FIX workflow',
    fields: [reference('organizationReference'), f('organizationName', { required: true, max: 255 }), f('organizationType', { options: orgTypes, required: true }), f('status', { options: activeInactive, required: true })],
    columns: ['organizationReference','organizationName','organizationType','status','version'],
    guidance: ['Use the legal counterparty name and a stable organization reference.','Organization types are LENDER, BORROWER and CUSTODIAN. The owning Custodian comes from the JWT.','Updates require the current server version to avoid overwriting concurrent changes.'],
    note: 'Creating a party does not automatically create a lending mandate or authorize its trades.',
  },
  mandates: {
    title: 'Lender Mandates', subtitle: 'Authorize relationships between the authenticated Custodian and a lender organization with effective dates.', resource: 'mandates', key: 'mandateReference', category: 'Lending setup', fix: 'Party and mandate eligibility',
    fields: [reference('mandateReference'), f('lenderOrganizationReference', { required: true, hint: 'Select or enter a lender reference returned by Organizations.' }), f('effectiveFrom', { type: 'date', required: true }), f('effectiveTo', { type: 'date', hint: 'Optional. Must not precede effectiveFrom.' }), f('status', { options: states, required: true })],
    columns: ['mandateReference','lenderOrganizationReference','effectiveFrom','effectiveTo','status','version'],
    guidance: ['Create an active LENDER organization first.','Dates establish the program authorization period for each lending mandate.','Choose the lender under your Custodian scope; you cannot change the owning Custodian.'],
    note: 'An MSLA must reference an eligible mandate before any Borrower request can be approved.',
  },
  inventory: {
    title: 'Lender Inventory', subtitle: 'Maintain securities available for lending. Reserved, on-loan and available quantities remain service-derived.', resource: 'inventory', key: 'securityIsin', category: 'Supply controls', fix: 'Inventory reservation & U1 loan validation',
    fields: [f('lenderOrganizationReference', { required: true, hint: 'Must be an authorized lender organization.' }), f('securityIsin', { required: true, pattern: '[A-Z0-9]{12}', minLength: 12, max: 12, hint: '12 uppercase alphanumeric characters; a genuine security ISIN.' }), f('totalQuantity', { required: true, type: 'number', min: 0, step: 1, hint: 'Total supply; reserved/on-loan/available are calculated by the backend.' })],
    columns: ['lenderOrganizationReference','securityIsin','totalQuantity','onLoanQuantity','reservedQuantity','availableQuantity','version'],
    guidance: ['Use an existing lender and a valid 12-character security ISIN.','Edit total quantity only; on-loan and reserved are read-only ledger states.','Updates use optimistic concurrency (version). Availability is not recalculated in the browser.'],
    note: 'A successful inventory edit does not override active loan reservations or the securities master.',
  },
  mslas: {
    title: 'MSLA Agreements', subtitle: 'Configure Master Securities Lending Agreement terms, collateral coverage and operational controls.', resource: 'mslas', key: 'mslaReference', category: 'Agreement governance', fix: 'BB → BA collateral rules · AK trade confirmation',
    fields: [reference('mslaReference'), reference('mandateReference'), reference('borrowerOrganizationReference'), f('effectiveDate', { type: 'date', required: true }), f('expiryDate', { type: 'date' }), f('agreementStatus', { options: ['DRAFT','ACTIVE','INACTIVE','EXPIRED'], required: true }), f('baseCurrency', { required: true, pattern: '[A-Z]{3}', placeholder: 'INR', hint: 'Three-letter uppercase currency code.' }), f('eligibleSecurities', { required: true, placeholder: assetTypes.join(', '), hint: 'Comma-separated allowed asset types: EQUITY, CORPORATE_BOND, GOVT_BOND, ETF. Sent as eligibleSecurities.allowed_types.' }), f('collateralType', { options: collateralTypes, required: true }), f('collateralCoveragePct', { required: true, type: 'number', min: 100, step: 0.0001, hint: 'Percentage, minimum 100. Do not divide by 100.' }), f('mtmIntervalMinutes', { required: true, type: 'number', min: 1, step: 1 }), f('marginCallWindowHours', { required: true, type: 'number', min: 1, step: 1 }), f('recallRule', { max: 500, hint: 'Operational recall term; align with T+1 workflow.' }), f('settlementCalendarCode', { required: true, max: 80, hint: 'Backend-supported settlement calendar reference.' }), f('defaultRules', { multiline: true, placeholder: '{}', hint: 'Optional JSON object, never interpreted as calculations in the UI.' })],
    columns: ['mslaReference','mandateReference','borrowerOrganizationReference','agreementStatus','baseCurrency','collateralCoveragePct','version'],
    guidance: ['Create the lender mandate and Borrower organization first.','Coverage percentage, asset eligibility and settlement calendar must be validated by the backend.','MSLA terms drive Borrower eligibility and subsequent collateral and settlement processing.'],
    note: 'MSLA governs agency-lending terms; do not bypass eligibility checks when a Borrower asks to borrow.',
  },
  credit: {
    title: 'Credit Limits & History', subtitle: 'Set and govern counterparty limits by MSLA; inspect server-calculated utilization and versioned changes.', resource: 'creditLimits', key: 'mslaReference', category: 'Risk controls', fix: 'AK authorization · credit check',
    fields: [reference('mslaReference'), f('limitAmount', { type: 'number', required: true, min: 0, step: 0.01, hint: 'Limit in base-currency units, not crore. Must be nonnegative.' }), f('status', { options: activeInactive, required: true, updateOnly: true }), f('reason', { required: true, multiline: true, hint: 'Reason is mandatory for creation and every amendment.' })],
    columns: ['mslaReference','currency','limitAmount','usedAmount','reservedAmount','availableAmount','status','version'],
    guidance: ['A credit limit is scoped to an existing MSLA.','The server computes used, reserved and available credit; do not edit those fields.','Use the returned version on update and review immutable change history.'],
    note: 'Limit changes can affect future approvals; settled contract exposure remains owned by the service.',
  },
  haircuts: {
    title: 'Collateral Haircuts', subtitle: 'Define agreement-scoped collateral risk haircuts and effective windows, without calculating required collateral in the browser.', resource: 'haircuts', key: 'haircutId', scope: 'mslaReference', category: 'Collateral controls', fix: 'BB collateral inquiry → BA report',
    fields: [f('collateralType', { required: true, options: collateralTypes }), f('currency', { required: true, pattern: '[A-Z]{3}', placeholder: 'INR' }), f('haircutPercentage', { required: true, type: 'number', min: 0, step: 0.0001, hint: 'Enter the percentage value as configured by the MSLA rules.' }), f('effectiveFrom', { required: true, type: 'datetime-local' }), f('effectiveTo', { type: 'datetime-local' })],
    columns: ['haircutId','mslaReference','collateralType','currency','haircutPercentage','effectiveFrom','effectiveTo','version'],
    guidance: ['Enter an existing MSLA reference to load agreement haircuts.','The backend chooses applicable rules and validates valuation calculations.','A haircut update requires the returned rule version. Effective dates use UTC instants.'],
    note: 'Do not derive collateral requirements from haircut percentages in the browser.',
  },
  fees: {
    title: 'Lending Fee Schedules', subtitle: 'Configure effective annual lending fee rates per agreement and optionally per security ISIN.', resource: 'lendingFees', key: 'feeId', scope: 'mslaReference', category: 'Commercial terms', fix: 'AE trade terms · accrual/payment settlement',
    fields: [f('securityIsin', { pattern: '[A-Z0-9]{12}', max: 12, hint: 'Optional: blank applies at agreement scope.' }), f('annualFeePct', { required: true, type: 'number', min: 0, step: 0.000001 }), f('effectiveFrom', { required: true, type: 'date' }), f('effectiveTo', { type: 'date' }), f('status', { required: true, options: activeInactive })],
    columns: ['feeId','mslaReference','securityIsin','annualFeePct','effectiveFrom','effectiveTo','status','version'],
    guidance: ['An empty security ISIN can define the default agreement fee.','Create time-bounded security overrides where commercial terms require them.','Accrual results, settlement charges and payment references are always produced by the backend.'],
    note: 'Fee accrual must use an applicable server-selected rule; the browser does not compute interest or rebate.',
  },
}
