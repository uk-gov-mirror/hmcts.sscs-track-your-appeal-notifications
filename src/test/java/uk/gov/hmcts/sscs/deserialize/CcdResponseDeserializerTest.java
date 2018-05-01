package uk.gov.hmcts.sscs.deserialize;

import static org.junit.Assert.*;
import static uk.gov.hmcts.sscs.config.AppConstants.ZONE_ID;
import static uk.gov.hmcts.sscs.domain.Benefit.PIP;
import static uk.gov.hmcts.sscs.domain.notify.EventType.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import uk.gov.hmcts.sscs.domain.*;
import uk.gov.hmcts.sscs.exception.BenefitMappingException;

public class CcdResponseDeserializerTest {

    private CcdResponseDeserializer ccdResponseDeserializer;
    private ObjectMapper mapper;

    @Before
    public void setup() {
        ccdResponseDeserializer = new CcdResponseDeserializer();
        mapper = new ObjectMapper();
    }

    @Test
    public void deserializeBenefitJson() throws IOException {

        String appealJson = "{\"benefitType\":{\"code\":\"PIP\"}}";

        Appeal appeal = Appeal.builder().build();

        ccdResponseDeserializer.deserializeBenefitDetailsJson(mapper.readTree(appealJson), appeal);

        assertEquals(PIP, appeal.getBenefit());
    }

    @Test(expected = BenefitMappingException.class)
    public void throwBenefitMappingExceptionWhenBenefitTypeUnknown() throws IOException {

        String appealJson = "{\"benefitType\":{\"code\":\"UNK\"}}";

        ccdResponseDeserializer.deserializeBenefitDetailsJson(mapper.readTree(appealJson), Appeal.builder().build());
    }

    @Test
    public void deserializeAppellantJson() throws IOException {

        String subscriptionJson = "{\"appellantSubscription\":{\"tya\":\"543212345\",\"email\":\"test@testing.com\",\"mobile\":\"01234556634\",\"reason\":null,\"subscribeSms\":\"No\",\"subscribeEmail\":\"Yes\"},"
                + "\"supporterSubscription\":{\"tya\":\"232929249492\",\"email\":\"supporter@live.co.uk\",\"mobile\":\"07925289702\",\"reason\":null,\"subscribeSms\":\"Yes\",\"subscribeEmail\":\"No\"}}";

        CcdResponse ccdResponse = CcdResponse.builder().build();

        ccdResponseDeserializer.deserializeSubscriptionJson(mapper.readTree(subscriptionJson), ccdResponse);

        Subscription appellantSubscription = ccdResponse.getSubscriptions().getAppellantSubscription();

        assertEquals("test@testing.com", appellantSubscription.getEmail());
        assertEquals("01234556634", appellantSubscription.getMobile());
        assertFalse(appellantSubscription.isSubscribeSms());
        assertTrue(appellantSubscription.isSubscribeEmail());

        Subscription supporterSubscription = ccdResponse.getSubscriptions().getSupporterSubscription();
        assertEquals("supporter@live.co.uk", supporterSubscription.getEmail());
        assertEquals("07925289702", supporterSubscription.getMobile());
        assertTrue(supporterSubscription.isSubscribeSms());
        assertFalse(supporterSubscription.isSubscribeEmail());
    }

    @Test
    public void deserializeEventJson() throws IOException {
        String eventJson = "{\"events\": [{\"id\": \"bad54ab0-5d09-47ab-b9fd-c3d55cbaf56f\",\"value\": {\"date\": \"2018-01-19T12:54:12.000\",\"description\": null,\"type\": \"appealReceived\"}}]}";

        CcdResponse ccdResponse = CcdResponse.builder().build();

        ccdResponseDeserializer.deserializeEventDetailsJson(mapper.readTree(eventJson), ccdResponse);

        assertEquals(1, ccdResponse.getEvents().size());
        assertEquals(ZonedDateTime.of(LocalDate.of(2018, 1, 19), LocalTime.of(12, 54, 12), ZoneId.of(ZONE_ID)), ccdResponse.getEvents().get(0).getValue().getDateTime());
        assertEquals(APPEAL_RECEIVED, ccdResponse.getEvents().get(0).getValue().getEventType());
    }

    @Test
    public void deserializeMultipleEventJsonInDescendingEventDateOrder() throws IOException {
        String eventJson = "{\"events\": [{\"id\": \"bad54ab0\",\"value\": {\"date\": \"2018-01-19T12:00:00.000\",\"description\": null,\"type\": \"appealReceived\"}},\n"
                + "{\"id\": \"12354ab0\",\"value\": {\"date\": \"2018-01-19T14:00:00.000\",\"description\": null,\"type\": \"appealWithdrawn\"}},\n"
                + "{\"id\": \"87564ab0\",\"value\": {\"date\": \"2018-01-19T13:00:00.000\",\"description\": null,\"type\": \"appealLapsed\"}}]}";

        CcdResponse ccdResponse = CcdResponse.builder().build();

        ccdResponseDeserializer.deserializeEventDetailsJson(mapper.readTree(eventJson), ccdResponse);

        assertEquals(3, ccdResponse.getEvents().size());

        assertEquals(ZonedDateTime.of(LocalDate.of(2018, 1, 19), LocalTime.of(14, 0), ZoneId.of(ZONE_ID)), ccdResponse.getEvents().get(0).getValue().getDateTime());
        assertEquals(APPEAL_WITHDRAWN, ccdResponse.getEvents().get(0).getValue().getEventType());
        assertEquals(ZonedDateTime.of(LocalDate.of(2018, 1, 19), LocalTime.of(13, 0), ZoneId.of(ZONE_ID)), ccdResponse.getEvents().get(1).getValue().getDateTime());
        assertEquals(APPEAL_LAPSED, ccdResponse.getEvents().get(1).getValue().getEventType());
        assertEquals(ZonedDateTime.of(LocalDate.of(2018, 1, 19), LocalTime.of(12, 0), ZoneId.of(ZONE_ID)), ccdResponse.getEvents().get(2).getValue().getDateTime());
        assertEquals(APPEAL_RECEIVED, ccdResponse.getEvents().get(2).getValue().getEventType());
    }

    @Test
    public void deserializeHearingJsonWithWinterHearingTime() throws IOException {
        String hearingJson = "{\"hearings\": [{\"id\": \"1234\",\"value\": {"
                + "\"hearingDate\": \"2018-01-12\",\"time\": \"11:00\",\"venue\": {"
                + "\"name\": \"Prudential House\",\"address\": {\"line1\": \"36 Dale Street\",\"line2\": \"\","
                + "\"town\": \"Liverpool\",\"county\": \"Merseyside\",\"postcode\": \"L2 5UZ\"},"
                + "\"googleMapLink\": \"https://www.google.com/theAddress\"}}}]}";

        CcdResponse ccdResponse = CcdResponse.builder().build();

        ccdResponseDeserializer.deserializeHearingDetailsJson(mapper.readTree(hearingJson), ccdResponse);

        assertEquals(1, ccdResponse.getHearings().size());

        Hearing hearing = ccdResponse.getHearings().get(0);
        assertEquals(LocalDateTime.of(LocalDate.of(2018, 1, 12), LocalTime.of(11, 00, 00)), hearing.getValue().getHearingDateTime());
        assertEquals("Prudential House", hearing.getValue().getVenue().getName());
        assertEquals("36 Dale Street", hearing.getValue().getVenue().getAddress().getLine1());
        assertEquals("Liverpool", hearing.getValue().getVenue().getAddress().getTown());
        assertEquals("Merseyside", hearing.getValue().getVenue().getAddress().getCounty());
        assertEquals("L2 5UZ", hearing.getValue().getVenue().getAddress().getPostcode());
        assertEquals("https://www.google.com/theAddress", hearing.getValue().getVenue().getGoogleMapLink());
    }

    @Test
    public void deserializeHearingJsonWithSummerHearingTime() throws IOException {
        String hearingJson = "{\"hearings\": [{\"id\": \"1234\",\"value\": {"
                + "\"hearingDate\": \"2018-07-12\",\"time\": \"11:00\",\"venue\": {"
                + "\"name\": \"Prudential House\",\"address\": {\"line1\": \"36 Dale Street\",\"line2\": \"\","
                + "\"town\": \"Liverpool\",\"county\": \"Merseyside\",\"postcode\": \"L2 5UZ\"},"
                + "\"googleMapLink\": \"https://www.google.com/theAddress\"}}}]}";

        CcdResponse ccdResponse = CcdResponse.builder().build();

        ccdResponseDeserializer.deserializeHearingDetailsJson(mapper.readTree(hearingJson), ccdResponse);

        assertEquals(1, ccdResponse.getHearings().size());

        Hearing hearing = ccdResponse.getHearings().get(0);
        assertEquals(LocalDateTime.of(LocalDate.of(2018, 7, 12), LocalTime.of(11, 00, 00)), hearing.getValue().getHearingDateTime());
    }

    @Test
    public void deserializeMultipleHearingJsonInDescendingHearingDateOrder() throws IOException {
        String hearingJson = "{\"hearings\": [{\"id\": \"1234\",\"value\": {"
                + "\"hearingDate\": \"2018-01-12\",\"time\": \"11:00\",\"venue\": {"
                + "\"name\": \"Prudential House\",\"address\": {\"line1\": \"36 Dale Street\",\"line2\": \"\","
                + "\"town\": \"Liverpool\",\"county\": \"Merseyside\",\"postcode\": \"L2 5UZ\"},"
                + "\"googleMapLink\": \"https://www.google.com/theAddress\"}}},"
                + "{\"id\": \"4567\",\"value\": {"
                + "\"hearingDate\": \"2018-01-12\",\"time\": \"13:00\",\"venue\": {"
                + "\"name\": \"Prudential House\",\"address\": {\"line1\": \"36 Dale Street\",\"line2\": \"\","
                + "\"town\": \"Liverpool\",\"county\": \"Merseyside\",\"postcode\": \"L2 5UZ\"},"
                + "\"googleMapLink\": \"https://www.google.com/theAddress\"}}},"
                + "{\"id\": \"9875\",\"value\": {"
                + "\"hearingDate\": \"2018-01-12\",\"time\": \"12:00\",\"venue\": {"
                + "\"name\": \"Prudential House\",\"address\": {\"line1\": \"36 Dale Street\",\"line2\": \"\","
                + "\"town\": \"Liverpool\",\"county\": \"Merseyside\",\"postcode\": \"L2 5UZ\"},"
                + "\"googleMapLink\": \"https://www.google.com/theAddress\"}}}"
                + "]}";
        CcdResponse ccdResponse = CcdResponse.builder().build();

        ccdResponseDeserializer.deserializeHearingDetailsJson(mapper.readTree(hearingJson), ccdResponse);

        assertEquals(3, ccdResponse.getHearings().size());

        assertEquals(LocalDateTime.of(LocalDate.of(2018, 1, 12), LocalTime.of(13, 0)), ccdResponse.getHearings().get(0).getValue().getHearingDateTime());
        assertEquals(LocalDateTime.of(LocalDate.of(2018, 1, 12), LocalTime.of(12, 0)), ccdResponse.getHearings().get(1).getValue().getHearingDateTime());
        assertEquals(LocalDateTime.of(LocalDate.of(2018, 1, 12), LocalTime.of(11, 0)), ccdResponse.getHearings().get(2).getValue().getHearingDateTime());
    }

    @Test
    public void deserializeAllCcdResponseJsonWithNewAndOldCcdData() throws IOException {

        String json = "{\"case_details\":{\"case_data\":{\"subscriptions\":{"
                + "\"appellantSubscription\":{\"tya\":\"543212345\",\"email\":\"test@testing.com\",\"mobile\":\"01234556634\",\"reason\":null,\"subscribeSms\":\"No\",\"subscribeEmail\":\"Yes\"},"
                + "\"supporterSubscription\":{\"tya\":\"232929249492\",\"email\":\"supporter@live.co.uk\",\"mobile\":\"07925289702\",\"reason\":null,\"subscribeSms\":\"Yes\",\"subscribeEmail\":\"No\"}},"
                + "\"caseReference\":\"SC/1234/23\",\"appeal\":{"
                + "\"appellant\":{\"name\":{\"title\":\"Mr\",\"lastName\":\"Vasquez\",\"firstName\":\"Dexter\",\"middleName\":\"Ali Sosa\"}},"
                + "\"supporter\":{\"name\":{\"title\":\"Mrs\",\"lastName\":\"Wilder\",\"firstName\":\"Amber\",\"middleName\":\"Clark Eaton\"}}}},"
                + "\"id\": \"123456789\"},"
                + "\"case_details_before\":{\"case_data\":{\"subscriptions\":{"
                + "\"appellantSubscription\":{\"tya\":\"123456\",\"email\":\"old@email.com\",\"mobile\":\"07543534345\",\"reason\":null,\"subscribeSms\":\"No\",\"subscribeEmail\":\"Yes\"},"
                + "\"supporterSubscription\":{\"tya\":\"232929249492\",\"email\":\"supporter@gmail.co.uk\",\"mobile\":\"07925267702\",\"reason\":null,\"subscribeSms\":\"Yes\",\"subscribeEmail\":\"No\"}},"
                + "\"caseReference\":\"SC/5432/89\",\"appeal\":{"
                + "\"appellant\":{\"name\":{\"title\":\"Mr\",\"lastName\":\"Smith\",\"firstName\":\"Jeremy\",\"middleName\":\"Rupert\"}},"
                + "\"supporter\":{\"name\":{\"title\":\"Mr\",\"lastName\":\"Redknapp\",\"firstName\":\"Harry\",\"middleName\":\"Winston\"}}}},"
                + "\"id\": \"523456789\"},"
                + "\"event_id\": \"appealReceived\"\n}";

        CcdResponseWrapper wrapper = mapper.readValue(json, CcdResponseWrapper.class);
        CcdResponse newCcdResponse = wrapper.getNewCcdResponse();
        Subscription newAppellantSubscription = newCcdResponse.getSubscriptions().getAppellantSubscription();

        Appellant newAppellant = newCcdResponse.getAppeal().getAppellant();
        assertEquals("Dexter", newAppellant.getName().getFirstName());
        assertEquals("Vasquez", newAppellant.getName().getLastName());
        assertEquals("Mr", newAppellant.getName().getTitle());

        assertEquals(APPEAL_RECEIVED, newCcdResponse.getNotificationType());
        assertEquals("test@testing.com", newAppellantSubscription.getEmail());
        assertEquals("01234556634", newAppellantSubscription.getMobile());
        assertFalse(newAppellantSubscription.isSubscribeSms());
        assertTrue(newAppellantSubscription.isSubscribeEmail());

        Subscription newSupporterSubscription = newCcdResponse.getSubscriptions().getSupporterSubscription();
        assertEquals("supporter@live.co.uk", newSupporterSubscription.getEmail());
        assertEquals("07925289702", newSupporterSubscription.getMobile());
        assertTrue(newSupporterSubscription.isSubscribeSms());
        assertFalse(newSupporterSubscription.isSubscribeEmail());
        assertEquals("SC/1234/23", newCcdResponse.getCaseReference());
        assertEquals("123456789", newCcdResponse.getCaseId());

        CcdResponse oldCcdResponse = wrapper.getOldCcdResponse();

        Appellant oldAppellant = oldCcdResponse.getAppeal().getAppellant();
        assertEquals("Jeremy", oldAppellant.getName().getFirstName());
        assertEquals("Smith", oldAppellant.getName().getLastName());
        assertEquals("Mr", oldAppellant.getName().getTitle());

        Subscription oldAppellantSubscription = oldCcdResponse.getSubscriptions().getAppellantSubscription();
        assertEquals("old@email.com", oldAppellantSubscription.getEmail());
        assertEquals("07543534345", oldAppellantSubscription.getMobile());
        assertFalse(oldAppellantSubscription.isSubscribeSms());
        assertTrue(oldAppellantSubscription.isSubscribeEmail());

        Subscription oldSupporterSubscription = oldCcdResponse.getSubscriptions().getSupporterSubscription();
        assertEquals("supporter@gmail.co.uk", oldSupporterSubscription.getEmail());
        assertEquals("07925267702", oldSupporterSubscription.getMobile());
        assertTrue(oldSupporterSubscription.isSubscribeSms());
        assertFalse(oldSupporterSubscription.isSubscribeEmail());
        assertEquals("SC/5432/89", oldCcdResponse.getCaseReference());
        assertEquals("523456789", oldCcdResponse.getCaseId());
    }

    @Test
    public void deserializeWithMissingCaseReference() throws IOException {
        String json = "{\"case_details\":{\"case_data\":{\"subscriptions\":{"
                + "\"appellantSubscription\":{\"tya\":\"543212345\",\"email\":\"test@testing.com\",\"mobile\":\"01234556634\",\"reason\":null,\"subscribeSms\":\"No\",\"subscribeEmail\":\"Yes\"},"
                + "\"supporterSubscription\":{\"tya\":\"232929249492\",\"email\":\"supporter@live.co.uk\",\"mobile\":\"07925289702\",\"reason\":null,\"subscribeSms\":\"Yes\",\"subscribeEmail\":\"No\"}},"
                + "\"appeal\":{"
                + "\"appellant\":{\"name\":{\"title\":\"Mr\",\"lastName\":\"Vasquez\",\"firstName\":\"Dexter\",\"middleName\":\"Ali Sosa\"}},"
                + "\"supporter\":{\"name\":{\"title\":\"Mrs\",\"lastName\":\"Wilder\",\"firstName\":\"Amber\",\"middleName\":\"Clark Eaton\"}}}}},\"event_id\": \"appealReceived\"\n}";

        CcdResponseWrapper wrapper = mapper.readValue(json, CcdResponseWrapper.class);

        assertNull(wrapper.getNewCcdResponse().getCaseReference());
    }

    @Test
    public void returnNodeWhenNodeIsPresent() {
        final JsonNodeFactory factory = JsonNodeFactory.instance;
        final ObjectNode node = factory.objectNode();
        final ObjectNode child = factory.objectNode();

        node.put("message", "test");
        child.set("child", node);
        assertEquals(node, ccdResponseDeserializer.getNode(child, "child"));
    }

    @Test
    public void returnNullWhenNodeIsNotPresent() {
        final JsonNodeFactory factory = JsonNodeFactory.instance;
        final ObjectNode child = factory.objectNode();

        child.put("message", "test");
        assertEquals(null, ccdResponseDeserializer.getNode(child, "somethingelse"));
    }

    @Test
    public void returnNullWhenNodeIsNull() {
        assertEquals(null, ccdResponseDeserializer.getNode(null, "somethingelse"));
    }

    @Test
    public void returnTextWhenFieldIsPresent() {
        final JsonNodeFactory factory = JsonNodeFactory.instance;
        final ObjectNode node = factory.objectNode();

        node.put("message", "test");
        assertEquals("test", ccdResponseDeserializer.getField(node, "message"));
    }

    @Test
    public void returnNullWhenFieldIsNotPresent() {
        final JsonNodeFactory factory = JsonNodeFactory.instance;
        final ObjectNode child = factory.objectNode();

        child.put("message", "test");
        assertEquals(null, ccdResponseDeserializer.getField(child, "somethingelse"));
    }

    @Test
    public void returnNullWhenFieldIsNull() {
        assertEquals(null, ccdResponseDeserializer.getField(null, "somethingelse"));
    }

    @Test
    public void shouldDeserializeRegionalProcessingCenterIfPresent() throws Exception {
        String rpcJson = "{\"regionalProcessingCenter\":{\"name\":\"CARDIFF\",\"address1\":\"HM Courts & Tribunals Service\","
                + "\"address2\":\"Social Security & Child Support Appeals\",\"address3\":\"Eastgate House\",\n"
                + "\"address4\":\"Newport Road\",\"city\":\"CARDIFF\",\"postcode\":\"CF24 0AB\",\"phoneNumber\":\"0300 123 1142\",\"faxNumber\":\"0870 739 4438\"}}";

        CcdResponse ccdResponse = CcdResponse.builder().build();
        ccdResponseDeserializer.deserializeRegionalProcessingCenterJson(mapper.readTree(rpcJson), ccdResponse);

        assertNotNull(ccdResponse.getRegionalProcessingCenter());

        RegionalProcessingCenter regionalProcessingCenter = ccdResponse.getRegionalProcessingCenter();

        assertEquals(regionalProcessingCenter.getName(), "CARDIFF");
        assertEquals(regionalProcessingCenter.getAddress1(), "HM Courts & Tribunals Service");
        assertEquals(regionalProcessingCenter.getAddress2(), "Social Security & Child Support Appeals");
        assertEquals(regionalProcessingCenter.getAddress3(), "Eastgate House");
        assertEquals(regionalProcessingCenter.getAddress4(), "Newport Road");
        assertEquals(regionalProcessingCenter.getCity(), "CARDIFF");
        assertEquals(regionalProcessingCenter.getPostcode(), "CF24 0AB");
        assertEquals(regionalProcessingCenter.getPhoneNumber(), "0300 123 1142");
        assertEquals(regionalProcessingCenter.getFaxNumber(), "0870 739 4438");
    }

    @Test
    public void shouldNotDeserializeRegionalProcessingCenterIfItsNotPresent() throws Exception {
        String json = "{\"benefitType\":{\"code\":\"UNK\"}}";
        CcdResponse ccdResponse = CcdResponse.builder().build();

        ccdResponseDeserializer.deserializeRegionalProcessingCenterJson(mapper.readTree(json), ccdResponse);

        assertNull(ccdResponse.getRegionalProcessingCenter());
    }

    @Test
    public void shouldDeserializeMrnDetails() throws Exception {
        String mrnJson = "{\"mrnDetails\":{\"dwpIssuingOffice\":\"Birmingham\",\"mrnDate\":\"2018-01-01\","
                + "\"mrnLateReason\":\"It is late\",\"mrnMissingReason\":\"It went missing\"}}";

        Appeal appeal = Appeal.builder().build();
        ccdResponseDeserializer.deserializeMrnDetailsJson(mapper.readTree(mrnJson), appeal);

        MrnDetails mrnDetails = appeal.getMrnDetails();

        assertEquals("Birmingham", mrnDetails.getDwpIssuingOffice());
        assertEquals("2018-01-01", mrnDetails.getMrnDate());
        assertEquals("It is late", mrnDetails.getMrnLateReason());
        assertEquals("It went missing", mrnDetails.getMrnMissingReason());
    }

    @Test
    public void shouldDeserializeAppellantDetails() throws Exception {
        String appellantJson = "{\"appellant\":{\"name\":{\"title\":\"Mr\",\"lastName\":\"Vasquez\",\"firstName\":\"Dexter\"},"
                + "\"address\": {\"line1\": \"36 Dale Street\",\"line2\": \"Village\","
                + "\"town\": \"Liverpool\",\"county\": \"Merseyside\",\"postcode\": \"L2 5UZ\"},"
                + "\"contact\": {\"email\": \"test@tester.com\", \"phone\": \"01435550606\", \"mobile\": \"07848484848\"},"
                + "\"identity\": {\"dob\": \"1998-07-01\", \"nino\": \"JT098230B\"},"
                + "\"isAppointee\": \"Yes\"}}";

        Appeal appeal = Appeal.builder().build();
        ccdResponseDeserializer.deserializeAppellantDetailsJson(mapper.readTree(appellantJson), appeal);

        Appellant appellant = appeal.getAppellant();

        assertEquals("Mr", appellant.getName().getTitle());
        assertEquals("Dexter", appellant.getName().getFirstName());
        assertEquals("Vasquez", appellant.getName().getLastName());
        assertEquals("36 Dale Street", appellant.getAddress().getLine1());
        assertEquals("Village", appellant.getAddress().getLine2());
        assertEquals("Liverpool", appellant.getAddress().getTown());
        assertEquals("Merseyside", appellant.getAddress().getCounty());
        assertEquals("L2 5UZ", appellant.getAddress().getPostcode());
        assertEquals("test@tester.com", appellant.getContact().getEmail());
        assertEquals("01435550606", appellant.getContact().getPhone());
        assertEquals("07848484848", appellant.getContact().getMobile());
        assertEquals("1998-07-01", appellant.getIdentity().getDob());
        assertEquals("JT098230B", appellant.getIdentity().getNino());
        assertEquals("Yes", appellant.getIsAppointee());
    }

    @Test
    public void shouldDeserializeHearingOptionsDetails() throws Exception {
        String hearingOptionsDetailsJson = "{\"hearingOptions\":{\"wantsToAttend\":\"Yes\",\"wantsSupport\":\"No\",\"languageInterpreter\":\"Yes\","
                + "\"languages\": \"French\",\"scheduleHearing\": \"Yes\","
                + "\"other\": \"Bla\",\"arrangements\": [\"signLanguageInterpreter\",\"hearingLoop\"],"
                + "\"excludeDates\": [{\"value\": {\"start\": \"2018-04-04\",\"end\": \"2018-04-06\"}},"
                + "{\"value\": {\"start\": \"2018-04-10\"}}]}}";

        Appeal appeal = Appeal.builder().build();
        ccdResponseDeserializer.deserializeHearingOptionsJson(mapper.readTree(hearingOptionsDetailsJson), appeal);

        HearingOptions hearingOptions = appeal.getHearingOptions();

        List<String> arrangements = new ArrayList<>();
        arrangements.add("signLanguageInterpreter");
        arrangements.add("hearingLoop");

        assertEquals("Yes", hearingOptions.getWantsToAttend());
        assertEquals("No", hearingOptions.getWantsSupport());
        assertEquals("Yes", hearingOptions.getLanguageInterpreter());
        assertEquals("French", hearingOptions.getLanguages());
        assertEquals("Yes", hearingOptions.getScheduleHearing());
        assertEquals("Bla", hearingOptions.getOther());
        assertEquals(arrangements, hearingOptions.getArrangements());

        assertEquals("2018-04-04", hearingOptions.getExcludeDates().get(0).getValue().getStart());
        assertEquals("2018-04-06", hearingOptions.getExcludeDates().get(0).getValue().getEnd());
        assertEquals("2018-04-10", hearingOptions.getExcludeDates().get(1).getValue().getStart());
        assertNull(hearingOptions.getExcludeDates().get(1).getValue().getEnd());
    }

    @Test
    public void shouldDeserializeAppealReasonDetails() throws Exception {
        String appealReasonsJson = "{\"appealReasons\": {\"reasons\": [{\"value\": {\"reason\": \"reason1\",\"description\": \"description1\"}},"
                +"{\"value\": {\"reason\": \"reason2\",\"description\": \"description2\"}}],\"otherReasons\": \"Another reason\"}}";

        Appeal appeal = Appeal.builder().build();
        ccdResponseDeserializer.deserializeAppealReasonsJson(mapper.readTree(appealReasonsJson), appeal);

        AppealReasons appealReasons = appeal.getAppealReasons();

        assertEquals("reason1", appealReasons.getReasons().get(0).getValue().getReason());
        assertEquals("description1", appealReasons.getReasons().get(0).getValue().getDescription());
        assertEquals("reason2", appealReasons.getReasons().get(1).getValue().getReason());
        assertEquals("description2", appealReasons.getReasons().get(1).getValue().getDescription());
        assertEquals("Another reason", appealReasons.getOtherReasons());
    }

    @Test
    public void shouldDeserializeRepresentativeDetails() throws Exception {
        String appealReasonsJson = "{\"rep\": {\"hasRepresentative\": \"Yes\",\"name\": {"
                + "\"title\": \"Mr\",\"firstName\": \"Harry\",\"lastName\": \"Potter\"},\n"
                + "\"address\": {\"line1\": \"123 Hairy Lane\",\"line2\": \"Off Hairy Park\",\"town\": \"Town\",\n"
                + "\"county\": \"County\",\"postcode\": \"CM14 4LQ\"},\n"
                + "\"contact\": {\"email\": \"harry.potter@wizards.com\",\"phone\": \"07987877873\",\"mobile\": \"07411999999\"},"
                + "\"organisation\": \"HP Ltd\"}}}";

        Appeal appeal = Appeal.builder().build();
        ccdResponseDeserializer.deserializeRepresentativeReasons(mapper.readTree(appealReasonsJson), appeal);

        Representative rep = appeal.getRep();

        assertEquals("Mr", rep.getName().getTitle());
        assertEquals("Harry", rep.getName().getFirstName());
        assertEquals("Potter", rep.getName().getLastName());
        assertEquals("123 Hairy Lane", rep.getAddress().getLine1());
        assertEquals("Off Hairy Park", rep.getAddress().getLine2());
        assertEquals("Town", rep.getAddress().getTown());
        assertEquals("County", rep.getAddress().getCounty());
        assertEquals("CM14 4LQ", rep.getAddress().getPostcode());
        assertEquals("07987877873", rep.getContact().getPhone());
        assertEquals("07411999999", rep.getContact().getMobile());
        assertEquals("harry.potter@wizards.com", rep.getContact().getEmail());
        assertEquals("HP Ltd", rep.getOrganisation());
    }

    @Test
    public void shouldDeserializeAppealSignerDetails() throws Exception {
        String appealJson = "{\"signer\":\"Yes\"}";

        CcdResponse ccdResponse = CcdResponse.builder().build();
        ccdResponseDeserializer.deserializeAppealDetailsJson(mapper.readTree(appealJson), ccdResponse);

        assertEquals("Yes", ccdResponse.getAppeal().getSigner());
    }

    @Test
    public void deserializeAllCcdResponseJson() throws IOException {

        String path = getClass().getClassLoader().getResource("json/ccdCallbackResponse.json").getFile();
        String json = FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8.name());

        CcdResponseWrapper wrapper = mapper.readValue(json, CcdResponseWrapper.class);
        CcdResponse ccdResponse = wrapper.getNewCcdResponse();

        Subscription appellantSubscription = ccdResponse.getSubscriptions().getAppellantSubscription();

        assertEquals(APPEAL_RECEIVED, ccdResponse.getNotificationType());
        assertEquals("updatedemail@hmcts.net", appellantSubscription.getEmail());
        assertEquals("07985233301", appellantSubscription.getMobile());
        assertTrue(appellantSubscription.isSubscribeSms());
        assertTrue(appellantSubscription.isSubscribeEmail());

        Appeal appeal = ccdResponse.getAppeal();
        assertEquals(PIP, appeal.getBenefit());

        MrnDetails mrnDetails = appeal.getMrnDetails();
        assertEquals("Birmingham", mrnDetails.getDwpIssuingOffice());
        assertEquals("2018-01-01", mrnDetails.getMrnDate());
        assertEquals("It is late", mrnDetails.getMrnLateReason());
        assertEquals("It went missing", mrnDetails.getMrnMissingReason());

        Appellant appellant = appeal.getAppellant();
        assertEquals("Dexter", appellant.getName().getFirstName());
        assertEquals("Vasquez", appellant.getName().getLastName());
        assertEquals("Mr", appellant.getName().getTitle());
        assertEquals("36 Dale Street", appellant.getAddress().getLine1());
        assertEquals("Village", appellant.getAddress().getLine2());
        assertEquals("Liverpool", appellant.getAddress().getTown());
        assertEquals("Merseyside", appellant.getAddress().getCounty());
        assertEquals("L2 5UZ", appellant.getAddress().getPostcode());
        assertEquals("test@tester.com", appellant.getContact().getEmail());
        assertEquals("07848484848", appellant.getContact().getMobile());
        assertEquals("01435550606", appellant.getContact().getPhone());
        assertEquals("JT098230B", appellant.getIdentity().getNino());
        assertEquals("1998-07-01", appellant.getIdentity().getDob());
        assertEquals("Yes", appellant.getIsAppointee());

        List<String> arrangements = new ArrayList<>();
        arrangements.add("signLanguageInterpreter");
        arrangements.add("hearingLoop");

        HearingOptions hearingOptions = appeal.getHearingOptions();
        assertEquals("Yes", hearingOptions.getWantsToAttend());
        assertEquals("No", hearingOptions.getWantsSupport());
        assertEquals("Yes", hearingOptions.getLanguageInterpreter());
        assertEquals("French", hearingOptions.getLanguages());
        assertEquals("Yes", hearingOptions.getScheduleHearing());
        assertEquals("Bla", hearingOptions.getOther());
        assertEquals(arrangements, hearingOptions.getArrangements());

        assertEquals("2018-04-04", hearingOptions.getExcludeDates().get(0).getValue().getStart());
        assertEquals("2018-04-06", hearingOptions.getExcludeDates().get(0).getValue().getEnd());
        assertEquals("2018-04-10", hearingOptions.getExcludeDates().get(1).getValue().getStart());
        assertNull(hearingOptions.getExcludeDates().get(1).getValue().getEnd());

        AppealReasons appealReasons = appeal.getAppealReasons();
        assertEquals("reason1", appealReasons.getReasons().get(0).getValue().getReason());
        assertEquals("description1", appealReasons.getReasons().get(0).getValue().getDescription());
        assertEquals("reason2", appealReasons.getReasons().get(1).getValue().getReason());
        assertEquals("description2", appealReasons.getReasons().get(1).getValue().getDescription());
        assertEquals("Another reason", appealReasons.getOtherReasons());

        Representative rep = appeal.getRep();

        assertEquals("Mr", rep.getName().getTitle());
        assertEquals("Harry", rep.getName().getFirstName());
        assertEquals("Potter", rep.getName().getLastName());
        assertEquals("123 Hairy Lane", rep.getAddress().getLine1());
        assertEquals("Off Hairy Park", rep.getAddress().getLine2());
        assertEquals("Town", rep.getAddress().getTown());
        assertEquals("County", rep.getAddress().getCounty());
        assertEquals("CM14 4LQ", rep.getAddress().getPostcode());
        assertEquals("07987877873", rep.getContact().getPhone());
        assertEquals("07411999999", rep.getContact().getMobile());
        assertEquals("harry.potter@wizards.com", rep.getContact().getEmail());
        assertEquals("HP Ltd", rep.getOrganisation());

        assertEquals("Yes", ccdResponse.getAppeal().getSigner());

        Subscription supporterSubscription = ccdResponse.getSubscriptions().getSupporterSubscription();
        assertEquals("supporter@hmcts.net", supporterSubscription.getEmail());
        assertEquals("07983469702", supporterSubscription.getMobile());
        assertTrue(supporterSubscription.isSubscribeSms());
        assertFalse(supporterSubscription.isSubscribeEmail());
        assertEquals("SC/1234/23", ccdResponse.getCaseReference());

        Hearing hearing = ccdResponse.getHearings().get(0);
        assertEquals("Prudential House", hearing.getValue().getVenue().getName());
        assertEquals("36 Dale Street", hearing.getValue().getVenue().getAddress().getLine1());
        assertEquals("Liverpool", hearing.getValue().getVenue().getAddress().getTown());
        assertEquals("Merseyside", hearing.getValue().getVenue().getAddress().getCounty());
        assertEquals("L2 5UZ", hearing.getValue().getVenue().getAddress().getPostcode());
        assertEquals("https://www.google.com/theAddress", hearing.getValue().getVenue().getGoogleMapLink());
        assertEquals("12345656789", ccdResponse.getCaseId());
        assertNotNull(ccdResponse.getRegionalProcessingCenter());
    }
}