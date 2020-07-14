package com.global.api.builders;

import com.global.api.entities.enums.ReportType;
import com.global.api.entities.enums.SortDirection;
import com.global.api.entities.enums.TimeZoneConversion;
import com.global.api.entities.enums.TransactionSortProperty;
import com.global.api.entities.reporting.SearchCriteria;
import com.global.api.entities.reporting.SearchCriteriaBuilder;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

public class TransactionReportBuilder<TResult> extends ReportBuilder<TResult> {
    private String deviceId;
    private Date endDate;
    private Date startDate;
    private String transactionId;
    @Getter @Setter private int page = 1;       // 1: DEFAULT PARAM VALUE
    @Getter @Setter private int pageSize = 5;   // 5: DEFAULT PARAM VALUE
    @Getter @Setter private TransactionSortProperty orderProperty;
    @Getter @Setter private SortDirection orderDirection;

    private SearchCriteriaBuilder<TResult> _searchBuilder;

    public String getDeviceId() {
        return getSearchBuilder().getUniqueDeviceId();
    }
    public Date getEndDate() {
        return getSearchBuilder().getEndDate();
    }
    public Date getStartDate() {
        return getSearchBuilder().getStartDate();
    }
    public String getTransactionId() {
        return transactionId;
    }
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public TransactionReportBuilder<TResult> withDeviceId(String value) {
        this.deviceId = value;
        return this;
    }
    public TransactionReportBuilder<TResult> withEndDate(Date value) {
        this.endDate = value;
        return this;
    }
    public TransactionReportBuilder<TResult> withStartDate(Date value) {
        this.startDate = value;
        return this;
    }
    public TransactionReportBuilder<TResult> withTransactionId(String value) {
        this.transactionId = value;
        return this;
    }
    public TransactionReportBuilder<TResult> withTimeZoneConversion(TimeZoneConversion value) {
        setTimeZoneConversion(value);
        return this;
    }

    public TransactionReportBuilder(ReportType type, Class<TResult> clazz) {
        super(type, clazz);
    }

    public SearchCriteriaBuilder<TResult> getSearchBuilder() {
        if (_searchBuilder == null) {
            _searchBuilder = new SearchCriteriaBuilder<TResult>(this);
        }
        return _searchBuilder;
    }

    public <T> SearchCriteriaBuilder<TResult> where(SearchCriteria criteria, T value) {
        return getSearchBuilder().and(criteria, value);
    }

    public TransactionReportBuilder<TResult> withPaging(int page, int pageSize)
    {
        this.page = page;
        this.pageSize = pageSize;
        return this;
    }

    public TransactionReportBuilder<TResult> orderBy(TransactionSortProperty orderProperty, SortDirection orderDirection)
    {
        this.orderProperty = orderProperty;
        this.orderDirection = (orderDirection != null) ? orderDirection : SortDirection.Ascending;
        return this;
    }

    public void setupValidations() {
        this.validations.of(ReportType.TransactionDetail)
                .check("transactionId").isNotNull();

        this.validations.of(ReportType.Activity).check("transactionId").isNull();
    }
}