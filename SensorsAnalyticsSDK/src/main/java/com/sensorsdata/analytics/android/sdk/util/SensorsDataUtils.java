/**
 * Created by wangzhuozhou on 2015/08/01.
 * Copyright © 2015－2018 Sensors Data Inc. All rights reserved.
 */

package com.sensorsdata.analytics.android.sdk.util;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.Surface;
import android.webkit.WebSettings;

import com.sensorsdata.analytics.android.sdk.SALog;
import com.sensorsdata.analytics.android.sdk.SensorsDataSDKRemoteConfig;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SensorsDataUtils {
    /**
     * 将 json 格式的字符串转成 SensorsDataSDKRemoteConfig 对象，并处理默认值
     *
     * @param config String
     * @return SensorsDataSDKRemoteConfig
     */
    public static SensorsDataSDKRemoteConfig toSDKRemoteConfig(String config) {
        SensorsDataSDKRemoteConfig sdkRemoteConfig = new SensorsDataSDKRemoteConfig();
        try {
            if (!TextUtils.isEmpty(config)) {
                JSONObject jsonObject = new JSONObject(config);
                sdkRemoteConfig.setV(jsonObject.optString("v"));

                if (!TextUtils.isEmpty(jsonObject.optString("configs"))) {
                    JSONObject configObject = new JSONObject(jsonObject.optString("configs"));
                    sdkRemoteConfig.setDisableDebugMode(configObject.optBoolean("disableDebugMode", false));
                    sdkRemoteConfig.setDisableSDK(configObject.optBoolean("disableSDK", false));
                    sdkRemoteConfig.setAutoTrackMode(configObject.optInt("autoTrackMode", -1));
                } else {
                    //默认配置
                    sdkRemoteConfig.setDisableDebugMode(false);
                    sdkRemoteConfig.setDisableSDK(false);
                    sdkRemoteConfig.setAutoTrackMode(-1);
                }
                return sdkRemoteConfig;
            }
        } catch (Exception e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
        }
        return sdkRemoteConfig;
    }

    public static String getManufacturer() {
        String manufacturer = Build.MANUFACTURER == null ? "UNKNOWN" : Build.MANUFACTURER.trim();
        try {
            if (!TextUtils.isEmpty(manufacturer)) {
                for (String item : sManufacturer) {
                    if (item.equalsIgnoreCase(manufacturer)) {
                        return item;
                    }
                }
            }
        } catch (Exception e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
        }
        return manufacturer;
    }

    /**
     * 读取配置配置的 AutoTrack 的 Fragment
     * @param context Context
     * @return ArrayList Fragment 列表
     */
    public static ArrayList<String> getAutoTrackFragments(Context context) {
        ArrayList<String> autoTrackFragments = new ArrayList<>();
        BufferedReader bf = null;
        try {
            //获取assets资源管理器
            AssetManager assetManager = context.getAssets();
            //通过管理器打开文件并读取
            bf = new BufferedReader(new InputStreamReader(
                    assetManager.open("sa_autotrack_fragment.config")));
            String line;
            while ((line = bf.readLine()) != null) {
                if (!TextUtils.isEmpty(line) && !line.startsWith("#")) {
                    autoTrackFragments.add(line);
                }
            }
        } catch (IOException e) {
            if (e.toString().contains("FileNotFoundException")) {
                SALog.d(TAG, "SensorsDataAutoTrackFragment file not exists.");
            } else {
                com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
            }
        } finally {
            if (bf != null) {
                try {
                    bf.close();
                } catch (IOException e) {
                    com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
                }
            }
        }
        return autoTrackFragments;
    }

    private static String getJsonFromAssets(String fileName, Context context) {
        //将json数据变成字符串
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader bf = null;
        try {
            //获取assets资源管理器
            AssetManager assetManager = context.getAssets();
            //通过管理器打开文件并读取
            bf = new BufferedReader(new InputStreamReader(
                    assetManager.open(fileName)));
            String line;
            while ((line = bf.readLine()) != null) {
                stringBuilder.append(line);
            }
        } catch (IOException e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
        } finally {
            if (bf != null) {
                try {
                    bf.close();
                } catch (IOException e) {
                    com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
                }
            }
        }
        return stringBuilder.toString();
    }

    /**
     * 此方法谨慎修改
     * 插件配置 disableCarrier 会修改此方法
     *
     * 获取运营商信息
     * @param context Context
     * @return 运营商信息
     */
    public static String getCarrier(Context context) {
        try {
            if (SensorsDataUtils.checkHasPermission(context, "android.permission.READ_PHONE_STATE")) {
                try {
                    TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context
                            .TELEPHONY_SERVICE);
                    if (telephonyManager != null) {
                        String operatorString = telephonyManager.getSubscriberId();
                        if (!TextUtils.isEmpty(operatorString)) {
                            return SensorsDataUtils.operatorToCarrier(context, operatorString);
                        }
                    }
                } catch (Exception e) {
                    com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
                }
            }
        } catch (Exception e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
        }

        return null;
    }

    /**
     * 获取 Activity 的 title
     *
     * @param activity Activity
     * @return Activity 的 title
     */
    public static String getActivityTitle(Activity activity) {
        try {
            if (activity != null) {
                try {
                    String activityTitle = null;

                    if (Build.VERSION.SDK_INT >= 11) {
                        String toolbarTitle = SensorsDataUtils.getToolbarTitle(activity);
                        if (!TextUtils.isEmpty(toolbarTitle)) {
                            activityTitle = toolbarTitle;
                        }
                    }

                    if (!TextUtils.isEmpty(activityTitle)) {
                        activityTitle = activity.getTitle().toString();
                    }

                    if (TextUtils.isEmpty(activityTitle)) {
                        PackageManager packageManager = activity.getPackageManager();
                        if (packageManager != null) {
                            ActivityInfo activityInfo = packageManager.getActivityInfo(activity.getComponentName(), 0);
                            if (activityInfo != null) {
                                if (!TextUtils.isEmpty(activityInfo.loadLabel(packageManager))) {
                                    activityTitle = activityInfo.loadLabel(packageManager).toString();
                                }
                            }
                        }
                    }

                    return activityTitle;
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        } catch (Exception e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
            return null;
        }
    }

    /**
     * 获取主进程的名称
     * @param context Context
     * @return 主进程名称
     */
    public static String getMainProcessName(Context context) {
        if (context == null) {
            return "";
        }
        String mainProcessName = "";
        try {
            mainProcessName = context.getApplicationContext().getApplicationInfo().processName;
        } catch (Exception ex) {
            SALog.printStackTrace(ex);
        }
        return mainProcessName;
    }


    /**
     * 获得当前进程的名字
     *
     * @param context Context
     * @return 进程名称
     */
    public static String getCurrentProcessName(Context context) {

        try {
            int pid = android.os.Process.myPid();

            ActivityManager activityManager = (ActivityManager) context
                    .getSystemService(Context.ACTIVITY_SERVICE);


            if (activityManager == null) {
                return null;
            }

            List<ActivityManager.RunningAppProcessInfo> runningAppProcessInfoList = activityManager.getRunningAppProcesses();
            if (runningAppProcessInfoList != null) {
                for (ActivityManager.RunningAppProcessInfo appProcess : runningAppProcessInfoList) {

                    if (appProcess != null) {
                        if (appProcess.pid == pid) {
                            return appProcess.processName;
                        }
                    }
                }
            }
        } catch (Exception e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
            return null;
        }
        return null;
    }

    public static boolean isMainProcess(Context context, String mainProcessName) {
        if (TextUtils.isEmpty(mainProcessName)) {
            return true;
        }

        String currentProcess = getCurrentProcessName(context.getApplicationContext());
        if (TextUtils.isEmpty(currentProcess) || mainProcessName.equals(currentProcess)) {
            return true;
        }

        return false;
    }

    public static String operatorToCarrier(Context context, String operator) {
        final String other = "其他";

        try {
            if (TextUtils.isEmpty(operator)) {
                return other;
            }

            for (Map.Entry<String, String> entry : sCarrierMap.entrySet()) {
                if (operator.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
            }

            String carrierJson = getJsonFromAssets("sa_mcc_mnc_mini.json", context);
            if (TextUtils.isEmpty(carrierJson)) {
                return other;
            }

            JSONObject jsonObject = new JSONObject(carrierJson);
            int operatorLength = operator.length();
            String carrier = null;

            //mcc与mnc之和为5位数或6位数,6位数比较少，先截取6位数进行判断
            if (operatorLength >= 6) {
                String mccMnc = operator.substring(0, 6);
                carrier = getCarrierFromJsonObject(jsonObject, mccMnc);
            }

            if (TextUtils.isEmpty(carrier) && operatorLength >= 5) {
                String mccMnc = operator.substring(0, 5);
                carrier = getCarrierFromJsonObject(jsonObject, mccMnc);
            }

            if (!TextUtils.isEmpty(carrier)) {
                return carrier;
            }
        } catch (Exception e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
        }
        return other;
    }

    private static String getCarrierFromJsonObject(JSONObject jsonObject, String mccMnc) {
        if (jsonObject == null || TextUtils.isEmpty(mccMnc)) {
            return null;
        }
        return jsonObject.optString(mccMnc);

    }

    public static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(SHARED_PREF_EDITS_FILE, Context.MODE_PRIVATE);
    }

    @TargetApi(11)
    public static String getToolbarTitle(Activity activity) {
        try {
            if ("com.tencent.connect.common.AssistActivity".equals(activity.getClass().getCanonicalName())) {
                if (!TextUtils.isEmpty(activity.getTitle())) {
                    return activity.getTitle().toString();
                }
                return null;
            }

            ActionBar actionBar = activity.getActionBar();
            if (actionBar != null) {
                if (!TextUtils.isEmpty(actionBar.getTitle())) {
                    return actionBar.getTitle().toString();
                }
            } else {
                try {
                    Class<?> appCompatActivityClass = null;
                    try {
                        appCompatActivityClass = Class.forName("android.support.v7.app.AppCompatActivity");
                    } catch (Exception e) {
                        //ignored
                    }

                    if (appCompatActivityClass == null) {
                        try {
                            appCompatActivityClass = Class.forName("androidx.appcompat.app.AppCompatActivity");
                        } catch (Exception e) {
                            //ignored
                        }
                    }
                    if (appCompatActivityClass != null && appCompatActivityClass.isInstance(activity)) {
                        Method method = activity.getClass().getMethod("getSupportActionBar");
                        if (method != null) {
                            Object supportActionBar = method.invoke(activity);
                            if (supportActionBar != null) {
                                method = supportActionBar.getClass().getMethod("getTitle");
                                if (method != null) {
                                    CharSequence charSequence = (CharSequence) method.invoke(supportActionBar);
                                    if (charSequence != null) {
                                        return charSequence.toString();
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    //ignored
                }
            }
        } catch (Exception e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
        }
        return null;
    }

    /**
     * 尝试读取页面 title
     *
     * @param properties JSONObject
     * @param activity   Activity
     */
    public static void getScreenNameAndTitleFromActivity(JSONObject properties, Activity activity) {
        if (activity == null || properties == null) {
            return;
        }

        try {
            properties.put("$screen_name", activity.getClass().getCanonicalName());

            String activityTitle = null;
            if (!TextUtils.isEmpty(activity.getTitle())) {
                activityTitle = activity.getTitle().toString();
            }

            if (Build.VERSION.SDK_INT >= 11) {
                String toolbarTitle = getToolbarTitle(activity);
                if (!TextUtils.isEmpty(toolbarTitle)) {
                    activityTitle = toolbarTitle;
                }
            }

            if (TextUtils.isEmpty(activityTitle)) {
                PackageManager packageManager = activity.getPackageManager();
                if (packageManager != null) {
                    ActivityInfo activityInfo = packageManager.getActivityInfo(activity.getComponentName(), 0);
                    if (activityInfo != null) {
                        activityTitle = activityInfo.loadLabel(packageManager).toString();
                    }
                }
            }
            if (!TextUtils.isEmpty(activityTitle)) {
                properties.put("$title", activityTitle);
            }
        } catch (Exception e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
        }
    }

    public static void cleanUserAgent(Context context) {
        try {
            final SharedPreferences preferences = getSharedPreferences(context);
            final SharedPreferences.Editor editor = preferences.edit();
            editor.putString(SHARED_PREF_USER_AGENT_KEY, null);
            editor.apply();
        } catch (Exception e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
        }
    }

    public static void mergeJSONObject(final JSONObject source, JSONObject dest) {
        try {
            Iterator<String> superPropertiesIterator = source.keys();
            if (mDateFormat == null) {
                mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"
                        + ".SSS", Locale.CHINA);
            }
            while (superPropertiesIterator.hasNext()) {
                String key = superPropertiesIterator.next();
                Object value = source.get(key);
                if (value instanceof Date) {
                    synchronized (mDateFormat) {
                        dest.put(key, mDateFormat.format((Date) value));
                    }
                } else {
                    dest.put(key, value);
                }
            }
        } catch (Exception ex) {
            SALog.printStackTrace(ex);
        }
    }

    /**
     * 融合静态公共属性
     * @param source 源属性
     * @param dest  目标属性
     */
    public static void mergeSuperJSONObject(final JSONObject source, JSONObject dest) {
        try {
            Iterator<String> superPropertiesIterator = source.keys();
            if (mDateFormat == null) {
                mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"
                        + ".SSS", Locale.CHINA);
            }
            while (superPropertiesIterator.hasNext()) {
                String key = superPropertiesIterator.next();
                Iterator<String> destPropertiesIterator = dest.keys();
                while (destPropertiesIterator.hasNext()) {
                    String destKey = destPropertiesIterator.next();
                    if (!TextUtils.isEmpty(key) && key.toLowerCase().equals(destKey.toLowerCase())) {
                        dest.remove(destKey);
                        break;
                    }
                }

                Object value = source.get(key);
                if (value instanceof Date) {
                    synchronized (mDateFormat) {
                        dest.put(key, mDateFormat.format((Date) value));
                    }
                } else {
                    dest.put(key, value);
                }
            }
        } catch (Exception ex) {
            SALog.printStackTrace(ex);
        }
    }

    /**
     * 获取 UA 值
     *
     * @param context Context
     * @return 当前 UA 值
     */
    public static String getUserAgent(Context context) {
        try {
            final SharedPreferences preferences = getSharedPreferences(context);
            String userAgent = preferences.getString(SHARED_PREF_USER_AGENT_KEY, null);
            if (TextUtils.isEmpty(userAgent)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    try {
                        Class webSettingsClass = Class.forName("android.webkit.WebSettings");
                        Method getDefaultUserAgentMethod = webSettingsClass.getMethod("getDefaultUserAgent", Context.class);
                        if (getDefaultUserAgentMethod != null) {
                            userAgent = WebSettings.getDefaultUserAgent(context);
                        }
                    } catch (Exception e) {
                        SALog.i(TAG, "WebSettings NoSuchMethod: getDefaultUserAgent");
                    }
                }

                if (TextUtils.isEmpty(userAgent)) {
                    userAgent = System.getProperty("http.agent");
                }

                if (!TextUtils.isEmpty(userAgent)) {
                    final SharedPreferences.Editor editor = preferences.edit();
                    editor.putString(SHARED_PREF_USER_AGENT_KEY, userAgent);
                    editor.apply();
                }
            }

            return userAgent;
        } catch (Exception e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
            return null;
        }
    }

    public static String getDeviceID(Context context) {
        final SharedPreferences preferences = getSharedPreferences(context);
        String storedDeviceID = preferences.getString(SHARED_PREF_DEVICE_ID_KEY, null);

        if (storedDeviceID == null) {
            storedDeviceID = UUID.randomUUID().toString();
            final SharedPreferences.Editor editor = preferences.edit();
            editor.putString(SHARED_PREF_DEVICE_ID_KEY, storedDeviceID);
            editor.apply();
        }

        return storedDeviceID;
    }

    /**
     * 检测权限
     *
     * @param context    Context
     * @param permission 权限名称
     * @return true:已允许该权限; false:没有允许该权限
     */
    public static boolean checkHasPermission(Context context, String permission) {
        try {
            Class<?> contextCompat = null;
            try {
                contextCompat = Class.forName("android.support.v4.content.ContextCompat");
            } catch (Exception e) {
                //ignored
            }

            if (contextCompat == null) {
                try {
                    contextCompat = Class.forName("androidx.core.content.ContextCompat");
                } catch (Exception e) {
                    //ignored
                }
            }

            if (contextCompat == null) {
                return true;
            }

            Method checkSelfPermissionMethod = contextCompat.getMethod("checkSelfPermission", new Class[]{Context.class, String.class});
            int result = (int)checkSelfPermissionMethod.invoke(null, new Object[]{context, permission});
            if (result != PackageManager.PERMISSION_GRANTED) {
                SALog.i(TAG, "You can fix this by adding the following to your AndroidManifest.xml file:\n"
                        + "<uses-permission android:name=\"" + permission + "\" />");
                return false;
            }

            return true;
        } catch (Exception e) {
            SALog.i(TAG, e.toString());
            return true;
        }
    }

    public static String networkType(Context context) {
        try {
            // 检测权限
            if (!checkHasPermission(context, "android.permission.ACCESS_NETWORK_STATE")) {
                return "NULL";
            }

            // Wifi
            ConnectivityManager manager = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager != null) {
                NetworkInfo networkInfo = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if (networkInfo != null && networkInfo.isConnectedOrConnecting()) {
                    return "WIFI";
                }
            }

            // Mobile network
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context
                    .TELEPHONY_SERVICE);

            if (telephonyManager == null) {
                return "NULL";
            }

            int networkType = telephonyManager.getNetworkType();
            switch (networkType) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_IDEN:
                    return "2G";
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                case TelephonyManager.NETWORK_TYPE_EHRPD:
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    return "3G";
                case TelephonyManager.NETWORK_TYPE_LTE:
                    return "4G";
            }

            // disconnected to the internet
            return "NULL";
        } catch (Exception e) {
            return "NULL";
        }
    }

    public static boolean isNetworkAvailable(Context context) {
        // 检测权限
        if (!checkHasPermission(context, "android.permission.ACCESS_NETWORK_STATE")) {
            return false;
        }
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                return true;
            }
            return false;
        } catch (Exception e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
            return false;
        }
    }

    /**
     * 此方法谨慎修改
     * 插件配置 disableIMEI 会修改此方法
     *
     * 获取IMEI
     * @param mContext Context
     * @return IMEI
     */
    public static String getIMEI(Context mContext) {
        String imei = "";
        try {
            if (!checkHasPermission(mContext, "android.permission.READ_PHONE_STATE")) {
                return imei;
            }

            TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                imei = tm.getDeviceId();
            }
        } catch (Exception e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
        }
        return imei;
    }

    /**
     *
     * 此方法谨慎修改
     * 插件配置 disableAndroidID 会修改此方法
     *
     * 获取 Android ID
     *
     * @param mContext Context
     * @return androidID
     */
    public static String getAndroidID(Context mContext) {
        String androidID = "";
        try {
            androidID = Settings.Secure.getString(mContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
        }
        return androidID;
    }

    /**
     * 获取时区偏移值
     * @return 时区偏移值，单位：秒
     */
    public static Integer getZoneOffset() {
        try {
            Calendar cal = Calendar.getInstance(Locale.getDefault());
            int zoneOffset = cal.get(java.util.Calendar.ZONE_OFFSET);
            return zoneOffset / 1000;
        } catch (Exception ex) {
            SALog.printStackTrace(ex);
        }
        return null;
    }

    public static String getApplicationMetaData(Context mContext, String metaKey) {
        try {
            ApplicationInfo appInfo = mContext.getApplicationContext().getPackageManager()
                    .getApplicationInfo(mContext.getApplicationContext().getPackageName(),
                            PackageManager.GET_META_DATA);
            String value = appInfo.metaData.getString(metaKey);
            int iValue = -1;
            if (value == null) {
                iValue = appInfo.metaData.getInt(metaKey, -1);
            }
            if (iValue != -1) {
                value = String.valueOf(iValue);
            }
            return value;
        } catch (Exception e) {
            return "";
        }
    }

    private static String getMacAddressByInterface() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (nif.getName().equalsIgnoreCase("wlan0")) {
                    byte[] macBytes = nif.getHardwareAddress();
                    if (macBytes == null) {
                        return "";
                    }

                    StringBuilder res1 = new StringBuilder();
                    for (byte b : macBytes) {
                        res1.append(String.format("%02X:", b));
                    }

                    if (res1.length() > 0) {
                        res1.deleteCharAt(res1.length() - 1);
                    }
                    return res1.toString();
                }
            }

        } catch (Exception e) {
            //ignore
        }
        return null;
    }

    private static final String marshmallowMacAddress = "02:00:00:00:00:00";
    private static final String fileAddressMac = "/sys/class/net/wlan0/address";

    /**
     * 此方法谨慎修改
     * 插件配置 disableMacAddress 会修改此方法
     * 获取手机的 Mac 地址
     *
     * @param context Context
     * @return String 当前手机的 Mac 地址
     */
    public static String getMacAddress(Context context) {
        try {
            if (!checkHasPermission(context, "android.permission.ACCESS_WIFI_STATE")) {
                return "";
            }
            WifiManager wifiMan = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiMan.getConnectionInfo();

            if (wifiInfo != null && marshmallowMacAddress.equals(wifiInfo.getMacAddress())) {
                String result = null;
                try {
                    result = getMacAddressByInterface();
                    if (result != null) {
                        return result;
                    }
                } catch (Exception e) {
                    //ignore
                }
            } else {
                if (wifiInfo != null && wifiInfo.getMacAddress() != null) {
                    return wifiInfo.getMacAddress();
                } else {
                    return "";
                }
            }
            return marshmallowMacAddress;
        } catch (Exception e) {
            //ignore
        }
        return "";
    }

    public static boolean isValidAndroidId(String androidId) {
        if (TextUtils.isEmpty(androidId)) {
            return false;
        }

        if (mInvalidAndroidId.contains(androidId.toLowerCase())) {
            return false;
        }

        return true;
    }

    public static boolean hasUtmProperties(JSONObject properties) {
        if (properties == null) {
            return false;
        }

        return properties.has("$utm_source") ||
                properties.has("$utm_medium") ||
                properties.has("$utm_term") ||
                properties.has("$utm_content") ||
                properties.has("$utm_campaign");
    }

    private static final String SHARED_PREF_EDITS_FILE = "sensorsdata";
    private static final String SHARED_PREF_DEVICE_ID_KEY = "sensorsdata.device.id";
    private static final String SHARED_PREF_USER_AGENT_KEY = "sensorsdata.user.agent";

    private static SimpleDateFormat mDateFormat = null;
    private static final Map<String, String> sCarrierMap = new HashMap<String, String>() {
        {
            //中国移动
            put("46000", "中国移动");
            put("46002", "中国移动");
            put("46007", "中国移动");
            put("46008", "中国移动");

            //中国联通
            put("46001", "中国联通");
            put("46006", "中国联通");
            put("46009", "中国联通");

            //中国电信
            put("46003", "中国电信");
            put("46005", "中国电信");
            put("46011", "中国电信");

            //中国卫通
            put("46004", "中国卫通");

            //中国铁通
            put("46020", "中国铁通");

        }
    };

    private static final List<String> sManufacturer = new ArrayList<String>() {
        {
            add("HUAWEI");
            add("OPPO");
            add("vivo");
        }
    };

    private static final List<String> mInvalidAndroidId = new ArrayList<String>() {
        {
            add("9774d56d682e549c");
            add("0123456789abcdef");
        }
    };

    /**
     * 根据设备 rotation，判断屏幕方向，获取自然方向宽
     *
     * @param rotation 设备方向
     * @param width    逻辑宽
     * @param height   逻辑高
     * @return 自然尺寸
     */
    public static int getNaturalWidth(int rotation, int width, int height) {
        return rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180 ?
                width : height;
    }

    /**
     * 根据设备 rotation，判断屏幕方向，获取自然方向高
     *
     * @param rotation 设备方向
     * @param width    逻辑宽
     * @param height   逻辑高
     * @return 自然尺寸
     */
    public static int getNaturalHeight(int rotation, int width, int height) {
        return rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180 ?
                height : width;
    }

    private static final String TAG = "SA.SensorsDataUtils";
}
