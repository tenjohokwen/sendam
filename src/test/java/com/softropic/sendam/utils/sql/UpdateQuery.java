package com.softropic.sendam.utils.sql;

public class UpdateQuery extends SqlQuery {
    @Override
    protected String getQueryType() {
        return "UPDATE";
    }
}
