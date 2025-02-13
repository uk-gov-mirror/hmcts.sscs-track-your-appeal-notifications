package uk.gov.hmcts.reform.sscs.factory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static uk.gov.hmcts.reform.sscs.ccd.domain.Benefit.PIP;
import static uk.gov.hmcts.reform.sscs.config.AppealHearingType.ORAL;
import static uk.gov.hmcts.reform.sscs.config.SubscriptionType.APPELLANT;
import static uk.gov.hmcts.reform.sscs.domain.notify.NotificationEventType.*;
import static uk.gov.hmcts.reform.sscs.service.NotificationUtils.getSubscription;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.annotation.Resource;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.converters.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.config.NotificationConfig;
import uk.gov.hmcts.reform.sscs.config.SubscriptionType;
import uk.gov.hmcts.reform.sscs.config.properties.EvidenceProperties;
import uk.gov.hmcts.reform.sscs.domain.SscsCaseDataWrapper;
import uk.gov.hmcts.reform.sscs.domain.SubscriptionWithType;
import uk.gov.hmcts.reform.sscs.domain.notify.Destination;
import uk.gov.hmcts.reform.sscs.domain.notify.Link;
import uk.gov.hmcts.reform.sscs.domain.notify.Notification;
import uk.gov.hmcts.reform.sscs.domain.notify.NotificationEventType;
import uk.gov.hmcts.reform.sscs.domain.notify.Template;
import uk.gov.hmcts.reform.sscs.extractor.HearingContactDateExtractor;
import uk.gov.hmcts.reform.sscs.personalisation.*;
import uk.gov.hmcts.reform.sscs.service.MessageAuthenticationServiceImpl;
import uk.gov.hmcts.reform.sscs.service.RegionalProcessingCenterService;
import uk.gov.hmcts.reform.sscs.utility.PhoneNumbersUtil;

@RunWith(JUnitParamsRunner.class)
public class NotificationFactoryTest {

    private static final String CASE_ID = "54321";
    private static final String DATE = "2018-01-01T14:01:18.243";

    private NotificationFactory factory;

    private SscsCaseDataWrapper wrapper;

    private SscsCaseData ccdResponse;

    @Mock
    private PersonalisationFactory personalisationFactory;

    @Mock
    private EvidenceProperties evidenceProperties;

    @Mock
    private EvidenceProperties.EvidenceAddress evidencePropertiesAddress;

    @Mock
    private RegionalProcessingCenterService regionalProcessingCenterService;

    @Mock
    private HearingContactDateExtractor hearingContactDateExtractor;

    @Mock
    private NotificationConfig config;

    @Mock
    private NotificationDateConverterUtil notificationDateConverterUtil;

    @InjectMocks
    @Resource
    private SubscriptionPersonalisation subscriptionPersonalisation;

    @Mock
    private MessageAuthenticationServiceImpl macService;

    private Subscription subscription;

    @Mock
    private WithRepresentativePersonalisation withRepresentativePersonalisation;

    @Before
    public void setup() {
        initMocks(this);
        factory = new NotificationFactory(personalisationFactory);

        subscription = Subscription.builder()
                .tya("ABC").email("test@testing.com")
                .mobile("07985858594").subscribeEmail("Yes").subscribeSms("No").build();

        ccdResponse = SscsCaseData.builder().ccdCaseId(CASE_ID).caseReference("SC/1234/5").appeal(Appeal.builder()
                .appellant(Appellant.builder().name(Name.builder().firstName("Ronnie").lastName("Scott").title("Mr").build()).build())
                .benefitType(BenefitType.builder().code("PIP").build()).build())
                .subscriptions(Subscriptions.builder().appellantSubscription(subscription).build()).build();

        wrapper = SscsCaseDataWrapper.builder().newSscsCaseData(ccdResponse).notificationEventType(APPEAL_RECEIVED_NOTIFICATION).build();

        when(config.getManageEmailsLink()).thenReturn(Link.builder().linkUrl("http://manageemails.com/mac").build());
        when(config.getTrackAppealLink()).thenReturn(Link.builder().linkUrl("http://tyalink.com/appeal_id").build());
        when(config.getEvidenceSubmissionInfoLink()).thenReturn(Link.builder().linkUrl("http://link.com/appeal_id").build());
        when(config.getManageEmailsLink()).thenReturn(Link.builder().linkUrl("http://link.com/manage-email-notifications/mac").build());
        when(config.getClaimingExpensesLink()).thenReturn(Link.builder().linkUrl("http://link.com/progress/appeal_id/expenses").build());
        when(config.getHearingInfoLink()).thenReturn(Link.builder().linkUrl("http://link.com/progress/appeal_id/abouthearing").build());
        when(config.getOnlineHearingLinkWithEmail()).thenReturn(Link.builder().linkUrl("http://link.com/onlineHearing?email={email}").build());
        when(macService.generateToken("ABC", PIP.name())).thenReturn("ZYX");

        RegionalProcessingCenter rpc = RegionalProcessingCenter.builder()
                .name("Venue").address1("HMCTS").address2("The Road").address3("Town").address4("City").city("Birmingham").postcode("B23 1EH").build();
        when(regionalProcessingCenterService.getByScReferenceCode("SC/1234/5")).thenReturn(rpc);
        when(hearingContactDateExtractor.extract(any())).thenReturn(Optional.empty());
        when(notificationDateConverterUtil.toEmailDate(any(LocalDate.class))).thenReturn("1 January 2018");

        when(evidenceProperties.getAddress()).thenReturn(evidencePropertiesAddress);
    }

    @Test
    @Parameters({"APPELLANT, appellantEmail", "REPRESENTATIVE, repsEmail"})
    public void givenAppealLapsedEventAndSubscriptionType_shouldInferRightSubscriptionToCreateNotification(
            SubscriptionType subscriptionType, String expectedEmail) {
        SscsCaseDataWrapper sscsCaseDataWrapper = SscsCaseDataWrapper.builder()
                .newSscsCaseData(SscsCaseData.builder()
                        .appeal(Appeal.builder()
                                .benefitType(BenefitType.builder()
                                        .code("PIP")
                                        .build())
                                .build())
                        .subscriptions(Subscriptions.builder()
                                .appellantSubscription(Subscription.builder()
                                        .email("appellantEmail")
                                        .build())
                                .representativeSubscription(Subscription.builder()
                                        .email("repsEmail")
                                        .build())
                                .build())
                        .build())
                .notificationEventType(APPEAL_LAPSED_NOTIFICATION)
                .build();
        CcdNotificationWrapper notificationWrapper = new CcdNotificationWrapper(sscsCaseDataWrapper);

        given(personalisationFactory.apply(any(NotificationEventType.class)))
                .willReturn(withRepresentativePersonalisation);

        Notification notification = factory.create(notificationWrapper, getSubscriptionWithType(sscsCaseDataWrapper, subscriptionType));
        assertEquals(expectedEmail, notification.getEmail());

        then(withRepresentativePersonalisation).should()
                .getTemplate(eq(notificationWrapper), eq(PIP), eq(subscriptionType));

    }

    @Test
    @Parameters({"APPELLANT, appellantEmail", "REPRESENTATIVE, repsEmail"})
    public void givenAppealDwpLapsedEventAndSubscriptionType_shouldInferRightSubscriptionToCreateNotification(
            SubscriptionType subscriptionType, String expectedEmail) {
        SscsCaseDataWrapper sscsCaseDataWrapper = SscsCaseDataWrapper.builder()
                .newSscsCaseData(SscsCaseData.builder()
                        .appeal(Appeal.builder()
                                .benefitType(BenefitType.builder()
                                        .code("PIP")
                                        .build())
                                .build())
                        .subscriptions(Subscriptions.builder()
                                .appellantSubscription(Subscription.builder()
                                        .email("appellantEmail")
                                        .build())
                                .representativeSubscription(Subscription.builder()
                                        .email("repsEmail")
                                        .build())
                                .build())
                        .build())
                .notificationEventType(DWP_APPEAL_LAPSED_NOTIFICATION)
                .build();
        CcdNotificationWrapper notificationWrapper = new CcdNotificationWrapper(sscsCaseDataWrapper);

        given(personalisationFactory.apply(any(NotificationEventType.class)))
                .willReturn(withRepresentativePersonalisation);

        Notification notification = factory.create(notificationWrapper, getSubscriptionWithType(sscsCaseDataWrapper, subscriptionType));
        assertEquals(expectedEmail, notification.getEmail());

        then(withRepresentativePersonalisation).should()
                .getTemplate(eq(notificationWrapper), eq(PIP), eq(subscriptionType));

    }

    @Test
    @Parameters({"APPOINTEE, appointeeEmail", "REPRESENTATIVE, repsEmail"})
    public void givenAppealCreatedEventAndSubscriptionType_shouldInferRightSubscriptionToCreateNotification(
        SubscriptionType subscriptionType, String expectedEmail) {
        SscsCaseDataWrapper wrapper = SscsCaseDataWrapper.builder()
                .newSscsCaseData(SscsCaseData.builder()
                        .appeal(Appeal.builder().appellant(Appellant.builder().appointee(Appointee.builder().build()).build())
                                .benefitType(BenefitType.builder()
                                        .code("PIP")
                                        .build())
                                .build())
                        .subscriptions(Subscriptions.builder()
                                .appellantSubscription(Subscription.builder()
                                        .email("appellantEmail")
                                        .build())
                                .appointeeSubscription(Subscription.builder()
                                        .email("appointeeEmail")
                                        .build())
                                .representativeSubscription(Subscription.builder()
                                        .email("repsEmail")
                                        .build())
                                .build())
                        .build())
                .notificationEventType(SYA_APPEAL_CREATED_NOTIFICATION)
                .build();
        CcdNotificationWrapper notificationWrapper = new CcdNotificationWrapper(wrapper);

        given(personalisationFactory.apply(any(NotificationEventType.class)))
            .willReturn(withRepresentativePersonalisation);

        Notification notification = factory.create(notificationWrapper, getSubscriptionWithType(wrapper, subscriptionType));
        assertEquals(expectedEmail, notification.getEmail());

        then(withRepresentativePersonalisation).should()
            .getTemplate(eq(notificationWrapper), eq(PIP), eq(subscriptionType));

    }

    @Test
    @Parameters({"APPOINTEE, appointeeEmail", "REPRESENTATIVE, repsEmail"})
    public void givenValidAppealCreatedEventAndSubscriptionType_shouldInferRightSubscriptionToCreateNotification(
            SubscriptionType subscriptionType, String expectedEmail) {
        SscsCaseDataWrapper wrapper = SscsCaseDataWrapper.builder()
                .newSscsCaseData(SscsCaseData.builder()
                        .appeal(Appeal.builder().appellant(Appellant.builder().appointee(Appointee.builder().build()).build())
                                .benefitType(BenefitType.builder()
                                        .code("PIP")
                                        .build())
                                .build())
                        .subscriptions(Subscriptions.builder()
                                .appellantSubscription(Subscription.builder()
                                        .email("appellantEmail")
                                        .build())
                                .appointeeSubscription(Subscription.builder()
                                        .email("appointeeEmail")
                                        .build())
                                .representativeSubscription(Subscription.builder()
                                        .email("repsEmail")
                                        .build())
                                .build())
                        .build())
                .notificationEventType(VALID_APPEAL_CREATED)
                .build();
        CcdNotificationWrapper notificationWrapper = new CcdNotificationWrapper(wrapper);

        given(personalisationFactory.apply(any(NotificationEventType.class)))
                .willReturn(withRepresentativePersonalisation);

        Notification notification = factory.create(notificationWrapper, getSubscriptionWithType(wrapper, subscriptionType));
        assertEquals(expectedEmail, notification.getEmail());

        then(withRepresentativePersonalisation).should()
                .getTemplate(eq(notificationWrapper), eq(PIP), eq(subscriptionType));

    }

    @Test
    public void buildSubscriptionCreatedSmsNotificationFromSscsCaseDataWithSubscriptionUpdatedNotificationAndSmsFirstSubscribed() {
        when(personalisationFactory.apply(SUBSCRIPTION_UPDATED_NOTIFICATION)).thenReturn(subscriptionPersonalisation);
        when(config.getTemplate(SUBSCRIPTION_UPDATED_NOTIFICATION.getId(), SUBSCRIPTION_CREATED_NOTIFICATION.getId() + ".appellant", SUBSCRIPTION_UPDATED_NOTIFICATION.getId(), SUBSCRIPTION_UPDATED_NOTIFICATION.getId(), PIP, ORAL, null,LanguagePreference.ENGLISH)).thenReturn(Template.builder().emailTemplateId(null).smsTemplateId(Arrays.asList("123")).build());

        wrapper = SscsCaseDataWrapper.builder()
                .newSscsCaseData(
                        ccdResponse.toBuilder()
                                .subscriptions(Subscriptions.builder().appellantSubscription(subscription.toBuilder().subscribeSms("Yes").wantSmsNotifications("Yes").subscribeEmail("No").build()).build())
                                .build())
                .oldSscsCaseData(
                        ccdResponse.toBuilder()
                                .subscriptions(Subscriptions.builder().appellantSubscription(subscription.toBuilder().subscribeSms("No").wantSmsNotifications("No").subscribeEmail("No").build()).build())
                                .build())
                .notificationEventType(SUBSCRIPTION_UPDATED_NOTIFICATION)
                .build();

        Notification result = factory.create(new CcdNotificationWrapper(wrapper), getSubscriptionWithType(wrapper, APPELLANT));

        assertEquals("123", result.getSmsTemplate().get(0));
    }

    @Test
    public void buildSubscriptionUpdatedSmsNotificationFromSscsCaseDataWithSubscriptionUpdatedNotificationAndSmsAlreadySubscribed() {
        when(personalisationFactory.apply(SUBSCRIPTION_UPDATED_NOTIFICATION)).thenReturn(subscriptionPersonalisation);
        when(config.getTemplate(SUBSCRIPTION_UPDATED_NOTIFICATION.getId(), SUBSCRIPTION_UPDATED_NOTIFICATION.getId(), SUBSCRIPTION_UPDATED_NOTIFICATION.getId(), SUBSCRIPTION_UPDATED_NOTIFICATION.getId(), PIP, ORAL, null, LanguagePreference.ENGLISH)).thenReturn(Template.builder().emailTemplateId("123").smsTemplateId(Arrays.asList("123")).build());

        wrapper = SscsCaseDataWrapper.builder()
                .newSscsCaseData(
                        ccdResponse.toBuilder()
                                .subscriptions(Subscriptions.builder().appellantSubscription(subscription.toBuilder().subscribeSms("Yes").wantSmsNotifications("Yes").subscribeEmail("No").build()).build())
                                .build())
                .oldSscsCaseData(
                        ccdResponse.toBuilder()
                                .subscriptions(Subscriptions.builder().appellantSubscription(subscription.toBuilder().subscribeSms("Yes").wantSmsNotifications("Yes").subscribeEmail("Yes").build()).build())
                                .build())
                .notificationEventType(SUBSCRIPTION_UPDATED_NOTIFICATION)
                .build();

        Notification result = factory.create(new CcdNotificationWrapper(wrapper), getSubscriptionWithType(wrapper, APPELLANT));

        assertEquals("123", result.getSmsTemplate().get(0));
    }

    @Test
    public void buildLastNotificationFromSscsCaseDataEventWhenSmsFirstSubscribed() {
        when(personalisationFactory.apply(SUBSCRIPTION_UPDATED_NOTIFICATION)).thenReturn(subscriptionPersonalisation);
        when(config.getTemplate(SUBSCRIPTION_UPDATED_NOTIFICATION.getId(), SUBSCRIPTION_CREATED_NOTIFICATION.getId() + ".appellant", SUBSCRIPTION_UPDATED_NOTIFICATION.getId(), SUBSCRIPTION_UPDATED_NOTIFICATION.getId(), PIP, ORAL, null, LanguagePreference.ENGLISH)).thenReturn(Template.builder().emailTemplateId("123").smsTemplateId(Arrays.asList("123")).build());

        List<Event> event = new ArrayList<>();
        event.add(Event.builder().value(EventDetails.builder().date(DATE).type(APPEAL_RECEIVED_NOTIFICATION.getId()).build()).build());

        SscsCaseDataWrapper wrapper = SscsCaseDataWrapper.builder()
                .newSscsCaseData(
                        ccdResponse.toBuilder()
                                .subscriptions(Subscriptions.builder().appellantSubscription(subscription.toBuilder().subscribeSms("Yes").wantSmsNotifications("Yes").subscribeEmail("Yes").build()).build())
                                .events(event)
                                .build())
                .oldSscsCaseData(
                        ccdResponse.toBuilder()
                                .subscriptions(Subscriptions.builder().appellantSubscription(subscription.toBuilder().subscribeSms("No").wantSmsNotifications("No").subscribeEmail("Yes").build()).build())
                                .build())
                .notificationEventType(SUBSCRIPTION_UPDATED_NOTIFICATION)
                .build();

        Notification result = factory.create(new CcdNotificationWrapper(wrapper), getSubscriptionWithType(wrapper, APPELLANT));

        assertNull(result.getDestination().email);
        assertNotNull(subscription.getMobile());
        assertEquals(PhoneNumbersUtil.cleanPhoneNumber(subscription.getMobile()).orElse(subscription.getMobile()),
                result.getDestination().sms);
        assertEquals("123", result.getSmsTemplate().get(0));
    }

    @Test
    public void buildNoNotificationFromSscsCaseDataWhenSubscriptionUpdateReceivedWithNoChangeInSubscription() {
        when(personalisationFactory.apply(SUBSCRIPTION_UPDATED_NOTIFICATION)).thenReturn(subscriptionPersonalisation);
        when(config.getTemplate(SUBSCRIPTION_UPDATED_NOTIFICATION.getId(), SUBSCRIPTION_UPDATED_NOTIFICATION.getId(), SUBSCRIPTION_UPDATED_NOTIFICATION.getId(), SUBSCRIPTION_UPDATED_NOTIFICATION.getId(), PIP, ORAL, null, LanguagePreference.ENGLISH)).thenReturn(Template.builder().emailTemplateId("123").smsTemplateId(Arrays.asList("123")).build());

        List<Event> events = new ArrayList<>();
        events.add(Event.builder().value(EventDetails.builder().date(DATE).type(APPEAL_RECEIVED_NOTIFICATION.getId()).build()).build());

        SscsCaseDataWrapper wrapper = SscsCaseDataWrapper.builder()
                .newSscsCaseData(
                        ccdResponse.toBuilder()
                                .subscriptions(Subscriptions.builder().appellantSubscription(subscription.toBuilder().subscribeSms("Yes").wantSmsNotifications("Yes").subscribeEmail("Yes").build()).build())
                                .events(events)
                                .build())
                .oldSscsCaseData(
                        ccdResponse.toBuilder()
                                .subscriptions(Subscriptions.builder().appellantSubscription(subscription.toBuilder().subscribeSms("Yes").wantSmsNotifications("Yes").subscribeEmail("Yes").build()).build())
                                .build())
                .notificationEventType(SUBSCRIPTION_UPDATED_NOTIFICATION)
                .build();

        Notification result = factory.create(new CcdNotificationWrapper(wrapper), getSubscriptionWithType(wrapper, APPELLANT));

        assertEquals("123", result.getEmailTemplate());
        assertEquals("123", result.getSmsTemplate().get(0));
        assertNull(result.getDestination().email);
        assertNull(result.getDestination().sms);
    }

    @Test
    public void buildSubscriptionUpdatedNotificationFromSscsCaseDataWhenEmailIsChanged() {
        when(personalisationFactory.apply(SUBSCRIPTION_UPDATED_NOTIFICATION)).thenReturn(subscriptionPersonalisation);
        when(config.getTemplate(SUBSCRIPTION_UPDATED_NOTIFICATION.getId(), SUBSCRIPTION_UPDATED_NOTIFICATION.getId(), SUBSCRIPTION_UPDATED_NOTIFICATION.getId(), SUBSCRIPTION_UPDATED_NOTIFICATION.getId(), PIP, ORAL, null, LanguagePreference.ENGLISH)).thenReturn(Template.builder().emailTemplateId("123").smsTemplateId(Arrays.asList("123")).build());

        List<Event> events = new ArrayList<>();
        events.add(Event.builder().value(EventDetails.builder().date(DATE).type(APPEAL_RECEIVED_NOTIFICATION.getId()).build()).build());

        wrapper = SscsCaseDataWrapper.builder()
                .newSscsCaseData(
                        ccdResponse.toBuilder()
                                .subscriptions(Subscriptions.builder().appellantSubscription(subscription.toBuilder().email("changed@testing.com").subscribeSms("Yes").wantSmsNotifications("Yes").subscribeEmail("Yes").build()).build())
                                .events(events)
                                .build())
                .oldSscsCaseData(
                        ccdResponse.toBuilder()
                                .subscriptions(Subscriptions.builder().appellantSubscription(subscription.toBuilder().subscribeSms("Yes").wantSmsNotifications("Yes").subscribeEmail("Yes").build()).build())
                                .build())
                .notificationEventType(SUBSCRIPTION_UPDATED_NOTIFICATION)
                .build();

        Notification result = factory.create(new CcdNotificationWrapper(wrapper), getSubscriptionWithType(wrapper, APPELLANT));

        assertEquals("123", result.getEmailTemplate());
        assertEquals("123", result.getSmsTemplate().get(0));
        assertEquals("changed@testing.com", result.getDestination().email);
        assertNull(result.getDestination().sms);
    }

    @Test
    public void returnNullIfPersonalisationNotFound() {
        when(personalisationFactory.apply(APPEAL_RECEIVED_NOTIFICATION)).thenReturn(null);
        Notification result = factory.create(new CcdNotificationWrapper(wrapper), getSubscriptionWithType(wrapper, APPELLANT));

        assertNull(result);
    }

    @Test
    @Parameters({"null", ""})
    public void givenAnEmptyBenefitType_shouldNotThrowExceptionAndGenerateTemplate(@Nullable String benefitType) {

        SscsCaseDataWrapper sscsCaseDataWrapper = SscsCaseDataWrapper.builder()
                .newSscsCaseData(SscsCaseData.builder()
                        .appeal(Appeal.builder()
                                .benefitType(BenefitType.builder()
                                        .code(benefitType)
                                        .build())
                                .build())
                        .build())
                .notificationEventType(APPEAL_RECEIVED_NOTIFICATION)
                .build();

        given(personalisationFactory.apply(any(NotificationEventType.class)))
                .willReturn(withRepresentativePersonalisation);

        CcdNotificationWrapper notificationWrapper = new CcdNotificationWrapper(sscsCaseDataWrapper);

        factory.create(notificationWrapper, new SubscriptionWithType(null, APPELLANT));

        then(withRepresentativePersonalisation).should()
                .getTemplate(eq(notificationWrapper), eq(null), eq(APPELLANT));
    }

    @Test
    public void shouldHandleNoSubscription() {
        SscsCaseDataWrapper sscsCaseDataWrapper = SscsCaseDataWrapper.builder()
                .newSscsCaseData(SscsCaseData.builder()
                        .appeal(Appeal.builder()
                                .benefitType(BenefitType.builder()
                                        .code("PIP")
                                        .build())
                                .build())
                        .build())
                .notificationEventType(APPEAL_RECEIVED_NOTIFICATION)
                .build();
        CcdNotificationWrapper notificationWrapper = new CcdNotificationWrapper(sscsCaseDataWrapper);

        given(personalisationFactory.apply(any(NotificationEventType.class)))
                .willReturn(withRepresentativePersonalisation);

        Notification notification = factory.create(notificationWrapper, new SubscriptionWithType(null, APPELLANT));

        assertEquals(StringUtils.EMPTY, notification.getAppealNumber());
        assertEquals(Destination.builder().build(), notification.getDestination());
    }

    private SubscriptionWithType getSubscriptionWithType(SscsCaseDataWrapper sscsCaseDataWrapper, SubscriptionType subscriptionType) {
        return new SubscriptionWithType(getSubscription(sscsCaseDataWrapper.getNewSscsCaseData(), subscriptionType), subscriptionType);
    }
}
