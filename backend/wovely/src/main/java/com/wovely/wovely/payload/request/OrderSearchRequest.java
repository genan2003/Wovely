package com.wovely.wovely.payload.request;

public class OrderSearchRequest {
    private String query;
    private String searchType;

    public OrderSearchRequest() {
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getSearchType() {
        return searchType;
    }

    public void setSearchType(String searchType) {
        this.searchType = searchType;
    }
}
