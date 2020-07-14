package com.global.api.builders;

import com.global.api.ServicesContainer;
import com.global.api.entities.enums.ReportType;
import com.global.api.entities.enums.TimeZoneConversion;
import com.global.api.entities.exceptions.ApiException;
import com.global.api.gateways.IReportingService;
import lombok.Getter;
import lombok.Setter;

public abstract class ReportBuilder<TResult> extends BaseBuilder<TResult> {
    @Getter @Setter private ReportType reportType;
    @Getter @Setter private TimeZoneConversion timeZoneConversion;
    private Class<TResult> clazz;

    public ReportBuilder(ReportType type, Class<TResult> clazz) {
        super();
        this.reportType = type;
        this.clazz = clazz;
    }

    @Override
    public TResult execute(String configName) throws ApiException {
        super.execute(configName);

        IReportingService client = ServicesContainer.getInstance().getReporting(configName);
        return client.processReport(this, clazz);
    }
}
