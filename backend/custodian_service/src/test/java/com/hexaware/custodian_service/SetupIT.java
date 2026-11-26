package com.hexaware.custodian_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hexaware.custodian_service.dto.request.EligibleSecurities;
import com.hexaware.custodian_service.dto.request.MandateRequest;
import com.hexaware.custodian_service.dto.request.MslaRequest;
import com.hexaware.custodian_service.dto.request.OrganizationRequest;
import com.hexaware.custodian_service.dto.response.MandateResponse;
import com.hexaware.custodian_service.dto.response.MslaResponse;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.entity.User;
import com.hexaware.custodian_service.enums.AgreementStatus;
import com.hexaware.custodian_service.enums.AssetType;
import com.hexaware.custodian_service.enums.CollateralType;
import com.hexaware.custodian_service.enums.MandateStatus;
import com.hexaware.custodian_service.enums.OrganizationStatus;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.repository.UserRepository;
import com.hexaware.custodian_service.security.authentication.LocalUserAuthentication;
import com.hexaware.custodian_service.service.lending.LendingPrerequisiteService;
import com.hexaware.custodian_service.service.inventory.LenderInventoryService;
import com.hexaware.custodian_service.service.organization.MandateService;
import com.hexaware.custodian_service.service.agreement.MslaService;
import com.hexaware.custodian_service.service.organization.OrganizationService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/** Real MySQL persistence and production MVC/security components; fixtures roll back. */
@SpringBootTest(classes=CustodianServiceApplication.class)
@Transactional
class SetupIT {
 @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception { PersistenceIT.database(registry); }
 @Autowired OrganizationRepository organizations;
 @Autowired UserRepository users;
 @Autowired OrganizationService registry;
 @Autowired MandateService mandates;
 @Autowired MslaService mslas;
 @Autowired MslaAgreementRepository agreements;
 @Autowired LendingPrerequisiteService prerequisites;
 @Autowired LenderInventoryService inventoryService;
 @Autowired ObjectMapper json;
 @Autowired EntityManager em;
 @Autowired JdbcTemplate jdbc;
 @Autowired Clock clock;
 @Autowired WebApplicationContext context;
 LocalUserAuthentication operatorA;
 LocalUserAuthentication operatorB;
 String lender;
 String borrower;
 MandateResponse mandate;
 LocalDate today;
 MockMvc mvc;

 @BeforeEach void setup() {
  today=LocalDate.now(clock);
  operatorA=operator("A"); operatorB=operator("B");
  lender="L-"+UUID.randomUUID(); borrower="B-"+UUID.randomUUID();
  registry.create(operatorA,organization(lender,OrganizationType.LENDER));
  registry.create(operatorA,organization(borrower,OrganizationType.BORROWER));
  mandate=mandates.create(operatorA,mandateRequest("M-"+UUID.randomUUID()));
  mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
 }
 LocalUserAuthentication operator(String label) {
  var organization=organizations.saveAndFlush(Organization.builder().organizationReference(label+UUID.randomUUID()).organizationName(label).organizationType(OrganizationType.CUSTODIAN).build());
  var user=users.saveAndFlush(User.builder().organization(organization).identityIssuer("https://identity.example.test").identitySubject(UUID.randomUUID().toString()).username("operator").email(UUID.randomUUID()+"@example.test").build());
  var jwt=Jwt.withTokenValue("test-only").header("alg","RS256").issuer(user.getIdentityIssuer()).subject(user.getIdentitySubject()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).claim("organization_reference",organization.getOrganizationReference()).build();
  return new LocalUserAuthentication(jwt,List.of(new SimpleGrantedAuthority("ROLE_CUSTODIAN_OPERATOR")),user.getUserId(),organization.getOrganizationReference());
 }
 OrganizationRequest organization(String reference,OrganizationType type) {
  return OrganizationRequest.builder().organizationReference(reference).organizationName(reference).organizationType(type).status(OrganizationStatus.ACTIVE).build();
 }
 MandateRequest mandateRequest(String reference) {
  return MandateRequest.builder().mandateReference(reference).lenderOrganizationReference(lender).effectiveFrom(today.minusDays(10)).effectiveTo(today.plusYears(2)).status(MandateStatus.ACTIVE).build();
 }
 MslaRequest request() {
  return MslaRequest.builder().mslaReference("A-"+UUID.randomUUID()).mandateReference(mandate.getMandateReference()).borrowerOrganizationReference(borrower).effectiveDate(today.minusDays(1)).expiryDate(today.plusYears(1)).agreementStatus(AgreementStatus.DRAFT).baseCurrency("INR").eligibleSecurities(EligibleSecurities.builder().allowedTypes(List.of(AssetType.EQUITY,AssetType.GOVT_BOND)).build()).collateralType(CollateralType.CASH).collateralCoveragePct(new BigDecimal("105.0000")).mtmIntervalMinutes(60).marginCallWindowHours(24).settlementCalendarCode("IN-NSE").build();
 }
 MslaResponse activate(MslaRequest request) {
  var draft=mslas.create(operatorA,request);request.setVersion(draft.getVersion());request.setAgreementStatus(AgreementStatus.ACTIVE);return mslas.update(operatorA,draft.getMslaReference(),request);
 }
 @Test void jsonObjectsPersistInMysqlAndRoundTripWithoutEscaping() throws Exception {
  var request=request();request.setDefaultRules(Map.of("unapproved",Map.of("description","Await business definition")));
  var created=mslas.create(operatorA,request);em.flush();em.clear();
  String storedType=jdbc.queryForObject("SELECT JSON_TYPE(eligible_securities) FROM msla_agreement WHERE msla_reference=?",String.class,created.getMslaReference());
  assertThat(storedType).isEqualTo("OBJECT");
  assertThat(jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(eligible_securities,'$.allowed_types[0]')) FROM msla_agreement WHERE msla_reference=?",String.class,created.getMslaReference())).isEqualTo("EQUITY");
  assertThat(jdbc.queryForObject("SELECT JSON_TYPE(default_rules) FROM msla_agreement WHERE msla_reference=?",String.class,created.getMslaReference())).isEqualTo("OBJECT");
  var restored=mslas.get(operatorA,created.getMslaReference());assertThat(restored.getDefaultRules()).isEqualTo(request.getDefaultRules());
  var response=mvc.perform(get("/api/mslas/"+created.getMslaReference()).with(authentication(operatorA))).andExpect(status().isOk()).andReturn();
  var body=json.readTree(response.getResponse().getContentAsString());assertThat(body.path("eligibleSecurities").isObject()).isTrue();assertThat(body.path("eligibleSecurities").path("allowed_types").size()).isEqualTo(2);assertThat(body.path("defaultRules").isObject()).isTrue();
 }
 @Test void inventoryListIsLimitedToLendersWithTheOperatorsCurrentMandate() {
  Long lenderId=organizations.findByOrganizationReference(lender).orElseThrow().getOrganizationId();
  jdbc.update("INSERT INTO lender_inventory(organization_id,security_isin,total_quantity) VALUES(?,?,?)",lenderId,"INE002A01018",100L);
  assertThat(inventoryService.list(operatorA,0,20).getContent())
      .extracting(item->item.securityIsin())
      .containsExactly("INE002A01018");
  assertThat(inventoryService.list(operatorB,0,20).getContent()).isEmpty();
 }
 @Test void organizationListIncludesAuthorizedNewSetupPartiesAndHistoricalMslasOnly() {
  String otherCustodianBorrower="OCB-"+UUID.randomUUID();
  String otherCustodianLender="OCL-"+UUID.randomUUID();
  registry.create(operatorB,organization(otherCustodianBorrower,OrganizationType.BORROWER));
  registry.create(operatorB,organization(otherCustodianLender,OrganizationType.LENDER));
  var otherCustodianMandateRequest=mandateRequest("OM-"+UUID.randomUUID());
  otherCustodianMandateRequest.setLenderOrganizationReference(otherCustodianLender);
  mandates.create(operatorB,otherCustodianMandateRequest);
  var unauthorizedMsla=request();
  unauthorizedMsla.setBorrowerOrganizationReference(otherCustodianBorrower);
  assertThatThrownBy(()->mslas.create(operatorA,unauthorizedMsla))
      .isInstanceOf(SetupException.class)
      .hasMessageContaining("not found in your scope");
  assertThat(registry.list(operatorA,0,100).getContent())
      .extracting(item->item.getOrganizationReference())
      .containsExactlyInAnyOrder(lender,borrower)
      .doesNotContain(
          operatorA.getOrganizationReference(),
          operatorB.getOrganizationReference(),
          otherCustodianBorrower,
          otherCustodianLender);
  assertThat(registry.list(operatorB,0,100).getContent())
      .extracting(item->item.getOrganizationReference())
      .containsExactlyInAnyOrder(otherCustodianBorrower,otherCustodianLender)
      .doesNotContain(lender,borrower);

  String unrelatedLender="UL-"+UUID.randomUUID();
  String unrelatedBorrower="UB-"+UUID.randomUUID();
  registry.create(operatorA,organization(unrelatedLender,OrganizationType.LENDER));
  registry.create(operatorA,organization(unrelatedBorrower,OrganizationType.BORROWER));
  assertThat(registry.list(operatorA,0,100).getContent())
      .extracting(item->item.getOrganizationReference())
      .containsExactlyInAnyOrder(lender,borrower,unrelatedLender,unrelatedBorrower);

  var expiredRequest=request();
  expiredRequest.setEffectiveDate(today.minusDays(30));
  expiredRequest.setExpiryDate(today.minusDays(1));
  var expired=mslas.create(operatorA,expiredRequest);
  expiredRequest.setVersion(expired.getVersion());
  expiredRequest.setAgreementStatus(AgreementStatus.EXPIRED);
  mslas.update(operatorA,expired.getMslaReference(),expiredRequest);

  String inactiveLender="IL-"+UUID.randomUUID();
  String inactiveBorrower="IB-"+UUID.randomUUID();
  registry.create(operatorA,organization(inactiveLender,OrganizationType.LENDER));
  registry.create(operatorA,organization(inactiveBorrower,OrganizationType.BORROWER));
  var inactiveMandateRequest=mandateRequest("IM-"+UUID.randomUUID());
  inactiveMandateRequest.setLenderOrganizationReference(inactiveLender);
  var inactiveMandate=mandates.create(operatorA,inactiveMandateRequest);
  var inactiveRequest=request();
  inactiveRequest.setMandateReference(inactiveMandate.getMandateReference());
  inactiveRequest.setBorrowerOrganizationReference(inactiveBorrower);
  var inactive=mslas.create(operatorA,inactiveRequest);
  inactiveRequest.setVersion(inactive.getVersion());
  inactiveRequest.setAgreementStatus(AgreementStatus.INACTIVE);
  mslas.update(operatorA,inactive.getMslaReference(),inactiveRequest);

  assertThat(registry.list(operatorA,0,100).getContent())
      .extracting(item -> item.getOrganizationReference())
      .containsExactlyInAnyOrder(
          lender, borrower, unrelatedLender, unrelatedBorrower, inactiveLender, inactiveBorrower)
      .doesNotContain(
          otherCustodianBorrower,
          otherCustodianLender,
          operatorA.getOrganizationReference(),
          operatorB.getOrganizationReference());
  assertThat(registry.list(operatorB,0,100).getContent()).isEmpty();
 }
 @Test void undefinedDefaultRulesCannotBeSilentlyActivated() {
  var request=request();request.setDefaultRules(Map.of("threshold",100));var draft=mslas.create(operatorA,request);request.setVersion(draft.getVersion());request.setAgreementStatus(AgreementStatus.ACTIVE);
  assertThatThrownBy(()->mslas.update(operatorA,draft.getMslaReference(),request)).isInstanceOf(SetupException.class).hasMessageContaining("business semantics");
 }
 @Test void invalidEligibilityAndClientSuppliedOwnershipAreRejectedByRest() throws Exception {
  var request=request();request.setEligibleSecurities(EligibleSecurities.builder().allowedTypes(List.of(AssetType.EQUITY,AssetType.EQUITY)).build());
  mvc.perform(post("/api/mslas").with(authentication(operatorA)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andExpect(status().isBadRequest());
  request=request();var text=json.writeValueAsString(request);
  mvc.perform(post("/api/mslas").with(authentication(operatorA)).contentType(MediaType.APPLICATION_JSON).content(text.replace("EQUITY","INE144J01027"))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/mslas").with(authentication(operatorA)).contentType(MediaType.APPLICATION_JSON).content(text.substring(0,text.length()-1)+",\"custodianOrgId\":999}" )).andExpect(status().isBadRequest());
 }
 @Test void cashOnlyAndEmptyEligibilityAreValidated() throws Exception {
  var request=request();request.setCollateralType(CollateralType.ETF);
  mvc.perform(post("/api/mslas").with(authentication(operatorA)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andExpect(status().isBadRequest());
  request.setCollateralType(CollateralType.CASH);request.setEligibleSecurities(EligibleSecurities.builder().allowedTypes(List.of()).build());
  mvc.perform(post("/api/mslas").with(authentication(operatorA)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andExpect(status().isBadRequest());
 }
 @Test void custodianCannotReadOrUpdateAnotherCustodiansAgreement() throws Exception {
  var request=request();var created=mslas.create(operatorA,request);
  mvc.perform(get("/api/mslas/"+created.getMslaReference()).with(authentication(operatorB))).andExpect(status().isNotFound());
  assertThat(mslas.list(operatorB,0,100).getContent()).noneMatch(a->a.getMslaReference().equals(created.getMslaReference()));
  request.setVersion(created.getVersion());assertThatThrownBy(()->mslas.update(operatorB,created.getMslaReference(),request)).isInstanceOf(SetupException.class).hasMessageContaining("your scope");
 }
 @Test void custodianCannotCreateAgreementUsingAnotherCustodiansMandate() {
  var request=request();assertThatThrownBy(()->mslas.create(operatorB,request)).isInstanceOf(SetupException.class).hasMessageContaining("Mandate not found");
 }
 @Test void sharedIdentitiesAreReusedAndCannotBeRenamedByAnOperator() {
  var reused=registry.create(operatorB,organization(lender,OrganizationType.LENDER));assertThat(reused.getOrganizationReference()).isEqualTo(lender);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM organization WHERE organization_reference=?",Long.class,lender)).isEqualTo(1);
  mandates.create(operatorB,mandateRequest("M-"+UUID.randomUUID()));
  var update=organization(lender,OrganizationType.LENDER);update.setVersion(reused.getVersion());update.setOrganizationName("unsafe rename");
  assertThatThrownBy(()->registry.update(operatorA,lender,update)).isInstanceOf(SetupException.class).hasMessageContaining("Shared organization");
 }
 @Test void custodianProvisioningAndRolesCannotBeEscalatedThroughSetup() throws Exception {
  mvc.perform(post("/api/organizations").with(authentication(operatorA)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(organization("NEW-C",OrganizationType.CUSTODIAN)))).andExpect(status().isForbidden());
  var own=organizations.findByOrganizationReference(operatorA.getOrganizationReference()).orElseThrow();
  var update=organization(own.getOrganizationReference(),OrganizationType.CUSTODIAN);update.setVersion(own.getVersion());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/organizations/"+own.getOrganizationReference()).with(authentication(operatorA)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update))).andExpect(status().isForbidden());
  var noRole=new LocalUserAuthentication(operatorA.getToken(),List.of(),operatorA.getUserId(),operatorA.getOrganizationReference());
  mvc.perform(get("/api/mslas").with(authentication(noRole))).andExpect(status().isForbidden());
 }
 @Test void duplicateMandateAndAgreementPeriodsAreRejected() {
  assertThatThrownBy(()->mandates.create(operatorA,mandateRequest("DIFFERENT-REFERENCE"))).isInstanceOf(SetupException.class).hasMessageContaining("already exists");
  var overlap=mandateRequest("OVERLAPPING-REFERENCE");overlap.setEffectiveFrom(today.minusDays(1));
  assertThatThrownBy(()->mandates.create(operatorA,overlap)).isInstanceOf(SetupException.class).hasMessageContaining("already exists");
 }
 @Test void duplicateAgreementWithDifferentReferenceIsRejected() {
  mslas.create(operatorA,request());assertThatThrownBy(()->mslas.create(operatorA,request())).isInstanceOf(SetupException.class).hasMessageContaining("already exists");
 }
 @Test void activePrerequisitesFailImmediatelyAfterMandateDeactivation() {
  var request=request();var active=activate(request);
  assertThat(prerequisites.requireActive(operatorA,active.getMslaReference(),today).getAgreementStatus()).isEqualTo(AgreementStatus.ACTIVE);
  var update=mandateRequest(mandate.getMandateReference());update.setVersion(mandate.getVersion());update.setStatus(MandateStatus.INACTIVE);mandates.update(operatorA,mandate.getMandateReference(),update);
  assertThatThrownBy(()->prerequisites.requireActive(operatorA,active.getMslaReference(),today)).isInstanceOf(SetupException.class).hasMessageContaining("active effective");
 }
 @Test void cannotActivateWithoutActiveMandate() {
  var request=request();var draft=mslas.create(operatorA,request);var update=mandateRequest(mandate.getMandateReference());update.setVersion(mandate.getVersion());update.setStatus(MandateStatus.INACTIVE);mandates.update(operatorA,mandate.getMandateReference(),update);
  request.setVersion(draft.getVersion());request.setAgreementStatus(AgreementStatus.ACTIVE);
  assertThatThrownBy(()->mslas.update(operatorA,draft.getMslaReference(),request)).isInstanceOf(SetupException.class).hasMessageContaining("currently active mandate");
 }
 @Test void activeTermsAndOutdatedVersionsCannotBeChanged() {
  var request=request();var active=activate(request);request.setVersion(active.getVersion());request.setCollateralCoveragePct(new BigDecimal("110"));
  assertThatThrownBy(()->mslas.update(operatorA,active.getMslaReference(),request)).isInstanceOf(SetupException.class).hasMessageContaining("Fixed terms");
 }
 @Test void expiredAgreementsCannotBeReactivated() {
  var request=request();var draft=mslas.create(operatorA,request);request.setVersion(draft.getVersion());request.setAgreementStatus(AgreementStatus.EXPIRED);request.setExpiryDate(today.minusDays(1));
  var expired=mslas.update(operatorA,draft.getMslaReference(),request);request.setVersion(expired.getVersion());request.setAgreementStatus(AgreementStatus.ACTIVE);
  assertThatThrownBy(()->mslas.update(operatorA,draft.getMslaReference(),request)).isInstanceOf(SetupException.class).hasMessageContaining("transition");
 }
 @Test void outdatedVersionIsRejected() {
  var request=request();var created=mslas.create(operatorA,request);request.setVersion(created.getVersion()+1);
  assertThatThrownBy(()->mslas.update(operatorA,created.getMslaReference(),request)).isInstanceOf(SetupException.class).hasMessageContaining("Version");
 }
 @Test void usedDraftAgreementTermsAreProtected() {
  var request=request();var draft=mslas.create(operatorA,request);Long id=agreements.findScoped(draft.getMslaReference(),organizations.findByOrganizationReference(operatorA.getOrganizationReference()).orElseThrow().getOrganizationId()).orElseThrow().getMslaId();
  jdbc.update("INSERT INTO locate_request(version,submitted_by_issuer,locate_reference,msla_id,security_isin,quantity,submitted_by_subject,request_time,expiry_time,status) VALUES(0,?,?,?,'INE144J01027',1,?,UTC_TIMESTAMP(6),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 HOUR),'PENDING')","https://identity.example.test",UUID.randomUUID().toString(),id,"fixture");
  request.setVersion(draft.getVersion());request.setMtmIntervalMinutes(120);
  assertThatThrownBy(()->mslas.update(operatorA,draft.getMslaReference(),request)).isInstanceOf(SetupException.class).hasMessageContaining("already referenced");
 }
 @Test void nonZeroEffectiveCashHaircutBlocksActivationAndLending() {
  var request=request();var draft=mslas.create(operatorA,request);
  Long id=agreements.findScoped(draft.getMslaReference(),organizations.findByOrganizationReference(operatorA.getOrganizationReference()).orElseThrow().getOrganizationId()).orElseThrow().getMslaId();
  jdbc.update("INSERT INTO collateral_haircut(msla_id,collateral_type,currency,haircut_percentage,effective_from) VALUES(?,'CASH','INR',1.0000,UTC_TIMESTAMP(6))",id);
  request.setVersion(draft.getVersion());request.setAgreementStatus(AgreementStatus.ACTIVE);
  assertThatThrownBy(()->mslas.update(operatorA,draft.getMslaReference(),request)).isInstanceOf(SetupException.class).hasMessageContaining("haircuts must be zero");
 }
 @Test void illegalLifecycleTransitionAndInvalidDatesAreRejected() throws Exception {
  var request=request();request.setExpiryDate(today.minusYears(1));
  mvc.perform(post("/api/mslas").with(authentication(operatorA)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andExpect(status().isBadRequest());
 }
}
