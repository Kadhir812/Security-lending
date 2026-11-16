/**
 * JSDoc contract declarations matching the Custodian Java DTOs and PageResponse.
 * They can be consumed by a TypeScript-aware editor without converting React JSX to TSX.
 * The server remains authoritative for enum values and validation.
 */
/** @template T @typedef {{content:T[],page:number,size:number,totalElements:number,totalPages:number}} PageResponse */
/** @typedef {'PENDING'|'APPROVED'|'REJECTED'|'CANCELLED'|'EXPIRED'} LoanRequestStatus */
/** @typedef {'ACTIVE'|'INACTIVE'} RuleStatus */
/** @typedef {'CASH'|'ETF'|'GOVT_BOND'|'CORPORATE_BOND'|'EQUITY'} CollateralType */
/** @typedef {'EQUITY'|'CORPORATE_BOND'|'GOVT_BOND'|'ETF'} AssetType */
/** @typedef {'DRAFT'|'ACTIVE'|'INACTIVE'|'EXPIRED'} AgreementStatus */
/** @typedef {{organizationReference:string,organizationName:string,organizationType:'LENDER'|'BORROWER'|'CUSTODIAN',status:'ACTIVE'|'INACTIVE',version?:number}} OrganizationRequest */
/** @typedef {OrganizationRequest & {createdAt?:string,updatedAt?:string}} OrganizationResponse */
/** @typedef {{mandateReference:string,lenderOrganizationReference:string,effectiveFrom:string,effectiveTo?:string,status:'ACTIVE'|'INACTIVE'|'EXPIRED',version?:number}} MandateRequest */
/** @typedef {MandateRequest & {custodianOrganizationReference:string,currentlyEffective:boolean}} MandateResponse */
/** @typedef {{lenderOrganizationReference:string,securityIsin:string,totalQuantity:number}} InventoryCreateRequest */
/** @typedef {{totalQuantity:number,version:number}} InventoryUpdateRequest */
/** @typedef {InventoryCreateRequest & {onLoanQuantity:number,reservedQuantity:number,availableQuantity:number,version:number}} InventoryResponse */
/** @typedef {{mslaReference:string,mandateReference:string,borrowerOrganizationReference:string,effectiveDate:string,expiryDate?:string,agreementStatus:AgreementStatus,baseCurrency:string,eligibleSecurities:{allowed_types:AssetType[]},collateralType:CollateralType,collateralCoveragePct:number,mtmIntervalMinutes:number,marginCallWindowHours:number,recallRule?:string,defaultRules?:Record<string,unknown>,settlementCalendarCode:string,version?:number}} MslaRequest */
/** @typedef {MslaRequest & {currentlyEffective:boolean,lenderOrganizationReference:string,custodianOrganizationReference:string}} MslaResponse */
/** @typedef {{mslaReference:string,limitAmount:number,usedAmount:number,reservedAmount:number,availableAmount:number,currency:string,status:RuleStatus,version:number}} CreditLimitResponse */
/** @typedef {{limitAmount:number,reason:string}} CreditLimitCreateRequest */
/** @typedef {{limitAmount:number,status:RuleStatus,version:number,reason:string}} CreditLimitUpdateRequest */
/** @typedef {{collateralType:CollateralType,currency:string,haircutPercentage:number,effectiveFrom:string,effectiveTo?:string,version?:number}} CollateralHaircutRequest */
/** @typedef {{securityIsin?:string,annualFeePct:number,effectiveFrom:string,effectiveTo?:string,status:RuleStatus,version?:number}} LendingFeeRequest */
/** @typedef {{requestReference:string,mslaReference:string,borrowerOrganizationReference:string,lenderOrganizationReference:string,securityIsin:string,quantity:number,requestedSettlementDate:string,settlementCurrency:string,requestedAt:string,status:LoanRequestStatus}} CustodianLoanRequestResponse */
/** @typedef {{requestReference:string,status:LoanRequestStatus,reason?:string,contractReference?:string,loanMarketValue?:number,requiredCashCollateral?:number,respondedAt?:string}} LoanRequestDecisionResponse */
/** @typedef {{instructionReference:string,currency:string,amount:number,fromAccount:string,toAccount:string,collateralType:string}} CashCollateralInstructionRequest */
/** @typedef {{instructionReference:string,confirmationReference:string}} SimulatedSettlementConfirmationRequest */
/** @typedef {{instructionReference:string,failureReason:string}} SimulatedSettlementFailureRequest */
/** @typedef {{recallReference:string,quantity:number,dueAt:string,reason:string}} CreateRecallRequest */
/** @typedef {{closureReference:string,reason:string}} CloseContractRequest */
/** @typedef {{externalConfirmationReference:string}} SimulatedFeePaymentConfirmationRequest */
/** @typedef {{reason:string}} SimulatedFeePaymentFailureRequest */
export {}
