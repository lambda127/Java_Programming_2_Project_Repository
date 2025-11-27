package UWB

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.uwb.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

// Java로 결과를 전달하기 위한 인터페이스
interface UwbRangingCallback {
    fun onLocalAddressReceived(address: ByteArray)
    fun onRangingResult(distance: Float)
    fun onRangingError(error: String)
    fun onRangingComplete()
    fun onRangingStarted(isController: Boolean, complexChannel: UwbComplexChannel?)
}

class UwbRangingHelper(private val context: Context, private val callback: UwbRangingCallback) {

    private var uwbManager: UwbManager? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var rangingJob: Job? = null
    private val TAG = "UwbRangingHelper"

    val isRanging: Boolean
        get() = rangingJob?.isActive == true

    init {
        scope.launch {
            Log.d(TAG, "UWB Manager 초기화 시작")
            
            // Check UWB System Feature
            val hasUwbFeature = context.packageManager.hasSystemFeature("android.hardware.uwb")
            Log.d(TAG, "System has UWB feature: $hasUwbFeature")

            // Check Google Play Services Version
            try {
                val pm = context.packageManager
                val pkgInfo = pm.getPackageInfo("com.google.android.gms", 0)
                Log.d(TAG, "Google Play Services Version: ${pkgInfo.versionName} (${pkgInfo.longVersionCode})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get GMS version", e)
            }

            uwbManager = UwbManager.createInstance(context)
            Log.d(TAG, "UWB Manager 초기화 완료")
        }
    }

    // 1단계: 내 기기의 UWB 주소 가져오기
    fun prepareLocalAddress(isController: Boolean) {
        Log.d(TAG, "prepareLocalAddress 호출됨 - isController: $isController")
        scope.launch {
            try {
                val manager = uwbManager ?: run {
                    Log.e(TAG, "UwbManager가 null입니다.")
                    throw IllegalStateException("UwbManager 초기화 실패")
                }
                val sessionScope = if (isController) {
                    Log.d(TAG, "Controller session scope 생성 중")
                    manager.controllerSessionScope()
                } else {
                    Log.d(TAG, "Controlee session scope 생성 중")
                    manager.controleeSessionScope()
                }
                var addressBytes = sessionScope.localAddress.address
                // Removed Android 13 byte reversal logic
                Log.d(TAG, "로컬 주소 수신: ${addressBytes.joinToString { "%02X".format(it) }}")
                callback.onLocalAddressReceived(addressBytes)
            } catch (e: Exception) {
                Log.e(TAG, "주소 생성 실패", e)
                callback.onRangingError("주소 생성 실패: ${e.message}")
            }
        }
    }

    // 2단계: Ranging 시작
    fun startRanging(remoteAddress: ByteArray, sessionId: Int, isController: Boolean, complexChannel: UwbComplexChannel? = null) {
        Log.d(TAG, "startRanging 호출됨 - remoteAddress: ${remoteAddress.joinToString { "%02X".format(it) }}, sessionId: $sessionId, isController: $isController, complexChannel: $complexChannel")
        stopRanging()

        rangingJob = scope.launch {
            try {
                val manager = uwbManager ?: run {
                    Log.e(TAG, "startRanging: UwbManager가 null입니다.")
                    return@launch
                }
                val sessionScope = if (isController) {
                    manager.controllerSessionScope()
                } else {
                    manager.controleeSessionScope()
                }

                // Removed Android 13 byte reversal logic for remoteAddress
                val partnerAddressBytes = remoteAddress
                val partnerAddress = UwbAddress(partnerAddressBytes)
                val partnerDevice = UwbDevice.createForAddress(partnerAddress.address)

                // 8바이트 세션 키를 생성합니다.
                var sessionKey = "12345678".toByteArray()
                // Removed Android 13 byte reversal logic for sessionKey

                // Controller인 경우 세션 스코프에서 채널 정보를 가져와서 콜백으로 전달
                if (isController && sessionScope is UwbControllerSessionScope) {
                     val localComplexChannel = sessionScope.uwbComplexChannel
                     Log.d(TAG, "Controller complex channel: $localComplexChannel (Channel: ${localComplexChannel.channel}, Preamble: ${localComplexChannel.preambleIndex})")
                     callback.onRangingStarted(true, localComplexChannel)
                } else if (!isController) {
                     // Controlee인 경우 전달받은 채널 정보 사용
                     if (complexChannel != null) {
                         Log.d(TAG, "Controlee using complex channel: $complexChannel (Channel: ${complexChannel.channel}, Preamble: ${complexChannel.preambleIndex})")
                         callback.onRangingStarted(false, complexChannel)
                     } else {
                         Log.w(TAG, "Controlee이지만 complexChannel이 null입니다.")
                     }
                }

                val parameters = RangingParameters(
                    uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
                    sessionId = sessionId,
                    subSessionId = 0,
                    sessionKeyInfo = sessionKey,
                    subSessionKeyInfo = null,
                    complexChannel = complexChannel, // Controlee needs this
                    peerDevices = listOf(partnerDevice),
                    updateRateType = RangingParameters.RANGING_UPDATE_RATE_AUTOMATIC
                )

                Log.d(TAG, "Ranging session 준비 중... Parameters: $parameters")
                try {
                    sessionScope.prepareSession(parameters)
                        .onStart { Log.d(TAG, "Ranging Flow 시작됨 (Thread: ${Thread.currentThread().name})") }
                        .onEach { result ->
                            Log.v(TAG, "Ranging 결과 수신: $result")
                            when(result) {
                                is RangingResult.RangingResultPosition -> {
                                    val distance = result.position.distance
                                    if (distance != null) {
                                        Log.d(TAG, "Ranging 성공: 거리 = ${distance.value}m")
                                        callback.onRangingResult(distance.value)
                                    } else {
                                        Log.d(TAG, "Ranging 결과에 거리가 포함되지 않음")
                                    }
                                }
                                is RangingResult.RangingResultInitialized -> {
                                    Log.i(TAG, "Ranging 세션 초기화 완료, 거리 측정 준비됨")
                                }
                                is RangingResult.RangingResultPeerDisconnected -> {
                                    Log.w(TAG, "상대방 연결 끊김")
                                    callback.onRangingError("Peer disconnected")
                                    stopRanging()
                                }
                                else -> {
                                    Log.d(TAG, "처리되지 않은 Ranging 결과: $result")
                                }
                            }
                        }
                        .catch { e ->
                            Log.e(TAG, "Ranging flow에서 오류 발생 (Thread: ${Thread.currentThread().name})", e)
                            if (e is RuntimeException) {
                                Log.e(TAG, "RuntimeException Details: ${e.message}")
                                e.stackTrace.take(5).forEach { Log.e(TAG, "  at $it") }
                            }
                            callback.onRangingError("Ranging flow error: ${e.message}")
                        }
                        .onCompletion { cause ->
                            Log.d(TAG, "Ranging Flow 종료됨. Cause: $cause")
                        }
                        .launchIn(this)
                    Log.d(TAG, "Ranging session 시작됨")
                } catch (e: Exception) {
                     Log.e(TAG, "prepareSession 호출 중 오류 발생", e)
                     callback.onRangingError("prepareSession error: ${e.message}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Ranging 시작 실패", e)
                callback.onRangingError("Ranging 시작 실패: ${e.message}")
            }
        }
    }

    fun stopRanging() {
        if (rangingJob?.isActive == true) {
            Log.d(TAG, "stopRanging 호출됨, Ranging 중단")
            rangingJob?.cancel()
        }
        rangingJob = null
        callback.onRangingComplete()
    }
}