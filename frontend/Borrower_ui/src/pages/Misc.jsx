import { BookOpenText } from 'lucide-react'
import { Panel } from '../components/UI'
import { WorkPage } from '../components/Work'
const terms = [
 ['MSLA','Master Securities Lending Agreement','Contractual terms setting eligible parties, securities, fee structure, currencies, haircut and settlement parameters.'],
 ['MTM','Mark to Market','A valuation update to ensure posted collateral continues to meet the required amount. Custodian owns MTM.'],
 ['Haircut','Collateral haircut','A valuation discount or margin buffer applied to collateral before establishing an acceptable loan exposure.'],
 ['Rebate / fee','Lending fee economics','Payments for lending securities; a rebate may apply to cash-collateral arrangements depending on agreement terms.'],
 ['Recall','Return notice','A Custodian demand that borrowed securities be returned, subject to its quantity and deadline.'],
 ['T+1','Trade date plus one business day','An operational settlement/recall timetable; actual deadlines depend on the transaction and calendar.'],
 ['Shortfall','Collateral deficiency','Required collateral minus recognized, confirmed collateral when positive. Usually requires a top-up.'],
 ['FIX AF','Request for Security (original training PDF)','The original assignment lists AF as a loan availability request. Updated project instead uses a custom U1 loan message.'],
 ['FIX U1','Project-specific loan request','The customized Borrower-to-Custodian request type used by the updated backend.'],
 ['FIX BB','Collateral Inquiry','Borrower asks for collateral eligibility/valuation information. The updated Borrower controller has no exposed REST BB route.'],
 ['FIX BA','Collateral Report','Custodian publishes eligible collateral terms/required amounts. The updated Borrower controller has no BA report REST read route.'],
 ['FIX AE','Trade Capture Report','The original PDF requires AE; not supported by the checked-in custom FIX profile described in the supplied audit.'],
 ['FIX AK','Trade Confirmation','Custodian-issued message signaling acceptance/contract confirmation in the project flow.'],
 ['FIX f','Security Status','The original PDF references f for loan state. Updated project uses a custom U2 initial securities delivery.'],
 ['FIX U2','Project-specific initial delivery','Custom message representing initial securities delivery in the current backend.'],
]
/** GlossaryPage: domain language tied to original PDF and current project divergences. */
export function GlossaryPage(){return <WorkPage title="Securities Lending Glossary" subtitle="A practical dictionary for the Borrower desk, with distinctions between original FIX requirements and implemented custom events." stats={[{label:'Terms',value:terms.length,detail:'Operations definitions'},{label:'Roles',value:'Borrower / Custodian',detail:'Separate authorization'},{label:'Settlement',value:'T+1',detail:'Context-dependent business calendar'},{label:'FIX profile',value:'Custom',detail:'U1 and U2 extensions'}]} guide={['Business state is not the same as FIX transport state.','CASH collateral can be submitted by the Borrower, but confirmed by the Custodian.','A derivative projection is not a legal record or complete audit trail.']} fix="Original: AF → BB → BA → AE → AK → f; updated custom: U1, BB, BA, AK, U2"><Panel title="Reference terminology" icon={BookOpenText}><div className="glossary-list">{terms.map(([key,title,description])=><div key={key}><strong>{key}</strong><h3>{title}</h3><p>{description}</p></div>)}</div></Panel></WorkPage>}
