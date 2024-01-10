# Video Capturer CameraX

Sample app shows how to use device camera as the video source with the custom capturer using the [CameraX](https://developer.android.com/media/camera/camerax) library. This class was introduced in API level 21 by providing an alternative for deprecated [Camera](https://developer.android.com/reference/android/hardware/Camera) class.


## Using a custom video capturer

The `MirrorVideoCapturer` is a custom class that extends the `BaseVideoCapturer` class, defined in the OpenTok Android SDK. During construction of a `Publisher` object, the code sets a custom video capturer by calling the `capturer` method of the Publisher:

```kotlin
    publisher = Publisher.Builder(this@MainActivity)
      .capturer(mirrorVideoCapturer)
      .build()
```

The `getCaptureSettings` method returns the settings of the video capturer, including the frame rate, width, height, video delay, and video format for the capturer:

```kotlin
    override fun getCaptureSettings(): CaptureSettings {
        val captureSettings = CaptureSettings()
        captureSettings.fps = desiredFps
        captureSettings.width = if (null != cameraFrame) cameraFrame!!.width else 0
        captureSettings.height = if (null != cameraFrame) cameraFrame!!.height else 0
        captureSettings.format = NV21
        captureSettings.expectedDelay = 0
        return captureSettings
    }
```

The app calls `startCamera` method to start capturing video from the custom video capturer.

```kotlin
    override fun startCapture(): Int {
        startCamera()
        return 0
    }
```

The publisher adds video frames to the published stream.


## Set up credentials

You will need a valid [TokBox account](https://tokbox.com/account/user/signup) for the project. OpenTok credentials (`API_KEY`, `SESSION_ID`, `TOKEN`) are stored inside `OpenTokConfig` class. Credentials can be retrieved from the [Dashboard](https://dashboard.tokbox.com/projects) and hardcoded in the application. 

> Note: To facilitate testing connect to the same session using [OpenTok Playground](https://tokbox.com/developer/tools/playground/) (web client).


