package com.global.api.serviceConfigs;

import com.global.api.ConfiguredServices;
import com.global.api.entities.enums.Channel;
import com.global.api.entities.enums.Environment;
import com.global.api.entities.enums.ServiceEndpoints;
import com.global.api.entities.exceptions.ConfigurationException;
import com.global.api.gateways.GpApiConnector;
import com.global.api.utils.StringUtils;
import lombok.Getter;
import lombok.Setter;

public class GpApiConfig extends GatewayConfig {

    // A unique random value included while creating the secret
    @Getter
    @Setter
    private String NONCE = "transactionsapi";

    // Default GP-API language
    @Getter
    @Setter
    public String language = "EN";

    public void configureContainer(ConfiguredServices services) {
        GpApiConnector gpApiConnector = new GpApiConnector(this);

        if (StringUtils.isNullOrEmpty(serviceUrl)) {
            serviceUrl = environment.equals(Environment.TEST) ?
                    ServiceEndpoints.GP_API_TEST.getValue() : ServiceEndpoints.GP_API_PRODUCTION.getValue();
        }

        services.setGatewayConnector(gpApiConnector);
        services.setReportingService(gpApiConnector);
    }

    @Override
    public void validate() throws ConfigurationException {
        super.validate();

        if (StringUtils.isNullOrEmpty(this.getAppId()) || StringUtils.isNullOrEmpty(this.getAppKey()))
            throw new ConfigurationException("appId and apiKey cannot be null.");
    }

}
