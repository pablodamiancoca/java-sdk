package com.global.api.tests.gpapi;

import com.global.api.ServicesContainer;
import com.global.api.entities.Transaction;
import com.global.api.entities.exceptions.ApiException;
import com.global.api.entities.exceptions.BuilderException;
import com.global.api.entities.exceptions.GatewayException;
import com.global.api.paymentMethods.CreditCardData;
import com.global.api.serviceConfigs.GpApiConfig;
import org.junit.Test;

import static org.junit.Assert.*;

public class GpApiTokenManagementTests {

    private CreditCardData card;
    private String token;

    public GpApiTokenManagementTests() throws ApiException {

        GpApiConfig config = new GpApiConfig();

        // GP-API settings
        config.setServiceUrl("https://apis.sandbox.globalpay.com/ucp");
        config.setEnableLogging(true);

        config
                .setAppId("OWTP5ptQZKGj7EnvPt3uqO844XDBt8Oj")
                .setAppKey("qM31FmlFiyXRHGYh");

        ServicesContainer.configureService(config, "GpApiConfig");

        card = new CreditCardData();
        card.setNumber("4111111111111111");
        card.setExpMonth(12);
        card.setExpYear(2015);
        card.setCvn("123");

        token = card.tokenize("GpApiConfig");

        assertNotNull("Token could not be generated.", token);
    }

    @Test
    public void verifyTokenizedPaymentMethod() throws ApiException {
        CreditCardData tokenizedCard = new CreditCardData();
        tokenizedCard.setToken(token);

        Transaction response =
                tokenizedCard
                        .verify()
                        .execute("GpApiConfig");

        assertNotNull(response);
        assertEquals("00", response.getResponseCode());
        assertEquals("ACTIVE", response.getResponseMessage());
    }

    @Test
    public void detokenizePaymentMethod() throws ApiException, CloneNotSupportedException {
        CreditCardData tokenizedCard = new CreditCardData();
        tokenizedCard.setToken(token);

        CreditCardData detokenizedCard = tokenizedCard.detokenize("GpApiConfig");

        assertNotNull(detokenizedCard);
        assertEquals(card.getNumber(), detokenizedCard.getNumber());
        assertEquals(card.getExpMonth(), detokenizedCard.getExpMonth());
        assertEquals(card.getExpYear(), detokenizedCard.getExpYear());
        assertEquals(card.getShortExpiry(), detokenizedCard.getShortExpiry());
    }

    @Test
    public void updateTokenizedPaymentMethod() throws BuilderException, ApiException {
        CreditCardData tokenizedCard = new CreditCardData();
        tokenizedCard.setToken(token);
        tokenizedCard.setExpMonth(12);
        tokenizedCard.setExpYear(2030); // Expiration Year is updated until 2030

        assertTrue(tokenizedCard.updateTokenExpiry("GpApiConfig"));

        Transaction response =
                tokenizedCard
                        .verify()
                        .execute("GpApiConfig");

        assertNotNull(response);
        assertEquals("00", response.getResponseCode());
        assertEquals("ACTIVE", response.getResponseMessage());
    }

    @Test(expected = GatewayException.class)
    public void deleteToken() throws ApiException {
        CreditCardData tokenizedCard = new CreditCardData();
        tokenizedCard.setToken(token);

        assertTrue(tokenizedCard.deleteToken("GpApiConfig"));

        tokenizedCard
                .verify()
                .execute("GpApiConfig");
    }

}
