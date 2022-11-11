package com.moosedrive.wallpaperer;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class BackgroundExecutor {
    private static ThreadPoolExecutor executor;

    public static ThreadPoolExecutor getExecutor(){
        if (executor == null){
            int poolSize = (Runtime.getRuntime().availableProcessors() < 2)
                    ? 1
                    : Runtime.getRuntime().availableProcessors() - 1;
            executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(poolSize);
        }
        return executor;
    }
}
