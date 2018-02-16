package uk.gov.hmcts.sscs.domain.notify;


public enum NotificationType {
    ADJOURNED("hearingAdjourned"),
    APPEAL_CREATED("appealCreated"),
    APPEAL_LAPSED("appealLapsed"),
    APPEAL_RECEIVED("appealReceived"),
    APPEAL_WITHDRAWN("appealWithdrawn"),
    DWP_RESPONSE_RECEIVED("responseReceived"),
    EVIDENCE_RECEIVED("evidenceReceived"),
    POSTPONEMENT("hearingPostponed"),
    SUBSCRIPTION_CREATED("subscriptionCreated"),
    SUBSCRIPTION_UPDATED("subscriptionUpdated");

    private String id;

    NotificationType(String id) {
        this.id = id;
    }

    public static NotificationType getNotificationById(String id) {
        NotificationType b = null;
        for (NotificationType type : NotificationType.values()) {
            if (type.getId().equals(id)) {
                b = type;
            }
        }
        return b;
    }

    public String getId() {
        return id;
    }

}
