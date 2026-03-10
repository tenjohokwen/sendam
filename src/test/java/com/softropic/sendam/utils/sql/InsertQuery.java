package com.softropic.sendam.utils.sql;

public class InsertQuery extends SqlQuery {
    @Override
    protected String getQueryType() {
        return "INSERT";
    }
}
