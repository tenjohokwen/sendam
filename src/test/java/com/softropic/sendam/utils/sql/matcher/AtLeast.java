package com.softropic.sendam.utils.sql.matcher;

public class AtLeast extends CountStrategy {
    private static final String CLAUSE = "AT LEAST";

    protected AtLeast(long limit) {
        super(limit);
    }

    @Override
    public boolean isMatch(long actualCount) {
        return actualCount >= limit;
    }

    @Override
    public String getClause() {
        return CLAUSE;
    }
}
