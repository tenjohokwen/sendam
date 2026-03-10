package com.softropic.sendam.utils.sql;

public class DeleteQuery extends SqlQuery {
    @Override
    protected String getQueryType() {
        return "DELETE";
    }
}
