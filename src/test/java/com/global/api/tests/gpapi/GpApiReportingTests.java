package com.global.api.tests.gpapi;

import com.global.api.ServicesContainer;
import com.global.api.entities.TransactionSummary;
import com.global.api.entities.TransactionSummaryList;
import com.global.api.entities.enums.SortDirection;
import com.global.api.entities.enums.TransactionSortProperty;
import com.global.api.entities.exceptions.ApiException;
import com.global.api.entities.reporting.SearchCriteria;
import com.global.api.serviceConfigs.GpApiConfig;
import com.global.api.services.ReportingService;
import com.global.api.utils.DateUtils;
import org.junit.Test;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

public class GpApiReportingTests {

    private static final String TRANSACTION_ID = "TRN_ImiKh03hpvpjJDPMLmCbpRMyv5v6Q7";

    public GpApiReportingTests() throws ApiException {

        GpApiConfig config = new GpApiConfig();

        // GP-API settings
        config
                .setServiceUrl("https://apis.sandbox.globalpay.com/ucp")
                .setEnableLogging(true);
        config
                .setAppId("OWTP5ptQZKGj7EnvPt3uqO844XDBt8Oj")
                .setAppKey("qM31FmlFiyXRHGYh");

        ServicesContainer.configureService(config, "GpApiConfig");
    }

    @Test
    public void reportTransactionDetail() throws ApiException {
        TransactionSummary transaction =
                ReportingService
                        .transactionDetail(TRANSACTION_ID)
                        .execute("GpApiConfig");

        assertNotNull(transaction);
        assertEquals(TRANSACTION_ID, transaction.getTransactionId());
    }

    @Test
    public void reportFindTransactionsNoCriteria() throws ApiException {
        TransactionSummaryList transactions =
                ReportingService.findTransactions()
                        .execute("GpApiConfig");

        assertNotNull(transactions);
    }

    @Test
    public void reportFindTransactionsWithCriteria() throws ApiException {
        List<TransactionSummary> transactions =
                ReportingService.findTransactions()
                        .orderBy(TransactionSortProperty.TimeCreated, SortDirection.Descending)
                        .withPaging(2, 10)
                        .where(SearchCriteria.StartDate, DateUtils.addDays(new Date(), -30))
                        .execute("GpApiConfig");

        assertNotNull(transactions);
    }

}