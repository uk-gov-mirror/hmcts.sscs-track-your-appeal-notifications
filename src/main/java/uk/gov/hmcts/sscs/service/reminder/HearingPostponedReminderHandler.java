package uk.gov.hmcts.sscs.service.reminder;

import static org.slf4j.LoggerFactory.getLogger;
import static uk.gov.hmcts.sscs.domain.notify.EventType.HEARING_REMINDER;
import static uk.gov.hmcts.sscs.domain.notify.EventType.POSTPONEMENT;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.jobscheduler.services.JobNotFoundException;
import uk.gov.hmcts.reform.sscs.jobscheduler.services.JobRemover;
import uk.gov.hmcts.sscs.domain.CcdResponse;

@Component
public class HearingPostponedReminderHandler implements ReminderHandler {

    private static final org.slf4j.Logger LOG = getLogger(HearingPostponedReminderHandler.class);

    private final JobGroupGenerator jobGroupGenerator;
    private final JobRemover jobRemover;

    @Autowired
    public HearingPostponedReminderHandler(
        JobGroupGenerator jobGroupGenerator,
        JobRemover jobRemover
    ) {
        this.jobGroupGenerator = jobGroupGenerator;
        this.jobRemover = jobRemover;
    }

    public boolean canHandle(CcdResponse ccdResponse) {
        return ccdResponse
            .getNotificationType()
            .equals(POSTPONEMENT);
    }

    public void handle(CcdResponse ccdResponse) {
        if (!canHandle(ccdResponse)) {
            throw new IllegalArgumentException("cannot handle ccdResponse");
        }

        String caseId = ccdResponse.getCaseId();
        String jobGroup = jobGroupGenerator.generate(caseId, HEARING_REMINDER);

        try {
            jobRemover.removeGroup(jobGroup);
        } catch (JobNotFoundException ignore) {
            LOG.warn("Hearing reminder for case ID: " + caseId + " could not be found");
        }
    }

}
