package uk.gov.hmcts.sscs;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import uk.gov.hmcts.sscs.domain.*;
import uk.gov.hmcts.sscs.domain.notify.Event;
import uk.gov.hmcts.sscs.domain.notify.EventType;

public final class CcdResponseUtils {
    private CcdResponseUtils() {
    }

    public static CcdResponse buildCcdResponse(String caseReference) {

        Hearing hearing = Hearing.builder().value(HearingDetails.builder()
            .hearingDate("2018-01-01")
            .time("12:00")
            .venue(Venue.builder()
                .name("The venue")
                .address(Address.builder()
                    .line1("12 The Road Avenue")
                    .line2("Village")
                    .town("Aberdeen")
                    .county("Aberdeenshire")
                    .postcode("AB12 0HN").build())
                .googleMapLink("http://www.googlemaps.com/aberdeenvenue")
                .build()).build()).build();


        List<Hearing> hearingsList = new ArrayList<>();
        hearingsList.add(hearing);

        Event event = Event.builder()
            .eventType(EventType.APPEAL_CREATED)
            .dateTime(ZonedDateTime.now())
            .build();

        Subscription appellantSubscription = Subscription.builder()
            .tya("")
            .email("")
            .mobile("")
            .subscribeEmail("No")
            .subscribeSms("No")
            .build();
        Subscription supporterSubscription = Subscription.builder()
            .tya("")
            .email("")
            .mobile("")
            .subscribeEmail("No")
            .subscribeSms("No")
            .build();
        Subscriptions subscriptions = Subscriptions.builder()
            .appellantSubscription(appellantSubscription)
            .supporterSubscription(supporterSubscription)
            .build();

        return CcdResponse.builder()
            .caseReference(caseReference)
            .hearings(hearingsList)
//            .events(Collections.singletonList(event))
            .subscriptions(subscriptions)
            .build();
    }
}
