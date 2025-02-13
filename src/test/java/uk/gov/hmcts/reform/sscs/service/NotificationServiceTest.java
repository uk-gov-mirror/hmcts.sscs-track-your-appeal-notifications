package uk.gov.hmcts.reform.sscs.service;

import static java.util.Collections.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;
import static uk.gov.hmcts.reform.sscs.ccd.domain.State.READY_TO_LIST;
import static uk.gov.hmcts.reform.sscs.config.SubscriptionType.*;
import static uk.gov.hmcts.reform.sscs.domain.notify.NotificationEventType.*;
import static uk.gov.hmcts.reform.sscs.service.NotificationUtils.getSubscription;
import static uk.gov.hmcts.reform.sscs.service.NotificationValidService.BUNDLED_LETTER_EVENT_TYPES;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.converters.Nullable;
import org.apache.pdfbox.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.config.AppealHearingType;
import uk.gov.hmcts.reform.sscs.config.NotificationConfig;
import uk.gov.hmcts.reform.sscs.config.SubscriptionType;
import uk.gov.hmcts.reform.sscs.domain.SscsCaseDataWrapper;
import uk.gov.hmcts.reform.sscs.domain.SubscriptionWithType;
import uk.gov.hmcts.reform.sscs.domain.notify.*;
import uk.gov.hmcts.reform.sscs.factory.CcdNotificationWrapper;
import uk.gov.hmcts.reform.sscs.factory.NotificationFactory;
import uk.gov.hmcts.reform.sscs.factory.NotificationWrapper;
import uk.gov.hmcts.reform.sscs.idam.IdamService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;
import uk.gov.hmcts.reform.sscs.service.docmosis.PdfLetterService;
import uk.gov.service.notify.NotificationClientException;

@RunWith(JUnitParamsRunner.class)
public class NotificationServiceTest {

    static Appellant APPELLANT_WITH_ADDRESS = Appellant.builder()
        .name(Name.builder().firstName("Ap").lastName("pellant").build())
        .address(Address.builder().line1("Appellant Line 1").town("Appellant Town").county("Appellant County").postcode("AP9 3LL").build())
        .build();

    private static final String APPEAL_NUMBER = "GLSCRR";
    private static final String YES = "Yes";
    private static final String CASE_REFERENCE = "ABC123";
    private static final String CASE_ID = "1000001";
    private static final String EMAIL_TEMPLATE_ID = "email-template-id";
    private static final String SMS_TEMPLATE_ID = "sms-template-id";
    private static final String WELSH_SMS_TEMPLATE_ID = "welsh-template-id";
    private static final String LETTER_TEMPLATE_ID = "letter-template-id";
    private static final String SAME_TEST_EMAIL_COM = "sametest@email.com";
    private static final String NEW_TEST_EMAIL_COM = "newtest@email.com";
    private static final String NO = "No";
    private static final String PIP = "PIP";
    private static final String EMAIL = "Email";
    private static final String SMS = "SMS";
    private static final String SMS_MOBILE = "07123456789";
    private static final String LETTER = "Letter";
    private static final String MOBILE_NUMBER_1 = "07983495065";
    private static final String MOBILE_NUMBER_2 = "07983495067";

    private NotificationService notificationService;

    @Mock
    private NotificationSender notificationSender;

    @Mock
    private NotificationFactory factory;

    @Mock
    private ReminderService reminderService;

    @Mock
    private NotificationValidService notificationValidService;

    @Mock
    private NotificationHandler notificationHandler;

    @Mock
    private OutOfHoursCalculator outOfHoursCalculator;

    @Mock
    private NotificationConfig notificationConfig;

    @Mock
    private EvidenceManagementService evidenceManagementService;

    @Mock
    private IdamService idamService;

    @Mock
    private PdfLetterService pdfLetterService;

    private SscsCaseData sscsCaseData;
    private CcdNotificationWrapper ccdNotificationWrapper;
    private SscsCaseDataWrapper sscsCaseDataWrapper;
    private final Subscription subscription = Subscription.builder()
        .tya(APPEAL_NUMBER)
        .email(EMAIL)
        .mobile(MOBILE_NUMBER_1)
        .subscribeEmail(YES)
        .subscribeSms(YES).wantSmsNotifications(YES)
        .build();

    @Mock
    private Appender<ILoggingEvent> mockAppender;
    @Captor
    private ArgumentCaptor captorLoggingEvent;

    @Captor
    private ArgumentCaptor<CcdNotificationWrapper> ccdNotificationWrapperCaptor;

    @Before
    public void setup() {
        openMocks(this);

        notificationService = getNotificationService();

        sscsCaseData = SscsCaseData.builder()
            .appeal(
                Appeal.builder()
                    .hearingType(AppealHearingType.ORAL.name())
                    .hearingOptions(HearingOptions.builder().wantsToAttend(YES).build())
                    .appellant(APPELLANT_WITH_ADDRESS)
                    .build()
            )
            .subscriptions(Subscriptions.builder().appellantSubscription(subscription).build())
            .caseReference(CASE_REFERENCE)
            .createdInGapsFrom(READY_TO_LIST.getId())
            .build();
        sscsCaseDataWrapper = SscsCaseDataWrapper.builder().newSscsCaseData(sscsCaseData).oldSscsCaseData(sscsCaseData).notificationEventType(APPEAL_WITHDRAWN_NOTIFICATION).build();
        ccdNotificationWrapper = new CcdNotificationWrapper(sscsCaseDataWrapper);
        when(outOfHoursCalculator.isItOutOfHours()).thenReturn(false);

        String authHeader = "authHeader";
        String serviceAuthHeader = "serviceAuthHeader";
        IdamTokens idamTokens = IdamTokens.builder().idamOauth2Token(authHeader).serviceAuthorization(serviceAuthHeader).build();

        when(idamService.getIdamTokens()).thenReturn(idamTokens);

        Logger logger = (Logger) LoggerFactory.getLogger(NotificationService.class.getName());
        logger.addAppender(mockAppender);
    }

    @Test
    @Parameters(method = "generateNotificationTypeAndSubscriptionsScenarios")
    public void givenNotificationEventTypeAndDifferentSubscriptionCombinations_shouldManageNotificationAndSubscriptionAccordingly(
        NotificationEventType notificationEventType,
        int wantedNumberOfEmailNotificationsSent,
        int wantedNumberOfSmsNotificationsSent,
        int wantedNumberOfLetterNotificationsSent,
        int wantedNumberOfFactoryCreateCalls,
        Subscription appellantSubscription,
        Subscription repsSubscription,
        Subscription appointeeSubscription,
        SubscriptionType[] expectedSubscriptionTypes,
        boolean fallbackLetter) {

        ccdNotificationWrapper = buildNotificationWrapperGivenNotificationTypeAndSubscriptions(
            notificationEventType, appellantSubscription, repsSubscription, appointeeSubscription);

        if (notificationEventType == DRAFT_TO_VALID_APPEAL_CREATED) {
            //override
            notificationEventType = VALID_APPEAL_CREATED;
        }

        given(notificationValidService.isHearingTypeValidToSendNotification(
            any(SscsCaseData.class), eq(notificationEventType))).willReturn(true);

        given(notificationValidService.isNotificationStillValidToSend(anyList(), eq(notificationEventType)))
            .willReturn(true);


        given(notificationValidService.isFallbackLetterRequiredForSubscriptionType(any(), any(), any()))
            .willReturn(fallbackLetter);

        given(factory.create(any(NotificationWrapper.class), any(SubscriptionWithType.class)))
            .willReturn(new Notification(
                Template.builder()
                    .emailTemplateId(EMAIL_TEMPLATE_ID)
                    .smsTemplateId(Arrays.asList(SMS_TEMPLATE_ID, WELSH_SMS_TEMPLATE_ID))
                    .letterTemplateId(LETTER_TEMPLATE_ID)
                    .build(),
                Destination.builder()
                    .email(EMAIL)
                    .sms(SMS_MOBILE)
                    .build(),
                new HashMap<>(),
                new Reference(),
                null));

        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, true);

        ArgumentCaptor<SubscriptionWithType> subscriptionWithTypeCaptor = ArgumentCaptor.forClass(SubscriptionWithType.class);
        then(factory).should(times(wantedNumberOfFactoryCreateCalls))
            .create(any(NotificationWrapper.class), subscriptionWithTypeCaptor.capture());
        SubscriptionType actualSubscriptionType = subscriptionWithTypeCaptor.getAllValues().stream()
            .map(SubscriptionWithType::getSubscriptionType)
            .findFirst().orElse(null);
        if (expectedSubscriptionTypes != null) {
            assertTrue(Arrays.asList(expectedSubscriptionTypes).contains(actualSubscriptionType));
        }

        then(notificationHandler).should(times(wantedNumberOfEmailNotificationsSent)).sendNotification(
            eq(ccdNotificationWrapper), eq(EMAIL_TEMPLATE_ID), eq("Email"),
            any(NotificationHandler.SendNotification.class));
        then(notificationHandler).should(times(wantedNumberOfSmsNotificationsSent)).sendNotification(
            eq(ccdNotificationWrapper), eq(SMS_TEMPLATE_ID), eq("SMS"),
            any(NotificationHandler.SendNotification.class));
        then(notificationHandler).should(times(wantedNumberOfLetterNotificationsSent)).sendNotification(
            eq(ccdNotificationWrapper), eq(LETTER_TEMPLATE_ID), eq("Letter"),
            any(NotificationHandler.SendNotification.class));

        verifyNoErrorsLogged(mockAppender, captorLoggingEvent);
    }

    @Test
    @Parameters(method = "generateNotificationTypeAndSubscriptionsWhenOldCaseReferenceScenarios")
    public void givenNotificationEventTypeAndDifferentSubscriptionCombinationsWhenOldCaseReference_shouldManageNotificationAndSubscriptionAccordingly(
        NotificationEventType notificationEventType, int wantedNumberOfEmailNotificationsSent,
        int wantedNumberOfSmsNotificationsSent, Subscription appellantSubscription, Subscription repsSubscription,
        Subscription appointeeSubscription, SubscriptionType[] expectedSubscriptionTypes) {

        SscsCaseData oldCaseData = SscsCaseData.builder()
            .appeal(Appeal.builder().appellant(Appellant.builder().build())
                .hearingType(AppealHearingType.ORAL.name())
                .hearingOptions(HearingOptions.builder()
                    .wantsToAttend(YES)
                    .build())
                .build())
            .subscriptions(Subscriptions.builder()
                .appellantSubscription(Subscription.builder().build())
                .appointeeSubscription(appointeeSubscription)
                .representativeSubscription(repsSubscription)
                .build())
            .caseReference(CASE_REFERENCE)
            .hearings(singletonList(Hearing.builder().build()))
            .build();

        ccdNotificationWrapper = buildNotificationWrapperGivenNotificationTypeAndSubscriptions(
            notificationEventType, appellantSubscription, repsSubscription, appointeeSubscription, oldCaseData);

        if (notificationEventType == DRAFT_TO_VALID_APPEAL_CREATED) {
            //override
            notificationEventType = VALID_APPEAL_CREATED;
        }

        given(notificationValidService.isHearingTypeValidToSendNotification(
            any(SscsCaseData.class), eq(notificationEventType))).willReturn(true);

        given(notificationValidService.isNotificationStillValidToSend(anyList(), eq(notificationEventType)))
            .willReturn(true);

        if (0 != expectedSubscriptionTypes.length) {
            given(notificationValidService.isFallbackLetterRequiredForSubscriptionType(any(), any(), any())).willReturn(true);
        }

        given(factory.create(any(NotificationWrapper.class), any(SubscriptionWithType.class)))
            .willReturn(new Notification(
                Template.builder()
                    .emailTemplateId(EMAIL_TEMPLATE_ID)
                    .smsTemplateId(Arrays.asList(SMS_TEMPLATE_ID))
                    .build(),
                Destination.builder()
                    .email(EMAIL)
                    .sms(SMS_MOBILE)
                    .build(),
                new HashMap<>(),
                new Reference(),
                null));

        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, true);

        ArgumentCaptor<SubscriptionWithType> subscriptionWithTypeCaptor = ArgumentCaptor.forClass(SubscriptionWithType.class);
        then(factory).should(times(expectedSubscriptionTypes.length))
            .create(any(NotificationWrapper.class), subscriptionWithTypeCaptor.capture());
        assertArrayEquals(expectedSubscriptionTypes, subscriptionWithTypeCaptor.getAllValues().stream().map(SubscriptionWithType::getSubscriptionType).toArray());

        then(notificationHandler).should(times(wantedNumberOfEmailNotificationsSent)).sendNotification(
            eq(ccdNotificationWrapper), eq(EMAIL_TEMPLATE_ID), eq("Email"),
            any(NotificationHandler.SendNotification.class));
        then(notificationHandler).should(times(wantedNumberOfSmsNotificationsSent)).sendNotification(
            eq(ccdNotificationWrapper), eq(SMS_TEMPLATE_ID), eq("SMS"),
            any(NotificationHandler.SendNotification.class));

        verifyExpectedLogErrorCount(mockAppender, captorLoggingEvent, wantedNumberOfEmailNotificationsSent, wantedNumberOfSmsNotificationsSent);
    }

    @Test
    @Parameters(method = "generateNotificationTypeAndSubscriptionsAppointeeScenarios")
    public void givenNotificationEventTypeAndAppointeeSubscriptionCombinations_shouldManageNotificationAndSubscriptionAccordingly(
        NotificationEventType notificationEventType, int wantedNumberOfEmailNotificationsSent,
        int wantedNumberOfSmsNotificationsSent, Subscription appointeeSubscription, Subscription repsSubscription,
        SubscriptionType[] expectedSubscriptionTypes) {

        ccdNotificationWrapper = buildNotificationWrapperGivenNotificationTypeAndAppointeeSubscriptions(
            notificationEventType, appointeeSubscription, repsSubscription);

        if (notificationEventType == DRAFT_TO_VALID_APPEAL_CREATED) {
            //override
            notificationEventType = VALID_APPEAL_CREATED;
        }

        given(notificationValidService.isHearingTypeValidToSendNotification(
            any(SscsCaseData.class), eq(notificationEventType))).willReturn(true);

        given(notificationValidService.isNotificationStillValidToSend(anyList(), eq(notificationEventType)))
            .willReturn(true);

        given(notificationValidService.isFallbackLetterRequiredForSubscriptionType(any(), any(), any())).willReturn(true);

        given(factory.create(any(NotificationWrapper.class), any(SubscriptionWithType.class)))
            .willReturn(new Notification(
                Template.builder()
                    .emailTemplateId(EMAIL_TEMPLATE_ID)
                    .smsTemplateId(Arrays.asList(SMS_TEMPLATE_ID))
                    .build(),
                Destination.builder()
                    .email(EMAIL)
                    .sms(SMS_MOBILE)
                    .build(),
                null,
                new Reference(),
                null));

        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, true);

        ArgumentCaptor<SubscriptionWithType> subscriptionWithTypeCaptor = ArgumentCaptor.forClass(SubscriptionWithType.class);
        then(factory).should(times(expectedSubscriptionTypes.length))
            .create(any(NotificationWrapper.class), subscriptionWithTypeCaptor.capture());
        assertArrayEquals(expectedSubscriptionTypes, subscriptionWithTypeCaptor.getAllValues().stream().map(SubscriptionWithType::getSubscriptionType).toArray());

        then(notificationHandler).should(times(wantedNumberOfEmailNotificationsSent)).sendNotification(
            eq(ccdNotificationWrapper), eq(EMAIL_TEMPLATE_ID), eq("Email"),
            any(NotificationHandler.SendNotification.class));
        then(notificationHandler).should(times(wantedNumberOfSmsNotificationsSent)).sendNotification(
            eq(ccdNotificationWrapper), eq(SMS_TEMPLATE_ID), eq("SMS"),
            any(NotificationHandler.SendNotification.class));

        verifyNoErrorsLogged(mockAppender, captorLoggingEvent);
    }

    @Test
    @Parameters(method = "generateNotificationTypeAndSubscriptionsAppointeeWhenOldCaseReferenceScenarios")
    public void givenNotificationEventTypeAndAppointeeSubscriptionCombinationsWhenOldCaseReference_shouldManageNotificationAndSubscriptionAccordingly(
        NotificationEventType notificationEventType, int wantedNumberOfEmailNotificationsSent,
        int wantedNumberOfSmsNotificationsSent, Subscription appointeeSubscription, Subscription repsSubscription,
        SubscriptionType[] expectedSubscriptionTypes) {

        SscsCaseData oldCaseData = SscsCaseData.builder()
            .appeal(Appeal.builder().appellant(Appellant.builder().build())
                .hearingType(AppealHearingType.ORAL.name())
                .hearingOptions(HearingOptions.builder()
                    .wantsToAttend(YES)
                    .build())
                .build())
            .subscriptions(Subscriptions.builder()
                .appellantSubscription(Subscription.builder().build())
                .appointeeSubscription(appointeeSubscription)
                .representativeSubscription(repsSubscription)
                .build())
            .caseReference(CASE_REFERENCE)
            .hearings(singletonList(Hearing.builder().build()))
            .build();

        ccdNotificationWrapper = buildNotificationWrapperGivenNotificationTypeAndAppointeeSubscriptions(
            notificationEventType, appointeeSubscription, repsSubscription, oldCaseData);

        if (notificationEventType == DRAFT_TO_VALID_APPEAL_CREATED) {
            //override
            notificationEventType = VALID_APPEAL_CREATED;
        }

        given(notificationValidService.isHearingTypeValidToSendNotification(
            any(SscsCaseData.class), eq(notificationEventType))).willReturn(true);

        given(notificationValidService.isNotificationStillValidToSend(anyList(), eq(notificationEventType)))
            .willReturn(true);

        given(factory.create(any(NotificationWrapper.class), any(SubscriptionWithType.class)))
            .willReturn(new Notification(
                Template.builder()
                    .emailTemplateId(EMAIL_TEMPLATE_ID)
                    .smsTemplateId(Arrays.asList(SMS_TEMPLATE_ID))
                    .build(),
                Destination.builder()
                    .email(EMAIL)
                    .sms(SMS_MOBILE)
                    .build(),
                null,
                new Reference(),
                null));

        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, true);

        ArgumentCaptor<SubscriptionWithType> subscriptionWithTypeCaptor = ArgumentCaptor.forClass(SubscriptionWithType.class);
        then(factory).should(times(expectedSubscriptionTypes.length))
            .create(any(NotificationWrapper.class), subscriptionWithTypeCaptor.capture());
        assertArrayEquals(expectedSubscriptionTypes, subscriptionWithTypeCaptor.getAllValues().stream().map(SubscriptionWithType::getSubscriptionType).toArray());

        then(notificationHandler).should(times(wantedNumberOfEmailNotificationsSent)).sendNotification(
            eq(ccdNotificationWrapper), eq(EMAIL_TEMPLATE_ID), eq("Email"),
            any(NotificationHandler.SendNotification.class));
        then(notificationHandler).should(times(wantedNumberOfSmsNotificationsSent)).sendNotification(
            eq(ccdNotificationWrapper), eq(SMS_TEMPLATE_ID), eq("SMS"),
            any(NotificationHandler.SendNotification.class));

        verifyExpectedLogErrorCount(mockAppender, captorLoggingEvent, wantedNumberOfEmailNotificationsSent, wantedNumberOfSmsNotificationsSent);
    }

    @SuppressWarnings({"Indentation", "UnusedPrivateMethod"})
    private Object[] generateNotificationTypeAndSubscriptionsScenarios() {
        return new Object[]{
            new Object[]{
                APPEAL_LAPSED_NOTIFICATION,
                1,
                1,
                1,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                null,
                null,
                new SubscriptionType[]{APPELLANT},
                true
            },
            new Object[]{
                ADMIN_APPEAL_WITHDRAWN,
                1,
                1,
                1,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                null,
                null,
                new SubscriptionType[]{APPELLANT},
                false
            },
            new Object[]{
                ADMIN_APPEAL_WITHDRAWN,
                0,
                0,
                1,
                1,
                null,
                null,
                null,
                new SubscriptionType[]{APPELLANT},
                false
            },
            new Object[]{
                ADMIN_APPEAL_WITHDRAWN,
                2,
                1,
                2,
                2,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .build(),
                null,
                new SubscriptionType[]{APPELLANT, REPRESENTATIVE},
                false
            },
            new Object[]{
                APPEAL_LAPSED_NOTIFICATION,
                2,
                1,
                2,
                2,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .build(),
                null,
                new SubscriptionType[]{APPELLANT, REPRESENTATIVE},
                true
            },
            new Object[]{
                APPEAL_LAPSED_NOTIFICATION,
                2,
                2,
                2,
                2,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                null,
                new SubscriptionType[]{APPELLANT, REPRESENTATIVE},
                true
            },
            new Object[]{
                APPEAL_RECEIVED_NOTIFICATION,
                1,
                1,
                1,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                null,
                null,
                new SubscriptionType[]{APPELLANT},
                true
            },
            new Object[]{
                APPEAL_RECEIVED_NOTIFICATION,
                2,
                1,
                2,
                2,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .build(),
                null,
                new SubscriptionType[]{APPELLANT, REPRESENTATIVE},
                true
            },
            new Object[]{
                APPEAL_RECEIVED_NOTIFICATION,
                2,
                2,
                2,
                2,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                null,
                new SubscriptionType[]{APPELLANT, REPRESENTATIVE},
                true
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                0,
                0,
                1,
                1,
                null,
                null,
                null,
                new SubscriptionType[]{APPELLANT},  // Fallback letter
                true
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                1,
                0,
                0,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .build(),
                null,
                null,
                new SubscriptionType[]{APPELLANT},
                true
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                1,
                1,
                0,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .build(),
                null,
                null,
                new SubscriptionType[]{APPELLANT},
                true
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                0,
                0,
                1,
                2,
                Subscription.builder().build(),
                Subscription.builder().build(),
                Subscription.builder().build(),
                new SubscriptionType[]{APPOINTEE, REPRESENTATIVE},  // Fallback letter
                true
            },
            new Object[]{
                VALID_APPEAL_CREATED,
                0,
                0,
                1,
                1,
                null,
                null,
                null,
                new SubscriptionType[]{APPELLANT},  // Fallback letter
                true
            },
            new Object[]{
                VALID_APPEAL_CREATED,
                1,
                0,
                0,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .build(),
                null,
                null,
                new SubscriptionType[]{APPELLANT},
                true
            },
            new Object[]{
                VALID_APPEAL_CREATED,
                1,
                1,
                0,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .build(),
                null,
                null,
                new SubscriptionType[]{APPELLANT},
                true
            },
            new Object[]{
                VALID_APPEAL_CREATED,
                0,
                0,
                1,
                2,
                Subscription.builder().build(),
                Subscription.builder().build(),
                Subscription.builder().build(),
                new SubscriptionType[]{APPOINTEE, REPRESENTATIVE},  // Fallback letter
                true
            },
            new Object[]{
                DRAFT_TO_VALID_APPEAL_CREATED,
                0,
                0,
                1,
                1,
                null,
                null,
                null,
                new SubscriptionType[]{APPELLANT},  // Fallback letter
                true
            },
            new Object[]{
                DRAFT_TO_VALID_APPEAL_CREATED,
                1,
                0,
                0,
                1,
                Subscription.builder()
                        .tya(APPEAL_NUMBER)
                        .email(EMAIL)
                        .subscribeEmail(YES)
                        .build(),
                null,
                null,
                new SubscriptionType[]{APPELLANT},
                true
            },
            new Object[]{
                DRAFT_TO_VALID_APPEAL_CREATED,
                1,
                1,
                0,
                1,
                Subscription.builder()
                        .tya(APPEAL_NUMBER)
                        .email(EMAIL)
                        .subscribeEmail(YES)
                        .subscribeSms(YES).wantSmsNotifications(YES)
                        .mobile(MOBILE_NUMBER_1)
                        .build(),
                null,
                null,
                new SubscriptionType[]{APPELLANT},
                true
            },
            new Object[]{
                DRAFT_TO_VALID_APPEAL_CREATED,
                0,
                0,
                1,
                2,
                Subscription.builder().build(),
                Subscription.builder().build(),
                Subscription.builder().build(),
                new SubscriptionType[]{APPOINTEE, REPRESENTATIVE},  // Fallback letter
                true
            }
        };
    }

    @SuppressWarnings({"Indentation", "UnusedPrivateMethod"})
    private Object[] generateNotificationTypeAndSubscriptionsWhenOldCaseReferenceScenarios() {
        return new Object[]{
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                1,
                0,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .build(),
                null,
                null,
                new SubscriptionType[]{APPELLANT},
            },
            new Object[]{
                APPEAL_LAPSED_NOTIFICATION,
                1,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                null,
                null,
                new SubscriptionType[]{APPELLANT},
            },
            new Object[]{
                APPEAL_LAPSED_NOTIFICATION,
                2,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .build(),
                null,
                new SubscriptionType[]{APPELLANT, REPRESENTATIVE},
            },
            new Object[]{
                APPEAL_LAPSED_NOTIFICATION,
                2,
                2,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                null,
                new SubscriptionType[]{APPELLANT, REPRESENTATIVE},
            },
            new Object[]{
                APPEAL_RECEIVED_NOTIFICATION,
                1,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                null,
                null,
                new SubscriptionType[]{APPELLANT},
            },
            new Object[]{
                APPEAL_RECEIVED_NOTIFICATION,
                2,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .build(),
                null,
                new SubscriptionType[]{APPELLANT, REPRESENTATIVE},
            },
            new Object[]{
                APPEAL_RECEIVED_NOTIFICATION,
                2,
                2,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                null,
                new SubscriptionType[]{APPELLANT, REPRESENTATIVE},
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                0,
                0,
                null,
                null,
                null,
                new SubscriptionType[]{},
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                1,
                0,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .build(),
                null,
                null,
                new SubscriptionType[]{APPELLANT},
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                1,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .build(),
                null,
                null,
                new SubscriptionType[]{APPELLANT},
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                0,
                0,
                Subscription.builder().build(),
                Subscription.builder().build(),
                Subscription.builder().build(),
                new SubscriptionType[]{},
            },
            new Object[]{
                VALID_APPEAL_CREATED,
                0,
                0,
                null,
                null,
                null,
                new SubscriptionType[]{},
            },
            new Object[]{
                VALID_APPEAL_CREATED,
                1,
                0,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .build(),
                null,
                null,
                new SubscriptionType[]{APPELLANT},
            },
            new Object[]{
                VALID_APPEAL_CREATED,
                1,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .build(),
                null,
                null,
                new SubscriptionType[]{APPELLANT},
            },
            new Object[]{
                VALID_APPEAL_CREATED,
                0,
                0,
                Subscription.builder().build(),
                Subscription.builder().build(),
                Subscription.builder().build(),
                new SubscriptionType[]{},
            },
            new Object[]{
                DRAFT_TO_VALID_APPEAL_CREATED,
                0,
                0,
                null,
                null,
                null,
                new SubscriptionType[]{},
            },
            new Object[]{
                DRAFT_TO_VALID_APPEAL_CREATED,
                1,
                0,
                Subscription.builder()
                        .tya(APPEAL_NUMBER)
                        .email(EMAIL)
                        .subscribeEmail(YES)
                        .build(),
                null,
                null,
                new SubscriptionType[]{APPELLANT},
            },
            new Object[]{
                DRAFT_TO_VALID_APPEAL_CREATED,
                1,
                1,
                Subscription.builder()
                        .tya(APPEAL_NUMBER)
                        .email(EMAIL)
                        .subscribeEmail(YES)
                        .subscribeSms(YES).wantSmsNotifications(YES)
                        .mobile(MOBILE_NUMBER_1)
                        .build(),
                null,
                null,
                new SubscriptionType[]{APPELLANT},
            },
            new Object[]{
                DRAFT_TO_VALID_APPEAL_CREATED,
                0,
                0,
                Subscription.builder().build(),
                Subscription.builder().build(),
                Subscription.builder().build(),
                new SubscriptionType[]{},
            },
            new Object[]{
                CASE_UPDATED,
                0,
                0,
                Subscription.builder().build(),
                Subscription.builder().build(),
                Subscription.builder().build(),
                new SubscriptionType[]{},
            }
        };
    }

    @SuppressWarnings("Indentation")
    private Object[] generateNotificationTypeAndSubscriptionsAppointeeScenarios() {
        return new Object[]{
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                1,
                0,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .build(),
                null,
                new SubscriptionType[]{APPOINTEE},
            },
            new Object[]{
                APPEAL_RECEIVED_NOTIFICATION,
                1,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                null,
                new SubscriptionType[]{APPOINTEE},
            },
            new Object[]{
                APPEAL_RECEIVED_NOTIFICATION,
                2,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .build(),
                new SubscriptionType[]{APPOINTEE, REPRESENTATIVE},
            },
            new Object[]{
                APPEAL_RECEIVED_NOTIFICATION,
                2,
                2,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                new SubscriptionType[]{APPOINTEE, REPRESENTATIVE},
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                0,
                0,
                null,
                null,
                new SubscriptionType[]{APPELLANT},
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                1,
                0,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .build(),
                null,
                new SubscriptionType[]{APPOINTEE},
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                1,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .build(),
                null,
                new SubscriptionType[]{APPOINTEE},
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                0,
                0,
                Subscription.builder().build(),
                Subscription.builder().build(),
                new SubscriptionType[]{APPOINTEE, REPRESENTATIVE},
            },
            new Object[]{
                HEARING_REMINDER_NOTIFICATION,
                1,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .build(),
                null,
                new SubscriptionType[]{APPOINTEE},
            },
            new Object[]{
                HEARING_REMINDER_NOTIFICATION,
                1,
                0,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .build(),
                null,
                new SubscriptionType[]{APPOINTEE},
            },
            new Object[]{
                HEARING_REMINDER_NOTIFICATION,
                0,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .build(),
                null,
                new SubscriptionType[]{APPOINTEE},
            },
            new Object[]{
                VALID_APPEAL_CREATED,
                0,
                0,
                null,
                null,
                new SubscriptionType[]{APPELLANT},
            },
            new Object[]{
                VALID_APPEAL_CREATED,
                1,
                0,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .build(),
                null,
                new SubscriptionType[]{APPOINTEE},
            },
            new Object[]{
                VALID_APPEAL_CREATED,
                1,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .build(),
                null,
                new SubscriptionType[]{APPOINTEE},
            },
            new Object[]{
                VALID_APPEAL_CREATED,
                0,
                0,
                Subscription.builder().build(),
                Subscription.builder().build(),
                new SubscriptionType[]{APPOINTEE, REPRESENTATIVE},
            },
            new Object[]{
                DRAFT_TO_VALID_APPEAL_CREATED,
                0,
                0,
                null,
                null,
                new SubscriptionType[]{APPELLANT},
            },
            new Object[]{
                DRAFT_TO_VALID_APPEAL_CREATED,
                1,
                0,
                Subscription.builder()
                        .tya(APPEAL_NUMBER)
                        .email(EMAIL)
                        .subscribeEmail(YES)
                        .build(),
                null,
                new SubscriptionType[]{APPOINTEE},
            },
            new Object[]{
                DRAFT_TO_VALID_APPEAL_CREATED,
                1,
                1,
                Subscription.builder()
                        .tya(APPEAL_NUMBER)
                        .email(EMAIL)
                        .subscribeEmail(YES)
                        .subscribeSms(YES).wantSmsNotifications(YES)
                        .mobile(MOBILE_NUMBER_1)
                        .build(),
                null,
                new SubscriptionType[]{APPOINTEE},
            },
            new Object[]{
                DRAFT_TO_VALID_APPEAL_CREATED,
                0,
                0,
                Subscription.builder().build(),
                Subscription.builder().build(),
                new SubscriptionType[]{APPOINTEE, REPRESENTATIVE},
            }
        };
    }

    @SuppressWarnings("Indentation")
    private Object[] generateNotificationTypeAndSubscriptionsAppointeeWhenOldCaseReferenceScenarios() {
        return new Object[]{
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                1,
                0,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .build(),
                null,
                new SubscriptionType[]{APPOINTEE},
            },
            new Object[]{
                APPEAL_RECEIVED_NOTIFICATION,
                1,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                null,
                new SubscriptionType[]{APPOINTEE},
            },
            new Object[]{
                APPEAL_RECEIVED_NOTIFICATION,
                2,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .build(),
                new SubscriptionType[]{APPOINTEE, REPRESENTATIVE},
            },
            new Object[]{
                APPEAL_RECEIVED_NOTIFICATION,
                2,
                2,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                new SubscriptionType[]{APPOINTEE, REPRESENTATIVE},
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                0,
                0,
                null,
                null,
                new SubscriptionType[]{},
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                1,
                0,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .build(),
                null,
                new SubscriptionType[]{APPOINTEE},
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                1,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .build(),
                null,
                new SubscriptionType[]{APPOINTEE},
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                0,
                0,
                Subscription.builder().build(),
                Subscription.builder().build(),
                new SubscriptionType[]{},
            },
            new Object[]{
                EVIDENCE_REMINDER_NOTIFICATION,
                1,
                0,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .build(),
                null,
                new SubscriptionType[]{APPOINTEE},
            },
            new Object[]{
                EVIDENCE_REMINDER_NOTIFICATION,
                1,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build(),
                null,
                new SubscriptionType[]{APPOINTEE},
            },
            new Object[]{
                VALID_APPEAL_CREATED,
                0,
                0,
                null,
                null,
                new SubscriptionType[]{},
            },
            new Object[]{
                VALID_APPEAL_CREATED,
                1,
                0,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .build(),
                null,
                new SubscriptionType[]{APPOINTEE},
            },
            new Object[]{
                VALID_APPEAL_CREATED,
                1,
                1,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .build(),
                null,
                new SubscriptionType[]{APPOINTEE},
            },
            new Object[]{
                VALID_APPEAL_CREATED,
                0,
                0,
                Subscription.builder().build(),
                Subscription.builder().build(),
                new SubscriptionType[]{},
            },
            new Object[]{
                DRAFT_TO_VALID_APPEAL_CREATED,
                0,
                0,
                null,
                null,
                new SubscriptionType[]{},
            },
            new Object[]{
                DRAFT_TO_VALID_APPEAL_CREATED,
                1,
                0,
                Subscription.builder()
                        .tya(APPEAL_NUMBER)
                        .email(EMAIL)
                        .subscribeEmail(YES)
                        .build(),
                null,
                new SubscriptionType[]{APPOINTEE},
            },
            new Object[]{
                DRAFT_TO_VALID_APPEAL_CREATED,
                1,
                1,
                Subscription.builder()
                        .tya(APPEAL_NUMBER)
                        .email(EMAIL)
                        .subscribeEmail(YES)
                        .subscribeSms(YES).wantSmsNotifications(YES)
                        .mobile(MOBILE_NUMBER_1)
                        .build(),
                null,
                new SubscriptionType[]{APPOINTEE},
            },
            new Object[]{
                DRAFT_TO_VALID_APPEAL_CREATED,
                0,
                0,
                Subscription.builder().build(),
                Subscription.builder().build(),
                new SubscriptionType[]{},
            },
            new Object[]{
                CASE_UPDATED,
                0,
                0,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .build(),
                null,
                new SubscriptionType[]{},
            },
            new Object[]{
                CASE_UPDATED,
                0,
                0,
                Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .subscribeEmail(YES)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .mobile(MOBILE_NUMBER_1)
                    .build(),
                null,
                new SubscriptionType[]{},
            },
            new Object[]{
                CASE_UPDATED,
                0,
                0,
                Subscription.builder().build(),
                Subscription.builder().build(),
                new SubscriptionType[]{},
            }
        };
    }

    private CcdNotificationWrapper buildNotificationWrapperGivenNotificationTypeAndSubscriptions(
        NotificationEventType notificationEventType, Subscription appellantSubscription,
        Subscription repsSubscription, Subscription appointeeSubscription) {
        return buildNotificationWrapperGivenNotificationTypeAndSubscriptions(notificationEventType,
            appellantSubscription, repsSubscription, appointeeSubscription, null);
    }

    private CcdNotificationWrapper buildNotificationWrapperGivenNotificationTypeAndSubscriptions(
        NotificationEventType notificationEventType, Subscription appellantSubscription,
        Subscription repsSubscription, Subscription appointeeSubscription, SscsCaseData oldCaseData) {

        Representative rep = null;
        if (repsSubscription != null) {
            rep = Representative.builder()
                .hasRepresentative("Yes")
                .name(Name.builder().firstName("Joe").lastName("Bloggs").build())
                .address(Address.builder().line1("Rep Line 1").town("Rep Town").county("Rep County").postcode("RE9 7SE").build())
                .build();
        }

        Appellant appellant = Appellant.builder()
            .address(Address.builder().line1("Appellant Line 1").town("Appellant Town").county("Appellant County").postcode("AP9 7LL").build())
            .build();
        if (appointeeSubscription != null) {
            appellant.setAppointee(Appointee.builder()
                .name(Name.builder().firstName("Jack").lastName("Smith").build())
                .build());
        }

        sscsCaseData = SscsCaseData.builder()
            .appeal(Appeal.builder()
                .appellant(appellant)
                .rep(rep)
                .hearingType(AppealHearingType.ORAL.name())
                .hearingOptions(HearingOptions.builder()
                    .wantsToAttend(YES)
                    .build())
                .build())
            .subscriptions(Subscriptions.builder()
                .appellantSubscription(appellantSubscription)
                .representativeSubscription(repsSubscription)
                .appointeeSubscription(appointeeSubscription)
                .build())
            .caseReference(CASE_REFERENCE)
            .hearings(singletonList(Hearing.builder().build()))
            .createdInGapsFrom(READY_TO_LIST.getId())
            .build();

        sscsCaseDataWrapper = SscsCaseDataWrapper.builder()
            .oldSscsCaseData(oldCaseData)
            .newSscsCaseData(sscsCaseData)
            .notificationEventType(notificationEventType)
            .build();

        return new CcdNotificationWrapper(sscsCaseDataWrapper);
    }

    private CcdNotificationWrapper buildNotificationWrapperGivenNotificationTypeAndAppointeeSubscriptions(
        NotificationEventType notificationEventType, Subscription appointeeSubscription,
        Subscription repsSubscription) {
        return buildNotificationWrapperGivenNotificationTypeAndAppointeeSubscriptions(notificationEventType, appointeeSubscription, repsSubscription, null);
    }

    private CcdNotificationWrapper buildNotificationWrapperGivenNotificationTypeAndAppointeeSubscriptions(
        NotificationEventType notificationEventType, Subscription appointeeSubscription,
        Subscription repsSubscription, SscsCaseData oldCaseData) {

        Representative rep = null;
        if (repsSubscription != null) {
            rep = Representative.builder()
                .hasRepresentative("Yes")
                .name(Name.builder().firstName("Joe").lastName("Bloggs").build())
                .address(Address.builder().line1("Rep Line 1").town("Rep Town").county("Rep County").postcode("RE9 7SE").build())
                .build();
        }

        Appointee appointee = null;
        if (appointeeSubscription != null) {
            appointee = Appointee.builder()
                .name(Name.builder().firstName("Jack").lastName("Johnson").build())
                .address(Address.builder().line1("Appellant Line 1").town("Appellant Town").county("Appellant County").postcode("AP9 7LL").build())
                .build();
        }

        sscsCaseData = SscsCaseData.builder()
            .appeal(Appeal.builder().appellant(Appellant.builder().appointee(appointee).build())
                .rep(rep)
                .hearingType(AppealHearingType.ORAL.name())
                .hearingOptions(HearingOptions.builder()
                    .wantsToAttend(YES)
                    .build())
                .build())
            .subscriptions(Subscriptions.builder()
                .appellantSubscription(Subscription.builder().build())
                .appointeeSubscription(appointeeSubscription)
                .representativeSubscription(repsSubscription)
                .build())
            .caseReference(CASE_REFERENCE)
            .hearings(singletonList(Hearing.builder().build()))
            .build();

        sscsCaseDataWrapper = SscsCaseDataWrapper.builder()
            .oldSscsCaseData(oldCaseData)
            .newSscsCaseData(sscsCaseData)
            .notificationEventType(notificationEventType)
            .build();

        return new CcdNotificationWrapper(sscsCaseDataWrapper);
    }

    @Test
    public void sendEmailToGovNotifyWhenNotificationIsAnEmailAndTemplateNotBlank() {
        String emailTemplateId = "abc";
        Notification notification = new Notification(Template.builder().emailTemplateId(emailTemplateId).smsTemplateId(null).build(), Destination.builder().email("test@testing.com").sms(null).build(), null, new Reference(), null);
        when(notificationValidService.isNotificationStillValidToSend(any(), any())).thenReturn(true);
        when(notificationValidService.isHearingTypeValidToSendNotification(any(), any())).thenReturn(true);

        when(factory.create(ccdNotificationWrapper, getSubscriptionWithType(ccdNotificationWrapper))).thenReturn(notification);
        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        verify(notificationHandler, times(1)).sendNotification(eq(ccdNotificationWrapper), eq(emailTemplateId), eq(EMAIL), any(NotificationHandler.SendNotification.class));

        verifyNoErrorsLogged(mockAppender, captorLoggingEvent);
    }

    @Test
    public void sendSmsToGovNotifyWhenNotificationIsAnSmsAndTemplateNotBlank() {
        String smsTemplateId = "123";
        Notification notification = new Notification(Template.builder().emailTemplateId(null).smsTemplateId(Arrays.asList(smsTemplateId)).build(), Destination.builder().email(null).sms("07823456746").build(), null, new Reference(), null);
        when(notificationValidService.isNotificationStillValidToSend(any(), any())).thenReturn(true);
        when(notificationValidService.isHearingTypeValidToSendNotification(any(), any())).thenReturn(true);

        when(factory.create(ccdNotificationWrapper, getSubscriptionWithType(ccdNotificationWrapper))).thenReturn(notification);
        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        verify(notificationHandler, times(1)).sendNotification(eq(ccdNotificationWrapper), eq(smsTemplateId), eq(SMS), any(NotificationHandler.SendNotification.class));

        verifyNoErrorsLogged(mockAppender, captorLoggingEvent);
    }

    @Test
    public void sendSmsAndEmailToGovNotifyWhenNotificationIsAnSmsAndEmailAndTemplateNotBlank() {
        String emailTemplateId = "abc";
        String smsTemplateId = "123";
        Notification notification = new Notification(Template.builder().emailTemplateId(emailTemplateId).smsTemplateId(Arrays.asList(smsTemplateId)).build(), Destination.builder().email("test@testing.com").sms("07823456746").build(), null, new Reference(), null);
        when(notificationValidService.isNotificationStillValidToSend(any(), any())).thenReturn(true);
        when(notificationValidService.isHearingTypeValidToSendNotification(any(), any())).thenReturn(true);

        when(factory.create(ccdNotificationWrapper, getSubscriptionWithType(ccdNotificationWrapper))).thenReturn(notification);
        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        verify(notificationHandler, times(1)).sendNotification(eq(ccdNotificationWrapper), eq(emailTemplateId), eq(EMAIL), any(NotificationHandler.SendNotification.class));
        verify(notificationHandler, times(1)).sendNotification(eq(ccdNotificationWrapper), eq(smsTemplateId), eq(SMS), any(NotificationHandler.SendNotification.class));

        verifyNoErrorsLogged(mockAppender, captorLoggingEvent);
    }

    @Test
    public void doNotSendEmailToGovNotifyWhenNotificationIsNotAnEmail() throws Exception {
        Notification notification = new Notification(Template.builder().emailTemplateId("abc").smsTemplateId(Arrays.asList("123")).build(), Destination.builder().email(null).sms("07823456746").build(), null, new Reference(), null);
        when(factory.create(ccdNotificationWrapper, getSubscriptionWithType(ccdNotificationWrapper))).thenReturn(notification);
        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        verify(notificationSender, never()).sendEmail(notification.getEmailTemplate(), notification.getEmail(), notification.getPlaceholders(), notification.getReference(), sscsCaseDataWrapper.getNotificationEventType(), ccdNotificationWrapper.getNewSscsCaseData());

        verifyNoErrorsLogged(mockAppender, captorLoggingEvent);
    }

    @Test
    public void doNotSendSmsToGovNotifyWhenNotificationIsNotAnSms() throws Exception {
        Notification notification = new Notification(Template.builder().emailTemplateId("abc").smsTemplateId(Arrays.asList("123")).build(), Destination.builder().email("test@testing.com").sms(null).build(), null, new Reference(), null);
        when(factory.create(ccdNotificationWrapper, getSubscriptionWithType(ccdNotificationWrapper))).thenReturn(notification);
        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        verify(notificationSender, never()).sendSms(notification.getSmsTemplate().get(0), notification.getMobile(), notification.getPlaceholders(), notification.getReference(), notification.getSmsSenderTemplate(), sscsCaseDataWrapper.getNotificationEventType(), ccdNotificationWrapper.getNewSscsCaseData());

        verifyNoErrorsLogged(mockAppender, captorLoggingEvent);
    }

    @Test
    public void doNotSendEmailToGovNotifyWhenEmailTemplateIsBlank() throws Exception {
        Notification notification = new Notification(Template.builder().emailTemplateId(null).smsTemplateId(Arrays.asList("123")).build(), Destination.builder().email("test@testing.com").sms("07823456746").build(), null, new Reference(), null);
        when(factory.create(ccdNotificationWrapper, getSubscriptionWithType(ccdNotificationWrapper))).thenReturn(notification);
        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        verify(notificationSender, never()).sendEmail(notification.getEmailTemplate(), notification.getEmail(), notification.getPlaceholders(), notification.getReference(), sscsCaseDataWrapper.getNotificationEventType(), ccdNotificationWrapper.getNewSscsCaseData());
        verifyNoErrorsLogged(mockAppender, captorLoggingEvent);
    }

    @Test
    public void doNotSendSmsToGovNotifyWhenSmsTemplateIsBlank() throws Exception {
        String smsTemplateId = null;
        Notification notification = new Notification(Template.builder().emailTemplateId("abc").smsTemplateId(null).build(), Destination.builder().email("test@testing.com").sms("07823456746").build(), null, new Reference(), null);
        when(factory.create(ccdNotificationWrapper, getSubscriptionWithType(ccdNotificationWrapper))).thenReturn(notification);
        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        verify(notificationSender, never()).sendSms(anyString(), eq(notification.getMobile()), eq(notification.getPlaceholders()), eq(notification.getReference()), eq(notification.getSmsSenderTemplate()), eq(sscsCaseDataWrapper.getNotificationEventType()), eq(ccdNotificationWrapper.getNewSscsCaseData()));

        verifyNoErrorsLogged(mockAppender, captorLoggingEvent);
    }

    @Test
    public void doNotSendEmailOrSmsWhenNoActiveSubscription() throws Exception {
        Appeal appeal = Appeal.builder().appellant(Appellant.builder().build()).build();
        Subscription appellantSubscription = Subscription.builder().tya(APPEAL_NUMBER).email("test@email.com")
            .mobile(MOBILE_NUMBER_1).subscribeEmail("No").subscribeSms("No").build();

        sscsCaseData = SscsCaseData.builder().appeal(appeal).subscriptions(Subscriptions.builder().appellantSubscription(appellantSubscription).build()).caseReference(CASE_REFERENCE).build();
        SscsCaseDataWrapper wrapper = SscsCaseDataWrapper.builder().newSscsCaseData(sscsCaseData).oldSscsCaseData(sscsCaseData).notificationEventType(APPEAL_WITHDRAWN_NOTIFICATION).build();
        ccdNotificationWrapper = new CcdNotificationWrapper(wrapper);

        Notification notification = new Notification(Template.builder().emailTemplateId(null).smsTemplateId(Arrays.asList("123")).build(), Destination.builder().email(null).sms("07823456746").build(), null, new Reference(), null);
        when(factory.create(ccdNotificationWrapper, getSubscriptionWithType(ccdNotificationWrapper))).thenReturn(notification);

        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        verify(notificationSender, never()).sendSms(anyString(), eq(notification.getMobile()), eq(notification.getPlaceholders()), eq(notification.getReference()), eq(notification.getSmsSenderTemplate()), eq(sscsCaseDataWrapper.getNotificationEventType()), eq(ccdNotificationWrapper.getNewSscsCaseData()));
        verify(notificationSender, never()).sendEmail(notification.getEmailTemplate(), notification.getEmail(), notification.getPlaceholders(), notification.getReference(), sscsCaseDataWrapper.getNotificationEventType(), ccdNotificationWrapper.getNewSscsCaseData());

        verifyNoErrorsLogged(mockAppender, captorLoggingEvent);
    }

    @Test
    public void createsReminders() {

        Notification notification = new Notification(Template.builder().emailTemplateId(null).smsTemplateId(Arrays.asList("123")).build(), Destination.builder().email(null).sms("07823456746").build(), null, new Reference(), null);

        when(notificationValidService.isNotificationStillValidToSend(any(), any())).thenReturn(true);
        when(notificationValidService.isHearingTypeValidToSendNotification(any(), any())).thenReturn(true);

        when(factory.create(ccdNotificationWrapper, getSubscriptionWithType(ccdNotificationWrapper))).thenReturn(notification);
        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        verify(reminderService).createReminders(ccdNotificationWrapper);

        verifyNoErrorsLogged(mockAppender, captorLoggingEvent);
    }

    @Test
    public void doNotSendNotificationWhenNotificationNotValidToSend() throws Exception {
        Notification notification = new Notification(Template.builder().emailTemplateId("abc").smsTemplateId(null).build(), Destination.builder().email("test@testing.com").sms(null).build(), null, new Reference(), null);
        when(factory.create(ccdNotificationWrapper, getSubscriptionWithType(ccdNotificationWrapper))).thenReturn(notification);
        when(notificationValidService.isNotificationStillValidToSend(any(), any())).thenReturn(false);

        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        verify(notificationSender, never()).sendEmail(notification.getEmailTemplate(), notification.getEmail(), notification.getPlaceholders(), notification.getReference(), sscsCaseDataWrapper.getNotificationEventType(), ccdNotificationWrapper.getNewSscsCaseData());

        verifyNoErrorsLogged(mockAppender, captorLoggingEvent);
    }

    @Test
    public void doNotSendNotificationWhenHearingTypeIsNotValidToSend() throws Exception {
        Notification notification = new Notification(Template.builder().emailTemplateId("abc").smsTemplateId(null).build(), Destination.builder().email("test@testing.com").sms(null).build(), null, new Reference(), null);
        when(factory.create(ccdNotificationWrapper, getSubscriptionWithType(ccdNotificationWrapper))).thenReturn(notification);
        when(notificationValidService.isHearingTypeValidToSendNotification(any(), any())).thenReturn(false);

        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        verify(notificationSender, never()).sendEmail(notification.getEmailTemplate(), notification.getEmail(), notification.getPlaceholders(), notification.getReference(), sscsCaseDataWrapper.getNotificationEventType(), ccdNotificationWrapper.getNewSscsCaseData());

        verifyNoErrorsLogged(mockAppender, captorLoggingEvent);
    }

    @Test
    public void doNotSendNotificationsOutOfHours() {
        SscsCaseDataWrapper wrapper = SscsCaseDataWrapper.builder().newSscsCaseData(sscsCaseData).oldSscsCaseData(sscsCaseData).notificationEventType(HEARING_REMINDER_NOTIFICATION).build();
        ccdNotificationWrapper = new CcdNotificationWrapper(wrapper);
        when(notificationValidService.isNotificationStillValidToSend(any(), any())).thenReturn(true);
        when(notificationValidService.isHearingTypeValidToSendNotification(any(), any())).thenReturn(true);

        Notification notification = new Notification(Template.builder().emailTemplateId("abc").smsTemplateId(Arrays.asList("123")).build(), Destination.builder().email("test@testing.com").sms("07823456746").build(), null, new Reference(), null);
        when(factory.create(ccdNotificationWrapper, getSubscriptionWithType(ccdNotificationWrapper))).thenReturn(notification);
        when(outOfHoursCalculator.isItOutOfHours()).thenReturn(true);
        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        verify(notificationHandler, never()).sendNotification(any(), any(), any(), any());
        verify(notificationHandler).scheduleNotification(ccdNotificationWrapper);
        verifyNoMoreInteractions(reminderService);

        verifyNoErrorsLogged(mockAppender, captorLoggingEvent);
    }

    @Test
    @Parameters({"VALID_APPEAL_CREATED", "DRAFT_TO_VALID_APPEAL_CREATED", "APPEAL_RECEIVED_NOTIFICATION"})
    public void delayScheduleOfEvents(NotificationEventType eventType) {
        sscsCaseData.setCaseCreated(LocalDate.now().toString());
        SscsCaseDataWrapper wrapper = SscsCaseDataWrapper.builder()
                .newSscsCaseData(sscsCaseData).oldSscsCaseData(sscsCaseData)
                .notificationEventType(eventType)
                .build();
        ccdNotificationWrapper = new CcdNotificationWrapper(wrapper);
        when(notificationValidService.isNotificationStillValidToSend(any(), any())).thenReturn(true);
        when(notificationValidService.isHearingTypeValidToSendNotification(any(), any())).thenReturn(true);

        Notification notification = new Notification(Template.builder().emailTemplateId("abc").smsTemplateId(Arrays.asList("123")).build(), Destination.builder().email("test@testing.com").sms("07823456746").build(), null, new Reference(), null);
        when(factory.create(ccdNotificationWrapper, getSubscriptionWithType(ccdNotificationWrapper))).thenReturn(notification);
        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        verify(notificationHandler, never()).sendNotification(any(), any(), any(), any());
        ArgumentCaptor<ZonedDateTime> argument = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(notificationHandler).scheduleNotification(eq(ccdNotificationWrapper), argument.capture());
        assertThat(argument.getValue().isBefore(ZonedDateTime.now().plusMinutes(6)), is(true));
        verifyNoMoreInteractions(reminderService);

        verifyNoErrorsLogged(mockAppender, captorLoggingEvent);
    }

    @Test
    public void shouldSendEmailAndSmsToOldEmailAddressForEmailSubscriptionUpdateForPaperCase() {
        Subscription appellantNewSubscription = Subscription.builder().tya(APPEAL_NUMBER).email(NEW_TEST_EMAIL_COM)
            .mobile(MOBILE_NUMBER_1).subscribeEmail(YES).subscribeSms(YES).wantSmsNotifications(YES).build();
        Subscription appellantOldSubscription = Subscription.builder().tya(APPEAL_NUMBER).email("oldtest@email.com")
            .mobile(MOBILE_NUMBER_2).subscribeEmail(YES).subscribeSms(YES).wantSmsNotifications(YES).build();

        SscsCaseData newSscsCaseData = SscsCaseData.builder()
            .appeal(Appeal.builder().appellant(Appellant.builder().build())
                .hearingType(AppealHearingType.PAPER.name()).benefitType(BenefitType.builder().code(PIP).build()).build())
            .subscriptions(Subscriptions.builder().appellantSubscription(appellantNewSubscription).build())
            .caseReference(CASE_REFERENCE).build();

        SscsCaseData oldSscsCaseData = SscsCaseData.builder()
            .appeal(Appeal.builder().appellant(Appellant.builder().build())
                .hearingType(AppealHearingType.PAPER.name()).benefitType(BenefitType.builder().code(PIP).build()).build())
            .subscriptions(Subscriptions.builder().appellantSubscription(appellantOldSubscription).build())
            .caseReference(CASE_REFERENCE).build();

        SscsCaseDataWrapper wrapper = SscsCaseDataWrapper.builder().newSscsCaseData(newSscsCaseData).oldSscsCaseData(oldSscsCaseData).notificationEventType(SUBSCRIPTION_UPDATED_NOTIFICATION).build();
        ccdNotificationWrapper = new CcdNotificationWrapper(wrapper);

        Notification notification = new Notification(
            Template.builder().emailTemplateId(EMAIL_TEMPLATE_ID).smsTemplateId(Arrays.asList(SMS_TEMPLATE_ID)).build(),
            Destination.builder().email(NEW_TEST_EMAIL_COM).sms(MOBILE_NUMBER_2).build(), null, new Reference(), null);
        when(notificationValidService.isNotificationStillValidToSend(any(), any())).thenReturn(true);
        when(notificationValidService.isHearingTypeValidToSendNotification(any(), any())).thenReturn(true);

        when(factory.create(ccdNotificationWrapper, getSubscriptionWithType(ccdNotificationWrapper))).thenReturn(notification);
        when(notificationConfig.getTemplate(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(Template.builder().emailTemplateId(EMAIL_TEMPLATE_ID).smsTemplateId(Arrays.asList(SMS_TEMPLATE_ID)).build());

        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        verify(notificationHandler, times(2)).sendNotification(eq(ccdNotificationWrapper), any(), eq(EMAIL), any(NotificationHandler.SendNotification.class));
        verify(notificationHandler, times(2)).sendNotification(eq(ccdNotificationWrapper), any(), eq(SMS), any(NotificationHandler.SendNotification.class));

        verifyNoErrorsLogged(mockAppender, captorLoggingEvent);
    }


    @Test
    public void shouldNotSendEmailOrSmsToOldEmailAddressIfOldAndNewEmailAndSmsAreSame() {
        Subscription appellantNewSubscription = Subscription.builder().tya(APPEAL_NUMBER).email(SAME_TEST_EMAIL_COM)
            .mobile(MOBILE_NUMBER_1).subscribeEmail(YES).subscribeSms(YES).wantSmsNotifications(YES).build();
        Subscription appellantOldSubscription = Subscription.builder().tya(APPEAL_NUMBER).email(SAME_TEST_EMAIL_COM)
            .mobile(MOBILE_NUMBER_1).subscribeEmail(YES).subscribeSms(YES).wantSmsNotifications(YES).build();

        SscsCaseData newSscsCaseData = SscsCaseData.builder()
            .appeal(Appeal.builder().appellant(Appellant.builder().build())
                .hearingType(AppealHearingType.PAPER.name()).benefitType(BenefitType.builder().code(PIP).build()).build())
            .subscriptions(Subscriptions.builder().appellantSubscription(appellantNewSubscription).build())
            .caseReference(CASE_REFERENCE).build();

        SscsCaseData oldSscsCaseData = SscsCaseData.builder()
            .appeal(Appeal.builder().appellant(Appellant.builder().build())
                .hearingType(AppealHearingType.PAPER.name()).benefitType(BenefitType.builder().code(PIP).build()).build())
            .subscriptions(Subscriptions.builder().appellantSubscription(appellantOldSubscription).build())
            .caseReference(CASE_REFERENCE).build();

        SscsCaseDataWrapper wrapper = SscsCaseDataWrapper.builder().newSscsCaseData(newSscsCaseData)
            .oldSscsCaseData(oldSscsCaseData).notificationEventType(SUBSCRIPTION_UPDATED_NOTIFICATION).build();
        ccdNotificationWrapper = new CcdNotificationWrapper(wrapper);

        Notification notification = new Notification(
            Template.builder().emailTemplateId(EMAIL_TEMPLATE_ID).smsTemplateId(Arrays.asList(SMS_TEMPLATE_ID)).build(),
            Destination.builder().email(NEW_TEST_EMAIL_COM).sms(MOBILE_NUMBER_2).build(), null, new Reference(), null);

        when(notificationValidService.isNotificationStillValidToSend(any(), any())).thenReturn(true);
        when(notificationValidService.isHearingTypeValidToSendNotification(any(), any())).thenReturn(true);

        when(factory.create(ccdNotificationWrapper, getSubscriptionWithType(ccdNotificationWrapper))).thenReturn(notification);
        when(notificationConfig.getTemplate(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(Template.builder().emailTemplateId(EMAIL_TEMPLATE_ID).smsTemplateId(Arrays.asList(SMS_TEMPLATE_ID)).build());

        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        verify(notificationHandler, times(1)).sendNotification(eq(ccdNotificationWrapper), any(), eq(EMAIL), any(NotificationHandler.SendNotification.class));
        verify(notificationHandler, times(1)).sendNotification(eq(ccdNotificationWrapper), any(), eq(SMS), any(NotificationHandler.SendNotification.class));

        verifyNoErrorsLogged(mockAppender, captorLoggingEvent);
    }

    @Test
    public void shouldNotSendEmailAndSmsToOldEmailAddressIfOldEmailAddressAndSmsNotPresent() {
        Subscription appellantNewSubscription = Subscription.builder()
            .tya(APPEAL_NUMBER)
            .email(SAME_TEST_EMAIL_COM)
            .mobile(MOBILE_NUMBER_1)
            .subscribeEmail(YES)
            .subscribeSms(YES).wantSmsNotifications(YES)
            .build();
        Subscription appellantOldSubscription = Subscription.builder()
            .tya(APPEAL_NUMBER)
            .build();

        SscsCaseData newSscsCaseData = SscsCaseData.builder()
            .appeal(Appeal.builder()
                .appellant(Appellant.builder().build())
                .hearingType(AppealHearingType.PAPER.name())
                .benefitType(BenefitType.builder()
                    .code(PIP)
                    .build())
                .build())
            .subscriptions(Subscriptions.builder()
                .appellantSubscription(appellantNewSubscription)
                .build())
            .caseReference(CASE_REFERENCE).build();

        SscsCaseData oldSscsCaseData = SscsCaseData.builder()
            .appeal(Appeal.builder()
                .appellant(Appellant.builder().build())
                .hearingType(AppealHearingType.PAPER.name())
                .benefitType(BenefitType.builder()
                    .code(PIP)
                    .build())
                .build())
            .subscriptions(Subscriptions.builder()
                .appellantSubscription(appellantOldSubscription).build())
            .caseReference(CASE_REFERENCE).build();

        SscsCaseDataWrapper wrapper = SscsCaseDataWrapper.builder()
            .newSscsCaseData(newSscsCaseData)
            .oldSscsCaseData(oldSscsCaseData)
            .notificationEventType(SUBSCRIPTION_UPDATED_NOTIFICATION)
            .build();
        ccdNotificationWrapper = new CcdNotificationWrapper(wrapper);

        Notification notification = new Notification(
            Template.builder()
                .emailTemplateId(EMAIL_TEMPLATE_ID)
                .smsTemplateId(Arrays.asList(SMS_TEMPLATE_ID))
                .build(),
            Destination.builder()
                .email(NEW_TEST_EMAIL_COM)
                .sms(MOBILE_NUMBER_2)
                .build(),
            null,
            new Reference(),
            null);

        when(notificationValidService.isNotificationStillValidToSend(any(), any())).thenReturn(true);
        when(notificationValidService.isHearingTypeValidToSendNotification(any(), any())).thenReturn(true);

        when(factory.create(ccdNotificationWrapper, getSubscriptionWithType(ccdNotificationWrapper))).thenReturn(notification);
        when(notificationConfig.getTemplate(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(Template.builder().emailTemplateId(EMAIL_TEMPLATE_ID).smsTemplateId(Arrays.asList(SMS_TEMPLATE_ID)).build());

        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        verify(notificationHandler, times(1)).sendNotification(eq(ccdNotificationWrapper), any(), eq(EMAIL), any(NotificationHandler.SendNotification.class));
        verify(notificationHandler, times(1)).sendNotification(eq(ccdNotificationWrapper), any(), eq(SMS), any(NotificationHandler.SendNotification.class));

        verifyNoErrorsLogged(mockAppender, captorLoggingEvent);
    }

    @Test
    public void sendAppellantLetterOnAppealReceived() throws IOException {
        String fileUrl = "http://dm-store:4506/documents/1e1eb3d2-5b6c-430d-8dad-ebcea1ad7ecf";
        String docmosisId = "docmosis-id.doc";
        CcdNotificationWrapper ccdNotificationWrapper = buildWrapperWithDocuments(APPEAL_RECEIVED_NOTIFICATION, fileUrl, APPELLANT_WITH_ADDRESS, null, "");
        Notification notification = new Notification(Template.builder().docmosisTemplateId(docmosisId).build(), Destination.builder().build(), new HashMap<>(), new Reference(), null);

        byte[] sampleDirectionCoversheet = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("pdfs/direction-notice-coversheet-sample.pdf"));

        when((notificationValidService).isNotificationStillValidToSend(any(), any())).thenReturn(true);
        when((notificationValidService).isHearingTypeValidToSendNotification(any(), any())).thenReturn(true);
        when(pdfLetterService.generateLetter(any(), any(), any())).thenReturn(sampleDirectionCoversheet);

        when(factory.create(ccdNotificationWrapper, getSubscriptionWithType(ccdNotificationWrapper))).thenReturn(notification);

        getNotificationService().manageNotificationAndSubscription(ccdNotificationWrapper, true);

        verify(notificationHandler, times(1)).sendNotification(eq(ccdNotificationWrapper), eq(docmosisId), eq(LETTER), any(NotificationHandler.SendNotification.class));

        verifyNoErrorsLogged(mockAppender, captorLoggingEvent);
    }

    @Test
    public void shouldLogErrorWhenIncompleteInfoRequestWithEmptyInfoFromAppellant() {
        CcdNotificationWrapper wrapper = buildBaseWrapperWithCaseData(
            getSscsCaseDataBuilderSettingInformationFromAppellant(APPELLANT_WITH_ADDRESS, null, null, null).build(),
            REQUEST_INFO_INCOMPLETE
        );

        getNotificationService().manageNotificationAndSubscription(wrapper, false);

        verifyExpectedErrorLogMessage(mockAppender, captorLoggingEvent, wrapper.getNewSscsCaseData().getCcdCaseId(), "Request Incomplete Information");
    }

    @Test
    public void shouldLogErrorWhenIncompleteInfoRequestWithNoInfoFromAppellant() {
        CcdNotificationWrapper wrapper = buildBaseWrapperWithCaseData(
            getSscsCaseDataBuilderSettingInformationFromAppellant(APPELLANT_WITH_ADDRESS, null, null, "no").build(),
            REQUEST_INFO_INCOMPLETE
        );

        getNotificationService().manageNotificationAndSubscription(wrapper, false);

        verifyExpectedErrorLogMessage(mockAppender, captorLoggingEvent, wrapper.getNewSscsCaseData().getCcdCaseId(), "Request Incomplete Information");
    }

    @Test
    public void shouldNotLogErrorWhenIncompleteInfoRequestWithInfoFromAppellant() {
        CcdNotificationWrapper wrapper = buildBaseWrapperWithCaseData(
            getSscsCaseDataBuilderSettingInformationFromAppellant(APPELLANT_WITH_ADDRESS, null, null, "yes").build(),
            REQUEST_INFO_INCOMPLETE
        );

        given(factory.create(any(NotificationWrapper.class), any(SubscriptionWithType.class)))
            .willReturn(new Notification(
                Template.builder()
                    .emailTemplateId(EMAIL_TEMPLATE_ID)
                    .smsTemplateId(Arrays.asList(SMS_TEMPLATE_ID))
                    .build(),
                Destination.builder()
                    .email(EMAIL)
                    .sms(SMS_MOBILE)
                    .build(),
                new HashMap<>(),
                new Reference(),
                null));

        getNotificationService().manageNotificationAndSubscription(wrapper, false);

        verifyNoErrorsLogged(mockAppender, captorLoggingEvent);
    }

    @Test
    @Parameters(method = "allEventTypesExceptRequestInfoIncomplete")
    public void shouldNotLogErrorWhenNotIncompleteInfoRequest(NotificationEventType eventType) {
        CcdNotificationWrapper wrapper = buildBaseWrapperWithCaseData(
            getSscsCaseDataBuilderSettingInformationFromAppellant(APPELLANT_WITH_ADDRESS, null, null, "yes").build(),
            eventType
        );

        given(factory.create(any(NotificationWrapper.class), any(SubscriptionWithType.class)))
            .willReturn(new Notification(
                Template.builder()
                    .emailTemplateId(EMAIL_TEMPLATE_ID)
                    .smsTemplateId(Arrays.asList(SMS_TEMPLATE_ID))
                    .build(),
                Destination.builder()
                    .email(EMAIL)
                    .sms(SMS_MOBILE)
                    .build(),
                new HashMap<>(),
                new Reference(),
                null));

        getNotificationService().manageNotificationAndSubscription(wrapper, false);

        verifyErrorLogMessageNotLogged(mockAppender, captorLoggingEvent, "Request Incomplete Information");
    }

    @Test
    public void shouldLogErrorWhenNotValidationNotification() {
        CcdNotificationWrapper wrapper = buildBaseWrapperWithCaseData(
            getSscsCaseDataBuilderSettingInformationFromAppellant(APPELLANT_WITH_ADDRESS, null, null, "yes").build(),
            ADJOURNED_NOTIFICATION
        );

        given(factory.create(any(NotificationWrapper.class), any(SubscriptionWithType.class)))
            .willReturn(new Notification(
                Template.builder()
                    .emailTemplateId(EMAIL_TEMPLATE_ID)
                    .smsTemplateId(Arrays.asList(SMS_TEMPLATE_ID))
                    .build(),
                Destination.builder()
                    .email(EMAIL)
                    .sms(SMS_MOBILE)
                    .build(),
                new HashMap<>(),
                new Reference(),
                null));

        getNotificationService().manageNotificationAndSubscription(wrapper, false);

        verifyExpectedErrorLogMessage(mockAppender, captorLoggingEvent, wrapper.getNewSscsCaseData().getCcdCaseId(), "Is not a valid notification event");
    }

    @Test
    public void willSendDwpUpload_whenCreatedInGapsFromIsReadyToList() throws NotificationClientException {
        CcdNotificationWrapper ccdNotificationWrapper = buildBaseWrapper(DWP_UPLOAD_RESPONSE_NOTIFICATION,  APPELLANT_WITH_ADDRESS, null, null);
        ccdNotificationWrapper.getNewSscsCaseData().setCreatedInGapsFrom(READY_TO_LIST.getId());

        Notification notification = new Notification(Template.builder().emailTemplateId(EMAIL_TEMPLATE_ID).smsTemplateId(null).build(), Destination.builder().email("test@testing.com").sms("07823456746").build(), null, new Reference(), null);
        given(factory.create(ccdNotificationWrapper, getSubscriptionWithType(ccdNotificationWrapper))).willReturn(notification);
        given(factory.create(ccdNotificationWrapper, getSubscriptionWithTypeJoint(ccdNotificationWrapper))).willReturn(notification);
        given(notificationValidService.isHearingTypeValidToSendNotification(
                any(SscsCaseData.class), eq(DWP_UPLOAD_RESPONSE_NOTIFICATION))).willReturn(true);
        given(notificationValidService.isNotificationStillValidToSend(anyList(), eq(DWP_UPLOAD_RESPONSE_NOTIFICATION)))
                .willReturn(true);
        given(notificationValidService.isFallbackLetterRequiredForSubscriptionType(any(), any(), any())).willReturn(true);

        getNotificationService().manageNotificationAndSubscription(ccdNotificationWrapper, false);

        then(notificationHandler).should(atLeastOnce()).sendNotification(
                eq(ccdNotificationWrapper), eq(EMAIL_TEMPLATE_ID), eq("Email"),
                any(NotificationHandler.SendNotification.class));
    }

    @Test
    public void willNotSendDwpUpload_whenCreatedInGapsFromIsValidAppeal() {
        CcdNotificationWrapper ccdNotificationWrapper = buildBaseWrapper(DWP_UPLOAD_RESPONSE_NOTIFICATION,  APPELLANT_WITH_ADDRESS, null, null);
        ccdNotificationWrapper.getNewSscsCaseData().setCreatedInGapsFrom(State.VALID_APPEAL.getId());

        Notification notification = new Notification(Template.builder().emailTemplateId(EMAIL_TEMPLATE_ID).smsTemplateId(null).build(), Destination.builder().email("test@testing.com").sms("07823456746").build(), null, new Reference(), null);
        given(factory.create(ccdNotificationWrapper, getSubscriptionWithType(ccdNotificationWrapper))).willReturn(notification);
        given(notificationValidService.isHearingTypeValidToSendNotification(
                any(SscsCaseData.class), eq(DWP_UPLOAD_RESPONSE_NOTIFICATION))).willReturn(true);
        given(notificationValidService.isNotificationStillValidToSend(anyList(), eq(DWP_UPLOAD_RESPONSE_NOTIFICATION)))
                .willReturn(true);
        given(notificationValidService.isFallbackLetterRequiredForSubscriptionType(any(), any(), any())).willReturn(true);

        getNotificationService().manageNotificationAndSubscription(ccdNotificationWrapper, false);

        then(notificationHandler).shouldHaveNoMoreInteractions();
    }

    @Test
    @Parameters({"HEARING_BOOKED_NOTIFICATION", "HEARING_REMINDER_NOTIFICATION"})
    public void willNotSendHearingNotifications_whenCovid19FeatureTrue(NotificationEventType notificationEventType) {
        CcdNotificationWrapper ccdNotificationWrapper = buildBaseWrapper(notificationEventType,  APPELLANT_WITH_ADDRESS, null, null);
        ccdNotificationWrapper.getNewSscsCaseData().setCreatedInGapsFrom(State.VALID_APPEAL.getId());

        Notification notification = new Notification(Template.builder().emailTemplateId(EMAIL_TEMPLATE_ID).smsTemplateId(null).build(), Destination.builder().email("test@testing.com").sms("07823456746").build(), null, new Reference(), null);
        given(factory.create(ccdNotificationWrapper, getSubscriptionWithType(ccdNotificationWrapper))).willReturn(notification);
        given(notificationValidService.isHearingTypeValidToSendNotification(
                any(SscsCaseData.class), eq(notificationEventType))).willReturn(true);
        given(notificationValidService.isNotificationStillValidToSend(anyList(), eq(notificationEventType)))
                .willReturn(true);
        given(notificationValidService.isFallbackLetterRequiredForSubscriptionType(any(), any(), any())).willReturn(true);

        SendNotificationService sendNotificationService = new SendNotificationService(notificationSender, evidenceManagementService, notificationHandler, notificationValidService, pdfLetterService);

        final NotificationService notificationService = new NotificationService(factory, reminderService,
                notificationValidService, notificationHandler, outOfHoursCalculator, notificationConfig, sendNotificationService, true
        );

        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        then(notificationHandler).shouldHaveNoMoreInteractions();
    }

    @Test
    @Parameters({"DIRECTION_ISSUED", "DECISION_ISSUED", "ISSUE_FINAL_DECISION", "ISSUE_FINAL_DECISION_WELSH", "DIRECTION_ISSUED_WELSH", "DECISION_ISSUED_WELSH"})
    public void givenReissueDocumentEventReceivedAndResendToAppellantYes_thenOverrideNotificationTypeAndSendToAppellant(NotificationEventType notificationEventType) throws IOException {
        CcdNotificationWrapper ccdNotificationWrapper = buildBaseWrapper(REISSUE_DOCUMENT,  APPELLANT_WITH_ADDRESS, null, SscsDocument.builder().value(SscsDocumentDetails.builder().build()).build());
        ccdNotificationWrapper.getNewSscsCaseData().setResendToAppellant("Yes");
        ccdNotificationWrapper.getNewSscsCaseData().setReissueFurtherEvidenceDocument(new DynamicList(notificationEventType.getId()));
        ccdNotificationWrapper.getNewSscsCaseData().setSubscriptions(null);

        Notification notification = new Notification(Template.builder().docmosisTemplateId(LETTER_TEMPLATE_ID).emailTemplateId(null).smsTemplateId(null).build(), Destination.builder().email("test@testing.com").sms("07823456746").build(), null, new Reference(), null);
        given(factory.create(ccdNotificationWrapperCaptor.capture(), eq(getSubscriptionWithType(ccdNotificationWrapper)))).willReturn(notification);
        given(notificationValidService.isHearingTypeValidToSendNotification(
                any(SscsCaseData.class), any())).willReturn(true);
        given(notificationValidService.isNotificationStillValidToSend(anyList(), any()))
                .willReturn(true);
        given(notificationValidService.isFallbackLetterRequiredForSubscriptionType(any(), any(), any())).willReturn(true);

        byte[] sampleDirectionCoversheet = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("pdfs/direction-notice-coversheet-sample.pdf"));
        given(pdfLetterService.generateLetter(any(), any(), any())).willReturn(sampleDirectionCoversheet);

        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        assertEquals(notificationEventType, ccdNotificationWrapperCaptor.getValue().getNotificationType());

        then(notificationHandler).should(atLeastOnce()).sendNotification(
                eq(ccdNotificationWrapper), any(), eq("Letter"),
                any(NotificationHandler.SendNotification.class));
    }

    @Test
    @Parameters({"DRAFT_TO_NON_COMPLIANT_NOTIFICATION, NON_COMPLIANT_NOTIFICATION", "DRAFT_TO_VALID_APPEAL_CREATED, VALID_APPEAL_CREATED"})
    public void givenNotificationTypeToBeOverriden_thenOverrideNotificationTypeAndSend(NotificationEventType receivedNotificationType, NotificationEventType sentNotificationType) throws IOException {
        CcdNotificationWrapper ccdNotificationWrapper = buildBaseWrapper(receivedNotificationType, APPELLANT_WITH_ADDRESS, null, SscsDocument.builder().value(SscsDocumentDetails.builder().build()).build());
        ccdNotificationWrapper.getNewSscsCaseData().setSubscriptions(null);

        Notification notification = new Notification(Template.builder().docmosisTemplateId(LETTER_TEMPLATE_ID).emailTemplateId(null).smsTemplateId(null).build(), Destination.builder().email("test@testing.com").sms("07823456746").build(), null, new Reference(), null);
        given(factory.create(ccdNotificationWrapperCaptor.capture(), eq(getSubscriptionWithType(ccdNotificationWrapper)))).willReturn(notification);
        given(notificationValidService.isHearingTypeValidToSendNotification(
                any(SscsCaseData.class), any())).willReturn(true);
        given(notificationValidService.isNotificationStillValidToSend(anyList(), any()))
                .willReturn(true);
        given(notificationValidService.isFallbackLetterRequiredForSubscriptionType(any(), any(), any())).willReturn(true);

        byte[] sampleDirectionCoversheet = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("pdfs/direction-notice-coversheet-sample.pdf"));
        given(pdfLetterService.generateLetter(any(), any(), any())).willReturn(sampleDirectionCoversheet);

        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, true);

        assertEquals(sentNotificationType, ccdNotificationWrapperCaptor.getValue().getNotificationType());

        then(notificationHandler).should(atLeastOnce()).sendNotification(
                eq(ccdNotificationWrapper), any(), eq("Letter"),
                any(NotificationHandler.SendNotification.class));
    }

    @Test
    @Parameters({"DIRECTION_ISSUED, No", "DIRECTION_ISSUED, null", "DECISION_ISSUED, No", "DECISION_ISSUED, null",
            "DIRECTION_ISSUED_WELSH, No", "DIRECTION_ISSUED_WELSH, null", "DECISION_ISSUED_WELSH, No", "DECISION_ISSUED_WELSH, null",
            "ISSUE_FINAL_DECISION, No", "ISSUE_FINAL_DECISION, null", "ISSUE_FINAL_DECISION_WELSH, No", "ISSUE_FINAL_DECISION_WELSH, null", "ISSUE_ADJOURNMENT_NOTICE, No", "ISSUE_ADJOURNMENT_NOTICE, null"})
    public void givenReissueDocumentEventReceivedAndResendToAppellantNotSet_thenDoNotSendToAppellant(NotificationEventType notificationEventType, @Nullable String resendToAppellant) {
        CcdNotificationWrapper ccdNotificationWrapper = buildBaseWrapper(REISSUE_DOCUMENT,  APPELLANT_WITH_ADDRESS, null, SscsDocument.builder().value(SscsDocumentDetails.builder().build()).build());
        ccdNotificationWrapper.getNewSscsCaseData().setResendToAppellant(resendToAppellant);
        ccdNotificationWrapper.getNewSscsCaseData().setReissueFurtherEvidenceDocument(new DynamicList(notificationEventType.getId()));
        ccdNotificationWrapper.getNewSscsCaseData().setSubscriptions(null);

        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        verifyNoInteractions(notificationHandler);
    }

    @Test
    @Parameters({"DIRECTION_ISSUED", "DECISION_ISSUED", "DIRECTION_ISSUED_WELSH", "DECISION_ISSUED_WELSH",  "ISSUE_FINAL_DECISION", "ISSUE_FINAL_DECISION_WELSH", "ISSUE_ADJOURNMENT_NOTICE"})
    public void givenReissueDocumentEventReceivedAndResendToRepYes_thenOverrideNotificationTypeAndSendToRep(NotificationEventType notificationEventType) throws IOException {
        CcdNotificationWrapper ccdNotificationWrapper = buildBaseWrapper(REISSUE_DOCUMENT,  APPELLANT_WITH_ADDRESS, Representative.builder().hasRepresentative("yes").address(Address.builder().line1("test").postcode("Bla").build()).build(), SscsDocument.builder().value(SscsDocumentDetails.builder().build()).build());
        ccdNotificationWrapper.getNewSscsCaseData().setResendToRepresentative("Yes");
        ccdNotificationWrapper.getNewSscsCaseData().setReissueFurtherEvidenceDocument(new DynamicList(notificationEventType.getId()));
        ccdNotificationWrapper.getNewSscsCaseData().setSubscriptions(null);

        Notification notification = new Notification(Template.builder().docmosisTemplateId(LETTER_TEMPLATE_ID).emailTemplateId(null).smsTemplateId(null).build(), Destination.builder().email("test@testing.com").sms("07823456746").build(), null, new Reference(), null);
        given(factory.create(ccdNotificationWrapperCaptor.capture(), eq(getSubscriptionWithTypeRep(ccdNotificationWrapper)))).willReturn(notification);
        given(notificationValidService.isHearingTypeValidToSendNotification(
                any(SscsCaseData.class), any())).willReturn(true);
        given(notificationValidService.isNotificationStillValidToSend(anyList(), any()))
                .willReturn(true);
        given(notificationValidService.isFallbackLetterRequiredForSubscriptionType(any(), any(), any())).willReturn(true);

        byte[] sampleDirectionCoversheet = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("pdfs/direction-notice-coversheet-sample.pdf"));
        given(pdfLetterService.generateLetter(any(), any(), any())).willReturn(sampleDirectionCoversheet);

        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        assertEquals(notificationEventType, ccdNotificationWrapperCaptor.getValue().getNotificationType());

        then(notificationHandler).should(atLeastOnce()).sendNotification(
                eq(ccdNotificationWrapper), any(), eq("Letter"),
                any(NotificationHandler.SendNotification.class));
    }

    @Test
    @Parameters({"DIRECTION_ISSUED, No", "DIRECTION_ISSUED, null", "DECISION_ISSUED, No", "DECISION_ISSUED, null",
            "DIRECTION_ISSUED, No", "DIRECTION_ISSUED, null", "DECISION_ISSUED, No", "DECISION_ISSUED, null",
            "ISSUE_FINAL_DECISION, No", "ISSUE_FINAL_DECISION, null", "ISSUE_FINAL_DECISION_WELSH, No", "ISSUE_FINAL_DECISION_WELSH, null", "ISSUE_ADJOURNMENT_NOTICE, No", "ISSUE_ADJOURNMENT_NOTICE, null"})
    public void givenReissueDocumentEventReceivedAndResendToRepNotSet_thenDoNotSendToRep(NotificationEventType notificationEventType, @Nullable String resendToRep) {
        CcdNotificationWrapper ccdNotificationWrapper = buildBaseWrapper(REISSUE_DOCUMENT,  APPELLANT_WITH_ADDRESS, Representative.builder().hasRepresentative("yes").build(), SscsDocument.builder().value(SscsDocumentDetails.builder().build()).build());
        ccdNotificationWrapper.getNewSscsCaseData().setResendToRepresentative(resendToRep);
        ccdNotificationWrapper.getNewSscsCaseData().setReissueFurtherEvidenceDocument(new DynamicList(notificationEventType.getId()));
        ccdNotificationWrapper.getNewSscsCaseData().setSubscriptions(null);

        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        verifyNoInteractions(notificationHandler);
    }


    @Test
    @Parameters({"DIRECTION_ISSUED, Yes", "DECISION_ISSUED, Yes", "ISSUE_ADJOURNMENT_NOTICE, Yes", "PROCESS_AUDIO_VIDEO, Yes", "ISSUE_FINAL_DECISION, Yes"})
    public void givenIssueDocumentEventReceivedAndWelshLanguagePref_thenDoNotSendToNotifications(NotificationEventType notificationEventType, @Nullable String languagePrefWelsh) {
        CcdNotificationWrapper ccdNotificationWrapper = buildBaseWrapper(notificationEventType,  APPELLANT_WITH_ADDRESS, Representative.builder().hasRepresentative("no").build(), SscsDocument.builder().value(SscsDocumentDetails.builder().build()).build());

        ccdNotificationWrapper.getNewSscsCaseData().setState(State.WITH_DWP);
        ccdNotificationWrapper.getNewSscsCaseData().setSubscriptions(null);
        ccdNotificationWrapper.getNewSscsCaseData().setLanguagePreferenceWelsh(languagePrefWelsh);
        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        verifyNoInteractions(notificationHandler);
    }

    @Test
    @Parameters({"issueDirectionsNotice", "excludeEvidence", "includeEvidence"})
    public void givenProcessAudioVideo_thenProcessNotificationForCertainActions(String action) {
        CcdNotificationWrapper ccdNotificationWrapper = buildBaseWrapper(PROCESS_AUDIO_VIDEO,  APPELLANT_WITH_ADDRESS, Representative.builder().hasRepresentative("no").build(), SscsDocument.builder().value(SscsDocumentDetails.builder().build()).build());
        ccdNotificationWrapper.getNewSscsCaseData().setProcessAudioVideoAction(new DynamicList(new DynamicListItem(action, action), null));
        when(outOfHoursCalculator.isItOutOfHours()).thenReturn(false);
        Notification notification = new Notification(Template.builder().docmosisTemplateId(LETTER_TEMPLATE_ID).emailTemplateId(null).smsTemplateId(null).build(), Destination.builder().email("test@testing.com").sms("07823456746").build(), null, new Reference(), null);
        given(factory.create(ccdNotificationWrapperCaptor.capture(), any())).willReturn(notification);
        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);
        verify(reminderService).createReminders(ccdNotificationWrapper);
    }

    @Test
    @Parameters({"sendToJudge", "sendToAdmin"})
    public void givenProcessAudioVideo_thenDoProcessNotificationForCertainActions(String action) {
        CcdNotificationWrapper ccdNotificationWrapper = buildBaseWrapper(PROCESS_AUDIO_VIDEO,  APPELLANT_WITH_ADDRESS, Representative.builder().hasRepresentative("no").build(), SscsDocument.builder().value(SscsDocumentDetails.builder().build()).build());
        ccdNotificationWrapper.getNewSscsCaseData().setProcessAudioVideoAction(new DynamicList(new DynamicListItem(action, action), null));
        when(outOfHoursCalculator.isItOutOfHours()).thenReturn(false);
        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);
        verifyNoInteractions(reminderService);
    }

    @Test
    @Parameters({"DIRECTION_ISSUED_WELSH, Yes", "DECISION_ISSUED_WELSH, Yes", "ISSUE_FINAL_DECISION_WELSH, Yes"})
    public void givenIssueDocumentEventReceivedAndEventWelsh_thenDoSendNotifications(NotificationEventType notificationEventType, @Nullable String languagePrefWelsh) {
        CcdNotificationWrapper ccdNotificationWrapper = buildBaseWrapper(notificationEventType,  APPELLANT_WITH_ADDRESS, Representative.builder().hasRepresentative("no").build(), SscsDocument.builder().value(SscsDocumentDetails.builder().build()).build());
        ccdNotificationWrapper.getNewSscsCaseData().setState(State.WITH_DWP);
        ccdNotificationWrapper.getNewSscsCaseData().setSubscriptions(Subscriptions.builder().appellantSubscription(Subscription.builder()
                .tya(APPEAL_NUMBER)
                .email(EMAIL)
                .subscribeEmail(YES)
                .mobile(MOBILE_NUMBER_1)
                .subscribeSms(YES).wantSmsNotifications(YES)
                .build()).build());
        ccdNotificationWrapper.getNewSscsCaseData().setLanguagePreferenceWelsh(languagePrefWelsh);

        String emailTemplateId = "abc";
        Notification notification = new Notification(Template.builder().emailTemplateId(emailTemplateId).smsTemplateId(null).build(), Destination.builder().email("test@testing.com").sms(null).build(), null, new Reference(), null);
        when(notificationValidService.isNotificationStillValidToSend(any(), any())).thenReturn(true);
        when(notificationValidService.isHearingTypeValidToSendNotification(any(), any())).thenReturn(true);

        when(factory.create(ccdNotificationWrapper, getSubscriptionWithType(ccdNotificationWrapper))).thenReturn(notification);
        notificationService.manageNotificationAndSubscription(ccdNotificationWrapper, false);

        then(notificationHandler).should(atLeastOnce()).sendNotification(
                eq(ccdNotificationWrapper), eq(emailTemplateId), eq("Email"),
                any(NotificationHandler.SendNotification.class));
    }



    @SuppressWarnings({"Indentation", "UnusedPrivateMethod"})
    private Object[] allEventTypesExceptRequestInfoIncomplete() {
        return Arrays.stream(NotificationEventType.values()).filter(eventType ->
            !eventType.equals(REQUEST_INFO_INCOMPLETE)
                && !BUNDLED_LETTER_EVENT_TYPES.contains(eventType)
        ).toArray();
    }

    @Test
    public void hasJustSubscribedNoChange_returnsFalse() {
        assertFalse(NotificationService.hasCaseJustSubscribed(subscription, subscription));
    }

    @Test
    public void hasJustSubscribedUnsubscribedEmailAndSms_returnsFalse() {
        Subscription newSubscription = subscription.toBuilder().subscribeEmail(NO).subscribeSms(NO).wantSmsNotifications(NO).build();
        assertFalse(NotificationService.hasCaseJustSubscribed(newSubscription, subscription));
    }

    @Test
    public void hasJustSubscribedEmailAndMobile_returnsTrue() {
        Subscription oldSubscription = subscription.toBuilder().subscribeEmail(NO).subscribeSms(NO).wantSmsNotifications(NO).build();
        assertTrue(NotificationService.hasCaseJustSubscribed(subscription, oldSubscription));
    }

    @Test
    public void hasJustSubscribedEmail_returnsTrue() {
        Subscription oldSubscription = subscription.toBuilder().subscribeEmail(NO).build();
        assertTrue(NotificationService.hasCaseJustSubscribed(subscription, oldSubscription));
    }

    @Test
    public void hasJustSubscribedSms_returnsTrue() {
        Subscription oldSubscription = subscription.toBuilder().subscribeSms(NO).wantSmsNotifications(NO).build();
        assertTrue(NotificationService.hasCaseJustSubscribed(subscription, oldSubscription));
    }

    private NotificationService getNotificationService() {
        SendNotificationService sendNotificationService = new SendNotificationService(notificationSender, evidenceManagementService, notificationHandler, notificationValidService, pdfLetterService);

        final NotificationService notificationService = new NotificationService(factory, reminderService,
            notificationValidService, notificationHandler, outOfHoursCalculator, notificationConfig, sendNotificationService, false
        );
        return notificationService;
    }

    private CcdNotificationWrapper buildWrapperWithDocuments(NotificationEventType eventType, String fileUrl, Appellant appellant, Representative rep, String documentType) {

        SscsDocumentDetails sscsDocumentDetails = SscsDocumentDetails.builder()
            .documentType(documentType)
            .documentLink(
                DocumentLink.builder()
                    .documentUrl(fileUrl)
                    .documentFilename("direction-text.pdf")
                    .documentBinaryUrl(fileUrl + "/binary")
                    .build()
            )
            .build();

        SscsDocument sscsDocument = SscsDocument.builder().value(sscsDocumentDetails).build();

        return buildBaseWrapper(eventType, appellant, rep, sscsDocument);
    }

    private SubscriptionWithType getSubscriptionWithType(CcdNotificationWrapper ccdNotificationWrapper) {
        return new SubscriptionWithType(getSubscription(ccdNotificationWrapper.getNewSscsCaseData(), SubscriptionType.APPELLANT), SubscriptionType.APPELLANT);
    }

    private SubscriptionWithType getSubscriptionWithTypeJoint(CcdNotificationWrapper ccdNotificationWrapper) {
        return new SubscriptionWithType(getSubscription(ccdNotificationWrapper.getNewSscsCaseData(), JOINT_PARTY), JOINT_PARTY);
    }

    private SubscriptionWithType getSubscriptionWithTypeRep(CcdNotificationWrapper ccdNotificationWrapper) {
        return new SubscriptionWithType(getSubscription(ccdNotificationWrapper.getNewSscsCaseData(), REPRESENTATIVE), SubscriptionType.REPRESENTATIVE);
    }

    public static CcdNotificationWrapper buildBaseWrapper(NotificationEventType eventType, Appellant appellant, Representative rep, SscsDocument sscsDocument) {
        return buildBaseWrapperWithCaseData(getSscsCaseDataBuilder(appellant, rep, sscsDocument).build(), eventType);
    }

    public static CcdNotificationWrapper buildBaseWrapperJointParty(NotificationEventType eventType, Appellant appellant, JointPartyName jointPartyName, Address address, SscsDocument sscsDocument) {
        SscsCaseData sscsCaseData = getSscsCaseDataBuilder(appellant, null, sscsDocument)
                .jointParty(YES)
                .jointPartyName(jointPartyName)
                .jointPartyAddressSameAsAppellant(address == null ? "Yes" : "No")
                .jointPartyAddress(address)
                .build();
        CcdNotificationWrapper ccdNotificationWrapper = buildBaseWrapperWithCaseData(sscsCaseData, eventType);
        return ccdNotificationWrapper;
    }

    public static CcdNotificationWrapper buildBaseWrapperWithCaseData(SscsCaseData sscsCaseDataWithDocuments, NotificationEventType eventType) {
        SscsCaseDataWrapper caseDataWrapper = SscsCaseDataWrapper.builder()
            .newSscsCaseData(sscsCaseDataWithDocuments)
            .oldSscsCaseData(sscsCaseDataWithDocuments)
            .notificationEventType(eventType)
            .build();
        return new CcdNotificationWrapper(caseDataWrapper);
    }

    public static CcdNotificationWrapper buildBaseWrapperWithReasonableAdjustment() {
        SscsCaseData caseData = SscsCaseData.builder()
                .reasonableAdjustments(ReasonableAdjustments.builder()
                        .appellant(ReasonableAdjustmentDetails.builder().wantsReasonableAdjustment(YesNo.YES).build())
                        .appointee(ReasonableAdjustmentDetails.builder().wantsReasonableAdjustment(YesNo.YES).build())
                        .representative(ReasonableAdjustmentDetails.builder().wantsReasonableAdjustment(YesNo.YES).build())
                        .jointParty(ReasonableAdjustmentDetails.builder().wantsReasonableAdjustment(YesNo.YES).build())
                        .build()).build();
        SscsCaseDataWrapper caseDataWrapper = SscsCaseDataWrapper.builder()
                .newSscsCaseData(caseData)
                .oldSscsCaseData(caseData)
                .notificationEventType(APPEAL_RECEIVED_NOTIFICATION)
                .build();
        return new CcdNotificationWrapper(caseDataWrapper);
    }

    protected static SscsCaseData.SscsCaseDataBuilder getSscsCaseDataBuilder(Appellant appellant, Representative rep, SscsDocument sscsDocument) {
        return SscsCaseData.builder()
            .appeal(
                Appeal
                    .builder()
                    .benefitType(BenefitType.builder().code(Benefit.PIP.name()).description(Benefit.PIP.getDescription()).build())
                    .receivedVia("Online")
                    .hearingType(AppealHearingType.ORAL.name())
                    .hearingOptions(HearingOptions.builder().wantsToAttend(YES).build())
                    .appellant(appellant)
                    .rep(rep)
                    .build())
            .subscriptions(Subscriptions.builder()
                .appellantSubscription(Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeEmail(YES)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build()
                )
                .build())
            .caseReference(CASE_REFERENCE)
            .sscsInterlocDecisionDocument(SscsInterlocDecisionDocument.builder().documentLink(DocumentLink.builder().documentUrl("http://dm-store:4506/documents/1e1eb3d2-5b6c-430d-8dad-ebcea1ad7ecf")
                .documentFilename("test.pdf")
                .documentBinaryUrl("test/binary").build()).build())
            .sscsInterlocDirectionDocument(SscsInterlocDirectionDocument.builder().documentLink(DocumentLink.builder().documentUrl("http://dm-store:4506/documents/1e1eb3d2-5b6c-430d-8dad-ebcea1ad7ecf")
                .documentFilename("test.pdf")
                .documentBinaryUrl("test/binary").build()).build())
            .sscsStrikeOutDocument(SscsStrikeOutDocument.builder().documentLink(DocumentLink.builder().documentUrl("http://dm-store:4506/documents/1e1eb3d2-5b6c-430d-8dad-ebcea1ad7ecf")
                .documentFilename("test.pdf")
                .documentBinaryUrl("test/binary").build()).build())
            .ccdCaseId(CASE_ID)
            .hearings(emptyList())
            .sscsDocument(new ArrayList<>(singletonList(sscsDocument)));
    }

    protected static SscsCaseData.SscsCaseDataBuilder getSscsCaseDataBuilderSettingInformationFromAppellant(Appellant appellant, Representative rep, SscsDocument sscsDocument, String informationFromAppellant) {
        return SscsCaseData.builder()
            .appeal(
                Appeal
                    .builder()
                    .hearingType(AppealHearingType.ORAL.name())
                    .hearingOptions(HearingOptions.builder().wantsToAttend(YES).build())
                    .appellant(appellant)
                    .rep(rep)
                    .build())
            .subscriptions(Subscriptions.builder()
                .appellantSubscription(Subscription.builder()
                    .tya(APPEAL_NUMBER)
                    .email(EMAIL)
                    .mobile(MOBILE_NUMBER_1)
                    .subscribeEmail(YES)
                    .subscribeSms(YES).wantSmsNotifications(YES)
                    .build()
                )
                .build())
            .caseReference(CASE_REFERENCE)
            .sscsInterlocDecisionDocument(SscsInterlocDecisionDocument.builder().documentLink(DocumentLink.builder().documentUrl("http://dm-store:4506/documents/1e1eb3d2-5b6c-430d-8dad-ebcea1ad7ecf")
                .documentFilename("test.pdf")
                .documentBinaryUrl("test/binary").build()).build())
            .sscsInterlocDirectionDocument(SscsInterlocDirectionDocument.builder().documentLink(DocumentLink.builder().documentUrl("http://dm-store:4506/documents/1e1eb3d2-5b6c-430d-8dad-ebcea1ad7ecf")
                .documentFilename("test.pdf")
                .documentBinaryUrl("test/binary").build()).build())
            .sscsStrikeOutDocument(SscsStrikeOutDocument.builder().build().builder().documentLink(DocumentLink.builder().documentUrl("http://dm-store:4506/documents/1e1eb3d2-5b6c-430d-8dad-ebcea1ad7ecf")
                .documentFilename("test.pdf")
                .documentBinaryUrl("test/binary").build()).build())
            .ccdCaseId(CASE_ID)
            .sscsDocument(new ArrayList<>(singletonList(sscsDocument)))
            .informationFromAppellant(informationFromAppellant);
    }

    private void verifyExpectedLogErrorCount(Appender<ILoggingEvent> mockAppender, ArgumentCaptor captorLoggingEvent, int wantedNumberOfEmailNotificationsSent, int wantedNumberOfSmsNotificationsSent) {
        int expectedErrors = 0;
        if (wantedNumberOfEmailNotificationsSent > 0
            || wantedNumberOfSmsNotificationsSent > 0) {
            expectedErrors = 1;
        }
        verify(mockAppender, atLeast(expectedErrors)).doAppend(
            (ILoggingEvent) captorLoggingEvent.capture()
        );
        List<ILoggingEvent> logEvents = (List<ILoggingEvent>) captorLoggingEvent.getAllValues();
        if (expectedErrors == 0) {
            if (logEvents.isEmpty()) {
                assertEquals(logEvents.size(), expectedErrors);
            } else {
                assertFalse(logEvents.stream().noneMatch(e -> e.getLevel().equals(Level.ERROR)));
            }
        } else {
            assertTrue(logEvents.stream().noneMatch(e -> e.getLevel().equals(Level.ERROR)));
        }
    }

    protected static void verifyNoErrorsLogged(Appender<ILoggingEvent> mockAppender, ArgumentCaptor captorLoggingEvent) {
        verify(mockAppender, atLeast(0)).doAppend(
            (ILoggingEvent) captorLoggingEvent.capture()
        );
        List<ILoggingEvent> logEvents = (List<ILoggingEvent>) captorLoggingEvent.getAllValues();
        assertTrue(logEvents.stream().noneMatch(e -> e.getLevel().equals(Level.ERROR)));
    }

    protected static void verifyExpectedErrorLogMessage(Appender<ILoggingEvent> mockAppender, ArgumentCaptor captorLoggingEvent, String ccdCaseId, String errorMessage) {
        verify(mockAppender, atLeastOnce()).doAppend(
            (ILoggingEvent) captorLoggingEvent.capture()
        );
        List<ILoggingEvent> logEvents = (List<ILoggingEvent>) captorLoggingEvent.getAllValues();
        assertFalse(logEvents.stream().noneMatch(e -> e.getLevel().equals(Level.ERROR)));
        assertEquals(1, logEvents.stream().filter(logEvent -> logEvent.getFormattedMessage().contains(errorMessage)).count());
        assertTrue(logEvents.stream().filter(logEvent -> logEvent.getFormattedMessage().contains(ccdCaseId)).count() >= 1);
    }

    private static void verifyErrorLogMessageNotLogged(Appender<ILoggingEvent> mockAppender, ArgumentCaptor captorLoggingEvent, String errorText) {
        verify(mockAppender, atLeast(0)).doAppend(
            (ILoggingEvent) captorLoggingEvent.capture()
        );
        List<ILoggingEvent> logEvents = (List<ILoggingEvent>) captorLoggingEvent.getAllValues();
        assertEquals(0, logEvents.stream().filter(logEvent -> logEvent.getFormattedMessage().contains(errorText)).count());
    }
}
