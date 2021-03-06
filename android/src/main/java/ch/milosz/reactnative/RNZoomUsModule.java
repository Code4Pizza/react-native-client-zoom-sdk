package ch.milosz.reactnative;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;
import android.widget.PopupWindow;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import ch.milosz.reactnative.audio.MeetingAudioCallback;
import ch.milosz.reactnative.audio.MeetingAudioHelper;
import ch.milosz.reactnative.event.MeetingStateEvent;
import ch.milosz.reactnative.event.MeetingUserEvent;
import ch.milosz.reactnative.initsdk.AuthParams;
import ch.milosz.reactnative.initsdk.InitAuthSDKCallback;
import ch.milosz.reactnative.initsdk.InitAuthSDKHelper;
import ch.milosz.reactnative.user.MeetingUserCallback;
import ch.milosz.reactnative.video.MeetingVideoCallback;
import ch.milosz.reactnative.video.MeetingVideoHelper;
import us.zoom.sdk.CustomizedNotificationData;
import us.zoom.sdk.InMeetingNotificationHandle;
import us.zoom.sdk.InMeetingShareController;
import us.zoom.sdk.InMeetingUserInfo;
import us.zoom.sdk.JoinMeetingOptions;
import us.zoom.sdk.JoinMeetingParams;
import us.zoom.sdk.MeetingError;
import us.zoom.sdk.MeetingService;
import us.zoom.sdk.MeetingServiceListener;
import us.zoom.sdk.MeetingStatus;
import us.zoom.sdk.ZoomError;
import us.zoom.sdk.ZoomSDK;

import static ch.milosz.reactnative.ZoomConstants.ZoomInstantSDKShareStatus_Start;
import static ch.milosz.reactnative.ZoomConstants.ZoomInstantSDKShareStatus_Stop;
import static ch.milosz.reactnative.event.EventConstants.MEETING_ACTIVE_SHARE;
import static ch.milosz.reactnative.event.EventConstants.MEETING_AUDIO_STATUS_CHANGE;
import static ch.milosz.reactnative.event.EventConstants.MEETING_USER_JOIN;
import static ch.milosz.reactnative.event.EventConstants.MEETING_USER_LEFT;
import static ch.milosz.reactnative.event.EventConstants.MEETING_VIDEO_STATUS_CHANGE;

public class RNZoomUsModule extends ReactContextBaseJavaModule implements
        MeetingServiceListener,
        InitAuthSDKCallback,
        LifecycleEventListener,
        MeetingAudioCallback.AudioEvent,
        MeetingVideoCallback.VideoEvent,
        MeetingUserCallback.UserEvent, InMeetingShareController.InMeetingShareListener {

  private static final String TAG = "RNZoomUsModule";

  public final static int REQUEST_CAMERA_CODE = 1010;

  public final static int REQUEST_AUDIO_CODE = 1011;

  public static final String MEETING_EVENT = "onMeetingEvent";

  private final ReactContext mContext;
  private final AtomicBoolean mIsObserverRegistered = new AtomicBoolean(false);
  private final IntentFilter filter = new IntentFilter() {{
    addAction("onConfigurationChanged");
    addAction("onRequestPermissionsResult");
  }};
  private ZoomSDK mZoomSDK;
  private MeetingAudioHelper meetingAudioHelper;
  private MeetingVideoHelper meetingVideoHelper;
  private Callback mInitCallback;
  private long currentShareUserId = -1;

  private final BroadcastReceiver moduleConfigReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (mContext.getCurrentActivity() == null) {
        return;
      }
      if ("onConfigurationChanged".equals(intent.getAction())) {
        Configuration configuration = intent.getParcelableExtra("newConfig");
        if (configuration != null) {
          Objects.requireNonNull(mContext.getCurrentActivity()).runOnUiThread(() -> {
            if (meetingVideoHelper != null) {
              meetingVideoHelper.checkVideoRotation(mContext);
            }
          });
        }
      } else if ("onRequestPermissionsResult".equals(intent.getAction())) {
        String[] permissions = intent.getStringArrayExtra("permissions");
        int[] grantResults = intent.getIntArrayExtra("grantResults");
        if (permissions == null || grantResults == null) {
          return;
        }
        Objects.requireNonNull(mContext.getCurrentActivity()).runOnUiThread(() -> {
          for (int i = 0; i < permissions.length; i++) {
            if (Manifest.permission.RECORD_AUDIO.equals(permissions[i])) {
              if (grantResults[i] == PackageManager.PERMISSION_GRANTED && meetingAudioHelper != null) {
                meetingAudioHelper.switchAudio(false);
              }
            } else if (Manifest.permission.CAMERA.equals(permissions[i]) && meetingVideoHelper != null) {
              if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                meetingVideoHelper.onVideo();
              }
            }
          }
        });
      }
    }
  };

  public RNZoomUsModule(ReactApplicationContext context) {
    super(context);
    this.mContext = context;
    this.mContext.addLifecycleEventListener(this);
  }

  @NonNull
  @Override
  public String getName() {
    return "ZoomModule";
  }

  @ReactMethod
  public void toast(String text) {
    Toast.makeText(mContext, text, Toast.LENGTH_SHORT).show();
  }

  @ReactMethod
  public void initZoomSDK(ReadableMap data, Callback callback) {
    mInitCallback = callback;
    String domain = data.getString("domain");
    String clientKey = data.getString("clientKey");
    String clientSecret = data.getString("clientSecret");
    AuthParams.init(domain, clientKey, clientSecret);

    Objects.requireNonNull(mContext.getCurrentActivity()).runOnUiThread(() -> {
      mZoomSDK = ZoomSDK.getInstance();
      InitAuthSDKHelper.getInstance().initSDK(mContext, RNZoomUsModule.this);
    });
  }

  @Override
  public void onZoomSDKInitializeResult(int errorCode, int internalErrorCode) {
    Log.i(TAG, "onZoomSDKInitializeResult, errorCode=" + errorCode + ", internalErrorCode=" + internalErrorCode);
    if (errorCode != ZoomError.ZOOM_ERROR_SUCCESS) {
      Log.e(TAG, "Failed to initialize Zoom SDK. Error: " + errorCode + ", internalErrorCode=" + internalErrorCode);
      if (mInitCallback != null) {
        mInitCallback.invoke(false);
      }
    } else {
      Objects.requireNonNull(mContext.getCurrentActivity()).runOnUiThread(() -> {
        ZoomSDK.getInstance().getMeetingSettingsHelper().enable720p(false);
        ZoomSDK.getInstance().getMeetingSettingsHelper().setCustomizedMeetingUIEnabled(true);
        ZoomSDK.getInstance().getMeetingSettingsHelper().setAutoConnectVoIPWhenJoinMeeting(true);
        CustomizedNotificationData data = new CustomizedNotificationData();
        data.setContentTitleId(R.string.notification_title);
        data.setContentTextId(R.string.notification_text);
        data.setLargeIconId(R.drawable.ic_launcher);
        data.setSmallIconId(R.drawable.ic_launcher);
        ZoomSDK.getInstance().getMeetingSettingsHelper().setCustomizedNotificationData(data, handle);
        ZoomSDK.getInstance().getMeetingService().addListener(RNZoomUsModule.this);

        meetingAudioHelper = new MeetingAudioHelper(audioCallBack);
        meetingVideoHelper = new MeetingVideoHelper(mContext.getCurrentActivity(), videoCallBack);

        MeetingAudioCallback.getInstance().addListener(this);
        MeetingVideoCallback.getInstance().addListener(this);
        MeetingUserCallback.getInstance().addListener(this);

        ZoomSDK.getInstance().getInMeetingService().getInMeetingShareController().addListener(this);

        if (mInitCallback != null) {
          mInitCallback.invoke(true);
        }
      });
      Log.d(TAG, "Initialize Zoom SDK successfully.");
    }
  }

  MeetingAudioHelper.AudioCallBack audioCallBack = new MeetingAudioHelper.AudioCallBack() {
    @Override
    public boolean requestAudioPermission() {
      if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(Objects.requireNonNull(mContext.getCurrentActivity()), new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_CODE);
        return false;
      }
      return true;
    }

    @Override
    public void updateAudioButton() {
    }
  };

  MeetingVideoHelper.VideoCallBack videoCallBack = new MeetingVideoHelper.VideoCallBack() {
    @Override
    public boolean requestVideoPermission() {

      if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(Objects.requireNonNull(mContext.getCurrentActivity()), new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_CODE);
        return false;
      }
      return true;
    }

    @Override
    public void showCameraList(PopupWindow popupWindow) {
    }
  };

  public int checkSelfPermission(String permission) {
    if (permission == null || permission.length() == 0) {
      return PackageManager.PERMISSION_DENIED;
    }
    try {
      return Objects.requireNonNull(mContext.getCurrentActivity()).checkPermission(permission, android.os.Process.myPid(), android.os.Process.myUid());
    } catch (Throwable e) {
      return PackageManager.PERMISSION_DENIED;
    }
  }

  InMeetingNotificationHandle handle = (context, intent) -> true;

  @Override
  public void onZoomAuthIdentityExpired() {
    Log.d(TAG, "onZoomAuthIdentityExpired");
  }

  @Override
  public void onMeetingStatusChanged(MeetingStatus meetingStatus, int errorCode, int internalErrorCode) {
    Log.d(TAG, "onMeetingStatusChanged " + meetingStatus + ":" + errorCode + ":" + internalErrorCode);
    if (!mZoomSDK.isInitialized()) {
      return;
    }
    if (meetingStatus == MeetingStatus.MEETING_STATUS_INMEETING) {
      Objects.requireNonNull(mContext.getCurrentActivity()).runOnUiThread(() -> {
        if (meetingVideoHelper != null) {
          meetingVideoHelper.checkVideoRotation(mContext);
        }
        if (ZoomSDK.getInstance().getInMeetingService() != null) {
          Log.d(TAG, "onZoomSDKInitializeResult: off Chime");
          ZoomSDK.getInstance().getInMeetingService().setPlayChimeOnOff(false);
        }
      });
    }
    if (mIsObserverRegistered.get()) {
      sendEvent(MeetingStateEvent.toParams(meetingStatus));
    }
  }

  @ReactMethod
  public void joinMeeting(ReadableMap data) {
    final String roomNumber = data.getString(ZoomConstants.ARG_ROOM_NUMBER);
    final String roomPassword = data.getString(ZoomConstants.ARG_ROOM_PASSWORD);
    final String userName = data.getString(ZoomConstants.ARG_USER_NAME);

    Objects.requireNonNull(mContext.getCurrentActivity()).runOnUiThread(() -> {
      if (!mZoomSDK.isInitialized()) {
        // RNZoomUsModule.this.toast("Init SDK First");
        InitAuthSDKHelper.getInstance().initSDK(mContext, RNZoomUsModule.this);
        return;
      }

      final MeetingService meetingService = mZoomSDK.getMeetingService();
      if (meetingService.getMeetingStatus() != MeetingStatus.MEETING_STATUS_IDLE) {
        long lMeetingNo = 0;
        try {
          lMeetingNo = Long.parseLong(roomNumber);
        } catch (NumberFormatException e) {
          // TODO send invalid room number event
          return;
        }

        if (meetingService.getCurrentRtcMeetingNumber() == lMeetingNo) {
          meetingService.returnToMeeting(mContext.getCurrentActivity());
          Log.w(TAG, "Already joined zoom meeting");
          return;
        }
      }

      ZoomSDK.getInstance().getSmsService().enableZoomAuthRealNameMeetingUIShown(false);

      JoinMeetingParams params = new JoinMeetingParams();
      params.meetingNo = roomNumber;
      params.password = roomPassword;
      params.displayName = userName;
      JoinMeetingOptions options = new JoinMeetingOptions();

      int startMeetingResult = meetingService.joinMeetingWithParams(mContext.getCurrentActivity(), params, options);
      Log.i(TAG, "joinMeeting, result=" + startMeetingResult);

      if (startMeetingResult != MeetingError.MEETING_ERROR_SUCCESS) {
        // TODO send event failed join meeting
      }
    });
  }

  @ReactMethod
  public void leaveCurrentMeeting() {
    if (mZoomSDK == null || mZoomSDK.getInMeetingService() == null) {
      return;
    }
    mZoomSDK.getInMeetingService().leaveCurrentMeeting(false);
  }

  @ReactMethod
  public void getParticipants(final Callback callback) {
    if (mZoomSDK == null || mZoomSDK.getInMeetingService() == null) {
      return;
    }
    Objects.requireNonNull(mContext.getCurrentActivity()).runOnUiThread(() -> {
      List<Long> userIds = mZoomSDK.getInMeetingService().getInMeetingUserList();
      if (userIds == null) {
        return;
      }
      Log.d(TAG, "getParticipants: " + Arrays.toString(userIds.toArray()));
      WritableArray array = new WritableNativeArray();
      for (Long userId : userIds) {
        InMeetingUserInfo info = mZoomSDK.getInMeetingService().getUserInfoById(userId);
        if (info != null) {
          WritableMap params = new WritableNativeMap();
          params.putString(ZoomConstants.ARG_USER_ID, String.valueOf(info.getUserId()));
          params.putString(ZoomConstants.ARG_USER_NAME, info.getUserName());
          params.putString(ZoomConstants.ARG_AVATAR_PATH, info.getAvatarPath());
          params.putBoolean(ZoomConstants.ARG_VIDEO_STATUS, info.getVideoStatus() != null && info.getVideoStatus().isSending());
          params.putBoolean(ZoomConstants.ARG_AUDIO_STATUS, info.getAudioStatus() != null && !info.getAudioStatus().isMuted());
          params.putString(ZoomConstants.ARG_VIDEO_RATIO, "1.0");
          params.putBoolean(ZoomConstants.ARG_IS_HOST, info.getInMeetingUserRole() == InMeetingUserInfo.InMeetingUserRole.USERROLE_HOST);
          array.pushMap(params);
        } else {
          Log.e(TAG, "failed to getUserInfo: " + userId);
        }
      }
      callback.invoke(null, array);
    });
  }

  @ReactMethod
  public void getUserInfo(final String userId, final Callback callback) {
    if (mZoomSDK == null || mZoomSDK.getInMeetingService() == null) {
      return;
    }
    Objects.requireNonNull(mContext.getCurrentActivity()).runOnUiThread(() -> {
      InMeetingUserInfo info;
      if ("local_user".equals(userId)) {
        info = mZoomSDK.getInMeetingService().getMyUserInfo();
      } else {
        info = mZoomSDK.getInMeetingService().getUserInfoById(Long.parseLong(userId));
      }
      if (info != null) {
        WritableMap map = new WritableNativeMap();
        map.putString(ZoomConstants.ARG_USER_ID, String.valueOf(info.getUserId()));
        map.putString(ZoomConstants.ARG_USER_NAME, info.getUserName());
        map.putString(ZoomConstants.ARG_AVATAR_PATH, info.getAvatarPath());
        map.putBoolean(ZoomConstants.ARG_VIDEO_STATUS, info.getVideoStatus() != null && info.getVideoStatus().isSending());
        map.putBoolean(ZoomConstants.ARG_AUDIO_STATUS, info.getAudioStatus() != null && !info.getAudioStatus().isMuted());
        map.putString(ZoomConstants.ARG_VIDEO_RATIO, "1.0");
        map.putBoolean(ZoomConstants.ARG_IS_HOST, info.getInMeetingUserRole() == InMeetingUserInfo.InMeetingUserRole.USERROLE_HOST);
        callback.invoke(null, map);
      } else {
        Log.e(TAG, "failed to getUserInfo: " + userId);
      }
    });
  }

  @ReactMethod
  public void onMyAudio() {
    Log.d(TAG, "onMyAudio: ");
    Objects.requireNonNull(mContext.getCurrentActivity()).runOnUiThread(() -> {
      if (meetingAudioHelper != null) {
        meetingAudioHelper.switchAudio(false);
      }
    });
  }

  @ReactMethod
  public void offMyAudio() {
    Log.d(TAG, "offMyAudio: ");
    Objects.requireNonNull(mContext.getCurrentActivity()).runOnUiThread(() -> {
      if (meetingAudioHelper != null) {
        meetingAudioHelper.switchAudio(true);
      }
    });
  }

  @ReactMethod
  public void onOffMyVideo() {
    Objects.requireNonNull(mContext.getCurrentActivity()).runOnUiThread(() -> {
      if (meetingVideoHelper != null) {
        meetingVideoHelper.switchVideo();
      }
    });
  }

  @ReactMethod
  public void switchMyCamera() {
    Objects.requireNonNull(mContext.getCurrentActivity()).runOnUiThread(() -> {
      if (meetingVideoHelper != null) {
        meetingVideoHelper.switchCamera();
      }
    });
  }

  @ReactMethod
  public void startObserverEvent() {
    Log.i(TAG, "registerListener");
    mIsObserverRegistered.set(true);
  }

  @ReactMethod
  public void stopObserverEvent() {
    Log.i(TAG, "unregisterListener");
    mIsObserverRegistered.set(false);
  }

  private void sendEvent(@Nullable WritableMap params) {
    mContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(MEETING_EVENT, params);
  }

  // React LifeCycle
  @Override
  public void onHostDestroy() {
    Log.d(TAG, "onHostDestroy: ");
    this.mContext.unregisterReceiver(moduleConfigReceiver);
  }

  @Override
  public void onHostPause() {
    Log.d(TAG, "onHostPause: ");
  }

  @Override
  public void onHostResume() {
    Log.d(TAG, "onHostResume: ");
    Objects.requireNonNull(mContext.getCurrentActivity()).runOnUiThread(() -> {
      if (meetingVideoHelper != null) {
        meetingVideoHelper.checkVideoRotation(mContext);
      }
    });
    this.mContext.registerReceiver(moduleConfigReceiver, filter);
  }

  @Override
  public void onMeetingUserJoin(List<Long> list) {
    if (!mIsObserverRegistered.get() || list == null || list.isEmpty()) {
      return;
    }
    Log.d(TAG, "onMeetingUserJoin: " + Arrays.toString(list.toArray()));
    Objects.requireNonNull(mContext.getCurrentActivity()).runOnUiThread(() -> {
      for (Long userId : list) {
        InMeetingUserInfo info = mZoomSDK.getInMeetingService().getUserInfoById(userId);
        if (info != null) {
          sendEvent(MeetingUserEvent.toParams(MEETING_USER_JOIN, info));
        }
      }
    });
  }

  @Override
  public void onMeetingUserLeave(List<Long> list) {
    if (!mIsObserverRegistered.get() || list == null || list.isEmpty()) {
      return;
    }
    Log.d(TAG, "onMeetingUserLeave: " + Arrays.toString(list.toArray()));
    Objects.requireNonNull(mContext.getCurrentActivity()).runOnUiThread(() -> {
      for (Long userId : list) {
        InMeetingUserInfo info = mZoomSDK.getInMeetingService().getUserInfoById(userId);
        if (info != null) {
          sendEvent(MeetingUserEvent.toParams(MEETING_USER_LEFT, info));
        }
      }
    });
  }

  @Override
  public void onSilentModeChanged(boolean inSilentMode) {
    // Log.d(TAG, "onSilentModeChanged: " + inSilentMode);
  }

  @Override
  public void onUserAudioStatusChanged(long userId) {
    if (!mIsObserverRegistered.get()) {
      return;
    }
    Log.d(TAG, "onUserAudioStatusChanged: " + userId);
    Objects.requireNonNull(mContext.getCurrentActivity()).runOnUiThread(() -> {
      InMeetingUserInfo info = mZoomSDK.getInMeetingService().getUserInfoById(userId);
      if (info != null) {
        sendEvent(MeetingUserEvent.toParams(MEETING_AUDIO_STATUS_CHANGE, info));
      }
    });
  }

  @Override
  public void onUserAudioTypeChanged(long userId) {
    // Log.d(TAG, "onUserAudioTypeChanged: " + userId);
  }

  @Override
  public void onMyAudioSourceTypeChanged(int type) {
    // Log.d(TAG, "onMyAudioSourceTypeChanged: " + type);
  }

  @Override
  public void onUserVideoStatusChanged(long userId) {
    if (!mIsObserverRegistered.get()) {
      return;
    }
    Log.d(TAG, "onUserVideoStatusChanged: " + userId);
    Objects.requireNonNull(mContext.getCurrentActivity()).runOnUiThread(() -> {
      InMeetingUserInfo info = mZoomSDK.getInMeetingService().getUserInfoById(userId);
      if (info != null) {
        sendEvent(MeetingUserEvent.toParams(MEETING_VIDEO_STATUS_CHANGE, info));
      }
    });
  }

  @Override
  public void onShareActiveUser(long userId) {
    if (!mIsObserverRegistered.get()) {
      return;
    }
    Objects.requireNonNull(mContext.getCurrentActivity()).runOnUiThread(() -> {
      InMeetingShareController controller = ZoomSDK.getInstance().getInMeetingService().getInMeetingShareController();
      if (controller.isOtherSharing() && userId > 0) {
        InMeetingUserInfo info = mZoomSDK.getInMeetingService().getUserInfoById(userId);
        if (info != null) {
          currentShareUserId = userId;
          sendEvent(MeetingUserEvent.toParams(MEETING_ACTIVE_SHARE, info, ZoomInstantSDKShareStatus_Start));
        }
      }
      if (userId < 0)
        if (currentShareUserId > 0) {
          InMeetingUserInfo info = mZoomSDK.getInMeetingService().getUserInfoById(currentShareUserId);
          if (info != null) {
            currentShareUserId = -1;
            sendEvent(MeetingUserEvent.toParams(MEETING_ACTIVE_SHARE, info, ZoomInstantSDKShareStatus_Stop));
          }
        } else {
          sendEvent(MeetingUserEvent.toParams(MEETING_ACTIVE_SHARE, ZoomInstantSDKShareStatus_Stop));
        }
    });
  }

  @Override
  public void onShareUserReceivingStatus(long userId) {

  }
}
