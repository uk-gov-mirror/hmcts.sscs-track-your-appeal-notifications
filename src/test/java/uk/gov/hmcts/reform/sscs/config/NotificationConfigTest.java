package uk.gov.hmcts.reform.sscs.config;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.sscs.config.AppealHearingType.ONLINE;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.converters.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.core.env.Environment;
import uk.gov.hmcts.reform.sscs.ccd.domain.Benefit;
import uk.gov.hmcts.reform.sscs.ccd.domain.LanguagePreference;
import uk.gov.hmcts.reform.sscs.domain.notify.Template;

@RunWith(JUnitParamsRunner.class)
public class NotificationConfigTest {

    private final Environment env = mock(Environment.class);

    @Test
    @Parameters({
            "emailTemplateName, notification.english.emailTemplateName.emailId, emailTemplateId, smsTemplateName, notification.english.smsTemplateName.smsId, smsTemplateId, letterTemplateName, notification.english.letterTemplateName.letterId, letterTemplateId, notification.english.letterTemplateName.docmosisId, docmosisTemplateId, docmosisTemplateId, validAppeal",
            "emailTemplateName, notification.english.online.emailTemplateName.emailId, onlineEmailTemplateId, smsTemplateName, notification.english.online.smsTemplateName.smsId, onlineSmsTemplateId, appealReceived, notification.english.online.appealReceived.letterId, onlineLetterTemplateId, notification.english.online.appealReceived.docmosisId, docmosisTemplateId, docmosisTemplateId, readyToList",
            "emailTemplateName, notification.english.online.emailTemplateName.emailId, onlineEmailTemplateId, smsTemplateName, notification.english.online.smsTemplateName.smsId, onlineSmsTemplateId, appealReceived, notification.english.online.appealReceived.letterId, onlineLetterTemplateId, notification.english.online.appealReceived.docmosisId, docmosisTemplateId, null, validAppeal"
    })
    public void getDefaultTemplate(String emailTemplateName, String emailTemplateKey, String emailTemplateId,
                                   String smsTemplateName, String smsTemplateKey, String smsTemplateId,
                                   String letterTemplateName, String letterTemplateKey, String letterTemplateId,
                                   String docmosisTemplateKey, String docmosisTemplateId, @Nullable String expectedDocmosisTemplateId, String createdInGapsFrom) {

        when(env.getProperty(emailTemplateKey)).thenReturn(emailTemplateId);
        when(env.containsProperty(emailTemplateKey)).thenReturn(true);
        when(env.getProperty(smsTemplateKey)).thenReturn(smsTemplateId);
        when(env.containsProperty(smsTemplateKey)).thenReturn(true);
        when(env.getProperty(letterTemplateKey)).thenReturn(letterTemplateId);
        when(env.getProperty(docmosisTemplateKey)).thenReturn(docmosisTemplateId);
        when(env.containsProperty(letterTemplateKey)).thenReturn(true);
        when(env.getProperty("feature.docmosis_leters.letterTemplateName_on")).thenReturn("true");

        Template template = new NotificationConfig(env).getTemplate(emailTemplateName, smsTemplateName, letterTemplateName, letterTemplateName, Benefit.PIP, ONLINE, createdInGapsFrom, LanguagePreference.ENGLISH);

        assertThat(template.getEmailTemplateId(), is(emailTemplateId));
        assertThat(template.getSmsTemplateId(), is(smsTemplateId));
        assertThat(template.getLetterTemplateId(), is(letterTemplateId));
        assertThat(template.getDocmosisTemplateId(), is(expectedDocmosisTemplateId));
    }
}