package uk.gov.hmcts.reform.sscs.tya;

import static helper.IntegrationTestHelper.*;
import static org.mockito.Mockito.*;
import static uk.gov.hmcts.reform.sscs.domain.notify.NotificationEventType.*;

import helper.IntegrationTestHelper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import javax.servlet.http.HttpServletResponse;
import junitparams.NamedParameters;
import junitparams.Parameters;
import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.io.IOUtils;
import org.junit.Test;
import org.quartz.SchedulerException;
import org.springframework.http.HttpStatus;
import uk.gov.hmcts.reform.sscs.ccd.domain.DatedRequestOutcome;
import uk.gov.hmcts.reform.sscs.ccd.domain.RequestOutcome;
import uk.gov.hmcts.reform.sscs.ccd.domain.State;
import uk.gov.hmcts.reform.sscs.domain.notify.NotificationEventType;

public class NotificationsIt extends NotificationsItBase {

    @Test
    public void shouldSendNotificationForAnAdjournedRequestForAnOralHearing() throws Exception {
        json = json.replace("appealReceived", "hearingAdjourned");

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient).sendEmail(any(), any(), any(), any());
        verify(notificationClient, times(2)).sendSms(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldNotSendNotificationForAnAdjournedRequestForAPaperHearing() throws Exception {
        updateJsonForPaperHearing();
        json = json.replace("appealReceived", "hearingAdjourned");

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient, never()).sendEmail(any(), any(), any(), any());
        verify(notificationClient, never()).sendSms(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldSendNotificationForAnEvidenceReceivedRequestForAnOralHearing() throws Exception {
        json = json.replace("appealReceived", "evidenceReceived");

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient).sendEmail(any(), any(), any(), any());
        verify(notificationClient, times(2)).sendSms(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldSendEmailNotificationOnlyForAnEvidenceReceivedRequestToAnAppellantForAPaperHearing() throws Exception {
        updateJsonForPaperHearing();
        json = json.replace("appealReceived", "evidenceReceived");
        json = updateEmbeddedJson(json, "No", "case_details", "case_data", "subscriptions", "representativeSubscription", "subscribeSms");
        json = updateEmbeddedJson(json, "No", "case_details", "case_data", "subscriptions", "representativeSubscription", "subscribeEmail");

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient, times(1)).sendEmail(any(), any(), any(), any());
        verify(notificationClient, times(1)).sendSms(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldSendNotificationForAHearingPostponedRequestForAnOralHearing() throws Exception {
        json = json.replace("appealReceived", "hearingPostponed");

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient).sendEmail(any(), any(), any(), any());
        verify(notificationClient, never()).sendSms(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldNotSendNotificationForAHearingPostponedRequestForAPaperHearing() throws Exception {
        updateJsonForPaperHearing();
        json = json.replace("appealReceived", "hearingPostponed");

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient, never()).sendEmail(any(), any(), any(), any());
        verify(notificationClient, never()).sendSms(any(), any(), any(), any(), any());
    }

    @Test
    @Parameters(method = "generateDelayedNotificationScenarios")
    public void shouldScheduleDelayedNotificationsForAnEvent(
            NotificationEventType notificationEventType, String message, int expectedValue) throws Exception {

        try {
            quartzScheduler.clear();
        } catch (SchedulerException e) {
            throw new RuntimeException(e);
        }

        json = updateEmbeddedJson(json, notificationEventType.getId(), "event_id");
        json = updateEmbeddedJson(json, LocalDate.now().toString(), "case_details", "case_data", "caseCreated");

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));
        assertHttpStatus(response, HttpStatus.OK);

        IntegrationTestHelper.assertScheduledJobCount(quartzScheduler, message, notificationEventType.getId(), expectedValue);
    }

    @Test
    @Parameters(method = "generateRepsNotificationScenarios")
    public void shouldSendRepsNotificationsForAnEventForAnOralOrPaperHearingAndForEachSubscription(
        NotificationEventType notificationEventType, String hearingType, List<String> expectedEmailTemplateIds,
        List<String> expectedSmsTemplateIds, List<String> expectedLetterTemplateIds, String appellantEmailSubs, String appellantSmsSubs, String repsEmailSubs,
        String repsSmsSubs, int wantedNumberOfSendEmailInvocations, int wantedNumberOfSendSmsInvocations, int wantedNumberOfSendLetterInvocations) throws Exception {
        json = updateEmbeddedJson(json, hearingType, "case_details", "case_data", "appeal", "hearingType");
        json = updateEmbeddedJson(json, appellantEmailSubs, "case_details", "case_data", "subscriptions",
            "appellantSubscription", "subscribeEmail");
        json = updateEmbeddedJson(json, appellantSmsSubs, "case_details", "case_data", "subscriptions",
            "appellantSubscription", "subscribeSms");
        json = updateEmbeddedJson(json, repsEmailSubs, "case_details", "case_data", "subscriptions",
            "representativeSubscription", "subscribeEmail");
        json = updateEmbeddedJson(json, repsSmsSubs, "case_details", "case_data", "subscriptions",
            "representativeSubscription", "subscribeSms");
        json = updateEmbeddedJson(json, notificationEventType.getId(), "event_id");
        json = updateCommonJsonData(notificationEventType, json);
        if (notificationEventType.equals(REQUEST_INFO_INCOMPLETE)) {
            json = updateEmbeddedJson(json, "Yes", "case_details", "case_data", "informationFromAppellant");
        }

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));
        assertHttpStatus(response, HttpStatus.OK);

        String expectedName = "Harry Potter";
        validateEmailNotifications(expectedEmailTemplateIds, wantedNumberOfSendEmailInvocations, expectedName);
        validateSmsNotifications(expectedSmsTemplateIds, wantedNumberOfSendSmsInvocations);
        validateLetterNotifications(expectedLetterTemplateIds, wantedNumberOfSendLetterInvocations, expectedName);
    }

    @Test
    @Parameters(method = "generateBundledLetterNotificationScenarios")
    public void shouldSendRepsBundledLetterNotificationsForAnEventForAnOralOrPaperHearingAndForEachSubscription(
        NotificationEventType notificationEventType, String hearingType, boolean hasRep, boolean hasAppointee, int wantedNumberOfSendLetterInvocations) throws Exception {

        byte[] sampleDirectionNotice = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("pdfs/direction-text.pdf"));
        when(evidenceManagementService.download(any(), any())).thenReturn(sampleDirectionNotice);

        String filename = "json/ccdResponse_"
            + notificationEventType.getId()
            + (hasRep ? "_withRep" : "")
            + (hasAppointee ? "_withAppointee" : "")
            + ".json";
        String path = getClass().getClassLoader().getResource(filename).getFile();
        String json = FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8.name());

        json = updateEmbeddedJson(json, hearingType, "case_details", "case_data", "appeal", "hearingType");

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));
        assertHttpStatus(response, HttpStatus.OK);

        verify(notificationClient, times(wantedNumberOfSendLetterInvocations)).sendPrecompiledLetterWithInputStream(any(), any());
    }

    @Test
    @Parameters(method = "generateRepsNotificationScenariosWhenNoOldCaseRef")
    public void shouldSendRepsNotificationsForAnEventForAnOralOrPaperHearingAndForEachSubscriptionWhenNoOldCaseRef(
        NotificationEventType notificationEventType, String hearingType, List<String> expectedEmailTemplateIds,
        List<String> expectedSmsTemplateIds, List<String> expectedLetterTemplateIds, String appellantEmailSubs, String appellantSmsSubs, String repsEmailSubs,
        String repsSmsSubs, int wantedNumberOfSendEmailInvocations, int wantedNumberOfSendSmsInvocations, int wantedNumberOfSendLetterInvocations) throws Exception {
        String path = getClass().getClassLoader().getResource("json/ccdResponseWithNoOldCaseRef.json").getFile();
        String json = FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8.name());

        json = updateEmbeddedJson(json, hearingType, "case_details", "case_data", "appeal", "hearingType");
        json = updateEmbeddedJson(json, appellantEmailSubs, "case_details", "case_data", "subscriptions",
            "appellantSubscription", "subscribeEmail");
        json = updateEmbeddedJson(json, appellantSmsSubs, "case_details", "case_data", "subscriptions",
            "appellantSubscription", "subscribeSms");
        json = updateEmbeddedJson(json, repsEmailSubs, "case_details", "case_data", "subscriptions",
            "representativeSubscription", "subscribeEmail");
        json = updateEmbeddedJson(json, repsSmsSubs, "case_details", "case_data", "subscriptions",
            "representativeSubscription", "subscribeSms");
        json = updateEmbeddedJson(json, notificationEventType.getId(), "event_id");
        json = updateCommonJsonData(notificationEventType, json);
        if (notificationEventType.equals(REQUEST_INFO_INCOMPLETE)) {
            json = updateEmbeddedJson(json, "Yes", "case_details", "case_data", "informationFromAppellant");
        }

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));
        assertHttpStatus(response, HttpStatus.OK);

        String expectedName = "Harry Potter";
        validateEmailNotifications(expectedEmailTemplateIds, wantedNumberOfSendEmailInvocations, expectedName);
        validateSmsNotifications(expectedSmsTemplateIds, wantedNumberOfSendSmsInvocations);
        validateLetterNotifications(expectedLetterTemplateIds, wantedNumberOfSendLetterInvocations, expectedName);
    }

    @Test
    @Parameters(method = "generateAppointeeNotificationScenarios")
    @SuppressWarnings("unchecked")
    public void shouldSendAppointeeNotificationsForAnEventForAnOralOrPaperHearingAndForEachSubscription(
        NotificationEventType notificationEventType, String hearingType, List<String> expectedEmailTemplateIds,
        List<String> expectedSmsTemplateIds, List<String> expectedLetterTemplateIds, String appointeeEmailSubs,
        String appointeeSmsSubs, int wantedNumberOfSendEmailInvocations, int wantedNumberOfSendSmsInvocations,
        int wantedNumberOfSendLetterInvocations, String expectedName) throws Exception {

        String path = getClass().getClassLoader().getResource("json/ccdResponseWithAppointee.json").getFile();
        String jsonAppointee = FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8.name());
        jsonAppointee = updateEmbeddedJson(jsonAppointee, hearingType, "case_details", "case_data", "appeal", "hearingType");

        jsonAppointee = updateCommonJsonData(notificationEventType, jsonAppointee);
        jsonAppointee = updateEmbeddedJson(jsonAppointee, appointeeEmailSubs, "case_details", "case_data", "subscriptions",
            "appointeeSubscription", "subscribeEmail");
        jsonAppointee = updateEmbeddedJson(jsonAppointee, appointeeSmsSubs, "case_details", "case_data", "subscriptions",
            "appointeeSubscription", "subscribeSms");

        if (notificationEventType.equals(HEARING_BOOKED_NOTIFICATION)) {
            jsonAppointee = jsonAppointee.replace("appealReceived", "hearingBooked");
            jsonAppointee = jsonAppointee.replace("2018-01-12", LocalDate.now().plusDays(2).toString());
        }

        if (notificationEventType.equals(REQUEST_INFO_INCOMPLETE)) {
            jsonAppointee = updateEmbeddedJson(jsonAppointee, "Yes", "case_details", "case_data", "informationFromAppellant");
        }

        jsonAppointee = updateEmbeddedJson(jsonAppointee, notificationEventType.getId(), "event_id");

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(jsonAppointee));

        assertHttpStatus(response, HttpStatus.OK);

        validateEmailNotifications(expectedEmailTemplateIds, wantedNumberOfSendEmailInvocations, expectedName);
        validateSmsNotifications(expectedSmsTemplateIds, wantedNumberOfSendSmsInvocations);
        validateLetterNotifications(expectedLetterTemplateIds, wantedNumberOfSendLetterInvocations, expectedName);
    }

    private String updateCommonJsonData(final NotificationEventType notificationEventType, String json) throws IOException {
        json = updateJsonDataForWelshNotifications(notificationEventType, json);
        return updateJsonDataForProcessAudioVideoEvent(notificationEventType, json);
    }

    private String updateJsonDataForProcessAudioVideoEvent(final NotificationEventType notificationEventType, String json) throws IOException {
        if (notificationEventType.equals(PROCESS_AUDIO_VIDEO)) {
            Map<String, Object> map = new HashMap<>();
            Map<String, String> mapValue = new HashMap<>();
            mapValue.put("code", "includeEvidence");
            map.put("value", mapValue);
            json = updateEmbeddedJson(json, map, "case_details", "case_data", "processAudioVideoAction");
        }
        return json;
    }

    private String updateJsonDataForWelshNotifications(final NotificationEventType notificationEventType, String json) throws IOException {
        if (notificationEventType.getId().contains("Welsh")) {
            json = updateEmbeddedJson(json, "Yes", "case_details", "case_data", "languagePreferenceWelsh");
        }
        return json;
    }

    @Test
    @Parameters(method = "generateAppointeeNotificationWhenNoOldCaseReferenceScenarios")
    @SuppressWarnings("unchecked")
    public void shouldSendAppointeeNotificationsForAnEventForAnOralOrPaperHearingAndForEachSubscriptionWhenNoOldCaseReference(
        NotificationEventType notificationEventType,
        String hearingType,
        List<String> expectedEmailTemplateIds,
        List<String> expectedSmsTemplateIds,
        List<String> expectedLetterTemplateIds,
        String appointeeEmailSubs,
        String appointeeSmsSubs,
        int wantedNumberOfSendEmailInvocations,
        int wantedNumberOfSendSmsInvocations,
        int wantedNumberOfSendLetterInvocations,
        String expectedName) throws Exception {

        String path = Objects.requireNonNull(getClass().getClassLoader()
            .getResource("json/ccdResponseWithAppointeeWithNoOldCaseRef.json")).getFile();
        String jsonAppointee = FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8.name());
        jsonAppointee = updateEmbeddedJson(jsonAppointee, hearingType, "case_details", "case_data", "appeal", "hearingType");

        jsonAppointee = updateEmbeddedJson(jsonAppointee, appointeeEmailSubs, "case_details", "case_data", "subscriptions",
            "appointeeSubscription", "subscribeEmail");
        jsonAppointee = updateEmbeddedJson(jsonAppointee, appointeeSmsSubs, "case_details", "case_data", "subscriptions",
            "appointeeSubscription", "subscribeSms");

        if (notificationEventType.equals(REQUEST_INFO_INCOMPLETE)) {
            jsonAppointee = updateEmbeddedJson(jsonAppointee, "Yes", "case_details", "case_data", "informationFromAppellant");
        }

        jsonAppointee = updateEmbeddedJson(jsonAppointee, notificationEventType.getId(), "event_id");

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(jsonAppointee));

        assertHttpStatus(response, HttpStatus.OK);

        validateEmailNotifications(expectedEmailTemplateIds, wantedNumberOfSendEmailInvocations, expectedName);
        validateSmsNotifications(expectedSmsTemplateIds, wantedNumberOfSendSmsInvocations);
        validateLetterNotifications(expectedLetterTemplateIds, wantedNumberOfSendLetterInvocations, expectedName);
    }

    @Test
    @Parameters(method = "generateJointPartyNotificationScenarios")
    public void shouldSendJointPartyNotificationsForAnEventForAnOralOrPaperHearingAndForEachSubscription(
            NotificationEventType notificationEventType, String hearingType, List<String> expectedEmailTemplateIds,
            List<String> expectedSmsTemplateIds, List<String> expectedLetterTemplateIds, String jointPartyEmailSubs,
            String jointPartySmsSubs, int wantedNumberOfSendEmailInvocations, int wantedNumberOfSendSmsInvocations, int wantedNumberOfSendLetterInvocations) throws Exception {
        String path = getClass().getClassLoader().getResource("json/ccdResponseWithJointParty.json").getFile();
        String json = FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8.name());

        json = updateEmbeddedJson(json, hearingType, "case_details", "case_data", "appeal", "hearingType");
        json = updateEmbeddedJson(json, "no", "case_details", "case_data", "subscriptions",
                "appellantSubscription", "subscribeEmail");
        json = updateEmbeddedJson(json, "no", "case_details", "case_data", "subscriptions",
                "appellantSubscription", "subscribeSms");
        json = updateEmbeddedJson(json, jointPartyEmailSubs, "case_details", "case_data", "subscriptions",
                "jointPartySubscription", "subscribeEmail");
        json = updateEmbeddedJson(json, jointPartySmsSubs, "case_details", "case_data", "subscriptions",
                "jointPartySubscription", "subscribeSms");
        json = updateEmbeddedJson(json, notificationEventType.getId(), "event_id");

        json = updateCommonJsonData(notificationEventType, json);
        if (notificationEventType.equals(REQUEST_INFO_INCOMPLETE)) {
            json = updateEmbeddedJson(json, "Yes", "case_details", "case_data", "informationFromAppellant");
        }

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));
        assertHttpStatus(response, HttpStatus.OK);

        String expectedName = "Joint Party";
        validateEmailNotifications(expectedEmailTemplateIds, wantedNumberOfSendEmailInvocations, expectedName);
        validateSmsNotifications(expectedSmsTemplateIds, wantedNumberOfSendSmsInvocations);
        validateLetterNotifications(expectedLetterTemplateIds, wantedNumberOfSendLetterInvocations, expectedName);
    }



    @SuppressWarnings({"Indentation", "unused"})
    private Object[] generateJointPartyNotificationScenarios() {
        return new Object[]{
               new Object[]{
                        APPEAL_LAPSED_NOTIFICATION,
                        "paper",
                        Collections.singletonList("8ce8d794-75e8-49a0-b4d2-0c6cd2061c11"),
                        Collections.singletonList("d2b4394b-d1c9-4d5c-a44e-b382e41c67e5"),
                        Arrays.asList("TB-SCS-GNO-ENG-00656.docx", "TB-SCS-GNO-ENG-00656.docx"),
                        "yes",
                        "yes",
                        "1",
                        "1",
                        "0"
                },
                new Object[]{
                        APPEAL_LAPSED_NOTIFICATION,
                        "oral",
                        Collections.singletonList("8ce8d794-75e8-49a0-b4d2-0c6cd2061c11"),
                        Collections.singletonList("d2b4394b-d1c9-4d5c-a44e-b382e41c67e5"),
                        Arrays.asList("TB-SCS-GNO-ENG-00656.docx", "TB-SCS-GNO-ENG-00656.docx"),
                        "yes",
                        "yes",
                        "1",
                        "1",
                        "0"
                },
                new Object[]{
                        APPEAL_LAPSED_NOTIFICATION,
                        "paper",
                        Collections.emptyList(),
                        Collections.singletonList("d2b4394b-d1c9-4d5c-a44e-b382e41c67e5"),
                        Arrays.asList("TB-SCS-GNO-ENG-00656.docx", "TB-SCS-GNO-ENG-00656.docx"),
                        "no",
                        "yes",
                        "0",
                        "1",
                        "0"
                },
                new Object[]{
                        APPEAL_LAPSED_NOTIFICATION,
                        "paper",
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Arrays.asList("TB-SCS-GNO-ENG-00656.docx", "TB-SCS-GNO-ENG-00656.docx"),
                        "no",
                        "no",
                        "0",
                        "0",
                        "0"
                },
                new Object[]{
                        APPEAL_DORMANT_NOTIFICATION,
                        "paper",
                        Collections.singletonList("976bdb6c-8a86-48cf-9e0f-7989acaec0c2"),
                        Collections.singletonList("8b459c7d-c7b9-4293-9734-26341a231695"),
                        Collections.emptyList(),
                        "yes",
                        "yes",
                        "1",
                        "1",
                        "0"
                },
                new Object[]{
                        APPEAL_DORMANT_NOTIFICATION,
                        "oral",
                        Collections.singletonList("1a2683d0-ca0f-4465-b25d-59d3d817750a"),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        "yes",
                        "yes",
                        "1",
                        "0",
                        "0"
                },
                new Object[]{
                        EVIDENCE_REMINDER_NOTIFICATION,
                        "paper",
                        Collections.singletonList("c507a630-9e6a-43c9-8e39-dcabdcffaf53"),
                        Collections.singletonList("56a6c0c8-a251-482d-be83-95a7a1bf528c"),
                        Collections.emptyList(),
                        "yes",
                        "yes",
                        "1",
                        "1",
                        "0"
                },
                new Object[]{
                        EVIDENCE_REMINDER_NOTIFICATION,
                        "oral",
                        Collections.singletonList("d994236b-d7c4-44ef-9627-12372bb0434a"),
                        Collections.singletonList("7d36718b-1193-4b3d-86bd-db54612c5363"),
                        Collections.emptyList(),
                        "yes",
                        "yes",
                        "1",
                        "1",
                        "0"
                },
                new Object[]{
                        ADJOURNED_NOTIFICATION,
                        "paper",
                        Collections.singletonList("77ea995b-9744-4167-9250-e627c85e5eda"),
                        Collections.singletonList("7455de19-aa3b-48f0-b765-ab2757ba6a88"),
                        Collections.emptyList(),
                        "yes",
                        "yes",
                        "1",
                        "1",
                        "0"
                },
                new Object[]{
                        ADJOURNED_NOTIFICATION,
                        "oral",
                        Collections.singletonList("77ea995b-9744-4167-9250-e627c85e5eda"),
                        Collections.singletonList("7455de19-aa3b-48f0-b765-ab2757ba6a88"),
                        Collections.emptyList(),
                        "yes",
                        "yes",
                        "1",
                        "1",
                        "0"
                },
                new Object[]{
                        POSTPONEMENT_NOTIFICATION,
                        "paper",
                        Collections.singletonList("732ec1a2-243f-4047-b963-e8427cb007b8"),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        "yes",
                        "yes",
                        "1",
                        "0",
                        "0"
                },
                new Object[]{
                        POSTPONEMENT_NOTIFICATION,
                        "oral",
                        Collections.singletonList("732ec1a2-243f-4047-b963-e8427cb007b8"),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        "yes",
                        "yes",
                        "1",
                        "0",
                        "0"
                },
                new Object[]{
                        EVIDENCE_RECEIVED_NOTIFICATION,
                        "paper",
                        Collections.singletonList("8509fb1b-eb15-449f-b4ee-15ce286ab404"),
                        Collections.singletonList("e7868511-3a1f-4b8e-8bb3-b36c2bd99799"),
                        Collections.emptyList(),
                        "yes",
                        "yes",
                        "1",
                        "1",
                        "0"
                },
                new Object[]{
                        EVIDENCE_RECEIVED_NOTIFICATION,
                        "oral",
                        Collections.singletonList("bd78cbc4-27d3-4692-a491-6c1770df174e"),
                        Collections.singletonList("e7868511-3a1f-4b8e-8bb3-b36c2bd99799"),
                        Collections.emptyList(),
                        "yes",
                        "yes",
                        "1",
                        "1",
                        "0"
                },
                new Object[]{
                        HEARING_BOOKED_NOTIFICATION,
                        "oral",
                        Collections.singletonList("aa0930a3-e1bd-4b50-ac6b-34df73ec8378"),
                        Collections.singletonList("8aa77a9c-9bc6-424d-8716-1c948681270e"),
                        Collections.emptyList(),
                        "yes",
                        "yes",
                        "1",
                        "1",
                        "0"
                },
                new Object[]{
                        HEARING_BOOKED_NOTIFICATION,
                        "paper",
                        Collections.singletonList("aa0930a3-e1bd-4b50-ac6b-34df73ec8378"),
                        Collections.singletonList("8aa77a9c-9bc6-424d-8716-1c948681270e"),
                        Collections.emptyList(),
                        "yes",
                        "yes",
                        "1",
                        "1",
                        "0"
                },
                new Object[]{
                        HEARING_REMINDER_NOTIFICATION,
                        "oral",
                        Collections.singletonList("07bebee4-f07a-4a0d-9c50-65be30dc72a5"),
                        Collections.singletonList("18960596-1983-4da8-8b5c-dc1c851bb19b"),
                        Collections.emptyList(),
                        "yes",
                        "yes",
                        "1",
                        "1",
                        "0"
                },
                new Object[]{
                        HEARING_REMINDER_NOTIFICATION,
                        "paper",
                        Collections.singletonList("07bebee4-f07a-4a0d-9c50-65be30dc72a5"),
                        Collections.singletonList("18960596-1983-4da8-8b5c-dc1c851bb19b"),
                        Collections.emptyList(),
                        "yes",
                        "yes",
                        "1",
                        "1",
                        "0"
                },
                new Object[]{
                        APPEAL_WITHDRAWN_NOTIFICATION,
                        "paper",
                        Collections.singletonList("6ce5e7b0-b94f-4f6e-878b-012ec0ee17d1"),
                        Collections.singletonList("c4db4fca-6876-4130-b4eb-09e900ae45a8"),
                        Arrays.asList("TB-SCS-GNO-ENG-00659.docx", "TB-SCS-GNO-ENG-00659.docx"),
                        "yes",
                        "yes",
                        "1",
                        "1",
                        "0"
                },
                new Object[]{
                        APPEAL_WITHDRAWN_NOTIFICATION,
                        "oral",
                        Collections.singletonList("6ce5e7b0-b94f-4f6e-878b-012ec0ee17d1"),
                        Collections.singletonList("c4db4fca-6876-4130-b4eb-09e900ae45a8"),
                        Arrays.asList("TB-SCS-GNO-ENG-00659.docx", "TB-SCS-GNO-ENG-00659.docx"),
                        "yes",
                        "yes",
                        "1",
                        "1",
                        "0"
                },
                new Object[]{
                        PROCESS_AUDIO_VIDEO,
                        "paper",
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Arrays.asList("TB-SCS-GNO-ENG-00677.docx", "TB-SCS-GNO-ENG-00677.docx"),
                        "yes",
                        "yes",
                        "0",
                        "0",
                        "0"
                },
                new Object[]{
                        PROCESS_AUDIO_VIDEO_WELSH,
                        "paper",
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Arrays.asList("TB-SCS-GNO-ENG-00677.docx", "TB-SCS-GNO-ENG-00677.docx"),
                        "yes",
                        "yes",
                        "0",
                        "0",
                        "0"
                },
                new Object[]{
                        DIRECTION_ISSUED,
                        "paper",
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Arrays.asList("TB-SCS-GNO-ENG-00067.docx", "TB-SCS-GNO-ENG-00067.docx"),
                        "yes",
                        "yes",
                        "0",
                        "0",
                        "0"
                },
                new Object[]{
                        DIRECTION_ISSUED,
                        "oral",
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Arrays.asList("TB-SCS-GNO-ENG-00067.docx", "TB-SCS-GNO-ENG-00067.docx"),
                        "yes",
                        "yes",
                        "0",
                        "0",
                        "0"
                },
                new Object[]{
                        STRUCK_OUT,
                        "oral",
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Arrays.asList("TB-SCS-GNO-ENG-00466.docx", "TB-SCS-GNO-ENG-00466.docx"),
                        "yes",
                        "yes",
                        "0",
                        "0",
                        "0"
                },
                new Object[]{
                        REQUEST_INFO_INCOMPLETE,
                        "paper",
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Arrays.asList("TB-SCS-GNO-ENG-00452.docx", "TB-SCS-GNO-ENG-00452.docx"),
                        "yes",
                        "yes",
                        "0",
                        "0",
                        "0"
                },
                new Object[]{
                        REQUEST_INFO_INCOMPLETE,
                        "oral",
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Arrays.asList("TB-SCS-GNO-ENG-00452.docx", "TB-SCS-GNO-ENG-00452.docx"),
                        "yes",
                        "yes",
                        "0",
                        "0",
                        "0"
                },
                new Object[]{
                        DWP_UPLOAD_RESPONSE_NOTIFICATION,
                        "paper",
                        Collections.singletonList("8c4770ca-13c9-49ea-9df1-f2952030f95e"),
                        Collections.singletonList("15cd6837-e998-4bf9-a815-af3e98922d19"),
                        Arrays.asList("TB-SCS-GNO-ENG-00261.docx", "TB-SCS-GNO-ENG-00261.docx"),
                        "yes",
                        "yes",
                        "1",
                        "1",
                        "0"
                },
                new Object[]{
                    DWP_UPLOAD_RESPONSE_NOTIFICATION,
                    "oral",
                    Collections.singletonList("ffa58120-24e4-44cb-8026-0becf1416684"),
                    Collections.singletonList("f0444380-a8a4-4805-b9c2-563d1bd199cd"),
                    Arrays.asList("TB-SCS-GNO-ENG-00261.docx", "TB-SCS-GNO-WEL-00478.docx"),
                    "yes",
                    "yes",
                    "1",
                    "1",
                    "0"
                },
                new Object[] {
                    DWP_UPLOAD_RESPONSE_NOTIFICATION,
                    "oral",
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Arrays.asList("TB-SCS-GNO-ENG-00261.docx", "TB-SCS-GNO-WEL-00478.docx"),
                    "no",
                    "no",
                    "0",
                    "0",
                    "0"
                },
                new Object[]{
                    JOINT_PARTY_ADDED,
                    "paper",
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Arrays.asList("TB-SCS-GNO-ENG-00579.docx", "TB-SCS-GNO-ENG-00579.docx"),
                    "yes",
                    "yes",
                    "0",
                    "0",
                    "0"
                },
                new Object[]{
                    ISSUE_ADJOURNMENT_NOTICE,
                    "paper",
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Arrays.asList("TB-SCS-GNO-ENG-00510.docx", "TB-SCS-GNO-ENG-00510.docx"),
                    "yes",
                    "yes",
                    "0",
                    "0",
                    "0"
                },
                new Object[]{
                    ISSUE_ADJOURNMENT_NOTICE_WELSH,
                    "oral",
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Arrays.asList("TB-SCS-GNO-WEL-00649.docx", "TB-SCS-GNO-WEL-00649.docx"),
                    "yes",
                    "yes",
                    "0",
                    "0",
                    "0"
                }
        };
    }

    @SuppressWarnings({"Indentation", "unused"})
    private Object[] generateRepsNotificationScenarios() {
        return new Object[]{
            new Object[]{
                    STRUCK_OUT,
                    "paper",
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Arrays.asList("TB-SCS-GNO-ENG-00466.docx", "TB-SCS-GNO-ENG-00466.docx"),
                    "no",
                    "no",
                    "no",
                    "no",
                    "0",
                    "0",
                    "0"
            },
            new Object[]{
                EVIDENCE_RECEIVED_NOTIFICATION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                EVIDENCE_RECEIVED_NOTIFICATION,
                "paper",
                Arrays.asList("7af36950-fc63-45d1-907d-f472fac7af06"),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "yes",
                "no",
                "1",
                "0",
                "0"
            },
            new Object[]{
                EVIDENCE_RECEIVED_NOTIFICATION,
                "paper",
                Arrays.asList("8509fb1b-eb15-449f-b4ee-15ce286ab404", "7af36950-fc63-45d1-907d-f472fac7af06"),
                Collections.emptyList(),
                Collections.emptyList(),
                "yes",
                "no",
                "yes",
                "no",
                "2",
                "0",
                "0"
            },
            new Object[]{
                EVIDENCE_RECEIVED_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                EVIDENCE_RECEIVED_NOTIFICATION,
                "oral",
                Arrays.asList("30260c0b-5575-4f4e-bce4-73cf3f245c2d"),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "yes",
                "no",
                "1",
                "0",
                "0"
            },
            new Object[]{
                EVIDENCE_RECEIVED_NOTIFICATION,
                "oral",
                Arrays.asList("bd78cbc4-27d3-4692-a491-6c1770df174e", "30260c0b-5575-4f4e-bce4-73cf3f245c2d"),
                Collections.emptyList(),
                Collections.emptyList(),
                "yes",
                "no",
                "yes",
                "no",
                "2",
                "0",
                "0"
            },
            new Object[]{
                APPEAL_LAPSED_NOTIFICATION,
                "paper",
                Arrays.asList("8ce8d794-75e8-49a0-b4d2-0c6cd2061c11", "e93dd744-84a1-4173-847a-6d023b55637f"),
                Arrays.asList("d2b4394b-d1c9-4d5c-a44e-b382e41c67e5", "ee58f7d0-8de7-4bee-acd4-252213db6b7b"),
                Arrays.asList("TB-SCS-GNO-ENG-00656.docx", "TB-SCS-GNO-ENG-00656.docx"),
                "yes",
                "yes",
                "yes",
                "yes",
                "2",
                "2",
                "0"
            },
            new Object[]{
                APPEAL_LAPSED_NOTIFICATION,
                "oral",
                Arrays.asList("8ce8d794-75e8-49a0-b4d2-0c6cd2061c11", "e93dd744-84a1-4173-847a-6d023b55637f"),
                Arrays.asList("d2b4394b-d1c9-4d5c-a44e-b382e41c67e5", "ee58f7d0-8de7-4bee-acd4-252213db6b7b"),
                Arrays.asList("TB-SCS-GNO-ENG-00656.docx", "TB-SCS-GNO-ENG-00656.docx"),
                "yes",
                "yes",
                "yes",
                "yes",
                "2",
                "2",
                "0"
            },
            new Object[]{
                APPEAL_LAPSED_NOTIFICATION,
                "paper",
                Collections.singletonList("e93dd744-84a1-4173-847a-6d023b55637f"),
                Arrays.asList("d2b4394b-d1c9-4d5c-a44e-b382e41c67e5", "ee58f7d0-8de7-4bee-acd4-252213db6b7b"),
                Arrays.asList("TB-SCS-GNO-ENG-00656.docx", "TB-SCS-GNO-ENG-00656.docx"),
                "no",
                "yes",
                "yes",
                "yes",
                "1",
                "2",
                "0"
            },
            new Object[]{
                APPEAL_LAPSED_NOTIFICATION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-ENG-00656.docx", "TB-SCS-GNO-ENG-00656.docx"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                APPEAL_WITHDRAWN_NOTIFICATION,
                "paper",
                Arrays.asList("8620e023-f663-477e-a771-9cfad50ee30f", "e29a2275-553f-4e70-97f4-2994c095f281"),
                Arrays.asList("446c7b23-7342-42e1-adff-b4c367e951cb", "f59440ee-19ca-4d47-a702-13e9cecaccbd"),
                Arrays.asList("TB-SCS-GNO-ENG-00659.docx", "TB-SCS-GNO-ENG-00659.docx"),
                "yes",
                "yes",
                "yes",
                "yes",
                "2",
                "2",
                "0"
            },
            new Object[]{
                APPEAL_WITHDRAWN_NOTIFICATION,
                "oral",
                Arrays.asList("8620e023-f663-477e-a771-9cfad50ee30f", "e29a2275-553f-4e70-97f4-2994c095f281"),
                Arrays.asList("446c7b23-7342-42e1-adff-b4c367e951cb", "f59440ee-19ca-4d47-a702-13e9cecaccbd"),
                Arrays.asList("TB-SCS-GNO-ENG-00659.docx", "TB-SCS-GNO-ENG-00659.docx"),
                "yes",
                "yes",
                "yes",
                "yes",
                "2",
                "2",
                "0"
            },
            new Object[]{
                APPEAL_WITHDRAWN_NOTIFICATION,
                "paper",
                Collections.singletonList("e29a2275-553f-4e70-97f4-2994c095f281"),
                Arrays.asList("446c7b23-7342-42e1-adff-b4c367e951cb", "f59440ee-19ca-4d47-a702-13e9cecaccbd"),
                Arrays.asList("TB-SCS-GNO-ENG-00659.docx", "TB-SCS-GNO-ENG-00659.docx"),
                "no",
                "yes",
                "yes",
                "yes",
                "1",
                "2",
                "0"
            },
            new Object[]{
                APPEAL_WITHDRAWN_NOTIFICATION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-ENG-00659.docx", "TB-SCS-GNO-ENG-00659.docx"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                ADMIN_APPEAL_WITHDRAWN,
                "paper",
                Collections.singletonList("e29a2275-553f-4e70-97f4-2994c095f281"),
                Arrays.asList("446c7b23-7342-42e1-adff-b4c367e951cb", "f59440ee-19ca-4d47-a702-13e9cecaccbd"),
                Arrays.asList("TB-SCS-GNO-ENG-00659.docx", "TB-SCS-GNO-ENG-00659.docx"),
                "no",
                "yes",
                "yes",
                "yes",
                "1",
                "2",
                "0"
            },
            new Object[]{
                ADJOURNED_NOTIFICATION,
                "paper",
                Arrays.asList("77ea995b-9744-4167-9250-e627c85e5eda", "ecf7db7d-a257-4496-a2bf-768e560c80e7"),
                Arrays.asList("7455de19-aa3b-48f0-b765-ab2757ba6a88", "259b8e81-b44a-4271-a57b-ba7f8bdfcb33"),
                Collections.emptyList(),
                "yes",
                "yes",
                "yes",
                "yes",
                "2",
                "2",
                "0"
            },
            new Object[]{
                ADJOURNED_NOTIFICATION,
                "oral",
                Arrays.asList("77ea995b-9744-4167-9250-e627c85e5eda", "ecf7db7d-a257-4496-a2bf-768e560c80e7"),
                Arrays.asList("7455de19-aa3b-48f0-b765-ab2757ba6a88", "259b8e81-b44a-4271-a57b-ba7f8bdfcb33"),
                Collections.emptyList(),
                "yes",
                "yes",
                "yes",
                "yes",
                "2",
                "2",
                "0"
            },
            new Object[]{
                ADJOURNED_NOTIFICATION,
                "paper",
                Collections.singletonList("ecf7db7d-a257-4496-a2bf-768e560c80e7"),
                Arrays.asList("7455de19-aa3b-48f0-b765-ab2757ba6a88", "259b8e81-b44a-4271-a57b-ba7f8bdfcb33"),
                Collections.emptyList(),
                "no",
                "yes",
                "yes",
                "yes",
                "1",
                "2",
                "0"
            },
            new Object[]{
                ADJOURNED_NOTIFICATION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                APPEAL_DORMANT_NOTIFICATION,
                "paper",
                Arrays.asList("976bdb6c-8a86-48cf-9e0f-7989acaec0c2", "b74ea5d4-dba2-4148-b822-d102cedbea12"),
                Arrays.asList("8b459c7d-c7b9-4293-9734-26341a231695", "4562984e-2854-4191-81d9-cffbe5111015"),
                Collections.emptyList(),
                "yes",
                "yes",
                "yes",
                "yes",
                "2",
                "2",
                "0"
            },
            new Object[]{
                APPEAL_DORMANT_NOTIFICATION,
                "paper",
                Collections.singletonList("b74ea5d4-dba2-4148-b822-d102cedbea12"),
                Arrays.asList("8b459c7d-c7b9-4293-9734-26341a231695", "4562984e-2854-4191-81d9-cffbe5111015"),
                Collections.emptyList(),
                "no",
                "yes",
                "yes",
                "yes",
                "1",
                "2",
                "0"
            },
            new Object[]{
                APPEAL_DORMANT_NOTIFICATION,
                "oral",
                Arrays.asList("1a2683d0-ca0f-4465-b25d-59d3d817750a", "e2ee8609-7d56-4857-b3f8-79028e8960aa"),
                Collections.emptyList(),
                Collections.emptyList(),
                "yes",
                "no",
                "yes",
                "no",
                "2",
                "0",
                "0"
            },
            new Object[]{
                APPEAL_DORMANT_NOTIFICATION,
                "oral",
                Collections.singletonList("e2ee8609-7d56-4857-b3f8-79028e8960aa"),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "yes",
                "no",
                "1",
                "0",
                "0"
            },
            new Object[]{
                APPEAL_DORMANT_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                "paper",
                Arrays.asList("01293b93-b23e-40a3-ad78-2c6cd01cd21c", "652753bf-59b4-46eb-9c24-bd762338a098"),
                Arrays.asList("f41222ef-c05c-4682-9634-6b034a166368", "a6c09fad-6265-4c7c-8b95-36245ffa5352"),
                Collections.emptyList(),
                "yes",
                "yes",
                "yes",
                "yes",
                "2",
                "2",
                "0"
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                "oral",
                Arrays.asList("01293b93-b23e-40a3-ad78-2c6cd01cd21c", "652753bf-59b4-46eb-9c24-bd762338a098"),
                Arrays.asList("f41222ef-c05c-4682-9634-6b034a166368", "a6c09fad-6265-4c7c-8b95-36245ffa5352"),
                Collections.emptyList(),
                "yes",
                "yes",
                "yes",
                "yes",
                "2",
                "2",
                "0"
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                "paper",
                Collections.singletonList("652753bf-59b4-46eb-9c24-bd762338a098"),
                Arrays.asList("f41222ef-c05c-4682-9634-6b034a166368", "a6c09fad-6265-4c7c-8b95-36245ffa5352"),
                Collections.emptyList(),
                "no",
                "yes",
                "yes",
                "yes",
                "1",
                "2",
                "0"
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                POSTPONEMENT_NOTIFICATION,
                "paper",
                Arrays.asList("732ec1a2-243f-4047-b963-e8427cb007b8", "e07b7dba-f383-49ca-a0ba-b5b61be27da6"),
                Collections.emptyList(),
                Collections.emptyList(),
                "yes",
                "no",
                "yes",
                "no",
                "2",
                "0",
                "0"
            },
            new Object[]{
                POSTPONEMENT_NOTIFICATION,
                "oral",
                Arrays.asList("732ec1a2-243f-4047-b963-e8427cb007b8", "e07b7dba-f383-49ca-a0ba-b5b61be27da6"),
                Collections.emptyList(),
                Collections.emptyList(),
                "yes",
                "no",
                "Yes",
                "no",
                "2",
                "0",
                "0"
            },
            new Object[]{
                POSTPONEMENT_NOTIFICATION,
                "paper",
                Collections.singletonList("e07b7dba-f383-49ca-a0ba-b5b61be27da6"),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "yes",
                "no",
                "1",
                "0",
                "0"
            },
            new Object[]{
                POSTPONEMENT_NOTIFICATION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                CASE_UPDATED,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "yes",
                "no",
                "yes",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                CASE_UPDATED,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "yes",
                "no",
                "Yes",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                CASE_UPDATED,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "yes",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                CASE_UPDATED,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "yes",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                CASE_UPDATED,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                EVIDENCE_REMINDER_NOTIFICATION,
                "oral",
                Arrays.asList("df0803aa-f804-49fe-a2ac-c27adc4bb585"),
                Arrays.asList("5f91012e-0d3f-465b-b301-ee3ee5a50100"),
                Collections.emptyList(),
                "no",
                "no",
                "yes",
                "yes",
                "1",
                "1",
                "0"
            },
            new Object[]{
                EVIDENCE_REMINDER_NOTIFICATION,
                "paper",
                Arrays.asList("81fa38cc-b7cc-469c-8109-67c801dc9c84"),
                Arrays.asList("f1076482-a76d-4389-b411-9865373cfc42"),
                Collections.emptyList(),
                "no",
                "no",
                "yes",
                "yes",
                "1",
                "1",
                "0"
            },
            new Object[]{
                REQUEST_INFO_INCOMPLETE,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-ENG-00452.docx", "TB-SCS-GNO-ENG-00452.docx"),
                "yes",
                "yes",
                "yes",
                "yes",
                "0",
                "0",
                "0"
            },
            new Object[]{
                REQUEST_INFO_INCOMPLETE,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-ENG-00452.docx", "TB-SCS-GNO-ENG-00452.docx"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                RESEND_APPEAL_CREATED_NOTIFICATION,
                "paper",
                Arrays.asList("01293b93-b23e-40a3-ad78-2c6cd01cd21c", "652753bf-59b4-46eb-9c24-bd762338a098"),
                Arrays.asList("f41222ef-c05c-4682-9634-6b034a166368", "a6c09fad-6265-4c7c-8b95-36245ffa5352"),
                Collections.emptyList(),
                "yes",
                "yes",
                "yes",
                "yes",
                "2",
                "2",
                "0"
            },
            new Object[]{
                RESEND_APPEAL_CREATED_NOTIFICATION,
                "oral",
                Arrays.asList("01293b93-b23e-40a3-ad78-2c6cd01cd21c", "652753bf-59b4-46eb-9c24-bd762338a098"),
                Arrays.asList("f41222ef-c05c-4682-9634-6b034a166368", "a6c09fad-6265-4c7c-8b95-36245ffa5352"),
                Collections.emptyList(),
                "yes",
                "yes",
                "yes",
                "yes",
                "2",
                "2",
                "0"
            },
            new Object[]{
                RESEND_APPEAL_CREATED_NOTIFICATION,
                "paper",
                Collections.singletonList("652753bf-59b4-46eb-9c24-bd762338a098"),
                Arrays.asList("f41222ef-c05c-4682-9634-6b034a166368", "a6c09fad-6265-4c7c-8b95-36245ffa5352"),
                Collections.emptyList(),
                "no",
                "yes",
                "yes",
                "yes",
                "1",
                "2",
                "0"
            },
            new Object[]{
                RESEND_APPEAL_CREATED_NOTIFICATION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                NON_COMPLIANT_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-WEL-00663.docx", "TB-SCS-GNO-WEL-00663.docx"),
                "yes",
                "yes",
                "yes",
                "yes",
                "0",
                "0",
                "0"
            },
            new Object[]{
                NON_COMPLIANT_NOTIFICATION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-WEL-00663.docx", "TB-SCS-GNO-WEL-00663.docx"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                DRAFT_TO_NON_COMPLIANT_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-WEL-00663.docx", "TB-SCS-GNO-WEL-00663.docx"),
                "yes",
                "yes",
                "yes",
                "yes",
                "0",
                "0",
                "0"
            },
            new Object[]{
                DRAFT_TO_NON_COMPLIANT_NOTIFICATION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-WEL-00663.docx", "TB-SCS-GNO-WEL-00663.docx"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                PROCESS_AUDIO_VIDEO,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-ENG-00677.docx", "TB-SCS-GNO-ENG-00677.docx"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                PROCESS_AUDIO_VIDEO_WELSH,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-ENG-00677.docx", "TB-SCS-GNO-ENG-00677.docx"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                DIRECTION_ISSUED,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-ENG-00067.docx", "TB-SCS-GNO-ENG-00089.docx"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                DECISION_ISSUED,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-ENG-00067.docx", "TB-SCS-GNO-ENG-00089.docx"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                ISSUE_FINAL_DECISION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-ENG-00067.docx", "TB-SCS-GNO-ENG-00089.docx"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                ISSUE_FINAL_DECISION_WELSH,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-ENG-00067.docx", "TB-SCS-GNO-ENG-00089.docx"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                ISSUE_ADJOURNMENT_NOTICE,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-ENG-00067.docx", "TB-SCS-GNO-ENG-00089.docx"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                    ISSUE_ADJOURNMENT_NOTICE_WELSH,
                    "paper",
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Arrays.asList("TB-SCS-GNO-ENG-00067.docx", "TB-SCS-GNO-ENG-00089.docx"),
                    "no",
                    "no",
                    "no",
                    "no",
                    "0",
                    "0",
                    "0"
            },
        };
    }

    private Object[] generateDelayedNotificationScenarios() {
        return new Object[]{
            new Object[]{
                APPEAL_RECEIVED_NOTIFICATION,
                "Appeal received scheduled",
                1
            },
            new Object[]{
                VALID_APPEAL_CREATED,
                "valid appeal created scheduled",
                1
            },
            new Object[]{
                DRAFT_TO_VALID_APPEAL_CREATED,
                "draft to valid appeal created scheduled",
                1
            },
        };
    }

    @SuppressWarnings({"Indentation", "unused"})
    private Object[] generateBundledLetterNotificationScenarios() {
        return new Object[]{
            new Object[]{
                    PROCESS_AUDIO_VIDEO,
                    "paper",
                    false,
                    false,
                    "1"
            },
            new Object[]{
                    PROCESS_AUDIO_VIDEO,
                    "oral",
                    false,
                    false,
                    "1"
            },
            new Object[]{
                    PROCESS_AUDIO_VIDEO_WELSH,
                    "paper",
                    false,
                    false,
                    "1"
            },
            new Object[]{
                    PROCESS_AUDIO_VIDEO_WELSH,
                    "oral",
                    false,
                    false,
                    "1"
            },
            new Object[]{
                    STRUCK_OUT,
                    "paper",
                    false,
                    false,
                    "1"
            },
            new Object[]{
                    STRUCK_OUT,
                    "oral",
                    false,
                    false,
                    "1"
            },
            new Object[]{
                DIRECTION_ISSUED,
                "paper",
                false,
                false,
                "1"
            },
            new Object[]{
                DIRECTION_ISSUED,
                "oral",
                false,
                false,
                "1"
            },
            new Object[]{
                DIRECTION_ISSUED,
                "oral",
                false,
                true,
                "1"
            },
            new Object[]{
                DIRECTION_ISSUED,
                "paper",
                false,
                true,
                "1"
            },
            new Object[]{
                DIRECTION_ISSUED,
                "paper",
                true,
                false,
                "2"
            },
            new Object[]{
                DIRECTION_ISSUED,
                "oral",
                true,
                false,
                "2"
            },
            new Object[]{
                DIRECTION_ISSUED,
                "paper",
                true,
                true,
                "2"
            },
            new Object[]{
                DIRECTION_ISSUED,
                "oral",
                true,
                true,
                "2"
            },
            new Object[]{
                DECISION_ISSUED,
                "paper",
                false,
                false,
                "1"
            },
            new Object[]{
                DECISION_ISSUED,
                "oral",
                false,
                false,
                "1"
            },
            new Object[]{
                DECISION_ISSUED,
                "oral",
                false,
                true,
                "1"
            },
            new Object[]{
                DECISION_ISSUED,
                "paper",
                false,
                true,
                "1"
            },
            new Object[]{
                DECISION_ISSUED,
                "paper",
                true,
                false,
                "2"
            },
            new Object[]{
                DECISION_ISSUED,
                "oral",
                true,
                false,
                "2"
            },
            new Object[]{
                DECISION_ISSUED,
                "paper",
                true,
                true,
                "2"
            },
            new Object[]{
                DECISION_ISSUED,
                "oral",
                true,
                true,
                "2"
            },
            new Object[]{
                ISSUE_FINAL_DECISION,
                "paper",
                false,
                false,
                "1"
            },
            new Object[]{
                ISSUE_FINAL_DECISION,
                "oral",
                false,
                false,
                "1"
            },
            new Object[]{
                ISSUE_FINAL_DECISION,
                "oral",
                false,
                true,
                "1"
            },
            new Object[]{
                ISSUE_FINAL_DECISION,
                "paper",
                false,
                true,
                "1"
            },
            new Object[]{
                ISSUE_FINAL_DECISION,
                "paper",
                true,
                false,
                "2"
            },
            new Object[]{
                ISSUE_FINAL_DECISION,
                "oral",
                true,
                false,
                "2"
            },
            new Object[]{
                ISSUE_FINAL_DECISION,
                "paper",
                true,
                true,
                "2"
            },
            new Object[]{
                ISSUE_FINAL_DECISION,
                "oral",
                true,
                true,
                "2"
            },
            new Object[]{
                ISSUE_FINAL_DECISION_WELSH,
                "paper",
                false,
                false,
                "1"
            },
            new Object[]{
                ISSUE_FINAL_DECISION_WELSH,
                "oral",
                false,
                false,
                "1"
            },
            new Object[]{
                ISSUE_ADJOURNMENT_NOTICE,
                "paper",
                false,
                false,
                "1"
            },
            new Object[]{
                ISSUE_ADJOURNMENT_NOTICE,
                "oral",
                false,
                false,
                "1"
            },
            new Object[]{
                ISSUE_ADJOURNMENT_NOTICE,
                "oral",
                false,
                true,
                "1"
            },
            new Object[]{
                ISSUE_ADJOURNMENT_NOTICE,
                "paper",
                false,
                true,
                "1"
            },
            new Object[]{
                ISSUE_ADJOURNMENT_NOTICE,
                "paper",
                true,
                false,
                "2"
            },
            new Object[]{
                ISSUE_ADJOURNMENT_NOTICE,
                "oral",
                true,
                false,
                "2"
            },
            new Object[]{
                ISSUE_ADJOURNMENT_NOTICE,
                "paper",
                true,
                true,
                "2"
            },
            new Object[]{
                ISSUE_ADJOURNMENT_NOTICE,
                "oral",
                true,
                true,
                "2"
            },
            new Object[]{
                ISSUE_ADJOURNMENT_NOTICE_WELSH,
                "paper",
                false,
                false,
                "1"
            },
            new Object[]{
                ISSUE_ADJOURNMENT_NOTICE_WELSH,
                "oral",
                false,
                false,
                "1"
            },
            new Object[]{
                ISSUE_ADJOURNMENT_NOTICE_WELSH,
                "oral",
                false,
                true,
                "1"
            },
            new Object[]{
                ISSUE_ADJOURNMENT_NOTICE_WELSH,
                "paper",
                false,
                true,
                "1"
            },
            new Object[]{
                ISSUE_ADJOURNMENT_NOTICE_WELSH,
                "paper",
                true,
                false,
                "2"
            },
            new Object[]{
                ISSUE_ADJOURNMENT_NOTICE_WELSH,
                "oral",
                true,
                false,
                "2"
            },
            new Object[]{
                ISSUE_ADJOURNMENT_NOTICE_WELSH,
                "paper",
                true,
                true,
                "2"
            },
            new Object[]{
                ISSUE_ADJOURNMENT_NOTICE_WELSH,
                "oral",
                true,
                true,
                "2"
            }
        };
    }

    @SuppressWarnings({"Indentation", "unused"})
    private Object[] generateRepsNotificationScenariosWhenNoOldCaseRef() {
        return new Object[]{
            new Object[]{
                EVIDENCE_RECEIVED_NOTIFICATION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                EVIDENCE_RECEIVED_NOTIFICATION,
                "paper",
                Arrays.asList("8509fb1b-eb15-449f-b4ee-15ce286ab404", "7af36950-fc63-45d1-907d-f472fac7af06"),
                Collections.emptyList(),
                Collections.emptyList(),
                "yes",
                "no",
                "yes",
                "no",
                "2",
                "0",
                "0"
            },
            new Object[]{
                APPEAL_LAPSED_NOTIFICATION,
                "paper",
                Arrays.asList("8ce8d794-75e8-49a0-b4d2-0c6cd2061c11", "e93dd744-84a1-4173-847a-6d023b55637f"),
                Arrays.asList("d2b4394b-d1c9-4d5c-a44e-b382e41c67e5", "ee58f7d0-8de7-4bee-acd4-252213db6b7b"),
                Arrays.asList("TB-SCS-GNO-ENG-00656.docx", "TB-SCS-GNO-ENG-00656.docx"),
                "yes",
                "yes",
                "yes",
                "yes",
                "2",
                "2",
                "0"
            },
            new Object[]{
                APPEAL_LAPSED_NOTIFICATION,
                "oral",
                Arrays.asList("8ce8d794-75e8-49a0-b4d2-0c6cd2061c11", "e93dd744-84a1-4173-847a-6d023b55637f"),
                Arrays.asList("d2b4394b-d1c9-4d5c-a44e-b382e41c67e5", "ee58f7d0-8de7-4bee-acd4-252213db6b7b"),
                Arrays.asList("TB-SCS-GNO-ENG-00656.docx", "TB-SCS-GNO-ENG-00656.docx"),
                "yes",
                "yes",
                "yes",
                "yes",
                "2",
                "2",
                "0"
            },
            new Object[]{
                APPEAL_LAPSED_NOTIFICATION,
                "paper",
                Collections.singletonList("e93dd744-84a1-4173-847a-6d023b55637f"),
                Arrays.asList("d2b4394b-d1c9-4d5c-a44e-b382e41c67e5", "ee58f7d0-8de7-4bee-acd4-252213db6b7b"),
                Arrays.asList("TB-SCS-GNO-ENG-00656.docx", "TB-SCS-GNO-ENG-00656.docx"),
                "no",
                "yes",
                "yes",
                "yes",
                "1",
                "2",
                "0"
            },
            new Object[]{
                APPEAL_LAPSED_NOTIFICATION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-ENG-00656.docx", "TB-SCS-GNO-ENG-00656.docx"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                APPEAL_WITHDRAWN_NOTIFICATION,
                "paper",
                Arrays.asList("8620e023-f663-477e-a771-9cfad50ee30f", "e29a2275-553f-4e70-97f4-2994c095f281"),
                Arrays.asList("446c7b23-7342-42e1-adff-b4c367e951cb", "f59440ee-19ca-4d47-a702-13e9cecaccbd"),
                Arrays.asList("TB-SCS-GNO-ENG-00659.docx", "TB-SCS-GNO-ENG-00659.docx"),
                "yes",
                "yes",
                "yes",
                "yes",
                "2",
                "2",
                "0"
            },
            new Object[]{
                APPEAL_WITHDRAWN_NOTIFICATION,
                "oral",
                Arrays.asList("8620e023-f663-477e-a771-9cfad50ee30f", "e29a2275-553f-4e70-97f4-2994c095f281"),
                Arrays.asList("446c7b23-7342-42e1-adff-b4c367e951cb", "f59440ee-19ca-4d47-a702-13e9cecaccbd"),
                Arrays.asList("TB-SCS-GNO-ENG-00659.docx", "TB-SCS-GNO-ENG-00659.docx"),
                "yes",
                "yes",
                "yes",
                "yes",
                "2",
                "2",
                "0"
            },
            new Object[]{
                APPEAL_WITHDRAWN_NOTIFICATION,
                "paper",
                Collections.singletonList("e29a2275-553f-4e70-97f4-2994c095f281"),
                Arrays.asList("446c7b23-7342-42e1-adff-b4c367e951cb", "f59440ee-19ca-4d47-a702-13e9cecaccbd"),
                Arrays.asList("TB-SCS-GNO-ENG-00659.docx", "TB-SCS-GNO-ENG-00659.docx"),
                "no",
                "yes",
                "yes",
                "yes",
                "1",
                "2",
                "0"
            },
            new Object[]{
                APPEAL_WITHDRAWN_NOTIFICATION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-ENG-00659.docx", "TB-SCS-GNO-ENG-00659.docx"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                ADJOURNED_NOTIFICATION,
                "paper",
                Arrays.asList("77ea995b-9744-4167-9250-e627c85e5eda", "ecf7db7d-a257-4496-a2bf-768e560c80e7"),
                Arrays.asList("7455de19-aa3b-48f0-b765-ab2757ba6a88", "259b8e81-b44a-4271-a57b-ba7f8bdfcb33"),
                Collections.emptyList(),
                "yes",
                "yes",
                "yes",
                "yes",
                "2",
                "2",
                "0"
            },
            new Object[]{
                ADJOURNED_NOTIFICATION,
                "oral",
                Arrays.asList("77ea995b-9744-4167-9250-e627c85e5eda", "ecf7db7d-a257-4496-a2bf-768e560c80e7"),
                Arrays.asList("7455de19-aa3b-48f0-b765-ab2757ba6a88", "259b8e81-b44a-4271-a57b-ba7f8bdfcb33"),
                Collections.emptyList(),
                "yes",
                "yes",
                "yes",
                "yes",
                "2",
                "2",
                "0"
            },
            new Object[]{
                ADJOURNED_NOTIFICATION,
                "paper",
                Collections.singletonList("ecf7db7d-a257-4496-a2bf-768e560c80e7"),
                Arrays.asList("7455de19-aa3b-48f0-b765-ab2757ba6a88", "259b8e81-b44a-4271-a57b-ba7f8bdfcb33"),
                Collections.emptyList(),
                "no",
                "yes",
                "yes",
                "yes",
                "1",
                "2",
                "0"
            },
            new Object[]{
                ADJOURNED_NOTIFICATION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                APPEAL_DORMANT_NOTIFICATION,
                "paper",
                Arrays.asList("976bdb6c-8a86-48cf-9e0f-7989acaec0c2", "b74ea5d4-dba2-4148-b822-d102cedbea12"),
                Arrays.asList("8b459c7d-c7b9-4293-9734-26341a231695", "4562984e-2854-4191-81d9-cffbe5111015"),
                Collections.emptyList(),
                "yes",
                "yes",
                "yes",
                "yes",
                "2",
                "2",
                "0"
            },
            new Object[]{
                APPEAL_DORMANT_NOTIFICATION,
                "paper",
                Collections.singletonList("b74ea5d4-dba2-4148-b822-d102cedbea12"),
                Arrays.asList("8b459c7d-c7b9-4293-9734-26341a231695", "4562984e-2854-4191-81d9-cffbe5111015"),
                Collections.emptyList(),
                "no",
                "yes",
                "yes",
                "yes",
                "1",
                "2",
                "0"
            },
            new Object[]{
                APPEAL_DORMANT_NOTIFICATION,
                "oral",
                Arrays.asList("1a2683d0-ca0f-4465-b25d-59d3d817750a", "e2ee8609-7d56-4857-b3f8-79028e8960aa"),
                Collections.emptyList(),
                Collections.emptyList(),
                "yes",
                "no",
                "yes",
                "no",
                "2",
                "0",
                "0"
            },
            new Object[]{
                APPEAL_DORMANT_NOTIFICATION,
                "oral",
                Collections.singletonList("e2ee8609-7d56-4857-b3f8-79028e8960aa"),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "yes",
                "no",
                "1",
                "0",
                "0"
            },
            new Object[]{
                APPEAL_DORMANT_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                "paper",
                Arrays.asList("01293b93-b23e-40a3-ad78-2c6cd01cd21c", "652753bf-59b4-46eb-9c24-bd762338a098"),
                Arrays.asList("f41222ef-c05c-4682-9634-6b034a166368", "a6c09fad-6265-4c7c-8b95-36245ffa5352"),
                Collections.emptyList(),
                "yes",
                "yes",
                "yes",
                "yes",
                "2",
                "2",
                "0"
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                "oral",
                Arrays.asList("01293b93-b23e-40a3-ad78-2c6cd01cd21c", "652753bf-59b4-46eb-9c24-bd762338a098"),
                Arrays.asList("f41222ef-c05c-4682-9634-6b034a166368", "a6c09fad-6265-4c7c-8b95-36245ffa5352"),
                Collections.emptyList(),
                "yes",
                "yes",
                "yes",
                "yes",
                "2",
                "2",
                "0"
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                "paper",
                Collections.singletonList("652753bf-59b4-46eb-9c24-bd762338a098"),
                Arrays.asList("f41222ef-c05c-4682-9634-6b034a166368", "a6c09fad-6265-4c7c-8b95-36245ffa5352"),
                Collections.emptyList(),
                "no",
                "yes",
                "yes",
                "yes",
                "1",
                "2",
                "0"
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("91143b85-dd9d-430c-ba23-e42ec90f44f8", "77ea8a2f-06df-4279-9c1f-0f23cb2d9bbf"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "2"
            },
            new Object[]{
                POSTPONEMENT_NOTIFICATION,
                "paper",
                Arrays.asList("732ec1a2-243f-4047-b963-e8427cb007b8", "e07b7dba-f383-49ca-a0ba-b5b61be27da6"),
                Collections.emptyList(),
                Collections.emptyList(),
                "yes",
                "no",
                "yes",
                "no",
                "2",
                "0",
                "0"
            },
            new Object[]{
                POSTPONEMENT_NOTIFICATION,
                "oral",
                Arrays.asList("732ec1a2-243f-4047-b963-e8427cb007b8", "e07b7dba-f383-49ca-a0ba-b5b61be27da6"),
                Collections.emptyList(),
                Collections.emptyList(),
                "yes",
                "no",
                "Yes",
                "no",
                "2",
                "0",
                "0"
            },
            new Object[]{
                POSTPONEMENT_NOTIFICATION,
                "paper",
                Collections.singletonList("e07b7dba-f383-49ca-a0ba-b5b61be27da6"),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "yes",
                "no",
                "1",
                "0",
                "0"
            },
            new Object[]{
                POSTPONEMENT_NOTIFICATION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                EVIDENCE_REMINDER_NOTIFICATION,
                "oral",
                Arrays.asList("df0803aa-f804-49fe-a2ac-c27adc4bb585"),
                Arrays.asList("5f91012e-0d3f-465b-b301-ee3ee5a50100"),
                Collections.emptyList(),
                "no",
                "no",
                "yes",
                "yes",
                "1",
                "1",
                "0"
            },
            new Object[]{
                EVIDENCE_REMINDER_NOTIFICATION,
                "paper",
                Arrays.asList("81fa38cc-b7cc-469c-8109-67c801dc9c84"),
                Arrays.asList("f1076482-a76d-4389-b411-9865373cfc42"),
                Collections.emptyList(),
                "no",
                "no",
                "yes",
                "yes",
                "1",
                "1",
                "0"
            },
            new Object[]{
                REQUEST_INFO_INCOMPLETE,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-ENG-00452.docx", "TB-SCS-GNO-ENG-00452.docx"),
                "yes",
                "yes",
                "yes",
                "yes",
                "0",
                "0",
                "0"
            },
            new Object[]{
                REQUEST_INFO_INCOMPLETE,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-ENG-00452.docx", "TB-SCS-GNO-ENG-00452.docx"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                RESEND_APPEAL_CREATED_NOTIFICATION,
                "paper",
                Arrays.asList("01293b93-b23e-40a3-ad78-2c6cd01cd21c", "652753bf-59b4-46eb-9c24-bd762338a098"),
                Arrays.asList("f41222ef-c05c-4682-9634-6b034a166368", "a6c09fad-6265-4c7c-8b95-36245ffa5352"),
                Collections.emptyList(),
                "yes",
                "yes",
                "yes",
                "yes",
                "2",
                "2",
                "0"
            },
            new Object[]{
                RESEND_APPEAL_CREATED_NOTIFICATION,
                "oral",
                Arrays.asList("01293b93-b23e-40a3-ad78-2c6cd01cd21c", "652753bf-59b4-46eb-9c24-bd762338a098"),
                Arrays.asList("f41222ef-c05c-4682-9634-6b034a166368", "a6c09fad-6265-4c7c-8b95-36245ffa5352"),
                Collections.emptyList(),
                "yes",
                "yes",
                "yes",
                "yes",
                "2",
                "2",
                "0"
            },
            new Object[]{
                RESEND_APPEAL_CREATED_NOTIFICATION,
                "paper",
                Collections.singletonList("652753bf-59b4-46eb-9c24-bd762338a098"),
                Arrays.asList("f41222ef-c05c-4682-9634-6b034a166368", "a6c09fad-6265-4c7c-8b95-36245ffa5352"),
                Collections.emptyList(),
                "no",
                "yes",
                "yes",
                "yes",
                "1",
                "2",
                "0"
            },
            new Object[]{
                NON_COMPLIANT_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-WEL-00663.docx", "TB-SCS-GNO-WEL-00663.docx"),
                "yes",
                "yes",
                "yes",
                "yes",
                "0",
                "0",
                "0"
            },
            new Object[]{
                NON_COMPLIANT_NOTIFICATION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-WEL-00663.docx", "TB-SCS-GNO-WEL-00663.docx"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                DRAFT_TO_NON_COMPLIANT_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-WEL-00663.docx", "TB-SCS-GNO-WEL-00663.docx"),
                "yes",
                "yes",
                "yes",
                "yes",
                "0",
                "0",
                "0"
            },
            new Object[]{
                DRAFT_TO_NON_COMPLIANT_NOTIFICATION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-WEL-00663.docx", "TB-SCS-GNO-WEL-00663.docx"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                    PROCESS_AUDIO_VIDEO,
                    "paper",
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Arrays.asList("TB-SCS-GNO-ENG-00677.docx", "TB-SCS-GNO-ENG-00677.docx"),
                    "no",
                    "no",
                    "no",
                    "no",
                    "0",
                    "0",
                    "0"
            },
            new Object[]{
                    PROCESS_AUDIO_VIDEO_WELSH,
                    "paper",
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Arrays.asList("TB-SCS-GNO-ENG-00677.docx", "TB-SCS-GNO-ENG-00677.docx"),
                    "no",
                    "no",
                    "no",
                    "no",
                    "0",
                    "0",
                    "0"
            },
            new Object[]{
                DIRECTION_ISSUED,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-ENG-00067.docx", "TB-SCS-GNO-ENG-00089.docx"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                DECISION_ISSUED,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-ENG-00067.docx", "TB-SCS-GNO-ENG-00089.docx"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                ISSUE_FINAL_DECISION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-ENG-00067.docx", "TB-SCS-GNO-ENG-00089.docx"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
                new Object[]{
                    ISSUE_FINAL_DECISION_WELSH,
                    "paper",
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Arrays.asList("TB-SCS-GNO-ENG-00067.docx", "TB-SCS-GNO-ENG-00089.docx"),
                    "no",
                    "no",
                    "no",
                    "no",
                    "0",
                    "0",
                    "0"
                },
            new Object[]{
                ISSUE_ADJOURNMENT_NOTICE,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-ENG-00067.docx", "TB-SCS-GNO-ENG-00089.docx"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
            new Object[]{
                ISSUE_ADJOURNMENT_NOTICE_WELSH,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("TB-SCS-GNO-ENG-00067.docx", "TB-SCS-GNO-ENG-00089.docx"),
                "no",
                "no",
                "no",
                "no",
                "0",
                "0",
                "0"
            },
        };
    }

    @SuppressWarnings({"Indentation", "unused"})
    private Object[] generateAppointeeNotificationScenarios() {
        return new Object[]{
            new Object[]{
                APPEAL_DORMANT_NOTIFICATION,
                "oral",
                Collections.singletonList("1a2683d0-ca0f-4465-b25d-59d3d817750a"),
                Collections.emptyList(),
                Collections.emptyList(),
                "yes",
                "yes",
                "1",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                APPEAL_DORMANT_NOTIFICATION,
                "paper",
                Collections.singletonList("976bdb6c-8a86-48cf-9e0f-7989acaec0c2"),
                Collections.singletonList("8b459c7d-c7b9-4293-9734-26341a231695"),
                Collections.emptyList(),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                STRUCK_OUT,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("TB-SCS-GNO-ENG-00466.docx"),
                "no",
                "no",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                "oral",
                Collections.singletonList("362d9a85-e0e4-412b-b874-020c0464e2b4"),
                Collections.singletonList("f41222ef-c05c-4682-9634-6b034a166368"),
                Collections.emptyList(),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                "oral",
                Collections.singletonList("362d9a85-e0e4-412b-b874-020c0464e2b4"),
                Collections.singletonList("f41222ef-c05c-4682-9634-6b034a166368"),
                Collections.emptyList(),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "0",
                "0",
                "0",
                ""
            },
            new Object[]{
                DWP_RESPONSE_RECEIVED_NOTIFICATION,
                "oral",
                Collections.singletonList("2c5644db-1f7b-429b-b10a-8b23a80ed26a"),
                Collections.singletonList("f20ffcb1-c5f0-4bff-b2d1-a1094f8014e6"),
                Collections.emptyList(),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                DWP_RESPONSE_RECEIVED_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                DWP_UPLOAD_RESPONSE_NOTIFICATION,
                "oral",
                Collections.singletonList("793b3785-88d6-4994-ba8b-7c2fdc67d88d"),
                Collections.singletonList("f0444380-a8a4-4805-b9c2-563d1bd199cd"),
                Collections.singletonList("TB-SCS-GNO-ENG-00261.doc"),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                DWP_UPLOAD_RESPONSE_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("TB-SCS-GNO-ENG-00261.doc"),
                "no",
                "no",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                EVIDENCE_REMINDER_NOTIFICATION,
                "oral",
                Arrays.asList("d994236b-d7c4-44ef-9627-12372bb0434a"),
                Arrays.asList("7d36718b-1193-4b3d-86bd-db54612c5363"),
                Collections.emptyList(),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                EVIDENCE_REMINDER_NOTIFICATION,
                "paper",
                Arrays.asList("c507a630-9e6a-43c9-8e39-dcabdcffaf53"),
                Arrays.asList("56a6c0c8-a251-482d-be83-95a7a1bf528c"),
                Collections.emptyList(),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                EVIDENCE_RECEIVED_NOTIFICATION,
                "paper",
                Collections.singletonList("8509fb1b-eb15-449f-b4ee-15ce286ab404"),
                Collections.singletonList("e7868511-3a1f-4b8e-8bb3-b36c2bd99799"),
                Collections.emptyList(),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                EVIDENCE_RECEIVED_NOTIFICATION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                EVIDENCE_RECEIVED_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                EVIDENCE_RECEIVED_NOTIFICATION,
                "oral",
                Arrays.asList("bd78cbc4-27d3-4692-a491-6c1770df174e"),
                Arrays.asList("e7868511-3a1f-4b8e-8bb3-b36c2bd99799"),
                Collections.emptyList(),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                SUBSCRIPTION_UPDATED_NOTIFICATION,
                "oral",
                Arrays.asList("b8b2904f-629d-42cf-acea-1b74bde5b2ff", "03b957bf-e21d-4147-90c1-b6fefa8cf70d"),
                Arrays.asList("7397a76f-14cb-468c-b1a7-0570940ead91", "759c712a-6b55-485e-bcf7-1cf5c4896eb1"),
                Collections.emptyList(),
                "yes",
                "yes",
                "2",
                "2",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                SUBSCRIPTION_UPDATED_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "0",
                "0",
                "0",
                "Harry Potter"
            },
            new Object[]{
                PROCESS_AUDIO_VIDEO,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("TB-SCS-GNO-ENG-00677.docx"),
                "yes",
                "yes",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                PROCESS_AUDIO_VIDEO_WELSH,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("TB-SCS-GNO-ENG-00677.docx"),
                "yes",
                "yes",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                DIRECTION_ISSUED,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("TB-SCS-GNO-ENG-00067.docx"),
                "yes",
                "yes",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                DECISION_ISSUED,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("TB-SCS-GNO-ENG-00067.docx"),
                "yes",
                "yes",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                ISSUE_FINAL_DECISION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("TB-SCS-GNO-ENG-00067.docx"),
                "yes",
                "yes",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                ISSUE_ADJOURNMENT_NOTICE,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("TB-SCS-GNO-ENG-00067.docx"),
                "yes",
                "yes",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                ISSUE_ADJOURNMENT_NOTICE_WELSH,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("TB-SCS-GNO-ENG-00067.docx"),
                "yes",
                "yes",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                CASE_UPDATED,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "0",
                "0",
                "0",
                "Harry Potter"
            },
            new Object[]{
                HEARING_REMINDER_NOTIFICATION,
                "oral",
                Collections.singletonList("07bebee4-f07a-4a0d-9c50-65be30dc72a5"),
                Collections.singletonList("18960596-1983-4da8-8b5c-dc1c851bb19b"),
                Collections.emptyList(),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                HEARING_REMINDER_NOTIFICATION,
                "oral",
                Collections.singletonList("07bebee4-f07a-4a0d-9c50-65be30dc72a5"),
                Collections.emptyList(),
                Collections.emptyList(),
                "yes",
                "no",
                "1",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                HEARING_REMINDER_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.singletonList("18960596-1983-4da8-8b5c-dc1c851bb19b"),
                Collections.emptyList(),
                "no",
                "yes",
                "0",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                HEARING_REMINDER_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "0",
                "0",
                "0",
                ""
            },
            new Object[]{
                REQUEST_INFO_INCOMPLETE,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("TB-SCS-GNO-ENG-00452.docx"),
                "yes",
                "yes",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                REQUEST_INFO_INCOMPLETE,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("TB-SCS-GNO-ENG-00452.docx"),
                "no",
                "no",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                NON_COMPLIANT_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("TB-SCS-GNO-WEL-00663.docx"),
                "yes",
                "yes",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                NON_COMPLIANT_NOTIFICATION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("TB-SCS-GNO-WEL-00663.docx"),
                "no",
                "no",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                DRAFT_TO_NON_COMPLIANT_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("TB-SCS-GNO-WEL-00663.docx"),
                "yes",
                "yes",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                DRAFT_TO_NON_COMPLIANT_NOTIFICATION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("TB-SCS-GNO-WEL-00663.docx"),
                "no",
                "no",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                APPEAL_WITHDRAWN_NOTIFICATION,
                "paper",
                Collections.singletonList("8620e023-f663-477e-a771-9cfad50ee30f"),
                Collections.singletonList("446c7b23-7342-42e1-adff-b4c367e951cb"),
                Collections.singletonList("TB-SCS-GNO-ENG-00659.docx"),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                APPEAL_WITHDRAWN_NOTIFICATION,
                "oral",
                Collections.singletonList("8620e023-f663-477e-a771-9cfad50ee30f"),
                Collections.singletonList("446c7b23-7342-42e1-adff-b4c367e951cb"),
                Collections.singletonList("TB-SCS-GNO-ENG-00659.docx"),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                RESEND_APPEAL_CREATED_NOTIFICATION,
                "oral",
                Collections.singletonList("362d9a85-e0e4-412b-b874-020c0464e2b4"),
                Collections.singletonList("f41222ef-c05c-4682-9634-6b034a166368"),
                Collections.emptyList(),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                RESEND_APPEAL_CREATED_NOTIFICATION,
                "oral",
                Collections.singletonList("362d9a85-e0e4-412b-b874-020c0464e2b4"),
                Collections.singletonList("f41222ef-c05c-4682-9634-6b034a166368"),
                Collections.emptyList(),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                RESEND_APPEAL_CREATED_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "0",
                "0",
                "0",
                ""
            }
        };
    }

    @SuppressWarnings({"Indentation", "unused"})
    private Object[] generateAppointeeNotificationWhenNoOldCaseReferenceScenarios() {
        return new Object[]{
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                "oral",
                Collections.singletonList("362d9a85-e0e4-412b-b874-020c0464e2b4"),
                Collections.singletonList("f41222ef-c05c-4682-9634-6b034a166368"),
                Collections.emptyList(),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                "oral",
                Collections.singletonList("362d9a85-e0e4-412b-b874-020c0464e2b4"),
                Collections.singletonList("f41222ef-c05c-4682-9634-6b034a166368"),
                Collections.emptyList(),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                EVIDENCE_RECEIVED_NOTIFICATION,
                "oral",
                Collections.singletonList("bd78cbc4-27d3-4692-a491-6c1770df174e"),
                Collections.singletonList("e7868511-3a1f-4b8e-8bb3-b36c2bd99799"),
                Collections.emptyList(),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                SYA_APPEAL_CREATED_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("747d026e-1bec-4e96-8a34-28f36e30bba5"),
                "no",
                "no",
                "0",
                "0",
                "1",
                "Appointee Appointee"
            },
            new Object[]{
                DWP_RESPONSE_RECEIVED_NOTIFICATION,
                "oral",
                Collections.singletonList("2c5644db-1f7b-429b-b10a-8b23a80ed26a"),
                Collections.singletonList("f20ffcb1-c5f0-4bff-b2d1-a1094f8014e6"),
                Collections.emptyList(),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                DWP_RESPONSE_RECEIVED_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("8b11f3f4-6452-4a35-93d8-a94996af6499"),
                "no",
                "no",
                "0",
                "0",
                "1",
                "Appointee Appointee"
            },
            new Object[]{
                DWP_UPLOAD_RESPONSE_NOTIFICATION,
                "oral",
                Collections.singletonList("793b3785-88d6-4994-ba8b-7c2fdc67d88d"),
                Collections.singletonList("f0444380-a8a4-4805-b9c2-563d1bd199cd"),
                Collections.singletonList("TB-SCS-GNO-ENG-00261.doc"),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                DWP_UPLOAD_RESPONSE_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("TB-SCS-GNO-ENG-00261.doc"),
                "no",
                "no",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                EVIDENCE_REMINDER_NOTIFICATION,
                "oral",
                Collections.singletonList("d994236b-d7c4-44ef-9627-12372bb0434a"),
                Collections.singletonList("7d36718b-1193-4b3d-86bd-db54612c5363"),
                Collections.emptyList(),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                EVIDENCE_REMINDER_NOTIFICATION,
                "paper",
                Arrays.asList("c507a630-9e6a-43c9-8e39-dcabdcffaf53"),
                Arrays.asList("56a6c0c8-a251-482d-be83-95a7a1bf528c"),
                Collections.emptyList(),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                APPEAL_LAPSED_NOTIFICATION,
                "oral",
                Collections.singletonList("8ce8d794-75e8-49a0-b4d2-0c6cd2061c11"),
                Collections.singletonList("d2b4394b-d1c9-4d5c-a44e-b382e41c67e5"),
                Collections.singletonList("TB-SCS-GNO-ENG-00656.docx"),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                SUBSCRIPTION_UPDATED_NOTIFICATION,
                "oral",
                Arrays.asList("b8b2904f-629d-42cf-acea-1b74bde5b2ff", "03b957bf-e21d-4147-90c1-b6fefa8cf70d"),
                Arrays.asList("7397a76f-14cb-468c-b1a7-0570940ead91", "759c712a-6b55-485e-bcf7-1cf5c4896eb1"),
                Collections.emptyList(),
                "yes",
                "yes",
                "2",
                "2",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                ADJOURNED_NOTIFICATION,
                "oral",
                Collections.singletonList("77ea995b-9744-4167-9250-e627c85e5eda"),
                Collections.singletonList("7455de19-aa3b-48f0-b765-ab2757ba6a88"),
                Collections.emptyList(),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                DWP_RESPONSE_RECEIVED_NOTIFICATION,
                "paper",
                Collections.singletonList("e1084d78-5e2d-45d2-a54f-84339da141c1"),
                Collections.singletonList("505be856-ceca-4bbc-ba70-29024585056f"),
                Collections.emptyList(),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                DWP_UPLOAD_RESPONSE_NOTIFICATION,
                "paper",
                Collections.singletonList("ddbc7562-f299-4b19-9357-915f08f05da7"),
                Collections.singletonList("5e5cfe8d-b893-4f87-817f-9d05d22d657a"),
                Collections.singletonList("TB-SCS-GNO-ENG-00261.doc"),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                HEARING_BOOKED_NOTIFICATION,
                "oral",
                Collections.singletonList("aa0930a3-e1bd-4b50-ac6b-34df73ec8378"),
                Collections.singletonList("8aa77a9c-9bc6-424d-8716-1c948681270e"),
                Collections.emptyList(),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                APPEAL_WITHDRAWN_NOTIFICATION,
                "paper",
                Collections.singletonList("8620e023-f663-477e-a771-9cfad50ee30f"),
                Collections.singletonList("446c7b23-7342-42e1-adff-b4c367e951cb"),
                Collections.singletonList("TB-SCS-GNO-ENG-00659.docx"),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                APPEAL_WITHDRAWN_NOTIFICATION,
                "oral",
                Collections.singletonList("8620e023-f663-477e-a771-9cfad50ee30f"),
                Collections.singletonList("446c7b23-7342-42e1-adff-b4c367e951cb"),
                Collections.singletonList("TB-SCS-GNO-ENG-00659.docx"),
                "yes",
                "yes",
                "1",
                "1",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                SUBSCRIPTION_UPDATED_NOTIFICATION,
                "oral",
                Arrays.asList("b8b2904f-629d-42cf-acea-1b74bde5b2ff", "03b957bf-e21d-4147-90c1-b6fefa8cf70d"),
                Arrays.asList("7397a76f-14cb-468c-b1a7-0570940ead91", "759c712a-6b55-485e-bcf7-1cf5c4896eb1"),
                Collections.emptyList(),
                "yes",
                "yes",
                "2",
                "2",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                SUBSCRIPTION_UPDATED_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "no",
                "no",
                "0",
                "0",
                "0",
                "Harry Potter"
            },
            new Object[]{
                REQUEST_INFO_INCOMPLETE,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("TB-SCS-GNO-ENG-00452.docx"),
                "yes",
                "yes",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                REQUEST_INFO_INCOMPLETE,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("TB-SCS-GNO-ENG-00452.docx"),
                "no",
                "no",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                NON_COMPLIANT_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("TB-SCS-GNO-WEL-00663.docx"),
                "yes",
                "yes",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                NON_COMPLIANT_NOTIFICATION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("TB-SCS-GNO-WEL-00663.docx"),
                "no",
                "no",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                DRAFT_TO_NON_COMPLIANT_NOTIFICATION,
                "oral",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("TB-SCS-GNO-WEL-00663.docx"),
                "yes",
                "yes",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
            new Object[]{
                DRAFT_TO_NON_COMPLIANT_NOTIFICATION,
                "paper",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("TB-SCS-GNO-WEL-00663.docx"),
                "no",
                "no",
                "0",
                "0",
                "0",
                "Appointee Appointee"
            },
        };
    }

    @Test
    public void shouldSendNotificationForHearingBookedRequestForAnOralHearing() throws Exception {
        json = json.replace("appealReceived", "hearingBooked");
        json = json.replace("2018-01-12", LocalDate.now().plusDays(2).toString());

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient).sendEmail(any(), any(), any(), any());
        verify(notificationClient, times(2)).sendSms(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldNotSendNotificationForHearingBookedRequestForAPaperHearing() throws Exception {
        updateJsonForPaperHearing();
        json = json.replace("appealReceived", "hearingBooked");
        json = json.replace("2018-01-12", LocalDate.now().plusDays(2).toString());

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient, never()).sendEmail(any(), any(), any(), any());
        verify(notificationClient, never()).sendSms(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldNotSendNotificationForHearingBookedRequestForHearingInThePastForAnOralHearing() throws Exception {
        json = json.replace("appealReceived", "hearingBooked");

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient, never()).sendEmail(any(), any(), any(), any());
        verify(notificationClient, never()).sendSms(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldSendAppellantNotificationForEvidenceReminderForAnOralHearing() throws Exception {
        json = json.replace("appealReceived", "evidenceReminder");

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient).sendEmail(eq("d994236b-d7c4-44ef-9627-12372bb0434a"), any(), any(), any());
        verify(notificationClient).sendSms(eq("7d36718b-1193-4b3d-86bd-db54612c5363"), any(), any(), any(), any());
    }

    @Test
    public void shouldSendAppellantNotificationForEvidenceReminderForAPaperHearing() throws Exception {
        updateJsonForPaperHearing();
        json = json.replace("appealReceived", "evidenceReminder");

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient).sendEmail(eq("c507a630-9e6a-43c9-8e39-dcabdcffaf53"), any(), any(), any());
        verify(notificationClient).sendSms(eq("56a6c0c8-a251-482d-be83-95a7a1bf528c"), any(), any(), any(), any());
    }

    @Test
    public void shouldSendNotificationForHearingReminderForAnOralHearing() throws Exception {
        json = json.replace("appealReceived", "hearingReminder");
        json = json.replace("2018-01-12", LocalDate.now().plusDays(2).toString());

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient).sendEmail(any(), any(), any(), any());
        verify(notificationClient, times(2)).sendSms(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldNotSendNotificationForHearingReminderForAPaperHearing() throws Exception {
        updateJsonForPaperHearing();
        json = json.replace("appealReceived", "hearingReminder");
        json = json.replace("2018-01-12", LocalDate.now().plusDays(2).toString());

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient, never()).sendEmail(any(), any(), any(), any());
        verify(notificationClient, never()).sendSms(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldSendNotificationForSyaAppealCreatedRequestForAnOralHearing() throws Exception {
        json = json.replace("appealReceived", "appealCreated");

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient, times(1)).sendEmail(any(), any(), any(), any());
        verify(notificationClient, times(2)).sendSms(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldSendNotificationForSyaAppealCreatedRequestForAPaperHearing() throws Exception {
        updateJsonForPaperHearing();
        json = json.replace("appealReceived", "appealCreated");
        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient, times(1)).sendEmail(any(), any(), any(), any());
        verify(notificationClient, times(2)).sendSms(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldSendSubscriptionCreatedNotificationForSubscriptionUpdatedRequestWithNewSubscribeSmsRequestForAnOralHearing() throws Exception {
        json = json.replace("appealReceived", "subscriptionUpdated");
        json = updateEmbeddedJson(json, "No", "case_details", "case_data", "subscriptions", "appellantSubscription", "subscribeEmail");

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient, never()).sendEmail(any(), any(), any(), any());
        verify(notificationClient).sendSms(eq("7397a76f-14cb-468c-b1a7-0570940ead91"), any(), any(), any(), any());
    }

    /*@Test
    public void shouldSendSubscriptionCreatedNotificationForSubscriptionUpdatedRequestWithNewSubscribeSmsRequestForAPaperHearingWithRepSubscribedToSms() throws Exception {
        updateJsonForPaperHearing();
        json = json.replace("appealReceived", "subscriptionUpdated");
        json = updateEmbeddedJson(json, "No", "case_details", "case_data", "subscriptions", "appellantSubscription", "subscribeEmail");

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient).sendSms(eq(subscriptionCreatedSmsId), any(), any(), any(), any());
        verify(notificationClient).sendSms(eq(paperResponseReceivedSmsId), any(), any(), any(), any());
        verifyNoMoreInteractions(notificationClient);
    }

    @Test
    public void shouldSendSubscriptionUpdatedNotificationForSubscriptionUpdatedRequestWithNewEmailAddressForAnOralHearingWhenAlreadySubscribedToSms() throws Exception {
        json = updateEmbeddedJson(json, "subscriptionUpdated", "event_id");
        json = updateEmbeddedJson(json, "oral", "case_details", "case_data", "appeal", "hearingType");
        json = updateEmbeddedJson(json, "No", "case_details", "case_data", "subscriptions",
                "appellantSubscription", "subscribeSms");

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient).sendEmail(eq(subscriptionUpdatedEmailId), any(), any(), any());
        verify(notificationClient).sendEmail(eq(oralResponseReceivedEmailId), any(), any(), any());
        verifyNoMoreInteractions(notificationClient);
    }

    @Test
    public void shouldSendSubscriptionUpdatedNotificationForSubscriptionUpdatedRequestWithNewEmailAddressForAPaperHearingWhenRepAlreadySubscriptedToSms() throws Exception {
        updateJsonForPaperHearing();
        json = updateEmbeddedJson(json, "subscriptionUpdated", "event_id");
        json = updateEmbeddedJson(json, "No", "case_details", "case_data", "subscriptions",
                "appellantSubscription", "subscribeSms");

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient).sendEmail(eq(subscriptionUpdatedEmailId), any(), any(), any());
        verify(notificationClient).sendEmail(eq(paperResponseReceivedEmailId), any(), any(), any());
        verifyNoMoreInteractions(notificationClient);
    }*/

    @Test
    public void shouldNotSendSubscriptionUpdatedNotificationForSubscriptionUpdatedRequestWithSameEmailAddress() throws Exception {
        json = json.replace("appealReceived", "subscriptionUpdated");
        json = json.replace("sscstest@greencroftconsulting.com", "tester@hmcts.net");

        json = updateEmbeddedJson(json, "Yes", "case_details_before", "case_data", "subscriptions", "appellantSubscription", "subscribeEmail");
        json = updateEmbeddedJson(json, "No", "case_details", "case_data", "subscriptions", "appellantSubscription", "subscribeSms");

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient, never()).sendEmail(any(), any(), any(), any());
        verify(notificationClient, never()).sendSms(any(), any(), any(), any(), any());
    }

    @Test
    public void givenAnUnknownRpcCase_thenDoNotProcessNotifications() throws Exception {
        String path = getClass().getClassLoader().getResource("json/ccdResponseWithNoOldCaseRef.json").getFile();
        String json = FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8.name());

        json = json.replace("appealReceived", "appealCreated");
        json = json.replace("SC022", "SC948");

        json = updateEmbeddedJson(json, "No", "case_details", "case_data", "subscriptions", "appellantSubscription", "subscribeEmail");
        json = updateEmbeddedJson(json, "No", "case_details", "case_data", "subscriptions", "appellantSubscription", "subscribeSms");
        json = updateEmbeddedJson(json, "No", "case_details", "case_data", "subscriptions", "representativeSubscription", "subscribeEmail");
        json = updateEmbeddedJson(json, "No", "case_details", "case_data", "subscriptions", "representativeSubscription", "subscribeSms");
        json = updateEmbeddedJson(json, "No", "case_details_before", "case_data", "subscriptions", "representativeSubscription", "subscribeSms");

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient, never()).sendEmail(any(), any(), any(), any());
        verify(notificationClient, never()).sendSms(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldReturn400WhenAuthHeaderIsMissing() throws Exception {
        HttpServletResponse response = getResponse(getRequestWithoutAuthHeader(json));

        assertHttpStatus(response, HttpStatus.BAD_REQUEST);
        verify(authorisationService, never()).authorise(anyString());
        verify(notificationClient, never()).sendEmail(any(), any(), any(), any(), any());
    }

    @Test
    @Parameters({"subscriptionUpdated", "appealReceived", "directionIssued", "nonCompliant"})
    public void shouldNotSendNotificationWhenAppealDormantAndNotificationType(String notificationEventType) throws Exception {
        json = json.replace("appealCreated", State.DORMANT_APPEAL_STATE.toString());
        json = json.replace("appealReceived", notificationEventType);

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient, never()).sendEmail(any(), any(), any(), any());
        verify(notificationClient, never()).sendSms(any(), any(), any(), any(), any());
    }

    @Test
    @Parameters({"appealLapsed", "appealDormant"})
    public void shouldSendNotificationWhenAppealDormantAndNotificationType(String notificationEventType) throws Exception {
        json = json.replace("appealCreated", State.DORMANT_APPEAL_STATE.toString());
        json = json.replace("appealReceived", notificationEventType);

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient, atMostOnce()).sendEmail(any(), any(), any(), any());
        verify(notificationClient, atMost(2)).sendSms(any(), any(), any(), any(), any());

        if (notificationEventType.equals(APPEAL_LAPSED_NOTIFICATION.getId())) {
            verify(notificationClient, atLeast(2)).sendPrecompiledLetterWithInputStream(any(), any());
        } else {
            verify(notificationClient, atMostOnce()).sendPrecompiledLetterWithInputStream(any(), any());
        }
        verifyNoMoreInteractions(notificationClient);
    }

    @Test
    @Parameters({"appealWithdrawn", "directionIssued"})
    public void shouldSendNotificationLetterWhenAppealDormantAndNotificationType(String notificationEventType) throws Exception {
        json = json.replace("appealCreated", State.DORMANT_APPEAL_STATE.toString());
        json = json.replace("appealReceived", notificationEventType);

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient, atMostOnce()).sendEmail(any(), any(), any(), any());
        verify(notificationClient, atMost(2)).sendSms(any(), any(), any(), any(), any());
        verify(notificationClient, atMost(2)).sendPrecompiledLetterWithInputStream(any(), any());
        verifyNoMoreInteractions(notificationClient);
    }

    @Test
    public void givenAStruckOutEvent_shouldStillSendStruckOutNotificationWhenAppealDormant() throws Exception {

        String filename = "json/ccdResponse_struckOut.json";
        String path = getClass().getClassLoader().getResource(filename).getFile();
        String json = FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8.name());

        json = json.replace("appealCreated", State.DORMANT_APPEAL_STATE.toString());

        byte[] sampleDirectionNotice = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("pdfs/direction-text.pdf"));
        when(evidenceManagementService.download(any(), any())).thenReturn(sampleDirectionNotice);

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient, atMostOnce()).sendEmail(any(), any(), any(), any());
        verify(notificationClient, atMost(2)).sendSms(any(), any(), any(), any(), any());
        verify(notificationClient, atMostOnce()).sendPrecompiledLetterWithInputStream(any(), any());
        verifyNoMoreInteractions(notificationClient);
    }

    @Test
    @Parameters({"adjournCase", "issueFinalDecision", "decisionIssued", "directionIssued"})
    public void givenAReissueEvent_shouldStillSendDirectionIssued(String furtherEvidenceType) throws Exception {

        String filename = "json/ccdResponse_reissueDocument.json";
        String path = getClass().getClassLoader().getResource(filename).getFile();
        String json = FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8.name());

        json = json.replace("appealCreated", State.DORMANT_APPEAL_STATE.toString());
        json = json.replace("REISSUE_DOCUMENT", furtherEvidenceType);

        byte[] sampleDirectionNotice = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("pdfs/direction-text.pdf"));
        when(evidenceManagementService.download(any(), any())).thenReturn(sampleDirectionNotice);

        HttpServletResponse response = getResponse(getRequestWithAuthHeader(json));

        assertHttpStatus(response, HttpStatus.OK);
        verify(notificationClient, times(0)).sendEmail(any(), any(), any(), any());
        verify(notificationClient, times(0)).sendSms(any(), any(), any(), any(), any());
        verify(notificationClient, atMostOnce()).sendPrecompiledLetterWithInputStream(any(), any());
        verifyNoMoreInteractions(notificationClient);
    }

    @NamedParameters("grantedOrRefused")
    @SuppressWarnings("unused")
    private Object[] grantedOrRefused() {
        return new Object[] {
            new DatedRequestOutcome[] {DatedRequestOutcome.builder()
                    .requestOutcome(RequestOutcome.GRANTED).date(LocalDate.now()).build()},
            new DatedRequestOutcome[] {DatedRequestOutcome.builder()
                    .requestOutcome(RequestOutcome.REFUSED).date(LocalDate.now()).build()},
        };
    }

    @Test
    @Parameters(named = "grantedOrRefused")
    public void givenAppellantConfidentialityRequest_shouldSendConfidentialityLetter(DatedRequestOutcome requestOutcome) throws Exception {
        String path = getClass().getClassLoader().getResource("json/ccdResponseWithJointParty.json").getFile();
        String json = FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8.name());
        json = updateEmbeddedJson(json, "reviewConfidentialityRequest", "event_id");
        json = updateEmbeddedJson(json, requestOutcome, "case_details", "case_data", "confidentialityRequestOutcomeAppellant");

        getResponse(getRequestWithAuthHeader(json));

        verify(notificationClient, times(0)).sendEmail(any(), any(), any(), any());
        verify(notificationClient, times(0)).sendSms(any(), any(), any(), any(), any());
        verify(notificationClient, times(1)).sendPrecompiledLetterWithInputStream(any(), any());
    }

    @Test
    @Parameters(named = "grantedOrRefused")
    public void givenJointPartyConfidentialityRequest_shouldSendConfidentialityLetter(DatedRequestOutcome requestOutcome) throws Exception {
        String path = getClass().getClassLoader().getResource("json/ccdResponseWithJointParty.json").getFile();
        String json = FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8.name());
        json = updateEmbeddedJson(json, "reviewConfidentialityRequest", "event_id");
        json = updateEmbeddedJson(json, requestOutcome, "case_details", "case_data", "confidentialityRequestOutcomeJointParty");

        getResponse(getRequestWithAuthHeader(json));

        verify(notificationClient, times(0)).sendEmail(any(), any(), any(), any());
        verify(notificationClient, times(0)).sendSms(any(), any(), any(), any(), any());
        verify(notificationClient, times(1)).sendPrecompiledLetterWithInputStream(any(), any());
    }

    @Test
    @Parameters(named = "grantedOrRefused")
    public void givenJointPartyAndAppellantConfidentialityRequest_shouldSendBothConfidentialityLetters(DatedRequestOutcome requestOutcome) throws Exception {
        String path = getClass().getClassLoader().getResource("json/ccdResponseWithJointParty.json").getFile();
        String json = FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8.name());
        json = updateEmbeddedJson(json, "reviewConfidentialityRequest", "event_id");
        json = updateEmbeddedJson(json, requestOutcome, "case_details", "case_data", "confidentialityRequestOutcomeAppellant");
        json = updateEmbeddedJson(json, requestOutcome, "case_details", "case_data", "confidentialityRequestOutcomeJointParty");

        getResponse(getRequestWithAuthHeader(json));

        verify(notificationClient, times(0)).sendEmail(any(), any(), any(), any());
        verify(notificationClient, times(0)).sendSms(any(), any(), any(), any(), any());
        verify(notificationClient, times(2)).sendPrecompiledLetterWithInputStream(any(), any());
    }

    private void updateJsonForPaperHearing() throws IOException {
        json = updateEmbeddedJson(json, "No", "case_details", "case_data", "appeal", "hearingOptions", "wantsToAttend");
        json = updateEmbeddedJson(json, "paper", "case_details", "case_data", "appeal", "hearingType");
    }

}
