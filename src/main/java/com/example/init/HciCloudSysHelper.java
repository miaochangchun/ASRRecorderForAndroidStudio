package com.example.init;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.sinovoice.hcicloudsdk.api.HciCloudSys;
import com.sinovoice.hcicloudsdk.common.AuthExpireTime;
import com.sinovoice.hcicloudsdk.common.HciErrorCode;
import com.sinovoice.hcicloudsdk.common.InitParam;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by 李璐廷 on 2016/5/22.
 */
public class HciCloudSysHelper {
    private static final String TAG = HciCloudSysHelper.class.getSimpleName();
    private static final String CLOUD_URL = "http://test.api.hcicloud.com:8888";
    private static final String APP_KEY = "c85d54f1";
    private static final String DEVELOPER_KEY = "712ddd892cf9163e6383aa169e0454e3";
    private static HciCloudSysHelper mInstance;

    private HciCloudSysHelper() {
    }

    public static HciCloudSysHelper getInstance() {
        if (mInstance == null) {
            mInstance = new HciCloudSysHelper();
        }
        return mInstance;
    }

    /**
     * HciCloud系统初始化
     * @param context
     * @return 初始化状态，修改为返回boolean，true是初始化成功，false初始化失败
     */
    public boolean init(Context context) {
        // 加载信息,返回InitParam, 获得配置参数的字符串
        InitParam initParam = getInitParam(context);
        String strConfig = initParam.getStringConfig();
        Log.i(TAG, "strConfig value:" + strConfig);

        // 初始化
        int initResult = HciCloudSys.hciInit(strConfig, context);
        if (initResult != HciErrorCode.HCI_ERR_NONE && initResult != HciErrorCode.HCI_ERR_SYS_ALREADY_INIT) {
            Log.e(TAG, "hciInit error: " + initResult);
            return false;
        } else {
            Log.i(TAG, "hciInit success");
        }

        // 此函数会检测授权文件是否过期，若过期了则需要联网更新授权，不过期不用连接网络。
        // 第一次使用的时候，授权不存在，需要连接网络。
        int authResult = checkAuth();
        if (authResult != HciErrorCode.HCI_ERR_NONE) {
            // 由于系统已经初始化成功,在结束前需要调用方法hciRelease()进行系统的反初始化
            Log.e(TAG, "auth error: " + authResult);
            HciCloudSys.hciRelease();
            return false;
        }

        return true;
    }

    /**
     * 系统反初始化
     */
    public void release() {
        int nRet = HciCloudSys.hciRelease();
        Log.i(TAG, "HciCloud release, result = " + nRet);
    }

    /**
     * 设置初始化参数
     * @param context
     * @return
     */
    private InitParam getInitParam(Context context) {
        //授权文件存放的目录，此目录是/data/data/packagename/files/目录，授权文件通过联网下载是HCI_AUTH和HCI_USER_INFO
        String authDirPath = context.getFilesDir().getAbsolutePath();

        // 前置条件：无
        InitParam initparam = new InitParam();

        // 授权文件所在路径，此项必填
        initparam.addParam(InitParam.AuthParam.PARAM_KEY_AUTH_PATH, authDirPath);

        // 是否自动访问云授权,详见 获取授权/更新授权文件处注释
        initparam.addParam(InitParam.AuthParam.PARAM_KEY_AUTO_CLOUD_AUTH, "no");

        // 灵云云服务的接口地址，此项必填，可以在开发者社区注册应用之后再应用详情中获取
        initparam.addParam(InitParam.AuthParam.PARAM_KEY_CLOUD_URL, CLOUD_URL);

        // 开发者Key，此项必填，由捷通华声提供
        initparam.addParam(InitParam.AuthParam.PARAM_KEY_DEVELOPER_KEY, DEVELOPER_KEY);

        // 应用Key，此项必填，由捷通华声提供
        initparam.addParam(InitParam.AuthParam.PARAM_KEY_APP_KEY, APP_KEY);

        // 配置日志参数
        String sdcardState = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(sdcardState)) {
            String sdPath = Environment.getExternalStorageDirectory()
                    .getAbsolutePath();
            String packageName = context.getPackageName();
            //日志文件所在路径，一般目录下是/storage/sdcard/0/sinovoice/packagename/log目录下，日志文件名是hci.log
            String logPath = sdPath + File.separator + "sinovoice"
                    + File.separator + packageName + File.separator + "log"
                    + File.separator;

            // 日志文件地址
            File fileDir = new File(logPath);
            if (!fileDir.exists()) {
                fileDir.mkdirs();
            }

            // 日志的路径，可选，如果不传或者为空则不生成日志
            initparam.addParam(InitParam.LogParam.PARAM_KEY_LOG_FILE_PATH, logPath);

            // 日志数目，默认保留多少个日志文件，超过则覆盖最旧的日志
            initparam.addParam(InitParam.LogParam.PARAM_KEY_LOG_FILE_COUNT, "5");

            // 日志大小，默认一个日志文件写多大，单位为K
            initparam.addParam(InitParam.LogParam.PARAM_KEY_LOG_FILE_SIZE, "1024");

            // 日志等级，0=无，1=错误，2=警告，3=信息，4=细节，5=调试，SDK将输出小于等于logLevel的日志信息,
            // 在正式使用时可以设置为0，不会有日志在输出。
            initparam.addParam(InitParam.LogParam.PARAM_KEY_LOG_LEVEL, "5");
        }

        return initparam;
    }

    /**
     * 更新授权文件
     * @return
     */
    private int checkAuth() {
        // 获取系统授权到期时间
        int initResult;
        AuthExpireTime objExpireTime = new AuthExpireTime();
        initResult = HciCloudSys.hciGetAuthExpireTime(objExpireTime);
        if (initResult == HciErrorCode.HCI_ERR_NONE) {
            // 显示授权日期,如用户不需要关注该值,此处代码可忽略
            Date date = new Date(objExpireTime.getExpireTime() * 1000);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
            Log.i(TAG, "expire time: " + sdf.format(date));

            if (objExpireTime.getExpireTime() * 1000 < System
                    .currentTimeMillis()) {
                // 获取授权方法, 返回值为错误码
                Log.i(TAG, "expired date");

                initResult = HciCloudSys.hciCheckAuth();
                if (initResult == HciErrorCode.HCI_ERR_NONE) {
                    Log.i(TAG, "checkAuth success");
                    return initResult;
                } else {
                    Log.e(TAG, "checkAuth failed: " + initResult);
                    return initResult;
                }
            } else {
                // 已经成功获取了授权,并且距离授权到期有充足的时间(>7天)
                Log.i(TAG, "checkAuth success");
                return initResult;
            }
        } else if (initResult == HciErrorCode.HCI_ERR_SYS_AUTHFILE_INVALID) {
            // 如果读取Auth文件失败(比如第一次运行,还没有授权文件),则开始获取授权
            Log.i(TAG, "authfile invalid");

            initResult = HciCloudSys.hciCheckAuth();
            if (initResult == HciErrorCode.HCI_ERR_NONE) {
                Log.i(TAG, "checkAuth success");
                return initResult;
            } else {
                Log.e(TAG, "checkAuth failed: " + initResult);
                return initResult;
            }
        } else {
            // 其他失败原因,请根据SDK帮助文档中"常量字段值"中的错误码的含义检查错误所在
            Log.e(TAG, "getAuthExpireTime Error:" + initResult);
            return initResult;
        }
    }
}
