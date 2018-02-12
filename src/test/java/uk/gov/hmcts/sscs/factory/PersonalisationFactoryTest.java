package uk.gov.hmcts.sscs.factory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.MockitoAnnotations.initMocks;
import static uk.gov.hmcts.sscs.domain.notify.NotificationType.APPEAL_RECEIVED;
import static uk.gov.hmcts.sscs.domain.notify.NotificationType.DWP_RESPONSE_RECEIVED;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import uk.gov.hmcts.sscs.config.NotificationConfig;
import uk.gov.hmcts.sscs.domain.notify.Personalisation;
import uk.gov.hmcts.sscs.placeholders.AppealReceivedPersonalisation;
import uk.gov.hmcts.sscs.placeholders.ResponseReceivedPersonalisation;

public class PersonalisationFactoryTest {

    private PersonalisationFactory factory;

    @Mock
    private NotificationConfig config;

    @Before
    public void setup() {
        initMocks(this);
        factory = new PersonalisationFactory(config);
    }

    @Test
    public void createAppealReceivedPersonalisationWhenAppealReceivedNotification() {
        Personalisation result = factory.apply(APPEAL_RECEIVED);
        assertEquals(AppealReceivedPersonalisation.class, result.getClass());
    }

    @Test
    public void createDwpResponseReceivedPersonalisationWhenDwpResponseReceivedNotification() {
        Personalisation result = factory.apply(DWP_RESPONSE_RECEIVED);
        assertEquals(ResponseReceivedPersonalisation.class, result.getClass());
    }

    @Test
    public void shouldReturnNullWhenNotificationTypeIsNull() {
        Personalisation personalisation = factory.apply(null);
        assertNull(personalisation);
    }
}
