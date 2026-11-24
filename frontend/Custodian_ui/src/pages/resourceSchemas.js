/** Form contracts transcribed from the Custodian Java request DTOs. */
const orgTypes = ['LENDER', 'BORROWER', 'CUSTODIAN']
const activeInactive = ['ACTIVE', 'INACTIVE']
const states = ['ACTIVE', 'INACTIVE', 'EXPIRED']
const assetTypes = ['EQUITY','CORPORATE_BOND','GOVT_BOND','ETF']
const collateralTypes = ['CASH','ETF','GOVT_BOND','CORPORATE_BOND','EQUITY']
const f = (name, options = {}) => ({ name, label: options.label, ...options })
const reference = (name, generatedPrefix, extra = {}) => f(name, { ...extra, required: true, max: 80, generatedPrefix, hint: extra.hint || 'Generated automatically; use this reference in subsequent workflows.' })
export const schemas = {
  organizations: {
    title: 'Organizations', subtitle: 'Register and maintain Lender, Borrower and Custodian counterparties inside your authorized organization scope.', resource: 'organizations', key: 'organizationReference', icon: 'Organizations', category: 'Master data', fix: 'Identity & party validation before FIX workflow',
    fields: [reference('organizationReference', 'ORG'), f('organizationName', { required: true, max: 255 }), f('organizationType', { options: orgTypes, required: true }), f('status', { options: activeInactive, required: true })],
    columns: ['organizationReference','organizationName','organizationType','status','version'],
    guidance: ['Use the legal counterparty name and a stable organization reference.','Organization types are LENDER, BORROWER and CUSTODIAN. The owning Custodian comes from the JWT.','Updates require the current server version to avoid overwriting concurrent changes.'],
    note: 'Creating a party does not automatically create a lending mandate or authorize its trades.',
  },
  mandates: {
    title: 'Lender Mandates', subtitle: 'Authorize relationships between the authenticated Custodian and a lender organization with effective dates.', resource: 'mandates', key: 'mandateReference', category: 'Lending setup', fix: 'Party and mandate eligibility',
    fields: [reference('mandateReference', 'MAND'), f('lenderOrganizationReference', { required: true, referenceOptions: 'lenders', hint: 'Choose an existing lender organization.' }), f('effectiveFrom', { type: 'date', required: true }), f('effectiveTo', { type: 'date', hint: 'Optional. Must not precede effectiveFrom.' }), f('status', { options: states, required: true })],
    columns: ['mandateReference','lenderOrganizationReference','effectiveFrom','effectiveTo','status','version'],
    guidance: ['Create an active LENDER organization first.','Dates establish the program authorization period for each lending mandate.','Choose the lender under your Custodian scope; you cannot change the owning Custodian.'],
    note: 'An MSLA must reference an eligible mandate before any Borrower request can be approved.',
  },
  inventory: {
    title: 'Lender Inventory', subtitle: 'Maintain securities available for lending. Reserved, on-loan and available quantities remain service-derived.', resource: 'inventory', key: 'securityIsin', category: 'Supply controls', fix: 'Inventory reservation & U1 loan validation',
    fields: [f('lenderOrganizationReference', { required: true, referenceOptions: 'lenders', hint: 'Choose an existing lender organization.' }), f('securityIsin', { required: true, referenceOptions: 'securities', hint: 'Choose an active security from the market reference service.' }), f('totalQuantity', { required: true, type: 'number', min: 0, step: 1, hint: 'Total supply; reserved/on-loan/available are calculated by the backend.' })],
    columns: ['lenderOrganizationReference','securityIsin','totalQuantity','onLoanQuantity','reservedQuantity','availableQuantity','version'],
    guidance: ['Use an existing lender and a valid 12-character security ISIN.','Edit total quantity only; on-loan and reserved are read-only ledger states.','Updates use optimistic concurrency (version). Availability is not recalculated in the browser.'],
    note: 'A successful inventory edit does not override active loan reservations or the securities master.',
  },
  mslas: {
    title: 'MSLA & Lending Setup', subtitle: 'Create an immediately active lender mandate and borrower agreement with credit, collateral and fee terms in one submission.', resource: 'mslas', key: 'mslaReference', category: 'Agreement governance', fix: 'BB → BA collateral rules · AK trade confirmation',
    fields: [reference('mslaReference', 'MSLA'), f('mandateReference', { required: true, referenceOptions: 'mandates', hint: 'Choose an existing lender mandate.' }), f('borrowerOrganizationReference', { required: true, referenceOptions: 'borrowers', hint: 'Choose an existing Borrower organization.' }), f('effectiveDate', { type: 'date', required: true }), f('expiryDate', { type: 'date' }), f('agreementStatus', { options: ['DRAFT','ACTIVE','INACTIVE','EXPIRED'], required: true }), f('baseCurrency', { required: true, options: ['INR','USD','EUR','GBP','CAD','AUD'] }), f('eligibleSecurities', { required: true, options: assetTypes, multiple: true, hint: 'Select every allowed asset class.' }), f('collateralType', { options: collateralTypes, required: true }), f('collateralCoveragePct', { required: true, type: 'number', min: 100, step: 0.0001, hint: 'Percentage, minimum 100. Do not divide by 100.' }), f('annualRebatePct', { required: true, type: 'number', min: 0, step: 0.000001, hint: 'Annual rebate percentage applied by the backend to confirmed CASH collateral and accrued daily.' }), f('mtmIntervalMinutes', { required: true, type: 'number', min: 1, step: 1 }), f('marginCallWindowHours', { required: true, type: 'number', min: 1, step: 1 }), f('recallRule', { max: 500, hint: 'Operational recall term; align with T+1 workflow.' }), f('settlementCalendarCode', { required: true, max: 80, hint: 'Backend-supported settlement calendar code.' }), f('defaultRules', { multiline: true, placeholder: '{}', hint: 'Optional JSON object, never interpreted as calculations in the UI.' })],
    setupFields: [
      f('mandateMode', { options: [{ value: 'CREATE', label: 'Create a new mandate' }, { value: 'REUSE', label: 'Use an existing active mandate' }], required: true, defaultValue: 'CREATE', section: 'Lender mandate' }),
      f('existingMandateReference', { required: true, referenceOptions: 'mandates', section: 'Lender mandate', when: 'REUSE', hint: 'Select a mandate that covers the agreement dates.' }),
      reference('mandateReference', 'MAND', { section: 'Lender mandate', when: 'CREATE' }),
      f('lenderOrganizationReference', { required: true, referenceOptions: 'lenders', section: 'Lender mandate', when: 'CREATE', hint: 'Choose an active lender organization.' }),
      f('mandateEffectiveFrom', { type: 'date', required: true, defaultValue: '$today', section: 'Lender mandate', when: 'CREATE' }),
      f('mandateEffectiveTo', { type: 'date', section: 'Lender mandate', when: 'CREATE', hint: 'Leave blank for an open-ended mandate.' }),
      reference('mslaReference', 'MSLA', { section: 'Borrower agreement' }),
      f('borrowerOrganizationReference', { required: true, referenceOptions: 'borrowers', section: 'Borrower agreement', hint: 'Choose an active Borrower organization.' }),
      f('effectiveDate', { type: 'date', required: true, defaultValue: '$today', section: 'Borrower agreement' }),
      f('expiryDate', { type: 'date', section: 'Borrower agreement', hint: 'Leave blank for an open-ended agreement.' }),
      f('baseCurrency', { required: true, options: ['INR','USD','EUR','GBP','CAD','AUD'], section: 'Borrower agreement' }),
      f('eligibleSecurities', { required: true, options: assetTypes, multiple: true, section: 'Borrower agreement', hint: 'Select every allowed asset class.' }),
      f('collateralCoveragePct', { required: true, type: 'number', min: 100, step: 0.0001, section: 'Borrower agreement', hint: 'Coverage percentage, minimum 100.' }),
      f('annualRebatePct', { required: true, type: 'number', min: 0, step: 0.000001, section: 'Borrower agreement', hint: 'Annual rebate rate applied by the backend to confirmed CASH collateral and accrued daily.' }),
      f('mtmIntervalMinutes', { required: true, type: 'number', min: 1, step: 1, section: 'Borrower agreement' }),
      f('marginCallWindowHours', { required: true, type: 'number', min: 1, step: 1, section: 'Borrower agreement' }),
      f('recallRule', { max: 500, section: 'Borrower agreement', hint: 'Operational recall term; align with the configured settlement workflow.' }),
      f('settlementCalendarCode', { required: true, max: 80, section: 'Borrower agreement', hint: 'Backend-supported settlement calendar code.' }),
      f('creditLimitAmount', { required: true, type: 'number', min: 0, step: 0.01, section: 'Credit and collateral' }),
      f('creditLimitReason', { required: true, multiline: true, max: 500, section: 'Credit and collateral' }),
      f('cashHaircut', { label: 'CASH haircut', section: 'Credit and collateral', disabled: true, hint: 'Coverage sets required cash (105% coverage means 105% of loan value). CASH is valued at face value, so its haircut is 0%.' }),
      f('annualFeePct', { required: true, type: 'number', min: 0, step: 0.000001, section: 'Lending fee' }),
    ],
    columns: ['mslaReference','mandateReference','borrowerOrganizationReference','agreementStatus','baseCurrency','collateralCoveragePct','version'],
    guidance: ['The submission creates or reuses an active lender mandate, then commits the MSLA, credit limit, zero-percent CASH haircut and annual fee together.','Choose a lender and Borrower already registered with this Custodian. Reuse an active mandate when adding another Borrower agreement for that lender.','MSLA terms drive Borrower eligibility and later collateral and settlement processing.'],
    note: 'New agreements are active immediately. The supported CASH haircut is fixed at 0%; unsupported default-rule JSON is omitted from active setup.',
  },
  credit: {
    title: 'Credit Limits & History', subtitle: 'Set and govern counterparty limits by MSLA; inspect server-calculated utilization and versioned changes.', resource: 'creditLimits', key: 'mslaReference', category: 'Risk controls', fix: 'AK authorization · credit check',
    fields: [f('mslaReference', { required: true, referenceOptions: 'mslas', hint: 'Choose the MSLA covered by this credit limit.' }), f('limitAmount', { type: 'number', required: true, min: 0, step: 0.01, hint: 'Limit in base-currency units, not crore. Must be nonnegative.' }), f('status', { options: activeInactive, required: true, updateOnly: true }), f('reason', { required: true, multiline: true, hint: 'Reason is mandatory for creation and every amendment.' })],
    columns: ['mslaReference','currency','limitAmount','usedAmount','reservedAmount','availableAmount','status','version'],
    guidance: ['New credit limits are created in MSLA & Lending Setup; use this page to inspect or maintain an existing agreement limit.','The server computes used, reserved and available credit; do not edit those fields.','Use the returned version on update and review immutable change history.'],
    note: 'Limit changes can affect future approvals; settled contract exposure remains owned by the service.',
  },
  haircuts: {
    title: 'Collateral Haircuts', subtitle: 'Define agreement-scoped collateral risk haircuts and effective windows, without calculating required collateral in the browser.', resource: 'haircuts', key: 'haircutId', scope: 'mslaReference', category: 'Collateral controls', fix: 'BB collateral inquiry → BA report',
    fields: [f('collateralType', { required: true, options: collateralTypes }), f('currency', { required: true, options: ['INR','USD','EUR','GBP','CAD','AUD'] }), f('haircutPercentage', { required: true, type: 'number', min: 0, step: 0.0001, hint: 'Enter the percentage value as configured by the MSLA rules.' }), f('effectiveFrom', { required: true, type: 'datetime-local' }), f('effectiveTo', { type: 'datetime-local' })],
    columns: ['haircutId','mslaReference','collateralType','currency','haircutPercentage','effectiveFrom','effectiveTo','version'],
    guidance: ['New MSLA setup creates the required 0% CASH haircut; use this page for later supported schedule maintenance.','The backend chooses applicable rules and validates valuation calculations.','A haircut update requires the returned rule version. Effective dates use UTC instants.'],
    note: 'Do not derive collateral requirements from haircut percentages in the browser.',
  },
  fees: {
    title: 'Lending Fee Schedules', subtitle: 'Configure effective annual lending fee rates per agreement and optionally per security ISIN.', resource: 'lendingFees', key: 'feeId', scope: 'mslaReference', category: 'Commercial terms', fix: 'AE trade terms · accrual/payment settlement',
    fields: [f('securityIsin', { referenceOptions: 'optionalSecurities', hint: 'Optional: leave blank to apply at agreement scope.' }), f('annualFeePct', { required: true, type: 'number', min: 0, step: 0.000001 }), f('effectiveFrom', { required: true, type: 'date' }), f('effectiveTo', { type: 'date' }), f('status', { required: true, options: activeInactive })],
    columns: ['feeId','mslaReference','securityIsin','annualFeePct','effectiveFrom','effectiveTo','status','version'],
    guidance: ['New MSLA setup creates the agreement-wide annual fee; use this page for later schedule maintenance.','An empty security ISIN defines the agreement default; create time-bounded overrides only where commercial terms require them.','Accrual results, settlement charges and payment references are always produced by the backend.'],
    note: 'Fee accrual must use an applicable server-selected rule; the browser does not compute interest or rebate.',
  },
}
