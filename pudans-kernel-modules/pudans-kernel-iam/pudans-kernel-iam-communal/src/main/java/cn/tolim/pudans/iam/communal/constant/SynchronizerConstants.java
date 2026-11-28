package cn.tolim.pudans.iam.communal.constant;

import cn.tolim.pudans.iam.infra.constant.IamConstants;


public final class SynchronizerConstants {

    // Synchronizer path
    public final static String SYNCHRONIZER_PATH = IamConstants.V1_API_PATH + "/synchronizer";

    // Synchronizer event receive path
    public final static String EVENT_RECEIVE_PATH = SYNCHRONIZER_PATH + "/event_receive";
}
