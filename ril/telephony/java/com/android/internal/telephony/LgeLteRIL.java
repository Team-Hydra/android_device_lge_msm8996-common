/*
 * Copyright (C) 2016 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony;

import static com.android.internal.telephony.RILConstants.*;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Message;
import android.os.Parcel;
import android.os.SystemProperties;
import android.util.Log;

import com.android.internal.telephony.RILConstants;

import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;

import com.android.internal.telephony.uicc.SimPhoneBookAdnRecord;

import java.util.Arrays;
import java.util.List;

/**
 * Custom Qualcomm RIL for LG G5
 *
 * {@hide}
 */
public class LgeLteRIL extends RIL implements CommandsInterface {
    static final String LOG_TAG = "LgeLteRIL";

    final int RIL_REQUEST_SIM_GET_ATR_LEGACY = 136;
    final int RIL_REQUEST_CAF_SIM_OPEN_CHANNEL_WITH_P2_LEGACY = 137;
    final int RIL_REQUEST_GET_ADN_RECORD_LEGACY = 138;
    final int RIL_REQUEST_UPDATE_ADN_RECORD_LEGACY = 139;

    final int RIL_UNSOL_RESPONSE_ADN_INIT_DONE_LEGACY = 1046;
    final int RIL_UNSOL_RESPONSE_ADN_RECORDS_LEGACY = 1047;

    public static final int RIL_UNSOL_AVAILABLE_RAT = 1054;
    public static final int RIL_UNSOL_LOG_RF_BAND_INFO = 1165;
    public static final int RIL_UNSOL_LTE_REJECT_CAUSE = 1187;

    public LgeLteRIL(Context context, int preferredNetworkType, int cdmaSubscription) {
        super(context, preferredNetworkType, cdmaSubscription, null);
    }

    public LgeLteRIL(Context context, int preferredNetworkType,
            int cdmaSubscription, Integer instanceId) {
        super(context, preferredNetworkType, cdmaSubscription, instanceId);
    }

    protected RILRequest processSolicited (Parcel p, int type) {
	boolean found = false;
        RILRequest rr = null;
	int newRequest = 0;

        int dataPosition = p.dataPosition(); // save off position within the Parcel
	int serial = p.readInt();
        int error = p.readInt();

        // Pre-process the reply before popping it
        synchronized (mRequestList) {
            RILRequest tr = mRequestList.get(serial);
            if (tr != null && tr.mSerial == serial) {
                if (error == 0 || p.dataAvail() > 0) {
                    try {
			switch (tr.mRequest) {
                        // Get those we're interested in
                        case RIL_REQUEST_SIM_GET_ATR_LEGACY:
			case RIL_REQUEST_CAF_SIM_OPEN_CHANNEL_WITH_P2_LEGACY:
			case RIL_REQUEST_GET_ADN_RECORD_LEGACY:
			case RIL_REQUEST_UPDATE_ADN_RECORD_LEGACY:
                            rr = tr;
                            break;
			}
		    } catch (Throwable thr) {
                        // Exceptions here usually mean invalid RIL responses
                        if (tr.mResult != null) {
                            AsyncResult.forMessage(tr.mResult, null, thr);
                            tr.mResult.sendToTarget();
                        }
                        return tr;
                    }
                }
            }
        }

        if (rr == null) {
            // Nothing we care about, go up
            p.setDataPosition(dataPosition);
            return super.processSolicited(p, type);
        }

	rr = findAndRemoveRequestFromList(serial);
        if (rr == null) {
            return rr;
        }

        Object ret = null;
        if (error == 0 || p.dataAvail() > 0) {
            switch (rr.mRequest) {
	    case RIL_REQUEST_SIM_GET_ATR_LEGACY:
		ret = responseString(p);
                newRequest = RIL_REQUEST_SIM_GET_ATR;
                break;
	    case RIL_REQUEST_CAF_SIM_OPEN_CHANNEL_WITH_P2_LEGACY:
		newRequest = RIL_REQUEST_CAF_SIM_OPEN_CHANNEL_WITH_P2;
		ret = responseInts(p);
		break;
	    case RIL_REQUEST_GET_ADN_RECORD_LEGACY:
		newRequest = RIL_REQUEST_GET_ADN_RECORD;
		ret = responseInts(p);
		break;
	    case RIL_REQUEST_UPDATE_ADN_RECORD_LEGACY:
		ret = responseInts(p);
		newRequest = RIL_REQUEST_UPDATE_ADN_RECORD;
		break;

	    default:
		throw new RuntimeException("Unrecognized solicited response: " + rr.mRequest);
            }
        }
        if (RILJ_LOGD) riljLog(rr.serialString() + "< " + requestToString(rr.mRequest)
                               + " " + retToString(rr.mRequest, ret));
        if (rr.mResult != null) {
            AsyncResult.forMessage(rr.mResult, ret, null);
            rr.mResult.sendToTarget();
        }

        return rr;
    }

    static String
    lgeResponseToString(int request)
    {
        switch(request) {
            case RIL_UNSOL_AVAILABLE_RAT: return "RIL_UNSOL_AVAILABLE_RAT";
            case RIL_UNSOL_LOG_RF_BAND_INFO: return "RIL_UNSOL_LOG_RF_BAND_INFO";
            case RIL_UNSOL_LTE_REJECT_CAUSE: return "RIL_UNSOL_LTE_REJECT_CAUSE";
            default: return "<unknown response>";
        }
    }

    protected void lgeUnsljLogRet(int response, Object ret) {
        riljLog("[LGE-UNSL]< " + lgeResponseToString(response) + " " + retToString(response, ret));
    }

    @Override
    protected void
    processUnsolicited (Parcel p, int type) {
        Object ret;
        int dataPosition = p.dataPosition(); // save off position within the Parcel
        int response = p.readInt();

        switch(response) {
            case RIL_UNSOL_AVAILABLE_RAT: ret = responseInts(p); break;
            case RIL_UNSOL_LOG_RF_BAND_INFO: ret = responseInts(p); break;
            case RIL_UNSOL_LTE_REJECT_CAUSE: ret = responseInts(p); break;
	    case RIL_UNSOL_RESPONSE_ADN_INIT_DONE_LEGACY:
	        ret = responseVoid(p);
	        break;
	    case RIL_UNSOL_RESPONSE_ADN_RECORDS_LEGACY:
	        ret = responseAdnRecords(p);
	        break;
            default:
                // Rewind the Parcel
                p.setDataPosition(dataPosition);
                // Forward responses that we are not overriding to the super class
                super.processUnsolicited(p, type);
                return;
        }

        switch(response) {
            case RIL_UNSOL_AVAILABLE_RAT:
                if (RILJ_LOGD) lgeUnsljLogRet(response, ret);
                break;
            case RIL_UNSOL_LOG_RF_BAND_INFO:
                if (RILJ_LOGD) lgeUnsljLogRet(response, ret);
                break;
            case RIL_UNSOL_LTE_REJECT_CAUSE:
                if (RILJ_LOGD) lgeUnsljLogRet(response, ret);
                break;
        }

    }

    // below is a copy&paste from RIL class, replacing the RIL_REQUEST* with _LEGACY enums
    private Object responseAdnRecords(Parcel p) {
        int numRecords = p.readInt();
        SimPhoneBookAdnRecord[] AdnRecordsInfoGroup = new SimPhoneBookAdnRecord[numRecords];

        for (int i = 0 ; i < numRecords ; i++) {
            AdnRecordsInfoGroup[i]= new SimPhoneBookAdnRecord();

            AdnRecordsInfoGroup[i].mRecordIndex = p.readInt();
            AdnRecordsInfoGroup[i].mAlphaTag = p.readString();
            AdnRecordsInfoGroup[i].mNumber =
                    SimPhoneBookAdnRecord.ConvertToPhoneNumber(p.readString());

            int numEmails = p.readInt();
            if(numEmails > 0) {
                AdnRecordsInfoGroup[i].mEmailCount = numEmails;
                AdnRecordsInfoGroup[i].mEmails = new String[numEmails];
                for (int j = 0 ; j < numEmails; j++) {
                    AdnRecordsInfoGroup[i].mEmails[j] = p.readString();
                }
            }

            int numAnrs = p.readInt();
            if(numAnrs > 0) {
                AdnRecordsInfoGroup[i].mAdNumCount = numAnrs;
                AdnRecordsInfoGroup[i].mAdNumbers = new String[numAnrs];
                for (int k = 0 ; k < numAnrs; k++) {
                    AdnRecordsInfoGroup[i].mAdNumbers[k] =
                        SimPhoneBookAdnRecord.ConvertToPhoneNumber(p.readString());
                }
            }
        }
        riljLog(Arrays.toString(AdnRecordsInfoGroup));

        return AdnRecordsInfoGroup;
    }
    
    public void getAtr(Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SIM_GET_ATR_LEGACY, response);
        int slotId = 0;
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(slotId);
        if (RILJ_LOGD) riljLog(rr.serialString() + "> iccGetAtr: "
                + requestToString(rr.mRequest) + " " + slotId);
	
        send(rr);
    }
    
    public void iccOpenLogicalChannel(String AID, byte p2, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_CAF_SIM_OPEN_CHANNEL_WITH_P2_LEGACY, response);
        rr.mParcel.writeByte(p2);
        rr.mParcel.writeString(AID);

        if (RILJ_LOGD)
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }
    
    public void getAdnRecord(Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GET_ADN_RECORD_LEGACY, result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void updateAdnRecord(SimPhoneBookAdnRecord adnRecordInfo, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_UPDATE_ADN_RECORD_LEGACY, result);
        rr.mParcel.writeInt(adnRecordInfo.getRecordIndex());
        rr.mParcel.writeString(adnRecordInfo.getAlphaTag());
        rr.mParcel.writeString(
                SimPhoneBookAdnRecord.ConvertToRecordNumber(adnRecordInfo.getNumber()));

        int numEmails = adnRecordInfo.getNumEmails();
        rr.mParcel.writeInt(numEmails);
        for (int i = 0 ; i < numEmails; i++) {
            rr.mParcel.writeString(adnRecordInfo.getEmails()[i]);
        }

        int numAdNumbers = adnRecordInfo.getNumAdNumbers();
        rr.mParcel.writeInt(numAdNumbers);
        for (int j = 0 ; j < numAdNumbers; j++) {
            rr.mParcel.writeString(
                SimPhoneBookAdnRecord.ConvertToRecordNumber(adnRecordInfo.getAdNumbers()[j]));
        }

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
            + " with " + adnRecordInfo.toString());

        send(rr);
    }

    // new ril methods that are not supported
    public void setAllowedCarriers(List<CarrierIdentifier> carriers, Message response) {
        riljLog("setAllowedCarriers: not supported");
        if (response != null) {
            CommandException ex = new CommandException(
                CommandException.Error.REQUEST_NOT_SUPPORTED);
            AsyncResult.forMessage(response, null, ex);
            response.sendToTarget();
        }
    }

    public void getAllowedCarriers(Message response) {
        riljLog("getAllowedCarriers: not supported");
        if (response != null) {
            CommandException ex = new CommandException(
                CommandException.Error.REQUEST_NOT_SUPPORTED);
            AsyncResult.forMessage(response, null, ex);
            response.sendToTarget();
        }
    }

    @Override
    protected Object
    responseIccCardStatus(Parcel p) {
        IccCardApplicationStatus appStatus = null;

        IccCardStatus cardStatus = new IccCardStatus();
        cardStatus.setCardState(p.readInt());
        cardStatus.setUniversalPinState(p.readInt());
        cardStatus.mGsmUmtsSubscriptionAppIndex = p.readInt();
        cardStatus.mCdmaSubscriptionAppIndex = p.readInt();
        cardStatus.mImsSubscriptionAppIndex = p.readInt();

        int numApplications = p.readInt();

        // limit to maximum allowed applications
        if (numApplications > IccCardStatus.CARD_MAX_APPS) {
            numApplications = IccCardStatus.CARD_MAX_APPS;
        }
        cardStatus.mApplications = new IccCardApplicationStatus[numApplications];

        for (int i = 0 ; i < numApplications ; i++) {
            appStatus = new IccCardApplicationStatus();
            appStatus.app_type       = appStatus.AppTypeFromRILInt(p.readInt());
            appStatus.app_state      = appStatus.AppStateFromRILInt(p.readInt());
            appStatus.perso_substate = appStatus.PersoSubstateFromRILInt(p.readInt());
            appStatus.aid            = p.readString();
            appStatus.app_label      = p.readString();
            appStatus.pin1_replaced  = p.readInt();
            appStatus.pin1           = appStatus.PinStateFromRILInt(p.readInt());
            appStatus.pin2           = appStatus.PinStateFromRILInt(p.readInt());
            int remaining_count_pin1 = p.readInt();
            int remaining_count_puk1 = p.readInt();
            int remaining_count_pin2 = p.readInt();
            int remaining_count_puk2 = p.readInt();
            cardStatus.mApplications[i] = appStatus;
        }

        if (numApplications == 1 && appStatus != null
                && appStatus.app_type == appStatus.AppTypeFromRILInt(2)) {
            cardStatus.mApplications = new IccCardApplicationStatus[numApplications + 2];
            cardStatus.mGsmUmtsSubscriptionAppIndex = 0;
            cardStatus.mApplications[cardStatus.mGsmUmtsSubscriptionAppIndex] = appStatus;
            cardStatus.mCdmaSubscriptionAppIndex = 1;
            cardStatus.mImsSubscriptionAppIndex = 2;
            IccCardApplicationStatus appStatus2 = new IccCardApplicationStatus();
            appStatus2.app_type       = appStatus2.AppTypeFromRILInt(4); // CSIM State
            appStatus2.app_state      = appStatus.app_state;
            appStatus2.perso_substate = appStatus.perso_substate;
            appStatus2.aid            = appStatus.aid;
            appStatus2.app_label      = appStatus.app_label;
            appStatus2.pin1_replaced  = appStatus.pin1_replaced;
            appStatus2.pin1           = appStatus.pin1;
            appStatus2.pin2           = appStatus.pin2;
            cardStatus.mApplications[cardStatus.mCdmaSubscriptionAppIndex] = appStatus2;
            IccCardApplicationStatus appStatus3 = new IccCardApplicationStatus();
            appStatus3.app_type       = appStatus3.AppTypeFromRILInt(5); // IMS State
            appStatus3.app_state      = appStatus.app_state;
            appStatus3.perso_substate = appStatus.perso_substate;
            appStatus3.aid            = appStatus.aid;
            appStatus3.app_label      = appStatus.app_label;
            appStatus3.pin1_replaced  = appStatus.pin1_replaced;
            appStatus3.pin1           = appStatus.pin1;
            appStatus3.pin2           = appStatus.pin2;
            cardStatus.mApplications[cardStatus.mImsSubscriptionAppIndex] = appStatus3;
        }

        return cardStatus;
    }

    @Override
    public void
    setNetworkSelectionModeManual(String operatorNumeric, Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL,
                                    response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                    + " " + operatorNumeric);

        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(operatorNumeric);
        rr.mParcel.writeString("2"); // NOCHANGE

        send(rr);
    }
}
