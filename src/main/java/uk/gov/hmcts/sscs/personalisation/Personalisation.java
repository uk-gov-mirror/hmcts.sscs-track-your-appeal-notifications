package uk.gov.hmcts.sscs.personalisation;

import static com.google.common.collect.Lists.newArrayList;
import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.hmcts.sscs.config.AppConstants.*;
import static uk.gov.hmcts.sscs.domain.notify.EventType.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.sscs.config.NotificationConfig;
import uk.gov.hmcts.sscs.domain.*;
import uk.gov.hmcts.sscs.domain.notify.Event;
import uk.gov.hmcts.sscs.domain.notify.EventType;
import uk.gov.hmcts.sscs.domain.notify.Template;
import uk.gov.hmcts.sscs.service.MessageAuthenticationServiceImpl;
import uk.gov.hmcts.sscs.service.RegionalProcessingCenterService;

@Component
public class Personalisation {

    private boolean sendSmsSubscriptionConfirmation;

    private static final org.slf4j.Logger LOG = getLogger(Personalisation.class);

    @Autowired
    private NotificationConfig config;

    @Autowired
    private MessageAuthenticationServiceImpl macService;

    @Autowired
    private RegionalProcessingCenterService regionalProcessingCenterService;

    public Map<String, String> create(CcdResponseWrapper responseWrapper) {
        CcdResponse ccdResponse = responseWrapper.getNewCcdResponse();
        Map<String, String> personalisation = new HashMap<>();
        Subscription appellantSubscription = ccdResponse.getSubscriptions().getAppellantSubscription();
        personalisation.put(BENEFIT_NAME_ACRONYM_LITERAL, ccdResponse.getBenefitType().name() + " benefit");
        personalisation.put(BENEFIT_FULL_NAME_LITERAL, ccdResponse.getBenefitType().getDescription());
        personalisation.put(APPEAL_REF, ccdResponse.getCaseReference());
        personalisation.put(APPELLANT_NAME, String.format("%s %s", appellantSubscription.getFirstName(), appellantSubscription.getSurname()));
        personalisation.put(PHONE_NUMBER, config.getHmctsPhoneNumber());

        if (ccdResponse.getSubscriptions().getAppellantSubscription().getAppealNumber() != null) {
            personalisation.put(APPEAL_ID, ccdResponse.getSubscriptions().getAppellantSubscription().getAppealNumber());
            personalisation.put(MANAGE_EMAILS_LINK_LITERAL, config.getManageEmailsLink().replace(MAC_LITERAL,
                    getMacToken(ccdResponse.getSubscriptions().getAppellantSubscription().getAppealNumber(),
                            ccdResponse.getBenefitType().name())));
            personalisation.put(TRACK_APPEAL_LINK_LITERAL, config.getTrackAppealLink() != null ? config.getTrackAppealLink().replace(APPEAL_ID_LITERAL, appellantSubscription.getAppealNumber()) : null);
            personalisation.put(SUBMIT_EVIDENCE_LINK_LITERAL, config.getEvidenceSubmissionInfoLink().replace(APPEAL_ID, appellantSubscription.getAppealNumber()));
            personalisation.put(SUBMIT_EVIDENCE_INFO_LINK_LITERAL, config.getEvidenceSubmissionInfoLink().replace(APPEAL_ID_LITERAL, appellantSubscription.getAppealNumber()));
            personalisation.put(CLAIMING_EXPENSES_LINK_LITERAL, config.getClaimingExpensesLink().replace(APPEAL_ID, appellantSubscription.getAppealNumber()));
            personalisation.put(HEARING_INFO_LINK_LITERAL,
                    config.getHearingInfoLink().replace(APPEAL_ID_LITERAL, appellantSubscription.getAppealNumber()));
        }

        personalisation.put(FIRST_TIER_AGENCY_ACRONYM, DWP_ACRONYM);
        personalisation.put(FIRST_TIER_AGENCY_FULL_NAME, DWP_FUL_NAME);

        if (ccdResponse.getHearings() != null && !ccdResponse.getHearings().isEmpty()) {
            Hearing latestHearing = ccdResponse.getHearings().get(0);

            personalisation.put(HEARING_DATE, formatLocalDate(latestHearing.getHearingDateTime().toLocalDate()));
            personalisation.put(HEARING_TIME, formatLocalTime(latestHearing.getHearingDateTime()));
            personalisation.put(VENUE_ADDRESS_LITERAL, formatAddress(latestHearing));
            personalisation.put(VENUE_MAP_LINK_LITERAL, latestHearing.getVenueGoogleMapUrl());
        }

        //FIXME: Random NPE being thrown so logging the object value
        LOG.info("regionalProcessingCenterService value: " + regionalProcessingCenterService);
        if (config.isJobSchedulerEnabled()) {
            setEvidenceProcessingAddress(personalisation, ccdResponse.getCaseReference());
        }

        setEventData(personalisation, ccdResponse);
        setEvidenceReceivedNotificationData(personalisation, ccdResponse);

        return personalisation;
    }

    public Map<String, String> setEventData(Map<String, String> personalisation, CcdResponse ccdResponse) {
        if (ccdResponse.getEvents() != null) {

            for (Event event : ccdResponse.getEvents()) {
                if (ccdResponse.getNotificationType().equals(APPEAL_RECEIVED) && event.getEventType().equals(APPEAL_RECEIVED)) {
                    return setAppealReceivedDetails(personalisation, event);
                } else if (ccdResponse.getNotificationType().equals(POSTPONEMENT) && event.getEventType().equals(POSTPONEMENT)) {
                    return setPostponementDetails(personalisation, event);
                }
            }
        }
        return personalisation;
    }

    public Map<String, String> setEvidenceReceivedNotificationData(Map<String, String> personalisation, CcdResponse ccdResponse) {
        if (ccdResponse.getNotificationType().equals(EVIDENCE_RECEIVED)) {
            if (ccdResponse.getEvidences() != null && !ccdResponse.getEvidences().isEmpty()) {
                personalisation.put(EVIDENCE_RECEIVED_DATE_LITERAL, formatLocalDate(ccdResponse.getEvidences().get(0).getDateReceived()));
            }
        }
        return personalisation;
    }

    private Map<String, String> setAppealReceivedDetails(Map<String, String> personalisation, Event event) {
        String dwpResponseDateString = formatLocalDate(event.getDateTime().plusDays(MAX_DWP_RESPONSE_DAYS).toLocalDate());
        personalisation.put(APPEAL_RESPOND_DATE, dwpResponseDateString);
        setHearingContactDate(personalisation, event);
        return personalisation;
    }

    private Map<String, String> setPostponementDetails(Map<String, String> personalisation, Event event) {
        setHearingContactDate(personalisation, event);
        return personalisation;
    }

    private Map<String, String> setEvidenceProcessingAddress(Map<String, String> personalisation, String appealNumber) {
        RegionalProcessingCenter rpc = regionalProcessingCenterService.getByScReferenceCode(appealNumber);
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
        return newArrayList(hearing.getVenueName(),
                hearing.getVenueAddressLine1(),
                hearing.getVenueAddressLine2(),
                hearing.getVenueTown(),
                hearing.getVenueCounty(),
                hearing.getVenuePostcode())
                .stream()
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(", "));
    }

    private  Map<String, String> setHearingContactDate(Map<String, String> personalisation, Event event) {
        String hearingContactDate = formatLocalDate(event.getDateTime().plusDays(42).toLocalDate());
        personalisation.put(HEARING_CONTACT_DATE, hearingContactDate);

        return personalisation;
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
