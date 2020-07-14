package com.global.api.entities.enums;

public enum Channel implements IStringConstant {
    ClientPresent("CP"),
    ClientNotPresent("CNP");

    private String value;

    Channel(String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

    public byte[] getBytes() {
        return this.value.getBytes();
    }

}
