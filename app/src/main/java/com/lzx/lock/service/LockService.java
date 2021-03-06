package com.lzx.lock.service;

import android.app.ActivityManager;
import android.app.IntentService;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.lzx.lock.LockApplication;
import com.lzx.lock.base.AppConstants;
import com.lzx.lock.db.CommLockInfoManager;
import com.lzx.lock.module.lock.GestureUnlockActivity;
import com.lzx.lock.utils.SpUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xian on 2017/2/17.
 */

public class LockService extends IntentService {
    public static final String UNLOCK_ACTION = "UNLOCK_ACTION";
    public static final String LOCK_SERVICE_LASTTIME = "LOCK_SERVICE_LASTTIME";
    public static final String LOCK_SERVICE_LASTAPP = "LOCK_SERVICE_LASTAPP";
    public static boolean isActionLock = false;
    public boolean threadIsTerminate = false;
    @Nullable
    public String savePkgName;
    private long lastUnlockTimeSeconds = 0;
    private String lastUnlockPackageName = "";

    private boolean lockState;

    private ServiceReceiver mServiceReceiver;
    private CommLockInfoManager mLockInfoManager;
    @Nullable
    private ActivityManager activityManager;

    public LockService() {
        super("LockService");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        lockState = SpUtil.getInstance().getBoolean(AppConstants.LOCK_STATE);
        mLockInfoManager = new CommLockInfoManager(this);
        activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);


        mServiceReceiver = new ServiceReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(UNLOCK_ACTION);
        registerReceiver(mServiceReceiver, filter);

        threadIsTerminate = true;

    }

    @Override
    protected void onHandleIntent(Intent intent) {
        checkData();
    }

    private void checkData() {
        while (threadIsTerminate) {
            String packageName = getLauncherTopApp(LockService.this, activityManager);

            if (lockState && !inWhiteList(packageName) && !TextUtils.isEmpty(packageName)) {
                boolean isLockOffScreenTime = SpUtil.getInstance().getBoolean(AppConstants.LOCK_AUTO_SCREEN_TIME, false);
                boolean isLockOffScreen = SpUtil.getInstance().getBoolean(AppConstants.LOCK_AUTO_SCREEN, false);
                savePkgName = SpUtil.getInstance().getString(AppConstants.LOCK_LAST_LOAD_PKG_NAME, "");
                //Log.i("Server", "packageName = " + packageName + "  savePkgName = " + savePkgName);

                if (isLockOffScreenTime && !isLockOffScreen) {
                    long time = SpUtil.getInstance().getLong(AppConstants.LOCK_CURR_MILLISENCONS, 0);
                    long leaverTime = SpUtil.getInstance().getLong(AppConstants.LOCK_APART_MILLISENCONS, 0);
                    if (!TextUtils.isEmpty(savePkgName)) {
                        if (!TextUtils.isEmpty(packageName)) {
                            if (!savePkgName.equals(packageName)) { //
                                if (getHomes().contains(packageName) || packageName.contains("launcher")) {
                                    boolean isSetUnLock = mLockInfoManager.isSetUnLock(savePkgName);
                                    if (!isSetUnLock) {
                                        if (System.currentTimeMillis() - time > leaverTime) {
                                            mLockInfoManager.lockCommApplication(savePkgName);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isLockOffScreenTime && isLockOffScreen) {
                    long time = SpUtil.getInstance().getLong(AppConstants.LOCK_CURR_MILLISENCONS, 0);
                    long leaverTime = SpUtil.getInstance().getLong(AppConstants.LOCK_APART_MILLISENCONS, 0);
                    if (!TextUtils.isEmpty(savePkgName)) {
                        if (!TextUtils.isEmpty(packageName)) {
                            if (!savePkgName.equals(packageName)) {
                                if (getHomes().contains(packageName) || packageName.contains("launcher")) {
                                    boolean isSetUnLock = mLockInfoManager.isSetUnLock(savePkgName);
                                    if (!isSetUnLock) {
                                        if (System.currentTimeMillis() - time > leaverTime) {
                                            mLockInfoManager.lockCommApplication(savePkgName);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }


                if (!isLockOffScreenTime && isLockOffScreen) {
                    if (!TextUtils.isEmpty(savePkgName)) {
                        if (!TextUtils.isEmpty(packageName)) {
                            if (!savePkgName.equals(packageName)) {
                                isActionLock = false;
                                if (getHomes().contains(packageName) || packageName.contains("launcher")) {
                                    boolean isSetUnLock = mLockInfoManager.isSetUnLock(savePkgName);
                                    if (!isSetUnLock) {
                                        mLockInfoManager.lockCommApplication(savePkgName);
                                    }
                                }
                            } else {
                                isActionLock = true;
                            }
                        }
                    }
                }


                if (!isLockOffScreenTime && !isLockOffScreen) {
                    if (!TextUtils.isEmpty(savePkgName)) {
                        if (!TextUtils.isEmpty(packageName)) {
                            if (!savePkgName.equals(packageName)) {
                                if (getHomes().contains(packageName) || packageName.contains("launcher")) {
                                    boolean isSetUnLock = mLockInfoManager.isSetUnLock(savePkgName);
                                    if (!isSetUnLock) {
                                        mLockInfoManager.lockCommApplication(savePkgName);
                                    }
                                }
                            }
                        }
                    }
                }


                if (mLockInfoManager.isLockedPackageName(packageName)) {
                    passwordLock(packageName);
                    continue;
                } else {

                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 白名单
     */
    private boolean inWhiteList(String packageName) {
        return packageName.equals(AppConstants.APP_PACKAGE_NAME)
                || packageName.equals("com.android.settings");
    }

    //TODO:  Find new way to detect which is in foreground (may be use Accessibility service)
    public String getLauncherTopApp(@NonNull Context context, @NonNull ActivityManager activityManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            List<ActivityManager.RunningTaskInfo> appTasks = activityManager.getRunningTasks(1);
            if (null != appTasks && !appTasks.isEmpty()) {
                return appTasks.get(0).topActivity.getPackageName();
            }
        } else {

            UsageStatsManager sUsageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            long endTime = System.currentTimeMillis();
            long beginTime = endTime - 10000;
            String result = "";
            UsageEvents.Event event = new UsageEvents.Event();
            UsageEvents usageEvents = sUsageStatsManager.queryEvents(beginTime, endTime);
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    result = event.getPackageName();
                }
            }
            if (!android.text.TextUtils.isEmpty(result)) {
                return result;
            }
        }
        return "";
    }

    @NonNull
    private List<String> getHomes() {
        List<String> names = new ArrayList<>();
        PackageManager packageManager = this.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> resolveInfo = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo ri : resolveInfo) {
            names.add(ri.activityInfo.packageName);
        }
        return names;
    }

    private void passwordLock(String packageName) {
        LockApplication.getInstance().clearAllActivity();
        Intent intent = new Intent(this, GestureUnlockActivity.class);

        intent.putExtra(AppConstants.LOCK_PACKAGE_NAME, packageName);
        intent.putExtra(AppConstants.LOCK_FROM, AppConstants.LOCK_FROM_FINISH);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        threadIsTerminate = false;
        unregisterReceiver(mServiceReceiver);
    }

    /**
     * 服务广播
     */
    public class ServiceReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            String action = intent.getAction();

            boolean isLockOffScreen = SpUtil.getInstance().getBoolean(AppConstants.LOCK_AUTO_SCREEN, false); //是否在手机屏幕关闭后再次锁定
            boolean isLockOffScreenTime = SpUtil.getInstance().getBoolean(AppConstants.LOCK_AUTO_SCREEN_TIME, false); //是否在手机屏幕关闭后时间段后再次锁定

            switch (action) {
                case UNLOCK_ACTION:
                    lastUnlockPackageName = intent.getStringExtra(LOCK_SERVICE_LASTAPP);
                    lastUnlockTimeSeconds = intent.getLongExtra(LOCK_SERVICE_LASTTIME, lastUnlockTimeSeconds);
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    SpUtil.getInstance().putLong(AppConstants.LOCK_CURR_MILLISENCONS, System.currentTimeMillis());

                    if (!isLockOffScreenTime && isLockOffScreen) {
                        String savePkgName = SpUtil.getInstance().getString(AppConstants.LOCK_LAST_LOAD_PKG_NAME, "");
                        if (!TextUtils.isEmpty(savePkgName)) {
                            if (isActionLock) {
                                mLockInfoManager.lockCommApplication(lastUnlockPackageName);
                            }
                        }
                    }
                    break;
            }
        }
    }
}
