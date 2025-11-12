// IKeepAliveAidlInterface.aidl
package com.funshion.funautosend;

// 进程间通信的AIDL接口
interface IKeepAliveAidlInterface {
    /**
     * 检查进程是否存活
     */
    boolean isProcessAlive();
    
    /**
     * 获取当前进程名
     */
    String getProcessName();
}