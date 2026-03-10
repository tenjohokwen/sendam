package com.softropic.sendam.security.contract.event;


import com.softropic.sendam.email.contract.EmailTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record SendMailEvent(List<Long> userIds,
                            EmailTemplate emailTemplate,
                            LocalDateTime deadline,
                            Map<String, Object> data,
                            String sendId) {
}
