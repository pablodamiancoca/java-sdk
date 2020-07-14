package com.global.api.gateways;

import com.global.api.builders.AuthorizationBuilder;
import com.global.api.builders.ManagementBuilder;
import com.global.api.entities.Transaction;
import com.global.api.entities.exceptions.ApiException;
import com.global.api.network.NetworkMessageHeader;

import java.text.ParseException;

public interface IPaymentGateway {
    Transaction processAuthorization(AuthorizationBuilder builder) throws ApiException;
    Transaction manageTransaction(ManagementBuilder builder) throws ApiException;
    String serializeRequest(AuthorizationBuilder builder) throws ApiException;
    // NOTE: This method is not present in .NET SDK
    NetworkMessageHeader sendKeepAlive() throws ApiException;
    boolean supportsHostedPayments();
}
