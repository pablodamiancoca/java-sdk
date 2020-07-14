package com.global.api.tests.gpapi;

import com.global.api.ServicesContainer;
import com.global.api.entities.Transaction;
import com.global.api.entities.enums.Channel;
import com.global.api.entities.exceptions.ApiException;
import com.global.api.paymentMethods.CreditCardData;
import com.global.api.paymentMethods.CreditTrackData;
import com.global.api.serviceConfigs.GpApiConfig;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.*;

public class GpApiCreditTests {

    private final CreditCardData card;

    public GpApiCreditTests() throws ApiException {

        GpApiConfig config = new GpApiConfig();

        // GP-API settings
        config.setServiceUrl("https://apis.sandbox.globalpay.com/ucp");
        config.setEnableLogging(true);

        config
                .setAppId("OWTP5ptQZKGj7EnvPt3uqO844XDBt8Oj")
                .setAppKey("qM31FmlFiyXRHGYh")
                .setChannel(Channel.ClientNotPresent.getValue());

        ServicesContainer.configureService(config, "GpApiConfig");

        card = new CreditCardData();
        card.setNumber("4263970000005262");
        card.setExpMonth(5);
        card.setExpYear(2025);
        card.setCvn("852");
    }

    @Test
    public void creditAuthorization() throws ApiException {

        Transaction authorization =
                card
                        .authorize(new BigDecimal(14))
                        .withCurrency("USD")
                        .withAllowDuplicates(true)
                        .execute("GpApiConfig");

        assertNotNull(authorization);
    }

    @Test
    public void creditCapture() throws ApiException {

        Transaction authorization =
                card
                        .authorize(new BigDecimal(14))
                        .withCurrency("USD")
                        .withAllowDuplicates(true)
                        .execute("GpApiConfig");

        assertNotNull(authorization);

        Transaction capture =
                authorization
                        .capture(new BigDecimal(16))
                        .withGratuity(new BigDecimal(2))
                        .execute("GpApiConfig");

        assertNotNull(capture);
    }

    @Test
    public void creditSale() throws ApiException {
        Transaction sale =
                card
                        .charge(new BigDecimal("19.99"))
                        .withCurrency("USD")
                        .execute("GpApiConfig");

        assertNotNull(sale);
    }

    @Test
    public void creditRefund() throws ApiException {
        Transaction refund =
                card
                        .refund(new BigDecimal(16))
                        .withCurrency("USD")
                        .withAllowDuplicates(true)
                        .execute("GpApiConfig");

        assertNotNull(refund);
    }

    @Test
    public void creditRefundTransaction() throws ApiException {
        Transaction charge =
                card
                        .charge(new BigDecimal("10.95"))
                        .withCurrency("USD")
                        .withAllowDuplicates(true)
                        .execute("GpApiConfig");

        assertNotNull(charge);

        Transaction refund =
                charge
                        .refund(new BigDecimal("10.95"))
                        .withCurrency("USD")
                        .execute("GpApiConfig");

        assertNotNull(refund);
    }

    @Test
    public void creditReverseTransaction() throws ApiException {
        Transaction charge =
                card
                        .charge(new BigDecimal("12.99"))
                        .withCurrency("USD")
                        .withAllowDuplicates(true)
                        .execute("GpApiConfig");

        assertNotNull(charge);

        Transaction reverse =
                charge
                        .reverse(new BigDecimal("12.99"))
                        .execute("GpApiConfig");

        assertNotNull(reverse);
    }

}