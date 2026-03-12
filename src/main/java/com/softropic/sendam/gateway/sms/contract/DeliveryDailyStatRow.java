package com.softropic.sendam.gateway.sms.contract;

import java.sql.Date;

public interface DeliveryDailyStatRow {
    Date getDay();
    long getTotalSent();
    long getDelivered();
    long getFailed();
    long getTotalSegments();
}
