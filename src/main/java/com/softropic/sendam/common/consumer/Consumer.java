package com.softropic.sendam.common.consumer;



import com.softropic.sendam.common.Gender;
import com.softropic.sendam.common.validation.PhoneNumber;
import com.softropic.sendam.security.repo.Address;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Set;

/**
 * Needed to represent a user without login data.
 * JPA also uses it as read-only for queries
 */
public interface Consumer extends Serializable {
    Long getId();
    String getFirstName();
    String getLastName();
    String getTitle();
    Gender getGender();
    LocalDate getDateOfBirth();
    String getLangKey();
    PhoneNumber getPhone();
    String getEmail() ;
    Set<Address> getAddresses();
}
