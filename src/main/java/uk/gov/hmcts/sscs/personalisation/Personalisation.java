package uk.gov.hmcts.sscs.personalisation;

import static com.google.common.collect.Lists.newArrayList;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.hmcts.sscs.config.AppConstants.*;
import static uk.gov.hmcts.sscs.domain.Benefit.getBenefitByCode;
import static uk.gov.hmcts.sscs.domain.notify.EventType.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.sscs.config.NotificationConfig;
import uk.gov.hmcts.sscs.domain.*;
import uk.gov.hmcts.sscs.domain.notify.Event;
import uk.gov.hmcts.sscs.domain.notify.EventType;
import uk.gov.hmcts.sscs.domain.notify.Template;
import uk.gov.hmcts.sscs.extractor.HearingUpdateDateExtractor;
import uk.gov.hmcts.sscs.service.MessageAuthenticationServiceImpl;
import uk.gov.hmcts.sscs.service.RegionalProcessingCenterService;

@Component
public class Personalisation {

    private boolean sendSmsSubscriptionConfirmation;

    private static final org.slf4j.Logger LOG = getLogger(Personalisation.class);

    @Autowired
    private NotificationConfig config;

    @Autowired
    private HearingUpdateDateExtractor hearingUpdateDateExtractor;

    @Autowired
    private MessageAuthenticationServiceImpl macService;

    @Autowired
    private RegionalProcessingCenterService regionalProcessingCenterService;

    public Map<String, String> create(CcdResponseWrapper responseWrapper) {
        CcdResponse ccdResponse = responseWrapper.getNewCcdResponse();
        Map<String, String> personalisation = new HashMap<>();
        Subscription appellantSubscription = ccdResponse.getSubscriptions().getAppellantSubscription();
        Benefit benefit = getBenefitByCode(ccdResponse.getAppeal().getBenefitType().getCode());

        personalisation.put(BENEFIT_NAME_ACRONYM_LITERAL, benefit.name() + " benefit");
        personalisation.put(BENEFIT_NAME_ACRONYM_SHORT_LITERAL, benefit.name());
        personalisation.put(BENEFIT_FULL_NAME_LITERAL, benefit.getDescription());
        personalisation.put(APPEAL_REF, ccdResponse.getCaseReference());
        personalisation.put(APPELLANT_NAME, String.format("%s %s",
                ccdResponse.getAppeal().getAppellant().getName().getFirstName(), ccdResponse.getAppeal().getAppellant().getName().getLastName()));
        personalisation.put(PHONE_NUMBER, config.getHmctsPhoneNumber());

        if (ccdResponse.getSubscriptions().getAppellantSubscription().getTya() != null) {
            personalisation.put(APPEAL_ID, ccdResponse.getSubscriptions().getAppellantSubscription().getTya());
            personalisation.put(MANAGE_EMAILS_LINK_LITERAL, config.getManageEmailsLink().replace(MAC_LITERAL,
                    getMacToken(ccdResponse.getSubscriptions().getAppellantSubscription().getTya(),
                            benefit.name())));
            personalisation.put(TRACK_APPEAL_LINK_LITERAL, config.getTrackAppealLink() != null ? config.getTrackAppealLink().replace(APPEAL_ID_LITERAL, appellantSubscription.getTya()) : null);
            personalisation.put(SUBMIT_EVIDENCE_LINK_LITERAL, config.getEvidenceSubmissionInfoLink().replace(APPEAL_ID, appellantSubscription.getTya()));
            personalisation.put(SUBMIT_EVIDENCE_INFO_LINK_LITERAL, config.getEvidenceSubmissionInfoLink().replace(APPEAL_ID_LITERAL, appellantSubscription.getTya()));
            personalisation.put(CLAIMING_EXPENSES_LINK_LITERAL, config.getClaimingExpensesLink().replace(APPEAL_ID, appellantSubscription.getTya()));
            personalisation.put(HEARING_INFO_LINK_LITERAL,
                    config.getHearingInfoLink().replace(APPEAL_ID_LITERAL, appellantSubscription.getTya()));
        }

        personalisation.put(FIRST_TIER_AGENCY_ACRONYM, DWP_ACRONYM);
        personalisation.put(FIRST_TIER_AGENCY_FULL_NAME, DWP_FUL_NAME);

        if (ccdResponse.getHearings() != null && !ccdResponse.getHearings().isEmpty()) {
            Hearing latestHearing = ccdResponse.getHearings().get(0);

            LocalDateTime hearingDateTime = latestHearing.getValue().getHearingDateTime();
            personalisation.put(HEARING_DATE, formatLocalDate(hearingDateTime.toLocalDate()));
            personalisation.put(HEARING_TIME, formatLocalTime(hearingDateTime));
            personalisation.put(VENUE_ADDRESS_LITERAL, formatAddress(latestHearing));
            personalisation.put(VENUE_MAP_LINK_LITERAL, latestHearing.getValue().getVenue().getGoogleMapLink());
            personalisation.put(DAYS_TO_HEARING_LITERAL, calculateDaysToHearingText(hearingDateTime.toLocalDate()));
        }

        setEvidenceProcessingAddress(personalisation, ccdResponse);
        setEventData(personalisation, ccdResponse);
        setEvidenceReceivedNotificationData(personalisation, ccdResponse);
        setHearingUpdateDate(personalisation, ccdResponse);

        return personalisation;
    }

    public void setHearingUpdateDate(Map<String, String> personalisation, CcdResponse ccdResponse) {
        Optional<ZonedDateTime> hearingUpdateDate = hearingUpdateDateExtractor.extract(ccdResponse);
        if (hearingUpdateDate.isPresent()) {
            personalisation.put(
                HEARING_CONTACT_DATE,
                formatLocalDate(hearingUpdateDate.get().toLocalDate())
            );
        }
    }

    public Map<String, String> setEventData(Map<String, String> personalisation, CcdResponse ccdResponse) {
        if (ccdResponse.getEvents() != null) {

            for (Events events : ccdResponse.getEvents()) {
                if (events.getValue() != null) {
                    if ((ccdResponse.getNotificationType().equals(APPEAL_RECEIVED) && events.getValue().getEventType().equals(APPEAL_RECEIVED))
                        || ccdResponse.getNotificationType().equals(DWP_RESPONSE_LATE_REMINDER)) {
                        return setAppealReceivedDetails(personalisation, events.getValue());
                    } else if (ccdResponse.getNotificationType().equals(DWP_RESPONSE_RECEIVED) && events.getValue().getEventType().equals(DWP_RESPONSE_RECEIVED)) {
                        return setHearingContactDate(personalisation, events.getValue());
                    } else if (ccdResponse.getNotificationType().equals(POSTPONEMENT) && events.getValue().getEventType().equals(POSTPONEMENT)) {
                        return setPostponementDetails(personalisation, events.getValue());
                    }
                }
            }
        }
        return personalisation;
    }

    public Map<String, String> setEvidenceReceivedNotificationData(Map<String, String> personalisation, CcdResponse ccdResponse) {
        if (ccdResponse.getNotificationType().equals(EVIDENCE_RECEIVED)) {
            if (ccdResponse.getEvidence() != null && ccdResponse.getEvidence().getDocuments() != null && !ccdResponse.getEvidence().getDocuments().isEmpty()) {
                personalisation.put(EVIDENCE_RECEIVED_DATE_LITERAL, formatLocalDate(ccdResponse.getEvidence().getDocuments().get(0).getValue().getEvidenceDateTimeFormatted()));
            }
        }
        return personalisation;
    }

    private Map<String, String> setAppealReceivedDetails(Map<String, String> personalisation, Event event) {
        String dwpResponseDateString = formatLocalDate(event.getDateTime().plusDays(MAX_DWP_RESPONSE_DAYS).toLocalDate());
        personalisation.put(APPEAL_RESPOND_DATE, dwpResponseDateString);
        return personalisation;
    }

    private Map<String, String> setPostponementDetails(Map<String, String> personalisation, Event event) {
        setHearingContactDate(personalisation, event);
        return personalisation;
    }

    public Map<String, String> setEvidenceProcessingAddress(Map<String, String> personalisation, CcdResponse ccdResponse) {
        RegionalProcessingCenter rpc;

        if (null != ccdResponse.getRegionalProcessingCenter()) {
            rpc = ccdResponse.getRegionalProcessingCenter();
        } else {
            rpc = regionalProcessingCenterService.getByScReferenceCode(ccdResponse.getCaseReference());
        }
        personalisation.put(REGIONAL_OFFICE_NAME_LITERAL, rpc.getAddress1());
        personalisation.put(DEPARTMENT_NAME_LITERAL, DEPARTMENT_NAME_STRING);
        personalisation.put(SUPPORT_CENTRE_NAME_LITERAL, rpc.getAddress2());
        personalisation.put(ADDRESS_LINE_LITERAL, rpc.getAddress3());
        personalisation.put(TOWN_LITERAL, rpc.getAddress4());
        personalisation.put(COUNTY_LITERAL, rpc.getCity());
        personalisation.put(POSTCODE_LITERAL, rpc.getPostcode());

        return personalisation;
    }

    private String formatAddress(Hearing hearing) {
        return newArrayList(hearing.getValue().getVenue().getName(),
                hearing.getValue().getVenue().getAddress().getFullAddress())
                .stream()
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(", "));
    }

    private Map<String, String> setHearingContactDate(Map<String, String> personalisation, Event event) {
        String hearingContactDate = formatLocalDate(event.getDateTime().plusDays(MAX_HEARING_BOOKED_DAYS).toLocalDate());
        personalisation.put(HEARING_CONTACT_DATE, hearingContactDate);

        return personalisation;
    }

    private String calculateDaysToHearingText(LocalDate hearingDate) {
        Long daysBetween = ChronoUnit.DAYS.between(LocalDate.now(), hearingDate);

        String daysText = daysBetween == 1 ? DAY_STRING : DAYS_STRING;

        return "in " + daysBetween + daysText;
    }

    public String getMacToken(String id, String benefitType) {
        return macService.generateToken(id, benefitType);
    }

    private String formatLocalDate(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern(RESPONSE_DATE_FORMAT));
    }

    private String formatLocalTime(LocalDateTime date) {
        return date.format(DateTimeFormatter.ofPattern(HEARING_TIME_FORMAT));
    }

    public Template getTemplate(EventType type) {
        String smsTemplateId = isSendSmsSubscriptionConfirmation() ? SUBSCRIPTION_CREATED.getId() : type.getId();
        return config.getTemplate(type.getId(), smsTemplateId);
    }

    public Boolean isSendSmsSubscriptionConfirmation() {
        return sendSmsSubscriptionConfirmation;
    }

    public void setSendSmsSubscriptionConfirmation(Boolean sendSmsSubscriptionConfirmation) {
        this.sendSmsSubscriptionConfirmation = sendSmsSubscriptionConfirmation;
    }
}
