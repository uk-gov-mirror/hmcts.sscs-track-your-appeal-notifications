package uk.gov.hmcts.sscs.domain;

import java.time.LocalDateTime;

public class Hearing implements Comparable<Hearing> {

    private LocalDateTime hearingDateTime;
    private String venueName;
    private String venueAddressLine1;
    private String venueAddressLine2;
    private String venueTown;
    private String venueCounty;
    private String venuePostcode;
    private String venueGoogleMapUrl;

    public LocalDateTime getHearingDateTime() {
        return hearingDateTime;
    }

    public void setHearingDateTime(LocalDateTime hearingDateTime) {
        this.hearingDateTime = hearingDateTime;
    }

    public String getVenueName() {
        return venueName;
    }

    public void setVenueName(String venueName) {
        this.venueName = venueName;
    }

    public String getVenueAddressLine1() {
        return venueAddressLine1;
    }

    public void setVenueAddressLine1(String venueAddressLine1) {
        this.venueAddressLine1 = venueAddressLine1;
    }

    public String getVenueAddressLine2() {
        return venueAddressLine2;
    }

    public void setVenueAddressLine2(String venueAddressLine2) {
        this.venueAddressLine2 = venueAddressLine2;
    }

    public String getVenueTown() {
        return venueTown;
    }

    public void setVenueTown(String venueTown) {
        this.venueTown = venueTown;
    }

    public String getVenueCounty() {
        return venueCounty;
    }

    public void setVenueCounty(String venueCounty) {
        this.venueCounty = venueCounty;
    }

    public String getVenuePostcode() {
        return venuePostcode;
    }

    public void setVenuePostcode(String venuePostcode) {
        this.venuePostcode = venuePostcode;
    }

    public String getVenueGoogleMapUrl() {
        return venueGoogleMapUrl;
    }

    public void setVenueGoogleMapUrl(String venueGoogleMapUrl) {
        this.venueGoogleMapUrl = venueGoogleMapUrl;
    }

    @Override
    public String toString() {
        return "Hearing{"
                + " hearingDateTime='" + hearingDateTime + '\''
                + ", venueName='" + venueName + '\''
                + ", venueAddressLine1='" + venueAddressLine1 + '\''
                + ", venueAddressLine2='" + venueAddressLine2 + '\''
                + ", venueTown='" + venueTown + '\''
                + ", venueCounty='" + venueCounty + '\''
                + ", venuePostcode='" + venuePostcode + '\''
                + ", venueGoogleMapUrl='" + venueGoogleMapUrl + '\''
                + '}';
    }

    @Override
    public int compareTo(Hearing o) {
        return getHearingDateTime().compareTo(o.getHearingDateTime());
    }
}
