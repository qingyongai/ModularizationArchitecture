package com.spinytech.macore;

import android.app.Application;
import android.content.Intent;
import android.content.res.Configuration;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;

import com.spinytech.macore.multiprocess.BaseApplicationLogic;
import com.spinytech.macore.multiprocess.PriorityLogicWrapper;
import com.spinytech.macore.router.LocalRouter;
import com.spinytech.macore.router.WideRouter;
import com.spinytech.macore.router.WideRouterApplicationLogic;
import com.spinytech.macore.router.WideRouterConnectService;
import com.spinytech.macore.tools.Logger;
import com.spinytech.macore.tools.ProcessUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * Created by wanglei on 2016/11/25.
 * update 2017-02-22 添加注释
 */
public abstract class MaApplication extends Application {

    private static final String TAG = "MaApplication";
    private static MaApplication sInstance;
    private ArrayList<PriorityLogicWrapper> mLogicList;
    private HashMap<String, ArrayList<PriorityLogicWrapper>> mLogicClassMap;

    @CallSuper
    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        Logger.d(TAG, "Application onCreate start: " + System.currentTimeMillis());
        init();
        startWideRouter();
        initializeLogic();
        dispatchLogic();
        instantiateLogic();
        // logic的onCreate方法调用
        logiconCreate();
        Logger.d(TAG, "Application onCreate end: " + System.currentTimeMillis());
    }

    private void logiconCreate() {
        // Traverse the application logic.
        if (null != mLogicList && mLogicList.size() > 0) {
            for (PriorityLogicWrapper priorityLogicWrapper : mLogicList) {
                if (null != priorityLogicWrapper && null != priorityLogicWrapper.instance) {
                    priorityLogicWrapper.instance.onCreate();
                }
            }
        }
    }

    private void init() {
        // 初始化当前进程的LocalRouter
        LocalRouter.getInstance(this);
        mLogicClassMap = new HashMap<>();
    }

    /**
     * 如果需要多进程的话，在一个单独的进程里面注册路由，链接路由。
     */
    protected void startWideRouter() {
        if (needMultipleProcess()) {
            // WideRouter所在进程的Application逻辑
            registerApplicationLogic(WideRouter.PROCESS_NAME, 1000, WideRouterApplicationLogic.class);
            // WideRouter关联到WideRouter的守护Service
            Intent intent = new Intent(this, WideRouterConnectService.class);
            startService(intent);
        }
    }

    /**
     * 把每个进程的{@link LocalRouter}对应的{@link com.spinytech.macore.router.LocalRouterConnectService}注册到{@link WideRouter}
     * see {@link WideRouter#registerLocalRouter(String, Class)}
     * 参数分别为进程名和对应的守护Service
     */
    public abstract void initializeAllProcessRouter();

    /**
     * 处理每个进程里面Application的逻辑
     * see {@link #registerApplicationLogic(String, int, Class)}
     * 参数分别为进程名优先级和对应的BaseApplicationLogic的逻辑，里面一般用于注册Provider，see{@link MaProvider}
     */
    protected abstract void initializeLogic();

    /**
     * 当前App是否是多进程的
     *
     * @return
     */
    public abstract boolean needMultipleProcess();

    /**
     * 注册每个模块的一些在Application里面需要处理的逻辑
     *
     * @param processName 进程名
     * @param priority    优先级
     * @param logicClass  对应的处理类
     * @return
     */
    protected boolean registerApplicationLogic(
            String processName,
            int priority,
            @NonNull Class<? extends BaseApplicationLogic> logicClass) {
        boolean result = false;
        if (null != mLogicClassMap) {
            ArrayList<PriorityLogicWrapper> tempList = mLogicClassMap.get(processName);
            if (null == tempList) {
                tempList = new ArrayList<>();
                mLogicClassMap.put(processName, tempList);
            }
            if (tempList.size() > 0) {
                for (PriorityLogicWrapper priorityLogicWrapper : tempList) {
                    if (logicClass.getName().equals(priorityLogicWrapper.logicClass.getName())) {
                        throw new RuntimeException(logicClass.getName() + " has registered.");
                    }
                }
            }
            // 每个进程都可能有多个BaseApplicationLogic的
            PriorityLogicWrapper priorityLogicWrapper = new PriorityLogicWrapper(priority, logicClass);
            tempList.add(priorityLogicWrapper);
        }
        return result;
    }

    /**
     * 获取当前进程的Logic
     */
    private void dispatchLogic() {
        if (null != mLogicClassMap) {
            mLogicList = mLogicClassMap.get(ProcessUtil.getProcessName(this, ProcessUtil.getMyProcessId()));
        }
    }

    /**
     * 初始化当前进程的Logic，初始化之后就会可以调用里面的proxy的生命周期的方法
     */
    private void instantiateLogic() {
        if (null != mLogicList && mLogicList.size() > 0) {
            Collections.sort(mLogicList);
            for (PriorityLogicWrapper priorityLogicWrapper : mLogicList) {
                if (null != priorityLogicWrapper) {
                    try {
                        priorityLogicWrapper.instance = priorityLogicWrapper.logicClass.newInstance();
                    } catch (InstantiationException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    if (null != priorityLogicWrapper.instance) {
                        priorityLogicWrapper.instance.setApplication(this);
                    }
                }
            }
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        logiconTerminate();
    }

    private void logiconTerminate() {
        if (null != mLogicList && mLogicList.size() > 0) {
            for (PriorityLogicWrapper priorityLogicWrapper : mLogicList) {
                if (null != priorityLogicWrapper && null != priorityLogicWrapper.instance) {
                    priorityLogicWrapper.instance.onTerminate();
                }
            }
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        logiconLowMemory();
    }

    private void logiconLowMemory() {
        if (null != mLogicList && mLogicList.size() > 0) {
            for (PriorityLogicWrapper priorityLogicWrapper : mLogicList) {
                if (null != priorityLogicWrapper && null != priorityLogicWrapper.instance) {
                    priorityLogicWrapper.instance.onLowMemory();
                }
            }
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        logiconTrimMemory(level);
    }

    private void logiconTrimMemory(int level) {
        if (null != mLogicList && mLogicList.size() > 0) {
            for (PriorityLogicWrapper priorityLogicWrapper : mLogicList) {
                if (null != priorityLogicWrapper && null != priorityLogicWrapper.instance) {
                    priorityLogicWrapper.instance.onTrimMemory(level);
                }
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        logiconConfigurationChanged(newConfig);
    }

    private void logiconConfigurationChanged(Configuration newConfig) {
        if (null != mLogicList && mLogicList.size() > 0) {
            for (PriorityLogicWrapper priorityLogicWrapper : mLogicList) {
                if (null != priorityLogicWrapper && null != priorityLogicWrapper.instance) {
                    priorityLogicWrapper.instance.onConfigurationChanged(newConfig);
                }
            }
        }
    }

    public static MaApplication getMaApplication() {
        return sInstance;
    }
}
