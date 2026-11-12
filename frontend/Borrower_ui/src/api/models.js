/**
 * JSDoc DTO contract copied from backend/borrower_service Java records.
 * No transformations or synthetic payload fields are added by the API modules.
 * @typedef {{mslaReference:string,securityIsin:string,quantity:number,requestedSettlementDate:string,settlementCurrency:string,locateReference?:string}} LoanRequestSubmission
 * @typedef {{mslaReference:string,securityIsin:string,quantity:number}} LocateSubmission
 * @typedef {{instructionReference:string,amount:number,currency:string,collateralType:'CASH'}} CashCollateralInstructionRequest
 * @typedef {{instructionReference:string,recallReference?:string,quantity:number}} SecuritiesReturnRequest
 * @typedef {{releaseReference:string}} FinalCollateralReleaseRequest
 * @typedef {{calculationResultReference:string,itemKey:string,amount:number}} FeePaymentAllocationRequest
 * @typedef {{amount:number,currency:string,allocations:FeePaymentAllocationRequest[]}} FeePaymentRequest
 * @typedef {{content:object[],page:number,size:number,totalElements:number,totalPages:number}} PageResponse
 */
export const BORROWER_API_CONTRACT = 'Security-lending-fixedapi: borrower service Java controllers and DTO records'
