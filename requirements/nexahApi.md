## Nexah SMS-API Interface


## Table of Contents

- SMS-API Interfaces..................................................................................................
- 1. Objectives...........................................................................................................
- 2. API description for SMS API (RESTFul)................................................................
    - 2.1 Send SMS Operation.................................................................................
        - 2.1.1 Request.........................................................................................
        - 2.1.2 Response.......................................................................................
    - 2.2 Get SMS account balance.........................................................................
        - 2.2.1 Request.........................................................................................
        - 2.2.2 Response.......................................................................................
    - 2.3 Send SMS DR Notification.........................................................................
        - 2.3.1 Request.........................................................................................
        - 2.3.2 Response.......................................................................................


## 1. Objectives
```
This document is intended to specify the interface between the SMS
Wrapper and a partner or a developer through the BulkSMS platform for
SMS API RESTFul interface only. It describes how partners / developers
have to send requests to the SMS Wrapper and how the SMS Wrapper will
respond.
```

## 2. API description for SMS API (RESTFul)

The SMS API service includes the following operations:

* Send SMS
* Get SMS Account Balance
* Send SMS DR Notification


### 2.1 Send SMS Operation

**HTTP Method : POST/GET**

#### 2.1.1 Request

##### PARAMETER TYPE REQUIRED DESCRIPTION

| PARAMETER | TYPE   | REQUIRED  | DESCRIPTION                                                     |
|-----------|--------|-----------|-----------------------------------------------------------------|
| user      | String | Mandatory | login name                                                      |
| password  | String | Mandatory | login password                                                  |
| senderid  | String | Mandatory | sender identification                                           |
| sms       | String | Mandatory | message to be sent                                              |
| mobiles   | String | Mandatory | Comma separated mobile numbers (country code "237" is optional) |

```

POST https://smsvas.com/bulk/public/index.php/api/v1/sendsms HTTP/1.
Accept: application/json
Content-Type: application/json
{ 
  "user": "softropicblessed@gmail.com", 
  "password": "softropcPwd", 
  "senderid": "softropic", 
  "sms": "A long sms of more than 160 characters............", 
  "mobiles": "657114646, 658420169"
}



GET HTTP/1.
https://smsvas.com/bulk/public/index.php/api/v1/sendsms?
user=user&password=password&senderid=sender&sms=message&mobiles=XXXXXXXXX,X
XXXXXXXX,XXXXXXXXX
```

#### 2.1.2 Response

| PARAMETER           | TYPE    | VALUE/FORMAT           | DESCRIPTION                                                                                                |
|---------------------|---------|------------------------|------------------------------------------------------------------------------------------------------------|
| responsecode        | Integer | 1 or 0                 | 1 if success, 0 if error                                                                                   |
| responsedescription | String  | success OR error       | text description of response                                                                               |
| responsemessage     | String  | text                   | message of the response                                                                                    |
| sms                 | Array   | Array of Objects       | array of objects                                                                                           |
| status              | String  | success OR error       | text description of status                                                                                 |
| messageid           | String  | text                   | This uniquely identifies the message sent to a given number. The same id will be used when giving the report  |
| smsclientid         | String  | text                   | The id of the request used by nexah to send the sms to phone                                                                                      |
| errorcode           | Integer | -10019, -10003, -10008 | usually a negative number                                                                                  |
| errordescription    | String  | text                   | Human readable explanation                                                                                 |
| mobileno            | String  | 237XXXXXXXXX           | Number to which message should was sent by nexah                                                                    |
| `total_sms_unit`      | Integer | number                 | This refers to the number of sms segments that were calculated by nexah. This is equivalent to the units consumed.     |
| balance             | Integer | number                 | credits left                                                                                               |



#### Example (successful response):

```
 {
  "responsecode": 1,
  "responsedescription": "success",
  "responsemessage": "success",
  "sms": [
    {
      "status": "success",
      "smsclientid": "brk.ocm.699edeed733e2f56740dbd11",
      "messageid": "brk.ocm.ebd1f0b5-6d3d-476f-8cd6-a1a4e5b9624f",
      "mobileno": "+237657114646",
      "errorcode": null,
      "errordescription": null,
      "total_sms_unit": 2,
      "balance": 6
    },
    {
      "status": "success",
      "smsclientid": "brk.ocm.699edeed90bab2fd2c079915",
      "messageid": "brk.ocm.185788e1-5bb7-48dd-a7af-63e2b36bb16b",
      "mobileno": "+237658420169",
      "errorcode": null,
      "errordescription": null,
      "total_sms_unit": 2,
      "balance": 4
    }
  ]
}
```



#### Error Code and Description

* The errors below are non-retryable.

| Error Code | Description           |
|------------|-----------------------|
| -10019     | Inactive User         |
| -10003     | Invalid Mobile Number |
| -10008     | Balance not enough    |
| -10160     | Route not available   |

* "-10160" could be returned for example when the sendsms endpoint is called for an sms to be sent to an MTN number using a "senderid" that has not been whitelisted by MTN

#### ERRORS
* The below are mostly validation error messages returned by nexah.

```
Username and password require
Mobile number require
Message require
Senderid require
Balance not enough
Invalid senderid
```

* Below is an example response containing a validation error (password was omitted in the request)

```
  {
    "responsecode": 0,
    "responsedescription": "error",
    "responsemessage": "Username and password require",
    "sms": []
  }

```

### 2.2 Get SMS account balance

**HTTP Method : POST/GET**

#### 2.2.1 Request

| PARAMETER | TYPE    | REQUIRED  | DESCRIPTION |
|-----------|---------|-----------|-------------|
| user      | String  | Mandatory | login id    |
| password  | String  | Mandatory | password    |


```
**POST** https://smsvas.com/bulk/public/index.php/api/v1/smscredit HTTP/1.
Accept: application/json
Content-Type: application/json
{
"user": "user",
"password": "password"
}

**GET** HTTP/1.
https://smsvas.com/bulk/public/index.php/api/v1/smscredit?
user=user&password=password
```

#### 2.2.2 Response

| PARAMETER            | TYPE    | VALUE/FORMAT             | DESCRIPTION                             |
|----------------------|---------|--------------------------|-----------------------------------------|
| responsecode         | 0 or 1  | 0 or 1                   | 1 if success, 0 if error                |
| accountexpdate       | Date    | dd/MM/yyyy               | The account expiration Date             |
| balanceexpdate       | Date    | dd/MM/yyyy               | The balance expiration Date             |
| credit               | Integer | number                   | total credit units left                       |
| balance              | array   | array of objects         | array of balances                       |
| balance.country_code | String  | 3 character country code | country code                            |
| balance.country_name | String  | name of country          | country for which the credits are valid |
| balance.credit       | Integer | credit of given balance  | amount of credits                       |
| balance.credit_rate  | Number  | rate of credit           | rate                                    |
| balance.expire_date  | Date    | dd/MM/yyyy               | expiry date of credit                   |


```
{
  "responsecode": 1,
  "accountexpdate": null,
  "balanceexpdate": null,
  "credit": 4,
  "balance": [
    {
      "country_code": "CMR",
      "country_name": "Cameroun",
      "credit": 4,
      "credit_rate": null,
      "expire_date": ""
    },
    {
      "country_code": "CMR SOLDE PROMO",
      "country_name": "CAMEROUN SOLDE PROMO",
      "credit": 0,
      "credit_rate": "",
      "expire_date": ""
    }
  ]
}
```

### 2.3 Send SMS DR Notification

This method is invoked by the server to notify a Partner when a DR notification is received.


**HTTP Method : POST**

#### 2.3.1 Request
* This is the endpoint offered by the client.
* Nexah will use this endpoint to send delivery repors (DR)
* Nexah initiates the request to this endpoint

| PARAMETER          | TYPE    | VALUE/FORMAT          | DESCRIPTION                                                                         |
|--------------------|---------|-----------------------|-------------------------------------------------------------------------------------|
| reponsecode        | Integer | 0 OR 1                | only 0 or 1                                                                         |
| reponsedescription | String  | text                  | short explanation                                                                   |
| mobileno           | String  | +237XXXXXXXXX         | target of the sms                                                                   |
| messageid          | String  | string                | unique string                                                                       |
| total_sms_unit     | Integer | whole number          | credits deducted for the dispatch of sms referenced by messageid |
| submittime         | Date    | yyyy-MM-dd hh :mm :ss | date of submission                                                                  |
| senttime           | Date    | yyyy-MM-dd hh :mm :ss | date of dispatch                                                                    |
| deliverytime       | Date    | yyyy-MM-dd hh :mm :ss | date of delivery                                                                    |
| status             | String  | DELIVRD, UNDELIV      | indicates if target received the message                                            |
| traffic            | String  | OCM, *                | ??                                                                                  |


| ERROR CODE | DESCRIPTION OR STATUS |
|------------|-----------------------|
| 0          | UNDELIV               |
| 1          | DELIVRD               |


Example :

```
POST [Partner URL] HTTP/1.
Accept: application/json
Content-Type: application/json
{
  "dlrlist": [
    {
      "reponsecode": "1",
      "reponsedescription": "DeliveredToTerminal",
      "mobileno": "+237658420169",
      "messageid": "brk.ocm.185788e1-5bb7-48dd-a7af-63e2b36bb16b",
      "total_sms_unit": "2",
      "submittime": "2026-02-25 12:37:17",
      "senttime": "2026-02-25 12:37:17",
      "deliverytime": "2026-02-25 12:38:00",
      "status": "DELIVRD",
      "traffic": "OCM"
    },
    {
      "reponsecode": "1",
      "reponsedescription": "DeliveredToTerminal",
      "mobileno": "+237657114646",
      "messageid": "brk.ocm.ebd1f0b5-6d3d-476f-8cd6-a1a4e5b9624f",
      "total_sms_unit": "2",
      "submittime": "2026-02-25 12:37:17",
      "senttime": "2026-02-25 12:37:17",
      "deliverytime": "2026-02-25 12:38:00",
      "status": "DELIVRD",
      "traffic": "OCM"
    }
  ]
}
```

#### 2.3.2 Response

* The response is what the webhook should respond to the request from nexah.
* The partner or the developer should change **status to 1 if success or to 0 if
  failure**.
* It may be best to respond only with the dlr objects that indicate failure so that nexah will do another delivery report for them. (this saves bandwidth)
* If the response does not contain the failed dlr object and 0 for the status, nexah will not resend its delivery report (at a later time)
* Consider limiting the number of times you signal this to nexah so as not to remain in an unending loop (maybe because the sms will never be delivered)


Below exampe response is expected from the partner.

```
{
"dlrlist" : [
{
"reponsecode": 0,
"reponsedescription": "success",
"messageid": "e12dd358cff3961752f8c1180cc209ff",
"mobileno": "+237678018812",
"status": 0,
"submittime": "2019-05-11 13:53:13",
"senttime": "2019-05-11 13:53:14",
"deliverytime": "2019-05-11 13:53:14"
}
]
}
```


