package com.global.api.tests.gpapi;

import com.global.api.ServicesContainer;
import com.global.api.entities.EncryptionData;
import com.global.api.entities.Transaction;
import com.global.api.entities.enums.Channel;
import com.global.api.entities.enums.EntryMethod;
import com.global.api.entities.enums.TransactionStatus;
import com.global.api.entities.exceptions.ApiException;
import com.global.api.paymentMethods.DebitTrackData;
import com.global.api.serviceConfigs.GpApiConfig;
import org.junit.After;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class GpApiDebitTests {

    private final String SUCCESS = "SUCCESS";
    private DebitTrackData track = new DebitTrackData();

    public GpApiDebitTests() throws ApiException {

        GpApiConfig config = new GpApiConfig();

        // GP-API settings
        config.setServiceUrl("https://apis.sandbox.globalpay.com/ucp");
        config.setEnableLogging(true);

        config
                .setAppId("OWTP5ptQZKGj7EnvPt3uqO844XDBt8Oj")
                .setAppKey("qM31FmlFiyXRHGYh")
                .setChannel(Channel.ClientPresent.getValue());

        ServicesContainer.configureService(config, "GpApiConfig");
    }

    @Test
    public void DebitSaleSwipe() throws ApiException {
        track.setValue("%B4012002000060016^VI TEST CREDIT^251210118039000000000396?;4012002000060016=25121011803939600000?");
        track.setPinBlock("32539F50C245A6A93D123412324000AA");
        track.setEntryMethod(EntryMethod.Swipe);

        Transaction response =
                track
                        .charge(new BigDecimal("17.01"))
                        .withCurrency("USD")
                        .withAllowDuplicates(true)
                        .execute("GpApiConfig");

        assertNotNull(response);
        assertEquals(SUCCESS, response.getResponseCode());
        assertEquals(TransactionStatus.Captured.getValue(), response.getResponseMessage());
    }


    @Test
    public void DebitRefundSwipe() throws ApiException {
        track.setValue("%B4012002000060016^VI TEST CREDIT^251210118039000000000396?;4012002000060016=25121011803939600000?");
        track.setPinBlock("32539F50C245A6A93D123412324000AA");
        track.setEntryMethod(EntryMethod.Swipe);

        Transaction response =
                track.
                        refund(new BigDecimal("12.99"))
                        .withCurrency("USD")
                        .withAllowDuplicates(true)
                        .execute("GpApiConfig");

        assertNotNull(response);
        assertEquals(SUCCESS, response.getResponseCode());
        assertEquals(TransactionStatus.Captured.getValue(), response.getResponseMessage());
    }

    @Test
    public void DebitSaleSwipeEncrypted() throws ApiException {
        track.setValue("&lt;E1050711%B4012001000000016^VI TEST CREDIT^251200000000000000000000?|LO04K0WFOmdkDz0um+GwUkILL8ZZOP6Zc4rCpZ9+kg2T3JBT4AEOilWTI|+++++++Dbbn04ekG|11;4012001000000016=25120000000000000000?|1u2F/aEhbdoPixyAPGyIDv3gBfF|+++++++Dbbn04ekG|00|||/wECAQECAoFGAgEH2wYcShV78RZwb3NAc2VjdXJlZXhjaGFuZ2UubmV0PX50qfj4dt0lu9oFBESQQNkpoxEVpCW3ZKmoIV3T93zphPS3XKP4+DiVlM8VIOOmAuRrpzxNi0TN/DWXWSjUC8m/PI2dACGdl/hVJ/imfqIs68wYDnp8j0ZfgvM26MlnDbTVRrSx68Nzj2QAgpBCHcaBb/FZm9T7pfMr2Mlh2YcAt6gGG1i2bJgiEJn8IiSDX5M2ybzqRT86PCbKle/XCTwFFe1X|&gt;");
        track.setPinBlock("32539F50C245A6A93D123412324000AA");
        track.setEntryMethod(EntryMethod.Swipe);
        track.setEncryptionData(EncryptionData.version1());

        Transaction response =
                track
                        .charge(new BigDecimal("17.01"))
                        .withCurrency("USD")
                        .withAllowDuplicates(true)
                        .execute("GpApiConfig");

        assertNotNull(response);
        assertEquals(SUCCESS, response.getResponseCode());
        assertEquals(TransactionStatus.Captured.getValue(), response.getResponseMessage());
    }

    @Test
    public void DebitSaleSwipeChip() throws ApiException {
        track.setValue(";4024720012345671=18125025432198712345?");
        track.setEntryMethod(EntryMethod.Swipe);
        track.setPinBlock("AFEC374574FC90623D010000116001EE");

        String tagData = "82021C008407A0000002771010950580000000009A031709289C01005F280201245F2A0201245F3401019F02060000000010009F03060000000000009F080200019F090200019F100706010A03A420009F1A0201249F26089CC473F4A4CE18D39F2701809F3303E0F8C89F34030100029F3501229F360200639F370435EFED379F410400000019";

        Transaction response = track.charge(new BigDecimal("15.99"))
                .withCurrency("USD")
                .withAllowDuplicates(true)
                .withTagData(tagData)
                .execute("GpApiConfig");

        assertNotNull(response);
        assertEquals(SUCCESS, response.getResponseCode());
        assertEquals(TransactionStatus.Captured.getValue(), response.getResponseMessage());
    }

    @Test
    public void DebitSaleContactlessChip() throws ApiException {
        track.setValue(";4024720012345671=18125025432198712345?");
        track.setEntryMethod(EntryMethod.Proximity);
        track.setPinBlock("AFEC374574FC90623D010000116001EE");

        String tagData = "82021C008407A0000002771010950580000000009A031709289C01005F280201245F2A0201245F3401019F02060000000010009F03060000000000009F080200019F090200019F100706010A03A420009F1A0201249F26089CC473F4A4CE18D39F2701809F3303E0F8C89F34030100029F3501229F360200639F370435EFED379F410400000019";

        Transaction response = track.charge(new BigDecimal("25.95"))
                .withCurrency("USD")
                .withAllowDuplicates(true)
                .withTagData(tagData)
                .execute("GpApiConfig");

        assertNotNull(response);
        assertEquals(SUCCESS, response.getResponseCode());
        assertEquals(TransactionStatus.Captured.getValue(), response.getResponseMessage());
    }

    @After
    public void generalValidations() {
        assertEquals("Visa", track.getCardType());
    }

}