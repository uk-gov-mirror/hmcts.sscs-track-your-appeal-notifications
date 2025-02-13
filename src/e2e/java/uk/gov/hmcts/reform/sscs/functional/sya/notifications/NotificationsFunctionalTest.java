package uk.gov.hmcts.reform.sscs.functional.sya.notifications;

import static org.junit.Assert.*;
import static uk.gov.hmcts.reform.sscs.domain.notify.NotificationEventType.*;
import static uk.gov.hmcts.reform.sscs.domain.notify.NotificationEventType.DWP_UPLOAD_RESPONSE_NOTIFICATION;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import junitparams.Parameters;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Value;
import uk.gov.hmcts.reform.sscs.ccd.domain.ReasonableAdjustmentStatus;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseDetails;
import uk.gov.hmcts.reform.sscs.ccd.domain.YesNo;
import uk.gov.hmcts.reform.sscs.functional.AbstractFunctionalTest;
import uk.gov.service.notify.Notification;
import uk.gov.service.notify.NotificationClientException;

public class NotificationsFunctionalTest extends AbstractFunctionalTest {

    private static final String AS_APPOINTEE_FOR = "You are receiving this update as the appointee for";
    private static final String RESPONSE_RECEIVED_PAPER_PATH = "paper/responseReceived/";
    private static final String DEAR_APPOINTEE_USER = "Dear Appointee User";
    private static final String APPEAL_ID = "appeal_id";
    private static final String TYA = "v8eg15XeZk";

    @Value("${track.appeal.link}")
    private String tyaLink;

    @Value("${notification.english.oral.evidenceReceived.appellant.emailId}")
    private String evidenceReceivedEmailTemplateId;

    @Value("${notification.english.oral.evidenceReceived.appellant.smsId}")
    private String evidenceReceivedSmsTemplateId;

    @Value("${notification.english.hearingPostponed.appellant.emailId}")
    private String hearingPostponedEmailTemplateId;

    @Value("${notification.english.hearingAdjourned.appellant.emailId}")
    private String hearingAdjournedEmailTemplateId;

    @Value("${notification.english.hearingAdjourned.appellant.smsId}")
    private String hearingAdjournedSmsTemplateId;

    @Value("${notification.english.subscriptionCreated.appellant.smsId}")
    private String subscriptionCreatedSmsTemplateId;

    @Value("${notification.english.subscriptionUpdated.emailId}")
    private String subscriptionUpdatedEmailTemplateId;

    @Value("${notification.english.paper.responseReceived.appellant.emailId}")
    private String paperResponseReceivedEmailId;

    @Value("${notification.english.paper.responseReceived.appellant.smsId}")
    private String paperResponseReceivedSmsId;

    @Value("${notification.english.subscriptionUpdated.emailId}")
    private String subscriptionUpdateEmailId;

    @Value("${notification.english.subscriptionUpdated.smsId}")
    private String subscriptionUpdateSmsId;

    @Value("${notification.english.subscriptionOld.emailId}")
    private String subscriptionUpdateOldEmailId;

    @Value("${notification.english.subscriptionOld.smsId}")
    private String subscriptionUpdateOldSmsId;

    @Value("${notification.english.paper.evidenceReceived.appointee.emailId}")
    private String paperAppointeeEvidenceReceivedEmailId;

    @Value("${notification.english.paper.evidenceReceived.appointee.smsId}")
    private String paperAppointeeEvidenceReceivedSmsId;

    @Value("${notification.english.paper.responseReceived.appointee.emailId}")
    private String paperAppointeeResponseReceivedEmailId;

    @Value("${notification.english.paper.responseReceived.appointee.smsId}")
    private String paperAppointeeResponseReceivedSmsId;

    @Value("${notification.english.oral.evidenceReminder.appointee.emailId}")
    private String oralAppointeeEvidenceReminderEmailId;

    @Value("${notification.english.oral.evidenceReminder.appointee.smsId}")
    private String oralAppointeeEvidenceReminderSmsId;

    @Value("${notification.english.paper.evidenceReminder.appointee.emailId}")
    private String paperAppointeeEvidenceReminderEmailId;

    @Value("${notification.english.paper.evidenceReminder.appointee.smsId}")
    private String paperAppointeeEvidenceReminderSmsId;

    @Value("${notification.english.appealCreated.appellant.smsId}")
    private String appealCreatedAppellantSmsId;

    @Value("${notification.english.appealCreated.appellant.emailId}")
    private String appealCreatedAppellantEmailId;

    @Value("${notification.english.appealCreated.appointee.smsId}")
    private String appealCreatedAppointeeSmsId;

    @Value("${notification.english.appealCreated.appointee.emailId}")
    private String appealCreatedAppointeeEmailId;

    @Value("${notification.english.hearingAdjourned.appointee.emailId}")
    private String hearingAdjournedAppointeeEmailId;

    @Value("${notification.english.hearingAdjourned.appointee.smsId}")
    private String hearingAdjournedAppointeeSmsId;

    @Value("${notification.english.appealLapsed.appointee.emailId}")
    private String appealLapsedAppointeeEmailTemplateId;

    @Value("${notification.english.appealLapsed.appointee.smsId}")
    private String appealLapsedAppointeeSmsTemplateId;

    @Value("${notification.english.appealWithdrawn.appointee.emailId}")
    private String appointeeAppealWithdrawnEmailId;

    @Value("${notification.english.appealWithdrawn.appointee.smsId}")
    private String appointeeAppealWithdrawnSmsId;

    @Value("${notification.english.hearingBooked.appointee.emailId}")
    private String appointeeHearingBookedEmailId;

    @Value("${notification.english.hearingBooked.appointee.smsId}")
    private String appointeeHearingBookedSmsId;

    @Value("${notification.english.oral.evidenceReceived.appellant.emailId}")
    private String appointeeEvidenceReceivedEmailId;

    @Value("${notification.english.oral.evidenceReceived.appellant.smsId}")
    private String appointeeEvidenceReceivedSmsId;

    @Value("${notification.english.hearingPostponed.appointee.emailId}")
    private String appointeeHearingPostponedEmailId;

    @Value("${notification.english.oral.dwpUploadResponse.appellant.emailId}")
    private String oralDwpUploadResponseEmailId;

    @Value("${notification.english.oral.dwpUploadResponse.appellant.smsId}")
    private String oralDwpUploadResponseSmsId;

    @Value("${notification.english.paper.dwpUploadResponse.appellant.emailId}")
    private String paperDwpUploadResponseEmailId;

    @Value("${notification.english.paper.dwpUploadResponse.appellant.smsId}")
    private String paperDwpUploadResponseSmsId;

    public NotificationsFunctionalTest() {
        super(30);
    }

    @Test
    public void shouldSendEvidenceReceivedNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(EVIDENCE_RECEIVED_NOTIFICATION);

        tryFetchNotificationsForTestCase(
                evidenceReceivedEmailTemplateId,
                evidenceReceivedSmsTemplateId
        );
    }


    @Test
    public void shouldSendHearingPostponedNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(POSTPONEMENT_NOTIFICATION);

        tryFetchNotificationsForTestCase(hearingPostponedEmailTemplateId);
    }

    @Test
    public void shouldSendHearingAdjournedNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(ADJOURNED_NOTIFICATION);

        tryFetchNotificationsForTestCase(
                hearingAdjournedEmailTemplateId,
                hearingAdjournedSmsTemplateId
        );
    }

    @Test
    public void shouldSendSubscriptionCreatedNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(SUBSCRIPTION_CREATED_NOTIFICATION);

        tryFetchNotificationsForTestCase(subscriptionCreatedSmsTemplateId);
    }

    @Test
    public void shouldSendSubscriptionUpdatedNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(SUBSCRIPTION_UPDATED_NOTIFICATION);

        tryFetchNotificationsForTestCase(subscriptionUpdatedEmailTemplateId);
    }

    @Test
    public void shouldSendAppealCreatedAppellantNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(SYA_APPEAL_CREATED_NOTIFICATION, SYA_APPEAL_CREATED_NOTIFICATION.getId() + "Callback.json");
        List<Notification> notifications = tryFetchNotificationsForTestCase(appealCreatedAppellantEmailId, appealCreatedAppellantSmsId);

        assertNotificationBodyContains(notifications, appealCreatedAppellantEmailId, "appeal has been received");
    }

    @Test
    public void shouldSendValidAppealCreatedAppellantNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(VALID_APPEAL_CREATED, SYA_APPEAL_CREATED_NOTIFICATION.getId() + "Callback.json");
        List<Notification> notifications = tryFetchNotificationsForTestCase(appealCreatedAppellantEmailId, appealCreatedAppellantSmsId);

        assertNotificationBodyContains(notifications, appealCreatedAppellantEmailId, "appeal has been received");
    }

    @Test
    public void shouldSendAppealCreatedAppointeeNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(SYA_APPEAL_CREATED_NOTIFICATION, SYA_APPEAL_CREATED_NOTIFICATION.getId() + "AppointeeCallback.json");
        List<Notification> notifications = tryFetchNotificationsForTestCase(appealCreatedAppointeeEmailId, appealCreatedAppointeeSmsId);

        assertNotificationBodyContains(notifications, appealCreatedAppointeeEmailId, "appointee");
    }

    @Test
    public void shouldSendValidAppealCreatedAppointeeNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(VALID_APPEAL_CREATED, SYA_APPEAL_CREATED_NOTIFICATION.getId() + "AppointeeCallback.json");
        List<Notification> notifications = tryFetchNotificationsForTestCase(appealCreatedAppointeeEmailId, appealCreatedAppointeeSmsId);

        assertNotificationBodyContains(notifications, appealCreatedAppointeeEmailId, "appointee");
    }

    @Test
    @Parameters({
            "pip,judge\\, doctor and disability expert",
            "esa,judge and a doctor",
            "dla,judge\\, doctor and disability expert",
            "carers-allowance,judge\\, doctor and disability expert"
    })
    public void shouldSendPaperDwpResponseReceivedNotification(final String benefit, String expectedPanelComposition)
            throws Exception {

        simulateCcdCallback(DWP_RESPONSE_RECEIVED_NOTIFICATION, RESPONSE_RECEIVED_PAPER_PATH + benefit + "-paper-"
                + DWP_RESPONSE_RECEIVED_NOTIFICATION.getId() + "Callback.json");
        List<Notification> notifications = tryFetchNotificationsForTestCase(
                paperResponseReceivedEmailId, paperResponseReceivedSmsId);

        String expectedHearingContactDate = "how long";
        String expectedTyaLink = tyaLink.replace(APPEAL_ID, TYA);
        assertNotificationBodyContains(notifications, paperResponseReceivedEmailId, caseData.getCaseReference(),
                expectedPanelComposition, expectedHearingContactDate, expectedTyaLink);
        assertNotificationBodyContains(notifications, paperResponseReceivedSmsId, expectedHearingContactDate);
    }

    @Test
    public void shouldNotSendPaperDwpResponseReceivedNotificationIfNotSubscribed() throws NotificationClientException, IOException {
        simulateCcdCallback(DWP_RESPONSE_RECEIVED_NOTIFICATION, RESPONSE_RECEIVED_PAPER_PATH + "paper-no-subscriptions-"
                + DWP_RESPONSE_RECEIVED_NOTIFICATION.getId() + "Callback.json");

        List<Notification> notifications = tryFetchNotificationsForTestCaseWithFlag(true,
                paperResponseReceivedEmailId, paperResponseReceivedSmsId);

        assertTrue(notifications.isEmpty());
    }

    @Test
    public void shouldSendAppointeeEvidenceReminderForOralCaseNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(EVIDENCE_REMINDER_NOTIFICATION,
                "appointee/oral-" + EVIDENCE_REMINDER_NOTIFICATION.getId() + "Callback.json");

        List<Notification> notifications = tryFetchNotificationsForTestCase(
                oralAppointeeEvidenceReminderEmailId,
                oralAppointeeEvidenceReminderSmsId
        );

        assertNotificationBodyContains(
                notifications,
                oralAppointeeEvidenceReminderEmailId,
                DEAR_APPOINTEE_USER,
                AS_APPOINTEE_FOR,
                "/evidence/" + TYA
        );
    }

    @Test
    public void shouldSendAppointeeEvidenceReminderForPaperCaseNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(EVIDENCE_REMINDER_NOTIFICATION,
                "appointee/paper-" + EVIDENCE_REMINDER_NOTIFICATION.getId() + "Callback.json");

        List<Notification> notifications = tryFetchNotificationsForTestCase(
                paperAppointeeEvidenceReminderEmailId,
                paperAppointeeEvidenceReminderSmsId
        );

        assertNotificationBodyContains(
                notifications,
                paperAppointeeEvidenceReminderEmailId,
                DEAR_APPOINTEE_USER,
                AS_APPOINTEE_FOR,
                "/evidence/" + TYA
        );
    }

    @Test
    public void shouldSendAppellantSubscriptionUpdateNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(SUBSCRIPTION_UPDATED_NOTIFICATION,
                "appellant-" + SUBSCRIPTION_UPDATED_NOTIFICATION.getId() + "Callback.json");

        List<Notification> notifications = tryFetchNotificationsForTestCase(
                subscriptionUpdateEmailId,
                subscriptionUpdateSmsId,
                subscriptionUpdateOldEmailId,
                subscriptionUpdateOldSmsId
        );

        Notification updateEmailNotification = notifications.stream().filter(f -> f.getTemplateId().toString().equals(subscriptionUpdatedEmailTemplateId)).collect(Collectors.toList()).get(0);
        assertTrue(updateEmailNotification.getBody().contains("Dear Appellant User\r\n\r\nEmails about your ESA"));
        assertFalse(updateEmailNotification.getBody().contains("You are receiving this update as the appointee for"));
    }

    @Test
    public void shouldSendAppointeeSubscriptionUpdateNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(SUBSCRIPTION_UPDATED_NOTIFICATION,
                "appointee-" + SUBSCRIPTION_UPDATED_NOTIFICATION.getId() + "Callback.json");

        List<Notification> notifications = tryFetchNotificationsForTestCase(
                subscriptionUpdateEmailId,
                subscriptionUpdateSmsId,
                subscriptionUpdateOldEmailId,
                subscriptionUpdateOldSmsId
        );
        Notification updateEmailNotification = notifications.stream().filter(f -> f.getTemplateId().toString().equals(subscriptionUpdatedEmailTemplateId)).collect(Collectors.toList()).get(0);
        assertTrue(updateEmailNotification.getBody().contains("Dear Appointee User\r\n\r\nYou are receiving this update as the appointee for Appellant User.\r\n\r\nEmails about your ESA"));
    }

    @Test
    public void shouldSendAppointeeEvidenceReceivedPaperNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(EVIDENCE_RECEIVED_NOTIFICATION,
                "appointee/paper-" + EVIDENCE_RECEIVED_NOTIFICATION.getId() + "Callback.json");

        List<Notification> notifications = tryFetchNotificationsForTestCase(
                paperAppointeeEvidenceReceivedEmailId,
                paperAppointeeEvidenceReceivedSmsId
        );
        Notification emailNotification = notifications.stream().filter(f -> f.getTemplateId().toString().equals(paperAppointeeEvidenceReceivedEmailId)).collect(Collectors.toList()).get(0);
        assertTrue(emailNotification.getBody().contains("Dear Appointee User"));
        assertTrue(emailNotification.getBody().contains("You are receiving this update as the appointee for Appellant User."));
    }

    @Test
    public void shouldSendAppointeeHearingAdjournedNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(ADJOURNED_NOTIFICATION,
                "appointee/" + ADJOURNED_NOTIFICATION.getId() + "Callback.json");

        List<Notification> notifications = tryFetchNotificationsForTestCase(
                hearingAdjournedAppointeeEmailId,
                hearingAdjournedAppointeeSmsId
        );
        Notification emailNotification = notifications.stream().filter(f -> f.getTemplateId().toString().equals(hearingAdjournedAppointeeEmailId)).collect(Collectors.toList()).get(0);
        assertTrue(emailNotification.getBody().contains("Dear Appointee User"));
    }

    @Test
    public void shouldSendAppointeeAppealLapsedNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(APPEAL_LAPSED_NOTIFICATION,
                "appointee/" + APPEAL_LAPSED_NOTIFICATION.getId() + "Callback.json");
        List<Notification> notifications = tryFetchNotificationsForTestCase(
                appealLapsedAppointeeEmailTemplateId,
                appealLapsedAppointeeSmsTemplateId
        );
        Notification emailNotification = notifications.stream().filter(f -> f.getTemplateId().toString().equals(appealLapsedAppointeeEmailTemplateId)).collect(Collectors.toList()).get(0);

        assertTrue(emailNotification.getBody().contains("Dear Appointee User"));
        assertTrue(emailNotification.getBody().contains("You are receiving this update as the appointee for"));

        List<Notification> notificationLetters = fetchLetters();
        assertEquals(1, notificationLetters.size());
        assertEquals("Pre-compiled PDF", notificationLetters.get(0).getSubject().orElse("Unknown Subject"));
    }

    @Test
    public void shouldSendAppointeeDwpAppealLapsedNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(APPEAL_LAPSED_NOTIFICATION,
                "appointee/dwpAppealLapsedCallback.json");
        List<Notification> notifications = tryFetchNotificationsForTestCase(
                appealLapsedAppointeeEmailTemplateId,
                appealLapsedAppointeeSmsTemplateId
        );
        Notification emailNotification = notifications.stream().filter(f -> f.getTemplateId().toString().equals(appealLapsedAppointeeEmailTemplateId)).collect(Collectors.toList()).get(0);

        assertTrue(emailNotification.getBody().contains("Dear Appointee User"));
        assertTrue(emailNotification.getBody().contains("You are receiving this update as the appointee for"));
    }

    @Test
    public void shouldSendAppointeeResponseReceivedForPaperCaseNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(DWP_RESPONSE_RECEIVED_NOTIFICATION,
                "appointee/" + DWP_RESPONSE_RECEIVED_NOTIFICATION.getId() + "Callback.json");

        List<Notification> notifications = tryFetchNotificationsForTestCase(
                paperAppointeeResponseReceivedEmailId,
                paperAppointeeResponseReceivedSmsId
        );
        Notification emailNotification = notifications.stream().filter(f -> f.getTemplateId().toString().equals(paperAppointeeResponseReceivedEmailId)).collect(Collectors.toList()).get(0);
        assertTrue(emailNotification.getBody().contains("Dear Appointee User"));
        assertTrue(emailNotification.getBody().contains("You should have received a copy"));
    }

    public void shouldSendAppointeeAppealWithdrawnNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(APPEAL_WITHDRAWN_NOTIFICATION,
                "appointee/" + APPEAL_WITHDRAWN_NOTIFICATION.getId() + "Callback.json");
        List<Notification> notifications = tryFetchNotificationsForTestCase(
                appointeeAppealWithdrawnEmailId, appointeeAppealWithdrawnSmsId);
        Notification emailNotification = notifications.stream()
                .filter(f -> f.getTemplateId().toString().equals(appointeeAppealWithdrawnEmailId))
                .collect(Collectors.toList()).get(0);
        assertTrue(emailNotification.getBody().contains("Dear Appointee User"));
        assertTrue(emailNotification.getBody().contains("You are receiving this update as the appointee for"));
    }

    @Test
    // Put back when covid19 feature turned off
    @Ignore
    public void shouldSendAppointeeHearingBookedNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(HEARING_BOOKED_NOTIFICATION,
                "appointee/" + HEARING_BOOKED_NOTIFICATION.getId() + "Callback.json");

        List<Notification> notifications = tryFetchNotificationsForTestCase(
                appointeeHearingBookedEmailId,
                appointeeHearingBookedSmsId
        );
        Notification emailNotification = notifications.stream().filter(f -> f.getTemplateId().toString().equals(appointeeHearingBookedEmailId)).collect(Collectors.toList()).get(0);
        assertTrue(emailNotification.getBody().contains("Dear Appointee User\r\n\r\nYou are receiving this update as the appointee for Appellant User.\r\n\r\n"));
    }

    @Test
    public void shouldSendAppointeeEvidenceReceivedNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(EVIDENCE_RECEIVED_NOTIFICATION,
                "appointee/" + EVIDENCE_RECEIVED_NOTIFICATION.getId() + "Callback.json");
        List<Notification> notifications = tryFetchNotificationsForTestCase(appointeeEvidenceReceivedEmailId, appointeeEvidenceReceivedSmsId);
        Notification emailNotification = notifications.stream().filter(f -> f.getTemplateId().toString().equals(appointeeEvidenceReceivedEmailId)).collect(Collectors.toList()).get(0);
        assertTrue(emailNotification.getBody().contains("Dear Appointee User"));
        assertTrue(emailNotification.getBody().contains("You are receiving this update as the appointee for"));
    }

    @Test
    public void shouldSendAppointeeHearingPostponedNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(POSTPONEMENT_NOTIFICATION,
                "appointee/" + POSTPONEMENT_NOTIFICATION.getId() + "Callback.json");
        List<Notification> notifications = tryFetchNotificationsForTestCase(appointeeHearingPostponedEmailId);
        Notification emailNotification = notifications.get(0);

        assertTrue(emailNotification.getBody().contains("Dear Appointee User"));
        assertTrue(emailNotification.getBody().contains("You will receive another email"));
    }

    @Test
    public void shouldSendOralDwpUploadResponseReceivedNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(DWP_UPLOAD_RESPONSE_NOTIFICATION, "oral-" + DWP_UPLOAD_RESPONSE_NOTIFICATION.getId() + "Callback.json");

        List<Notification> notifications = tryFetchNotificationsForTestCase(
                oralDwpUploadResponseEmailId,
                oralDwpUploadResponseSmsId);

        Notification updateEmailNotification = notifications.stream().filter(f -> f.getTemplateId().toString().equals(oralDwpUploadResponseEmailId)).collect(Collectors.toList()).get(0);

        assertTrue(updateEmailNotification.getBody().contains("DWP has sent a 'Response' to your ESA benefit appeal"));
        assertTrue(updateEmailNotification.getBody().contains("We need to book a hearing for your appeal"));
    }

    @Test
    public void shouldSendPaperDwpUploadResponseReceivedNotification() throws NotificationClientException, IOException {
        simulateCcdCallback(DWP_UPLOAD_RESPONSE_NOTIFICATION, "paper-" + DWP_UPLOAD_RESPONSE_NOTIFICATION.getId() + "Callback.json");

        List<Notification> notifications = tryFetchNotificationsForTestCase(
                paperDwpUploadResponseEmailId,
                paperDwpUploadResponseSmsId);

        Notification updateEmailNotification = notifications.stream().filter(f -> f.getTemplateId().toString().equals(paperDwpUploadResponseEmailId)).collect(Collectors.toList()).get(0);
        assertTrue(updateEmailNotification.getBody().contains("DWP has sent a 'Response' to your ESA benefit appeal"));
        assertTrue(updateEmailNotification.getBody().contains("You have told us you do not want to attend the hearing of your appeal"));
    }

    @Test
    public void shouldSaveReasonableAdjustmentNotificationForAppellant() throws IOException {
        simulateCcdCallback(APPEAL_RECEIVED_NOTIFICATION, APPEAL_RECEIVED_NOTIFICATION.getId() + "AppellantReasonableAdjustmentCallback.json");

        delayInSeconds(10);

        SscsCaseDetails caseDetails = findCaseById(caseId);
        SscsCaseData caseData = caseDetails.getData();

        assertEquals(YesNo.YES, caseData.getReasonableAdjustmentsOutstanding());
        assertEquals(1, caseData.getReasonableAdjustmentsLetters().getAppellant().size());
        assertEquals(ReasonableAdjustmentStatus.REQUIRED, caseData.getReasonableAdjustmentsLetters().getAppellant().get(0).getValue().getReasonableAdjustmentStatus());
    }

    @Test
    public void shouldSaveReasonableAdjustmentNotificationForAppellantAndRep() throws IOException {
        simulateCcdCallback(APPEAL_RECEIVED_NOTIFICATION, APPEAL_RECEIVED_NOTIFICATION.getId() + "AppellantRepReasonableAdjustmentCallback.json");

        delayInSeconds(10);

        SscsCaseDetails caseDetails = findCaseById(caseId);
        SscsCaseData caseData = caseDetails.getData();

        assertEquals(YesNo.YES, caseData.getReasonableAdjustmentsOutstanding());
        assertEquals(1, caseData.getReasonableAdjustmentsLetters().getAppellant().size());
        assertEquals(1, caseData.getReasonableAdjustmentsLetters().getRepresentative().size());
        assertEquals(ReasonableAdjustmentStatus.REQUIRED, caseData.getReasonableAdjustmentsLetters().getAppellant().get(0).getValue().getReasonableAdjustmentStatus());
        assertEquals(ReasonableAdjustmentStatus.REQUIRED, caseData.getReasonableAdjustmentsLetters().getRepresentative().get(0).getValue().getReasonableAdjustmentStatus());
    }

}
