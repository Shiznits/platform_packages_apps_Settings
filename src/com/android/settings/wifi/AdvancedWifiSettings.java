/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.settings.wifi;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.DhcpInfo;
import android.net.NetworkScoreManager;
import android.net.NetworkScorerAppManager;
import android.net.NetworkScorerAppManager.NetworkScorerAppData;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.security.Credentials;
import android.text.format.Formatter;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import android.app.Dialog;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

import java.util.Collection;

public class AdvancedWifiSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "AdvancedWifiSettings";
    private static final String KEY_MAC_ADDRESS = "mac_address";
    private static final String KEY_CURRENT_IP_ADDRESS = "current_ip_address";
    private static final String KEY_FREQUENCY_BAND = "frequency_band";
    private static final String KEY_PRIORITY_SETTINGS = "wifi_priority_settings";
    private static final String KEY_NOTIFY_OPEN_NETWORKS = "notify_open_networks";
    private static final String KEY_SLEEP_POLICY = "sleep_policy";
    private static final String KEY_SCAN_ALWAYS_AVAILABLE = "wifi_scan_always_available";
    private static final String KEY_INSTALL_CREDENTIALS = "install_credentials";
    private static final String KEY_WIFI_ASSISTANT = "wifi_assistant";
    private static final String KEY_WIFI_DIRECT = "wifi_direct";
    private static final String KEY_WPS_PUSH = "wps_push_button";
    private static final String KEY_WPS_PIN = "wps_pin_entry";
    private static final String KEY_SUSPEND_OPTIMIZATIONS = "suspend_optimizations";
    private static final String KEY_AUTO_CONNECT_TYPE = "auto_connect_type";

    private static final String KEY_CURRENT_GATEWAY = "current_gateway";
    private static final String KEY_CURRENT_NETMASK = "current_netmask";
    private static final String KEY_WIFI_GSM_CONNECT_TYPE = "wifi_gsm_connect_type";
    private static final String KEY_GSM_WIFI_CONNECT_TYPE = "gsm_wifi_connect_type";

    private WifiManager mWifiManager;
    private NetworkScoreManager mNetworkScoreManager;
    private static final int WPS_PBC_DIALOG_ID = 1;
    private static final int WPS_PIN_DIALOG_ID = 2;

    CheckBoxPreference AutoPref;
    ListPreference cell2wifiPref;

    private IntentFilter mFilter;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(WifiManager.LINK_CONFIGURATION_CHANGED_ACTION) ||
                action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                refreshWifiInfo();
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.wifi_advanced_settings);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mFilter = new IntentFilter();
        mFilter.addAction(WifiManager.LINK_CONFIGURATION_CHANGED_ACTION);
        mFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mNetworkScoreManager =
                (NetworkScoreManager) getSystemService(Context.NETWORK_SCORE_SERVICE);
    }

    @Override
    public void onResume() {
        super.onResume();
        initPreferences();
        getActivity().registerReceiver(mReceiver, mFilter);
        refreshWifiInfo();
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mReceiver);
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        switch (dialogId) {
            case WPS_PBC_DIALOG_ID:
                 return new WpsDialog(getActivity(), WpsInfo.PBC);
            case WPS_PIN_DIALOG_ID:
                 return new WpsDialog(getActivity(), WpsInfo.DISPLAY);
        }
        return super.onCreateDialog(dialogId);
    }

    private void initPreferences() {
        SwitchPreference notifyOpenNetworks =
            (SwitchPreference) findPreference(KEY_NOTIFY_OPEN_NETWORKS);
        notifyOpenNetworks.setChecked(Settings.Global.getInt(getContentResolver(),
                Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON, 0) == 1);
        notifyOpenNetworks.setEnabled(mWifiManager.isWifiEnabled());

        SwitchPreference scanAlwaysAvailable =
            (SwitchPreference) findPreference(KEY_SCAN_ALWAYS_AVAILABLE);
        scanAlwaysAvailable.setChecked(Global.getInt(getContentResolver(),
                    Global.WIFI_SCAN_ALWAYS_AVAILABLE, 0) == 1);

        Intent intent = new Intent(Credentials.INSTALL_AS_USER_ACTION);
        intent.setClassName("com.android.certinstaller",
                "com.android.certinstaller.CertInstallerMain");
        intent.putExtra(Credentials.EXTRA_INSTALL_AS_UID, android.os.Process.WIFI_UID);
        Preference pref = findPreference(KEY_INSTALL_CREDENTIALS);
        pref.setIntent(intent);

        final Context context = getActivity();
        NetworkScorerAppData scorer = WifiSettings.getWifiAssistantApp(context);
        SwitchPreference wifiAssistant = (SwitchPreference)findPreference(KEY_WIFI_ASSISTANT);
        if (scorer != null) {
            final boolean checked = NetworkScorerAppManager.getActiveScorer(context) != null;
            wifiAssistant.setSummary(getResources().getString(
                    R.string.wifi_automatically_manage_summary, scorer.mScorerName));
            wifiAssistant.setOnPreferenceChangeListener(this);
            wifiAssistant.setChecked(checked);
        } else {
            if (wifiAssistant != null) {
                getPreferenceScreen().removePreference(wifiAssistant);
            }
        }

        Intent wifiDirectIntent = new Intent(context,
                com.android.settings.Settings.WifiP2pSettingsActivity.class);
        Preference wifiDirectPref = findPreference(KEY_WIFI_DIRECT);
        wifiDirectPref.setIntent(wifiDirectIntent);

        // WpsDialog: Create the dialog like WifiSettings does.
        Preference wpsPushPref = findPreference(KEY_WPS_PUSH);
        wpsPushPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference arg0) {
                    showDialog(WPS_PBC_DIALOG_ID);
                    return true;
                }
        });

        // WpsDialog: Create the dialog like WifiSettings does.
        Preference wpsPinPref = findPreference(KEY_WPS_PIN);
        wpsPinPref.setOnPreferenceClickListener(new OnPreferenceClickListener(){
                public boolean onPreferenceClick(Preference arg0) {
                    showDialog(WPS_PIN_DIALOG_ID);
                    return true;
                }
        });

        Preference prioritySettingPref = findPreference(KEY_PRIORITY_SETTINGS);
        if (prioritySettingPref != null) {
            if (!getResources().getBoolean(R.bool.set_wifi_priority)) {
                getPreferenceScreen().removePreference(prioritySettingPref);
            }
        } else {
            Log.d(TAG, "Fail to get priority pref...");
        }

        ListPreference frequencyPref = (ListPreference) findPreference(KEY_FREQUENCY_BAND);

        if (mWifiManager.isDualBandSupported()) {
            frequencyPref.setOnPreferenceChangeListener(this);
            int value = mWifiManager.getFrequencyBand();
            if (value != -1) {
                frequencyPref.setValue(String.valueOf(value));
                updateFrequencyBandSummary(frequencyPref, value);
            } else {
                Log.e(TAG, "Failed to fetch frequency band");
            }
        } else {
            if (frequencyPref != null) {
                // null if it has already been removed before resume
                getPreferenceScreen().removePreference(frequencyPref);
            }
        }

        ListPreference sleepPolicyPref = (ListPreference) findPreference(KEY_SLEEP_POLICY);
        if (sleepPolicyPref != null) {
            if (Utils.isWifiOnly(context)) {
                sleepPolicyPref.setEntries(R.array.wifi_sleep_policy_entries_wifi_only);
            }
            sleepPolicyPref.setOnPreferenceChangeListener(this);
            int value = Settings.Global.getInt(getContentResolver(),
                    Settings.Global.WIFI_SLEEP_POLICY,
                    Settings.Global.WIFI_SLEEP_POLICY_NEVER);
            String stringValue = String.valueOf(value);
            sleepPolicyPref.setValue(stringValue);
            updateSleepPolicySummary(sleepPolicyPref, stringValue);
        }

        CheckBoxPreference wifi2cellPref =
                (CheckBoxPreference) findPreference(KEY_WIFI_GSM_CONNECT_TYPE);
        if (wifi2cellPref != null) {
            if (getResources().getBoolean(R.bool.wifi_to_cell)) {
                wifi2cellPref.setChecked(Settings.System.getInt(getContentResolver(),
                        getResources().getString(R.string.wifi2cell_connect_type),
                        getResources().getInteger(R.integer.wifi2cell_connect_type_ask))
                        == getResources().getInteger(R.integer.wifi2cell_connect_type_ask));
                wifi2cellPref.setOnPreferenceChangeListener(this);
            } else {
                getPreferenceScreen().removePreference(wifi2cellPref);
            }
        } else {
            Log.d(TAG, "Fail to get wifi2cell pref");
        }

        AutoPref = (CheckBoxPreference) findPreference(KEY_AUTO_CONNECT_TYPE);
        if (AutoPref != null) {
            if (getResources().getBoolean(R.bool.config_auto_connect_wifi_enabled)) {
                AutoPref.setChecked(Settings.System.getInt(getContentResolver(),
                        getResources().getString(R.string.wifi_autoconn_type),
                        getResources().getInteger(R.integer.wifi_autoconn_type_auto)) ==
                        getResources().getInteger(R.integer.wifi_autoconn_type_auto));
                AutoPref.setOnPreferenceChangeListener(this);
            } else {
                getPreferenceScreen().removePreference(AutoPref);
            }
        } else {
            Log.d(TAG, "Fail to get auto connect pref");
        }

        String data2wifiKey = getActivity().getString(R.string.data_to_wifi_connect_key);
        String data2wifiValueAuto = getActivity().getString(
                R.string.data_to_wifi_connect_value_auto);
        cell2wifiPref = (ListPreference) findPreference(KEY_GSM_WIFI_CONNECT_TYPE);

        if (cell2wifiPref != null) {
            if (getResources().getBoolean(R.bool.cell_to_wifi)) {
                int value = Settings.System.getInt(getContentResolver(), data2wifiKey,
                        Integer.parseInt(data2wifiValueAuto));
                cell2wifiPref.setValue(String.valueOf(value));
                updateCellToWifiSummary(cell2wifiPref, value);
                cell2wifiPref.setOnPreferenceChangeListener(this);
            } else {
                getPreferenceScreen().removePreference(cell2wifiPref);
            }
        } else {
            Log.d(TAG, "Fail to get cellular2wifi pref");
        }
    }

    private void updateSleepPolicySummary(Preference sleepPolicyPref, String value) {
        if (value != null) {
            String[] values = getResources().getStringArray(R.array.wifi_sleep_policy_values);
            final int summaryArrayResId = Utils.isWifiOnly(getActivity()) ?
                    R.array.wifi_sleep_policy_entries_wifi_only : R.array.wifi_sleep_policy_entries;
            String[] summaries = getResources().getStringArray(summaryArrayResId);
            for (int i = 0; i < values.length; i++) {
                if (value.equals(values[i])) {
                    if (i < summaries.length) {
                        sleepPolicyPref.setSummary(summaries[i]);
                        return;
                    }
                }
            }
        }

        sleepPolicyPref.setSummary("");
        Log.e(TAG, "Invalid sleep policy value: " + value);
    }

    private void updateCellToWifiSummary(Preference preference, int index) {
        String[] summaries = getResources().getStringArray(R.array.cellcular2wifi_entries);
        preference.setSummary(summaries[index]);
    }

    private void updateFrequencyBandSummary(Preference frequencyBandPref, int index) {
        String[] summaries = getResources().getStringArray(R.array.wifi_frequency_band_entries);
        frequencyBandPref.setSummary(summaries[index]);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        String key = preference.getKey();

        if (KEY_NOTIFY_OPEN_NETWORKS.equals(key)) {
            Global.putInt(getContentResolver(),
                    Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
                    ((SwitchPreference) preference).isChecked() ? 1 : 0);
        } else if (KEY_SCAN_ALWAYS_AVAILABLE.equals(key)) {
            Global.putInt(getContentResolver(),
                    Global.WIFI_SCAN_ALWAYS_AVAILABLE,
                    ((SwitchPreference) preference).isChecked() ? 1 : 0);
        } else {
            return super.onPreferenceTreeClick(screen, preference);
        }
        return true;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final Context context = getActivity();
        String key = preference.getKey();

        if (KEY_FREQUENCY_BAND.equals(key)) {
            try {
                int value = Integer.parseInt((String) newValue);
                mWifiManager.setFrequencyBand(value, true);
                updateFrequencyBandSummary(preference, value);
            } catch (NumberFormatException e) {
                Toast.makeText(context, R.string.wifi_setting_frequency_band_error,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
        } else if (KEY_WIFI_ASSISTANT.equals(key)) {
            if (((Boolean)newValue).booleanValue() == false) {
                mNetworkScoreManager.setActiveScorer(null);
                return true;
            }

            NetworkScorerAppData wifiAssistant = WifiSettings.getWifiAssistantApp(context);
            Intent intent = new Intent();
            if (wifiAssistant.mConfigurationActivityClassName != null) {
                // App has a custom configuration activity; launch that.
                // This custom activity will be responsible for launching the system
                // dialog.
                intent.setClassName(wifiAssistant.mPackageName,
                        wifiAssistant.mConfigurationActivityClassName);
            } else {
                // Fall back on the system dialog.
                intent.setAction(NetworkScoreManager.ACTION_CHANGE_ACTIVE);
                intent.putExtra(NetworkScoreManager.EXTRA_PACKAGE_NAME,
                        wifiAssistant.mPackageName);
            }

            startActivity(intent);
        }

        if (KEY_SLEEP_POLICY.equals(key)) {
            try {
                String stringValue = (String) newValue;
                Settings.Global.putInt(getContentResolver(), Settings.Global.WIFI_SLEEP_POLICY,
                        Integer.parseInt(stringValue));
                updateSleepPolicySummary(preference, stringValue);
            } catch (NumberFormatException e) {
                Toast.makeText(context, R.string.wifi_setting_sleep_policy_error,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        if (KEY_WIFI_GSM_CONNECT_TYPE.equals(key)) {
            Log.d(TAG, "wifi2cell connect type is " + newValue);
            boolean checked = ((Boolean) newValue).booleanValue();
            Settings.System.putInt(getContentResolver(),
                    getResources().getString(R.string.wifi2cell_connect_type),
                    checked ? getResources().getInteger(R.integer.wifi2cell_connect_type_ask)
                            : getResources().getInteger(R.integer.wifi2cell_connect_type_auto));
        }

        if (KEY_AUTO_CONNECT_TYPE.equals(key)) {
            boolean checked = ((Boolean) newValue).booleanValue();
            setAutoPreference(checked);
            if (checked) {
                String data2wifiValueAuto = getActivity().getString(
                        R.string.data_to_wifi_connect_value_auto);
                setCellToWifiPreference(Integer.parseInt(data2wifiValueAuto));
            } else {
                String data2wifiValueManul = getActivity().getString(
                        R.string.data_to_wifi_connect_value_manul);
                setCellToWifiPreference(Integer.parseInt(data2wifiValueManul));
            }

        }

        if (KEY_GSM_WIFI_CONNECT_TYPE.equals(key)) {
            Log.d(TAG, "Gsm to Wifi connect type is " + newValue);
            String data2wifiValueAuto = getActivity().getString(
                    R.string.data_to_wifi_connect_value_auto);
            String data2wifiValueManul = getActivity().getString(
                    R.string.data_to_wifi_connect_value_manul);
            try {
                int value = Integer.parseInt(((String) newValue));
                setCellToWifiPreference(value);
                if (value == Integer.parseInt(data2wifiValueAuto)) {
                    setAutoPreference(true);
                } else if (value == Integer.parseInt(data2wifiValueManul)) {
                    setAutoPreference(false);
                }
            } catch (NumberFormatException e) {
                Toast.makeText(getActivity(), R.string.wifi_setting_connect_type_error,
                        Toast.LENGTH_SHORT).show();
            }
        }
        return true;
    }

    private void setAutoPreference(boolean check) {
        Settings.System.putInt(getContentResolver(),
                getResources().getString(R.string.wifi_autoconn_type),
                check ? getResources().getInteger(R.integer.wifi_autoconn_type_auto)
                        : getResources().getInteger(R.integer.wifi_autoconn_type_manual));
        AutoPref.setChecked(check);
    }

    private void setCellToWifiPreference(int value) {
        String data2wifiKey = getActivity().getString(R.string.data_to_wifi_connect_key);
        Settings.System.putInt(getContentResolver(), data2wifiKey, value);
        cell2wifiPref.setValue(String.valueOf(value));
        updateCellToWifiSummary(cell2wifiPref, value);
    }

    private void refreshWifiInfo() {
        final Context context = getActivity();
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();

        Preference wifiMacAddressPref = findPreference(KEY_MAC_ADDRESS);
        String macAddress = wifiInfo == null ? null : wifiInfo.getMacAddress();
        wifiMacAddressPref.setSummary(!TextUtils.isEmpty(macAddress) ? macAddress
                : context.getString(R.string.status_unavailable));
        wifiMacAddressPref.setSelectable(false);

        Preference wifiIpAddressPref = findPreference(KEY_CURRENT_IP_ADDRESS);
        String ipAddress = Utils.getWifiIpAddresses(context);
        wifiIpAddressPref.setSummary(ipAddress == null ?
                context.getString(R.string.status_unavailable) : ipAddress);
        wifiIpAddressPref.setSelectable(false);
        Preference wifiGatewayPref = findPreference(KEY_CURRENT_GATEWAY);
        String gateway = null;
        Preference wifiNetmaskPref = findPreference(KEY_CURRENT_NETMASK);
        String netmask = null;
        if (getResources().getBoolean(R.bool.config_netinfo)) {
            DhcpInfo dhcpInfo = mWifiManager.getDhcpInfo();
            if (wifiInfo != null) {
                if (dhcpInfo != null) {
                    gateway = Formatter.formatIpAddress(dhcpInfo.gateway);
                    netmask = Formatter.formatIpAddress(dhcpInfo.netmask);
                }
            }
            if (wifiGatewayPref != null) {
                wifiGatewayPref.setSummary(gateway == null ?
                        getString(R.string.status_unavailable) : gateway);
            }
            if (wifiNetmaskPref != null) {
                wifiNetmaskPref.setSummary(netmask == null ?
                        getString(R.string.status_unavailable) : netmask);
            }
        } else {
            PreferenceScreen screen = getPreferenceScreen();
            if (screen != null) {
                if (wifiGatewayPref != null) {
                    screen.removePreference(wifiGatewayPref);
                }
                if (wifiNetmaskPref != null) {
                    screen.removePreference(wifiNetmaskPref);
                }
            }
        }
    }

    /* Wrapper class for the WPS dialog to properly handle life cycle events like rotation. */
    public static class WpsFragment extends DialogFragment {
        private static int mWpsSetup;

        // Public default constructor is required for rotation.
        public WpsFragment() {
            super();
        }

        public WpsFragment(int wpsSetup) {
            super();
            mWpsSetup = wpsSetup;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new WpsDialog(getActivity(), mWpsSetup);
        }
    }

}
