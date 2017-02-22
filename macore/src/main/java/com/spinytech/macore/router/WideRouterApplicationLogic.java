package com.spinytech.macore.router;

import com.spinytech.macore.multiprocess.BaseApplicationLogic;

/**
 * Created by wanglei on 2016/11/25.
 * update 2017-2-22 添加注释
 */
public final class WideRouterApplicationLogic extends BaseApplicationLogic {
    @Override
    public void onCreate() {
        super.onCreate();
        initRouter();
    }

    protected void initRouter() {
        // 初始化WideRouter
        WideRouter.getInstance(mApplication);
        // 注册每个进程的对应的LocalRouter
        mApplication.initializeAllProcessRouter();
    }
}
