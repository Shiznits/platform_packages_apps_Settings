/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.settings.sim;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentUris;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.SearchIndexableResource;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Dialog;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;

import com.android.settings.notification.DropDownPreference;
import com.android.settings.RestrictedSettingsFragment;
import com.android.settings.Utils;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.settings.search.Indexable.SearchIndexProvider;
import com.android.settings.R;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SimSettings extends RestrictedSettingsFragment implements Indexable, TextWatcher {
    private static final String TAG = "SimSettings";
    private static final boolean DBG = true;

    public static final String CONFIG_LTE_SUB_SELECT_MODE = "config_lte_sub_select_mode";
    private static final String CONFIG_PRIMARY_SUB_SETABLE = "config_primary_sub_setable";

    private static final String DISALLOW_CONFIG_SIM = "no_config_sim";
    private static final String SIM_ENABLER_CATEGORY = "sim_enablers";
    private static final String SIM_CARD_CATEGORY = "sim_cards";
    private static final String SIM_ACTIVITIES_CATEGORY = "sim_activities";
    private static final String KEY_CELLULAR_DATA = "sim_cellular_data";
    private static final String KEY_CALLS = "sim_calls";
    private static final String KEY_SMS = "sim_sms";
    private static final String KEY_ACTIVITIES = "activities";
    private static final String KEY_PRIMARY_SUB_SELECT = "select_primary_sub";
    private static final String SETTING_USER_PREF_DATA_SUB = "user_preferred_data_sub";
    private static final String DISPLAY_NUMBERS_TYPE = "display_numbers_type";
    private static final String SETTING_USER_PREF_PRIMARY_SUB = "user_preferred_primary_sub";

    private int mPreferredDataSubscription;

    private static final int EVT_UPDATE = 1;
    private static int mNumSlots = 0;
    //The default legth to dispaly a character
    private static final int CHAR_LEN = 1;
    /**
     * By UX design we have use only one Subscription Information(SubInfo) record per SIM slot.
     * mSubInfoList is the list of all SubInfos.
     * mSelectableSubInfos is the list of SubInfos that a user can select for data, calls, and SMS.
     */
    private List<SubscriptionInfo> mSubInfoList = null;
    private List<SubscriptionInfo> mSelectableSubInfos = null;

    private Preference mPrimarySubSelect = null;
    private boolean mPrimaryPrefRemoved = false;
    private boolean needUpdate = false;

    private static final int ASK_VALUE = -1;
    private static List<MultiSimEnablerPreference> mSimEnablers = null;

    private int mPhoneCount;
    private int[] mCallState;
    private int[] mVoiceNetworkType;
    private int[] mDataNetworkType;
    private PhoneStateListener[] mPhoneStateListener;

    private AlertDialog mAlertDialog;
    private EditText nameText;
    private int mChangeStartPos;
    private int mChangeCount;

    private SubscriptionManager mSubscriptionManager;
    private Utils mUtils;
    private Context mContext;
    private TelecomManager mTelecommMgr;


    public SimSettings() {
        super(DISALLOW_CONFIG_SIM);
    }

    @Override
    public void onCreate(final Bundle bundle) {
        super.onCreate(bundle);
        mContext = getActivity();

        final TelephonyManager tm =
                    (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mTelecommMgr = (TelecomManager)getActivity().getSystemService(Context.TELECOM_SERVICE);
        mSubscriptionManager = SubscriptionManager.from(mContext);

        if (mSubInfoList == null) {
            mSubInfoList = mSubscriptionManager.getActiveSubscriptionInfoList();
        }
        if (DBG) log("[onCreate] mSubInfoList=" + mSubInfoList);

        mNumSlots = tm.getSimCount();
        mPhoneCount = TelephonyManager.getDefault().getPhoneCount();
        mCallState = new int[mPhoneCount];
        mVoiceNetworkType = new int[mPhoneCount];
        mDataNetworkType = new int[mPhoneCount];
        mPhoneStateListener = new PhoneStateListener[mPhoneCount];
        listen();

        mPreferredDataSubscription = SubscriptionManager.getDefaultDataSubId();

        createPreferences();
        updateAllOptions();

        IntentFilter intentFilter =
                new IntentFilter(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        intentFilter.addAction(TelephonyIntents.ACTION_SUBINFO_CONTENT_CHANGE);
        intentFilter.addAction(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);

        mContext.registerReceiver(mDdsSwitchReceiver, intentFilter);

        IntentFilter intentRadioFilter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intentFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mContext.registerReceiver(mRadioReceiver, intentRadioFilter);
    }

    @Override
    public void onDestroy() {
        final PreferenceCategory simEnablers =
                (PreferenceCategory)findPreference(SIM_ENABLER_CATEGORY);

        if (simEnablers != null) {
            for (int i = 0; i < simEnablers.getPreferenceCount(); ++i) {
                MultiSimEnablerPreference simEnabler = (MultiSimEnablerPreference) simEnablers
                        .getPreference(i);
                simEnabler.destroy();
            }
        }
        Log.d(TAG,"on onDestroy");
        getActivity().unregisterReceiver(mDdsSwitchReceiver);
        getActivity().unregisterReceiver(mRadioReceiver);
        unRegisterPhoneStateListener();
        super.onDestroy();
    }

    private void unRegisterPhoneStateListener() {
        TelephonyManager tm =
                (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
        for (int i = 0; i < mPhoneCount; i++) {
            if (mPhoneStateListener[i] != null) {
                tm.listen(mPhoneStateListener[i], PhoneStateListener.LISTEN_NONE);
                mPhoneStateListener[i] = null;
            }
        }
    }

    private BroadcastReceiver mDdsSwitchReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Intent received: " + action);
            if (TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED.equals(action)) {
                updateCellularDataValues();
                int preferredDataSubscription = SubscriptionManager.getDefaultDataSubId();
                if (preferredDataSubscription != mPreferredDataSubscription) {
                    mPreferredDataSubscription = preferredDataSubscription;
                    String status = getResources().getString(R.string.switch_data_subscription,
                            SubscriptionManager.getSlotId(preferredDataSubscription) + 1);
                    Toast.makeText(mContext, status, Toast.LENGTH_SHORT).show();
                }
            } else if (TelephonyIntents.ACTION_SUBINFO_CONTENT_CHANGE.equals(action)
                    || TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED.equals(action)) {
                mSelectableSubInfos.clear();
                mSubInfoList = SubscriptionManager.from(context).getActiveSubscriptionInfoList();
                for (int i = 0; i < mNumSlots; ++i) {
                    final SubscriptionInfo sir = Utils.findRecordBySlotId(mContext, i);
                    // Do not display deactivated subInfo in preference list
                    if ((sir != null) && (sir.getStatus() == SubscriptionManager.ACTIVE)) {
                        mSelectableSubInfos.add(sir);
                    }
                }
                // Refresh UI whenever subinfo record gets changed
                updateAllOptions();
                initLTEPreference();
            }
        }
    };

    private final BroadcastReceiver mRadioReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)
                    || Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                Log.d(TAG, "Received ACTION_SIM_STATE_CHANGED or ACTION_AIRPLANE_MODE_CHANGED");
                // Refresh UI like on resume
                initLTEPreference();
                updateAllOptions();
            }
        }
    };

    private void createPreferences() {
        addPreferencesFromResource(R.xml.sim_settings);

        mPrimarySubSelect = (Preference) findPreference(KEY_PRIMARY_SUB_SELECT);
        final PreferenceCategory simCards = (PreferenceCategory)findPreference(SIM_CARD_CATEGORY);
        final PreferenceCategory simEnablers =
                (PreferenceCategory)findPreference(SIM_ENABLER_CATEGORY);

        mSelectableSubInfos = new ArrayList<SubscriptionInfo>();
        mSimEnablers = new ArrayList<MultiSimEnablerPreference>(mNumSlots);
        for (int i = 0; i < mNumSlots; ++i) {
            final SubscriptionInfo sir = Utils.findRecordBySlotId(getActivity(), i);
            simCards.addPreference(new SimPreference(getActivity(), sir, i));
            if (mNumSlots > 1) {
                mSimEnablers.add(i, new MultiSimEnablerPreference(
                        getActivity(), sir, mHandler, i));
                simEnablers.addPreference(mSimEnablers.get(i));
            } else {
                removePreference(SIM_ENABLER_CATEGORY);
            }
            // Do not display deactivated subInfo in preference list
            if ((sir != null) && (sir.getStatus() == SubscriptionManager.ACTIVE)) {
                mSelectableSubInfos.add(sir);
            }
        }

        // Remove SIM_CARD_CATEGORY by default for UX, use SIM_ENABLER_CATEGORY replaced
        removePreference(SIM_CARD_CATEGORY);
    }

    private void updateAllOptions() {
        Log.d(TAG,"updateAllOptions");
        updateSimSlotValues();
        updateActivitesCategory();
        updateSimEnablers();
    }

    private void listen() {
        TelephonyManager tm =
                (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
        for (int i = 0; i < mPhoneCount; i++) {
            int[] subId = SubscriptionManager.getSubId(i);
            if (subId != null) {
                if (subId[0] > 0) {
                    mCallState[i] = tm.getCallState(subId[0]);
                    mVoiceNetworkType[i] = tm.getVoiceNetworkType(subId[0]);
                    mDataNetworkType[i] = tm.getDataNetworkType(subId[0]);
                    tm.listen(getPhoneStateListener(i, subId[0]),
                            PhoneStateListener.LISTEN_CALL_STATE |
                            PhoneStateListener.LISTEN_SERVICE_STATE );
                }
            }
        }
    }

    private PhoneStateListener getPhoneStateListener(int phoneId, int subId) {
        final int i = phoneId;
        mPhoneStateListener[phoneId]  = new PhoneStateListener(subId) {
            @Override
            public void onCallStateChanged(int state, String ignored) {
                Log.d(TAG, "onCallStateChanged: " + state);
                mCallState[i] = state;
                updateCellularDataValues();
            }

            @Override
            public void onServiceStateChanged(ServiceState serviceState) {
                if (ServiceState.STATE_IN_SERVICE == serviceState.getState()) {
                    mVoiceNetworkType[i] = serviceState.getVoiceNetworkType();
                }
                if (ServiceState.STATE_IN_SERVICE == serviceState.getDataRegState()) {
                    mDataNetworkType[i] = serviceState.getDataNetworkType();
                }
            }
        };
        return mPhoneStateListener[phoneId];
    }

    private void updateSimSlotValues() {
        final PreferenceCategory prefScreen = (PreferenceCategory) getPreferenceScreen()
                .findPreference(SIM_CARD_CATEGORY);
        if (prefScreen != null) {
            final int prefSize = prefScreen.getPreferenceCount();
            for (int i = 0; i < prefSize; ++i) {
                Preference pref = prefScreen.getPreference(i);
                if (pref instanceof SimPreference) {
                    ((SimPreference)pref).update();
                }
            }
        }
    }

    private boolean needDisableDataSub2() {
        boolean disableDataSub2 = false;
        if (getResources().getBoolean(R.bool.disable_data_sub2) &&
                !SystemProperties.getBoolean("persist.radio.ct_class_c", false)) {
            if (TelephonyManager.getDefault().getMultiSimConfiguration().
                equals(TelephonyManager.MultiSimVariants.DSDS)) {
                if (mSubInfoList.size() == 2) {
                    disableDataSub2 = true;
                }
            }
        }
        return disableDataSub2;
     }

    private void updateActivitesCategory() {
        createDropDown((DropDownPreference) findPreference(KEY_CELLULAR_DATA));
        createDropDown((DropDownPreference) findPreference(KEY_CALLS));
        createDropDown((DropDownPreference) findPreference(KEY_SMS));
        updateCellularDataValues();
        updateCallValues();
        updateSmsValues();
    }

    private void updateSmsValues() {
        final Preference simPref = findPreference(KEY_SMS);
        final SubscriptionInfo sir = Utils.findRecordBySubId(getActivity(),
                mSubscriptionManager.getDefaultSmsSubId());
        simPref.setTitle(R.string.sms_messages_title);
        if (SubscriptionManager.isSMSPromptEnabled() || sir == null) {
            simPref.setSummary(R.string.sim_selection_required_pref);
            ((DropDownPreference) simPref).setSelectedValue(ASK_VALUE, false);
        } else if (sir != null) {
            simPref.setSummary(sir.getDisplayName());
            ((DropDownPreference) simPref).setSelectedValue(sir.getSimSlotIndex(), false);
        }
        simPref.setEnabled(mSelectableSubInfos == null ? false : mSelectableSubInfos.size() > 1);
    }

    private void updateCellularDataValues() {
        final Preference simPref = findPreference(KEY_CELLULAR_DATA);
        final SubscriptionInfo sir = Utils.findRecordBySubId(mContext,
                SubscriptionManager.getDefaultDataSubId());
        boolean isCellularDataEnabled = false;
        boolean disableCellulardata = getResources().getBoolean(R.bool.disbale_cellular_data) &&
                !SystemProperties.getBoolean("persist.radio.ct_class_c", false);
        // Enable data preference in msim mode and call state idle
        boolean callStateIdle = isCallStateIdle();
        if (sir != null) {
            ((DropDownPreference) simPref).setSelectedValue(sir.getSimSlotIndex(), false);
            simPref.setSummary(sir.getDisplayName());
        }
        Log.d(TAG, "updateCellularDataValues" + sir);
        if (mSelectableSubInfos.size() > 1 && !needDisableDataSub2()) {
            isCellularDataEnabled = true;
        }

        Log.d(TAG, "updateCellularDataValues: enabled:" + isCellularDataEnabled);

        simPref.setEnabled(isCellularDataEnabled && (mSelectableSubInfos.size() > 1)
                && callStateIdle && (!disableCellulardata));
    }

    private boolean isCallStateIdle() {
        boolean callStateIdle = true;
        for (int i = 0; i < mCallState.length; i++) {
            if (TelephonyManager.CALL_STATE_IDLE != mCallState[i]) {
                callStateIdle = false;
            }
        }
        Log.d(TAG, "isCallStateIdle " + callStateIdle);
        return callStateIdle;
    }

    private void updateCallValues() {
        final Preference simPref = findPreference(KEY_CALLS);
        final TelecomManager telecomManager = TelecomManager.from(getActivity());
        final PhoneAccountHandle phoneAccount =
            telecomManager.getUserSelectedOutgoingPhoneAccount();
        final int subId = mSubscriptionManager.getDefaultVoiceSubId();
        final int slotId = mSubscriptionManager.getSlotId(subId);
        final SubscriptionInfo sir = Utils.findRecordBySubId(getActivity(),
                subId);
        if(sir == null) {
            simPref.setSummary(R.string.sim_selection_required_pref);
            simPref.setEnabled(
                    mSelectableSubInfos == null ? false : mSelectableSubInfos.size() > 1);
            return;
        }
        if (phoneAccount != null && SubscriptionManager.isValidSlotId(slotId)) {
            ((DropDownPreference) simPref).setSelectedValue(slotId, false);
        } else if (phoneAccount == null) {
            ((DropDownPreference) simPref).setSelectedValue(ASK_VALUE, false);
        }
        simPref.setTitle(R.string.calls_title);
        simPref.setSummary((phoneAccount == null || !SubscriptionManager.isValidSlotId(slotId))
                        ? getResources().getString(R.string.sim_calls_ask_first_prefs_title)
                        : (String) telecomManager.getPhoneAccount(phoneAccount).getLabel());
        simPref.setEnabled(mSelectableSubInfos == null ? false : mSelectableSubInfos.size() > 1);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG,"on Pause");
        for (int i = 0; i < mSimEnablers.size(); ++i) {
            MultiSimEnablerPreference simEnabler = mSimEnablers.get(i);
            if (simEnabler != null) simEnabler.cleanUp();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        mSubInfoList = mSubscriptionManager.getActiveSubscriptionInfoList();
        if (DBG) log("[onResme] mSubInfoList=" + mSubInfoList);
        initLTEPreference();
        updateAllOptions();
    }

    private void initLTEPreference() {
        boolean isPrimarySubFeatureEnable = SystemProperties
                .getBoolean("persist.radio.primarycard", false);

        boolean primarySetable = android.provider.Settings.Global.getInt(
                this.getContentResolver(), CONFIG_PRIMARY_SUB_SETABLE, 0) == 1;

        logd("isPrimarySubFeatureEnable :" + isPrimarySubFeatureEnable +
                " primarySetable :" + primarySetable);

        if (!isPrimarySubFeatureEnable || !primarySetable) {
            final PreferenceCategory simActivities =
                    (PreferenceCategory) findPreference(SIM_ACTIVITIES_CATEGORY);
            if (!mPrimaryPrefRemoved) {
                simActivities.removePreference(mPrimarySubSelect);
                mPrimaryPrefRemoved = true;
            }
            return;
        } else if (mPrimaryPrefRemoved == true) {
            final PreferenceCategory simActivities =
                    (PreferenceCategory) findPreference(SIM_ACTIVITIES_CATEGORY);
            simActivities.addPreference(mPrimarySubSelect);
            mPrimaryPrefRemoved = false;
        }

        int primarySlot = getCurrentPrimarySlot();

        boolean isManualMode = android.provider.Settings.Global.getInt(
                this.getContentResolver(), CONFIG_LTE_SUB_SELECT_MODE, 1) == 0;

        logd("init LTE primary slot : " + primarySlot + " isManualMode :" + isManualMode);
        if (-1 != primarySlot) {
            SubscriptionInfo subInfo = Utils.findRecordBySlotId(mContext, primarySlot);
            CharSequence lteSummary = (subInfo == null ) ? null : subInfo.getDisplayName();
            mPrimarySubSelect.setSummary(lteSummary);
        } else {
            mPrimarySubSelect.setSummary("");
        }

        mPrimarySubSelect.setEnabled(isDetect4gCardEnabled() ? (mSelectableSubInfos.size() > 1)
                : isManualMode);
    }

    public boolean isDetect4gCardEnabled() {
        return SystemProperties.getBoolean("persist.radio.detect4gcard", false) &&
                SystemProperties.getBoolean("persist.radio.primarycard", false);
    }

    public int getUserPrefPrimarySlotFromDB() {
        if (isDetect4gCardEnabled()) {
            List<SubscriptionInfo> sirList =
                    SubscriptionManager.from(mContext).getActiveSubscriptionInfoList();
            if (sirList != null ) {
                for (SubscriptionInfo sir : sirList) {
                    if (sir != null && sir.getSubscriptionId() > 0 && sir.getSimSlotIndex() >= 0
                            && getUserPrefPrimarySubIdFromDB() == sir.getSubscriptionId() &&
                            sir.getStatus() != SubscriptionManager.INACTIVE) {
                        return sir.getSimSlotIndex();
                    }
                }
            }
        }
        return -1;
    }

    public  int getUserPrefPrimarySubIdFromDB() {
        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        subId = android.provider.Settings.Global.getInt(mContext
                .getContentResolver(), SETTING_USER_PREF_PRIMARY_SUB, subId);
        logd("getUserPrefPrimarySubIdFromDB: " + subId);
        return subId;
    }

    public int getCurrentPrimarySlot() {
        for (int index = 0; index < mNumSlots; index++) {
            int current = getPreferredNetwork(index);
            if (isDetect4gCardEnabled()) {
                if (getUserPrefPrimarySlotFromDB() == index) {
                    return index;
                } else if (current != Phone.NT_MODE_GSM_ONLY) {
                    return index;
                }
            } else if (current == Phone.NT_MODE_TD_SCDMA_GSM_WCDMA_LTE
                    || current == Phone.NT_MODE_TD_SCDMA_GSM_WCDMA) {
                return index;
            }
        }
        return -1;
    }

    private int getPreferredNetwork(int sub) {
        int nwMode = -1;
        try {
            nwMode = TelephonyManager.getIntAtIndex(this.getContentResolver(),
                    android.provider.Settings.Global.PREFERRED_NETWORK_MODE, sub);
        } catch (SettingNotFoundException snfe) {
        }
        return nwMode;
    }

    @Override
    public boolean onPreferenceTreeClick(final PreferenceScreen preferenceScreen,
            final Preference preference) {
        if (preference instanceof SimPreference) {
            ((SimPreference) preference).createEditDialog((SimPreference) preference);
        } else if (preference instanceof MultiSimEnablerPreference) {
            ((MultiSimEnablerPreference) preference).createEditDialog();
        } else if (preference == mPrimarySubSelect) {
            startActivity(mPrimarySubSelect.getIntent());
        }

        return true;
    }

    public void createDropDown(DropDownPreference preference) {
        final DropDownPreference simPref = preference;
        final String keyPref = simPref.getKey();

        //If Fragment not yet attached to Activity, return
        if (!isAdded()) {
            Log.d(TAG,"Fragment not yet attached to Activity, EXIT!!" );
            return;
        }
        simPref.clearItems();

        if (mSelectableSubInfos == null) {
            return;
        }

        final int subSelectableSize = mSelectableSubInfos.size();
        if (subSelectableSize > 1 && !keyPref.equals(KEY_CELLULAR_DATA)) {
            simPref.addItem(getResources().getString(
                    R.string.sim_calls_ask_first_prefs_title), ASK_VALUE);
        }

        for (int i = 0; i < subSelectableSize; ++i) {
            final SubscriptionInfo sir = mSelectableSubInfos.get(i);
            if (sir != null) {
                simPref.addItem(sir.getDisplayName().toString(), sir.getSimSlotIndex());
            }
        }

        simPref.setCallback(new DropDownPreference.Callback() {
            @Override
            public boolean onItemSelected(int pos, Object value) {
                final int subId = (Integer) value == ASK_VALUE ? ASK_VALUE :
                        mSubscriptionManager.getSubId((Integer) value)[0];
                Log.d(TAG, "Preference[" + simPref.getKey() + "]select value :" + (Integer) value
                        + "with subid(" + subId + ")");
                if (simPref.getKey().equals(KEY_CELLULAR_DATA)) {
                    int defaultDataSubId = SubscriptionManager.getDefaultDataSubId();
                    Log.d(TAG, "DefDataId [" + defaultDataSubId + "]");
                    if (defaultDataSubId != subId) {
                        int phoneId = SubscriptionManager.getPhoneId(defaultDataSubId);

                        if (isDdsSwitchAlertDialogSupported(defaultDataSubId) &&
                                subSelectableSize > 1 &&
                               ((mVoiceNetworkType[phoneId] == TelephonyManager.NETWORK_TYPE_LTE) |
                                (mDataNetworkType[phoneId] == TelephonyManager.NETWORK_TYPE_LTE))) {
                            Log.d(TAG, "DDS switch request from LTE sub");
                            AlertDialog alertDlg = new AlertDialog.Builder(getActivity()).create();
                            String title = getResources().getString(
                                    R.string.data_switch_warning_title,
                                    SubscriptionManager.getSlotId(subId) + 1);
                            alertDlg.setTitle(title);
                            String warningString = getResources().getString(
                                    R.string.data_switch_warning_text);
                            alertDlg.setMessage(warningString);
                            alertDlg.setCancelable(false);
                            alertDlg.setButton("Yes", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    Log.d(TAG, "Switch DDS to subId: " + subId );
                                    mSubscriptionManager.setDefaultDataSubId(subId);
                                    if (subSelectableSize > 1) {
                                        setUserPrefDataSubIdInDb(subId);
                                    }
                                }
                            });

                            alertDlg.setButton2("No", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    Log.d(TAG, "Cancelled switch DDS to subId: " + subId);
                                    updateCellularDataValues();
                                    dialog.cancel();
                                    return;
                                }
                            });

                            alertDlg.show();
                        } else {
                            Log.d(TAG, "setDefaultDataSubId: " + subId);
                            mSubscriptionManager.setDefaultDataSubId(subId);
                            if (subSelectableSize > 1) {
                                setUserPrefDataSubIdInDb(subId);
                            }
                        }
                    }
                    //DDS error dialogue end
                } else if (simPref.getKey().equals(KEY_CALLS)) {
                    //subId 0 is meant for "Ask First"/"Prompt" option as per AOSP
                    Log.d(TAG, "DefVoiceId [" + SubscriptionManager.getDefaultVoiceSubId() + "]");
                    if (subId == ASK_VALUE) {
                        SubscriptionManager.setVoicePromptEnabled(true);
                    } else {
                        SubscriptionManager.setVoicePromptEnabled(false);
                        if (SubscriptionManager.getDefaultVoiceSubId() != subId) {
                            SubscriptionManager.from(getActivity()).setDefaultVoiceSubId(subId);
                        }
                        PhoneAccountHandle acc = findPhoneAccountHandleBySubId(subId);
                        if (acc != null) {
                            mTelecommMgr.setUserSelectedOutgoingPhoneAccount(acc);
                        }
                    }
                } else if (simPref.getKey().equals(KEY_SMS)) {
                    Log.d(TAG, "DefSMSId [" + SubscriptionManager.getDefaultSmsSubId() + "]");
                    if (subId == ASK_VALUE) {
                        SubscriptionManager.setSMSPromptEnabled(true);
                    } else {
                        SubscriptionManager.setSMSPromptEnabled(false);
                        if (SubscriptionManager.getDefaultSmsSubId() != subId) {
                            SubscriptionManager.from(getActivity()).setDefaultSmsSubId(subId);
                        }
                    }
                }

                return true;
            }
        });
    }

    private PhoneAccountHandle findPhoneAccountHandleBySubId(final int SubId) {
        PhoneAccountHandle account = null;
        int id = 0;
        List<PhoneAccountHandle> handles = mTelecommMgr.getAllPhoneAccountHandles();
        for (PhoneAccountHandle handle : handles) {
            if (handle.getId() != null) {
                try {
                    id = Integer.parseInt(handle.getId());
                } catch (NumberFormatException e) {
                    continue;
                }
                if (id == SubId) {
                    account = handle;
                    break;
                }
            }
        }
        return account;
    }

    private void setUserPrefDataSubIdInDb(long subId) {
        android.provider.Settings.Global.putLong(getContentResolver(), SETTING_USER_PREF_DATA_SUB,
                subId);
        logd("updating data subId: " + subId + " in DB");
    }

    private class SimPreference extends Preference{
        private SubscriptionInfo mSubscriptionInfo;
        private int mSlotId;

        public SimPreference(Context context, SubscriptionInfo subInfoRecord, int slotId) {
            super(context);

            mSubscriptionInfo = subInfoRecord;
            mSlotId = slotId;
            setKey("sim" + mSlotId);
            update();
        }

        public void update() {
            mSubscriptionInfo = Utils.findRecordBySlotId(mContext, mSlotId);
            final Resources res = getResources();

            setTitle(res.getString(R.string.sim_card_number_title, mSlotId + 1));
            if (mSubscriptionInfo != null) {
                setSummary(res.getString(R.string.sim_settings_summary,
                        mSubscriptionInfo.getDisplayName(),
                        mSubscriptionInfo.getNumber()));
                setEnabled(true);
            } else {
                setSummary(R.string.sim_slot_empty);
                setFragment(null);
                setEnabled(false);
            }
        }

        @Override
        protected void onAttachedToActivity() {
            super.onAttachedToActivity();
            if (needUpdate) {
                needUpdate = false;
                updateAllOptions();
            }
        }

        public void createEditDialog(SimPreference simPref) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            final View dialogLayout = getActivity().getLayoutInflater().inflate(
                    R.layout.multi_sim_dialog, null);
            builder.setView(dialogLayout);

            EditText nameText = (EditText)dialogLayout.findViewById(R.id.sim_name);
            nameText.setText(mSubscriptionInfo.getDisplayName());
            nameText.addTextChangedListener(SimSettings.this);

            TextView numberView = (TextView)dialogLayout.findViewById(R.id.number);
            final String rawNumber = getPhoneNumber(mSubscriptionInfo);
            if (TextUtils.isEmpty(rawNumber)) {
                numberView.setText(getResources().getString(
                        com.android.internal.R.string.unknownName));
            } else {
                numberView.setText(PhoneNumberUtils.formatNumber(rawNumber));
            }

            final TelephonyManager tm =
                        (TelephonyManager) getActivity().getSystemService(
                        Context.TELEPHONY_SERVICE);
            String simCarrierName = tm.getSimOperatorNameForSubscription(mSubscriptionInfo
                        .getSubscriptionId());
            TextView carrierView = (TextView)dialogLayout.findViewById(R.id.carrier);
            carrierView.setText(!TextUtils.isEmpty(simCarrierName) ? simCarrierName :
                    getContext().getString(com.android.internal.R.string.unknownName));

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            final Editor editor = prefs.edit();
            Spinner displayNumbers = (Spinner)dialogLayout.findViewById(R.id.display_numbers);
            displayNumbers.setSelection(prefs.getInt(DISPLAY_NUMBERS_TYPE, 0));
            displayNumbers.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position,
                            long id) {
                        editor.putInt(DISPLAY_NUMBERS_TYPE, position);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> arg0) {
                        // do nothing
                    }
                });

            final Resources res = getResources();
            builder.setTitle(res.getString(R.string.sim_editor_title, mSlotId + 1));

            builder.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int whichButton) {
                    final EditText nameText = (EditText)dialogLayout.findViewById(R.id.sim_name);
                    mSubscriptionInfo.setDisplayName(nameText.getText());
                    SubscriptionManager.from(getActivity()).setDisplayName(
                            mSubscriptionInfo.getDisplayName().toString(),
                            mSubscriptionInfo.getSubscriptionId(),
                            SubscriptionManager.NAME_SOURCE_USER_INPUT);

                    updateAllOptions();
                    initLTEPreference();
                    update();
                    editor.commit();
                }
            });

            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.dismiss();
                }
            });
            mAlertDialog = builder.create();
            mAlertDialog.show();
        }
    }

    // TextWatcher interface
    public void afterTextChanged(Editable s) {
        limitTextSize(s.toString().trim());
        // if user inputed whole space and saved,that is to say SLOT1 and
        // SLOT2 may named blank space, it is meaningless.
        mAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setEnabled(!TextUtils.isEmpty(s.toString().trim()));
    }

    // TextWatcher interface
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // not used
    }

    // TextWatcher interface
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // The start position of new added characters
        mChangeStartPos = start;
        // The number of new added characters
        mChangeCount = count;
    }

    private void limitTextSize(String textString) {

        if (nameText != null) {
            int wholeLen = 0;
            int i = 0;

            for (i = 0; i < textString.length(); i++) {
                wholeLen += getCharacterVisualLength(textString, i);
            }
           int InputNameMaxLength = getResources().getInteger(R.integer.sim_name_length);
            // Too many characters,cut off the new added characters
            if (wholeLen > InputNameMaxLength) {
                int cutNum = wholeLen - InputNameMaxLength;
                // Get start position of characters that will be cut off
                int changeEndPos = mChangeStartPos + mChangeCount - 1;
                int cutLen = 0;
                for (i = changeEndPos; i >= 0; i--) {
                    cutLen += getCharacterVisualLength(textString, i);
                    if (cutLen >= cutNum) {
                        break;
                    }
                }
                // The cut off characters is in range [i,mChangeStartPos + mChangeCount)
                int headStrEndPos = i;
                // Head substring that is before the cut off characters
                String headStr = "";
                // Rear substring that is after the cut off characters
                String rearStr = "";
                if (headStrEndPos > 0) {
                    // Get head substring if the cut off characters is not at the beginning
                    headStr = textString.substring(0, headStrEndPos);
                }
                int rearStrStartPos = mChangeStartPos + mChangeCount;
                if (rearStrStartPos < textString.length()) {
                    // Get rear substring if the cut off characters is not at the end
                    rearStr = textString.substring(rearStrStartPos, textString.length());
                }
                // headStr + rearStr is the new string after characters are cut off
                nameText.setText(headStr + rearStr);
                // Move cursor to the original position
                nameText.setSelection(i);
            }
        }
    }

    // A character beyond 0xff is twice as big as a character within 0xff in width when showing.
    private int getCharacterVisualLength(String seq, int index) {
        int cp = Character.codePointAt(seq, index);
        if (cp >= 0x00 && cp <= 0xFF) {
            return CHAR_LEN;
        } else {
            return CHAR_LEN*2;
        }
    }

    /**
     * For search
     */
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                        boolean enabled) {
                    ArrayList<SearchIndexableResource> result =
                            new ArrayList<SearchIndexableResource>();

                    if (Utils.showSimCardTile(context)) {
                        SearchIndexableResource sir = new SearchIndexableResource(context);
                        sir.xmlResId = R.xml.sim_settings;
                        result.add(sir);
                    }
                    return result;
                }
            };

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            logd("msg.what = " + msg.what);
            switch(msg.what) {
                case EVT_UPDATE:
                    if (isAdded()) {
                        updateAllOptions();
                    } else {
                        needUpdate = true;
                    }
                    break;
                default:
                    break;
            }
        }
    };

    private void updateSimEnablers() {
        for (int i = 0; i < mSimEnablers.size(); ++i) {
            MultiSimEnablerPreference simEnabler = mSimEnablers.get(i);
            if (simEnabler != null) simEnabler.update();
        }
    }

    // Returns the line1Number. Line1number should always be read from TelephonyManager since it can
    // be overridden for display purposes.
    private String getPhoneNumber(SubscriptionInfo info) {
        final TelephonyManager tm =
            (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
        return tm.getLine1NumberForSubscriber(info.getSubscriptionId());
    }

    private boolean isDdsSwitchAlertDialogSupported(int subId) {
        Resources res = SubscriptionManager.getResourcesForSubId(getActivity(), subId);
        return res.getBoolean(R.bool.config_dds_switch_alert_dialog_supported);
    }

    private void log(String s) {
        Log.d(TAG, s);
    }

    private void logd(String msg) {
        if (DBG) Log.d(TAG, msg);
    }

    private void loge(String s) {
        Log.e(TAG, s);
    }
}
