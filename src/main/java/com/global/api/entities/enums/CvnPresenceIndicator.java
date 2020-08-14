package com.global.api.entities.enums;

public enum CvnPresenceIndicator {
    Present("1"),           // Indicates CVN was present.
    Illegible("2"),         // Indicates CVN was present but illegible.
    NotOnCard("3"),         // Indicates CVN was not present.
    NotRequested("4");      // Indicates CVN was not requested.

    private String value;
    CvnPresenceIndicator(String value){
        this.value = value;
    }
    public String getValue() { return this.value; }
}
